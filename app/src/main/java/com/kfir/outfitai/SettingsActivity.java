package com.kfir.outfitai;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class SettingsActivity extends AppCompatActivity {

    private HelperUserDB dbHelper;
    private SessionManager sessionManager;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentEmail;

    private EditText editUsername, editEmail, editPassword, editConfirmPassword;
    private ShapeableImageView profileImageView;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private Uri tempImageUri;
    private Uri selectedProfileUri;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 2001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_screen);

        dbHelper = new HelperUserDB(this);
        sessionManager = new SessionManager(this);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        currentEmail = sessionManager.getCurrentUserEmail();

        editUsername = findViewById(R.id.edit_username);
        editEmail = findViewById(R.id.edit_email);
        editPassword = findViewById(R.id.edit_password);
        editConfirmPassword = findViewById(R.id.edit_confirm_password);
        profileImageView = findViewById(R.id.profile_image);

        View backButton = findViewById(R.id.Back_button);
        backButton.setOnClickListener(v -> finish());

        View saveButton = findViewById(R.id.save_changes_button);
        saveButton.setOnClickListener(v -> saveChanges());

        View logoutButton = findViewById(R.id.Logout_button);
        logoutButton.setOnClickListener(v -> {
            sessionManager.logoutUser();
            mAuth.signOut();
            Intent intent = new Intent(SettingsActivity.this, WelcomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        View quitButton = findViewById(R.id.Quit_button);
        quitButton.setOnClickListener(v -> {
            finishAffinity();
            System.exit(0);
        });

        ImageButton togglePassBtn = findViewById(R.id.btn_toggle_password_settings);
        setupPasswordToggle(editPassword, togglePassBtn);

        ImageButton toggleConfirmBtn = findViewById(R.id.btn_toggle_confirm_settings);
        setupPasswordToggle(editConfirmPassword, toggleConfirmBtn);

        findViewById(R.id.profile_image_clickable).setOnClickListener(v -> showImagePickerDialog());

        if (currentEmail == null || currentEmail.isEmpty() || currentEmail.equals("guest_user")) {
            findViewById(R.id.form_container).setVisibility(View.GONE);
            findViewById(R.id.profile_image_clickable).setEnabled(false);
            DialogUtils.showDialog(this, "Guest Mode", "Guest accounts cannot change settings.");
        } else {
            loadUserData();
            setupImagePickers();
            setupKeyboardHandling();
        }
    }

    private void loadUserData() {
        User user = dbHelper.getUserDetails(currentEmail);
        if (user != null) {
            editUsername.setText(user.getUsername());
            editEmail.setText(user.getEmail());
            editPassword.setText("");
            editConfirmPassword.setText("");
            if (user.getProfilePicUri() != null && !user.getProfilePicUri().isEmpty()) {
                selectedProfileUri = Uri.parse(user.getProfilePicUri());
                displayProfileImage(selectedProfileUri);
            }
        }
    }

    private void saveChanges() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            DialogUtils.showDialog(this, "Offline", "You must be online to update settings.");
            return;
        }

        String newUsername = editUsername.getText().toString().trim();
        String newEmail = editEmail.getText().toString().trim();
        String newPassword = editPassword.getText().toString().trim();
        String confirmPassword = editConfirmPassword.getText().toString().trim();

        editUsername.setError(null); editEmail.setError(null); editPassword.setError(null); editConfirmPassword.setError(null);

        if (newUsername.isEmpty()) { editUsername.setError("Username cannot be empty"); return; }
        if (newEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) { editEmail.setError("Invalid Email"); return; }

        User currentUser = dbHelper.getUserDetails(currentEmail);
        String finalPassword = currentUser.getPassword();

        if (!newPassword.isEmpty()) {
            if (newPassword.length() < 8) { editPassword.setError("Minimum 8 characters"); return; }
            if (!newPassword.equals(confirmPassword)) { editConfirmPassword.setError("Passwords do not match"); return; }
            finalPassword = newPassword;
        }

        boolean usernameChanged = !newUsername.equals(currentUser.getUsername());

        if (usernameChanged) {
            String finalP = finalPassword;
            db.collection("users").whereEqualTo("username", newUsername).get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && !task.getResult().isEmpty()) {
                            editUsername.setError("Username already taken");
                        } else {
                            proceedWithUpdates(newUsername, newEmail, finalP, currentUser);
                        }
                    });
        } else {
            proceedWithUpdates(newUsername, newEmail, finalPassword, currentUser);
        }
    }

    private void proceedWithUpdates(String newUsername, String newEmail, String newPassword, User currentUser) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            DialogUtils.showDialog(this, "Error", "Authentication session expired. Please login again.");
            return;
        }

        if (!newEmail.equals(currentUser.getEmail())) {
            firebaseUser.verifyBeforeUpdateEmail(newEmail);
            DialogUtils.showDialog(this, "Notice", "Email update verification sent. Please confirm in your inbox.");
        }

        if (!newPassword.equals(currentUser.getPassword())) {
            firebaseUser.updatePassword(newPassword);
        }

        db.collection("users").document(firebaseUser.getUid())
                .update("username", newUsername, "email", newEmail)
                .addOnSuccessListener(aVoid -> {
                    User updatedUser = new User();
                    updatedUser.setUsername(newUsername);
                    updatedUser.setEmail(newEmail);
                    updatedUser.setPassword(newPassword);
                    updatedUser.setProfilePicUri(selectedProfileUri != null ? selectedProfileUri.toString() : currentUser.getProfilePicUri());

                    if (dbHelper.updateUserProfile(currentEmail, updatedUser)) {
                        DialogUtils.showDialog(this, "Success", "Profile Updated Successfully!", () -> {
                            if (!currentEmail.equals(newEmail)) {
                                sessionManager.logoutUser();
                                sessionManager.createLoginSession(newEmail);
                                currentEmail = newEmail;
                            }
                            loadUserData();
                        });
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(SettingsActivity.this, "Failed to update cloud profile", Toast.LENGTH_SHORT).show());
    }

    private void setupPasswordToggle(EditText editText, ImageButton button) {
        button.setOnClickListener(v -> {
            int selectionStart = editText.getSelectionStart();
            int selectionEnd = editText.getSelectionEnd();
            if (editText.getTransformationMethod() instanceof PasswordTransformationMethod) {
                editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                button.setImageResource(R.drawable.ic_visibility_off);
            } else {
                editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                button.setImageResource(R.drawable.ic_visibility);
            }
            editText.setSelection(selectionStart, selectionEnd);
        });
    }

    private void displayProfileImage(Uri uri) {
        profileImageView.setImageTintList(null);
        Glide.with(this).load(uri).transform(new CircleCrop()).into(profileImageView);
    }

    private void setupImagePickers() {
        pickMediaLauncher = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                selectedProfileUri = saveUriToInternalStorage(uri);
                displayProfileImage(selectedProfileUri);
            }
        });

        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success) {
                selectedProfileUri = saveUriToInternalStorage(tempImageUri);
                displayProfileImage(selectedProfileUri);
            }
        });
    }

    private void showImagePickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Profile Picture");
        builder.setItems(new CharSequence[]{"Gallery", "Camera"}, (dialog, which) -> {
            switch (which) {
                case 0:
                    pickMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
                    break;
                case 1:
                    checkCameraPermissionAndOpenCamera();
                    break;
            }
        });
        builder.show();
    }

    private void checkCameraPermissionAndOpenCamera() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            DialogUtils.showDialog(this, "Error", "No camera found.");
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            openCamera();
        }
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) == null) {
            DialogUtils.showDialog(this, "Error", "No camera app found.");
            return;
        }
        tempImageUri = createImageUri();
        if (tempImageUri != null) {
            takePictureLauncher.launch(tempImageUri);
        }
    }

    private Uri createImageUri() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "Profile_Temp_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "OutfitAI");
        }
        return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    private Uri saveUriToInternalStorage(Uri sourceUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(sourceUri);
            File profileDir = new File(getFilesDir(), "profile_images");
            if (!profileDir.exists()) profileDir.mkdirs();
            File destFile = new File(profileDir, "profile_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(destFile);
            byte[] buffer = new byte[4096];
            int length;
            while ((length = inputStream.read(buffer)) > 0) { fos.write(buffer, 0, length); }
            fos.close(); inputStream.close();
            return Uri.fromFile(destFile);
        } catch (Exception e) { e.printStackTrace(); return sourceUri; }
    }

    private void setupKeyboardHandling() {
        View mainView = findViewById(R.id.main);
        final ScrollView scrollView = findViewById(R.id.scrollView);
        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });
        ImeUtils.addImeListener(mainView, isVisible -> {
            View contentContainer = scrollView.getChildAt(0);
            float density = getResources().getDisplayMetrics().density;
            int bottomPadding = isVisible ? (int) (300 * density) : (int) (50 * density);
            contentContainer.setPadding(0, 0, 0, bottomPadding);
            if (isVisible) scrollToFocusedView(scrollView);
        });
        mainView.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus instanceof EditText) scrollToFocusedView(scrollView);
        });
    }

    private void scrollToFocusedView(ScrollView scrollView) {
        View focusedView = getCurrentFocus();
        if (focusedView == null || scrollView == null) return;
        scrollView.postDelayed(() -> {
            Rect rect = new Rect();
            focusedView.getHitRect(rect);
            try { scrollView.offsetDescendantRectToMyCoords(focusedView, rect); } catch (IllegalArgumentException e) { return; }
            int visibleHeight = scrollView.getHeight();
            int scrollOffset = visibleHeight / 3;
            int scrollToY = rect.top - scrollOffset;
            scrollView.smoothScrollTo(0, Math.max(0, scrollToY));
        }, 100);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        }
    }
}