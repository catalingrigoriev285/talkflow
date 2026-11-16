package com.example.talkflow;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;

import java.util.Arrays;
import java.util.List;

public class ChatListActivity extends BaseActivity {

    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);
        setupEdgeToEdge(findViewById(R.id.app_bar), findViewById(R.id.content_root), false);

        drawerLayout = findViewById(R.id.drawer_layout);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        toolbar.setOnMenuItemClickListener(item -> {
            showComingSoon();
            return true;
        });

        setupDrawer();

        RecyclerView list = findViewById(R.id.chat_list);
        list.setAdapter(new ChatAdapter(sampleChats(), chat -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_NAME, chat.name);
            intent.putExtra(ChatActivity.EXTRA_ONLINE, chat.online);
            startActivity(intent);
        }));

        findViewById(R.id.new_message_fab).setOnClickListener(v -> showComingSoon());
    }

    private void setupDrawer() {
        NavigationView navView = findViewById(R.id.nav_view);
        navView.setNavigationItemSelectedListener(item -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            if (item.getItemId() == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else {
                showComingSoon();
            }
            return false;
        });

        View header = navView.getHeaderView(0);
        bindAvatar(header.findViewById(R.id.header_avatar), getString(R.string.sample_profile_name));
        header.findViewById(R.id.night_mode_toggle).setOnClickListener(v ->
                SettingsActivity.setNightMode(this, !isNightMode()));
        // Keep the header content below the status bar while its color extends behind it.
        final int headerTop = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), headerTop + bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        OnBackPressedCallback closeDrawer = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, closeDrawer);
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                closeDrawer.setEnabled(true);
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                closeDrawer.setEnabled(false);
            }
        });
    }

    private void showComingSoon() {
        Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
    }

    /** Placeholder data until chats come from the backend. */
    private static List<Chat> sampleChats() {
        return Arrays.asList(
                new Chat("Saved Messages", "Ideas for the Talkflow logo", "13:02", 0, false, true, false),
                new Chat("Alice Johnson", "See you tomorrow at the office!", "12:45", 2, false, false, true),
                new Chat("Design Team", "Mark: Sent the new mockups 🎨", "12:30", 14, false, false, false),
                new Chat("Mom", "Don't forget to call grandma", "11:58", 1, false, false, true),
                new Chat("Daniel Kim", "Sure, sounds good 👍", "10:21", 0, false, true, false),
                new Chat("Tech News", "Android 17 is rolling out to Pixel devices", "09:15", 128, true, false, false),
                new Chat("Elena Popescu", "Photo", "Yesterday", 0, false, true, true),
                new Chat("Book Club", "Sarah: Next book is \"Dune\"", "Yesterday", 5, true, false, false),
                new Chat("Victor Stone", "Thanks for the help!", "Mon", 0, false, false, false),
                new Chat("Maria Garcia", "Voice message", "Sun", 0, false, true, false),
                new Chat("Weekend Trip", "Liam: I'll bring the tent", "Sat", 0, true, false, false),
                new Chat("Oliver Brown", "Happy birthday! 🎉", "12.11.26", 0, false, true, false)
        );
    }

    static final class Chat {
        final String name;
        final String lastMessage;
        final String time;
        final int unread;
        final boolean muted;
        final boolean fromMe;
        final boolean online;

        Chat(String name, String lastMessage, String time, int unread,
             boolean muted, boolean fromMe, boolean online) {
            this.name = name;
            this.lastMessage = lastMessage;
            this.time = time;
            this.unread = unread;
            this.muted = muted;
            this.fromMe = fromMe;
            this.online = online;
        }
    }

    interface OnChatClickListener {
        void onChatClick(Chat chat);
    }

    private static final class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.Holder> {
        private final List<Chat> chats;
        private final OnChatClickListener listener;

        ChatAdapter(List<Chat> chats, OnChatClickListener listener) {
            this.chats = chats;
            this.listener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Chat chat = chats.get(position);
            bindAvatar(h.avatar, chat.name);
            h.name.setText(chat.name);
            h.message.setText(chat.lastMessage);
            h.time.setText(chat.time);
            h.onlineDot.setVisibility(chat.online ? View.VISIBLE : View.GONE);
            h.readStatus.setVisibility(chat.fromMe ? View.VISIBLE : View.GONE);
            if (chat.unread > 0) {
                h.badge.setVisibility(View.VISIBLE);
                h.badge.setText(String.valueOf(chat.unread));
                int color = ContextCompat.getColor(h.itemView.getContext(),
                        chat.muted ? R.color.tf_badge_muted : R.color.tf_badge);
                h.badge.setBackgroundTintList(ColorStateList.valueOf(color));
            } else {
                h.badge.setVisibility(View.GONE);
            }
            h.itemView.setOnClickListener(v -> listener.onChatClick(chat));
        }

        @Override
        public int getItemCount() {
            return chats.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView avatar, name, message, time, badge;
            final View onlineDot, readStatus;

            Holder(View v) {
                super(v);
                avatar = v.findViewById(R.id.avatar);
                name = v.findViewById(R.id.name);
                message = v.findViewById(R.id.message);
                time = v.findViewById(R.id.time);
                badge = v.findViewById(R.id.unread_badge);
                onlineDot = v.findViewById(R.id.online_dot);
                readStatus = v.findViewById(R.id.read_status);
            }
        }
    }
}
