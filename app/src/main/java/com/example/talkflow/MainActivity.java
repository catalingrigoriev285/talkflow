package com.example.talkflow;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

/** Splash screen: shows the app name briefly, then opens the chats (signed in) or login. */
public class MainActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 1200;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openLogin = () -> {
        boolean signedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        startActivity(new Intent(this, signedIn ? ChatListActivity.class : LoginActivity.class));
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsActivity.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        handler.postDelayed(openLogin, SPLASH_DELAY_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(openLogin);
        super.onDestroy();
    }
}
