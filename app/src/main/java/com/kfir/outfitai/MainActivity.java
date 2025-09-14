package com.kfir.outfitai;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showWelcomeScreen();
    }

    private void showWelcomeScreen() {
        setContentView(R.layout.welcome_screen);

        View signInButton = findViewById(R.id.SignIn_button);
        View signUpButton = findViewById(R.id.SignUp_button);

        signInButton.setOnClickListener(v -> showSignInScreen());
        signUpButton.setOnClickListener(v -> showSignUpScreen());
    }


    private void showSignInScreen() {
        setContentView(R.layout.signin_screen);
        View backButton = findViewById(R.id.Back_button);
        backButton.setOnClickListener(v -> showWelcomeScreen());
        setupKeyboardHandlingForForms();
    }


    private void showSignUpScreen() {
        setContentView(R.layout.signup_screen);

        View backButton = findViewById(R.id.Back_button);
        backButton.setOnClickListener(v -> showWelcomeScreen());

        setupKeyboardHandlingForForms();
    }

    private void setupKeyboardHandlingForForms() {
        View mainView = findViewById(R.id.main);
        final ScrollView scrollView = findViewById(R.id.scrollView);

        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // source: https://github.com/lofcoding/ImeOverlappingIssue
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