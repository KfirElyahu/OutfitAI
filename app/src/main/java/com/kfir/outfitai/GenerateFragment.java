package com.kfir.outfitai;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
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
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.FitCenter;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.getstream.photoview.dialog.PhotoViewDialog;

public class GenerateFragment extends Fragment {

    private ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<Intent> speechRecognizerLauncher;
    private Uri tempImageUri;

    private Uri selectedPersonUri;
    private Uri selectedClothingUri;
    private Uri generatedImageUri;
    private List<Uri> generatedGridUris = new ArrayList<>();
    private Uri currentVisibleDialogUri;

    private final Executor executor = Executors.newSingleThreadExecutor();
    private HelperUserDB dbHelper;
    private SessionManager sessionManager;
    private String currentUserEmail;
    private SharedPreferences userPrefs;

    private enum ImageTarget { PERSON, CLOTHING }
    private ImageTarget currentImageTarget;

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    private static final int RECORD_AUDIO_PERMISSION_REQUEST_CODE = 1002;

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

    private FirebaseFirestore firebaseDb;
    private FirebaseAuth mAuth;
    private int selectedRating = 0;
    private TextToSpeech textToSpeech;
    private boolean isTtsInitialized = false;

    private EditText currentFeedbackInput;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActivityLaunchers();
        initTextToSpeech();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_generate, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dbHelper = new HelperUserDB(requireContext());

