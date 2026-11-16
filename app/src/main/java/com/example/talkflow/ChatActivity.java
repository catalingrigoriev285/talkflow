package com.example.talkflow;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatActivity extends BaseActivity {

    public static final String EXTRA_CHAT_ID = "com.example.talkflow.extra.CHAT_ID";
    public static final String EXTRA_NAME = "com.example.talkflow.extra.NAME";

    /** Chat currently on screen; its notifications are skipped. */
    static volatile String openChatId;

    /** How many recent messages to keep live; older history can be paged in later. */
    private static final int MESSAGE_LIMIT = 300;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<Message> messages = new ArrayList<>();
    private final List<DocumentSnapshot> messageDocs = new ArrayList<>();

    private FirebaseUser user;
    private String otherUid;
    private DocumentReference chatRef;
    private long otherUnread;

    private MessageAdapter adapter;
    private RecyclerView messageList;
    private EditText input;
    private ImageButton sendButton;
    private ListenerRegistration messagesListener;
    private ListenerRegistration chatListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = requireUser();
        if (user == null) return;
        String chatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
        if (chatId == null) {
            finish();
            return;
        }
        chatRef = db.collection(CHATS).document(chatId);
        otherUid = otherMember(chatId, user.getUid());

        String name = getIntent().getStringExtra(EXTRA_NAME);
        if (name == null) name = getString(R.string.app_name);

        setContentView(R.layout.activity_chat);
        setupEdgeToEdge(findViewById(R.id.app_bar), findViewById(R.id.input_bar), false);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            showComingSoon();
            return true;
        });
        bindAvatar(findViewById(R.id.avatar), name);
        ((TextView) findViewById(R.id.chat_name)).setText(name);
        ((TextView) findViewById(R.id.chat_status)).setText(R.string.status_last_seen);
        findViewById(R.id.chat_header).setOnClickListener(v -> showComingSoon());

        adapter = new MessageAdapter(messages);
        messageList = findViewById(R.id.messages);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        messageList.setLayoutManager(layoutManager);
        messageList.setAdapter(adapter);

        input = findViewById(R.id.message_input);
        sendButton = findViewById(R.id.send_button);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSendButton(!TextUtils.isEmpty(s.toString().trim()));
            }
        });
        sendButton.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                showComingSoon(); // voice messages
            } else {
                sendMessage(text);
            }
        });
        findViewById(R.id.emoji_button).setOnClickListener(v -> showComingSoon());
        findViewById(R.id.attach_button).setOnClickListener(v -> showComingSoon());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (user == null || chatRef == null) return;
        openChatId = chatRef.getId();
        TalkflowMessagingService.cancelChatNotification(this, chatRef.getId());

        // Live messages: Firestore keeps a streaming connection open and pushes new
        // messages from either side as soon as they are written.
        messagesListener = chatRef.collection(MESSAGES)
                .orderBy("createdAt")
                .limitToLast(MESSAGE_LIMIT)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Toast.makeText(this, authErrorMessage(this, e), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snapshot == null) return;
                    messageDocs.clear();
                    messageDocs.addAll(snapshot.getDocuments());
                    messageDocs.sort((a, b) -> Long.compare(sentAt(a), sentAt(b)));
                    rebuildMessages();
                });

        // The chat document carries unread counters: reset ours while the chat is open,
        // and use the other person's to show read ticks.
        chatListener = chatRef.addSnapshotListener((doc, e) -> {
            if (doc == null || !doc.exists()) return;
            if (unreadCount(doc, user.getUid()) > 0) {
                chatRef.update(FieldPath.of("unread", user.getUid()), 0);
            }
            long previous = otherUnread;
            otherUnread = unreadCount(doc, otherUid);
            if (previous != otherUnread) rebuildMessages();
        });
    }

    @Override
    protected void onStop() {
        if (chatRef != null && chatRef.getId().equals(openChatId)) openChatId = null;
        if (messagesListener != null) messagesListener.remove();
        if (chatListener != null) chatListener.remove();
        super.onStop();
    }

    /** Turns message documents into rows with date separators and read ticks. */
    private void rebuildMessages() {
        int oldCount = messages.size();
        List<Message> rows = new ArrayList<>();
        Calendar lastDay = null;
        // My last `otherUnread` messages have not been seen yet; earlier ones have.
        int myMessagesFromEnd = 0;
        boolean[] readFlags = new boolean[messageDocs.size()];
        for (int i = messageDocs.size() - 1; i >= 0; i--) {
            if (user.getUid().equals(messageDocs.get(i).getString("senderId"))) {
                myMessagesFromEnd++;
                readFlags[i] = myMessagesFromEnd > otherUnread;
            }
        }
        for (int i = 0; i < messageDocs.size(); i++) {
            DocumentSnapshot doc = messageDocs.get(i);
            Date date = doc.getDate("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
            if (date == null) date = new Date();
            Calendar day = Calendar.getInstance();
            day.setTime(date);
            if (lastDay == null || !sameDay(lastDay, day)) {
                rows.add(new Message(Message.DATE, formatDay(date), null, false));
                lastDay = day;
            }
            boolean mine = user.getUid().equals(doc.getString("senderId"));
            rows.add(new Message(mine ? Message.OUT : Message.IN, doc.getString("text"),
                    TIME_FORMAT.get().format(date), readFlags[i]));
        }
        messages.clear();
        messages.addAll(rows);
        adapter.notifyDataSetChanged();
        if (messages.size() > oldCount) {
            messageList.scrollToPosition(messages.size() - 1);
        }
    }

    /** Telegram swaps the mic for a blue send arrow while there is text to send. */
    private void updateSendButton(boolean hasText) {
        sendButton.setImageResource(hasText ? R.drawable.ic_send : R.drawable.ic_mic);
        sendButton.setContentDescription(getString(hasText ? R.string.send : R.string.record_voice));
        int color = ContextCompat.getColor(this, hasText ? R.color.tf_primary : R.color.tf_icon);
        ImageViewCompat.setImageTintList(sendButton, ColorStateList.valueOf(color));
    }

    /**
     * Writes the message and updates the chat preview in one batch. The message shows
     * up instantly from the local cache and syncs when the device is online.
     */
    private void sendMessage(String text) {
        Map<String, Object> message = new HashMap<>();
        message.put("senderId", user.getUid());
        message.put("text", text);
        message.put("createdAt", FieldValue.serverTimestamp());

        Map<String, Object> unread = new HashMap<>();
        unread.put(otherUid, FieldValue.increment(1));
        Map<String, Object> chat = new HashMap<>();
        chat.put("members", membersFor(user.getUid(), otherUid));
        chat.put("lastMessage", text);
        chat.put("lastMessageAt", FieldValue.serverTimestamp());
        chat.put("lastSenderId", user.getUid());
        chat.put("unread", unread);

        WriteBatch batch = db.batch();
        batch.set(chatRef.collection(MESSAGES).document(), message);
        batch.set(chatRef, chat, SetOptions.merge());
        batch.commit().addOnFailureListener(e ->
                Toast.makeText(this, authErrorMessage(this, e), Toast.LENGTH_LONG).show());
        input.setText("");
    }

    private void showComingSoon() {
        Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
    }

    private static String otherMember(String chatId, String myUid) {
        String[] uids = chatId.split("_");
        if (uids.length != 2) return myUid;
        return uids[0].equals(myUid) ? uids[1] : uids[0];
    }

    @SuppressWarnings("unchecked")
    private static long unreadCount(DocumentSnapshot chat, String uid) {
        Map<String, Object> unread = (Map<String, Object>) chat.get("unread");
        Object v = unread == null ? null : unread.get(uid);
        return v instanceof Number ? ((Number) v).longValue() : 0;
    }

    private static long sentAt(DocumentSnapshot doc) {
        Date date = doc.getDate("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        return date == null ? Long.MAX_VALUE : date.getTime();
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private String formatDay(Date date) {
        if (DateUtils.isToday(date.getTime())) return getString(R.string.date_today);
        return DateFormat.getDateInstance(DateFormat.LONG).format(date);
    }

    private static final ThreadLocal<SimpleDateFormat> TIME_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("HH:mm", Locale.getDefault()));

    static final class Message {
        static final int DATE = 0;
        static final int IN = 1;
        static final int OUT = 2;

        final int type;
        final String text;
        final String time;
        final boolean read;

        Message(int type, String text, String time, boolean read) {
            this.type = type;
            this.text = text;
            this.time = time;
            this.read = read;
        }
    }

    private static final class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {
        private final List<Message> messages;

        MessageAdapter(List<Message> messages) {
            this.messages = messages;
        }

        @Override
        public int getItemViewType(int position) {
            return messages.get(position).type;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout;
            if (viewType == Message.DATE) {
                layout = R.layout.item_message_date;
            } else if (viewType == Message.OUT) {
                layout = R.layout.item_message_out;
            } else {
                layout = R.layout.item_message_in;
            }
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Message m = messages.get(position);
            h.text.setText(m.text);
            if (h.time != null) h.time.setText(m.time);
            if (h.status != null) h.status.setImageResource(m.read ? R.drawable.ic_done_all : R.drawable.ic_check);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView text;
            final TextView time;
            final ImageView status;

            Holder(View v) {
                super(v);
                text = v.findViewById(R.id.message_text);
                time = v.findViewById(R.id.message_time);
                status = v.findViewById(R.id.message_status);
            }
        }
    }
}
