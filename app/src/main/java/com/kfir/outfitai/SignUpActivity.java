package com.kfir.outfitai;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {

    private static final String TAG = "SignUpActivity";
    private HelperUserDB dbHelper;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.signup_screen);

        dbHelper = new HelperUserDB(this);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        View backButton = findViewById(R.id.Back_button);
        ScrollView formScrollView = findViewById(R.id.scrollView);
        View foreground = findViewById(R.id.foreground);

        AnimationHelper.animateSlideUp(foreground, 0);
        AnimationHelper.animateSlideUp(formScrollView, 0);

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

        ImageButton languageBtn = findViewById(R.id.language_button);
        if (languageBtn != null) {
            languageBtn.setOnClickListener(v -> {
                LanguageDialogHelper.showLanguageSelectionDialog(this, new LanguageManager(this), null);
            });
        }
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
        if (!NetworkUtils.isNetworkAvailable(this)) {
            DialogUtils.showDialog(this,
                    getString(R.string.signin_error_no_internet_title),
                    getString(R.string.signup_error_no_internet_msg));
            return;
        }

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
            usernameInput.setError(getString(R.string.signin_error_empty));
            isValid = false;
        }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError(getString(R.string.signup_error_invalid_email));
            isValid = false;
        }
        if (password.length() < 6) {
            passwordInput.setError(getString(R.string.signup_error_password_length));
            isValid = false;
        }
        if (!password.equals(confirmPassword)) {
            confirmPasswordInput.setError(getString(R.string.signup_error_password_match));
            isValid = false;
        }

        if (!isValid) return;

        View signUpButton = findViewById(R.id.SignUp_button);
        signUpButton.setEnabled(false);

        db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (!task.getResult().isEmpty()) {
                            usernameInput.setError(getString(R.string.signup_error_username_taken));
                            signUpButton.setEnabled(true);
                        } else {
                            createFirebaseAuthAccount(username, email, password, signUpButton);
                        }
                    } else {
                        DialogUtils.showDialog(this, getString(R.string.signup_error_check_username), getString(R.string.signup_error_check_username));
                        signUpButton.setEnabled(true);
                    }
                });
    }

    private void createFirebaseAuthAccount(String username, String email, String password, View button) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "createUserWithEmail:success");
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();

                        if (firebaseUser != null) {
                            Map<String, Object> userMap = new HashMap<>();
                            userMap.put("username", username);
                            userMap.put("email", email);

                            db.collection("users").document(firebaseUser.getUid())
                                    .set(userMap)
                                    .addOnSuccessListener(aVoid -> {
                                        User newUser = new User();
                                        newUser.setUsername(username);
                                        newUser.setEmail(email);
                                        newUser.setPassword(password);
                                        dbHelper.syncUser(newUser);

                                        sendVerificationEmail(firebaseUser, email);
                                    })
                                    .addOnFailureListener(e -> {
                                        DialogUtils.showDialog(SignUpActivity.this, getString(R.string.common_error), getString(R.string.signup_error_profile_setup));
                                    });
                        }
                    } else {
                        button.setEnabled(true);
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "Authentication failed.";
                        if (errorMsg != null && errorMsg.contains("already in use")) {
                            ((EditText)findViewById(R.id.Email_textInput)).setError(getString(R.string.signup_error_email_registered));
                        } else {
                            DialogUtils.showDialog(SignUpActivity.this, getString(R.string.signup_error_failed), errorMsg);
                        }
                    }
                });
    }

    private void sendVerificationEmail(FirebaseUser user, String email) {
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    DialogUtils.showDialog(SignUpActivity.this, getString(R.string.signup_success_created),
                            getString(R.string.signup_msg_verification_sent, email),
                            () -> {
                                Intent intent = new Intent(SignUpActivity.this, SignInActivity.class);
                                startActivity(intent);
                                finish();
                            });
                });
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
    }

    private void scrollToFocusedView(ScrollView scrollView) {
        View focusedView = getCurrentFocus();
        if (focusedView == null || scrollView == null) return;
        scrollView.postDelayed(() -> {
            Rect rect = new Rect();
            focusedView.getHitRect(rect);
            try {
                scrollView.offsetDescendantRectToMyCoords(focusedView, rect);
            } catch (IllegalArgumentException e) { return; }
            int visibleHeight = scrollView.getHeight();
            int scrollOffset = visibleHeight / 3;
            int scrollToY = rect.top - scrollOffset;
            scrollView.smoothScrollTo(0, Math.max(0, scrollToY));
        }, 100);
    }
}