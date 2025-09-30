package com.kfir.outfitai;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
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

import com.google.common.collect.ImmutableList;
import com.google.genai.Client;
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GenerateActivity extends AppCompatActivity {

    private ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private Uri tempImageUri;

    private Uri selectedPersonUri;
    private Uri selectedClothingUri;
    private final Executor executor = Executors.newSingleThreadExecutor();

    private enum ImageTarget {
        PERSON, CLOTHING
    }
    private ImageTarget currentImageTarget;

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;

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
        final String prompt = "Replace the outfit on the person in the first uploaded image with the exact clothing shown in the second uploaded image. Extract only the clothes from the second image, ignoring any person, background, or other elements in it. Precisely match the style, color, texture, patterns, and details of the clothes while preserving the original pose, body shape, facial features, expression, skin tone, hair, accessories (unless part of the outfit), lighting, shadows, background, and overall composition from the first image. Adjust the new clothes to fit naturally on the subject's body with realistic folds, wrinkles, and draping. Output a high-resolution, photorealistic edited image in the same aspect ratio as the first image.";

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