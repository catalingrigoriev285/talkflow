package com.example.talkflow;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatListActivity extends BaseActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<Chat> chats = new ArrayList<>();

    private FirebaseUser user;
    private String myName;
    private DrawerLayout drawerLayout;
    private ChatAdapter adapter;
    private View emptyView;
    private ListenerRegistration chatsListener;
    private ListenerRegistration profileListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = requireUser();
        if (user == null) return;
        myName = user.getDisplayName();

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

        emptyView = findViewById(R.id.empty_view);
        adapter = new ChatAdapter(chats, chat -> openChat(chat.id, chat.name));
        RecyclerView list = findViewById(R.id.chat_list);
        list.setAdapter(adapter);

        findViewById(R.id.new_message_fab).setOnClickListener(v -> showNewChatDialog());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (user == null) return;
        listenToChats();
        listenToProfile();
    }

    @Override
    protected void onStop() {
        if (chatsListener != null) chatsListener.remove();
        if (profileListener != null) profileListener.remove();
        super.onStop();
    }

    /**
     * Live chat list: Firestore pushes every change to chats this user is a member of.
     * Sorted on the device so no composite index is needed.
     */
    private void listenToChats() {
        String uid = user.getUid();
        chatsListener = db.collection(CHATS)
                .whereArrayContains("members", uid)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Toast.makeText(this, authErrorMessage(this, e), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snapshot == null) return;
                    chats.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        chats.add(Chat.from(doc, uid));
                    }
                    chats.sort((a, b) -> Long.compare(b.sortKey(), a.sortKey()));
                    adapter.notifyDataSetChanged();
                    emptyView.setVisibility(chats.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private void listenToProfile() {
        profileListener = db.collection(USERS).document(user.getUid())
                .addSnapshotListener((doc, e) -> {
                    if (doc != null && doc.exists() && !TextUtils.isEmpty(doc.getString("name"))) {
                        myName = doc.getString("name");
                        bindDrawerHeader();
                    }
                });
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
        bindDrawerHeader();
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

    private void bindDrawerHeader() {
        View header = ((NavigationView) findViewById(R.id.nav_view)).getHeaderView(0);
        String name = TextUtils.isEmpty(myName) ? user.getEmail() : myName;
        bindAvatar(header.findViewById(R.id.header_avatar), name);
        ((TextView) header.findViewById(R.id.header_name)).setText(name);
        ((TextView) header.findViewById(R.id.header_email)).setText(user.getEmail());
    }

    /** Asks for a friend's email, finds their account and opens (or creates) the chat. */
    private void showNewChatDialog() {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(getString(R.string.new_chat_hint));
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setSingleLine(true);
        layout.addView(input);
        FrameLayout container = new FrameLayout(this);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 3, pad, 0);
        container.addView(layout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.new_message)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.start_chat, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = input.getText() == null ? ""
                    : input.getText().toString().trim().toLowerCase(Locale.ROOT);
            if (email.isEmpty()) {
                layout.setError(getString(R.string.error_required));
            } else if (email.equalsIgnoreCase(user.getEmail())) {
                layout.setError(getString(R.string.cant_chat_with_self));
            } else {
                layout.setError(null);
                startChatWith(email, dialog, layout);
            }
        }));
        dialog.show();
    }

    private void startChatWith(String email, AlertDialog dialog, TextInputLayout layout) {
        db.collection(USERS).whereEqualTo("email", email).limit(1).get()
                .addOnSuccessListener(result -> {
                    if (result.isEmpty()) {
                        layout.setError(getString(R.string.user_not_found));
                        return;
                    }
                    DocumentSnapshot other = result.getDocuments().get(0);
                    String otherName = other.getString("name");
                    if (TextUtils.isEmpty(otherName)) otherName = email;
                    String chatId = chatIdFor(user.getUid(), other.getId());

                    Map<String, Object> names = new HashMap<>();
                    names.put(user.getUid(), TextUtils.isEmpty(myName) ? user.getEmail() : myName);
                    names.put(other.getId(), otherName);
                    Map<String, Object> chat = new HashMap<>();
                    chat.put("members", membersFor(user.getUid(), other.getId()));
                    chat.put("names", names);
                    // Merge so an existing chat keeps its messages and counters.
                    db.collection(CHATS).document(chatId).set(chat, SetOptions.merge())
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, authErrorMessage(this, e), Toast.LENGTH_LONG).show());

                    dialog.dismiss();
                    openChat(chatId, otherName);
                })
                .addOnFailureListener(e -> layout.setError(authErrorMessage(this, e)));
    }

    private void openChat(String chatId, String name) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CHAT_ID, chatId);
        intent.putExtra(ChatActivity.EXTRA_NAME, name);
        startActivity(intent);
    }

    private void showComingSoon() {
        Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
    }

    static final class Chat {
        String id;
        String name;
        String lastMessage;
        Date lastAt;
        long unread;
        boolean fromMe;
        boolean readByOther;

        /** Builds a row from a chats/{id} document as seen by {@code myUid}. */
        @SuppressWarnings("unchecked")
        static Chat from(DocumentSnapshot doc, String myUid) {
            Chat c = new Chat();
            c.id = doc.getId();
            String otherUid = myUid;
            List<String> members = (List<String>) doc.get("members");
            if (members != null) {
                for (String m : members) if (!m.equals(myUid)) otherUid = m;
            }
            Map<String, Object> names = (Map<String, Object>) doc.get("names");
            Object name = names == null ? null : names.get(otherUid);
            c.name = name == null ? "Talkflow user" : name.toString();
            c.lastMessage = doc.getString("lastMessage");
            c.lastAt = doc.getDate("lastMessageAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
            c.fromMe = myUid.equals(doc.getString("lastSenderId"));
            Map<String, Object> unread = (Map<String, Object>) doc.get("unread");
            c.unread = count(unread, myUid);
            c.readByOther = count(unread, otherUid) == 0;
            return c;
        }

        private static long count(Map<String, Object> unread, String uid) {
            Object v = unread == null ? null : unread.get(uid);
            return v instanceof Number ? ((Number) v).longValue() : 0;
        }

        long sortKey() {
            return lastAt == null ? Long.MAX_VALUE : lastAt.getTime(); // brand-new chats on top
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
            h.message.setText(chat.lastMessage == null ? "" : chat.lastMessage);
            h.time.setText(formatChatTime(chat.lastAt));
            h.onlineDot.setVisibility(View.GONE); // presence needs Realtime Database; not wired yet
            h.readStatus.setVisibility(chat.fromMe ? View.VISIBLE : View.GONE);
            h.readStatus.setImageResource(chat.readByOther ? R.drawable.ic_done_all : R.drawable.ic_check);
            if (chat.unread > 0) {
                h.badge.setVisibility(View.VISIBLE);
                h.badge.setText(String.valueOf(chat.unread));
                h.badge.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(h.itemView.getContext(), R.color.tf_badge)));
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
            final View onlineDot;
            final ImageView readStatus;

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
