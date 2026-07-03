package com.zejyv.azizul.uitm.fadebarber.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.zejyv.azizul.uitm.fadebarber.R;
import com.zejyv.azizul.uitm.fadebarber.models.Employee;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ColleagueAdapter extends RecyclerView.Adapter<ColleagueAdapter.ColleagueViewHolder> {

    public interface OnColleagueClickListener {
        void onPhoneClick(String rawPhone);
        void onUidClick(String uid);
    }

    private final List<Employee> colleagueList;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
    private boolean isShowingAdmins = false;
    private OnColleagueClickListener listener;

    public ColleagueAdapter(List<Employee> colleagueList) {
        this.colleagueList = colleagueList;
    }

    public void setOnColleagueClickListener(OnColleagueClickListener listener) {
        this.listener = listener;
    }

    public void setShowingAdmins(boolean showingAdmins) {
        this.isShowingAdmins = showingAdmins;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ColleagueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_colleague, parent, false);
        return new ColleagueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ColleagueViewHolder holder, int position) {
        Employee employee = colleagueList.get(position);

        holder.tvFullname.setText(employee.getFullname());
        holder.tvShortname.setText("@" + employee.getShortname());
        holder.tvUid.setText("UID: " + employee.getUid());

        // Specialty binding
        String specialty = employee.getSpecialty();
        if (specialty == null || specialty.trim().isEmpty()) {
            holder.tvSpecialty.setText("Not set");
            holder.tvSpecialty.setTypeface(null, android.graphics.Typeface.ITALIC);
        } else {
            holder.tvSpecialty.setText(specialty);
            holder.tvSpecialty.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
        
        // Rating binding
        if (isShowingAdmins) {
            holder.ratingContainer.setVisibility(View.GONE);
        } else {
            holder.ratingContainer.setVisibility(View.VISIBLE);
            if (employee.getOverallRating() > 0) {
                holder.tvRating.setText(String.format(Locale.getDefault(), "%.1f", employee.getOverallRating()));
                holder.tvRating.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                holder.tvRating.setText("N/A");
                holder.tvRating.setTypeface(null, android.graphics.Typeface.ITALIC);
            }
        }

        // Phone formatting logic from MainActivityEmployee
        String rawPhone = employee.getPhone();
        String formattedPhone = rawPhone;
        if (rawPhone != null && !rawPhone.isEmpty()) {
            String digits = rawPhone;
            if (rawPhone.startsWith("0")) {
                digits = rawPhone.substring(1);
            } else if (rawPhone.startsWith("+60")) {
                digits = rawPhone.substring(3);
            } else if (rawPhone.startsWith("60")) {
                digits = rawPhone.substring(2);
            }

            if (digits.length() >= 6) {
                StringBuilder sb = new StringBuilder("+60 ");
                sb.append(digits.substring(0, 2));
                sb.append("-");
                sb.append(digits.substring(2, 6));

                String remaining = digits.substring(6);
                for (int i = 0; i < remaining.length(); i++) {
                    if (i > 0 && i % 4 == 0) sb.append(" ");
                    if (i == 0) sb.append(" ");
                    sb.append(remaining.charAt(i));
                }
                formattedPhone = sb.toString();
            } else {
                formattedPhone = "+60 " + digits;
            }
        }
        holder.tvPhone.setText(formattedPhone);

        if (employee.getJoinedDate() != null) {
            String joined = "Joined: " + dateFormat.format(employee.getJoinedDate().toDate());
            holder.tvJoined.setText(joined);
        } else {
            holder.tvJoined.setText("Joined: Unknown");
        }

        Glide.with(holder.itemView.getContext())
                .load(employee.getProfilePicUrl())
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(holder.ivPic);

        if (listener != null) {
            holder.phoneContainer.setOnClickListener(v -> listener.onPhoneClick(employee.getPhone()));
            holder.tvUid.setOnClickListener(v -> listener.onUidClick(employee.getUid()));
        }
    }

    @Override
    public int getItemCount() {
        return colleagueList.size();
    }

    static class ColleagueViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPic;
        TextView tvFullname, tvShortname, tvSpecialty, tvPhone, tvUid, tvJoined, tvRating;
        View ratingContainer, phoneContainer;

        public ColleagueViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPic = itemView.findViewById(R.id.iv_colleague_pic);
            tvFullname = itemView.findViewById(R.id.tv_colleague_fullname);
            tvShortname = itemView.findViewById(R.id.tv_colleague_shortname);
            tvSpecialty = itemView.findViewById(R.id.tv_colleague_specialty);
            tvPhone = itemView.findViewById(R.id.tv_colleague_phone);
            tvUid = itemView.findViewById(R.id.tv_colleague_uid);
            tvJoined = itemView.findViewById(R.id.tv_colleague_joined);
            tvRating = itemView.findViewById(R.id.tv_colleague_rating);
            ratingContainer = itemView.findViewById(R.id.ll_rating_container);
            phoneContainer = itemView.findViewById(R.id.ll_phone_container);
        }
    }
}
