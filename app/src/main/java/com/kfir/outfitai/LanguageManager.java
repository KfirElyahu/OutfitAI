package com.kfir.outfitai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.LocaleList;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import java.util.Locale;

public class LanguageManager {

    private static final String PREF_NAME = "OutfitAI_Language_Settings";
    private static final String KEY_FIRST_RUN = "is_first_run";
    private static final String KEY_CURRENT_LANG = "current_language";

    private final SharedPreferences prefs;

    public LanguageManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isFirstRun() {
        return prefs.getBoolean(KEY_FIRST_RUN, true);
    }

    public void setFirstRunCompleted() {
        prefs.edit().putBoolean(KEY_FIRST_RUN, false).apply();
    }

    public void setLocale(String languageCode) {
        prefs.edit().putString(KEY_CURRENT_LANG, languageCode).apply();
        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(languageCode);
        AppCompatDelegate.setApplicationLocales(appLocale);
    }

    public String getCurrentLanguageCode() {
        String saved = prefs.getString(KEY_CURRENT_LANG, "");
        if (!saved.isEmpty()) {
            return saved;
        }

        Locale systemLocale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            systemLocale = LocaleList.getDefault().get(0);
        } else {
            systemLocale = Locale.getDefault();
        }

        String lang = systemLocale.getLanguage();
        if (lang.equals("he") || lang.equals("iw")) {
            return "he";
        }
        return "en";
    }
}