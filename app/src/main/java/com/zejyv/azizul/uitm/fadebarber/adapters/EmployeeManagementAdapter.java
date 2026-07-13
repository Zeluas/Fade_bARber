package com.zejyv.azizul.uitm.fadebarber.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.bumptech.glide.Glide;
import com.zejyv.azizul.uitm.fadebarber.R;
import com.zejyv.azizul.uitm.fadebarber.models.Employee;

import java.util.List;
import java.util.Locale;

public class EmployeeManagementAdapter extends RecyclerView.Adapter<EmployeeManagementAdapter.ViewHolder> {

    private final List<Employee> employees;
    private final OnEmployeeActionListener listener;

    public interface OnEmployeeActionListener {
        void onEdit(Employee employee);
        void onDelete(Employee employee);
        void onPicClick(String imageUrl);
    }

    public EmployeeManagementAdapter(List<Employee> employees, OnEmployeeActionListener listener) {
        this.employees = employees;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_employee_management, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Employee employee = employees.get(position);
        
        holder.tvName.setText(employee.getFullname());
        holder.tvShortname.setText("@" + employee.getShortname());
        holder.tvUid.setText("UID: " + employee.getUid());
        
        if (employee.getSpecialty() != null && !employee.getSpecialty().trim().isEmpty()) {
            holder.tvSpecialty.setText(employee.getSpecialty());
            holder.tvSpecialty.setTypeface(null, android.graphics.Typeface.NORMAL);
        } else {
            holder.tvSpecialty.setText("None");
            holder.tvSpecialty.setTypeface(null, android.graphics.Typeface.ITALIC);
        }
        
        if (employee.getOverallRating() > 0) {
            holder.tvRating.setText(String.format(Locale.getDefault(), "%.1f", employee.getOverallRating()));
        } else {
            holder.tvRating.setText("N/A");
        }

        // Phone formatting logic
        String rawPhone = employee.getPhone();
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
                .load(employee.getProfilePicUrl())
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(holder.ivPic);

        holder.ivPic.setOnClickListener(v -> listener.onPicClick(employee.getProfilePicUrl()));
        holder.llEditActions.setOnClickListener(v -> listener.onEdit(employee));
    }

    @Override
    public int getItemCount() {
        return employees.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPic;
        View llEditActions;
        TextView tvName, tvShortname, tvSpecialty, tvRating, tvPhone, tvUid;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPic = itemView.findViewById(R.id.iv_employee_pic);
            llEditActions = itemView.findViewById(R.id.ll_edit_actions);
            tvName = itemView.findViewById(R.id.tv_employee_name);
            tvShortname = itemView.findViewById(R.id.tv_employee_shortname);
            tvSpecialty = itemView.findViewById(R.id.tv_employee_specialty);
            tvRating = itemView.findViewById(R.id.tv_employee_rating);
            tvPhone = itemView.findViewById(R.id.tv_employee_phone);
            tvUid = itemView.findViewById(R.id.tv_employee_uid);
        }
    }
}
