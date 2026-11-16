package com.example.talkflow;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends BaseActivity {

    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private View loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setupEdgeToEdge(findViewById(R.id.scroll), findViewById(R.id.bottom_bar), true);

        emailLayout = findViewById(R.id.login_layout);
        passwordLayout = findViewById(R.id.password_layout);
        emailInput = findViewById(R.id.login_input);
        passwordInput = findViewById(R.id.password_input);
        loginButton = findViewById(R.id.login_button);

        loginButton.setOnClickListener(v -> attemptLogin());
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
        findViewById(R.id.sign_up_link).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        findViewById(R.id.forgot_password).setOnClickListener(v -> sendPasswordReset());
    }

    private void attemptLogin() {
        String email = text(emailInput);
        String password = text(passwordInput);
        boolean emailOk = Patterns.EMAIL_ADDRESS.matcher(email).matches();
        emailLayout.setError(emailOk ? null : getString(R.string.error_invalid_email));
        passwordLayout.setError(TextUtils.isEmpty(password) ? getString(R.string.error_required) : null);
        if (!emailOk || TextUtils.isEmpty(password)) return;

        loginButton.setEnabled(false);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> openClearingTask(ChatListActivity.class))
                .addOnFailureListener(e -> {
                    loginButton.setEnabled(true);
                    passwordLayout.setError(authErrorMessage(this, e));
                });
    }

    private void sendPasswordReset() {
        String email = text(emailInput);
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError(getString(R.string.error_enter_email_first));
            return;
        }
        emailLayout.setError(null);
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(v -> Toast.makeText(this,
                        getString(R.string.reset_email_sent, email), Toast.LENGTH_LONG).show())
                .addOnFailureListener(e -> emailLayout.setError(authErrorMessage(this, e)));
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
