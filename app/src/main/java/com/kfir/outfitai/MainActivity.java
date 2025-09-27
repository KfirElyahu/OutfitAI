package com.kfir.outfitai;

import com.google.common.collect.ImmutableList;
import com.google.genai.Client;
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ProgressBar;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.Rect;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private enum Screen {
        WELCOME,
        SIGN_IN,
        SIGN_UP,
        GENERATE,
        SETTINGS
    }

    private Screen currentScreen = Screen.WELCOME;
    private HelperUserDB dbHelper;
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

    // camera permission request code
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dbHelper = new HelperUserDB(this);

        setupActivityLaunchers();

        showWelcomeScreen();
        setupBackPressHandler();
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

    private void setupBackPressHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentScreen == Screen.SIGN_IN || currentScreen == Screen.SIGN_UP) {
                    showWelcomeScreen();
                } else if (currentScreen == Screen.GENERATE) {
                    showWelcomeScreen();
                } else if (currentScreen == Screen.SETTINGS) {
                    showGenerateScreen();
                }
                else {
                    finish();
                }
            }
        });
    }

    private void showWelcomeScreen() {
        setContentView(R.layout.welcome_screen);
        currentScreen = Screen.WELCOME;

        View signInButton = findViewById(R.id.SignIn_button);
        View signUpButton = findViewById(R.id.SignUp_button);
        View skipButton = findViewById(R.id.Skip_button);

        signInButton.setOnClickListener(v -> showSignInScreen());
        signUpButton.setOnClickListener(v -> showSignUpScreen());
        skipButton.setOnClickListener(v -> showGenerateScreen());
    }

    private void showGenerateScreen() {
        setContentView(R.layout.generate_screen);
        currentScreen = Screen.GENERATE;

        // Reset stored URIs when showing the screen
        selectedPersonUri = null;
        selectedClothingUri = null;

        View settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> showSettingsScreen());

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

    private void showImagePickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Image Source");
        builder.setItems(new CharSequence[]{"Gallery", "Camera"}, (dialog, which) -> {
            switch (which) {
                case 0:
                    pickMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
                    break;
                case 1:
                    // check permission before opening camera
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

        // Show loading, hide button
        loadingIndicator.setVisibility(View.VISIBLE);
        generateButton.setEnabled(false);

        // Execute in background
        executor.execute(() -> {
            try {
                byte[] result = callGeminiAPI(selectedPersonUri, selectedClothingUri);

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

    private byte[] callGeminiAPI(Uri personUri, Uri clothingUri) {
        final String prompt = "Replace the outfit on the person in the first uploaded image with the exact clothing shown in the second uploaded image. Extract only the clothes from the second image, ignoring any person, background, or other elements in it. Precisely match the style, color, texture, patterns, and details of the clothes while preserving the original pose, body shape, facial features, expression, skin tone, hair, accessories (unless part of the outfit), lighting, shadows, background, and overall composition from the first image. Adjust the new clothes to fit naturally on the subject's body with realistic folds, wrinkles, and draping. Output a high-resolution, photorealistic edited image in the same aspect ratio as the first image.";

        try (Client client = new Client.Builder()
                .apiKey(ApiConfig.GEMINI_API_KEY)
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
        // check if the device has a camera feature
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            Toast.makeText(this, "No camera found on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        // if camera hardware exists, check for permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // if permission is not granted request it
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            // if permission is already granted proceed with camera
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

    // handle permission request result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // if permission granted open camera
                openCamera();
            } else {
                // Permission denied
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

    private void showSettingsScreen() {
        setContentView(R.layout.settings_screen);
        currentScreen = Screen.SETTINGS;

        View backButton = findViewById(R.id.Back_button);
        backButton.setOnClickListener(v -> showGenerateScreen());

        View quitButton = findViewById(R.id.Quit_button);
        quitButton.setOnClickListener(v -> finish());
    }

    private void showSignInScreen() {
        setContentView(R.layout.signin_screen);
        currentScreen = Screen.SIGN_IN;

        View backButton = findViewById(R.id.Back_button);
        backButton.setOnClickListener(v -> showWelcomeScreen());

        View signInButton = findViewById(R.id.SignIn_button);
        signInButton.setOnClickListener(v -> validateSignIn());

        setupKeyboardHandlingForForms();
    }

    private void showSignUpScreen() {
        setContentView(R.layout.signup_screen);
        currentScreen = Screen.SIGN_UP;

        View backButton = findViewById(R.id.Back_button);
        backButton.setOnClickListener(v -> showWelcomeScreen());

        View signUpButton = findViewById(R.id.SignUp_button);
        signUpButton.setOnClickListener(v -> validateSignUp());

        setupKeyboardHandlingForForms();
    }

    private void validateSignIn() {
        EditText emailOrUsernameInput = findViewById(R.id.UsernameOrEmail_textInput);
        EditText passwordInput = findViewById(R.id.Password_textInput);

        String emailOrUsername = emailOrUsernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        emailOrUsernameInput.setError(null);
        passwordInput.setError(null);

        if (emailOrUsername.isEmpty()) {
            emailOrUsernameInput.setError("Field can't be empty");
            return;
        }

        if (password.isEmpty()) {
            passwordInput.setError("Field can't be empty");
            return;
        }

        if (dbHelper.checkUserCredentials(emailOrUsername, password)) {
            Toast.makeText(this, "Sign in successful!", Toast.LENGTH_SHORT).show();
            showGenerateScreen();
        } else {
            Toast.makeText(this, "Invalid username/email or password", Toast.LENGTH_SHORT).show();
        }
    }

    private void validateSignUp() {
        EditText usernameInput = findViewById(R.id.Username_textInput);
        EditText emailInput = findViewById(R.id.Email_textInput);
        EditText passwordInput = findViewById(R.id.Password_textInput);
        EditText confirmPasswordInput = findViewById(R.id.ConfirmPassword_textInput);

        String username = usernameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        usernameInput.setError(null);
        emailInput.setError(null);
        passwordInput.setError(null);
        confirmPasswordInput.setError(null);

        boolean isValid = true;

        if (username.isEmpty()) {
            usernameInput.setError("Field can't be empty");
            isValid = false;
        }

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Please enter a valid email address");
            isValid = false;
        }

        if (password.length() < 8) {
            passwordInput.setError("Password must be at least 8 characters long");
            isValid = false;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordInput.setError("Passwords do not match");
            isValid = false;
        }

        if (isValid) {
            if (dbHelper.checkUserExists(email)) {
                emailInput.setError("An account with this email already exists");
            } else {
                User newUser = new User();
                newUser.setUsername(username);
                newUser.setEmail(email);
                newUser.setPassword(password);
                dbHelper.addUser(newUser);

                Toast.makeText(this, "Sign up successful!", Toast.LENGTH_SHORT).show();
                showSignInScreen();
            }
        }
    }

    private void setupKeyboardHandlingForForms() {
        View mainView = findViewById(R.id.main);
        final ScrollView scrollView = findViewById(R.id.scrollView);

        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImeUtils.addImeListener(mainView, isVisible -> {
            if (isVisible) {
                View focusedView = getCurrentFocus();
                if (focusedView != null && scrollView != null) {
                    scrollView.postDelayed(() -> {
                        Rect focusedRect = new Rect();
                        focusedView.getHitRect(focusedRect);
                        scrollView.requestChildRectangleOnScreen(focusedView, focusedRect, false);
                    }, 200);
                }
            }
        });
    }
}