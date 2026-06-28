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
 * ReadFragment displays the history of notifications in real-time.
 */
public class ReadFragment extends Fragment {

    private LinearLayout llReadNotifications;
    private TextView tvEmptyRead;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration readListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_activity_read, container, false);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        llReadNotifications = view.findViewById(R.id.ll_read_notifications_list);
        tvEmptyRead = view.findViewById(R.id.tv_empty_read_msg);

        startReadListener();

        return view;
    }

    private void startReadListener() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        // Removed .orderBy() to match ActivityListFragment's instant responsiveness
        readListener = db.collection("notifications")
                .whereEqualTo("receiverId", uid)
                .whereEqualTo("isRead", true)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;

                    llReadNotifications.removeAllViews();
                    if (value.isEmpty()) {
                        tvEmptyRead.setVisibility(View.VISIBLE);
                    } else {
                        tvEmptyRead.setVisibility(View.GONE);
                        
                        List<QueryDocumentSnapshot> docs = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : value) docs.add(doc);
                        
                        // Sort by timestamp (descending)
                        Collections.sort(docs, (d1, d2) -> {
                            Timestamp t1 = d1.getTimestamp("timestamp");
                            Timestamp t2 = d2.getTimestamp("timestamp");
                            if (t1 == null || t2 == null) return 0;
                            return t2.compareTo(t1);
                        });

                        int index = 0;
                        for (QueryDocumentSnapshot doc : docs) {
                            if (!handleAutoDelete(doc)) {
                                addNotificationCard(doc, index++);
                            }
                        }
                    }
                });
    }

    private boolean handleAutoDelete(QueryDocumentSnapshot doc) {
        Timestamp lastRead = doc.getTimestamp("lastReadTimestamp");
        if (lastRead == null) return false;

        long diffInMillis = new Date().getTime() - lastRead.toDate().getTime();
        long diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis);

        if (diffInDays >= 30) {
            doc.getReference().delete();
            return true;
        }
        return false;
    }

    private void addNotificationCard(QueryDocumentSnapshot doc, int index) {
        String type = doc.getString("type");
        String title = doc.getString("title");
        String message = doc.getString("message");
        Timestamp ts = doc.getTimestamp("timestamp");
        Timestamp lastRead = doc.getTimestamp("lastReadTimestamp");

        View cardView = LayoutInflater.from(getContext()).inflate(R.layout.item_notification_dynamic, llReadNotifications, false);
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
        }

        // Countdown: 30 - elapsed
        if (lastRead != null) {
            long diffInMillis = new Date().getTime() - lastRead.toDate().getTime();
            long diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis);
            long daysLeft = 30 - diffInDays;
            if (daysLeft < 0) daysLeft = 0;
            tvDuration.setText(daysLeft + "d left");
        } else {
            tvDuration.setText("30d left");
        }

        if ("NOSHOW".equals(type) || "CANCELLATION".equals(type)) {
            ivBg.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.warning_red));
            ivIcon.setImageResource(R.drawable.ic_warning_circle);
            ivIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.warning_red_icon));
        } else {
            ivBg.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.info_blue));
            ivIcon.setImageResource(R.drawable.ic_info_circle);
            ivIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.info_blue_icon));
        }

        if (clickableContainer != null) {
            clickableContainer.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), NotificationDetailActivity.class);
                intent.putExtra("title", tvTitle.getText().toString());
                intent.putExtra("message", message);
                intent.putExtra("type", type);
                intent.putExtra("bookingId", doc.getString("bookingId"));
                intent.putExtra("NOTIFICATION_DOC_ID", doc.getId());
                intent.putExtra("senderId", doc.getString("senderId"));
                if (ts != null) intent.putExtra("timestamp", ts.toDate().getTime());
                startActivity(intent);
            });
        }

        cardView.setAlpha(0f);
        cardView.setTranslationY(20f);
        cardView.animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(index * 40L).start();

        llReadNotifications.addView(cardView);
    }

    @Override
    public void onDestroyView() {
        if (readListener != null) readListener.remove();
        super.onDestroyView();
    }
}
