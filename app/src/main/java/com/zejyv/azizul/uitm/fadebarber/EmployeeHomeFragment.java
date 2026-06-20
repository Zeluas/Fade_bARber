package com.zejyv.azizul.uitm.fadebarber;

import android.animation.LayoutTransition;
 import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * EmployeeHomeFragment: Dashboard for barbers/employees.
 * Features time-based themes, booking counters, and customer management.
 */
public class EmployeeHomeFragment extends Fragment {

    private Handler handler;
    private MaterialCardView mcvTopBar;
    private ScrollView svHomeContent;
    private TextView tvAiJab, tvWelcome, tvBookingCounter, tvTotalUpcoming, tvShopStatus, tvTimeEmployee, tvDateEmployee;
    private ImageView ivProfile;

    // Current Customer UI
    private TextView tvCustomerName, tvBookingTime, tvServiceName;
    private ImageView ivServicePreview;
    private View llBookingDetailsRow, tvNoBookingPlaceholder;
    private LinearLayout llLeftCol, llRightCol;
    private TextView tvLabelCustomer, tvLabelTime, tvLabelChosenHaircut;
    private View btnCallCustomer, btnNoShow, btnEditBooking, btnStartSession, btnEnterSession;
    private TextView tvLabelSessionTimer, tvSessionTimer;
    private String nearestCustomerPhone = "";

    // Upcoming Bookings UI
    private LinearLayout llUpcomingList;
    private View tvNoUpcomingPlaceholder;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration bookingListener;

    // Nearest booking info for AI Jabs
    private String nearestCustomerTime = null;
    private String nearestCustomerName = null;
    private String currentBookingId = null;
    private String lastBookingId = null;
    private List<String> lastUpcomingIds = new ArrayList<>();

    // UI State flags
    private boolean hasAnimatedBooking = false;
    private boolean isAnimating = false;
    private boolean isExpanded = true;
    private boolean isLockedAtMin = false;

    // AI Jab related state
    private static List<String> jabPool = new ArrayList<>();
    private static String lastUsedBookingId = "initial_session";
    private static boolean lastUsedShopStatusOpen = false;
    private static boolean isPoolInitialized = false;
    private long lastJabTime = 0;
    private static final long JAB_COOLDOWN = 10000;
    private Runnable typingRunnable;
    private long serverTimeOffset = 0;

