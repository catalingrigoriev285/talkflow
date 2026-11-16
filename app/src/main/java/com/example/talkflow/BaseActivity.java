package com.example.talkflow;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Locale;

/**
 * Shared base for every Talkflow screen: edge-to-edge window insets and
 * Telegram-style colored initials avatars.
 */
public abstract class BaseActivity extends AppCompatActivity {

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
