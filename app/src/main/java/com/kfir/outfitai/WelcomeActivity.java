package com.kfir.outfitai;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.graphics.drawable.Animatable;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

public class WelcomeActivity extends AppCompatActivity {

    private LanguageManager languageManager;
    private ObjectAnimator glowAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        languageManager = new LanguageManager(this);

        if (languageManager.isFirstRun()) {
            setContentView(R.layout.welcome_screen);
            findViewById(R.id.SignIn_button).setVisibility(View.INVISIBLE);
            findViewById(R.id.SignUp_button).setVisibility(View.INVISIBLE);
            findViewById(R.id.Skip_button).setVisibility(View.INVISIBLE);

            showLanguageDialog();
        } else {
            proceedToAppFlow();
        }

        startLogoAnimation();
    }

    private void startLogoAnimation() {
        ImageView logoImageView = findViewById(R.id.logoImageView);
        if (logoImageView != null) {
            Drawable d = logoImageView.getDrawable();
            if (d instanceof Animatable) {
                ((Animatable) d).start();
            }
        }

        ImageView glowView = findViewById(R.id.logoGlow);
        if (glowView != null) {
            startGlowAnimation(glowView);
        }
    }

    private void startGlowAnimation(ImageView glowView) {
        PropertyValuesHolder alphaHolder = PropertyValuesHolder.ofFloat(View.ALPHA,
                0f, 0.6f, 0.8f, 0.5f, 0.9f, 0.6f, 0f);

        PropertyValuesHolder scaleXHolder = PropertyValuesHolder.ofFloat(View.SCALE_X,
                0.8f, 1.0f, 1.1f, 1.0f, 1.15f, 1.0f, 0.8f);

        PropertyValuesHolder scaleYHolder = PropertyValuesHolder.ofFloat(View.SCALE_Y,
                0.8f, 1.0f, 1.1f, 1.0f, 1.15f, 1.0f, 0.8f);

        glowAnimator = ObjectAnimator.ofPropertyValuesHolder(glowView,
                alphaHolder, scaleXHolder, scaleYHolder);
        glowAnimator.setDuration(5000);
        glowAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        glowAnimator.setRepeatMode(ObjectAnimator.RESTART);
        glowAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        glowAnimator.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (glowAnimator != null && glowAnimator.isRunning()) {
            glowAnimator.pause();
        }

        ImageView logoImageView = findViewById(R.id.logoImageView);
        if (logoImageView != null) {
            Drawable d = logoImageView.getDrawable();
            if (d instanceof Animatable && ((Animatable) d).isRunning()) {
                ((Animatable) d).stop();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glowAnimator != null && glowAnimator.isPaused()) {
            glowAnimator.resume();
        } else if (glowAnimator == null) {
            ImageView glowView = findViewById(R.id.logoGlow);
            if (glowView != null) {
                startGlowAnimation(glowView);
            }
        }

        ImageView logoImageView = findViewById(R.id.logoImageView);
        if (logoImageView != null) {
            Drawable d = logoImageView.getDrawable();
            if (d instanceof Animatable && !((Animatable) d).isRunning()) {
                ((Animatable) d).start();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (glowAnimator != null) {
            glowAnimator.cancel();
            glowAnimator = null;
        }
    }

    private void proceedToAppFlow() {
        SessionManager sessionManager = new SessionManager(this);
        if (sessionManager.isLoggedIn()) {
            Intent intent = new Intent(WelcomeActivity.this, GenerateActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.welcome_screen);
        setupButtons();
    }

    private void setupButtons() {
        View signInButton = findViewById(R.id.SignIn_button);
        View signUpButton = findViewById(R.id.SignUp_button);
        View skipButton = findViewById(R.id.Skip_button);

        signInButton.setVisibility(View.VISIBLE);
        signUpButton.setVisibility(View.VISIBLE);
        skipButton.setVisibility(View.VISIBLE);

        animateButtonsEntrance(signInButton, signUpButton, skipButton);

        signInButton.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, SignInActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        signUpButton.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, SignUpActivity.class);
            startActivity(intent);
            overridePendingTransition(0, 0);
        });

        skipButton.setOnClickListener(v -> {
            SessionManager session = new SessionManager(this);
            session.createLoginSession("guest_user");
            Intent intent = new Intent(WelcomeActivity.this, GenerateActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void animateButtonsEntrance(View... buttons) {
        for (int i = 0; i < buttons.length; i++) {
            View button = buttons[i];
            button.setAlpha(0f);
            button.setTranslationY(50f);
            button.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .setStartDelay(100L * i)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        }
    }

    private void showLanguageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_language_selection, null);

        RadioGroup radioGroup = view.findViewById(R.id.language_radio_group);
        RadioButton radioEnglish = view.findViewById(R.id.radio_english);
        RadioButton radioHebrew = view.findViewById(R.id.radio_hebrew);
        Button confirmButton = view.findViewById(R.id.btn_confirm_lang);

        String currentCode = languageManager.getCurrentLanguageCode();
        android.util.Log.d("LANG_DEBUG", "Dialog Opened. Manager says: " + currentCode);
        android.util.Log.d("LANG_DEBUG", "System Locale: " + Locale.getDefault().toString());

        if (currentCode.equals("iw") || currentCode.equals("he")) {
            radioHebrew.setChecked(true);
        } else {
            radioEnglish.setChecked(true);
        }

        builder.setView(view);
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }

        confirmButton.setOnClickListener(v -> {
            String selectedLang = radioHebrew.isChecked() ? "he" : "en";
            android.util.Log.d("LANG_DEBUG", "User selected: " + selectedLang);

            languageManager.setFirstRunCompleted();

            languageManager.setLocale(selectedLang);
            dialog.dismiss();

            Configuration config = new Configuration(getResources().getConfiguration());
            config.setLocale(new Locale(selectedLang));
            Context tempContext = createConfigurationContext(config);
            String testString = tempContext.getString(R.string.welcome_sign_in);

            android.util.Log.d("LANG_DEBUG", "Testing Resource Lookup for: " + selectedLang);
            android.util.Log.d("LANG_DEBUG", "Resulting String: " + testString);

            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                android.util.Log.d("LANG_DEBUG", "Recreating Activity...");
                recreate();
            }, 100);
        });

        dialog.show();
    }

    private String getAppCurrentLanguage() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        if (!locales.isEmpty()) {
            return locales.get(0).getLanguage();
        }
        String systemLang = Locale.getDefault().getLanguage();
        if (systemLang.equals("he") || systemLang.equals("iw")) return "he";
        return "en";
    }
}