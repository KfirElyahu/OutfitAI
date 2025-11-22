package com.kfir.outfitai;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "OutfitAI_Global_Session";
    private static final String KEY_USER_EMAIL = "current_user_email";

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    public void createLoginSession(String email) {
        editor.putString(KEY_USER_EMAIL, email);
        editor.apply();
    }

    public void logoutUser() {
        editor.clear();
        editor.apply();
    }

    public boolean isLoggedIn() {
        return prefs.contains(KEY_USER_EMAIL);
    }

    public String getCurrentUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, null);
    }
}