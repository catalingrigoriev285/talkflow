package com.example.talkflow;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Collections;
import java.util.Map;

/**
 * Receives pushes sent by the notifyNewMessage Cloud Function (see functions/index.js)
 * and shows Telegram-style message notifications, following the user's choices in
 * {@link NotificationSettingsActivity}.
 *
 * Device tokens are stored in fcmTokens/{uid}.tokens, readable only by their owner.
 */
public class TalkflowMessagingService extends FirebaseMessagingService {

    static final String TOKENS = "fcmTokens";
    private static final String CHANNEL_PREFIX = "messages";
    private static final long SIGN_OUT_TIMEOUT_MS = 3000;

    @Override
    public void onNewToken(@NonNull String token) {
        saveToken(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        Map<String, String> data = message.getData();
        String chatId = data.get("chatId");
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (chatId == null || user == null) return;
        // A token can outlive a sign-out; never show another account's messages.
        String recipientId = data.get("recipientId");
        if (recipientId != null && !recipientId.equals(user.getUid())) return;
        showMessageNotification(this, chatId, data.get("senderName"), data.get("text"));
    }

    /** Registers this device for the signed-in user's pushes. Safe to call repeatedly. */
    static void registerDevice() {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(TalkflowMessagingService::saveToken);
    }

    private static void saveToken(String token) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        FirebaseFirestore.getInstance().collection(TOKENS).document(user.getUid())
                .set(Collections.singletonMap("tokens", FieldValue.arrayUnion(token)), SetOptions.merge());
    }

    /**
     * Stops pushes to this device for the current account, then signs out. Waits briefly for
     * the token removal so the next account on this phone doesn't get the old one's messages.
     */
    static void signOut(Runnable onSignedOut) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        Handler handler = new Handler(Looper.getMainLooper());
        boolean[] done = {false};
        Runnable finish = () -> {
            if (done[0]) return;
            done[0] = true;
            FirebaseMessaging.getInstance().deleteToken(); // the next sign-in gets a fresh token
            FirebaseAuth.getInstance().signOut();
            onSignedOut.run();
        };
        if (user == null) {
            finish.run();
            return;
        }
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(tokenTask -> {
            if (!tokenTask.isSuccessful() || tokenTask.getResult() == null) {
                finish.run();
                return;
            }
            FirebaseFirestore.getInstance().collection(TOKENS).document(user.getUid())
                    .update("tokens", FieldValue.arrayRemove(tokenTask.getResult()))
                    .addOnCompleteListener(t -> finish.run());
        });
        handler.postDelayed(finish, SIGN_OUT_TIMEOUT_MS); // don't hang logout when offline
    }

    static void showMessageNotification(Context context, String chatId,
                                        @Nullable String senderName, @Nullable String text) {
        if (!NotificationSettingsActivity.isEnabled(context)) return;
        if (chatId.equals(ChatActivity.openChatId)) return; // already reading this chat
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String name = senderName == null || senderName.isEmpty()
                ? context.getString(R.string.app_name) : senderName;
        String body = NotificationSettingsActivity.showPreview(context) && text != null
                ? text : context.getString(R.string.notification_new_message);
        int notificationId = chatId.hashCode();

        Person sender = new Person.Builder()
                .setKey(chatId)
                .setName(name)
                .setIcon(IconCompat.createWithBitmap(avatarBitmap(context, name)))
                .build();
        NotificationCompat.MessagingStyle style = existingStyle(context, notificationId);
        if (style == null) {
            style = new NotificationCompat.MessagingStyle(
                    new Person.Builder().setName(context.getString(R.string.notification_you)).build());
        }
        style.addMessage(body, System.currentTimeMillis(), sender);

        Intent open = new Intent(context, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_CHAT_ID, chatId)
                .putExtra(ChatActivity.EXTRA_NAME, name);
        PendingIntent contentIntent = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(open)
                .getPendingIntent(notificationId,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ensureChannel(context))
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.tf_primary))
                .setStyle(style)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentIntent)
                .setAutoCancel(true);
        // Android 7 and older: sound and vibration are set per notification, not per channel.
        int defaults = NotificationCompat.DEFAULT_LIGHTS;
        if (NotificationSettingsActivity.sound(context)) defaults |= NotificationCompat.DEFAULT_SOUND;
        if (NotificationSettingsActivity.vibrate(context)) defaults |= NotificationCompat.DEFAULT_VIBRATE;
        builder.setDefaults(defaults);

        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }

    /** Clears a chat's notification, e.g. when the chat is opened. */
    static void cancelChatNotification(Context context, String chatId) {
        NotificationManagerCompat.from(context).cancel(chatId.hashCode());
    }

    /** Continues the chat's existing notification so several messages stack like Telegram. */
    @Nullable
    private static NotificationCompat.MessagingStyle existingStyle(Context context, int notificationId) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        for (StatusBarNotification active : manager.getActiveNotifications()) {
            if (active.getId() == notificationId) {
                return NotificationCompat.MessagingStyle
                        .extractMessagingStyleFromNotification(active.getNotification());
            }
        }
        return null;
    }

    /**
     * Android 8+ fixes sound and vibration per channel, so each combination of the two
     * settings gets its own channel id and the unused ones are removed. Users only ever
     * see one "Messages" channel in system settings.
     */
    static String ensureChannel(Context context) {
        boolean sound = NotificationSettingsActivity.sound(context);
        boolean vibrate = NotificationSettingsActivity.vibrate(context);
        String id = CHANNEL_PREFIX + (sound ? "_sound" : "") + (vibrate ? "_vibrate" : "");
        if (Build.VERSION.SDK_INT < 26) return id;

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        for (NotificationChannel channel : manager.getNotificationChannels()) {
            if (channel.getId().startsWith(CHANNEL_PREFIX) && !channel.getId().equals(id)) {
                manager.deleteNotificationChannel(channel.getId());
            }
        }
        if (manager.getNotificationChannel(id) == null) {
            NotificationChannel channel = new NotificationChannel(id,
                    context.getString(R.string.channel_messages), NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(context.getString(R.string.channel_messages_description));
            if (!sound) channel.setSound(null, null);
            channel.enableVibration(vibrate);
            manager.createNotificationChannel(channel);
        }
        return id;
    }

    /** Colored circle with initials, matching the in-app avatars. */
    private static Bitmap avatarBitmap(Context context, String name) {
        int size = Math.round(64 * context.getResources().getDisplayMetrics().density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(BaseActivity.avatarColor(name));
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(size * 0.38f);
        paint.setTextAlign(Paint.Align.CENTER);
        float baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f;
        canvas.drawText(BaseActivity.initials(name), size / 2f, baseline, paint);
        return bitmap;
    }
}
