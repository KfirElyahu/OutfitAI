package com.kfir.outfitai;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

public class WelcomeActivity extends AppCompatActivity {

    private LanguageManager languageManager;

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