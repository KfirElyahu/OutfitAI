package com.kfir.outfitai;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.getstream.photoview.dialog.PhotoViewDialog;

public class GenerateActivity extends AppCompatActivity {

    private ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private Uri tempImageUri;

    private Uri selectedPersonUri;
    private Uri selectedClothingUri;
    private Uri generatedImageUri;
    private List<Uri> generatedGridUris = new ArrayList<>();

    private final Executor executor = Executors.newSingleThreadExecutor();

    private enum ImageTarget {
        PERSON, CLOTHING
    }
    private ImageTarget currentImageTarget;

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    private static final int WRITE_STORAGE_PERMISSION_REQUEST_CODE = 1002;

    private View loadingOverlay;
    private ProgressBar progressBar;
    private TextView loadingPercentageText;
    private TextView loadingTimerText;
    private TextView loadingStageText;

    private Timer progressTimer;
    private long startTimeMillis;
    private int currentProgress = 0;

    private List<String> availablePrompts;
    private String currentPrompt;
    private int selectedPromptIndex = 0;
    private boolean isTryAllMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.generate_screen);

        setupActivityLaunchers();
        loadPrompts();

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

        ImageButton promptMenuButton = findViewById(R.id.prompt_menu_button);
        promptMenuButton.setOnClickListener(v -> showPromptSelectionDialog());

        ImageView resultImage = findViewById(R.id.result_image);
        resultImage.setOnClickListener(v -> {
            if (generatedImageUri != null) {
                showGeneratedImageDialog(generatedImageUri);
            }
        });

        setupGridClickListeners();

        loadingOverlay = findViewById(R.id.loading_overlay);
        progressBar = findViewById(R.id.loading_progress_bar);
        loadingPercentageText = findViewById(R.id.loading_percentage);
        loadingTimerText = findViewById(R.id.loading_timer);
        loadingStageText = findViewById(R.id.loading_stage_text);
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

    private void setupGridClickListeners() {
        int[] gridIds = {R.id.grid_image_1, R.id.grid_image_2, R.id.grid_image_3, R.id.grid_image_4};
        for (int i = 0; i < gridIds.length; i++) {
            final int index = i;
            findViewById(gridIds[i]).setOnClickListener(v -> {
                if (index < generatedGridUris.size()) {
                    showGeneratedImageDialog(generatedGridUris.get(index));
                }
            });
        }
    }

    private void loadPrompts() {
        availablePrompts = new ArrayList<>();
        try {
            InputStream is = getAssets().open("prompts.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONArray jsonArray = new JSONArray(sb.toString());
            for (int i = 0; i < jsonArray.length(); i++) {
                availablePrompts.add(jsonArray.getString(i));
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error loading prompts", Toast.LENGTH_SHORT).show();
            // Fallback prompt
            availablePrompts.add("Default: Apply outfit from second image to person in first image.");
        }

        // Set default prompt
        if (!availablePrompts.isEmpty()) {
            currentPrompt = availablePrompts.get(0);
        }
    }

    private void showPromptSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        ListView listView = new ListView(this);

        TextView tryAllButton = new TextView(this);
        tryAllButton.setText("Try All Prompts");
        tryAllButton.setTextSize(18);
        tryAllButton.setPadding(30, 40, 30, 40);
        tryAllButton.setGravity(Gravity.CENTER);
        tryAllButton.setTextColor(Color.BLACK);
        tryAllButton.setTypeface(getResources().getFont(R.font.roboto_bold));
        tryAllButton.setBackgroundResource(R.drawable.green_large_button);

        listView.addHeaderView(tryAllButton);

        List<String> displayList = new ArrayList<>();
        for (int i = 0; i < availablePrompts.size(); i++) {
            displayList.add("Prompt " + (i + 1));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, displayList) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.WHITE);
                view.setTypeface(getResources().getFont(R.font.roboto_bold));
                view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                view.setPadding(50, 30, 20, 30);

                if (!isTryAllMode && position == selectedPromptIndex) {
                    view.setTextColor(ContextCompat.getColor(getContext(), R.color.green));
                }
                return view;
            }
        };

        listView.setAdapter(adapter);
        listView.setDivider(ContextCompat.getDrawable(this, R.drawable.button_ripple));
        listView.setDividerHeight(0);

        builder.setView(listView);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
        }

        tryAllButton.setOnClickListener(v -> {
            isTryAllMode = true;
            Toast.makeText(GenerateActivity.this, "Selected: Try All Prompts", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position > 0) {
                int promptIndex = position - 1;
                selectedPromptIndex = promptIndex;
                currentPrompt = availablePrompts.get(promptIndex);
                isTryAllMode = false;
                Toast.makeText(GenerateActivity.this, "Selected: Prompt " + (promptIndex + 1), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void generateOutfit() {
        if (selectedPersonUri == null || selectedClothingUri == null) {
            Toast.makeText(this, "Please select both a person and clothing image", Toast.LENGTH_SHORT).show();
            return;
        }

        startLoadingAnimation();

        View generateButton = findViewById(R.id.generate_button);
        generateButton.setEnabled(false);

        // Reset previous results views
        findViewById(R.id.result_image).setVisibility(View.GONE);
        findViewById(R.id.result_grid_container).setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                final String apiKey = ApiConfig.GEMINI_API_KEY;

                if (isTryAllMode) {
                    // Generate multiple images
                    List<Bitmap> results = new ArrayList<>();
                    int limit = Math.min(availablePrompts.size(), 4); // Max 4 images

                    for (int i = 0; i < limit; i++) {
                        final int currentIdx = i;
                        runOnUiThread(() -> loadingStageText.setText("Designing Variation " + (currentIdx + 1) + "..."));

                        byte[] resultBytes = callGeminiAPI(apiKey, selectedPersonUri, selectedClothingUri, availablePrompts.get(i));
                        if (resultBytes != null) {
                            Bitmap bitmap = BitmapFactory.decodeByteArray(resultBytes, 0, resultBytes.length);
                            if (bitmap != null) {
                                results.add(bitmap);
                            }
                        }
                    }

                    runOnUiThread(() -> {
                        stopLoadingAnimation();
                        if (!results.isEmpty()) {
                            displayGridResults(results);
                        } else {
                            Toast.makeText(this, "Failed to generate outfits", Toast.LENGTH_SHORT).show();
                        }
                        generateButton.setEnabled(true);
                    });

                } else {
                    // Generate single image
                    runOnUiThread(() -> loadingStageText.setText("AI is designing outfit..."));

                    byte[] result = callGeminiAPI(apiKey, selectedPersonUri, selectedClothingUri, currentPrompt);

                    runOnUiThread(() -> {
                        stopLoadingAnimation();
                        if (result != null) {
                            displayResult(result);
                        } else {
                            Toast.makeText(this, "Failed to generate outfit", Toast.LENGTH_SHORT).show();
                        }
                        generateButton.setEnabled(true);
                    });
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    stopLoadingAnimation();
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    generateButton.setEnabled(true);
                });
            }
        });
    }

    private void startLoadingAnimation() {
        loadingOverlay.setVisibility(View.VISIBLE);
        currentProgress = 0;
        startTimeMillis = System.currentTimeMillis();
        progressBar.setProgress(0);
        loadingStageText.setText("Preparing images...");

        progressTimer = new Timer();
        progressTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> updateProgressUI());
            }
        }, 0, 100); // Update every 100ms
    }

    private void updateProgressUI() {
        long elapsedMillis = System.currentTimeMillis() - startTimeMillis;

        long seconds = elapsedMillis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        loadingTimerText.setText(String.format("%02d:%02d", minutes, seconds));

        if (currentProgress < 20) {
            currentProgress += 2;
        } else if (currentProgress < 85) {
            // Slow down progression
            if (elapsedMillis % 200 < 100) currentProgress += 1;
        }

        // Specific text updates based on mode
        if (isTryAllMode && currentProgress > 30 && currentProgress < 90) {
            // The specific text is updated in the generate loop
        } else if (!isTryAllMode) {
            if (currentProgress > 30 && currentProgress < 60) {
                loadingStageText.setText("We are designing your outfit...");
            } else if (currentProgress >= 60 && currentProgress < 90) {
                loadingStageText.setText("Still working on it..");
            }
        }

        progressBar.setProgress(currentProgress);
        loadingPercentageText.setText(currentProgress + "%");
    }

    private void stopLoadingAnimation() {
        if (progressTimer != null) {
            progressTimer.cancel();
            progressTimer = null;
        }
        loadingOverlay.setVisibility(View.GONE);
    }

    private byte[] callGeminiAPI(String apiKey, Uri personUri, Uri clothingUri, String prompt) {
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

    // Display Single Result
    private void displayResult(byte[] imageData) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
        ImageView resultImage = findViewById(R.id.result_image);
        GridLayout gridLayout = findViewById(R.id.result_grid_container);

        if (bitmap != null) {
            generatedImageUri = saveBitmapToCacheAndGetUri(bitmap);
            if (generatedImageUri == null) {
                Toast.makeText(this, "Failed to cache generated image", Toast.LENGTH_SHORT).show();
                return;
            }

            gridLayout.setVisibility(View.GONE); // Hide grid
            resultImage.setImageBitmap(bitmap);
            resultImage.setVisibility(View.VISIBLE); // Show single image

            ScrollView scrollView = findViewById(R.id.scrollView);
            if (scrollView != null) {
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            }
        } else {
            Toast.makeText(this, "Failed to decode generated image", Toast.LENGTH_SHORT).show();
        }
    }

    // Display Grid Results
    private void displayGridResults(List<Bitmap> bitmaps) {
        ImageView resultImage = findViewById(R.id.result_image);
        GridLayout gridLayout = findViewById(R.id.result_grid_container);

        generatedGridUris.clear();

        // Ids of the images in the grid xml
        int[] gridIds = {R.id.grid_image_1, R.id.grid_image_2, R.id.grid_image_3, R.id.grid_image_4};

        // Hide all first
        for(int id : gridIds) {
            findViewById(id).setVisibility(View.GONE);
        }

        for (int i = 0; i < bitmaps.size() && i < gridIds.length; i++) {
            Bitmap bitmap = bitmaps.get(i);
            Uri uri = saveBitmapToCacheAndGetUri(bitmap);
            if (uri != null) {
                generatedGridUris.add(uri);
                ImageView imgView = findViewById(gridIds[i]);
                imgView.setImageBitmap(bitmap);
                imgView.setVisibility(View.VISIBLE);
            }
        }

        resultImage.setVisibility(View.GONE); // Hide single image
        gridLayout.setVisibility(View.VISIBLE); // Show grid

        ScrollView scrollView = findViewById(R.id.scrollView);
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void showGeneratedImageDialog(Uri imageUriToDisplay) {
        View overlayView = getLayoutInflater().inflate(R.layout.view_image_overlay, null);

        final PhotoViewDialog.Builder<Uri> builder = new PhotoViewDialog.Builder<>(
                this,
                Collections.singletonList(imageUriToDisplay),
                (imageView, uri) -> Glide.with(GenerateActivity.this).load(uri).into(imageView)
        );

        builder.withOverlayView(overlayView);
        final PhotoViewDialog<Uri> dialog = builder.build();

        ImageButton backButton = overlayView.findViewById(R.id.button_back);
        backButton.setOnClickListener(v -> dialog.dismiss());

        ImageButton saveButton = overlayView.findViewById(R.id.button_save);
        saveButton.setOnClickListener(v -> checkStoragePermissionAndSave(imageUriToDisplay));

        dialog.show();
    }

    private void checkStoragePermissionAndSave(Uri uriToSave) {
        if (uriToSave == null) {
            Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show();
            return;
        }

        generatedImageUri = uriToSave;

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        WRITE_STORAGE_PERMISSION_REQUEST_CODE);
            } else {
                saveImageToGallery(uriToSave);
            }
        } else {
            saveImageToGallery(uriToSave);
        }
    }

    private void checkStoragePermissionAndSave() {
        checkStoragePermissionAndSave(generatedImageUri);
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