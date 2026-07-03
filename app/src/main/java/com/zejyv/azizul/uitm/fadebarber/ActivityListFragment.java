package com.zejyv.azizul.uitm.fadebarber;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * ActivityListFragment displays the Inbox notifications in real-time.
 */
public class ActivityListFragment extends Fragment {

    private LinearLayout llDynamicNotifications;
    private TextView tvNoNotifications;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration notificationListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_activity_list, container, false);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        llDynamicNotifications = view.findViewById(R.id.ll_dynamic_notifications);
        tvNoNotifications = view.findViewById(R.id.tv_no_notifications);

        startNotificationListener();

        return view;
    }

    private void startNotificationListener() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        // Removed .orderBy() to avoid Index requirements, sorting manually for instant updates
        notificationListener = db.collection("notifications")
                .whereEqualTo("receiverId", uid)
                .whereEqualTo("isRead", false)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    
                    llDynamicNotifications.removeAllViews();
                    
                    if (value.isEmpty()) {
                        tvNoNotifications.setVisibility(View.VISIBLE);
                    } else {
                        tvNoNotifications.setVisibility(View.GONE);
                        
                        List<QueryDocumentSnapshot> docs = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : value) docs.add(doc);
                        
                        // Sort manually by timestamp (descending)
                        Collections.sort(docs, (d1, d2) -> {
                            Timestamp t1 = d1.getTimestamp("timestamp");
                            Timestamp t2 = d2.getTimestamp("timestamp");
                            if (t1 == null || t2 == null) return 0;
                            return t2.compareTo(t1);
                        });

                        int index = 0;
                        for (QueryDocumentSnapshot doc : docs) {
                            addNotificationCard(doc, index++);
                        }
                    }
                });
    }

    private void addNotificationCard(QueryDocumentSnapshot doc, int index) {
        String type = doc.getString("type");
        String title = doc.getString("title");
        String message = doc.getString("message");
        Timestamp ts = doc.getTimestamp("timestamp");

        View cardView = LayoutInflater.from(getContext()).inflate(R.layout.item_notification_dynamic, llDynamicNotifications, false);
        View clickableContainer = cardView.findViewById(R.id.ll_dynamic_notif_container);

        TextView tvTitle = cardView.findViewById(R.id.tv_dynamic_notif_title);
        TextView tvDesc = cardView.findViewById(R.id.tv_dynamic_notif_desc);
        TextView tvDate = cardView.findViewById(R.id.tv_dynamic_notif_date);
        ImageView ivIcon = cardView.findViewById(R.id.iv_dynamic_notif_icon);
        ImageView ivBg = cardView.findViewById(R.id.iv_dynamic_notif_bg);
        TextView tvDuration = cardView.findViewById(R.id.tv_dynamic_notif_duration);

        if ("NOSHOW".equals(type)) {
            tvTitle.setText("No-Show");
        } else {
            tvTitle.setText(title);
        }

        tvDesc.setText(message);

        if (ts != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yy", Locale.getDefault());
            tvDate.setText(sdf.format(ts.toDate()));
            tvDuration.setText(getTimeAgo(ts.toDate()));
        }

        if ("NOSHOW".equals(type) || "CANCELLATION".equals(type) || "PROFILE_ERROR".equals(type) || "AUTO_CANCELLATION".equals(type) || "CANCELLATION_LOCK".equals(type)) {
            ivBg.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.warning_red));
            ivIcon.setImageResource(R.drawable.ic_warning_circle);
            ivIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.warning_red_icon));
            int p = (int) (10 * getResources().getDisplayMetrics().density);
            ivIcon.setPadding(p, p, p, p);
        } else if ("PROFILE_UPDATE".equals(type)) {
            ivBg.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.alert_yellow));
            ivIcon.setImageResource(R.drawable.ic_notification);
            ivIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.alert_yellow_icon));
            int p = (int) (13 * getResources().getDisplayMetrics().density);
            ivIcon.setPadding(p, p, p, p);
        } else if ("PAYMENT_REQUIRED".equals(type)) {
            ivBg.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.success_green));
            ivIcon.setImageResource(R.drawable.ic_cash);
            ivIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.success_green_icon));
            int p = (int) (14 * getResources().getDisplayMetrics().density);
            ivIcon.setPadding(p, p, p, p);
        } else if ("COMPLETED".equals(type)) {
            ivBg.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.success_green));
            ivIcon.setImageResource(R.drawable.ic_check_circle);
            ivIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.success_green_icon));
            int p = (int) (8 * getResources().getDisplayMetrics().density);
            ivIcon.setPadding(p, p, p, p);
        } else {
            ivBg.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.info_blue));
            ivIcon.setImageResource(R.drawable.ic_info_circle);
            ivIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.info_blue_icon));
            int p = (int) (10 * getResources().getDisplayMetrics().density);
            ivIcon.setPadding(p, p, p, p);
        }

        if (clickableContainer != null) {
            clickableContainer.setOnClickListener(v -> {
                // Navigate immediately
                Intent intent = new Intent(getContext(), NotificationDetailActivity.class);
                intent.putExtra("title", tvTitle.getText().toString());
                intent.putExtra("message", message);
                intent.putExtra("type", type);
                intent.putExtra("bookingId", doc.getString("bookingId"));
                intent.putExtra("NOTIFICATION_DOC_ID", doc.getId());
                intent.putExtra("senderId", doc.getString("senderId"));
                if (ts != null) intent.putExtra("timestamp", ts.toDate().getTime());
                startActivity(intent);

                // Delay marking as read by 5 seconds
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (isAdded() && doc.getReference() != null) {
                        doc.getReference().update(
                                "isRead", true,
                                "lastReadTimestamp", Timestamp.now()
                        );
                    }
                }, 5000);
            });
        }

        // Staggered entry animation for smooth feel
        cardView.setAlpha(0f);
        cardView.setTranslationY(20f);
        cardView.animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(index * 40L).start();

        llDynamicNotifications.addView(cardView);
    }

    private String getTimeAgo(Date date) {
        long duration = new Date().getTime() - date.getTime();
        long mins = TimeUnit.MILLISECONDS.toMinutes(duration);
        long hours = TimeUnit.MILLISECONDS.toHours(duration);
        long days = TimeUnit.MILLISECONDS.toDays(duration);

        if (mins < 1) return "Just now";
        if (mins < 60) return mins + "m ago";
        if (hours < 24) return hours + "h ago";
        return days + "d ago";
    }

    @Override
    public void onDestroyView() {
        if (notificationListener != null) notificationListener.remove();
        super.onDestroyView();
    }
}
