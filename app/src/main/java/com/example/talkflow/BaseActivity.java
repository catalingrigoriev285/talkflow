package com.example.talkflow;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared base for every Talkflow screen: edge-to-edge window insets,
 * Telegram-style colored initials avatars and Firebase helpers.
 *
 * Firestore layout:
 * <pre>
 * users/{uid}                    name, email (lowercase), phone, createdAt
 * chats/{chatId}                 members [uid, uid], names {uid: name}, lastMessage,
 *                                lastMessageAt, lastSenderId, unread {uid: count}
 * chats/{chatId}/messages/{id}   senderId, text, createdAt
 * </pre>
 */
public abstract class BaseActivity extends AppCompatActivity {

    static final String USERS = "users";
    static final String CHATS = "chats";
    static final String MESSAGES = "messages";

    /** Telegram's classic avatar palette. */
    private static final int[] AVATAR_COLORS = {
            0xFFE17076, 0xFFFAA774, 0xFFA695E7, 0xFF7BC862,
            0xFF6EC9CB, 0xFF65AADD, 0xFFEE7AAE
    };

    /**
     * Draws the app behind the system bars and pads the given views so content stays visible.
     *
     * @param topView        receives the status bar height as extra top padding (usually the app bar)
     * @param bottomView     receives the navigation bar / keyboard height as extra bottom padding
     * @param lightStatusBar true when the screen behind the status bar is light (dark icons)
     */
    protected void setupEdgeToEdge(@Nullable View topView, @Nullable View bottomView, boolean lightStatusBar) {
        WindowCompat.enableEdgeToEdge(getWindow());
        boolean night = isNightMode();
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(lightStatusBar && !night);
        controller.setAppearanceLightNavigationBars(!night);

        final int topPadding = topView != null ? topView.getPaddingTop() : 0;
        final int bottomPadding = bottomView != null ? bottomView.getPaddingBottom() : 0;
        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, 0, bars.right, 0);
            if (topView != null) {
                topView.setPadding(topView.getPaddingLeft(), topPadding + bars.top,
                        topView.getPaddingRight(), topView.getPaddingBottom());
            }
            if (bottomView != null) {
                bottomView.setPadding(bottomView.getPaddingLeft(), bottomView.getPaddingTop(),
                        bottomView.getPaddingRight(), bottomPadding + Math.max(bars.bottom, ime.bottom));
            }
            return insets;
        });
    }

    protected boolean isNightMode() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Returns the signed-in user, or sends the user to the login screen and returns null.
     * Screens that need an account call this first in onCreate.
     */
    @Nullable
    protected FirebaseUser requireUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            openClearingTask(LoginActivity.class);
        }
        return user;
    }

    /**
     * Makes sure users/{uid} exists with the sign-in email, so other people can find this
     * account by email. Repairs accounts whose profile was never saved, for example ones
     * created before Firestore or its rules were set up. Never overwrites the phone number.
     */
    protected void ensureProfile(FirebaseUser user) {
        String email = user.getEmail() == null ? "" : user.getEmail().toLowerCase(Locale.ROOT);
        if (email.isEmpty()) return;
        DocumentReference ref = FirebaseFirestore.getInstance().collection(USERS).document(user.getUid());
        ref.get().addOnSuccessListener(doc -> {
            Map<String, Object> fixes = new HashMap<>();
            if (!email.equals(doc.getString("email"))) fixes.put("email", email);
            String name = doc.getString("name");
            if (name == null || name.trim().isEmpty()) {
                String displayName = user.getDisplayName();
                fixes.put("name", displayName == null || displayName.trim().isEmpty()
                        ? email.substring(0, email.indexOf('@') > 0 ? email.indexOf('@') : email.length())
                        : displayName);
            }
            if (!doc.exists()) fixes.put("createdAt", FieldValue.serverTimestamp());
            if (fixes.isEmpty()) return;
            if (!fixes.containsKey("email")) fixes.put("email", email); // rules check it on every write
            ref.set(fixes, SetOptions.merge()).addOnFailureListener(e ->
                    Toast.makeText(this, authErrorMessage(this, e), Toast.LENGTH_LONG).show());
        }).addOnFailureListener(e ->
                Toast.makeText(this, authErrorMessage(this, e), Toast.LENGTH_LONG).show());
    }

    /** Opens a screen as the only one in the back stack (after login, register or logout). */
    protected void openClearingTask(Class<?> activity) {
        Intent intent = new Intent(this, activity);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /** One chat per pair of users: the id is both uids sorted and joined with "_". */
    static String chatIdFor(String uidA, String uidB) {
        List<String> members = membersFor(uidA, uidB);
        return members.get(0) + "_" + members.get(1);
    }

    /** Chat members, always sorted so security rules can match them against the chat id. */
    static List<String> membersFor(String uidA, String uidB) {
        return uidA.compareTo(uidB) < 0 ? Arrays.asList(uidA, uidB) : Arrays.asList(uidB, uidA);
    }

    /** Turns Firebase errors into short messages for the user. */
    static String authErrorMessage(Context context, Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return context.getString(R.string.error_network);
        } else if (e instanceof FirebaseAuthUserCollisionException) {
            return context.getString(R.string.error_email_in_use);
        } else if (e instanceof FirebaseAuthWeakPasswordException) {
            return context.getString(R.string.error_weak_password);
        } else if (e instanceof FirebaseAuthInvalidUserException
                || e instanceof FirebaseAuthInvalidCredentialsException) {
            return context.getString(R.string.error_wrong_credentials);
        }
        return context.getString(R.string.error_generic, e.getLocalizedMessage());
    }

    /** Chat list style: "14:05" today, "Mon" this week, "12.11.26" otherwise. */
    static String formatChatTime(@Nullable Date date) {
        if (date == null) return "";
        long millis = date.getTime();
        long age = System.currentTimeMillis() - millis;
        String pattern;
        if (DateUtils.isToday(millis)) {
            pattern = "HH:mm";
        } else if (age < 6 * DateUtils.DAY_IN_MILLIS) {
            pattern = "EEE";
        } else {
            pattern = "dd.MM.yy";
        }
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(date);
    }

    /** Shows the user's initials on a circle colored consistently from their name. */
    static void bindAvatar(TextView view, String name) {
        view.setText(initials(name));
        view.setBackgroundTintList(ColorStateList.valueOf(avatarColor(name)));
    }

    static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "?";
        StringBuilder sb = new StringBuilder();
        sb.appendCodePoint(parts[0].codePointAt(0));
        if (parts.length > 1) sb.appendCodePoint(parts[parts.length - 1].codePointAt(0));
        return sb.toString().toUpperCase(Locale.getDefault());
    }

    static int avatarColor(String name) {
        return AVATAR_COLORS[Math.abs(name.hashCode() % AVATAR_COLORS.length)];
    }
}
