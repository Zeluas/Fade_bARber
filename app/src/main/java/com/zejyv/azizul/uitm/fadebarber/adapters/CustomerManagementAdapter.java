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
import com.zejyv.azizul.uitm.fadebarber.models.Customer;

import java.util.List;

public class CustomerManagementAdapter extends RecyclerView.Adapter<CustomerManagementAdapter.ViewHolder> {

    private final List<Customer> customers;
    private final OnCustomerActionListener listener;

    public interface OnCustomerActionListener {
        void onEdit(Customer customer);
        void onDelete(Customer customer);
        void onPicClick(String imageUrl);
    }

    public CustomerManagementAdapter(List<Customer> customers, OnCustomerActionListener listener) {
        this.customers = customers;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_customer_management, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Customer customer = customers.get(position);
        
        holder.tvName.setText(customer.getName());
        holder.tvUsername.setText("@" + customer.getUsername());
        holder.tvUid.setText("UID: " + customer.getUid());
        
        // Phone formatting logic
        String rawPhone = customer.getPhone();
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
                .load(customer.getProfilePicUrl())
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(holder.ivPic);

        holder.ivPic.setOnClickListener(v -> listener.onPicClick(customer.getProfilePicUrl()));
        holder.llEditActions.setOnClickListener(v -> listener.onEdit(customer));
    }

    @Override
    public int getItemCount() {
        return customers.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPic;
        View llEditActions;
        TextView tvName, tvUsername, tvPhone, tvUid;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPic = itemView.findViewById(R.id.iv_customer_pic);
            llEditActions = itemView.findViewById(R.id.ll_edit_actions);
            tvName = itemView.findViewById(R.id.tv_customer_name);
            tvUsername = itemView.findViewById(R.id.tv_customer_username);
            tvPhone = itemView.findViewById(R.id.tv_customer_phone);
            tvUid = itemView.findViewById(R.id.tv_customer_uid);
        }
    }
}
