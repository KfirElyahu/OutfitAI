package com.kfir.outfitai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FeedbackAdapter extends RecyclerView.Adapter<FeedbackAdapter.FeedbackViewHolder> {

    private final Context context;
    private final List<Feedback> feedbackList;
    private final Map<String, Bitmap> profileCache = new HashMap<>();

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

        holder.username.setText(feedback.getUsername() != null && !feedback.getUsername().isEmpty() ? feedback.getUsername() : context.getString(R.string.feedback_anonymous));

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

        holder.profileImage.setImageResource(R.drawable.person_icon);
        holder.profileImage.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));

        String userId = feedback.getUserId();
        if (userId != null && !userId.equals("anonymous")) {
            if (profileCache.containsKey(userId)) {
                holder.profileImage.setImageTintList(null);
                Glide.with(context)
                        .load(profileCache.get(userId))
                        .transform(new CircleCrop())
                        .into(holder.profileImage);
            } else {
                FirebaseFirestore.getInstance().collection("profile_images").document(userId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                String base64 = documentSnapshot.getString("base64");
                                if (base64 != null && !base64.isEmpty()) {
                                    try {
                                        byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
                                        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                                        profileCache.put(userId, decodedByte);

                                        holder.profileImage.setImageTintList(null);
                                        Glide.with(context)
                                                .load(decodedByte)
                                                .transform(new CircleCrop())
                                                .into(holder.profileImage);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        });
            }
        }
    }

    @Override
    public int getItemCount() {
        return feedbackList.size();
    }

    public static class FeedbackViewHolder extends RecyclerView.ViewHolder {
        TextView username, date, feedbackText;
        ImageView star1, star2, star3, star4, star5;
        ShapeableImageView profileImage;

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
            profileImage = itemView.findViewById(R.id.feedback_profile_image);
        }
    }
}