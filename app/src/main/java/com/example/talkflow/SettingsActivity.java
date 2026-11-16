package com.example.talkflow;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends BaseActivity {

    private static final String PREFS = "talkflow_settings";
    private static final String KEY_NIGHT_MODE = "night_mode";

    /** Rows that only show a placeholder for now. */
    private static final int[] PLACEHOLDER_ROWS = {
            R.id.row_phone, R.id.row_username, R.id.row_bio,
            R.id.row_notifications, R.id.row_privacy, R.id.row_data, R.id.row_chat_settings,
            R.id.row_devices, R.id.row_language,
            R.id.row_ask_question, R.id.row_faq, R.id.row_privacy_policy,
            R.id.change_photo_fab
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setupEdgeToEdge(findViewById(R.id.app_bar), findViewById(R.id.scroll), false);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        bindAvatar(findViewById(R.id.avatar), getString(R.string.sample_profile_name));

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

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.log_out)
                .setMessage(R.string.log_out_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.log_out, (dialog, which) -> {
                    // TODO: clear the session.
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
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