    private void saveJabPool() {
        if (getContext() == null) return;
        try {
            android.content.SharedPreferences prefs = requireContext().getSharedPreferences("ai_jab_prefs_emp", android.content.Context.MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < jabPool.size(); i++) {
                sb.append(jabPool.get(i));
                if (i < jabPool.size() - 1) sb.append("|||");
            }
            editor.putString("jab_pool", sb.toString());
            editor.putString("last_booking_id", lastUsedBookingId);
            editor.putBoolean("last_shop_open", lastUsedShopStatusOpen);
            editor.putBoolean("pool_init", isPoolInitialized);
            editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadJabPool() {
        if (getContext() == null) return;
        try {
            android.content.SharedPreferences prefs = requireContext().getSharedPreferences("ai_jab_prefs_emp", android.content.Context.MODE_PRIVATE);
            String savedPool = prefs.getString("jab_pool", "");
            if (!savedPool.isEmpty()) {
                String[] parts = savedPool.split("\\|\\|\\|");
                jabPool.clear();
                for (String p : parts) if (!p.isEmpty()) jabPool.add(p);
            }
            lastUsedBookingId = prefs.getString("last_booking_id", "initial_session");
            lastUsedShopStatusOpen = prefs.getBoolean("last_shop_open", false);
            isPoolInitialized = prefs.getBoolean("pool_init", false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final Runnable updateTimeThemeRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimeTheme();
            if (handler != null) {
                handler.postDelayed(this, 60000);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_employee_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        handler = new Handler(Looper.getMainLooper());
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        loadJabPool();
        initializeViews(view);
        setupClickLogic(view);
        populateWelcomeMessage();
        syncServerTimeAndFetch();
    }

    private void syncServerTimeAndFetch() {
        String uid = mAuth.getUid() != null ? mAuth.getUid() : "employee_sync";
        java.util.Map<String, Object> syncData = new java.util.HashMap<>();
        syncData.put("t", FieldValue.serverTimestamp());

        db.collection("metadata").document("time_sync_" + uid).set(syncData)
                .addOnSuccessListener(aVoid -> {
                    db.collection("metadata").document("time_sync_" + uid).get(Source.SERVER)
                            .addOnSuccessListener(doc -> {
                                Timestamp serverTime = doc.getTimestamp("t");
                                if (serverTime != null) {
                                    serverTimeOffset = serverTime.toDate().getTime() - System.currentTimeMillis();
                                }
                                fetchNearestBooking();
                            })
                            .addOnFailureListener(e -> fetchNearestBooking());
                })
                .addOnFailureListener(e -> fetchNearestBooking());
    }

    private void initializeViews(View view) {
        mcvTopBar = view.findViewById(R.id.mcv_employee_top_bar);
        svHomeContent = view.findViewById(R.id.sv_employee_home_content);
        tvAiJab = view.findViewById(R.id.tv_employee_ai_jab);
        tvWelcome = view.findViewById(R.id.tv_welcome_employee);
        tvBookingCounter = view.findViewById(R.id.tv_booking_counter);
        tvTotalUpcoming = view.findViewById(R.id.tv_total_upcoming);
        tvShopStatus = view.findViewById(R.id.tv_shop_status_employee);
        tvTimeEmployee = view.findViewById(R.id.tv_time_employee);
        tvDateEmployee = view.findViewById(R.id.tv_date_employee);
        ivProfile = view.findViewById(R.id.iv_employee_profile);

        // Booking details initialization
        tvCustomerName = view.findViewById(R.id.tv_customer_name);
        tvBookingTime = view.findViewById(R.id.tv_booking_time);
        tvServiceName = view.findViewById(R.id.tv_service_name);
        ivServicePreview = view.findViewById(R.id.iv_service_preview);
        llBookingDetailsRow = view.findViewById(R.id.ll_employee_booking_details_row);
        tvNoBookingPlaceholder = view.findViewById(R.id.tv_no_customer_placeholder);
        llLeftCol = view.findViewById(R.id.ll_employee_booking_details_left_col);
        llRightCol = view.findViewById(R.id.ll_employee_booking_details_right_col);

        // Labels and Buttons for staggered animation
        tvLabelCustomer = view.findViewById(R.id.tv_employee_label_customer);
        tvLabelTime = view.findViewById(R.id.tv_employee_label_time);
        tvLabelChosenHaircut = view.findViewById(R.id.tv_employee_label_chosen_haircut);
        btnCallCustomer = view.findViewById(R.id.btn_call_customer);
        btnNoShow = view.findViewById(R.id.btn_no_show);
        btnEditBooking = view.findViewById(R.id.btn_edit_booking_employee);
        btnStartSession = view.findViewById(R.id.btn_start_session);
        btnEnterSession = view.findViewById(R.id.btn_enter_session);

        tvLabelSessionTimer = view.findViewById(R.id.tv_employee_label_session_timer);
        tvSessionTimer = view.findViewById(R.id.tv_employee_session_timer);

        // Upcoming bookings initialization
        llUpcomingList = view.findViewById(R.id.ll_upcoming_bookings_list);
        if (llUpcomingList != null) {
            LayoutTransition transition = new LayoutTransition();
            transition.enableTransitionType(LayoutTransition.CHANGING);
            llUpcomingList.setLayoutTransition(transition);
        }
        tvNoUpcomingPlaceholder = view.findViewById(R.id.tv_no_upcoming_placeholder);

        LinearLayout llTextContainer = view.findViewById(R.id.ll_employee_top_text_container);
        if (llTextContainer != null) {
            LayoutTransition transition = new LayoutTransition();
            llTextContainer.setLayoutTransition(transition);
            transition.enableTransitionType(LayoutTransition.CHANGING);
        }

        updateBookingCounter();
        // Counter UI will be updated by fetchNearestBooking
    }

    private void updateBookingCounter() {
        // This will be called from fetchNearestBooking once we have data
    }

    private void updateBookingCounterUI(int count) {
        if (tvBookingCounter != null) {
            tvBookingCounter.setText(String.valueOf(count));
        }
    }

    private void populateWelcomeMessage() {
        try {
            MasterKey masterKey = new MasterKey.Builder(requireContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            android.content.SharedPreferences prefs = EncryptedSharedPreferences.create(
                    requireContext(),
                    "secret_shared_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            String name = prefs.getString("fullname", "Barber");
            String welcomeTemplate = getString(R.string.welcome_employee);
            tvWelcome.setText(welcomeTemplate.replace("(Barber)", name));
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    private void fetchNearestBooking() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        if (bookingListener != null) {
            bookingListener.remove();
        }

        bookingListener = db.collection("bookings")
                .whereEqualTo("employeeId", uid)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value != null) {
                        List<QueryDocumentSnapshot> todayPendingBookings = new ArrayList<>();
                        Calendar klCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
                        klCal.setTimeInMillis(System.currentTimeMillis() + serverTimeOffset);
                        String todayDateStr = String.format(Locale.getDefault(), "%02d/%02d/%02d",
                                klCal.get(Calendar.DAY_OF_MONTH), klCal.get(Calendar.MONTH) + 1, klCal.get(Calendar.YEAR) % 100);

                        int todayCount = 0;
                        for (QueryDocumentSnapshot doc : value) {
                            String date = doc.getString("date");
                            String status = doc.getString("status");
                            if (todayDateStr.equals(date) && !"Cancelled".equalsIgnoreCase(status)) {
                                todayCount++;
                                if ("Pending".equalsIgnoreCase(status) || "Starting".equalsIgnoreCase(status)) {
                                    todayPendingBookings.add(doc);
                                }
                            }
                        }

                        updateBookingCounterUI(todayCount);

                        if (!todayPendingBookings.isEmpty()) {
                            Collections.sort(todayPendingBookings, new Comparator<QueryDocumentSnapshot>() {
                                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                                @Override
                                public int compare(QueryDocumentSnapshot b1, QueryDocumentSnapshot b2) {
                                    String s1 = b1.getString("status");
                                    String s2 = b2.getString("status");
                                    boolean start1 = "Starting".equalsIgnoreCase(s1);
                                    boolean start2 = "Starting".equalsIgnoreCase(s2);

                                    if (start1 && !start2) return -1;
                                    if (!start1 && start2) return 1;

                                    try {
                                        Date d1 = sdf.parse(b1.getString("time"));
                                        Date d2 = sdf.parse(b2.getString("time"));
                                        if (d1 == null || d2 == null) return 0;
                                        return d1.compareTo(d2);
                                    } catch (ParseException e) {
                                        return 0;
                                    }
                                }
                            });
                        }

                        String newCurrentId = todayPendingBookings.isEmpty() ? null : todayPendingBookings.get(0).getId();
                        List<String> newUpcomingIds = new ArrayList<>();
                        for (int i = 1; i < todayPendingBookings.size(); i++) {
                            newUpcomingIds.add(todayPendingBookings.get(i).getId());
                        }

                        // Determine the type of transition
                        if (lastBookingId != null && !lastBookingId.equals(newCurrentId)) {
                            // The current booking has changed (Cancelled or Completed)
                            boolean movingFromUpcoming = !lastUpcomingIds.isEmpty() && lastUpcomingIds.get(0).equals(newCurrentId);
                            
                            if (movingFromUpcoming) {
                                // First upcoming is moving to current slot
                                animateBookingDeparture(() -> {
                                    animateFirstUpcomingDeparture(() -> {
                                        lastBookingId = newCurrentId;
                                        lastUpcomingIds = newUpcomingIds;
                                        updateBookingUI(todayPendingBookings.get(0));
                                        updateUpcomingListUI(todayPendingBookings.subList(1, todayPendingBookings.size()));
                                    });
                                });
                            } else {
                                // Just a departure (e.g. cancelled and next is different or none)
                                animateBookingDeparture(() -> {
                                    lastBookingId = newCurrentId;
                                    lastUpcomingIds = newUpcomingIds;
                                    if (newCurrentId != null) {
                                        updateBookingUI(todayPendingBookings.get(0));
                                    } else {
                                        resetBookingUI();
                                    }
                                    updateUpcomingListUI(todayPendingBookings.size() > 1 ? todayPendingBookings.subList(1, todayPendingBookings.size()) : new ArrayList<>());
                                });
                            }
                        } else {
                            // Initial load or no current change
                            lastBookingId = newCurrentId;
                            lastUpcomingIds = newUpcomingIds;
                            if (newCurrentId != null) {
                                updateBookingUI(todayPendingBookings.get(0));
                            } else {
                                resetBookingUI();
                            }
                            updateUpcomingListUI(todayPendingBookings.size() > 1 ? todayPendingBookings.subList(1, todayPendingBookings.size()) : new ArrayList<>());
                        }
                    }
                });
    }

    private void animateBookingDeparture(Runnable onEnd) {
        if (llBookingDetailsRow == null || llBookingDetailsRow.getVisibility() != View.VISIBLE) {
            if (onEnd != null) onEnd.run();
            return;
        }

        final View[] labels = {tvLabelCustomer, tvLabelTime, tvLabelChosenHaircut, tvLabelSessionTimer};
        final View[] values = {tvCustomerName, tvBookingTime, tvServiceName, ivServicePreview, tvSessionTimer};
        final View[] buttons = {btnCallCustomer, btnNoShow, btnEditBooking, btnStartSession, btnEnterSession};

        // Fade out elements and slide down
        for (View v : buttons) if (v != null) v.animate().alpha(0f).translationY(20f).setDuration(500).start();
        
        handler.postDelayed(() -> {
            for (View v : values) if (v != null) v.animate().alpha(0f).translationX(-30f).setDuration(500).start();
            if (llRightCol != null) llRightCol.animate().alpha(0f).translationX(100f).setDuration(500).start();
        }, 300);

        handler.postDelayed(() -> {
            for (View v : labels) if (v != null) v.animate().alpha(0f).translationX(-50f).setDuration(500).start();
        }, 600);

        handler.postDelayed(() -> {
            final int initialHeight = llBookingDetailsRow.getHeight();
            ValueAnimator heightAnimator = ValueAnimator.ofInt(initialHeight, 0);
            heightAnimator.setDuration(800);
            heightAnimator.setInterpolator(new android.view.animation.AccelerateInterpolator());
            heightAnimator.addUpdateListener(animation -> {
                int val = (Integer) animation.getAnimatedValue();
                ViewGroup.LayoutParams lp = llBookingDetailsRow.getLayoutParams();
                lp.height = val;
                llBookingDetailsRow.setLayoutParams(lp);
                llBookingDetailsRow.setAlpha((float) val / initialHeight);
            });
            heightAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    llBookingDetailsRow.setVisibility(View.GONE);
                    ViewGroup.LayoutParams lp = llBookingDetailsRow.getLayoutParams();
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    llBookingDetailsRow.setLayoutParams(lp);
                    hasAnimatedBooking = false;
                    if (onEnd != null) onEnd.run();
                }
            });
            heightAnimator.start();
        }, 1100);
    }

    private void animateFirstUpcomingDeparture(Runnable onEnd) {
        if (llUpcomingList == null || llUpcomingList.getChildCount() == 0) {
            if (onEnd != null) onEnd.run();
            return;
        }

        View firstItem = llUpcomingList.getChildAt(0);
        firstItem.animate()
                .translationY(-100f)
                .alpha(0f)
                .setDuration(500)
                .withEndAction(() -> {
                    // We don't remove it here yet, the list update will do it.
                    if (onEnd != null) onEnd.run();
                }).start();
    }

    private void updateUpcomingListUI(List<QueryDocumentSnapshot> upcomingBookings) {
        if (llUpcomingList == null) return;
        
        if (tvTotalUpcoming != null) {
            tvTotalUpcoming.setText(upcomingBookings.size() + " Left");
        }

        boolean wasEmpty = llUpcomingList.getChildCount() == 0;
        llUpcomingList.removeAllViews();

        if (upcomingBookings.isEmpty()) {
            if (tvNoUpcomingPlaceholder != null) tvNoUpcomingPlaceholder.setVisibility(View.VISIBLE);
            return;
        }

        if (tvNoUpcomingPlaceholder != null) tvNoUpcomingPlaceholder.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < upcomingBookings.size(); i++) {
            QueryDocumentSnapshot doc = upcomingBookings.get(i);
            View itemView = inflater.inflate(R.layout.item_upcoming_booking_employee, llUpcomingList, false);
            
            TextView tvName = itemView.findViewById(R.id.tv_upcoming_customer_name);
            TextView tvStyle = itemView.findViewById(R.id.tv_upcoming_hairstyle);
            TextView tvTime = itemView.findViewById(R.id.tv_upcoming_time);
            ImageView ivService = itemView.findViewById(R.id.iv_upcoming_service_preview);
            View btnCall = itemView.findViewById(R.id.btn_call_upcoming);

            tvStyle.setText(doc.getString("hairstyleName"));
            tvTime.setText(doc.getString("time"));

            // Fetch customer name later
            String cid = doc.getString("customerId");
            if (cid != null) {
                tvName.setText("Loading...");
                db.collection("customers").document(cid).get().addOnSuccessListener(userDoc -> {
                    if (userDoc.exists() && isAdded()) {
                        String name = userDoc.getString("name");
                        tvName.setText(name);
                    } else {
                        tvName.setText("Unknown Customer");
                    }
                });
            }

            // Load image
            loadHaircutImageToView(doc.getString("hairstyleName"), ivService);

            btnCall.setOnClickListener(v -> {
                if (cid != null) {
                    db.collection("customers").document(cid).get().addOnSuccessListener(userDoc -> {
                        if (userDoc.exists() && isAdded()) {
                            String phone = userDoc.getString("phone");
                            if (getActivity() instanceof MainActivityEmployee && phone != null && !phone.isEmpty()) {
                                ((MainActivityEmployee) getActivity()).showCallCustomerDialog(phone);
                            } else {
                                Toast.makeText(getContext(), "Phone number not available", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            });

            llUpcomingList.addView(itemView);

            // Animate arrival if it's the first time adding to an empty list
            if (wasEmpty && i == 0) {
                animateUpcomingArrival(itemView);
            }

            // Add divider if not the last item
            if (i < upcomingBookings.size() - 1) {
                View divider = new View(getContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * getResources().getDisplayMetrics().density));
                lp.setMargins((int) (12 * getResources().getDisplayMetrics().density), 0, (int) (12 * getResources().getDisplayMetrics().density), 0);
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(Color.parseColor("#EEEEEE"));
                llUpcomingList.addView(divider);
            }
        }
    }

    private void animateUpcomingArrival(View itemView) {
        itemView.setAlpha(0f);
        itemView.post(() -> {
            final int targetHeight = itemView.getHeight();
            ValueAnimator animator = ValueAnimator.ofInt(0, targetHeight);
            animator.setDuration(800);
            animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
            animator.addUpdateListener(animation -> {
                int val = (Integer) animation.getAnimatedValue();
                ViewGroup.LayoutParams lp = itemView.getLayoutParams();
                lp.height = val;
                itemView.setLayoutParams(lp);
                itemView.setAlpha((float) val / targetHeight);
            });
            animator.start();
        });
    }

    private void loadHaircutImageToView(String name, ImageView imageView) {
        if (name == null || imageView == null) return;
        try {
            String[] images = requireContext().getAssets().list("images");
            if (images != null) {
                String cleanName = name.toLowerCase().replace(" ", "").replace("-", "");
                for (String imageName : images) {
                    String cleanImg = imageName.toLowerCase().split("\\.")[0].replace(" ", "").replace("-", "");
                    if (cleanName.contains(cleanImg) || cleanImg.contains(cleanName)) {
                        try (java.io.InputStream is = requireContext().getAssets().open("images/" + imageName)) {
                            imageView.setImageBitmap(android.graphics.BitmapFactory.decodeStream(is));
                            return;
                        }
                    }
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
        imageView.setImageResource(R.drawable.ic_hair);
    }

    private ListenerRegistration sessionTimerListener;
    private long sessionStartTime = 0;
    private long totalPausedMillis = 0;
    private boolean isPaused = false;
    private long pausedAtMillis = 0;

    private void updateBookingUI(QueryDocumentSnapshot doc) {
        String oldTime = nearestCustomerTime;
        String oldHaircut = tvServiceName != null ? tvServiceName.getText().toString() : null;
        currentBookingId = doc.getId();
        nearestCustomerTime = doc.getString("time");
        String serviceName = doc.getString("hairstyleName");
        
        // Fetch customer info (name and phone)
        String customerId = doc.getString("customerId");
        if (customerId != null) {
            db.collection("customers").document(customerId).get().addOnSuccessListener(userDoc -> {
                if (userDoc.exists()) {
                    String name = userDoc.getString("name");
                    nearestCustomerName = name;
                    nearestCustomerPhone = userDoc.getString("phone");
                    
                    if (tvCustomerName != null) tvCustomerName.setText(nearestCustomerName);
                }
            });
        }

        if (tvBookingTime != null) tvBookingTime.setText(nearestCustomerTime);
        if (tvServiceName != null) tvServiceName.setText(serviceName);

        // Session State Handling
        String status = doc.getString("status");
        if ("Starting".equals(status)) {
            if (btnStartSession != null) btnStartSession.setVisibility(View.GONE);
            if (btnNoShow != null) btnNoShow.setVisibility(View.GONE);
            if (btnEnterSession != null) btnEnterSession.setVisibility(View.VISIBLE);
            
            if (btnEditBooking != null) btnEditBooking.setVisibility(View.GONE);
            if (tvLabelSessionTimer != null) tvLabelSessionTimer.setVisibility(View.VISIBLE);
            if (tvSessionTimer != null) {
                tvSessionTimer.setVisibility(View.VISIBLE);
                startSessionTimerListener();
            }
        } else {
            if (btnStartSession != null) btnStartSession.setVisibility(View.VISIBLE);
            if (btnNoShow != null) btnNoShow.setVisibility(View.VISIBLE);
            if (btnEnterSession != null) btnEnterSession.setVisibility(View.GONE);
            
            if (btnEditBooking != null) btnEditBooking.setVisibility(View.VISIBLE);
            if (tvLabelSessionTimer != null) tvLabelSessionTimer.setVisibility(View.GONE);
            if (tvSessionTimer != null) {
                tvSessionTimer.setVisibility(View.GONE);
                stopSessionTimerListener();
            }
        }

        if (ivServicePreview != null) {
            loadHaircutImage(serviceName);
        }

        if (tvNoBookingPlaceholder != null) tvNoBookingPlaceholder.setVisibility(View.GONE);
        if (llBookingDetailsRow != null && llBookingDetailsRow.getVisibility() != View.VISIBLE) {
            animateBookingArrival(status);
        } else if (llBookingDetailsRow != null) {
            llBookingDetailsRow.setVisibility(View.VISIBLE);
        }

        TimeZone klTimeZone = TimeZone.getTimeZone("Asia/Kuala_Lumpur");
        Calendar calendar = Calendar.getInstance(klTimeZone);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        boolean isOpen = (hour >= 10 && hour < 13) || (hour >= 14 && hour < 24);

        boolean contextChanged = !currentBookingId.equals(lastUsedBookingId) 
                || (isOpen != lastUsedShopStatusOpen)
                || !serviceName.equals(oldHaircut)
                || !nearestCustomerTime.equals(oldTime);

        if (!isPoolInitialized || contextChanged) {
            lastUsedBookingId = currentBookingId;
            lastUsedShopStatusOpen = isOpen;
            isPoolInitialized = true;
            updateAiJab(isOpen, true);
        } else if (jabPool.isEmpty()) {
            updateAiJab(isOpen, true);
        } else {
            showNextJabFromPool();
        }
    }

    private void startSessionTimerListener() {
        if (currentBookingId == null || sessionTimerListener != null) return;

        sessionTimerListener = db.collection("session_timers").document(currentBookingId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;

                    com.google.firebase.Timestamp ts = doc.getTimestamp("startTime");
                    isPaused = doc.getBoolean("isPaused") != null && doc.getBoolean("isPaused");
                    totalPausedMillis = doc.getLong("totalPausedMillis") != null ? doc.getLong("totalPausedMillis") : 0;

                    if (ts != null) {
                        sessionStartTime = ts.toDate().getTime();
                        if (isPaused) {
                            com.google.firebase.Timestamp pts = doc.getTimestamp("pausedAt");
                            pausedAtMillis = pts != null ? pts.toDate().getTime() : System.currentTimeMillis();
                        }
                        updateEmployeeHomeTimer();
                    }
                });
    }

    private void stopSessionTimerListener() {
        if (sessionTimerListener != null) {
            sessionTimerListener.remove();
            sessionTimerListener = null;
        }
    }

    private void updateEmployeeHomeTimer() {
        if (handler == null || sessionTimerListener == null || tvSessionTimer == null) return;

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (sessionTimerListener == null) return;

                long now = System.currentTimeMillis();
                long diff;
                if (isPaused) {
                    diff = pausedAtMillis - sessionStartTime - totalPausedMillis;
                } else {
                    diff = now - sessionStartTime - totalPausedMillis;
                }
                if (diff < 0) diff = 0;

                int seconds = (int) (diff / 1000);
                int minutes = seconds / 60;
                int hours = minutes / 60;
                seconds = seconds % 60;
                minutes = minutes % 60;

                String timeStr = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
                if (tvSessionTimer != null) {
                    tvSessionTimer.setText(timeStr);
                }

                if (!isPaused) {
                    handler.postDelayed(this, 1000);
                }
            }
        });
    }

    private void animateBookingArrival(String status) {
        if (llBookingDetailsRow == null || hasAnimatedBooking) {
            if (llBookingDetailsRow != null) llBookingDetailsRow.setVisibility(View.VISIBLE);
            return;
        }
        hasAnimatedBooking = true;

        llBookingDetailsRow.setVisibility(View.VISIBLE);
        llBookingDetailsRow.setAlpha(0f);

        final View[] labels = {tvLabelCustomer, tvLabelTime, tvLabelChosenHaircut, tvLabelSessionTimer};
        final View[] values = {tvCustomerName, tvBookingTime, tvServiceName, ivServicePreview, tvSessionTimer};
        final View[] buttons = {btnCallCustomer, btnNoShow, btnEditBooking, btnStartSession, btnEnterSession};

        boolean isStarting = "Starting".equals(status);

        for (View v : labels) if (v != null) { v.setAlpha(0f); v.setTranslationX(-50f); }
        for (View v : values) if (v != null) { v.setAlpha(0f); v.setTranslationX(-30f); }
        for (View v : buttons) if (v != null) {
            // Remove staggered alpha animation for Edit button if starting
            // It will instead fade in with the parent row at its locked alpha
            if (!(v == btnEditBooking && isStarting)) {
                v.setAlpha(0f);
            }
            v.setTranslationY(20f);
        }

        if (llRightCol != null) {
            llRightCol.setAlpha(0f);
            llRightCol.setTranslationX(100f);
        }

        llBookingDetailsRow.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        final int targetHeight = llBookingDetailsRow.getMeasuredHeight();

        ValueAnimator heightAnimator = ValueAnimator.ofInt(0, targetHeight);
        heightAnimator.setDuration(1500);
        heightAnimator.setInterpolator(new android.view.animation.BounceInterpolator());
        heightAnimator.addUpdateListener(animation -> {
            int val = (Integer) animation.getAnimatedValue();
            ViewGroup.LayoutParams lp = llBookingDetailsRow.getLayoutParams();
            lp.height = val;
            llBookingDetailsRow.setLayoutParams(lp);
            llBookingDetailsRow.setAlpha((float) val / targetHeight);
        });

        heightAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                ViewGroup.LayoutParams lp = llBookingDetailsRow.getLayoutParams();
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                llBookingDetailsRow.setLayoutParams(lp);

                for (View v : labels) if (v != null) v.animate().alpha(1f).translationX(0f).setDuration(800).start();
                handler.postDelayed(() -> {
                    for (View v : values) if (v != null) v.animate().alpha(1f).translationX(0f).setDuration(800).start();
                    if (llRightCol != null) llRightCol.animate().alpha(1f).translationX(0f).setDuration(1000).start();
                }, 800);
                handler.postDelayed(() -> {
                    for (View v : buttons) {
                        if (v != null) {
                            if (v == btnEditBooking && isStarting) {
                                // Remove alpha animation, only slide up
                                v.animate().translationY(0f).setDuration(800).start();
                            } else {
                                v.animate().alpha(1f).translationY(0f).setDuration(800).start();
                            }
                        }
                    }
                }, 1400);
            }
        });
        heightAnimator.start();
    }

    private void resetBookingUI() {
        currentBookingId = null;
        nearestCustomerTime = null;
        nearestCustomerName = null;
        if (llBookingDetailsRow != null) llBookingDetailsRow.setVisibility(View.GONE);
        if (tvNoBookingPlaceholder != null) tvNoBookingPlaceholder.setVisibility(View.VISIBLE);
        hasAnimatedBooking = false;

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        boolean isOpen = hour >= 10 && hour < 24;

        boolean contextChanged = !"no_booking".equals(lastUsedBookingId) || (isOpen != lastUsedShopStatusOpen);
        if (!isPoolInitialized || contextChanged) {
            lastUsedBookingId = "no_booking";
            lastUsedShopStatusOpen = isOpen;
            isPoolInitialized = true;
            updateAiJab(isOpen, true);
        } else if (jabPool.isEmpty()) {
            updateAiJab(isOpen, true);
        } else {
            showNextJabFromPool();
        }
    }

    private void loadHaircutImage(String name) {
        if (name == null || ivServicePreview == null) return;
        try {
            String[] images = requireContext().getAssets().list("images");
            if (images != null) {
                String cleanName = name.toLowerCase().replace(" ", "").replace("-", "");
                for (String imageName : images) {
                    String cleanImg = imageName.toLowerCase().split("\\.")[0].replace(" ", "").replace("-", "");
                    if (cleanName.contains(cleanImg) || cleanImg.contains(cleanName)) {
                        try (java.io.InputStream is = requireContext().getAssets().open("images/" + imageName)) {
                            ivServicePreview.setImageBitmap(android.graphics.BitmapFactory.decodeStream(is));
                            return;
                        }
                    }
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
        ivServicePreview.setImageResource(R.drawable.ic_hair);
    }

    private void setupClickLogic(View view) {
        if (ivProfile != null) {
            ivProfile.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivityEmployee) {
                    ((MainActivityEmployee) getActivity()).navigateToProfile();
                }
            });
        }

        view.findViewById(R.id.btn_start_session).setOnClickListener(v -> {
            if (currentBookingId != null) {
                android.content.Intent intent = new android.content.Intent(getContext(), SessionActivity.class);
                intent.putExtra("BOOKING_ID", currentBookingId);
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "No active booking selected", Toast.LENGTH_SHORT).show();
            }
        });
        
        view.findViewById(R.id.btn_enter_session).setOnClickListener(v -> {
            if (currentBookingId != null) {
                android.content.Intent intent = new android.content.Intent(getContext(), SessionActivity.class);
                intent.putExtra("BOOKING_ID", currentBookingId);
                startActivity(intent);
            }
        });
        
        btnNoShow.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivityEmployee && currentBookingId != null) {
                ((MainActivityEmployee) getActivity()).showNoShowDialog(() -> {
                    db.collection("bookings").document(currentBookingId)
                            .update("status", "Cancelled")
                            .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Booking marked as No-Show", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to update status", Toast.LENGTH_SHORT).show());
                });
            }
        });
        
        if (btnCallCustomer != null) {
            btnCallCustomer.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivityEmployee && nearestCustomerPhone != null && !nearestCustomerPhone.isEmpty()) {
                    ((MainActivityEmployee) getActivity()).showCallCustomerDialog(nearestCustomerPhone);
                } else if (nearestCustomerPhone == null || nearestCustomerPhone.isEmpty()) {
                    Toast.makeText(getContext(), "Customer phone number not available", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        if (btnEditBooking != null) {
            btnEditBooking.setOnClickListener(v -> {
                if (currentBookingId != null) {
                    android.content.Intent intent = new android.content.Intent(getActivity(), BookingActivity.class);
                    intent.putExtra("EDIT_BOOKING_ID", currentBookingId);
                    intent.putExtra("IS_EMPLOYEE_EDIT", true);
                    startActivity(intent);
                }
            });
        }

        mcvTopBar.setOnClickListener(v -> {
            showNextJabFromPool();
            if (svHomeContent != null) svHomeContent.smoothScrollTo(0, 0);
        });
    }

    private void updateAiJab(boolean isOpen, boolean forceNew) {
        if (tvAiJab == null) return;
        if (!jabPool.isEmpty() && !forceNew) {
            showNextJabFromPool();
            return;
        }

        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("PASTE_YOUR_API_KEY_HERE")) {
            useFallbackJab(isOpen);
            return;
        }

        GenerativeModel gm = new GenerativeModel("gemini-3.1-flash-lite", apiKey);
        GenerativeModelFutures model = GenerativeModelFutures.from(gm);

        String shopStatus = isOpen ? "OPEN" : "CLOSED";
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Generate EXACTLY 100 funny, unique, very short (max 20 words each) jabs/roasts for a barber/employee at a barber shop app. ");
        promptBuilder.append("Separate each jab with a '/' character. DO NOT NUMBER THEM. No list formatting. ");
        promptBuilder.append("Context: The shop is currently ").append(shopStatus).append(". ");
        
        if (nearestCustomerTime != null && currentBookingId != null && !"no_booking".equals(currentBookingId)) {
            String hairStyle = tvServiceName != null ? tvServiceName.getText().toString() : "a haircut";
            promptBuilder.append("The employee has an upcoming session at ").append(nearestCustomerTime)
                    .append(" for '").append(hairStyle).append("'. ");
            promptBuilder.append("Make them personal about the work load, the specific haircut difficulty, or the timing. ");
        } else {
            promptBuilder.append("The employee doesn't have any customers right now. Roast them for their laziness or being broke. ");
        }
        
        promptBuilder.append("Make them cheeky, playful, and slightly mean but professional for a barber shop. No emojis. ");
        promptBuilder.append("Only output the raw text of the jabs separated by '/' and nothing else.");

        Content content = new Content.Builder().addText(promptBuilder.toString()).build();
        Executor executor = Executors.newSingleThreadExecutor();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(@NonNull GenerateContentResponse result) {
                String fullText = result.getText();
                if (fullText != null && isAdded()) {
                    String[] parts = fullText.split("/");
                    handler.post(() -> {
                        jabPool.clear();
                        for (String part : parts) {
                            String trimmed = part.trim();
                            if (!trimmed.isEmpty()) jabPool.add(trimmed);
                        }
                        saveJabPool();
                        showNextJabFromPool();
                    });
                }
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                handler.post(() -> useFallbackJab(isOpen));
            }
        }, executor);
    }

    private void showNextJabFromPool() {
        if (jabPool.isEmpty()) {
            Calendar calendar = Calendar.getInstance();
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            boolean isOpen = hour >= 10 && hour < 24;
            updateAiJab(isOpen, true);
            return;
        }
        int index = new Random().nextInt(jabPool.size());
        String jab = jabPool.get(index);
        jabPool.remove(index);
        saveJabPool();
        startTypingAnimation(jab);
    }

    private void startTypingAnimation(final String text) {
        if (handler == null || tvAiJab == null) return;
        if (typingRunnable != null) handler.removeCallbacks(typingRunnable);

        final String oldText = tvAiJab.getText().toString();
        if (!oldText.isEmpty() && !oldText.equals(text)) {
            startBackspaceAnimation(oldText, text);
            return;
        }

        typingRunnable = new Runnable() {
            int index = 0;
            boolean cursorVisible = true;
            @Override
            public void run() {
                if (index <= text.length()) {
                    tvAiJab.setText(text.substring(0, index) + (cursorVisible ? " |" : "  "));
                    index++;
                    cursorVisible = !cursorVisible;
                    handler.postDelayed(this, 50);
                } else {
                    tvAiJab.setText(text);
                }
            }
        };
        handler.post(typingRunnable);
    }

    private void startBackspaceAnimation(final String oldText, final String newText) {
        final int charCount = oldText.length();
        final int delay = charCount > 0 ? 1000 / charCount : 0;
        typingRunnable = new Runnable() {
            int index = charCount;
            @Override
            public void run() {
                if (index > 0) {
                    index--;
                    tvAiJab.setText(oldText.substring(0, index) + " |");
                    handler.postDelayed(this, delay);
                } else {
                    tvAiJab.setText("");
                    startTypingAnimation(newText);
                }
            }
        };
        handler.post(typingRunnable);
    }

    private void useFallbackJab(boolean isOpen) {
        String[] jabs = isOpen ? new String[]{getString(R.string.employee_jab_1)} : new String[]{getString(R.string.employee_jab_2)};
        startTypingAnimation(jabs[new Random().nextInt(jabs.length)]);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (handler != null) handler.post(updateTimeThemeRunnable);
        populateWelcomeMessage();
        loadProfileImage();
        fetchNearestBooking();
    }

    private void loadProfileImage() {
        if (ivProfile == null || mAuth.getCurrentUser() == null) return;
        
        String uid = mAuth.getCurrentUser().getUid();
        
        db.collection("profile_pics").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (isAdded() && documentSnapshot.exists()) {
                        String url = documentSnapshot.getString("url");
                        if (url != null && !url.isEmpty()) {
                            Glide.with(this)
                                    .load(url)
                                    .placeholder(R.drawable.ic_profile)
                                    .into(ivProfile);
                            return;
                        }
                    }
                    if (isAdded()) ivProfile.setImageResource(R.drawable.ic_profile);
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) ivProfile.setImageResource(R.drawable.ic_profile);
                });
    }

    @Override
    public void onPause() {
        super.onPause();
        if (handler != null) handler.removeCallbacks(updateTimeThemeRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (bookingListener != null) bookingListener.remove();
    }

    private void updateTimeTheme() {
        View view = getView();
        if (view == null || !isAdded()) return;

        ImageView ivBg = view.findViewById(R.id.iv_time_bg_employee);
        TextView tvTimeName = view.findViewById(R.id.tv_time_name_employee);
        TextView tvAmPm = view.findViewById(R.id.tv_ampm_employee);
        TextView tvShopStatus = view.findViewById(R.id.tv_shop_status_employee);

        TimeZone klTimeZone = TimeZone.getTimeZone("Asia/Kuala_Lumpur");
        Calendar calendar = Calendar.getInstance(klTimeZone);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        // Update Time and Date TextViews manually
        if (tvTimeEmployee != null) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm", Locale.getDefault());
            timeFormat.setTimeZone(klTimeZone);
            tvTimeEmployee.setText(timeFormat.format(calendar.getTime()));
        }
        if (tvDateEmployee != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            dateFormat.setTimeZone(klTimeZone);
            tvDateEmployee.setText(dateFormat.format(calendar.getTime()));
        }

        if (tvAmPm != null) {
            tvAmPm.setText(calendar.get(Calendar.AM_PM) == Calendar.AM ? 
                    getString(R.string.am_label) : getString(R.string.pm_label));
        }

        boolean isOpen = (hour >= 10 && hour < 13) || (hour >= 14 && hour < 24);
        if (tvShopStatus != null) {
            String prefix;
            String keyword;
            int statusColor;

            if (hour >= 9 && hour < 10) {
                prefix = "Wakey wakey! ";
                keyword = "Shop opens soon.";
                statusColor = Color.parseColor("#FFBD00"); // Warning orange
            } else if (isOpen) {
                prefix = "Hustle mode! ";
                keyword = "Shift in progress.";
                statusColor = ContextCompat.getColor(requireContext(), R.color.primary_color);
            } else if (hour >= 13 && hour < 14) {
                prefix = "Hungry? ";
                keyword = "Lunch break.";
                statusColor = Color.parseColor("#004CA2"); // Info blue
            } else {
                prefix = "Zzz... ";
                keyword = "Shop is closed.";
                statusColor = Color.RED;
            }

            SpannableStringBuilder builder = new SpannableStringBuilder(prefix);
            int start = builder.length();
            builder.append(keyword);
            builder.setSpan(new ForegroundColorSpan(statusColor), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvShopStatus.setText(builder);
        }

        // AI Jab refresh on context change (Shop status change)
        if (isPoolInitialized) {
            if (isOpen != lastUsedShopStatusOpen) {
                lastUsedShopStatusOpen = isOpen;
                updateAiJab(isOpen, true);
            }
        }

        String timeName;
        int bgResId;

        if (hour < 6) { timeName = getString(R.string.time_midnight); bgResId = R.drawable.bg_time_midnight; }
        else if (hour < 8) { timeName = getString(R.string.time_sunrise); bgResId = R.drawable.bg_time_sunrise; }
        else if (hour < 12) { timeName = getString(R.string.time_morning); bgResId = R.drawable.bg_time_morning; }
        else if (hour < 14) { timeName = getString(R.string.time_noon); bgResId = R.drawable.bg_time_noon; }
        else if (hour < 18) { timeName = getString(R.string.time_evening); bgResId = R.drawable.bg_time_evening; }
        else if (hour < 20) { timeName = getString(R.string.time_sunset); bgResId = R.drawable.bg_time_sunset; }
        else { timeName = getString(R.string.time_night); bgResId = R.drawable.bg_time_night; }

        if (tvTimeName != null) tvTimeName.setText(timeName);
        if (ivBg != null) ivBg.setImageResource(bgResId);
    }
}
