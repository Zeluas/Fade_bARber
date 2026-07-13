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
import com.zejyv.azizul.uitm.fadebarber.models.Admin;

import java.util.List;

public class AdminManagementAdapter extends RecyclerView.Adapter<AdminManagementAdapter.ViewHolder> {

    private final List<Admin> admins;
    private final OnAdminActionListener listener;

    public interface OnAdminActionListener {
        void onEdit(Admin admin);
        void onDelete(Admin admin);
        void onPicClick(String imageUrl);
    }

    public AdminManagementAdapter(List<Admin> admins, OnAdminActionListener listener) {
        this.admins = admins;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_management, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Admin admin = admins.get(position);
        
        holder.tvName.setText(admin.getFullname());
        holder.tvShortname.setText("@" + admin.getShortname());
        holder.tvUid.setText("UID: " + admin.getUid());
        
        // Phone formatting logic
        String rawPhone = admin.getPhone();
        String formattedPhone = rawPhone;
        if (rawPhone != null && !rawPhone.isEmpty()) {
            String digits = rawPhone;
            if (rawPhone.startsWith("0")) digits = rawPhone.substring(1);
            else if (rawPhone.startsWith("+60")) digits = rawPhone.substring(3);
            else if (rawPhone.startsWith("60")) digits = rawPhone.substring(2);

            if (digits.length() >= 6) {
                StringBuilder sb = new StringBuilder("+60 ");
                sb.append(digits.substring(0, 2)).append("-").append(digits.substring(2, 6));
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

        Glide.with(holder.itemView.getContext())
                .load(admin.getProfilePicUrl())
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(holder.ivPic);

        holder.ivPic.setOnClickListener(v -> listener.onPicClick(admin.getProfilePicUrl()));
        holder.llEditActions.setOnClickListener(v -> listener.onEdit(admin));
    }

    @Override
    public int getItemCount() {
        return admins.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPic;
        View llEditActions;
        TextView tvName, tvShortname, tvPhone, tvUid;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPic = itemView.findViewById(R.id.iv_admin_pic);
            llEditActions = itemView.findViewById(R.id.ll_edit_actions);
            tvName = itemView.findViewById(R.id.tv_admin_name);
            tvShortname = itemView.findViewById(R.id.tv_admin_shortname);
            tvPhone = itemView.findViewById(R.id.tv_admin_phone);
            tvUid = itemView.findViewById(R.id.tv_admin_uid);
        }
    }
}
