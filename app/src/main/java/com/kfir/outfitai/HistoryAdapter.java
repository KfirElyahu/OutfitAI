package com.kfir.outfitai;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private final Context context;
    private final List<HistoryItem> historyList;
    private final OnHistoryItemClickListener listener;

    public interface OnHistoryItemClickListener {
        void onResultClick(List<Uri> resultUris);
    }

    public HistoryAdapter(Context context, List<HistoryItem> historyList, OnHistoryItemClickListener listener) {
        this.context = context;
        this.historyList = historyList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_history_card, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        HistoryItem item = historyList.get(position);

        Uri personUri = Uri.parse(item.getPersonUri());
        Uri clothUri = Uri.parse(item.getClothUri());

        String[] uriStrings = item.getResultUris().split(",");
        List<Uri> resultUris = new ArrayList<>();
        for(String s : uriStrings) {
            if(!s.trim().isEmpty()) resultUris.add(Uri.parse(s.trim()));
        }

        Glide.with(context).load(personUri).into(holder.personImage);
        Glide.with(context).load(clothUri).into(holder.clothImage);

        if (!resultUris.isEmpty()) {
            Glide.with(context).load(resultUris.get(0)).into(holder.resultImage);
        }

        holder.gridIndicator.setVisibility(resultUris.size() > 1 ? View.VISIBLE : View.GONE);

        holder.resultImage.setOnClickListener(v -> listener.onResultClick(resultUris));
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    public static class HistoryViewHolder extends RecyclerView.ViewHolder {
        ImageView personImage, clothImage, resultImage, gridIndicator;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            personImage = itemView.findViewById(R.id.history_person_image);
            clothImage = itemView.findViewById(R.id.history_cloth_image);
            resultImage = itemView.findViewById(R.id.history_result_image);
            gridIndicator = itemView.findViewById(R.id.grid_indicator);
        }
    }
}