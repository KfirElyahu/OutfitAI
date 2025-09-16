package com.kfir.outfitai;

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

public class MainActivity extends AppCompatActivity {

    private enum Screen {
        WELCOME,
        SIGN_IN,
        SIGN_UP,
        GENERATE
    }

    private Screen currentScreen = Screen.WELCOME;
    private HelperUserDB dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dbHelper = new HelperUserDB(this);
        showWelcomeScreen();
        setupBackPressHandler();
    }

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentScreen == Screen.SIGN_IN || currentScreen == Screen.SIGN_UP) {
                    showWelcomeScreen();
                } else if (currentScreen == Screen.GENERATE) {
                    showWelcomeScreen(); // go back to welcome screen from generate screen
                }
                else {
                    finish();
                }
            }
        });
    }

    private void showWelcomeScreen() {
        setContentView(R.layout.welcome_screen);
        currentScreen = Screen.WELCOME;

        View signInButton = findViewById(R.id.SignIn_button);
        View signUpButton = findViewById(R.id.SignUp_button);
        View skipButton = findViewById(R.id.Skip_button);

        signInButton.setOnClickListener(v -> showSignInScreen());
        signUpButton.setOnClickListener(v -> showSignUpScreen());
        skipButton.setOnClickListener(v -> showGenerateScreen());
    }

    private void showGenerateScreen() {
        setContentView(R.layout.generate_screen);
        currentScreen = Screen.GENERATE;
    }


    private void showSignInScreen() {
        setContentView(R.layout.signin_screen);
        currentScreen = Screen.SIGN_IN;

        View backButton = findViewById(R.id.Back_button);
        backButton.setOnClickListener(v -> showWelcomeScreen());

        View signInButton = findViewById(R.id.SignIn_button);
        signInButton.setOnClickListener(v -> validateSignIn());

        setupKeyboardHandlingForForms();
    }


    private void showSignUpScreen() {
        setContentView(R.layout.signup_screen);
        currentScreen = Screen.SIGN_UP;

        View backButton = findViewById(R.id.Back_button);
        backButton.setOnClickListener(v -> showWelcomeScreen());

        View signUpButton = findViewById(R.id.SignUp_button);
        signUpButton.setOnClickListener(v -> validateSignUp());

        setupKeyboardHandlingForForms();
    }

    private void validateSignIn() {
        EditText emailOrUsernameInput = findViewById(R.id.UsernameOrEmail_textInput);
        EditText passwordInput = findViewById(R.id.Password_textInput);

        String emailOrUsername = emailOrUsernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        // reset errors
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

        // SQLite Sign In
        if (dbHelper.checkUserCredentials(emailOrUsername, password)) {
            Toast.makeText(this, "Sign in successful!", Toast.LENGTH_SHORT).show();
            showGenerateScreen();
        } else {
            Toast.makeText(this, "Invalid username/email or password", Toast.LENGTH_SHORT).show();
        }
    }

    private void validateSignUp() {
        EditText usernameInput = findViewById(R.id.Username_textInput);
        EditText emailInput = findViewById(R.id.Email_textInput);
        EditText passwordInput = findViewById(R.id.Password_textInput);
        EditText confirmPasswordInput = findViewById(R.id.ConfirmPassword_textInput);

        // remove spaces from input texts
        String username = usernameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        // reset errors
        usernameInput.setError(null);
        emailInput.setError(null);
        passwordInput.setError(null);
        confirmPasswordInput.setError(null);

        boolean isValid = true;

        if (username.isEmpty()) {
            usernameInput.setError("Field can't be empty");
            isValid = false;
        }

        // checks if email contains "@" and "." characters.
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Please enter a valid email address");
            isValid = false;
        }

        // checks if the password is at least 8 characters long.
        if (password.length() < 8) {
            passwordInput.setError("Password must be at least 8 characters long");
            isValid = false;
        }

        // check if Passwords Match
        if (!password.equals(confirmPassword)) {
            confirmPasswordInput.setError("Passwords do not match");
            isValid = false;
        }

        // SQLite Sign Up
        if (isValid) {
            // check if user with this email already exists in the database
            if (dbHelper.checkUserExists(email)) {
                emailInput.setError("An account with this email already exists");
            } else {
                // add user to database
                User newUser = new User();
                newUser.setUsername(username);
                newUser.setEmail(email);
                newUser.setPassword(password);
                dbHelper.addUser(newUser);

                Toast.makeText(this, "Sign up successful!", Toast.LENGTH_SHORT).show();
                showSignInScreen();
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