package com.kfir.outfitai;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.google.common.collect.ImmutableList;
import com.google.genai.Client;
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.getstream.photoview.dialog.PhotoViewDialog;

public class GenerateActivity extends AppCompatActivity {

    private ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private Uri tempImageUri;

    private Uri selectedPersonUri;
    private Uri selectedClothingUri;
    private Uri generatedImageUri; // To store the URI of the generated image

    private final Executor executor = Executors.newSingleThreadExecutor();

    private enum ImageTarget {
        PERSON, CLOTHING
    }
    private ImageTarget currentImageTarget;

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    private static final int WRITE_STORAGE_PERMISSION_REQUEST_CODE = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.generate_screen);

        setupActivityLaunchers();

        View settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(GenerateActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        View uploadPersonButton = findViewById(R.id.upload_person_button);
        uploadPersonButton.setOnClickListener(v -> {
            currentImageTarget = ImageTarget.PERSON;
            showImagePickerDialog();
        });

        View uploadClothingButton = findViewById(R.id.upload_clothing_button);
        uploadClothingButton.setOnClickListener(v -> {
            currentImageTarget = ImageTarget.CLOTHING;
            showImagePickerDialog();
        });

        View generateButton = findViewById(R.id.generate_button);
        generateButton.setOnClickListener(v -> generateOutfit());

        // Set OnClickListener for the result image
        ImageView resultImage = findViewById(R.id.result_image);
        resultImage.setOnClickListener(v -> {
            if (generatedImageUri != null) {
                showGeneratedImageDialog();
            }
        });
    }

    private void setupActivityLaunchers() {
        pickMediaLauncher = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                displaySelectedImage(uri);
            } else {
                Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
            }
        });

        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success) {
                displaySelectedImage(tempImageUri);
            } else {
                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showImagePickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Image Source");
        builder.setItems(new CharSequence[]{"Gallery", "Camera"}, (dialog, which) -> {
            switch (which) {
                case 0: // Gallery
                    pickMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
                    break;
                case 1: // Camera
                    checkCameraPermissionAndOpenCamera();
                    break;
            }
        });
        builder.show();
    }

    private void generateOutfit() {
        if (selectedPersonUri == null || selectedClothingUri == null) {
            Toast.makeText(this, "Please select both a person and clothing image", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressBar loadingIndicator = findViewById(R.id.loading_indicator);
        View generateButton = findViewById(R.id.generate_button);

        loadingIndicator.setVisibility(View.VISIBLE);
        generateButton.setEnabled(false);

        executor.execute(() -> {
            try {
                final String apiKey = ApiConfig.GEMINI_API_KEY;

                byte[] result = callGeminiAPI(apiKey, selectedPersonUri, selectedClothingUri);

                runOnUiThread(() -> {
                    if (result != null) {
                        displayResult(result);
                    } else {
                        Toast.makeText(this, "Failed to generate outfit", Toast.LENGTH_SHORT).show();
                    }
                    loadingIndicator.setVisibility(View.GONE);
                    generateButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    loadingIndicator.setVisibility(View.GONE);
                    generateButton.setEnabled(true);
                });
            }
        });
    }

    private byte[] callGeminiAPI(String apiKey, Uri personUri, Uri clothingUri) {
        final String prompt = "Strictly preserve the identity, face, body shape, pose, and background of the subject in the first image while replacing their attire by extracting only the clothing garments, textures, and patterns visible in the second image—regardless of whether the source clothes are worn by another person or a flat lay—and apply them to the first subject with photorealistic draping, lighting consistency, and accurate fabric physics.";
        try (Client client = new Client.Builder()
                .apiKey(apiKey)
                .build()) {

            byte[] personImageBytes = getBytesFromUri(personUri);
            byte[] clothingImageBytes = getBytesFromUri(clothingUri);

            Part textPart = Part.fromText(prompt);
            Part personImagePart = Part.fromBytes(personImageBytes, "image/jpeg");
            Part clothingImagePart = Part.fromBytes(clothingImageBytes, "image/jpeg");

            Content content = Content.fromParts(textPart, personImagePart, clothingImagePart);

            GenerateContentResponse response = client.models.generateContent(
                    "gemini-2.5-flash-image-preview",
                    content,
                    null);

            ImmutableList<Part> parts = response.parts();

            if (parts != null) {
                for (Part part : parts) {
                    if (part.inlineData().isPresent()) {
                        Blob blob = part.inlineData().get();
                        if (blob.data().isPresent()) {
                            return blob.data().get();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private byte[] getBytesFromUri(Uri uri) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];

            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            return byteBuffer.toByteArray();
        }
    }

    private void displayResult(byte[] imageData) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
        ImageView resultImage = findViewById(R.id.result_image);

        if (bitmap != null) {
            generatedImageUri = saveBitmapToCacheAndGetUri(bitmap);
            if (generatedImageUri == null) {
                Toast.makeText(this, "Failed to cache generated image", Toast.LENGTH_SHORT).show();
                return;
            }

            resultImage.setImageBitmap(bitmap);
            resultImage.setVisibility(View.VISIBLE);

            ScrollView scrollView = findViewById(R.id.scrollView);
            if (scrollView != null) {
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            }
        } else {
            Toast.makeText(this, "Failed to decode generated image", Toast.LENGTH_SHORT).show();
        }
    }

    private void showGeneratedImageDialog() {
        View overlayView = getLayoutInflater().inflate(R.layout.view_image_overlay, null);

        final PhotoViewDialog.Builder<Uri> builder = new PhotoViewDialog.Builder<>(
                this,
                Collections.singletonList(generatedImageUri),
                (imageView, uri) -> Glide.with(GenerateActivity.this).load(uri).into(imageView)
        );

        builder.withOverlayView(overlayView);
        final PhotoViewDialog<Uri> dialog = builder.build();

        ImageButton backButton = overlayView.findViewById(R.id.button_back);
        backButton.setOnClickListener(v -> dialog.dismiss());

        ImageButton saveButton = overlayView.findViewById(R.id.button_save);
        saveButton.setOnClickListener(v -> checkStoragePermissionAndSave());

        dialog.show();
    }

    private void checkStoragePermissionAndSave() {
        if (generatedImageUri == null) {
            Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        WRITE_STORAGE_PERMISSION_REQUEST_CODE);
            } else {
                saveImageToGallery(generatedImageUri);
            }
        } else {
            saveImageToGallery(generatedImageUri);
        }
    }

    private void saveImageToGallery(Uri imageUri) {
        String fileName = "OutfitAI_" + System.currentTimeMillis() + ".jpg";
        ContentResolver resolver = getContentResolver();
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
                Toast.makeText(this, "Failed to create directory", Toast.LENGTH_SHORT).show();
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
                 InputStream is = getContentResolver().openInputStream(imageUri)) {
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

            Toast.makeText(this, "Image saved to Gallery", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            if (itemUri != null) {
                resolver.delete(itemUri, null, null);
            }
            e.printStackTrace();
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show();
        }
    }

    private Uri saveBitmapToCacheAndGetUri(Bitmap bitmap) {
        File imageFile = new File(getCacheDir(), "generated_outfit_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            return FileProvider.getUriForFile(this, getPackageName() + ".provider", imageFile);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void checkCameraPermissionAndOpenCamera() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            Toast.makeText(this, "No camera found on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            openCamera();
        }
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "No camera app found on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        tempImageUri = createImageUri();
        if (tempImageUri != null) {
            takePictureLauncher.launch(tempImageUri);
        } else {
            Toast.makeText(this, "Could not create image file", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to take photos",
                        Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == WRITE_STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (generatedImageUri != null) {
                    saveImageToGallery(generatedImageUri);
                }
            } else {
                Toast.makeText(this, "Storage permission is required to save images", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Uri createImageUri() {
        File imageFile = new File(getCacheDir(), "temp_image_" + System.currentTimeMillis() + ".jpg");
        return FileProvider.getUriForFile(this, getPackageName() + ".provider", imageFile);
    }

    private void displaySelectedImage(Uri imageUri) {
        if (currentImageTarget == ImageTarget.PERSON) {
            selectedPersonUri = imageUri;
            ImageView personSelectedImage = findViewById(R.id.person_selected_image);
            ImageView personIcon = findViewById(R.id.person_icon);
            ImageView personUploadIcon = findViewById(R.id.person_uploadIcon);

            personSelectedImage.setImageURI(imageUri);
            personSelectedImage.setVisibility(View.VISIBLE);
            personIcon.setVisibility(View.GONE);
            personUploadIcon.setVisibility(View.GONE);

        } else if (currentImageTarget == ImageTarget.CLOTHING) {
            selectedClothingUri = imageUri;
            ImageView clothingSelectedImage = findViewById(R.id.clothing_selected_image);
            ImageView clothingIcon = findViewById(R.id.clothing_icon);
            ImageView clothingUploadIcon = findViewById(R.id.clothing_uploadIcon);

            clothingSelectedImage.setImageURI(imageUri);
            clothingSelectedImage.setVisibility(View.VISIBLE);
            clothingIcon.setVisibility(View.GONE);
            clothingUploadIcon.setVisibility(View.GONE);
        }
    }
}