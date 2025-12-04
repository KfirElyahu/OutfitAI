package com.kfir.outfitai;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButtonToggleGroup;

public class LanguageDialogHelper {

    public interface OnLanguageSelectedListener {
        void onLanguageSelected();
    }

    public static void showLanguageSelectionDialog(Activity activity, LanguageManager languageManager, OnLanguageSelectedListener listener) {
        if (activity == null || languageManager == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_language_selection, null);

        MaterialButtonToggleGroup toggleGroup = view.findViewById(R.id.language_toggle_group);
        Button confirmButton = view.findViewById(R.id.btn_confirm_lang);

        String currentCode = languageManager.getCurrentLanguageCode();
        if (currentCode.equals("iw") || currentCode.equals("he")) {
            toggleGroup.check(R.id.btn_hebrew);
        } else {
            toggleGroup.check(R.id.btn_english);
        }

        builder.setView(view);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }

        confirmButton.setOnClickListener(v -> {
            int selectedId = toggleGroup.getCheckedButtonId();
            String selectedLang = (selectedId == R.id.btn_hebrew) ? "he" : "en";

            languageManager.setLocale(selectedLang);

            dialog.dismiss();

            if (listener != null) {
                listener.onLanguageSelected();
            }
        });

        dialog.show();
    }
}