        firebaseDb = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        sessionManager = new SessionManager(requireContext());
        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(requireContext(), WelcomeActivity.class));
            requireActivity().finish();
            return;
        }
        currentUserEmail = sessionManager.getCurrentUserEmail();
        userPrefs = requireContext().getSharedPreferences("Prefs_" + currentUserEmail, Context.MODE_PRIVATE);

        loadPrompts();

        View rateButton = view.findViewById(R.id.rate_button);
        rateButton.setOnClickListener(v -> showRatingDialog());

        View uploadPersonButton = view.findViewById(R.id.upload_person_button);
        uploadPersonButton.setOnClickListener(v -> {
            currentImageTarget = ImageTarget.PERSON;
            showImagePickerDialog();
        });

        View uploadClothingButton = view.findViewById(R.id.upload_clothing_button);
        uploadClothingButton.setOnClickListener(v -> {
            currentImageTarget = ImageTarget.CLOTHING;
            showImagePickerDialog();
        });

        View generateButton = view.findViewById(R.id.generate_button);
        generateButton.setOnClickListener(v -> generateOutfit());

        ImageButton promptMenuButton = view.findViewById(R.id.prompt_menu_button);
        promptMenuButton.setOnClickListener(v -> showPromptSelectionDialog());

        ImageView resultImage = view.findViewById(R.id.result_image);
        resultImage.setOnClickListener(v -> {
            if (!generatedGridUris.isEmpty()) {
                showGeneratedImageDialog(0);
            }
        });

        setupGridClickListeners(view);

        loadingOverlay = view.findViewById(R.id.loading_overlay);
        progressBar = view.findViewById(R.id.loading_progress_bar);
        loadingPercentageText = view.findViewById(R.id.loading_percentage);
        loadingTimerText = view.findViewById(R.id.loading_timer);
        loadingStageText = view.findViewById(R.id.loading_stage_text);

        ImageButton languageBtn = view.findViewById(R.id.language_button);
        if (languageBtn != null) {
            languageBtn.setOnClickListener(v -> {
                LanguageDialogHelper.showLanguageSelectionDialog(requireActivity(), new LanguageManager(requireContext()), null);
            });
        }
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsInitialized = false;
                } else {
                    isTtsInitialized = true;
                }
            } else {
                isTtsInitialized = false;
            }
        });
    }

    private void setupActivityLaunchers() {
        pickMediaLauncher = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                Uri persistentUri = saveUriToInternalStorage(uri);
                displaySelectedImage(persistentUri);
            } else {
                DialogUtils.showDialog(requireContext(),
                        getString(R.string.common_selection),
                        getString(R.string.generate_msg_no_image));
            }
        });

        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success) {
                Uri persistentUri = saveUriToInternalStorage(tempImageUri);
                displaySelectedImage(persistentUri);
            } else {
                DialogUtils.showDialog(requireContext(), getString(R.string.generate_error_camera_title), getString(R.string.generate_error_camera_capture));
            }
        });

        speechRecognizerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == requireActivity().RESULT_OK && result.getData() != null) {
                        ArrayList<String> speechResults = result.getData()
                                .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (speechResults != null && !speechResults.isEmpty()) {
                            String spokenText = speechResults.get(0);
                            if (currentFeedbackInput != null) {
                                String existingText = currentFeedbackInput.getText().toString();
                                if (existingText.isEmpty()) {
                                    currentFeedbackInput.setText(spokenText);
                                } else {
                                    currentFeedbackInput.setText(existingText + " " + spokenText);
                                }
                                currentFeedbackInput.setSelection(currentFeedbackInput.getText().length());
                            }
                        }
                    }
                }
        );
    }

    private Uri saveUriToInternalStorage(Uri sourceUri) {
        try {
            InputStream inputStream = requireContext().getContentResolver().openInputStream(sourceUri);
            File historyDir = new File(requireContext().getFilesDir(), "history_images");
            if (!historyDir.exists()) historyDir.mkdirs();

            String fileName = "hist_" + System.currentTimeMillis() + ".jpg";
            File destFile = new File(historyDir, fileName);

            FileOutputStream fos = new FileOutputStream(destFile);
            byte[] buffer = new byte[4096];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            fos.close();
            inputStream.close();

            return Uri.fromFile(destFile);
        } catch (Exception e) {
            e.printStackTrace();
            return sourceUri;
        }
    }

    private void showRatingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_rating, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ImageView[] stars = new ImageView[5];
        stars[0] = dialogView.findViewById(R.id.star_1);
        stars[1] = dialogView.findViewById(R.id.star_2);
        stars[2] = dialogView.findViewById(R.id.star_3);
        stars[3] = dialogView.findViewById(R.id.star_4);
        stars[4] = dialogView.findViewById(R.id.star_5);

        TextView ratingLabel = dialogView.findViewById(R.id.rating_label);
        EditText feedbackInput = dialogView.findViewById(R.id.rating_feedback_input);
        View submitButton = dialogView.findViewById(R.id.rating_submit_button);
        View cancelButton = dialogView.findViewById(R.id.rating_cancel_button);

        ImageButton btnSpeechToText = dialogView.findViewById(R.id.btn_speech_to_text);
        ImageButton btnTextToSpeech = dialogView.findViewById(R.id.btn_text_to_speech);

        currentFeedbackInput = feedbackInput;

        selectedRating = 0;

        for (int i = 0; i < 5; i++) {
            final int starIndex = i + 1;
            stars[i].setOnClickListener(v -> {
                selectedRating = starIndex;
                updateStarDisplay(stars, starIndex);
                updateRatingLabel(ratingLabel, starIndex);
            });
        }

        btnSpeechToText.setOnClickListener(v -> {
            startSpeechToText();
        });

        btnTextToSpeech.setOnClickListener(v -> {
            String textToRead = feedbackInput.getText().toString().trim();
            speakText(textToRead);
        });

        submitButton.setOnClickListener(v -> {
            if (selectedRating == 0) {
                DialogUtils.showDialog(requireContext(),
                        getString(R.string.rating_error_title),
                        getString(R.string.rating_error_no_stars));
                return;
            }

            if (textToSpeech != null && textToSpeech.isSpeaking()) {
                textToSpeech.stop();
            }

            String feedback = feedbackInput.getText().toString().trim();
            submitRatingToFirebase(selectedRating, feedback, dialog);
        });

        cancelButton.setOnClickListener(v -> {
            if (textToSpeech != null && textToSpeech.isSpeaking()) {
                textToSpeech.stop();
            }
            currentFeedbackInput = null;
            dialog.dismiss();
        });

        dialog.setOnDismissListener(dialogInterface -> {
            if (textToSpeech != null && textToSpeech.isSpeaking()) {
                textToSpeech.stop();
            }
            currentFeedbackInput = null;
        });

        dialog.show();
    }

    private void startSpeechToText() {
        if (!isSpeechRecognitionAvailable()) {
            Toast.makeText(requireContext(), getString(R.string.speech_not_supported), Toast.LENGTH_SHORT).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_REQUEST_CODE);
            return;
        }

        launchSpeechRecognizer();
    }

    private boolean isSpeechRecognitionAvailable() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        return intent.resolveActivity(requireContext().getPackageManager()) != null;
    }

    private void launchSpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        LanguageManager languageManager = new LanguageManager(requireContext());
        String currentLangCode = languageManager.getCurrentLanguageCode();

        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLangCode);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLangCode);
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, currentLangCode);

        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.listening));

        try {
            speechRecognizerLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.speech_error), Toast.LENGTH_SHORT).show();
        }
    }

    private void speakText(String text) {
        if (text.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.tts_no_text), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isTtsInitialized) {
            Toast.makeText(requireContext(), getString(R.string.tts_not_available), Toast.LENGTH_SHORT).show();
            return;
        }

        if (textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }

        Toast.makeText(requireContext(), getString(R.string.tts_speaking), Toast.LENGTH_SHORT).show();
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rating_feedback");
    }

    private void updateStarDisplay(ImageView[] stars, int rating) {
        for (int i = 0; i < 5; i++) {
            if (i < rating) {
                stars[i].setImageResource(R.drawable.ic_star_filled);
            } else {
                stars[i].setImageResource(R.drawable.ic_star_outline);
            }
        }
    }

    private void updateRatingLabel(TextView label, int rating) {
        String[] ratingTexts = {
                getString(R.string.rating_1_star),
                getString(R.string.rating_2_stars),
                getString(R.string.rating_3_stars),
                getString(R.string.rating_4_stars),
                getString(R.string.rating_5_stars)
        };

        if (rating >= 1 && rating <= 5) {
            label.setText(ratingTexts[rating - 1]);
            label.setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
        }
    }

    private void submitRatingToFirebase(int rating, String feedback, AlertDialog dialog) {
        if (!NetworkUtils.isNetworkAvailable(requireContext())) {
            DialogUtils.showDialog(requireContext(),
                    getString(R.string.rating_error_title),
                    "No internet connection. Please try again later.");
            return;
        }

        String userEmail = currentUserEmail != null ? currentUserEmail : "anonymous";
        String userId = "anonymous";
        String username = "Anonymous";

        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser != null) {
            userId = firebaseUser.getUid();
        }

        if (currentUserEmail != null) {
            if (currentUserEmail.equals("guest_user")) {
                username = "Guest";
            } else {
                User user = dbHelper.getUserDetails(currentUserEmail);
                if (user != null && user.getUsername() != null) {
                    username = user.getUsername();
                }
            }
        }

        Map<String, Object> ratingData = new HashMap<>();
        ratingData.put("rating", rating);
        ratingData.put("feedback", feedback);
        ratingData.put("userEmail", userEmail);
        ratingData.put("userId", userId);
        ratingData.put("username", username);
        ratingData.put("timestamp", System.currentTimeMillis());
        ratingData.put("appVersion", getAppVersion());
        ratingData.put("deviceInfo", android.os.Build.MODEL + " - Android " + android.os.Build.VERSION.RELEASE);

        firebaseDb.collection("app_ratings")
                .add(ratingData)
                .addOnSuccessListener(documentReference -> {
                    dialog.dismiss();

                    userPrefs.edit().putBoolean("has_rated", true).apply();
                    userPrefs.edit().putLong("last_rating_time", System.currentTimeMillis()).apply();

                    DialogUtils.showDialog(requireContext(),
                            getString(R.string.rating_success_title),
                            getString(R.string.rating_success_message));
                })
                .addOnFailureListener(e -> {
                    DialogUtils.showDialog(requireContext(),
                            getString(R.string.rating_error_title),
                            getString(R.string.rating_error_message));
                });
    }

    private String getAppVersion() {
        try {
            return requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }

    private void showImagePickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(getString(R.string.generate_dialog_title_source));
        builder.setItems(new CharSequence[]{getString(R.string.common_gallery), getString(R.string.common_camera)}, (dialog, which) -> {
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

    private void setupGridClickListeners(View rootView) {
        int[] gridIds = {R.id.grid_image_1, R.id.grid_image_2, R.id.grid_image_3, R.id.grid_image_4};
        for (int i = 0; i < gridIds.length; i++) {
            final int index = i;
            rootView.findViewById(gridIds[i]).setOnClickListener(v -> {
                if (index < generatedGridUris.size()) {
                    showGeneratedImageDialog(index);
                }
            });
        }
    }

    private void loadPrompts() {
        availablePrompts = new ArrayList<>();
        try {
            InputStream is = requireContext().getAssets().open("prompts.json");
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
            availablePrompts.add("Default: Apply outfit from second image to person in first image.");
        }

        int savedIndex = userPrefs.getInt("last_prompt_index", 0);
        if (savedIndex >= 0 && savedIndex < availablePrompts.size()) {
            selectedPromptIndex = savedIndex;
            currentPrompt = availablePrompts.get(savedIndex);
        } else if (!availablePrompts.isEmpty()) {
            currentPrompt = availablePrompts.get(0);
        }
    }

    private void showPromptSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        ListView listView = new ListView(requireContext());

        TextView tryAllButton = new TextView(requireContext());
        tryAllButton.setText(getString(R.string.prompt_try_all));
        tryAllButton.setTextSize(18);
        tryAllButton.setPadding(30, 40, 30, 40);
        tryAllButton.setGravity(Gravity.CENTER);
        tryAllButton.setTextColor(Color.BLACK);
        tryAllButton.setTypeface(getResources().getFont(R.font.roboto_bold));
        tryAllButton.setBackgroundResource(R.drawable.green_large_button);

        listView.addHeaderView(tryAllButton);

        List<String> displayList = new ArrayList<>();
        for (int i = 0; i < availablePrompts.size(); i++) {
            displayList.add(getString(R.string.prompt_item_name, (i + 1)));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, displayList) {
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
        listView.setDivider(ContextCompat.getDrawable(requireContext(), R.drawable.button_ripple));
        listView.setDividerHeight(0);

        builder.setView(listView);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_background);
        }

        tryAllButton.setOnClickListener(v -> {
            isTryAllMode = true;
            Toast.makeText(requireContext(), getString(R.string.prompt_msg_selected_all), Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position > 0) {
                int promptIndex = position - 1;
                selectedPromptIndex = promptIndex;
                currentPrompt = availablePrompts.get(promptIndex);
                isTryAllMode = false;

                userPrefs.edit().putInt("last_prompt_index", selectedPromptIndex).apply();

                Toast.makeText(requireContext(), getString(R.string.prompt_msg_selected_one, (promptIndex + 1)), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void generateOutfit() {
        if (selectedPersonUri == null || selectedClothingUri == null) {
            DialogUtils.showDialog(requireContext(),
                    getString(R.string.generate_error_missing_images_title),
                    getString(R.string.generate_error_missing_images_msg));
            return;
        }

        startLoadingAnimation();
        View root = getView();
        if (root == null) return;

        View generateButton = root.findViewById(R.id.generate_button);
        generateButton.setEnabled(false);

        root.findViewById(R.id.result_image).setVisibility(View.GONE);
        root.findViewById(R.id.result_grid_container).setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                final String apiKey = ApiConfig.GEMINI_API_KEY;

                if (isTryAllMode) {
                    List<Bitmap> results = new ArrayList<>();
                    int limit = Math.min(availablePrompts.size(), 4);

                    for (int i = 0; i < limit; i++) {
                        final int currentIdx = i;
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> loadingStageText.setText(getString(R.string.loading_stage_designing_variation, currentIdx + 1)));
                        }

                        byte[] resultBytes = callGeminiAPI(apiKey, selectedPersonUri, selectedClothingUri, availablePrompts.get(i));
                        if (resultBytes != null) {
                            Bitmap bitmap = BitmapFactory.decodeByteArray(resultBytes, 0, resultBytes.length);
                            if (bitmap != null) results.add(bitmap);
                        }
                    }

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            stopLoadingAnimation();
                            if (!results.isEmpty()) {
                                displayGridResults(results);
                                saveToHistory(generatedGridUris);
                            } else {
                                DialogUtils.showDialog(requireContext(), getString(R.string.generate_error_failed_title), getString(R.string.generate_error_failed_msg_plural));
                            }
                            generateButton.setEnabled(true);
                        });
                    }

                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> loadingStageText.setText(getString(R.string.loading_stage_ai_designing)));
                    }
                    byte[] result = callGeminiAPI(apiKey, selectedPersonUri, selectedClothingUri, currentPrompt);

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            stopLoadingAnimation();
                            if (result != null) {
                                displayResult(result);
                                List<Uri> uris = new ArrayList<>();
                                uris.add(generatedImageUri);
                                saveToHistory(uris);
                            } else {
                                DialogUtils.showDialog(requireContext(), getString(R.string.generate_error_failed_title), getString(R.string.generate_error_failed_msg));
                            }
                            generateButton.setEnabled(true);
                        });
                    }
                }

            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        stopLoadingAnimation();
                        DialogUtils.showDialog(requireContext(), getString(R.string.common_error), getString(R.string.generate_error_unexpected, e.getMessage()));
                        generateButton.setEnabled(true);
                    });
                }
            }
        });
    }

    private void saveToHistory(List<Uri> resultUris) {
        if(resultUris == null || resultUris.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < resultUris.size(); i++) {
            sb.append(resultUris.get(i).toString());
            if(i < resultUris.size() - 1) sb.append(",");
        }

        HistoryItem item = new HistoryItem(
                selectedPersonUri.toString(),
                selectedClothingUri.toString(),
                sb.toString()
        );
        dbHelper.addHistoryItem(item, currentUserEmail);
    }

    private void startLoadingAnimation() {
        loadingOverlay.setVisibility(View.VISIBLE);
        currentProgress = 0;
        startTimeMillis = System.currentTimeMillis();
        progressBar.setProgress(0);
        loadingStageText.setText(getString(R.string.loading_stage_preparing));

        progressTimer = new Timer();
        progressTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> updateProgressUI());
                }
            }
        }, 0, 100);
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
            if (elapsedMillis % 200 < 100) currentProgress += 1;
        }

        if (!isTryAllMode) {
            if (currentProgress > 30 && currentProgress < 60) {
                loadingStageText.setText(getString(R.string.loading_stage_designing));
            } else if (currentProgress >= 60 && currentProgress < 90) {
                loadingStageText.setText(getString(R.string.loading_stage_working));
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
        try (Client client = new Client.Builder().apiKey(apiKey).build()) {
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
                        if (blob.data().isPresent()) return blob.data().get();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private byte[] getBytesFromUri(Uri uri) throws IOException {
        try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri)) {
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
        View root = getView();
        if (root == null) return;

        ImageView resultImage = root.findViewById(R.id.result_image);
        GridLayout gridLayout = root.findViewById(R.id.result_grid_container);

        generatedGridUris.clear();

        if (bitmap != null) {
            generatedImageUri = saveBitmapToCacheAndGetUri(bitmap);
            if (generatedImageUri == null) {
                DialogUtils.showDialog(requireContext(), getString(R.string.generate_error_storage_title), getString(R.string.generate_error_cache_failed));
                return;
            }
            generatedGridUris.add(generatedImageUri);

            gridLayout.setVisibility(View.GONE);

            int radius = (int) (15 * getResources().getDisplayMetrics().density);
            Glide.with(this)
                    .load(generatedImageUri)
                    .transform(new FitCenter(), new RoundedCorners(radius))
                    .into(resultImage);

            resultImage.setVisibility(View.VISIBLE);

            ScrollView scrollView = root.findViewById(R.id.scrollView);
            if (scrollView != null) {
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            }
        } else {
            DialogUtils.showDialog(requireContext(), getString(R.string.common_error), getString(R.string.generate_error_decode_failed));
        }
    }

    private void displayGridResults(List<Bitmap> bitmaps) {
        View root = getView();
        if (root == null) return;

        ImageView resultImage = root.findViewById(R.id.result_image);
        GridLayout gridLayout = root.findViewById(R.id.result_grid_container);

        generatedGridUris.clear();

        int[] gridIds = {R.id.grid_image_1, R.id.grid_image_2, R.id.grid_image_3, R.id.grid_image_4};
        for(int id : gridIds) root.findViewById(id).setVisibility(View.GONE);

        for (int i = 0; i < bitmaps.size() && i < gridIds.length; i++) {
            Bitmap bitmap = bitmaps.get(i);
            Uri uri = saveBitmapToCacheAndGetUri(bitmap);
            if (uri != null) {
                generatedGridUris.add(uri);
                ImageView imgView = root.findViewById(gridIds[i]);
                imgView.setImageBitmap(bitmap);
                imgView.setVisibility(View.VISIBLE);
            }
        }

        resultImage.setVisibility(View.GONE);
        gridLayout.setVisibility(View.VISIBLE);

        ScrollView scrollView = root.findViewById(R.id.scrollView);
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void showGeneratedImageDialog(int startIndex) {
        if (generatedGridUris.isEmpty()) return;

        currentVisibleDialogUri = generatedGridUris.get(startIndex);

        View overlayView = getLayoutInflater().inflate(R.layout.view_image_overlay, null);

        final PhotoViewDialog.Builder<Uri> builder = new PhotoViewDialog.Builder<>(
                requireActivity(),
                generatedGridUris,
                (imageView, uri) -> Glide.with(GenerateFragment.this).load(uri).into(imageView)
        );

        builder.withStartPosition(startIndex);
        builder.withOverlayView(overlayView);
        builder.withImageChangeListener(position -> currentVisibleDialogUri = generatedGridUris.get(position));

        final PhotoViewDialog<Uri> dialog = builder.build();

        ImageButton backButton = overlayView.findViewById(R.id.button_back);
        backButton.setOnClickListener(v -> dialog.dismiss());

        ImageButton saveButton = overlayView.findViewById(R.id.button_save);
        saveButton.setOnClickListener(v -> ImageSaveHelper.checkPermissionAndSave(requireActivity(), currentVisibleDialogUri));

        dialog.show();
    }

    private Uri saveBitmapToCacheAndGetUri(Bitmap bitmap) {
        File imageFile = new File(requireContext().getCacheDir(), "generated_outfit_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            return FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", imageFile);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void checkCameraPermissionAndOpenCamera() {
        if (!requireContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            DialogUtils.showDialog(requireContext(), getString(R.string.common_error), getString(R.string.generate_error_camera_missing));
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            openCamera();
        }
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(requireContext().getPackageManager()) == null) {
            DialogUtils.showDialog(requireContext(), getString(R.string.common_error), getString(R.string.generate_error_camera_missing));
            return;
        }
        tempImageUri = createImageUri();
        if (tempImageUri != null) {
            takePictureLauncher.launch(tempImageUri);
        } else {
            DialogUtils.showDialog(requireContext(), getString(R.string.generate_error_storage_title), getString(R.string.generate_error_temp_file));
        }
    }

    private Uri createImageUri() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "OutfitAI_Cam_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "OutfitAI");
        }
        return requireContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                DialogUtils.showDialog(requireContext(), getString(R.string.generate_permission_camera), getString(R.string.generate_permission_camera_msg));
            }
        } else if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchSpeechRecognizer();
            } else {
                Toast.makeText(requireContext(), "Microphone permission is required for speech recognition", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == ImageSaveHelper.WRITE_STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (currentVisibleDialogUri != null) {
                    ImageSaveHelper.saveImageToGallery(requireActivity(), currentVisibleDialogUri);
                } else if (generatedImageUri != null) {
                    ImageSaveHelper.saveImageToGallery(requireActivity(), generatedImageUri);
                }
            } else {
                DialogUtils.showDialog(requireContext(), getString(R.string.generate_permission_camera), getString(R.string.generate_permission_storage_msg));
            }
        }
    }

    private void displaySelectedImage(Uri imageUri) {
        View root = getView();
        if (root == null) return;

        if (currentImageTarget == ImageTarget.PERSON) {
            selectedPersonUri = imageUri;
            ImageView personSelectedImage = root.findViewById(R.id.person_selected_image);
            ImageView personIcon = root.findViewById(R.id.person_icon);
            ImageView personUploadIcon = root.findViewById(R.id.person_uploadIcon);
            personSelectedImage.setImageURI(imageUri);
            personSelectedImage.setVisibility(View.VISIBLE);
            personIcon.setVisibility(View.GONE);
            personUploadIcon.setVisibility(View.GONE);
        } else if (currentImageTarget == ImageTarget.CLOTHING) {
            selectedClothingUri = imageUri;
            ImageView clothingSelectedImage = root.findViewById(R.id.clothing_selected_image);
            ImageView clothingIcon = root.findViewById(R.id.clothing_icon);
            ImageView clothingUploadIcon = root.findViewById(R.id.clothing_uploadIcon);
            clothingSelectedImage.setImageURI(imageUri);
            clothingSelectedImage.setVisibility(View.VISIBLE);
            clothingIcon.setVisibility(View.GONE);
            clothingUploadIcon.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
    }
}