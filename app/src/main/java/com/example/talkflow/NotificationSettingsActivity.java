package com.example.talkflow;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

/** "Notifications and Sounds": what happens when a message arrives on this device. */
public class NotificationSettingsActivity extends BaseActivity {

    private static final String PREFS = "talkflow_notifications";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_PREVIEW = "preview";
    private static final String KEY_SOUND = "sound";
    private static final String KEY_VIBRATE = "vibrate";
    private static final String KEY_PERMISSION_ASKED = "permission_asked";

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // Android stops showing the prompt after repeated denials; then only its settings can allow it.
                if (!granted && Build.VERSION.SDK_INT >= 33
                        && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    openSystemSettings();
                }
                updatePermissionWarning();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);
        setupEdgeToEdge(findViewById(R.id.app_bar), findViewById(R.id.scroll), false);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        bindSwitch(R.id.row_enabled, R.id.switch_enabled, KEY_ENABLED);
        bindSwitch(R.id.row_preview, R.id.switch_preview, KEY_PREVIEW);
        bindSwitch(R.id.row_sound, R.id.switch_sound, KEY_SOUND);
        bindSwitch(R.id.row_vibrate, R.id.switch_vibrate, KEY_VIBRATE);
        updateDependentRows();

        findViewById(R.id.row_system_settings).setOnClickListener(v -> openSystemSettings());
        findViewById(R.id.permission_warning).setOnClickListener(v -> allowNotifications());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionWarning(); // the user may have changed it in Android settings
    }

    private void bindSwitch(int rowId, int switchId, String key) {
        MaterialSwitch toggle = findViewById(switchId);
        toggle.setChecked(prefs(this).getBoolean(key, true));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            prefs(this).edit().putBoolean(key, checked).apply();
            if (KEY_SOUND.equals(key) || KEY_VIBRATE.equals(key)) {
                TalkflowMessagingService.ensureChannel(this);
            }
            updateDependentRows();
        });
        findViewById(rowId).setOnClickListener(v -> {
            if (toggle.isEnabled()) toggle.toggle();
        });
    }

    /** Preview, sound and vibrate only matter while message notifications are on. */
    private void updateDependentRows() {
        boolean enabled = isEnabled(this);
        int[][] rows = {
                {R.id.row_preview, R.id.switch_preview},
                {R.id.row_sound, R.id.switch_sound},
                {R.id.row_vibrate, R.id.switch_vibrate}};
        for (int[] row : rows) {
            findViewById(row[0]).setAlpha(enabled ? 1f : 0.4f);
            findViewById(row[0]).setEnabled(enabled);
            findViewById(row[1]).setEnabled(enabled);
        }
    }

    private void updatePermissionWarning() {
        int visibility = NotificationManagerCompat.from(this).areNotificationsEnabled()
                ? View.GONE : View.VISIBLE;
        findViewById(R.id.permission_warning).setVisibility(visibility);
        findViewById(R.id.permission_warning_gap).setVisibility(visibility);
    }

    private void allowNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            markPermissionAsked(this);
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            openSystemSettings();
        }
    }

    /** Opens Android's settings for the Messages channel (or the app on older versions). */
    private void openSystemSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= 26) {
            boolean appBlocked = !NotificationManagerCompat.from(this).areNotificationsEnabled();
            intent = new Intent(appBlocked ? Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    : Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                    .putExtra(Settings.EXTRA_CHANNEL_ID, TalkflowMessagingService.ensureChannel(this));
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", getPackageName(), null));
        }
        startActivity(intent);
    }

    static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    static boolean showPreview(Context context) {
        return prefs(context).getBoolean(KEY_PREVIEW, true);
    }

    static boolean sound(Context context) {
        return prefs(context).getBoolean(KEY_SOUND, true);
    }

    static boolean vibrate(Context context) {
        return prefs(context).getBoolean(KEY_VIBRATE, true);
    }

    static boolean permissionAlreadyAsked(Context context) {
        return prefs(context).getBoolean(KEY_PERMISSION_ASKED, false);
    }

    static void markPermissionAsked(Context context) {
        prefs(context).edit().putBoolean(KEY_PERMISSION_ASKED, true).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
