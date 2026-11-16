package com.example.talkflow;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends BaseActivity {

    public static final String EXTRA_NAME = "com.example.talkflow.extra.NAME";
    public static final String EXTRA_ONLINE = "com.example.talkflow.extra.ONLINE";

    private final List<Message> messages = new ArrayList<>();
    private MessageAdapter adapter;
    private RecyclerView messageList;
    private EditText input;
    private ImageButton sendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        setupEdgeToEdge(findViewById(R.id.app_bar), findViewById(R.id.input_bar), false);

        String name = getIntent().getStringExtra(EXTRA_NAME);
        if (name == null) name = "Talkflow";
        boolean online = getIntent().getBooleanExtra(EXTRA_ONLINE, false);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            showComingSoon();
            return true;
        });
        bindAvatar(findViewById(R.id.avatar), name);
        ((TextView) findViewById(R.id.chat_name)).setText(name);
        ((TextView) findViewById(R.id.chat_status))
                .setText(online ? R.string.status_online : R.string.status_last_seen);
        findViewById(R.id.chat_header).setOnClickListener(v -> showComingSoon());

        messages.addAll(sampleMessages());
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

    /** Telegram swaps the mic for a blue send arrow while there is text to send. */
    private void updateSendButton(boolean hasText) {
        sendButton.setImageResource(hasText ? R.drawable.ic_send : R.drawable.ic_mic);
        sendButton.setContentDescription(getString(hasText ? R.string.send : R.string.record_voice));
        int color = ContextCompat.getColor(this, hasText ? R.color.tf_primary : R.color.tf_icon);
        ImageViewCompat.setImageTintList(sendButton, ColorStateList.valueOf(color));
    }

    private void sendMessage(String text) {
        // TODO: send through the backend.
        messages.add(new Message(Message.OUT, text, now()));
        adapter.notifyItemInserted(messages.size() - 1);
        messageList.scrollToPosition(messages.size() - 1);
        input.setText("");
    }

    private void showComingSoon() {
        Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }

    private List<Message> sampleMessages() {
        List<Message> list = new ArrayList<>();
        list.add(new Message(Message.DATE, getString(R.string.date_today), null));
        list.add(new Message(Message.IN, "Hey! How is the new app going?", "12:30"));
        list.add(new Message(Message.OUT, "Pretty good! Just finished the chat screen templates.", "12:31"));
        list.add(new Message(Message.IN, "Nice, can I see a preview?", "12:33"));
        list.add(new Message(Message.OUT, "Sure, I'll send you a build tonight 🚀", "12:34"));
        list.add(new Message(Message.IN, "Awesome. Does it have dark mode?", "12:40"));
        list.add(new Message(Message.OUT, "Yes, toggle it from the moon icon in the side menu or in Settings.", "12:41"));
        list.add(new Message(Message.IN, "See you tomorrow at the office!", "12:45"));
        return list;
    }

    static final class Message {
        static final int DATE = 0;
        static final int IN = 1;
        static final int OUT = 2;

        final int type;
        final String text;
        final String time;

        Message(int type, String text, String time) {
            this.type = type;
            this.text = text;
            this.time = time;
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
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView text;
            final TextView time;

            Holder(View v) {
                super(v);
                text = v.findViewById(R.id.message_text);
                time = v.findViewById(R.id.message_time);
            }
        }
    }
}
