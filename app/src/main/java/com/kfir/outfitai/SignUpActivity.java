package com.kfir.outfitai;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SignUpActivity extends AppCompatActivity {

    private static final String TAG = "SignUpActivity";
    private HelperUserDB dbHelper;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.signup_screen);

        dbHelper = new HelperUserDB(this);
        mAuth = FirebaseAuth.getInstance();

        View backButton = findViewById(R.id.Back_button);
        backButton.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, 0);
        });

        View signUpButton = findViewById(R.id.SignUp_button);
        signUpButton.setOnClickListener(v -> validateSignUp());

        EditText passwordInput = findViewById(R.id.Password_textInput);
        ImageButton togglePassBtn = findViewById(R.id.btn_toggle_password_signup);
        setupPasswordToggle(passwordInput, togglePassBtn);

        EditText confirmInput = findViewById(R.id.ConfirmPassword_textInput);
        ImageButton toggleConfirmBtn = findViewById(R.id.btn_toggle_confirm_signup);
        setupPasswordToggle(confirmInput, toggleConfirmBtn);

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

        if (password.length() < 6) {
            passwordInput.setError("Password must be at least 6 characters long");
            isValid = false;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordInput.setError("Passwords do not match");
            isValid = false;
        }

        if (isValid) {
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "createUserWithEmail:success");
                            FirebaseUser firebaseUser = mAuth.getCurrentUser();

                            if (firebaseUser != null) {
                                firebaseUser.sendEmailVerification()
                                        .addOnCompleteListener(emailTask -> {
                                            if (emailTask.isSuccessful()) {
                                                if (!dbHelper.checkUserExists(email)) {
                                                    User newUser = new User();
                                                    newUser.setUsername(username);
                                                    newUser.setEmail(email);
                                                    newUser.setPassword(password);
                                                    dbHelper.addUser(newUser);
                                                }

                                                DialogUtils.showDialog(SignUpActivity.this, "Account Created",
                                                        "Verification email sent to " + email + ". Please verify before logging in.",
                                                        () -> {
                                                            Intent intent = new Intent(SignUpActivity.this, SignInActivity.class);
                                                            startActivity(intent);
                                                            finish();
                                                        });

                                            } else {
                                                Log.e(TAG, "sendEmailVerification", emailTask.getException());
                                                DialogUtils.showDialog(SignUpActivity.this, "Error", "Failed to send verification email.");
                                            }
                                        });
                            }
                        } else {
                            Log.w(TAG, "createUserWithEmail:failure", task.getException());
                            String errorMsg = task.getException() != null ? task.getException().getMessage() : "Authentication failed.";
                            if (errorMsg.contains("already in use")) {
                                emailInput.setError("Email already registered");
                                DialogUtils.showDialog(SignUpActivity.this, "Sign Up Failed", "This email is already registered.");
                            } else {
                                DialogUtils.showDialog(SignUpActivity.this, "Sign Up Failed", errorMsg);
                            }
                        }
                    });
        }
    }

    private void setupKeyboardHandlingForForms() {
        View mainView = findViewById(R.id.main);
        final ScrollView scrollView = findViewById(R.id.scrollView);

        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImeUtils.addImeListener(mainView, isVisible -> {
            View contentContainer = scrollView.getChildAt(0);
            float density = getResources().getDisplayMetrics().density;
            int bottomPadding = isVisible ? (int) (300 * density) : (int) (50 * density);
            contentContainer.setPadding(0, 0, 0, bottomPadding);

            if (isVisible) {
                scrollToFocusedView(scrollView);
            }
        });

        mainView.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus instanceof EditText) {
                scrollToFocusedView(scrollView);
            }
        });
    }

    private void scrollToFocusedView(ScrollView scrollView) {
        View focusedView = getCurrentFocus();
        if (focusedView == null || scrollView == null) return;

        scrollView.postDelayed(() -> {
            Rect rect = new Rect();
            focusedView.getHitRect(rect);

            try {
                scrollView.offsetDescendantRectToMyCoords(focusedView, rect);
            } catch (IllegalArgumentException e) {
                return;
            }

            int visibleHeight = scrollView.getHeight();
            int scrollOffset = visibleHeight / 3;

            int scrollToY = rect.top - scrollOffset;

            scrollView.smoothScrollTo(0, Math.max(0, scrollToY));
        }, 100);
    }
}