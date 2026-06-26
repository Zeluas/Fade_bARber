package com.zejyv.azizul.uitm.fadebarber.adapters;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.zejyv.azizul.uitm.fadebarber.EmployeeBookFragment;
import com.zejyv.azizul.uitm.fadebarber.MainActivityEmployee;
import com.zejyv.azizul.uitm.fadebarber.R;
import com.zejyv.azizul.uitm.fadebarber.models.Booking;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public class EmployeeBookingAdapter extends RecyclerView.Adapter<EmployeeBookingAdapter.BookingViewHolder> {

    private List<EmployeeBookFragment.BookingItem> bookings;
    private final Context context;
    private final FirebaseFirestore db;
    private final OnBookingActionListener actionListener;
    private int expandedPosition = -1;

    public interface OnBookingActionListener {
        void onCancelBooking(Booking booking);
        void onEditBooking(Booking booking);
    }

    public EmployeeBookingAdapter(Context context, List<EmployeeBookFragment.BookingItem> bookings, OnBookingActionListener actionListener) {
        this.context = context;
        this.bookings = bookings;
        this.db = FirebaseFirestore.getInstance();
        this.actionListener = actionListener;
    }

    public void updateData(List<EmployeeBookFragment.BookingItem> newBookings) {
        this.bookings = newBookings;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_employee_booking, parent, false);
        return new BookingViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
        EmployeeBookFragment.BookingItem item = bookings.get(position);
        Booking booking = item.booking;
        boolean isExpanded = position == expandedPosition;
        
        holder.tvId.setText("#" + booking.getBookingId());
        holder.tvStatus.setText(booking.getStatus());
        holder.tvDate.setText(booking.getDate());
        holder.tvTime.setText(booking.getTime());
        holder.tvHaircutName.setText(booking.getHairstyleName());

        // Set Status Badge
        setStatusBadge(holder.tvStatus, booking.getStatus());
        
        // Load Hairstyle (Small)
        loadHaircutImage(booking.getHairstyleName(), holder.ivHaircutSmall);

        // Reset height and visibility based on state
        ViewGroup.LayoutParams layoutParams = holder.llExpandable.getLayoutParams();
        if (isExpanded) {
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            holder.llExpandable.setVisibility(View.VISIBLE);
            holder.llCollapsedRating.setVisibility(View.GONE);
            holder.ivArrow.setRotation(0);
        } else {
            layoutParams.height = 0;
            holder.llExpandable.setVisibility(View.GONE);
            holder.llCollapsedRating.setVisibility(View.VISIBLE);
            holder.ivArrow.setRotation(180);
        }
        holder.llExpandable.setLayoutParams(layoutParams);

        View.OnClickListener toggleExpand = v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;

            int prevExpanded = expandedPosition;
            boolean expanding = (expandedPosition != currentPos);
            expandedPosition = expanding ? currentPos : -1;

            if (expanding) {
                holder.llExpandable.setVisibility(View.VISIBLE);
                holder.llExpandable.measure(View.MeasureSpec.makeMeasureSpec(holder.itemView.getWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int targetHeight = holder.llExpandable.getMeasuredHeight();
                ValueAnimator animator = ValueAnimator.ofInt(0, targetHeight);
                animator.setDuration(450);
                animator.setInterpolator(new DecelerateInterpolator());
                animator.addUpdateListener(animation -> {
                    int val = (int) animation.getAnimatedValue();
                    ViewGroup.LayoutParams lp = holder.llExpandable.getLayoutParams();
                    lp.height = val;
                    holder.llExpandable.setLayoutParams(lp);
                    float alpha = 1.0f - ((float) val / targetHeight);
                    holder.llCollapsedRating.setAlpha(alpha);
                    if (alpha < 0.05f) holder.llCollapsedRating.setVisibility(View.GONE);
                });
                animator.start();
                holder.ivArrow.animate().rotation(0).setDuration(450).start();
            } else {
                int initialHeight = holder.llExpandable.getHeight();
                ValueAnimator animator = ValueAnimator.ofInt(initialHeight, 0);
                animator.setDuration(450);
                animator.setInterpolator(new DecelerateInterpolator());
                animator.addUpdateListener(animation -> {
                    int val = (int) animation.getAnimatedValue();
                    ViewGroup.LayoutParams lp = holder.llExpandable.getLayoutParams();
                    lp.height = val;
                    holder.llExpandable.setLayoutParams(lp);
                    float alpha = 1.0f - ((float) val / initialHeight);
                    holder.llCollapsedRating.setVisibility(View.VISIBLE);
                    holder.llCollapsedRating.setAlpha(alpha);
                    if (val == 0) holder.llExpandable.setVisibility(View.GONE);
                });
                animator.start();
                holder.ivArrow.animate().rotation(180).setDuration(450).start();
            }
            if (prevExpanded != -1 && prevExpanded != currentPos) notifyItemChanged(prevExpanded);
        };

        holder.ivArrow.setOnClickListener(toggleExpand);
        holder.itemView.setOnClickListener(toggleExpand);

        // Fetch Rating, Comment, Amount & Timer
        if ("Completed".equalsIgnoreCase(booking.getStatus())) {
            holder.rbRating.setRating(item.rating);
            holder.tvCollapsedRating.setText(String.format(Locale.getDefault(), "%.1f", item.rating));
            holder.tvComment.setText(item.comment != null && !item.comment.isEmpty() ? item.comment : "No comments provided.");
            holder.tvAmount.setText(String.format(Locale.getDefault(), "RM %.2f", item.amount));
            holder.tvSessionTimer.setText(formatDuration(item.durationMillis));
        } else {
            holder.rbRating.setRating(0);
            holder.tvCollapsedRating.setText("---");
            holder.tvComment.setText("No comments provided.");
            holder.tvAmount.setText("RM --.--");
            holder.tvSessionTimer.setText("--:--");
        }

        // Customer Info
        holder.tvCustomerName.setText(item.customerName);
        holder.ivCall.setOnClickListener(v -> {
            if (context instanceof MainActivityEmployee) ((MainActivityEmployee) context).showCallCustomerDialog(item.customerPhone);
        });

        // Profile Pic
        if (item.customerProfileUrl != null && !item.customerProfileUrl.isEmpty()) {
            Glide.with(context).load(item.customerProfileUrl).placeholder(R.drawable.ic_profile).into(holder.ivProfile);
            holder.ivProfile.setOnClickListener(v -> {
                if (context instanceof MainActivityEmployee) ((MainActivityEmployee) context).showImagePreview(item.customerProfileUrl);
            });
        } else {
            holder.ivProfile.setImageResource(R.drawable.ic_profile);
        }

        holder.btnCancel.setOnClickListener(v -> { if (actionListener != null) actionListener.onCancelBooking(booking); });
        holder.btnEdit.setOnClickListener(v -> { if (actionListener != null) actionListener.onEditBooking(booking); });

        if ("Pending".equalsIgnoreCase(booking.getStatus())) {
            holder.btnCancel.setVisibility(View.VISIBLE);
            holder.btnEdit.setVisibility(View.VISIBLE);
            holder.llButtons.setVisibility(View.VISIBLE);
        } else {
            holder.btnCancel.setVisibility(View.GONE);
            holder.btnEdit.setVisibility(View.GONE);
            holder.llButtons.setVisibility(View.GONE);
        }
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long m = seconds / 60;
        long h = m / 60;
        m = m % 60;
        seconds = seconds % 60;
        if (h > 0) return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, seconds);
        else return String.format(Locale.getDefault(), "%02d:%02d", m, seconds);
    }

    private void setStatusBadge(TextView tv, String status) {
        if (status == null) return;
        tv.setText(status);
        int colorRes = R.color.primary_color;

        switch (status) {
            case "Pending":
                colorRes = R.color.alert_yellow_icon;
                break;
            case "Completed":
                colorRes = R.color.success_green_icon;
                break;
            case "Cancelled":
                colorRes = R.color.warning_red_icon;
                break;
            case "Starting":
                colorRes = R.color.info_blue_icon;
                break;
        }
        tv.setTextColor(androidx.core.content.ContextCompat.getColor(tv.getContext(), R.color.white));
        tv.setBackgroundTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(tv.getContext(), colorRes)));
    }

    private void loadHaircutImage(String name, ImageView imageView) {
        if (name == null) { imageView.setImageResource(R.drawable.ic_hair); return; }
        try {
            String[] images = context.getAssets().list("images");
            if (images != null) {
                String cleanName = name.toLowerCase().replace(" ", "").replace("-", "");
                for (String imageName : images) {
                    String cleanImg = imageName.toLowerCase().split("\\.")[0].replace(" ", "").replace("-", "");
                    if (cleanName.contains(cleanImg) || cleanImg.contains(cleanName)) {
                        try (InputStream is = context.getAssets().open("images/" + imageName)) {
                            Bitmap bitmap = BitmapFactory.decodeStream(is);
                            imageView.setImageBitmap(bitmap);
                            return;
                        }
                    }
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
        imageView.setImageResource(R.drawable.ic_hair);
    }

    @Override
    public int getItemCount() { return bookings.size(); }

    static class BookingViewHolder extends RecyclerView.ViewHolder {
        TextView tvStatus, tvId, tvCustomerName, tvDate, tvTime, tvHaircutName, tvCollapsedRating, tvComment, tvAmount, tvSessionTimer;
        ImageView ivProfile, ivHaircutSmall, ivCall, ivArrow;
        MaterialButton btnCancel, btnEdit;
        LinearLayout llExpandable, llCollapsedRating, llButtons;
        RatingBar rbRating;

        BookingViewHolder(View v) {
            super(v);
            tvStatus = v.findViewById(R.id.tv_booking_status);
            tvId = v.findViewById(R.id.tv_booking_id);
            tvCustomerName = v.findViewById(R.id.tv_history_barber);
            tvDate = v.findViewById(R.id.tv_history_date);
            tvTime = v.findViewById(R.id.tv_history_time);
            tvHaircutName = v.findViewById(R.id.tv_history_style);
            ivProfile = v.findViewById(R.id.iv_history_hair);
            ivHaircutSmall = v.findViewById(R.id.iv_history_hair_small);
            ivCall = v.findViewById(R.id.iv_call_direct);
            ivArrow = v.findViewById(R.id.iv_expand_arrow);
            btnCancel = v.findViewById(R.id.btn_cancel_booking_book);
            btnEdit = v.findViewById(R.id.btn_edit_booking_book);
            llExpandable = v.findViewById(R.id.ll_expandable_content);
            llCollapsedRating = v.findViewById(R.id.ll_collapsed_rating);
            llButtons = v.findViewById(R.id.ll_buttons_container);
            rbRating = v.findViewById(R.id.rb_item_rating);
            tvCollapsedRating = v.findViewById(R.id.tv_collapsed_rating_val);
            tvComment = v.findViewById(R.id.tv_history_comment);
            tvAmount = v.findViewById(R.id.tv_history_amount);
            tvSessionTimer = v.findViewById(R.id.tv_history_session_timer);
        }
    }
}
