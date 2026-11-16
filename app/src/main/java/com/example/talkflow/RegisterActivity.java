package com.example.talkflow;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class RegisterActivity extends BaseActivity {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private View registerButton;

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
        registerButton = findViewById(R.id.register_button);
        registerButton.setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        boolean valid = requireField(R.id.first_name_layout, R.id.first_name_input);

        TextInputLayout emailLayout = findViewById(R.id.email_layout);
        String email = text(R.id.email_input).toLowerCase(Locale.ROOT);
        boolean emailOk = Patterns.EMAIL_ADDRESS.matcher(email).matches();
        emailLayout.setError(emailOk ? null : getString(R.string.error_invalid_email));
        valid &= emailOk;

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

        String name = (text(R.id.first_name_input) + " " + text(R.id.last_name_input)).trim();
        String phone = text(R.id.phone_input);

        registerButton.setEnabled(false);
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> saveProfile(result.getUser(), name, email, phone))
                .addOnFailureListener(e -> {
                    registerButton.setEnabled(true);
                    TextInputLayout target = e instanceof FirebaseAuthWeakPasswordException
                            ? passwordLayout : emailLayout;
                    target.setError(authErrorMessage(this, e));
                });
    }

    /** Stores the public profile other users see and search by email. */
    private void saveProfile(FirebaseUser user, String name, String email, String phone) {
        user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(name).build());

        Map<String, Object> profile = new HashMap<>();
        profile.put("name", name);
        profile.put("email", email);
        profile.put("phone", phone);
        profile.put("createdAt", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection(USERS).document(user.getUid()).set(profile)
                .addOnSuccessListener(v -> openClearingTask(ChatListActivity.class))
                .addOnFailureListener(e -> {
                    registerButton.setEnabled(true);
                    Toast.makeText(this, authErrorMessage(this, e), Toast.LENGTH_LONG).show();
                });
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
