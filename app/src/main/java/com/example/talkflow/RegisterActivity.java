package com.example.talkflow;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class RegisterActivity extends BaseActivity {

    private static final int MIN_PASSWORD_LENGTH = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        setupEdgeToEdge(findViewById(R.id.toolbar), findViewById(R.id.bottom_bar), true);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        findViewById(R.id.log_in_link).setOnClickListener(v -> finish());
        findViewById(R.id.avatar_picker).setOnClickListener(v ->
                Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show());
        findViewById(R.id.register_button).setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        boolean valid = requireField(R.id.first_name_layout, R.id.first_name_input);
        valid &= requireField(R.id.phone_layout, R.id.phone_input);

        TextInputLayout passwordLayout = findViewById(R.id.password_layout);
        TextInputLayout confirmLayout = findViewById(R.id.confirm_password_layout);
        String password = text(R.id.password_input);
        String confirm = text(R.id.confirm_password_input);

        if (password.length() < MIN_PASSWORD_LENGTH) {
            passwordLayout.setError(getString(R.string.error_password_short));
            valid = false;
        } else {
            passwordLayout.setError(null);
        }
        if (!password.equals(confirm)) {
            confirmLayout.setError(getString(R.string.error_password_mismatch));
            valid = false;
        } else {
            confirmLayout.setError(null);
        }
        if (!valid) return;

        // TODO: create the account on the backend.
        Intent intent = new Intent(this, ChatListActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private boolean requireField(int layoutId, int inputId) {
        TextInputLayout layout = findViewById(layoutId);
        boolean ok = !TextUtils.isEmpty(text(inputId));
        layout.setError(ok ? null : getString(R.string.error_required));
        return ok;
    }

    private String text(int inputId) {
        TextInputEditText input = findViewById(inputId);
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
