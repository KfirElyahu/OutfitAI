package com.kfir.outfitai;

import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import io.getstream.photoview.dialog.PhotoViewDialog;

public class HistoryFragment extends Fragment {

    private HelperUserDB dbHelper;
    private Uri currentVisibleUri;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dbHelper = new HelperUserDB(requireContext());
        SessionManager sessionManager = new SessionManager(requireContext());
        String currentUserEmail = sessionManager.getCurrentUserEmail();

        RecyclerView recyclerView = view.findViewById(R.id.history_recycler_view);
        TextView emptyText = view.findViewById(R.id.empty_history_text);

        List<HistoryItem> historyList = dbHelper.getUserHistory(currentUserEmail);

        if (historyList.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);

            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            HistoryAdapter adapter = new HistoryAdapter(requireContext(), historyList, this::showImageDialog);
            recyclerView.setAdapter(adapter);
        }

        ImageButton languageBtn = view.findViewById(R.id.language_button);
        if (languageBtn != null) {
            languageBtn.setOnClickListener(v -> {
                LanguageDialogHelper.showLanguageSelectionDialog(requireActivity(), new LanguageManager(requireContext()), null);
            });
        }
    }

    private void showImageDialog(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;

        currentVisibleUri = uris.get(0);

        View overlayView = getLayoutInflater().inflate(R.layout.view_image_overlay, null);

        PhotoViewDialog.Builder<Uri> builder = new PhotoViewDialog.Builder<>(requireContext(), uris, (imageView, uri) ->
                Glide.with(HistoryFragment.this).load(uri).into(imageView)
        );

        builder.withOverlayView(overlayView);
        builder.withImageChangeListener(position -> currentVisibleUri = uris.get(position));

        final PhotoViewDialog<Uri> dialog = builder.build();

        ImageButton backButton = overlayView.findViewById(R.id.button_back);
        backButton.setOnClickListener(v -> dialog.dismiss());

        ImageButton saveButton = overlayView.findViewById(R.id.button_save);
        saveButton.setOnClickListener(v -> {
            ImageSaveHelper.checkPermissionAndSave(requireActivity(), currentVisibleUri);
        });

        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == ImageSaveHelper.WRITE_STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (currentVisibleUri != null) {
                    ImageSaveHelper.saveImageToGallery(requireActivity(), currentVisibleUri);
                }
            } else {
                DialogUtils.showDialog(requireContext(), "Permission Required", "Storage permission is required to save images.");
            }
        }
    }
}