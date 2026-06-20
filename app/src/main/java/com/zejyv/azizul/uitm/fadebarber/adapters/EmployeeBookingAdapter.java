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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.zejyv.azizul.uitm.fadebarber.MainActivityEmployee;
import com.zejyv.azizul.uitm.fadebarber.R;
import com.zejyv.azizul.uitm.fadebarber.models.Booking;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class EmployeeBookingAdapter extends RecyclerView.Adapter<EmployeeBookingAdapter.BookingViewHolder> {

    private List<Booking> bookings;
    private final Context context;
    private final FirebaseFirestore db;
    private final OnBookingActionListener actionListener;
    private int expandedPosition = -1;

    public interface OnBookingActionListener {
        void onCancelBooking(Booking booking);
        void onEditBooking(Booking booking);
    }

    public EmployeeBookingAdapter(Context context, List<Booking> bookings, OnBookingActionListener actionListener) {
        this.context = context;
        this.bookings = bookings;
        this.db = FirebaseFirestore.getInstance();
        this.actionListener = actionListener;
    }

    public void updateData(List<Booking> newBookings) {
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
        Booking booking = bookings.get(position);
        boolean isExpanded = position == expandedPosition;
        
        holder.tvId.setText("#" + booking.getBookingId());
        holder.tvStatus.setText(booking.getStatus());
        holder.tvDate.setText(booking.getDate());
        holder.tvTime.setText(booking.getTime());
        holder.tvHaircutName.setText(booking.getHairstyleName());

        updateStatusStyle(holder.tvStatus, booking.getStatus());
        loadHaircutImage(booking.getHairstyleName(), holder.ivHaircut);

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
                // Animate expansion
                holder.llExpandable.setVisibility(View.VISIBLE);
                holder.llExpandable.measure(
                    View.MeasureSpec.makeMeasureSpec(holder.itemView.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );
                int targetHeight = holder.llExpandable.getMeasuredHeight();

                ValueAnimator animator = ValueAnimator.ofInt(0, targetHeight);
                animator.setDuration(450);
                animator.setInterpolator(new DecelerateInterpolator());
                animator.addUpdateListener(animation -> {
                    int val = (int) animation.getAnimatedValue();
                    ViewGroup.LayoutParams lp = holder.llExpandable.getLayoutParams();
                    lp.height = val;
                    holder.llExpandable.setLayoutParams(lp);
                    
                    // Smooth fade out for collapsed rating
                    float alpha = 1.0f - ((float) val / targetHeight);
                    holder.llCollapsedRating.setAlpha(alpha);
                    if (alpha < 0.05f) holder.llCollapsedRating.setVisibility(View.GONE);
                });
                animator.start();

                holder.ivArrow.animate().rotation(0).setDuration(450).start();
            } else {
                // Animate collapse
                int initialHeight = holder.llExpandable.getHeight();
                ValueAnimator animator = ValueAnimator.ofInt(initialHeight, 0);
                animator.setDuration(450);
                animator.setInterpolator(new DecelerateInterpolator());
                animator.addUpdateListener(animation -> {
                    int val = (int) animation.getAnimatedValue();
                    ViewGroup.LayoutParams lp = holder.llExpandable.getLayoutParams();
                    lp.height = val;
                    holder.llExpandable.setLayoutParams(lp);
                    
                    // Smooth fade in for collapsed rating
                    float alpha = 1.0f - ((float) val / initialHeight);
                    holder.llCollapsedRating.setVisibility(View.VISIBLE);
                    holder.llCollapsedRating.setAlpha(alpha);
                    
                    if (val == 0) {
                        holder.llExpandable.setVisibility(View.GONE);
                    }
                });
                animator.start();

                holder.ivArrow.animate().rotation(180).setDuration(450).start();
            }

            if (prevExpanded != -1 && prevExpanded != currentPos) {
                notifyItemChanged(prevExpanded);
            }
        };

        holder.ivArrow.setOnClickListener(toggleExpand);
        holder.itemView.setOnClickListener(toggleExpand);

        // Fetch Rating & Comment if Completed
        if ("Completed".equalsIgnoreCase(booking.getStatus())) {
            db.collection("hairstylist_ratings").document(booking.getBookingId()).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            float rating = doc.getDouble("rating") != null ? doc.getDouble("rating").floatValue() : 0f;
                            String comment = doc.getString("comment");
                            holder.rbRating.setRating(rating);
                            holder.tvCollapsedRating.setText(String.valueOf(rating));
                            holder.tvComment.setText(comment != null && !comment.isEmpty() ? comment : "No comments provided");
                        } else {
                            holder.rbRating.setRating(0);
                            holder.tvCollapsedRating.setText("0.0");
                            holder.tvComment.setText("Waiting for rating...");
                        }
                    });
        } else {
            holder.rbRating.setRating(0);
            holder.tvCollapsedRating.setText("---");
            holder.tvComment.setText("N/A");
        }

        // Fetch Customer Data
        db.collection("customers").document(booking.getCustomerId()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        String phone = doc.getString("phone");
                        holder.tvCustomerName.setText(name);
                        
                        holder.ivCall.setOnClickListener(v -> {
                            if (context instanceof MainActivityEmployee) {
                                ((MainActivityEmployee) context).showCallCustomerDialog(phone);
                            }
                        });
                    }
                });

        // Fetch Customer Profile Pic
        db.collection("profile_pics").document(booking.getCustomerId()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String url = doc.getString("url");
                        Glide.with(context)
                                .load(url)
                                .placeholder(R.drawable.ic_profile)
                                .into(holder.ivProfile);
                        
                        holder.ivProfile.setOnClickListener(v -> {
                             if (context instanceof MainActivityEmployee) {
                                ((MainActivityEmployee) context).showImagePreview(url);
                             }
                        });
                    } else {
                        holder.ivProfile.setImageResource(R.drawable.ic_profile);
                    }
                });

        holder.btnCancel.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onCancelBooking(booking);
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onEditBooking(booking);
        });

        // Hide buttons if status is not Pending
        if ("Pending".equalsIgnoreCase(booking.getStatus())) {
            holder.btnCancel.setVisibility(View.VISIBLE);
            holder.btnEdit.setVisibility(View.VISIBLE);
        } else {
            holder.btnCancel.setVisibility(View.GONE);
            holder.btnEdit.setVisibility(View.GONE);
        }
    }

    private void updateStatusStyle(TextView tvStatus, String status) {
        if ("Completed".equalsIgnoreCase(status)) {
            tvStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(context.getColor(R.color.primary_color)));
        } else if ("Pending".equalsIgnoreCase(status)) {
            tvStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF9800"))); // Orange
        } else if ("Cancelled".equalsIgnoreCase(status)) {
            tvStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.RED));
        } else {
            tvStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(context.getColor(R.color.info_blue_icon)));
        }
    }

    private void loadHaircutImage(String name, ImageView imageView) {
        if (name == null) return;
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
        } catch (IOException e) {
            e.printStackTrace();
        }
        imageView.setImageResource(R.drawable.ic_hair);
    }

    @Override
    public int getItemCount() {
        return bookings.size();
    }

    static class BookingViewHolder extends RecyclerView.ViewHolder {
        TextView tvStatus, tvId, tvCustomerName, tvDate, tvTime, tvHaircutName, tvCollapsedRating, tvComment;
        ImageView ivProfile, ivHaircut, ivCall, ivArrow;
        MaterialButton btnCancel, btnEdit;
        LinearLayout llExpandable, llCollapsedRating;
        RatingBar rbRating;

        BookingViewHolder(View v) {
            super(v);
            tvStatus = v.findViewById(R.id.tv_booking_status);
            tvId = v.findViewById(R.id.tv_booking_id);
            tvCustomerName = v.findViewById(R.id.tv_customer_name_book);
            tvDate = v.findViewById(R.id.tv_booking_date_book);
            tvTime = v.findViewById(R.id.tv_booking_time_book);
            tvHaircutName = v.findViewById(R.id.tv_hairstyle_name_book);
            ivProfile = v.findViewById(R.id.iv_customer_profile);
            ivHaircut = v.findViewById(R.id.iv_chosen_hairstyle);
            ivCall = v.findViewById(R.id.iv_call_direct);
            ivArrow = v.findViewById(R.id.iv_expand_arrow);
            btnCancel = v.findViewById(R.id.btn_cancel_booking_book);
            btnEdit = v.findViewById(R.id.btn_edit_booking_book);
            
            llExpandable = v.findViewById(R.id.ll_expandable_content);
            llCollapsedRating = v.findViewById(R.id.ll_collapsed_rating);
            rbRating = v.findViewById(R.id.rb_item_rating);
            tvCollapsedRating = v.findViewById(R.id.tv_collapsed_rating_val);
            tvComment = v.findViewById(R.id.tv_item_comment);
        }
    }
}
