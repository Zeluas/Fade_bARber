package com.zejyv.azizul.uitm.fadebarber.adapters;

import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.firebase.firestore.FirebaseFirestore;
import com.zejyv.azizul.uitm.fadebarber.R;
import com.zejyv.azizul.uitm.fadebarber.models.Employee;

import java.util.List;

public class StylistAdapter extends RecyclerView.Adapter<StylistAdapter.StylistViewHolder> {

    private final List<Employee> stylistList;
    private int selectedPosition = -1;
    private OnStylistSelectedListener listener;
    private final boolean isSelectionEnabled;

    public interface OnStylistSelectedListener {
        void onStylistSelected(Employee employee);
    }

    public StylistAdapter(List<Employee> stylistList, OnStylistSelectedListener listener) {
        this(stylistList, true, listener);
    }

    public StylistAdapter(List<Employee> stylistList, boolean isSelectionEnabled, OnStylistSelectedListener listener) {
        this.stylistList = stylistList;
        this.isSelectionEnabled = isSelectionEnabled;
        this.listener = listener;
    }

    @NonNull
    @Override
    public StylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_stylist_selection, parent, false);
        return new StylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StylistViewHolder holder, int position) {
        Employee employee = stylistList.get(position);
        holder.tvName.setText(employee.getFullname());
        String specialty = employee.getSpecialty();
        if (specialty == null || specialty.trim().isEmpty()) {
            holder.tvSpecialty.setText(Html.fromHtml("Specialty: <i>None</i>", Html.FROM_HTML_MODE_LEGACY));
        } else {
            holder.tvSpecialty.setText(String.format("Specialty: %s", specialty));
        }
        holder.rbRating.setRating(0); // Default rating as 0 for now
        holder.tvRatingVal.setText("0.0");

        // Load profile picture from profile_pics collection
        FirebaseFirestore.getInstance().collection("profile_pics").document(employee.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String url = documentSnapshot.getString("url");
                        if (url != null && !url.isEmpty()) {
                            Glide.with(holder.itemView.getContext())
                                    .load(url)
                                    .placeholder(R.drawable.ic_profile)
                                    .into(holder.ivStylist);
                        } else {
                            holder.ivStylist.setImageResource(R.drawable.ic_profile);
                        }
                    } else {
                        holder.ivStylist.setImageResource(R.drawable.ic_profile);
                    }
                })
                .addOnFailureListener(e -> holder.ivStylist.setImageResource(R.drawable.ic_profile));

        // Fetch and calculate average rating for the stylist in real-time
        FirebaseFirestore.getInstance().collection("hairstylist_ratings")
                .whereEqualTo("employeeId", employee.getUid())
                .addSnapshotListener((queryDocumentSnapshots, e) -> {
                    if (e != null) {
                        holder.rbRating.setRating(0);
                        holder.tvRatingVal.setText("0.0");
                        return;
                    }
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        double totalRating = 0;
                        int count = queryDocumentSnapshots.size();
                        for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                            Double rating = doc.getDouble("rating");
                            if (rating != null) totalRating += rating;
                        }
                        float average = (float) (totalRating / count);
                        holder.rbRating.setRating(average);
                        holder.tvRatingVal.setText(String.format(java.util.Locale.getDefault(), "%.1f", average));
                    } else {
                        holder.rbRating.setRating(0);
                        holder.tvRatingVal.setText("0.0");
                    }
                });

        if (isSelectionEnabled) {
            holder.cbSelected.setVisibility(View.VISIBLE);
            holder.cbSelected.setChecked(position == selectedPosition);
            holder.rootContainer.setOnClickListener(v -> {
                int previousSelected = selectedPosition;
                selectedPosition = holder.getBindingAdapterPosition();
                if (selectedPosition != RecyclerView.NO_POSITION) {
                    if (previousSelected != -1) {
                        notifyItemChanged(previousSelected);
                    }
                    notifyItemChanged(selectedPosition);
                    if (listener != null) {
                        listener.onStylistSelected(employee);
                    }
                }
            });
        } else {
            holder.cbSelected.setVisibility(View.GONE);
            holder.rootContainer.setOnClickListener(null);
            holder.rootContainer.setClickable(false);
            holder.rootContainer.setFocusable(false);
        }

        // Hide divider for the last item
        holder.divider.setVisibility(position == stylistList.size() - 1 ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return stylistList.size();
    }

    public void setSelectedIndex(int position) {
        if (position >= 0 && position < stylistList.size()) {
            int previousSelected = selectedPosition;
            selectedPosition = position;
            if (previousSelected != -1) {
                notifyItemChanged(previousSelected);
            }
            notifyItemChanged(selectedPosition);
        }
    }

    public Employee getSelectedStylist() {
        if (selectedPosition != -1 && selectedPosition < stylistList.size()) {
            return stylistList.get(selectedPosition);
        }
        return null;
    }

    static class StylistViewHolder extends RecyclerView.ViewHolder {
        View rootContainer;
        TextView tvName, tvSpecialty, tvRatingVal;
        RatingBar rbRating;
        MaterialCheckBox cbSelected;
        ImageView ivStylist;
        View divider;

        public StylistViewHolder(@NonNull View itemView) {
            super(itemView);
            rootContainer = itemView.findViewById(R.id.ll_stylist_item);
            tvName = itemView.findViewById(R.id.tv_stylist_name);
            tvSpecialty = itemView.findViewById(R.id.tv_stylist_specialty);
            tvRatingVal = itemView.findViewById(R.id.tv_stylist_rating_val);
            rbRating = itemView.findViewById(R.id.rb_stylist_rating);
            cbSelected = itemView.findViewById(R.id.cb_stylist);
            ivStylist = itemView.findViewById(R.id.iv_stylist_img);
            divider = itemView.findViewById(R.id.v_stylist_divider);
        }
    }
}
