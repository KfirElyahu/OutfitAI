package com.kfir.outfitai;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ImageSaveHelper {

    public static final int WRITE_STORAGE_PERMISSION_REQUEST_CODE = 1002;

    public static void checkPermissionAndSave(Activity activity, Uri uriToSave) {
        if (uriToSave == null) {
            Toast.makeText(activity, "No image to save", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        WRITE_STORAGE_PERMISSION_REQUEST_CODE);
            } else {
                saveImageToGallery(activity, uriToSave);
            }
        } else {
            saveImageToGallery(activity, uriToSave);
        }
    }

    public static void saveImageToGallery(Activity activity, Uri imageUri) {
        String fileName = "OutfitAI_" + System.currentTimeMillis() + ".jpg";
        ContentResolver resolver = activity.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);

        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "OutfitAI");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "OutfitAI");
            if (!directory.exists() && !directory.mkdirs()) {
                Toast.makeText(activity, "Failed to create directory", Toast.LENGTH_SHORT).show();
                return;
            }
            File file = new File(directory, fileName);
            values.put(MediaStore.Images.Media.DATA, file.getAbsolutePath());
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        Uri itemUri = null;
        try {
            itemUri = resolver.insert(collection, values);
            if (itemUri == null) throw new IOException("Failed to create new MediaStore record.");

            try (OutputStream os = resolver.openOutputStream(itemUri);
                 InputStream is = resolver.openInputStream(imageUri)) {
                if (os == null || is == null) throw new IOException("Failed to open streams.");
                byte[] buffer = new byte[4096];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(itemUri, values, null, null);
            }

            Toast.makeText(activity, "Image saved to Gallery", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            if (itemUri != null) {
                resolver.delete(itemUri, null, null);
            }
            e.printStackTrace();
            Toast.makeText(activity, "Failed to save image", Toast.LENGTH_SHORT).show();
        }
    }
}