package com.kfir.outfitai;

import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import io.getstream.photoview.dialog.PhotoViewDialog;

public class HistoryActivity extends AppCompatActivity {

    private HelperUserDB dbHelper;
    private Uri currentVisibleUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        dbHelper = new HelperUserDB(this);
        SessionManager sessionManager = new SessionManager(this);
        String currentUserEmail = sessionManager.getCurrentUserEmail();

        View backButton = findViewById(R.id.Back_button);
        backButton.setOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.history_recycler_view);
        TextView emptyText = findViewById(R.id.empty_history_text);

        List<HistoryItem> historyList = dbHelper.getUserHistory(currentUserEmail);

        if (historyList.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);

            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            HistoryAdapter adapter = new HistoryAdapter(this, historyList, this::showImageDialog);
            recyclerView.setAdapter(adapter);
        }

        ImageButton languageBtn = findViewById(R.id.language_button);
        if (languageBtn != null) {
            languageBtn.setOnClickListener(v -> {
                LanguageDialogHelper.showLanguageSelectionDialog(this, new LanguageManager(this), null);
            });
        }
    }

    private void showImageDialog(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;

        currentVisibleUri = uris.get(0);

        View overlayView = getLayoutInflater().inflate(R.layout.view_image_overlay, null);

        PhotoViewDialog.Builder<Uri> builder = new PhotoViewDialog.Builder<>(this, uris, (imageView, uri) ->
                Glide.with(HistoryActivity.this).load(uri).into(imageView)
        );

        builder.withOverlayView(overlayView);
        builder.withImageChangeListener(position -> currentVisibleUri = uris.get(position));

        final PhotoViewDialog<Uri> dialog = builder.build();

        ImageButton backButton = overlayView.findViewById(R.id.button_back);
        backButton.setOnClickListener(v -> dialog.dismiss());

        ImageButton saveButton = overlayView.findViewById(R.id.button_save);
        saveButton.setOnClickListener(v -> {
            ImageSaveHelper.checkPermissionAndSave(HistoryActivity.this, currentVisibleUri);
        });

        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == ImageSaveHelper.WRITE_STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (currentVisibleUri != null) {
                    ImageSaveHelper.saveImageToGallery(this, currentVisibleUri);
                }
            } else {
                DialogUtils.showDialog(this, "Permission Required", "Storage permission is required to save images.");
            }
        }
    }
}