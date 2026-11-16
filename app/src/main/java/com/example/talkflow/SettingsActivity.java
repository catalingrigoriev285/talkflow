package com.example.talkflow;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class SettingsActivity extends BaseActivity {

    private static final String PREFS = "talkflow_settings";
    private static final String KEY_NIGHT_MODE = "night_mode";

    /** Rows that only show a placeholder for now. */
    private static final int[] PLACEHOLDER_ROWS = {
            R.id.row_phone, R.id.row_email, R.id.row_bio,
            R.id.row_notifications, R.id.row_privacy, R.id.row_data, R.id.row_chat_settings,
            R.id.row_devices, R.id.row_language,
            R.id.row_ask_question, R.id.row_faq, R.id.row_privacy_policy,
            R.id.change_photo_fab
    };

    private FirebaseUser user;
    private ListenerRegistration profileListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        user = requireUser();
        if (user == null) return;
        setContentView(R.layout.activity_settings);
        setupEdgeToEdge(findViewById(R.id.app_bar), findViewById(R.id.scroll), false);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        showProfile(user.getDisplayName(), user.getEmail(), null);

        for (int id : PLACEHOLDER_ROWS) {
            findViewById(id).setOnClickListener(v ->
                    Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show());
        }

        MaterialSwitch nightSwitch = findViewById(R.id.switch_night_mode);
        nightSwitch.setChecked(isNightMode());
        nightSwitch.setOnCheckedChangeListener((button, checked) -> setNightMode(this, checked));
        findViewById(R.id.row_night_mode).setOnClickListener(v -> nightSwitch.toggle());

        findViewById(R.id.row_logout).setOnClickListener(v -> confirmLogout());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (user == null) return;
        profileListener = FirebaseFirestore.getInstance().collection(USERS).document(user.getUid())
                .addSnapshotListener((doc, e) -> {
                    if (doc != null && doc.exists()) {
                        showProfile(doc.getString("name"), doc.getString("email"), doc.getString("phone"));
                    }
                });
    }

    @Override
    protected void onStop() {
        if (profileListener != null) profileListener.remove();
        super.onStop();
    }

    private void showProfile(String name, String email, String phone) {
        String displayName = TextUtils.isEmpty(name) ? (email == null ? "?" : email) : name;
        bindAvatar(findViewById(R.id.avatar), displayName);
        ((TextView) findViewById(R.id.profile_name)).setText(displayName);
        ((TextView) findViewById(R.id.email_value)).setText(email);
        ((TextView) findViewById(R.id.phone_value)).setText(TextUtils.isEmpty(phone) ? "—" : phone);
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.log_out)
                .setMessage(R.string.log_out_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.log_out, (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    openClearingTask(LoginActivity.class);
                })
                .show();
    }

    /** Restores the night mode chosen in Settings; call before the first activity is created. */
    static void applySavedNightMode(Context context) {
        int mode = prefs(context).getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    static void setNightMode(Context context, boolean enabled) {
        int mode = enabled ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        prefs(context).edit().putInt(KEY_NIGHT_MODE, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
