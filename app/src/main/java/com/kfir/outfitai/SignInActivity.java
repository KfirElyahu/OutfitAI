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
import android.widget.Toast;
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
import com.google.firebase.firestore.QuerySnapshot;

public class SignInActivity extends AppCompatActivity {

    private static final String TAG = "SignInActivity";
    private HelperUserDB dbHelper;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.signin_screen);

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

        View signInButton = findViewById(R.id.SignIn_button);
        signInButton.setOnClickListener(v -> handleSignIn());

        View forgotPasswordButton = findViewById(R.id.ForgotPassword_button);
        forgotPasswordButton.setOnClickListener(v -> sendPasswordReset());

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

    private void sendPasswordReset() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            DialogUtils.showDialog(this, getString(R.string.signin_error_no_internet_title), getString(R.string.signin_error_no_internet_reset));
            return;
        }

        EditText emailOrUsernameInput = findViewById(R.id.UsernameOrEmail_textInput);
        String input = emailOrUsernameInput.getText().toString().trim();

        if (input.isEmpty()) {
            emailOrUsernameInput.setError(getString(R.string.signin_error_enter_email_reset));
            return;
        }

        if (android.util.Patterns.EMAIL_ADDRESS.matcher(input).matches()) {
            mAuth.sendPasswordResetEmail(input)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            DialogUtils.showDialog(SignInActivity.this,
                                    getString(R.string.common_notice),
                                    getString(R.string.signin_msg_reset_link_sent));
                        } else {
                            DialogUtils.showDialog(SignInActivity.this, getString(R.string.common_error), getString(R.string.signin_error_reset_failed));
                        }
                    });
        } else {
            db.collection("users").whereEqualTo("username", input).get().addOnCompleteListener(task -> {
                if(task.isSuccessful() && !task.getResult().isEmpty()) {
                    String email = task.getResult().getDocuments().get(0).getString("email");
                    mAuth.sendPasswordResetEmail(email).addOnSuccessListener(v -> DialogUtils.showDialog(this, getString(R.string.signin_msg_reset_email_associated_title), getString(R.string.signin_msg_reset_email_associated)));
                } else {
                    DialogUtils.showDialog(this, getString(R.string.common_error), getString(R.string.signin_error_account_not_found));
                }
            });
        }
    }

    private void handleSignIn() {
        EditText emailOrUsernameInput = findViewById(R.id.UsernameOrEmail_textInput);
        EditText passwordInput = findViewById(R.id.Password_textInput);

        String emailOrUsername = emailOrUsernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        emailOrUsernameInput.setError(null);
        passwordInput.setError(null);

        if (emailOrUsername.isEmpty()) {
            emailOrUsernameInput.setError(getString(R.string.signin_error_empty));
            return;
        }
        if (password.isEmpty()) {
            passwordInput.setError(getString(R.string.signin_error_empty));
            return;
        }

        View signInButton = findViewById(R.id.SignIn_button);
        signInButton.setEnabled(false);

        if (NetworkUtils.isNetworkAvailable(this)) {
            performOnlineLogin(emailOrUsername, password, signInButton);
        } else {
            performOfflineLogin(emailOrUsername, password, signInButton);
        }
    }

    private void performOfflineLogin(String input, String password, View button) {
        if (dbHelper.checkUserCredentials(input, password)) {
            String email = dbHelper.resolveEmailFromInput(input);
            if (email == null) email = input;

            completeLogin(email);
        } else {
            DialogUtils.showDialog(this,
                    getString(R.string.signin_error_offline_failed),
                    getString(R.string.signin_error_offline_msg));
            button.setEnabled(true);
        }
    }

    private void performOnlineLogin(String input, String password, View button) {
        if (android.util.Patterns.EMAIL_ADDRESS.matcher(input).matches()) {
            signInWithFirebase(input, password, null, button);
        } else {
            db.collection("users")
                    .whereEqualTo("username", input)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            QuerySnapshot result = task.getResult();
                            if (result != null && !result.isEmpty()) {
                                String email = result.getDocuments().get(0).getString("email");
                                signInWithFirebase(email, password, input, button);
                            } else {
                                DialogUtils.showDialog(SignInActivity.this, getString(R.string.signin_error_login_failed), getString(R.string.signin_error_username_not_found));
                                button.setEnabled(true);
                            }
                        } else {
                            DialogUtils.showDialog(SignInActivity.this, getString(R.string.common_error), getString(R.string.signin_error_connection_failed));
                            button.setEnabled(true);
                        }
                    });
        }
    }

    private void signInWithFirebase(String email, String password, String usernameIfKnown, View button) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            if (user.isEmailVerified()) {
                                syncCloudToLocal(user, email, password, usernameIfKnown);
                            } else {
                                mAuth.signOut();
                                DialogUtils.showDialog(SignInActivity.this, getString(R.string.signin_error_verification_required), getString(R.string.signin_msg_verification_required));
                                button.setEnabled(true);
                            }
                        }
                    } else {
                        button.setEnabled(true);
                        Toast.makeText(SignInActivity.this, getString(R.string.signin_error_auth_failed), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void syncCloudToLocal(FirebaseUser firebaseUser, String email, String password, String knownUsername) {
        if (knownUsername == null) {
            db.collection("users").document(firebaseUser.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String fetchedUsername = documentSnapshot.getString("username");
                        if (fetchedUsername == null) fetchedUsername = email.split("@")[0];

                        saveToLocalAndFinish(email, fetchedUsername, password);
                    })
                    .addOnFailureListener(e -> {
                        saveToLocalAndFinish(email, email.split("@")[0], password);
                    });
        } else {
            saveToLocalAndFinish(email, knownUsername, password);
        }
    }

    private void saveToLocalAndFinish(String email, String username, String password) {
        User syncedUser = new User();
        syncedUser.setEmail(email);
        syncedUser.setUsername(username);
        syncedUser.setPassword(password);

        dbHelper.syncUser(syncedUser);

        completeLogin(email);
    }

    private void completeLogin(String email) {
        SessionManager sessionManager = new SessionManager(SignInActivity.this);
        sessionManager.createLoginSession(email);

        Toast.makeText(SignInActivity.this, getString(R.string.signin_success_msg), Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(SignInActivity.this, GenerateActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
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