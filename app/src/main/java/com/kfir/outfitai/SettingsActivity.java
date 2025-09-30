package com.kfir.outfitai;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_screen);

        View backButton = findViewById(R.id.Back_button);
        backButton.setOnClickListener(v -> finish()); // close this activity

        View quitButton = findViewById(R.id.Quit_button);
        quitButton.setOnClickListener(v -> {
            finishAffinity(); // Closes all activities in the task
            System.exit(0);
        });
    }
}