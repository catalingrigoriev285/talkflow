package com.example.talkflow;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class LoginActivity extends BaseActivity {

    private TextInputLayout loginLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText loginInput;
    private TextInputEditText passwordInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setupEdgeToEdge(findViewById(R.id.scroll), findViewById(R.id.bottom_bar), true);

        loginLayout = findViewById(R.id.login_layout);
        passwordLayout = findViewById(R.id.password_layout);
        loginInput = findViewById(R.id.login_input);
        passwordInput = findViewById(R.id.password_input);

        findViewById(R.id.login_button).setOnClickListener(v -> attemptLogin());
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
        findViewById(R.id.sign_up_link).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        findViewById(R.id.forgot_password).setOnClickListener(v ->
                Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show());
    }

    private void attemptLogin() {
        String login = text(loginInput);
        String password = text(passwordInput);
        loginLayout.setError(TextUtils.isEmpty(login) ? getString(R.string.error_required) : null);
        passwordLayout.setError(TextUtils.isEmpty(password) ? getString(R.string.error_required) : null);
        if (TextUtils.isEmpty(login) || TextUtils.isEmpty(password)) return;

        // TODO: authenticate against the backend.
        Intent intent = new Intent(this, ChatListActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
