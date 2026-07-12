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
    private TextView tvNoTeamStats;

    // Customer Sentiment UI
    private TextView tvOverallRating, tvSentimentMsg;

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

        // Management Buttons
        view.findViewById(R.id.btn_admin_manage_staff).setOnClickListener(v -> Toast.makeText(getContext(), "Staff Management Activity planned", Toast.LENGTH_SHORT).show());
        view.findViewById(R.id.btn_admin_manage_services).setOnClickListener(v -> Toast.makeText(getContext(), "Service Management Activity planned", Toast.LENGTH_SHORT).show());
        view.findViewById(R.id.btn_admin_reports).setOnClickListener(v -> Toast.makeText(getContext(), "Analytics & Reports Activity planned", Toast.LENGTH_SHORT).show());
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
                            if ("Starting".equals(status)) active++;
                            else if ("Pending".equals(status)) pending++;
                            else if ("Cancelled".equals(status)) cancelled++;
                            else if ("Completed".equals(status)) completed++;
                            
                            if (style != null && !style.isEmpty()) stylesToday.put(style, stylesToday.getOrDefault(style, 0) + 1);
                        }
                        
                        if (date != null && date.contains(monthSuffix)) {
                            if (style != null && !style.isEmpty()) stylesMonth.put(style, stylesMonth.getOrDefault(style, 0) + 1);
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
                        });
                    }
                }));

        // 3. Team Performance & Ratings
        fetchTeamPerformance(todayDateStr, monthSuffix);
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

    private void fetchTeamPerformance(String today, String month) {
        db.collection("employees").get().addOnSuccessListener(employees -> {
            db.collection("bookings").get().addOnSuccessListener(allBookings -> {
                db.collection("session_payments").get().addOnSuccessListener(payments -> {
                    db.collection("hairstylist_ratings").get().addOnSuccessListener(ratings -> {

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

                        double totalShopRating = 0;
                        int ratingCount = 0;

                        for (QueryDocumentSnapshot empDoc : employees) {
                            String eId = empDoc.getId();
                            String name = empDoc.getString("fullname");

                            int compToday = 0, activeToday = 0, pendToday = 0, cancToday = 0;
                            int totalToday = 0, countMonth = 0;
                            double revToday = 0;

                            for (QueryDocumentSnapshot b : allBookings) {
                                if (eId.equals(b.getString("employeeId"))) {
                                    String date = b.getString("date");
                                    String status = b.getString("status");

                                    if (today.equals(date)) {
                                        totalToday++;
                                        if ("Completed".equals(status)) {
                                            compToday++;
                                            revToday += bookingPayments.getOrDefault(b.getId(), 0.0);
                                        } else if ("Starting".equals(status)) activeToday++;
                                        else if ("Pending".equals(status)) pendToday++;
                                        else if ("Cancelled".equals(status)) cancToday++;
                                    }

                                    if (date != null && date.contains(month) && "Completed".equals(status)) {
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

                            barberList.add(new BarberPerformance(name, eId, avgRating, compToday, activeToday, pendToday, cancToday, totalToday, countMonth, revToday));
                        }

                        if (ratingCount > 0) {
                            double shopAvg = totalShopRating / ratingCount;
                            tvOverallRating.setText(String.format(Locale.getDefault(), "%.1f", shopAvg));
                            updateSentimentMessage(shopAvg);
                        }
                        barberAdapter.notifyDataSetChanged();
                        runLayoutAnimation(rvTeamPerformance);
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
        boolean isExpanded = false;

        BarberPerformance(String name, String id, double rating, int completed, int active, int pending, int cancelled, int totalToday, int completedMonth, double revenueToday) {
            this.name = name; this.id = id; this.rating = rating;
            this.completed = completed; this.active = active; this.pending = pending; this.cancelled = cancelled;
            this.totalToday = totalToday; this.completedMonth = completedMonth; this.revenueToday = revenueToday;
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
            holder.tvAvgTime.setText("0 min");

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

        @Override
        public int getItemCount() { return barberList.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvRating, tvRevenue, tvComp, tvActive, tvPend, tvCanc, tvCounter, tvRevTodayCard, tvAvgTime;
            ImageView ivBarber, ivArrow;
            LinearLayout llHeader, llExpandable;
            View vDivider;

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
                vDivider = v.findViewById(R.id.v_barber_perf_divider);
            }
        }
    }

    private void updateSentimentMessage(double avg) {
        String msg;
        if (avg >= 4.5) msg = "Customers are loving the service!";
        else if (avg >= 3.5) msg = "Consistent quality, mostly happy faces.";
        else if (avg >= 2.5) msg = "Room for improvement in service quality.";
        else msg = "Attention needed: Customer satisfaction is low.";
        tvSentimentMsg.setText(msg);
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

        boolean isOpen = (hour >= 10 && hour < 13) || (hour >= 14 && hour < 24);
        tvAdminShopStatus.setText(isOpen ? "OPEN" : "CLOSED");
        tvAdminShopStatus.setTextColor(isOpen ? ContextCompat.getColor(requireContext(), R.color.primary_color) : Color.RED);

        // Calculate Remaining Time & Color
        String remainingText = "";
        int remainingColor = Color.WHITE;

        if (totalMinutes < 600) { // Before 10:00 AM
            int diff = 600 - totalMinutes;
            remainingText = "Opens in " + formatDiff(diff);
            if (diff <= 30) remainingColor = ContextCompat.getColor(requireContext(), R.color.info_blue_icon);
        } else if (totalMinutes < 780) { // 10:00 AM - 1:00 PM (Open)
            int diff = 780 - totalMinutes;
            remainingText = "Closes in " + formatDiff(diff);
            if (diff <= 15) remainingColor = ContextCompat.getColor(requireContext(), R.color.warning_red_icon);
            else if (diff <= 60) remainingColor = ContextCompat.getColor(requireContext(), R.color.alert_yellow_icon);
        } else if (totalMinutes < 840) { // 1:00 PM - 2:00 PM (Break)
            int diff = 840 - totalMinutes;
            remainingText = "Opens in " + formatDiff(diff);
            if (diff <= 30) remainingColor = ContextCompat.getColor(requireContext(), R.color.info_blue_icon);
        } else { // 2:00 PM - Midnight (Open)
            int diff = 1440 - totalMinutes;
            remainingText = "Closes in " + formatDiff(diff);
            if (diff <= 15) remainingColor = ContextCompat.getColor(requireContext(), R.color.warning_red_icon);
            else if (diff <= 60) remainingColor = ContextCompat.getColor(requireContext(), R.color.alert_yellow_icon);
        }
        tvAdminRemainingTime.setText(remainingText);
        tvAdminRemainingTime.setTextColor(remainingColor);

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
