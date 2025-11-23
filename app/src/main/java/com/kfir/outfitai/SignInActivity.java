package com.kfir.outfitai;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Toast;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.ImageButton;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SignInActivity extends AppCompatActivity {

    private HelperUserDB dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.signin_screen);

        dbHelper = new HelperUserDB(this);

        View backButton = findViewById(R.id.Back_button);
        backButton.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, 0);
        });

        View signInButton = findViewById(R.id.SignIn_button);
        signInButton.setOnClickListener(v -> {
            validateSignIn();
        });

        EditText passwordInput = findViewById(R.id.Password_textInput);
        ImageButton togglePassBtn = findViewById(R.id.btn_toggle_password_signin);
        setupPasswordToggle(passwordInput, togglePassBtn);

        setupKeyboardHandlingForForms();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
                overridePendingTransition(0, 0);
            }
        });
    }

    private void setupPasswordToggle(EditText editText, ImageButton button) {
        button.setOnClickListener(v -> {
            int selectionStart = editText.getSelectionStart();
            int selectionEnd = editText.getSelectionEnd();

            if (editText.getTransformationMethod() instanceof PasswordTransformationMethod) {
                editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                button.setImageResource(R.drawable.ic_visibility_off);
            } else {
                editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                button.setImageResource(R.drawable.ic_visibility);
            }

            editText.setSelection(selectionStart, selectionEnd);
        });
    }

    private void validateSignIn() {
        EditText emailOrUsernameInput = findViewById(R.id.UsernameOrEmail_textInput);
        EditText passwordInput = findViewById(R.id.Password_textInput);

        String emailOrUsername = emailOrUsernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        emailOrUsernameInput.setError(null);
        passwordInput.setError(null);

        if (emailOrUsername.isEmpty()) {
            emailOrUsernameInput.setError("Field can't be empty");
            return;
        }

        if (password.isEmpty()) {
            passwordInput.setError("Field can't be empty");
            return;
        }

        if (dbHelper.checkUserCredentials(emailOrUsername, password)) {
            String resolvedEmail = dbHelper.resolveEmailFromInput(emailOrUsername);

            if (resolvedEmail == null) {
                resolvedEmail = emailOrUsername;
            }

            SessionManager sessionManager = new SessionManager(this);
            sessionManager.createLoginSession(resolvedEmail);

            Toast.makeText(this, "Sign in successful!", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(SignInActivity.this, GenerateActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();

        } else {
            Toast.makeText(this, "Invalid username/email or password", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupKeyboardHandlingForForms() {
        View mainView = findViewById(R.id.main);
        final ScrollView scrollView = findViewById(R.id.scrollView);

        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImeUtils.addImeListener(mainView, isVisible -> {
            if (isVisible) {
                View focusedView = getCurrentFocus();
                if (focusedView != null && scrollView != null) {
                    scrollView.postDelayed(() -> {
                        Rect focusedRect = new Rect();
                        focusedView.getHitRect(focusedRect);
                        scrollView.requestChildRectangleOnScreen(focusedView, focusedRect, false);
                    }, 200);
                }
            }
        });
    }
}