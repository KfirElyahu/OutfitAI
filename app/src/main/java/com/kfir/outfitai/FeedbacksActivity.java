package com.kfir.outfitai;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FeedbacksActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private FeedbackAdapter adapter;
    private List<Feedback> feedbackList;
    private ProgressBar progressBar;
    private TextView emptyText;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedbacks);

        db = FirebaseFirestore.getInstance();

        ImageButton backButton = findViewById(R.id.Back_button);
        backButton.setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.feedbacks_recycler_view);
        progressBar = findViewById(R.id.feedbacks_progress_bar);
        emptyText = findViewById(R.id.empty_feedbacks_text);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        feedbackList = new ArrayList<>();
        adapter = new FeedbackAdapter(this, feedbackList);
        recyclerView.setAdapter(adapter);

        fetchFeedbacks();
    }

    private void fetchFeedbacks() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, getString(R.string.feedbacks_no_internet), Toast.LENGTH_SHORT).show();
            emptyText.setVisibility(View.VISIBLE);
            return;
        }

        db.collection("app_ratings")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        feedbackList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Feedback feedback = document.toObject(Feedback.class);
                            feedbackList.add(feedback);
                        }
                        adapter.notifyDataSetChanged();

                        if (feedbackList.isEmpty()) {
                            emptyText.setVisibility(View.VISIBLE);
                        } else {
                            emptyText.setVisibility(View.GONE);
                        }
                    } else {
                        Toast.makeText(this, getString(R.string.feedbacks_load_failed), Toast.LENGTH_SHORT).show();
                        emptyText.setVisibility(View.VISIBLE);
                    }
                });
    }
}