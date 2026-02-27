package com.kfir.outfitai;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FeedbackAdapter extends RecyclerView.Adapter<FeedbackAdapter.FeedbackViewHolder> {

    private final Context context;
    private final List<Feedback> feedbackList;

    public FeedbackAdapter(Context context, List<Feedback> feedbackList) {
        this.context = context;
        this.feedbackList = feedbackList;
    }

    @NonNull
    @Override
    public FeedbackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_feedback_card, parent, false);
        return new FeedbackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FeedbackViewHolder holder, int position) {
        Feedback feedback = feedbackList.get(position);

        holder.username.setText(feedback.getUsername() != null && !feedback.getUsername().isEmpty() ? feedback.getUsername() : "Anonymous");

        if (feedback.getTimestamp() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            holder.date.setText(sdf.format(new Date(feedback.getTimestamp())));
        } else {
            holder.date.setText("");
        }

        int rating = feedback.getRating();
        ImageView[] stars = {holder.star1, holder.star2, holder.star3, holder.star4, holder.star5};
        for (int i = 0; i < 5; i++) {
            if (i < rating) {
                stars[i].setImageResource(R.drawable.ic_star_filled);
            } else {
                stars[i].setImageResource(R.drawable.ic_star_outline);
            }
        }

        String text = feedback.getFeedback();
        if (text != null && !text.trim().isEmpty()) {
            holder.feedbackText.setText(text.trim());
            holder.feedbackText.setVisibility(View.VISIBLE);
        } else {
            holder.feedbackText.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return feedbackList.size();
    }

    public static class FeedbackViewHolder extends RecyclerView.ViewHolder {
        TextView username, date, feedbackText;
        ImageView star1, star2, star3, star4, star5;

        public FeedbackViewHolder(@NonNull View itemView) {
            super(itemView);
            username = itemView.findViewById(R.id.feedback_username);
            date = itemView.findViewById(R.id.feedback_date);
            feedbackText = itemView.findViewById(R.id.feedback_text);
            star1 = itemView.findViewById(R.id.star1);
            star2 = itemView.findViewById(R.id.star2);
            star3 = itemView.findViewById(R.id.star3);
            star4 = itemView.findViewById(R.id.star4);
            star5 = itemView.findViewById(R.id.star5);
        }
    }
}