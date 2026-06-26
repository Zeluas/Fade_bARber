package com.zejyv.azizul.uitm.fadebarber.adapters;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import com.zejyv.azizul.uitm.fadebarber.CutHistoryActivity;
import com.zejyv.azizul.uitm.fadebarber.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public class CutHistoryAdapter extends RecyclerView.Adapter<CutHistoryAdapter.HistoryViewHolder> {

    private List<CutHistoryActivity.HistoryItem> items;
    private int expandedPosition = -1;
    private boolean showStatusBadge = true;

    public CutHistoryAdapter(List<CutHistoryActivity.HistoryItem> items) {
        this.items = items;
    }

    public CutHistoryAdapter(List<CutHistoryActivity.HistoryItem> items, boolean showStatusBadge) {
        this.items = items;
        this.showStatusBadge = showStatusBadge;
    }

    public void updateData(List<CutHistoryActivity.HistoryItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cut_history, parent, false);
        return new HistoryViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        CutHistoryActivity.HistoryItem item = items.get(position);
        boolean isExpanded = position == expandedPosition;

        holder.tvId.setText("#" + item.booking.getBookingId());
        holder.tvBarber.setText(item.barberName);
        holder.tvDate.setText(item.booking.getDate());
        holder.tvTime.setText(item.booking.getTime());
        holder.tvStyle.setText(item.booking.getHairstyleName());

        if ("Completed".equalsIgnoreCase(item.booking.getStatus())) {
            holder.tvAmount.setText(String.format(Locale.getDefault(), "RM %.2f", item.amount));
            holder.tvSessionTimer.setText(formatDuration(item.durationMillis));
            holder.rbRating.setRating(item.rating);
            holder.tvCollapsedRating.setText(String.format(Locale.getDefault(), "%.1f", item.rating));
            holder.tvComment.setText(item.comment != null && !item.comment.isEmpty() ? item.comment : "No comments provided.");
        } else {
            holder.tvAmount.setText("RM --.--");
            holder.tvSessionTimer.setText("--:--");
            holder.rbRating.setRating(0);
            holder.tvCollapsedRating.setText("---");
            holder.tvComment.setText("N/A");
        }

        // Set Status Badge
        if (showStatusBadge) {
            holder.tvStatus.setVisibility(View.VISIBLE);
            setStatusBadge(holder.tvStatus, item.booking.getStatus());
        } else {
            holder.tvStatus.setVisibility(View.GONE);
        }

        // Handle Call Button
        if (holder.ivCall != null) {
            holder.ivCall.setOnClickListener(v -> {
                android.content.Context ctx = v.getContext();
                if (ctx instanceof CutHistoryActivity) {
                    ((CutHistoryActivity) ctx).showCallBarberDialog(item.barberPhone);
                }
            });
        }

        // Load Barber Profile Pic (Large)
        Glide.with(holder.itemView.getContext())
                .load(item.barberProfileUrl)
                .placeholder(R.drawable.ic_profile)
                .into(holder.ivHair);

        // Load Hairstyle Image (Small)
        loadHairstyleImage(item.booking.getHairstyleName(), holder.ivHairSmall);

        // Expand/Collapse Logic
        ViewGroup.LayoutParams lp = holder.llExpandable.getLayoutParams();
        if (isExpanded) {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            holder.llExpandable.setVisibility(View.VISIBLE);
            holder.llCollapsedRating.setVisibility(View.GONE);
            holder.ivArrow.setRotation(0);
        } else {
            lp.height = 0;
            holder.llExpandable.setVisibility(View.GONE);
            holder.llCollapsedRating.setVisibility(View.VISIBLE);
            holder.llCollapsedRating.setAlpha(1.0f);
            holder.ivArrow.setRotation(180);
        }
        holder.llExpandable.setLayoutParams(lp);

        View.OnClickListener toggle = v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos == RecyclerView.NO_POSITION) return;

            int prevExp = expandedPosition;
            boolean expanding = (expandedPosition != currentPos);
            expandedPosition = expanding ? currentPos : -1;

            if (expanding) {
                holder.llExpandable.setVisibility(View.VISIBLE);
                holder.llExpandable.measure(View.MeasureSpec.makeMeasureSpec(holder.itemView.getWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int targetH = holder.llExpandable.getMeasuredHeight();
                ValueAnimator anim = ValueAnimator.ofInt(0, targetH);
                anim.setDuration(400);
                anim.setInterpolator(new DecelerateInterpolator());
                anim.addUpdateListener(animation -> {
                    int val = (int) animation.getAnimatedValue();
                    ViewGroup.LayoutParams p = holder.llExpandable.getLayoutParams();
                    p.height = val;
                    holder.llExpandable.setLayoutParams(p);
                    float alpha = 1.0f - ((float) val / targetH);
                    holder.llCollapsedRating.setAlpha(alpha);
                    if (alpha < 0.05f) holder.llCollapsedRating.setVisibility(View.GONE);
                });
                anim.start();
                holder.ivArrow.animate().rotation(0).setDuration(400).start();
            } else {
                int startH = holder.llExpandable.getHeight();
                ValueAnimator anim = ValueAnimator.ofInt(startH, 0);
                anim.setDuration(400);
                anim.setInterpolator(new DecelerateInterpolator());
                anim.addUpdateListener(animation -> {
                    int val = (int) animation.getAnimatedValue();
                    ViewGroup.LayoutParams p = holder.llExpandable.getLayoutParams();
                    p.height = val;
                    holder.llExpandable.setLayoutParams(p);
                    float alpha = 1.0f - ((float) val / startH);
                    holder.llCollapsedRating.setVisibility(View.VISIBLE);
                    holder.llCollapsedRating.setAlpha(alpha);
                    if (val == 0) holder.llExpandable.setVisibility(View.GONE);
                });
                anim.start();
                holder.ivArrow.animate().rotation(180).setDuration(400).start();
            }
            if (prevExp != -1 && prevExp != currentPos) notifyItemChanged(prevExp);
        };

        holder.itemView.setOnClickListener(toggle);
        holder.ivArrow.setOnClickListener(toggle);
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

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long m = seconds / 60;
        long h = m / 60;
        m = m % 60;
        seconds = seconds % 60;
        if (h > 0) return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, seconds);
        else return String.format(Locale.getDefault(), "%02d:%02d", m, seconds);
    }

    private void loadHairstyleImage(String name, ImageView iv) {
        if (name == null) { iv.setImageResource(R.drawable.ic_hair); return; }
        Context context = iv.getContext();
        try {
            String[] images = context.getAssets().list("images");
            if (images != null) {
                String cleanName = name.toLowerCase().replace(" ", "").replace("-", "");
                for (String imageName : images) {
                    String cleanImg = imageName.toLowerCase().split("\\.")[0].replace(" ", "").replace("-", "");
                    if (cleanName.contains(cleanImg) || cleanImg.contains(cleanName)) {
                        try (InputStream is = context.getAssets().open("images/" + imageName)) {
                            Bitmap bitmap = BitmapFactory.decodeStream(is);
                            iv.setImageBitmap(bitmap);
                            return;
                        }
                    }
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
        iv.setImageResource(R.drawable.ic_hair);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvId, tvBarber, tvDate, tvTime, tvStyle, tvAmount, tvSessionTimer, tvCollapsedRating, tvComment, tvStatus;
        ImageView ivHair, ivHairSmall, ivArrow, ivCall;
        LinearLayout llExpandable, llCollapsedRating;
        RatingBar rbRating;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvId = itemView.findViewById(R.id.tv_booking_id);
            tvStatus = itemView.findViewById(R.id.tv_booking_status);
            tvBarber = itemView.findViewById(R.id.tv_history_barber);
            tvDate = itemView.findViewById(R.id.tv_history_date);
            tvTime = itemView.findViewById(R.id.tv_history_time);
            tvStyle = itemView.findViewById(R.id.tv_history_style);
            tvAmount = itemView.findViewById(R.id.tv_history_amount);
            tvSessionTimer = itemView.findViewById(R.id.tv_history_session_timer);
            tvCollapsedRating = itemView.findViewById(R.id.tv_collapsed_rating_val);
            tvComment = itemView.findViewById(R.id.tv_history_comment);
            ivHair = itemView.findViewById(R.id.iv_history_hair);
            ivHairSmall = itemView.findViewById(R.id.iv_history_hair_small);
            ivArrow = itemView.findViewById(R.id.iv_expand_arrow);
            ivCall = itemView.findViewById(R.id.iv_call_direct);
            llExpandable = itemView.findViewById(R.id.ll_expandable_content);
            llCollapsedRating = itemView.findViewById(R.id.ll_collapsed_rating);
            rbRating = itemView.findViewById(R.id.rb_item_rating);
        }
    }
}
