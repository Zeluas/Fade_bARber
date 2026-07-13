package com.zejyv.azizul.uitm.fadebarber;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import androidx.transition.TransitionManager;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AdminHomeFragment extends Fragment {

    private Handler handler;
    private MaterialCardView mcvTopBar;
    private ScrollView svHomeContent;
    private TextView tvAiJab, tvWelcome;
    private ImageView ivProfile;

    // Time & Status UI
    private TextView tvAdminTime, tvAdminDate, tvAdminShopStatus, tvAdminLoadIndicator;
    private TextView tvAdminAmpm, tvAdminTimeName, tvAdminRemainingTime;
    private ImageView ivAdminTimeBg;

    // Business Pulse UI
    private TextView tvRevToday, tvRevMonth, tvTopStyleToday, tvTopStyleMonth;
    private TextView tvCountActive, tvCountPending, tvCountCancelled, tvCountCompleted;
    private TextView tvLabelTopStyleMonth;
    private ImageView ivTopStyleTodayPreview, ivTopStyleMonthPreview;

    // Team Performance UI
    private RecyclerView rvTeamPerformance;
    private BarberAdapter barberAdapter;
    private List<BarberPerformance> barberList = new ArrayList<>();
    private List<String> activeEmployeeIds = new ArrayList<>();
    private TextView tvNoTeamStats;

    // Customer Sentiment UI
    private TextView tvOverallRating, tvSentimentMsg;
    private TextView tvAdminTotalReviewsCount, tvAdminNoReviewsPlaceholder;
    private LinearLayout llAdminRecentReviewsList;
    private boolean isFirstTeamLoad = true;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private List<ListenerRegistration> listeners = new ArrayList<>();

    // AI Jab related state
    private static List<String> jabPool = new ArrayList<>();
    private static boolean isPoolInitialized = false;
    private long lastJabTime = 0;
    private static final long JAB_COOLDOWN = 10000;
    private Runnable typingRunnable;

    private final Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimeStatus();
            if (handler != null) handler.postDelayed(this, 60000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        handler = new Handler(Looper.getMainLooper());
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        loadJabPool();
        initializeViews(view);
        setupClickLogic();
        populateWelcomeMessage();
        
        syncAndFetch();
    }

    private void initializeViews(View view) {
        mcvTopBar = view.findViewById(R.id.mcv_admin_top_bar);
        svHomeContent = view.findViewById(R.id.sv_admin_home_content);
        tvAiJab = view.findViewById(R.id.tv_admin_ai_jab);
        tvWelcome = view.findViewById(R.id.tv_welcome_admin);
        ivProfile = view.findViewById(R.id.iv_admin_profile);

        tvAdminTime = view.findViewById(R.id.tv_admin_time);
        tvAdminDate = view.findViewById(R.id.tv_admin_date);
        tvAdminAmpm = view.findViewById(R.id.tv_admin_ampm);
        tvAdminTimeName = view.findViewById(R.id.tv_admin_time_name);
        tvAdminRemainingTime = view.findViewById(R.id.tv_admin_remaining_time);
        tvAdminShopStatus = view.findViewById(R.id.tv_admin_shop_status);
        tvAdminLoadIndicator = view.findViewById(R.id.tv_admin_load_indicator);
        ivAdminTimeBg = view.findViewById(R.id.iv_admin_time_bg);

        tvRevToday = view.findViewById(R.id.tv_admin_rev_today);
        tvRevMonth = view.findViewById(R.id.tv_admin_rev_month);
        tvTopStyleToday = view.findViewById(R.id.tv_admin_top_style_today);
        tvTopStyleMonth = view.findViewById(R.id.tv_admin_top_style_month);
        tvLabelTopStyleMonth = view.findViewById(R.id.tv_admin_label_top_style_month);
        ivTopStyleTodayPreview = view.findViewById(R.id.iv_admin_top_style_today_preview);
        ivTopStyleMonthPreview = view.findViewById(R.id.iv_admin_top_style_month_preview);

        tvCountActive = view.findViewById(R.id.tv_admin_count_active);
        tvCountPending = view.findViewById(R.id.tv_admin_count_pending);
        tvCountCancelled = view.findViewById(R.id.tv_admin_count_cancelled);
        tvCountCompleted = view.findViewById(R.id.tv_admin_count_completed);

        tvNoTeamStats = view.findViewById(R.id.tv_admin_no_team_stats);

        rvTeamPerformance = view.findViewById(R.id.rv_admin_team_performance);
        rvTeamPerformance.setLayoutManager(new LinearLayoutManager(getContext()));
        
        // Disable the "blink" (cross-fade) animation for item changes
        if (rvTeamPerformance.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) rvTeamPerformance.getItemAnimator()).setSupportsChangeAnimations(false);
        }

        barberAdapter = new BarberAdapter();
        rvTeamPerformance.setAdapter(barberAdapter);

        tvOverallRating = view.findViewById(R.id.tv_admin_overall_rating);
        tvSentimentMsg = view.findViewById(R.id.tv_admin_sentiment_msg);
        tvAdminTotalReviewsCount = view.findViewById(R.id.tv_admin_total_reviews_count);
        tvAdminNoReviewsPlaceholder = view.findViewById(R.id.tv_admin_no_reviews_placeholder);
        llAdminRecentReviewsList = view.findViewById(R.id.ll_admin_recent_reviews_list);

        // Management Buttons
        view.findViewById(R.id.btn_admin_manage_staff).setOnClickListener(v -> {
            startActivity(new android.content.Intent(getContext(), EmployeeManagementActivity.class));
        });
        view.findViewById(R.id.btn_admin_manage_services).setOnClickListener(v -> Toast.makeText(getContext(), "Service Management Activity planned", Toast.LENGTH_SHORT).show());
        view.findViewById(R.id.btn_admin_reports).setOnClickListener(v -> {
            startActivity(new android.content.Intent(getContext(), BusinessRevenueActivity.class));
        });
        view.findViewById(R.id.btn_admin_announcements).setOnClickListener(v -> Toast.makeText(getContext(), "Ads & Announcements Activity planned", Toast.LENGTH_SHORT).show());
    }

    private void setupClickLogic() {
        if (ivProfile != null) {
            ivProfile.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivityAdmin) {
                    ((MainActivityAdmin) getActivity()).navigateToProfile();
                }
            });
        }

        if (mcvTopBar != null) {
            mcvTopBar.setOnClickListener(v -> {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastJabTime < JAB_COOLDOWN) {
                    long timeLeft = (JAB_COOLDOWN - (currentTime - lastJabTime)) / 1000;
                    Toast.makeText(getContext(), getString(R.string.jab_cooldown, (int) timeLeft), Toast.LENGTH_SHORT).show();
                } else {
                    lastJabTime = currentTime;
                    showNextJabFromPool();
                }
            });
        }
    }

    private void syncAndFetch() {
        NetworkTimeManager.getInstance().syncTime(new NetworkTimeManager.OnTimeSyncedListener() {
            @Override
            public void onSyncSuccess(long networkTime) {
                if (isAdded()) {
                    handler.post(() -> {
                        handler.removeCallbacks(updateTimeRunnable);
                        handler.post(updateTimeRunnable);
                        startDataListeners();
                        updateAiJabStatus();
                    });
                }
            }

            @Override
            public void onSyncFailed() {
                if (isAdded()) {
                    handler.post(() -> {
                        handler.removeCallbacks(updateTimeRunnable);
                        handler.post(updateTimeRunnable);
                        startDataListeners();
                        updateAiJabStatus();
                    });
                }
            }
        });
    }

    private void updateAiJabStatus() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        cal.setTimeInMillis(NetworkTimeManager.getInstance().getCurrentTime());
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        boolean isOpen = (hour >= 10 && hour < 13) || (hour >= 14 && hour < 24);
        
        if (!isPoolInitialized || jabPool.isEmpty()) {
            isPoolInitialized = true;
            updateAiJab(isOpen, true);
        } else {
            showNextJabFromPool();
        }
    }

    private void startDataListeners() {
        for (ListenerRegistration lr : listeners) lr.remove();
        listeners.clear();

        long now = NetworkTimeManager.getInstance().getCurrentTime();
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        cal.setTimeInMillis(now);
        String todayDateStr = String.format(Locale.getDefault(), "%02d/%02d/%02d",
                cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR) % 100);
        String monthSuffix = String.format(Locale.getDefault(), "/%02d/%02d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR) % 100);

        String monthName = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        if (tvLabelTopStyleMonth != null) {
            tvLabelTopStyleMonth.setText("Top Hairstyle (" + monthName + ")");
        }

        // 0. Listen for Active Employees
        listeners.add(db.collection("users")
                .whereEqualTo("role", "employee")
                .addSnapshotListener((userSnapshot, userError) -> {
                    if (userError != null || userSnapshot == null) return;
                    activeEmployeeIds.clear();
                    for (QueryDocumentSnapshot doc : userSnapshot) {
                        activeEmployeeIds.add(doc.getId());
                    }
                    // Trigger refresh if we have bookings snapshot already
                    // This is handled by fetchTeamPerformance being called from bookings listener
                }));

        // 1. Listen for Today's & Monthly Revenue
        listeners.add(db.collection("session_payments")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    
                    double revToday = 0;
                    double revMonth = 0;
                    
                    for (QueryDocumentSnapshot doc : value) {
                        // Assuming payments have a 'bookingId' and we fetch the booking to check date
                        // But for performance, let's assume session_payments might have a timestamp or we join
                        // Since we don't have date in payments, we fetch bookings first
                    }
                    fetchDetailedRevenue(todayDateStr, monthSuffix);
                }));

        // 2. Listen for Bookings (Pulse & Styles)
        listeners.add(db.collection("bookings")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    
                    int active = 0, pending = 0, cancelled = 0, completed = 0;
                    Map<String, Integer> stylesToday = new HashMap<>();
                    Map<String, Integer> stylesMonth = new HashMap<>();
                    Map<String, String> styleNameToId = new HashMap<>();

                    for (QueryDocumentSnapshot doc : value) {
                        String date = doc.getString("date");
                        String status = doc.getString("status");
                        String style = doc.getString("hairstyleName");
                        String styleId = doc.getString("hairstyleId");

                        if (style != null && !style.isEmpty()) {
                            styleNameToId.put(style, styleId);
                        }

                        if (todayDateStr.equals(date)) {
                            if ("Completed".equalsIgnoreCase(status)) completed++;
                            else if ("Pending".equalsIgnoreCase(status)) pending++;
                            else if ("Cancelled".equalsIgnoreCase(status)) cancelled++;
                            else active++; // Covers Starting, Paying, Rating, etc.
                            
                            // Top Style Today: every status except Cancelled
                            if (!"Cancelled".equalsIgnoreCase(status)) {
                                if (style != null && !style.isEmpty()) stylesToday.put(style, stylesToday.getOrDefault(style, 0) + 1);
                            }
                        }
                        
                        if (date != null && date.contains(monthSuffix)) {
                            // Top Style Month: only Completed
                            if ("Completed".equalsIgnoreCase(status)) {
                                if (style != null && !style.isEmpty()) stylesMonth.put(style, stylesMonth.getOrDefault(style, 0) + 1);
                            }
                        }
                    }

                    final int finalActive = active;
                    final int finalPending = pending;
                    final int finalCancelled = cancelled;
                    final int finalCompleted = completed;

                    if (isAdded()) {
                        handler.post(() -> {
                            tvCountActive.setText(String.valueOf(finalActive));
                            tvCountPending.setText(String.valueOf(finalPending));
                            tvCountCancelled.setText(String.valueOf(finalCancelled));
                            tvCountCompleted.setText(String.valueOf(finalCompleted));

                            updateTopStyle(tvTopStyleToday, ivTopStyleTodayPreview, stylesToday, styleNameToId);
                            updateTopStyle(tvTopStyleMonth, ivTopStyleMonthPreview, stylesMonth, styleNameToId);
                            updateLoadIndicator(finalActive, finalPending);

                            // Update Team Performance in real-time
                            fetchTeamPerformance(todayDateStr, monthSuffix, value);
                        });
                    }
                }));
    }

    private void fetchDetailedRevenue(String today, String month) {
        db.collection("bookings").get().addOnSuccessListener(bookings -> {
            Map<String, String> bookingDates = new HashMap<>();
            for (QueryDocumentSnapshot b : bookings) {
                bookingDates.put(b.getId(), b.getString("date"));
            }

            db.collection("session_payments").get().addOnSuccessListener(payments -> {
                double rToday = 0, rMonth = 0;
                for (QueryDocumentSnapshot p : payments) {
                    String bId = p.getString("bookingId");
                    Double amt = p.getDouble("paymentAmount");
                    if (bId != null && amt != null) {
                        String date = bookingDates.get(bId);
                        if (today.equals(date)) rToday += amt;
                        if (date != null && date.contains(month)) rMonth += amt;
                    }
                }
                tvRevToday.setText(String.format(Locale.US, "RM %.2f", rToday));
                tvRevMonth.setText(String.format(Locale.US, "RM %.2f", rMonth));
            });
        });
    }

    private void updateTopStyle(TextView tv, ImageView iv, Map<String, Integer> styles, Map<String, String> styleIdMap) {
        if (styles.isEmpty()) {
            tv.setText("N/A");
            if (iv != null) {
                iv.setPadding(32, 32, 32, 32);
                iv.setImageResource(R.drawable.ic_hair);
            }
            return;
        }
        String top = Collections.max(styles.entrySet(), Map.Entry.comparingByValue()).getKey();
        tv.setText(top);
        loadServiceImage(top, styleIdMap.get(top), iv);
    }

    private void loadServiceImage(String name, String id, ImageView imageView) {
        if (name == null || imageView == null || !isAdded()) return;
        try {
            String[] images = requireContext().getAssets().list("images");
            if (images != null) {
                String key = (id != null && id.startsWith("hs_")) ? id.substring(3) : "";
                String cleanKey = key.toLowerCase().replace(" ", "").replace("-", "");
                String cleanName = name.toLowerCase().replace(" ", "").replace("-", "");

                for (String imageName : images) {
                    String cleanImg = imageName.toLowerCase().split("\\.")[0].replace(" ", "").replace("-", "");
                    boolean matchFound = (!cleanKey.isEmpty() && (cleanImg.contains(cleanKey) || cleanKey.contains(cleanImg))) ||
                                       (!cleanName.isEmpty() && (cleanName.contains(cleanImg) || cleanImg.contains(cleanName)));

                    if (matchFound) {
                        int strokePadding = (int) (1.0 * getResources().getDisplayMetrics().density);
                        imageView.setPadding(strokePadding, strokePadding, strokePadding, strokePadding);
                        Glide.with(this)
                                .load("file:///android_asset/images/" + imageName)
                                .transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop(),
                                           new com.bumptech.glide.load.resource.bitmap.RoundedCorners((int) (12 * getResources().getDisplayMetrics().density)))
                                .into(imageView);
                        return;
                    }
                }
            }
        } catch (IOException e) { e.printStackTrace(); }

        imageView.setPadding(32, 32, 32, 32);
        imageView.setImageResource(R.drawable.ic_hair);
    }

    private void updateLoadIndicator(int active, int pending) {
        int total = active + pending;
        int color;
        String category;
        if (total == 0) { category = "Quiet"; color = Color.GRAY; }
        else if (total < 3) { category = "Light"; color = ContextCompat.getColor(requireContext(), R.color.success_green_icon); }
        else if (total < 6) { category = "Moderate"; color = ContextCompat.getColor(requireContext(), R.color.alert_yellow_icon); }
        else { category = "High"; color = ContextCompat.getColor(requireContext(), R.color.warning_red_icon); }
        
        tvAdminLoadIndicator.setTextColor(color);
        fetchAiLoadDescription(active, pending, category);
    }

    private void fetchAiLoadDescription(int active, int pending, String category) {
        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("PASTE_YOUR_API_KEY_HERE")) {
            tvAdminLoadIndicator.setText(category + " Load");
            return;
        }

        GenerativeModel gm = new GenerativeModel("gemini-3.1-flash-lite", apiKey);
        GenerativeModelFutures model = GenerativeModelFutures.from(gm);

        String prompt = String.format(Locale.US,
                "Describe a barber shop's current workload in exactly 5 to 8 words. " +
                "Context: %d active, %d pending. Category: %s. " +
                "Make it witty for an admin dashboard. No emojis, no periods.",
                active, pending, category);

        Content content = new Content.Builder().addText(prompt).build();
        Futures.addCallback(model.generateContent(content), new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(@NonNull GenerateContentResponse result) {
                String text = result.getText();
                if (text != null && isAdded()) {
                    handler.post(() -> tvAdminLoadIndicator.setText(text.trim()));
                }
            }
            @Override
            public void onFailure(@NonNull Throwable t) {
                if (isAdded()) {
                    handler.post(() -> tvAdminLoadIndicator.setText(category + " Load"));
                }
            }
        }, Executors.newSingleThreadExecutor());
    }

    private void fetchTeamPerformance(String today, String month, com.google.firebase.firestore.QuerySnapshot realtimeBookings) {
        db.collection("employees").get().addOnSuccessListener(employees -> {
            db.collection("session_payments").get().addOnSuccessListener(payments -> {
                db.collection("hairstylist_ratings").get().addOnSuccessListener(ratings -> {
                    db.collection("session_timers").get().addOnSuccessListener(timers -> {
                        if (!isAdded()) return;
                        barberList.clear();

                        if (employees.isEmpty()) {
                            tvNoTeamStats.setVisibility(View.VISIBLE);
                            barberAdapter.notifyDataSetChanged();
                            return;
                        }
                        tvNoTeamStats.setVisibility(View.GONE);

                        Map<String, Double> bookingPayments = new HashMap<>();
                        for (QueryDocumentSnapshot p : payments) {
                            bookingPayments.put(p.getString("bookingId"), p.getDouble("paymentAmount"));
                        }

                        Map<String, List<Double>> empRatings = new HashMap<>();
                        for (QueryDocumentSnapshot r : ratings) {
                            String eId = r.getString("employeeId");
                            Double val = r.getDouble("rating");
                            if (eId != null && val != null) {
                                empRatings.computeIfAbsent(eId, k -> new ArrayList<>()).add(val);
                            }
                        }

                        Map<String, Long> sessionDurations = new HashMap<>();
                        for (QueryDocumentSnapshot t : timers) {
                            com.google.firebase.Timestamp start = t.getTimestamp("startTime");
                            com.google.firebase.Timestamp end = t.getTimestamp("endTime");
                            Long paused = t.getLong("totalPausedMillis");
                            if (start != null && end != null) {
                                long duration = end.toDate().getTime() - start.toDate().getTime() - (paused != null ? paused : 0);
                                sessionDurations.put(t.getId(), duration);
                            }
                        }

                        List<QueryDocumentSnapshot> allRatings = new ArrayList<>();
                        for (QueryDocumentSnapshot r : ratings) allRatings.add(r);

                        double totalShopRating = 0;
                        int ratingCount = 0;

                        for (QueryDocumentSnapshot empDoc : employees) {
                            String eId = empDoc.getId();
                            
                            // ACTIVE ROLE CHECK
                            if (!activeEmployeeIds.contains(eId)) continue;

                            String name = empDoc.getString("fullname");

                            int compToday = 0, activeToday = 0, pendToday = 0, cancToday = 0;
                            int totalToday = 0, countMonth = 0;
                            double revToday = 0;

                            for (QueryDocumentSnapshot b : realtimeBookings) {
                                if (eId.equals(b.getString("employeeId"))) {
                                    String date = b.getString("date");
                                    String status = b.getString("status");

                                    if (today.equals(date)) {
                                        totalToday++;
                                        if ("Completed".equalsIgnoreCase(status)) {
                                            compToday++;
                                            revToday += bookingPayments.getOrDefault(b.getId(), 0.0);
                                        } else if ("Pending".equalsIgnoreCase(status)) pendToday++;
                                        else if ("Cancelled".equalsIgnoreCase(status)) cancToday++;
                                        else activeToday++; // Covers Starting, Paying, Rating
                                    }

                                    if (date != null && date.contains(month) && "Completed".equalsIgnoreCase(status)) {
                                        countMonth++;
                                    }
                                }
                            }

                            double avgRating = 0;
                            List<Double> rList = empRatings.get(eId);
                            if (rList != null && !rList.isEmpty()) {
                                double sum = 0;
                                for (Double d : rList) sum += d;
                                avgRating = sum / rList.size();
                                totalShopRating += sum;
                                ratingCount += rList.size();
                            }

                            BarberPerformance barber = new BarberPerformance(name, eId, avgRating, compToday, activeToday, pendToday, cancToday, totalToday, countMonth, revToday);
                            
                            // Collect booked times for this barber today and calc service time
                            for (QueryDocumentSnapshot b : realtimeBookings) {
                                if (eId.equals(b.getString("employeeId")) && today.equals(b.getString("date"))) {
                                    String status = b.getString("status");
                                    if (!"Cancelled".equalsIgnoreCase(status)) {
                                        String time = b.getString("time");
                                        if (time != null) barber.slotStatuses.put(time, status);
                                    }
                                    
                                    if ("Completed".equalsIgnoreCase(status)) {
                                        Long duration = sessionDurations.get(b.getId());
                                        if (duration != null && duration > 0) {
                                            barber.totalServiceTimeMs += duration;
                                            barber.completedWithTime++;
                                        }
                                    }
                                }
                            }
                            
                            barberList.add(barber);
                        }

                        if (ratingCount > 0) {
                            double shopAvg = totalShopRating / ratingCount;
                            tvOverallRating.setText(String.format(Locale.getDefault(), "%.1f", shopAvg));
                            generateAiSentimentSummary(shopAvg, ratingCount, allRatings);
                        }

                        if (!allRatings.isEmpty()) {
                            Collections.sort(allRatings, (r1, r2) -> {
                                Timestamp t1 = r1.getTimestamp("timestamp");
                                Timestamp t2 = r2.getTimestamp("timestamp");
                                if (t1 == null || t2 == null) return 0;
                                return t2.compareTo(t1);
                            });

                            int limit = Math.min(allRatings.size(), 5);
                            updateReviewsUI(allRatings.subList(0, limit));
                            tvAdminTotalReviewsCount.setText("Based on " + allRatings.size() + " reviews");
                            tvAdminNoReviewsPlaceholder.setVisibility(View.GONE);
                        } else {
                            tvAdminTotalReviewsCount.setText("Based on 0 reviews");
                            tvAdminNoReviewsPlaceholder.setVisibility(View.VISIBLE);
                            if (llAdminRecentReviewsList != null) llAdminRecentReviewsList.removeAllViews();
                        }
                        
                        barberAdapter.notifyDataSetChanged();
                        if (isFirstTeamLoad && !barberList.isEmpty()) {
                            runLayoutAnimation(rvTeamPerformance);
                            isFirstTeamLoad = false;
                        }
                    });
                });
            });
        });
    }

    private void runLayoutAnimation(final RecyclerView recyclerView) {
        final android.view.animation.LayoutAnimationController controller = android.view.animation.AnimationUtils.loadLayoutAnimation(recyclerView.getContext(), R.anim.layout_animation_pop_up);
        recyclerView.setLayoutAnimation(controller);
        if (recyclerView.getAdapter() != null) recyclerView.getAdapter().notifyDataSetChanged();
        recyclerView.scheduleLayoutAnimation();
    }

    private static class BarberPerformance {
        String name, id;
        double rating, revenueToday;
        int completed, active, pending, cancelled, totalToday, completedMonth;
        long totalServiceTimeMs = 0;
        int completedWithTime = 0;
        boolean isExpanded = false;
        String selectedAmPm = "PM"; // Default to PM
        Map<String, String> slotStatuses = new HashMap<>(); // time -> status

        BarberPerformance(String name, String id, double rating, int completed, int active, int pending, int cancelled, int totalToday, int completedMonth, double revenueToday) {
            this.name = name; this.id = id; this.rating = rating;
            this.completed = completed; this.active = active; this.pending = pending; this.cancelled = cancelled;
            this.totalToday = totalToday; this.completedMonth = completedMonth; this.revenueToday = revenueToday;
        }

        String getAvgServiceTime() {
            if (completedWithTime == 0) return "0s";
            long avgMs = totalServiceTimeMs / completedWithTime;
            long seconds = avgMs / 1000;
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long secs = seconds % 60;

            if (hours > 0) {
                return String.format(Locale.getDefault(), "%dh %dm %ds", hours, minutes, secs);
            } else if (minutes > 0) {
                return String.format(Locale.getDefault(), "%dm %ds", minutes, secs);
            } else {
                return String.format(Locale.getDefault(), "%ds", secs);
            }
        }
    }

    private class BarberAdapter extends RecyclerView.Adapter<BarberAdapter.ViewHolder> {
        private int expandedPosition = -1;

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_barber_performance, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BarberPerformance b = barberList.get(position);
            holder.tvName.setText(b.name);
            holder.tvRating.setText(String.format(Locale.getDefault(), "%.1f", b.rating));
            holder.tvRevenue.setText(String.format(Locale.US, "RM %.2f", b.revenueToday));
            holder.tvComp.setText(String.valueOf(b.completed));
            holder.tvActive.setText(String.valueOf(b.active));
            holder.tvPend.setText(String.valueOf(b.pending));
            holder.tvCanc.setText(String.valueOf(b.cancelled));
            holder.tvCounter.setText(b.completed + "/" + b.totalToday);
            holder.tvRevTodayCard.setText(String.format(Locale.US, "RM %.2f", b.revenueToday));
            holder.tvAvgTime.setText(b.getAvgServiceTime());

            // --- Status Action Logic ---
            updateBarberStatus(holder, b);

            // --- Available Slots Logic ---
            updateSlotToggles(holder, b);
            updateHourGrid(holder, b);

            holder.btnAm.setOnClickListener(v -> {
                b.selectedAmPm = "AM";
                updateSlotToggles(holder, b);
                updateHourGrid(holder, b);
            });

            holder.btnPm.setOnClickListener(v -> {
                b.selectedAmPm = "PM";
                updateSlotToggles(holder, b);
                updateHourGrid(holder, b);
            });
            // -----------------------------

            // Hide divider for the last item
            holder.vDivider.setVisibility(position == barberList.size() - 1 ? View.GONE : View.VISIBLE);

            boolean isExpanded = position == expandedPosition;
            
            // Reset state for manual animation
            ViewGroup.LayoutParams layoutParams = holder.llExpandable.getLayoutParams();
            if (isExpanded) {
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                holder.llExpandable.setVisibility(View.VISIBLE);
                holder.ivArrow.setRotation(0);
            } else {
                layoutParams.height = 0;
                holder.llExpandable.setVisibility(View.GONE);
                holder.ivArrow.setRotation(180);
            }
            holder.llExpandable.setLayoutParams(layoutParams);

            holder.llHeader.setOnClickListener(v -> {
                int currentPos = holder.getAdapterPosition();
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
                        if (val == 0) holder.llExpandable.setVisibility(View.GONE);
                    });
                    animator.start();
                    holder.ivArrow.animate().rotation(180).setDuration(450).start();
                }

                if (prevExpanded != -1 && prevExpanded != currentPos) {
                    notifyItemChanged(prevExpanded);
                }
            });

            db.collection("profile_pics").document(b.id).get().addOnSuccessListener(doc -> {
                if (doc.exists() && isAdded()) {
                    String url = doc.getString("url");
                    if (url != null && !url.isEmpty()) Glide.with(AdminHomeFragment.this).load(url).placeholder(R.drawable.ic_profile).into(holder.ivBarber);
                }
            });
        }

        private void updateSlotToggles(ViewHolder holder, BarberPerformance b) {
            boolean isAm = "AM".equals(b.selectedAmPm);
            int primary = ContextCompat.getColor(requireContext(), R.color.primary_color);
            int white = ContextCompat.getColor(requireContext(), R.color.white);
            int black = ContextCompat.getColor(requireContext(), R.color.black);
            int strokeWidth = (int) (1 * requireContext().getResources().getDisplayMetrics().density);

            // AM Button styling
            if (isAm) {
                holder.btnAm.setBackgroundTintList(android.content.res.ColorStateList.valueOf(white));
                holder.btnAm.setTextColor(primary);
                holder.btnAm.setStrokeColor(android.content.res.ColorStateList.valueOf(black));
                holder.btnAm.setStrokeWidth(strokeWidth);
            } else {
                holder.btnAm.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary));
                holder.btnAm.setTextColor(white);
                holder.btnAm.setStrokeColor(android.content.res.ColorStateList.valueOf(white));
                holder.btnAm.setStrokeWidth(strokeWidth);
            }

            // PM Button styling
            if (!isAm) {
                holder.btnPm.setBackgroundTintList(android.content.res.ColorStateList.valueOf(white));
                holder.btnPm.setTextColor(primary);
                holder.btnPm.setStrokeColor(android.content.res.ColorStateList.valueOf(black));
                holder.btnPm.setStrokeWidth(strokeWidth);
            } else {
                holder.btnPm.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary));
                holder.btnPm.setTextColor(white);
                holder.btnPm.setStrokeColor(android.content.res.ColorStateList.valueOf(white));
                holder.btnPm.setStrokeWidth(strokeWidth);
            }
        }

        private void updateBarberStatus(ViewHolder holder, BarberPerformance b) {
            long now = NetworkTimeManager.getInstance().getCurrentTime();
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
            cal.setTimeInMillis(now);
            int hour = cal.get(Calendar.HOUR_OF_DAY);

            String actionText;
            int bgColorRes;

            boolean isShopBreak = (hour == 14 || hour == 19);
            
            // Check if any booking is currently "Starting" (Active)
            boolean hasActiveBooking = false;
            for (String status : b.slotStatuses.values()) {
                if ("Starting".equalsIgnoreCase(status)) {
                    hasActiveBooking = true;
                    break;
                }
            }

            // Priority: Active > On Break > Idle
            if (hasActiveBooking) {
                actionText = "Active";
                bgColorRes = R.color.primary_color;
            } else if (isShopBreak) {
                actionText = "On Break";
                bgColorRes = R.color.alert_yellow_icon;
            } else {
                actionText = "Idle";
                bgColorRes = R.color.info_blue_icon;
            }

            holder.tvStatus.setText(actionText);
            
            // Set card background for rounded status badge
            if (holder.mcvStatus != null) {
                holder.mcvStatus.setCardBackgroundColor(ContextCompat.getColor(requireContext(), bgColorRes));
            } else {
                // Fallback if MCV not yet inflated or handled
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setColor(ContextCompat.getColor(requireContext(), bgColorRes));
                gd.setCornerRadius(4 * getResources().getDisplayMetrics().density);
                holder.tvStatus.setBackground(gd);
            }
        }

        private void updateHourGrid(ViewHolder holder, BarberPerformance b) {
            long now = NetworkTimeManager.getInstance().getCurrentTime();
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
            cal.setTimeInMillis(now);
            
            // Leeway logic matching BookingActivity
            Calendar cutoffCal = (Calendar) cal.clone();
            if (cutoffCal.get(Calendar.MINUTE) > 10) {
                cutoffCal.add(Calendar.HOUR_OF_DAY, 1);
            }
            cutoffCal.set(Calendar.MINUTE, 0);
            cutoffCal.set(Calendar.SECOND, 0);
            cutoffCal.set(Calendar.MILLISECOND, 0);

            for (int i = 0; i < 12; i++) {
                int hour = i + 1;
                String timeStr = String.format(Locale.getDefault(), "%02d:00 %s", hour, b.selectedAmPm);
                String bookingStatus = b.slotStatuses.get(timeStr);
                boolean isBooked = bookingStatus != null;
                boolean isClosed = !isWithinBusinessHours(hour, b.selectedAmPm);
                
                // Logic for "Wasted Time" / Past slots matching BookingActivity
                Calendar slotCal = (Calendar) cal.clone();
                slotCal.set(Calendar.HOUR, hour == 12 ? 0 : hour);
                slotCal.set(Calendar.AM_PM, "AM".equals(b.selectedAmPm) ? Calendar.AM : Calendar.PM);
                slotCal.set(Calendar.MINUTE, 0);
                slotCal.set(Calendar.SECOND, 0);
                slotCal.set(Calendar.MILLISECOND, 0);

                boolean isPast = slotCal.before(cutoffCal);

                com.google.android.material.button.MaterialButton btn = holder.hourButtons[i];
                if (btn == null) continue;

                if (isClosed) {
                    btn.setEnabled(false);
                    btn.setAlpha(0.2f);
                    btn.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.transparent));
                    btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.grey_grey));
                    btn.setStrokeColorResource(R.color.grey_grey);
                    btn.setStrokeWidth((int) (1 * requireContext().getResources().getDisplayMetrics().density));
                } else if (isBooked) {
                    btn.setEnabled(true);
                    btn.setAlpha(1.0f);
                    int bgColor;
                    if ("Pending".equalsIgnoreCase(bookingStatus)) {
                        bgColor = R.color.alert_yellow_icon;
                    } else if ("Completed".equalsIgnoreCase(bookingStatus)) {
                        bgColor = R.color.primary_color;
                    } else {
                        // Starting, Paying, etc.
                        bgColor = R.color.info_blue_icon;
                    }
                    btn.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), bgColor));
                    btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
                    btn.setStrokeWidth(0);
                } else if (isPast) {
                    // Wasted Time
                    btn.setEnabled(true);
                    btn.setAlpha(1.0f);
                    btn.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.warning_red_icon));
                    btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
                    btn.setStrokeWidth(0);
                } else {
                    // Available
                    btn.setEnabled(true);
                    btn.setAlpha(1.0f);
                    btn.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.transparent));
                    btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_color));
                    btn.setStrokeColorResource(R.color.primary_color);
                    btn.setStrokeWidth((int) (1 * requireContext().getResources().getDisplayMetrics().density));
                }
            }
        }

        private boolean isWithinBusinessHours(int hour, String amPm) {
            if ("AM".equals(amPm)) return hour == 10 || hour == 11;
            else return hour != 2 && hour != 7; // 12, 1, 3, 4, 5, 6, 8, 9, 10, 11 are valid (2pm & 7pm are break)
        }

        @Override
        public int getItemCount() { return barberList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvRating, tvRevenue, tvComp, tvActive, tvPend, tvCanc, tvCounter, tvRevTodayCard, tvAvgTime, tvStatus;
            MaterialCardView mcvStatus;
            ImageView ivBarber, ivArrow;
            LinearLayout llHeader, llExpandable;
            View vDivider;
            
            // Slots UI
            com.google.android.material.button.MaterialButton btnAm, btnPm;
            com.google.android.material.button.MaterialButton[] hourButtons = new com.google.android.material.button.MaterialButton[12];

            ViewHolder(View v) {
                super(v);
                tvName = v.findViewById(R.id.tv_barber_performance_name);
                tvRating = v.findViewById(R.id.tv_barber_performance_rating);
                tvRevenue = v.findViewById(R.id.tv_barber_perf_revenue_val);
                ivBarber = v.findViewById(R.id.iv_barber_performance_profile);
                llHeader = v.findViewById(R.id.ll_barber_perf_header);
                llExpandable = v.findViewById(R.id.ll_barber_perf_expandable);
                ivArrow = v.findViewById(R.id.iv_barber_perf_expand_arrow);
                tvComp = v.findViewById(R.id.tv_barber_perf_completed);
                tvActive = v.findViewById(R.id.tv_barber_perf_active);
                tvPend = v.findViewById(R.id.tv_barber_perf_pending);
                tvCanc = v.findViewById(R.id.tv_barber_perf_cancelled);
                tvCounter = v.findViewById(R.id.tv_barber_perf_booking_counter);
                tvRevTodayCard = v.findViewById(R.id.tv_barber_perf_rev_today);
                tvAvgTime = v.findViewById(R.id.tv_barber_perf_avg_time);
                tvStatus = v.findViewById(R.id.tv_barber_performance_status);
                mcvStatus = v.findViewById(R.id.mcv_barber_performance_status);
                vDivider = v.findViewById(R.id.v_barber_perf_divider);

                btnAm = v.findViewById(R.id.btn_barber_perf_am);
                btnPm = v.findViewById(R.id.btn_barber_perf_pm);
                
                int[] ids = {
                    R.id.btn_barber_perf_hour_1, R.id.btn_barber_perf_hour_2, R.id.btn_barber_perf_hour_3,
                    R.id.btn_barber_perf_hour_4, R.id.btn_barber_perf_hour_5, R.id.btn_barber_perf_hour_6,
                    R.id.btn_barber_perf_hour_7, R.id.btn_barber_perf_hour_8, R.id.btn_barber_perf_hour_9,
                    R.id.btn_barber_perf_hour_10, R.id.btn_barber_perf_hour_11, R.id.btn_barber_perf_hour_12
                };
                for (int i = 0; i < 12; i++) hourButtons[i] = v.findViewById(ids[i]);
            }
        }
    }

    private void generateAiSentimentSummary(double avgRating, int totalCount, List<QueryDocumentSnapshot> recentReviews) {
        if (getContext() == null || tvSentimentMsg == null) return;

        android.content.SharedPreferences prefs = getContext().getSharedPreferences("admin_sentiment_prefs", android.content.Context.MODE_PRIVATE);
        String cachedDate = prefs.getString("last_refresh_date", "");
        String cachedSummary = prefs.getString("cached_summary", "");

        Calendar klCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        klCal.setTimeInMillis(NetworkTimeManager.getInstance().getCurrentTime());
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        sdfDate.setTimeZone(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        String todayDate = sdfDate.format(klCal.getTime());

        if (todayDate.equals(cachedDate) && !cachedSummary.isEmpty()) {
            tvSentimentMsg.setText(cachedSummary);
            return;
        }

        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("PASTE_YOUR_API_KEY_HERE")) {
            updateSentimentMessage(avgRating);
            return;
        }

        GenerativeModel gm = new GenerativeModel("gemini-3.1-flash-lite", apiKey);
        GenerativeModelFutures model = GenerativeModelFutures.from(gm);

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Generate a short, professional business summary (max 30 words) for a barber shop admin based on customer feedback. ");
        promptBuilder.append("Overall shop rating is ").append(String.format(Locale.getDefault(), "%.1f", avgRating)).append("/5.0 from ").append(totalCount).append(" reviews. ");
        promptBuilder.append("Recent comments: ");
        int limit = Math.min(recentReviews.size(), 10);
        for (int i = 0; i < limit; i++) {
            String comment = recentReviews.get(i).getString("comment");
            if (comment != null && !comment.isEmpty()) promptBuilder.append("'").append(comment).append("', ");
        }
        promptBuilder.append("Summarize the general mood and highlight one key strength or area of concern. No emojis.");

        Content content = new Content.Builder().addText(promptBuilder.toString()).build();
        Futures.addCallback(model.generateContent(content), new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(@NonNull GenerateContentResponse result) {
                String text = result.getText();
                if (text != null && isAdded()) {
                    handler.post(() -> {
                        String trimmed = text.trim();
                        tvSentimentMsg.setText(trimmed);
                        prefs.edit()
                                .putString("last_refresh_date", todayDate)
                                .putString("cached_summary", trimmed)
                                .apply();
                    });
                }
            }
            @Override
            public void onFailure(@NonNull Throwable t) {
                handler.post(() -> updateSentimentMessage(avgRating));
            }
        }, Executors.newSingleThreadExecutor());
    }

    private void updateSentimentMessage(double avg) {
        String msg;
        if (avg >= 4.5) msg = "Customers are loving the service!";
        else if (avg >= 3.5) msg = "Consistent quality, mostly happy faces.";
        else if (avg >= 2.5) msg = "Room for improvement in service quality.";
        else msg = "Attention needed: Customer satisfaction is low.";
        tvSentimentMsg.setText(msg);
    }

    private void updateReviewsUI(List<QueryDocumentSnapshot> reviews) {
        if (llAdminRecentReviewsList == null || !isAdded()) return;
        llAdminRecentReviewsList.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < reviews.size(); i++) {
            QueryDocumentSnapshot doc = reviews.get(i);
            View itemView = inflater.inflate(R.layout.item_review_employee, llAdminRecentReviewsList, false);

            TextView tvName = itemView.findViewById(R.id.tv_reviewer_name);
            TextView tvComment = itemView.findViewById(R.id.tv_review_comment);
            TextView tvDate = itemView.findViewById(R.id.tv_review_date);
            TextView tvTime = itemView.findViewById(R.id.tv_review_time);
            TextView tvBookingId = itemView.findViewById(R.id.tv_review_booking_id);
            RatingBar rbStars = itemView.findViewById(R.id.rb_review_stars);
            ImageView ivReviewer = itemView.findViewById(R.id.iv_reviewer_profile);
            
            // Barber Name Badge
            View mcvBarberBadge = itemView.findViewById(R.id.mcv_review_barber_badge);
            TextView tvBarberName = itemView.findViewById(R.id.tv_review_barber_name);

            Double rating = doc.getDouble("rating");
            if (rating != null) rbStars.setRating(rating.floatValue());
            
            tvComment.setText(doc.getString("comment"));
            
            String bId = doc.getString("bookingId");
            if (bId != null) {
                tvBookingId.setText("#" + bId);
                db.collection("bookings").document(bId).get().addOnSuccessListener(bookingDoc -> {
                    if (bookingDoc.exists() && isAdded()) {
                        tvTime.setText(bookingDoc.getString("time"));
                        String date = bookingDoc.getString("date");
                        if (date != null) tvDate.setText(date);
                    }
                });
            }

            // Fetch Barber Name
            String employeeId = doc.getString("employeeId");
            if (employeeId != null && tvBarberName != null) {
                mcvBarberBadge.setVisibility(View.VISIBLE);
                db.collection("employees").document(employeeId).get().addOnSuccessListener(empDoc -> {
                    if (empDoc.exists() && isAdded()) {
                        tvBarberName.setText(empDoc.getString("fullname"));
                    }
                });
            }

            Timestamp ts = doc.getTimestamp("timestamp");
            if (ts != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
                tvDate.setText(sdf.format(ts.toDate()));
            }

            String customerId = doc.getString("customerId");
            if (customerId != null) {
                db.collection("customers").document(customerId).get().addOnSuccessListener(userDoc -> {
                    if (userDoc.exists() && isAdded()) {
                        // Use "name" as standard across app
                        tvName.setText(userDoc.getString("name"));
                    }
                });
                db.collection("profile_pics").document(customerId).get().addOnSuccessListener(picDoc -> {
                    if (picDoc.exists() && isAdded()) {
                        String url = picDoc.getString("url");
                        if (url != null && !url.isEmpty()) Glide.with(AdminHomeFragment.this).load(url).placeholder(R.drawable.ic_profile).into(ivReviewer);
                    }
                });
            }

            llAdminRecentReviewsList.addView(itemView);
            
            // Add a small divider if not the last item
            if (i < reviews.size() - 1) {
                View divider = new View(getContext());
                divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(Color.parseColor("#EEEEEE"));
                llAdminRecentReviewsList.addView(divider);
            }
        }
    }

    private void updateTimeStatus() {
        if (!isAdded()) return;
        TimeZone klTz = TimeZone.getTimeZone("Asia/Kuala_Lumpur");
        Calendar cal = Calendar.getInstance(klTz);
        cal.setTimeInMillis(NetworkTimeManager.getInstance().getCurrentTime());

        SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm", Locale.getDefault());
        timeFmt.setTimeZone(klTz);
        tvAdminTime.setText(timeFmt.format(cal.getTime()));

        SimpleDateFormat ampmFmt = new SimpleDateFormat("a", Locale.getDefault());
        ampmFmt.setTimeZone(klTz);
        tvAdminAmpm.setText(ampmFmt.format(cal.getTime()));

        SimpleDateFormat dateFmt = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
        dateFmt.setTimeZone(klTz);
        tvAdminDate.setText(dateFmt.format(cal.getTime()));

        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int totalMinutes = (hour * 60) + minute;

        boolean isBreak = (hour == 14 || hour == 19);
        boolean isOpen = ((hour >= 10 && hour < 14) || (hour >= 15 && hour < 19) || (hour >= 20 && hour < 24)) && !isBreak;
        
        if (isBreak) {
            tvAdminShopStatus.setText("ON BREAK");
            tvAdminShopStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.alert_yellow_icon));
        } else {
            tvAdminShopStatus.setText(isOpen ? "OPEN" : "CLOSED");
            tvAdminShopStatus.setTextColor(isOpen ? ContextCompat.getColor(requireContext(), R.color.primary_color) : Color.RED);
        }

        // Calculate Remaining Time & Color
        String remainingText = "";
        int bgColorRes = R.color.info_blue_icon;

        if (totalMinutes < 600) { // Before 10:00 AM
            int diff = 600 - totalMinutes;
            remainingText = "Opens in " + formatDiff(diff);
            if (diff <= 30) bgColorRes = R.color.info_blue_icon;
        } else if (totalMinutes < 840) { // 10:00 AM - 2:00 PM (Open)
            int diff = 840 - totalMinutes;
            remainingText = "Closes in " + formatDiff(diff);
            if (diff <= 15) bgColorRes = R.color.warning_red_icon;
            else if (diff <= 60) bgColorRes = R.color.alert_yellow_icon;
            else bgColorRes = R.color.success_green_icon;
        } else if (totalMinutes < 900) { // 2:00 PM - 3:00 PM (Break)
            int diff = 900 - totalMinutes;
            remainingText = "Opens in " + formatDiff(diff);
            if (diff <= 30) bgColorRes = R.color.info_blue_icon;
        } else if (totalMinutes < 1140) { // 3:00 PM - 7:00 PM (Open)
            int diff = 1140 - totalMinutes;
            remainingText = "Closes in " + formatDiff(diff);
            if (diff <= 15) bgColorRes = R.color.warning_red_icon;
            else if (diff <= 60) bgColorRes = R.color.alert_yellow_icon;
            else bgColorRes = R.color.success_green_icon;
        } else if (totalMinutes < 1200) { // 7:00 PM - 8:00 PM (Break)
            int diff = 1200 - totalMinutes;
            remainingText = "Opens in " + formatDiff(diff);
            if (diff <= 30) bgColorRes = R.color.info_blue_icon;
        } else { // 8:00 PM - Midnight (Open)
            int diff = 1440 - totalMinutes;
            remainingText = "Closes in " + formatDiff(diff);
            if (diff <= 15) bgColorRes = R.color.warning_red_icon;
            else if (diff <= 60) bgColorRes = R.color.alert_yellow_icon;
            else bgColorRes = R.color.success_green_icon;
        }
        tvAdminRemainingTime.setText(remainingText);
        tvAdminRemainingTime.setTextColor(Color.WHITE);
        
        // Background with corner radius
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(ContextCompat.getColor(requireContext(), bgColorRes));
        gd.setCornerRadius(4 * getResources().getDisplayMetrics().density);
        tvAdminRemainingTime.setBackground(gd);
        int padding = (int) (6 * getResources().getDisplayMetrics().density);
        tvAdminRemainingTime.setPadding(padding, padding/2, padding, padding/2);

        int bgRes;
        String timeName;
        if (hour < 6) { bgRes = R.drawable.bg_time_midnight; timeName = "Midnight"; }
        else if (hour < 8) { bgRes = R.drawable.bg_time_sunrise; timeName = "Sunrise"; }
        else if (hour < 12) { bgRes = R.drawable.bg_time_morning; timeName = "Morning"; }
        else if (hour < 14) { bgRes = R.drawable.bg_time_noon; timeName = "Noon"; }
        else if (hour < 18) { bgRes = R.drawable.bg_time_evening; timeName = "Afternoon"; }
        else if (hour < 20) { bgRes = R.drawable.bg_time_sunset; timeName = "Sunset"; }
        else { bgRes = R.drawable.bg_time_night; timeName = "Night"; }
        
        ivAdminTimeBg.setImageResource(bgRes);
        tvAdminTimeName.setText(timeName);
    }

    private String formatDiff(int totalMinutes) {
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;
        if (h > 0) return h + "h " + m + "m";
        else return m + "m";
    }

    private void populateWelcomeMessage() {
        try {
            MasterKey mk = new MasterKey.Builder(requireContext()).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            android.content.SharedPreferences prefs = EncryptedSharedPreferences.create(requireContext(), "secret_shared_prefs", mk,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            String name = prefs.getString("fullname", "Admin");
            tvWelcome.setText("Hello, Admin " + name);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateAiJab(boolean isOpen, boolean forceNew) {
        if (tvAiJab == null) return;
        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("PASTE_YOUR_API_KEY_HERE")) {
            tvAiJab.setText(isOpen ? "Welcome to the command center, boss." : "Shop is closed. Rest up, Admin!");
            return;
        }

        GenerativeModel gm = new GenerativeModel("gemini-3.1-flash-lite", apiKey);
        GenerativeModelFutures model = GenerativeModelFutures.from(gm);

        String prompt = "Generate 10 funny, very short roasts for a barber shop admin. Separate by '/'. " +
                "Context: Shop is " + (isOpen ? "OPEN" : "CLOSED") + ". Make it about managing stats and lazy barbers. No emojis.";

        Content content = new Content.Builder().addText(prompt).build();
        Futures.addCallback(model.generateContent(content), new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(@NonNull GenerateContentResponse result) {
                String text = result.getText();
                if (text != null && isAdded()) {
                    String[] parts = text.split("/");
                    handler.post(() -> {
                        jabPool.clear();
                        for (String p : parts) if (!p.trim().isEmpty()) jabPool.add(p.trim());
                        showNextJabFromPool();
                    });
                }
            }
            @Override public void onFailure(@NonNull Throwable t) {}
        }, Executors.newSingleThreadExecutor());
    }

    private void showNextJabFromPool() {
        if (jabPool.isEmpty()) return;
        String jab = jabPool.get(new Random().nextInt(jabPool.size()));
        startTypingAnimation(jab);
    }

    private void startTypingAnimation(final String text) {
        if (handler == null || tvAiJab == null) return;
        if (typingRunnable != null) handler.removeCallbacks(typingRunnable);
        typingRunnable = new Runnable() {
            int index = 0;
            @Override
            public void run() {
                if (index <= text.length()) {
                    tvAiJab.setText(text.substring(0, index) + " |");
                    index++;
                    handler.postDelayed(this, 50);
                } else {
                    tvAiJab.setText(text);
                }
            }
        };
        handler.post(typingRunnable);
    }

    private void loadJabPool() { /* Handled by syncAndFetch */ }
    private void saveJabPool() { /* Optional */ }

    @Override
    public void onResume() {
        super.onResume();
        loadProfileImage();
    }

    private void loadProfileImage() {
        if (ivProfile == null || mAuth.getCurrentUser() == null) return;
        db.collection("profile_pics").document(mAuth.getCurrentUser().getUid()).get().addOnSuccessListener(doc -> {
            if (isAdded() && doc.exists()) {
                String url = doc.getString("url");
                if (url != null) Glide.with(this).load(url).placeholder(R.drawable.ic_profile).into(ivProfile);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        for (ListenerRegistration lr : listeners) lr.remove();
        if (handler != null) handler.removeCallbacksAndMessages(null);
    }
}
