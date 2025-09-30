package com.kfir.outfitai;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SignUpActivity extends AppCompatActivity {

    private HelperUserDB dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.signup_screen);

        dbHelper = new HelperUserDB(this);

        View backButton = findViewById(R.id.Back_button);
        backButton.setOnClickListener(v -> {
            finish(); // Go back to the previous screen
            overridePendingTransition(0, 0); // Disable animation
        });

        View signUpButton = findViewById(R.id.SignUp_button);
        signUpButton.setOnClickListener(v -> validateSignUp());

        setupKeyboardHandlingForForms();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
                overridePendingTransition(0, 0); // Disable animation
            }
        });
    }

    private void validateSignUp() {
        EditText usernameInput = findViewById(R.id.Username_textInput);
        EditText emailInput = findViewById(R.id.Email_textInput);
        EditText passwordInput = findViewById(R.id.Password_textInput);
        EditText confirmPasswordInput = findViewById(R.id.ConfirmPassword_textInput);

        String username = usernameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        usernameInput.setError(null);
        emailInput.setError(null);
        passwordInput.setError(null);
        confirmPasswordInput.setError(null);

        boolean isValid = true;

        if (username.isEmpty()) {
            usernameInput.setError("Field can't be empty");
            isValid = false;
        }

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Please enter a valid email address");
            isValid = false;
        }

        if (password.length() < 8) {
            passwordInput.setError("Password must be at least 8 characters long");
            isValid = false;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordInput.setError("Passwords do not match");
            isValid = false;
        }

        if (isValid) {
            if (dbHelper.checkUserExists(email)) {
                emailInput.setError("An account with this email already exists");
            } else {
                User newUser = new User();
                newUser.setUsername(username);
                newUser.setEmail(email);
                newUser.setPassword(password);
                dbHelper.addUser(newUser);

                Toast.makeText(this, "Sign up successful!", Toast.LENGTH_SHORT).show();

                // Go to Sign In screen
                Intent intent = new Intent(SignUpActivity.this, SignInActivity.class);
                startActivity(intent);
                overridePendingTransition(0, 0); // Disable animation
                finish(); // Close this screen
            }
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