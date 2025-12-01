package com.kfir.outfitai;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.Locale;

public class WelcomeActivity extends AppCompatActivity {

    private LanguageManager languageManager;
    private ObjectAnimator glowAnimator;
    private Vibrator vibrator;
    private boolean beat1Triggered = false;
    private boolean beat2Triggered = false;
    private boolean isLanguageDialogShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        languageManager = new LanguageManager(this);

        if (languageManager.isFirstRun()) {
            setContentView(R.layout.welcome_screen);
            findViewById(R.id.SignIn_button).setVisibility(View.INVISIBLE);
            findViewById(R.id.SignUp_button).setVisibility(View.INVISIBLE);
            findViewById(R.id.Skip_button).setVisibility(View.INVISIBLE);

            LanguageDialogHelper.showLanguageSelectionDialog(this, languageManager, () -> {
                languageManager.setFirstRunCompleted();
            });
        } else {
            proceedToAppFlow();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isLanguageDialogShowing) {
            startLogoAnimation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLogoAnimation();
    }

    private void startLogoAnimation() {
        ImageView logoImageView = findViewById(R.id.logoImageView);
        if (logoImageView != null) {
            Drawable d = logoImageView.getDrawable();
            if (d instanceof Animatable && !((Animatable) d).isRunning()) {
                ((Animatable) d).start();
            }
        }

        ImageView glowView = findViewById(R.id.logoGlow);
        if (glowView != null) {
            startGlowAnimation(glowView);
        }
    }

    private void stopLogoAnimation() {
        ImageView logoImageView = findViewById(R.id.logoImageView);
        if (logoImageView != null) {
            Drawable d = logoImageView.getDrawable();
            if (d instanceof Animatable && ((Animatable) d).isRunning()) {
                ((Animatable) d).stop();
            }
        }

        if (glowAnimator != null) {
            glowAnimator.removeAllUpdateListeners();
            glowAnimator.cancel();
            glowAnimator = null;
        }
    }

    private void startGlowAnimation(ImageView glowView) {
        if (glowAnimator != null) {
            glowAnimator.cancel();
        }

        beat1Triggered = false;
        beat2Triggered = false;

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

        glowAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();

            if (fraction >= 0.38f && fraction <= 0.45f && !beat1Triggered) {
                triggerWeakVibration();
                beat1Triggered = true;
            }

            if (fraction >= 0.60f && fraction <= 0.67f && !beat2Triggered) {
                triggerStrongVibration();
                beat2Triggered = true;
            }
        });

        glowAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationRepeat(Animator animation) {
                beat1Triggered = false;
                beat2Triggered = false;
            }
        });

        glowAnimator.start();
    }

    private void triggerWeakVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }

    private void triggerStrongVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLogoAnimation();
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

        ImageButton languageButton = findViewById(R.id.language_button);
        languageButton.setOnClickListener(v -> {
            LanguageDialogHelper.showLanguageSelectionDialog(this, languageManager, null);
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
        isLanguageDialogShowing = true;
        stopLogoAnimation();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_language_selection, null);

        MaterialButtonToggleGroup toggleGroup = view.findViewById(R.id.language_toggle_group);
        Button confirmButton = view.findViewById(R.id.btn_confirm_lang);

        String currentCode = languageManager.getCurrentLanguageCode();

        if (currentCode.equals("iw") || currentCode.equals("he")) {
            toggleGroup.check(R.id.btn_hebrew);
        } else {
            toggleGroup.check(R.id.btn_english);
        }

        builder.setView(view);
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }

        confirmButton.setOnClickListener(v -> {
            int selectedId = toggleGroup.getCheckedButtonId();
            String selectedLang = (selectedId == R.id.btn_hebrew) ? "he" : "en";

            languageManager.setFirstRunCompleted();
            languageManager.setLocale(selectedLang);

            isLanguageDialogShowing = false;
            dialog.dismiss();

            Configuration config = new Configuration(getResources().getConfiguration());
            config.setLocale(new Locale(selectedLang));
            Context tempContext = createConfigurationContext(config);

            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                recreate();
            }, 100);
        });

        dialog.setOnDismissListener(dialogInterface -> {
            isLanguageDialogShowing = false;
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