package com.kfir.outfitai;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.welcome_screen);

        View signInButton = findViewById(R.id.SignIn_button);
        View signUpButton = findViewById(R.id.SignUp_button);
        View skipButton = findViewById(R.id.Skip_button);

        // go to Sign In screen
        signInButton.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, SignInActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0); // Disable animation
        });

        // go to Sign Up screen
        signUpButton.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, SignUpActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0); // Disable animation
        });

        // go to the main Generate screen
        skipButton.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, GenerateActivity.class);
            startActivity(intent);
        });
    }
}