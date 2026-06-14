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
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
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
import com.zejyv.azizul.uitm.fadebarber.adapters.StylistAdapter;
import com.zejyv.azizul.uitm.fadebarber.models.Employee;

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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * HomeFragment: The main dashboard for the Fade bARber app.
 * Features dynamic UI updates based on time of day, AI-generated jabs/roasts,
 * and a collapsible top bar that responds to scrolling.
 */
public class HomeFragment extends Fragment {

    private Handler handler;
    private MaterialCardView mcvTopBar;
    private ScrollView svHomeContent;
    private TextView tvAiJab;

    // Booking Details UI
    private TextView tvBookingDate, tvBookingTime, tvHairstylist, tvHaircut;
    private ImageView ivHairPreview;
    private View llBookingDetailsRow, tvNoBookingPlaceholder;
    private LinearLayout llLeftCol, llRightCol;
    private TextView tvLabelDate, tvLabelTime, tvLabelStylist, tvLabelChosenHaircut;
    private View btnCallStylist, btnCancelBooking, btnEditBooking;
    
    // Stylist List UI
    private RecyclerView rvStylists;
    private ProgressBar pbStylists;
    private TextView tvNoStylists;
    private StylistAdapter stylistAdapter;
    private List<Employee> stylistList = new ArrayList<>();
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration bookingListener;

    // Nearest booking info for AI Jabs
    private String nearestBookingTime = null;
    private String nearestBookingHaircut = null;

    // UI State flags
    private boolean isExpanded = true;
    private boolean isAnimating = false;
    private boolean isLockedAtMin = false;
    private boolean hasAnimatedBooking = false;
    
    // AI Jab related state
    private static List<String> jabPool = new ArrayList<>();
    private static String lastUsedBookingId = "initial_session";
    private static boolean lastUsedShopStatusOpen = false;
    private static boolean isPoolInitialized = false;
    private long lastJabTime = 0;
    private static final long JAB_COOLDOWN = 10000; // 10 seconds cooldown between showing jabs
    private Runnable typingRunnable;

    private void saveJabPool() {
        if (getContext() == null) return;
        try {
            android.content.SharedPreferences prefs = requireContext().getSharedPreferences("ai_jab_prefs", android.content.Context.MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            
            // Join pool with specialized separator to avoid conflict with /
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
            android.content.SharedPreferences prefs = requireContext().getSharedPreferences("ai_jab_prefs", android.content.Context.MODE_PRIVATE);
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

    /**
     * Runnable to periodically update the time-based theme (e.g., background and greeting).
     */
    private final Runnable updateTimeThemeRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimeTheme();
            if (handler != null) {
                handler.postDelayed(this, 60000); // Update every minute
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        handler = new Handler(Looper.getMainLooper());

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        
        loadJabPool();

        // Initialize UI components
        mcvTopBar = view.findViewById(R.id.mcv_top_bar);
        svHomeContent = view.findViewById(R.id.sv_home_content);
        tvAiJab = view.findViewById(R.id.tv_ai_jab);
        LinearLayout llTextContainer = view.findViewById(R.id.ll_top_bar_text_container);

        // Booking details initialization
        tvBookingDate = view.findViewById(R.id.tv_booking_date_home);
        tvBookingTime = view.findViewById(R.id.tv_booking_time_home);
        tvHairstylist = view.findViewById(R.id.tv_hairstylist_name_home);
        tvHaircut = view.findViewById(R.id.tv_haircut_name_home);
        ivHairPreview = view.findViewById(R.id.iv_hairPreview);
        llBookingDetailsRow = view.findViewById(R.id.ll_booking_details_row);
        tvNoBookingPlaceholder = view.findViewById(R.id.tv_no_booking_placeholder);
        llLeftCol = view.findViewById(R.id.ll_booking_details_left_col);
        llRightCol = view.findViewById(R.id.ll_booking_details_right_col);

        // Labels and Buttons for staggered animation
        tvLabelDate = view.findViewById(R.id.tv_label_date);
        tvLabelTime = view.findViewById(R.id.tv_label_time);
        tvLabelStylist = view.findViewById(R.id.tv_label_stylist);
        tvLabelChosenHaircut = view.findViewById(R.id.tv_label_chosen_haircut);
        btnCallStylist = view.findViewById(R.id.btn_call_stylist);
        btnCancelBooking = view.findViewById(R.id.btn_cancel_booking);
        btnEditBooking = view.findViewById(R.id.btn_edit_booking);

        // Stylist list initialization
        rvStylists = view.findViewById(R.id.rv_stylists_home);
        pbStylists = view.findViewById(R.id.pb_stylists_home);
        tvNoStylists = view.findViewById(R.id.tv_no_stylists_home);
        setupStylistRecyclerView();

        // Configure smooth layout transitions for the top bar's text container
        if (llTextContainer != null) {
            LayoutTransition transition = llTextContainer.getLayoutTransition();
            if (transition == null) {
                transition = new LayoutTransition();
                llTextContainer.setLayoutTransition(transition);
            }
            transition.enableTransitionType(LayoutTransition.CHANGING);
            transition.setDuration(300); // Duration for text movement during resize
        }

        setupScrollingLogic();
        setupClickLogic();
        populateWelcomeMessage(view);
        fetchNearestBooking();
        fetchStylists();
    }

    private void setupStylistRecyclerView() {
        if (rvStylists == null) return;
        rvStylists.setLayoutManager(new LinearLayoutManager(requireContext()));
        stylistAdapter = new StylistAdapter(stylistList, false, employee -> {
            // Unclickable on Home screen
        });
        rvStylists.setAdapter(stylistAdapter);
    }

    private void fetchStylists() {
        if (pbStylists != null) pbStylists.setVisibility(View.VISIBLE);
        db.collection("employees").get(Source.SERVER).addOnCompleteListener(task -> {
            if (pbStylists != null) pbStylists.setVisibility(View.GONE);
            if (task.isSuccessful() && task.getResult() != null) {
                stylistList.clear();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    Employee employee = document.toObject(Employee.class);
                    stylistList.add(employee);
                }
                if (stylistAdapter != null) stylistAdapter.notifyDataSetChanged();
                
                if (tvNoStylists != null) {
                    tvNoStylists.setVisibility(stylistList.isEmpty() ? View.VISIBLE : View.GONE);
                }
            } else {
                if (tvNoStylists != null) {
                    tvNoStylists.setVisibility(View.VISIBLE);
                    tvNoStylists.setText("Error loading stylists.");
                }
            }
        });
    }

    /**
     * Fetches the nearest upcoming pending booking for the logged-in user.
     */
    private void fetchNearestBooking() {
        if (mAuth.getCurrentUser() == null) return;

        String uid = mAuth.getCurrentUser().getUid();

        bookingListener = db.collection("bookings")
                .whereEqualTo("customerId", uid)
                .whereEqualTo("status", "Pending")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        android.util.Log.e("HomeFragment", "Error fetching bookings: " + error.getMessage());
                        return;
                    }

                    if (value != null && !value.isEmpty()) {
                        List<QueryDocumentSnapshot> bookings = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : value) {
                            bookings.add(doc);
                        }

                        // Sort bookings by date and time to find the nearest
                        Collections.sort(bookings, new Comparator<QueryDocumentSnapshot>() {
                            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy hh:mm a", Locale.getDefault());
                            @Override
                            public int compare(QueryDocumentSnapshot b1, QueryDocumentSnapshot b2) {
                                try {
                                    Date d1 = sdf.parse(b1.getString("date") + " " + b1.getString("time"));
                                    Date d2 = sdf.parse(b2.getString("date") + " " + b2.getString("time"));
                                    if (d1 == null || d2 == null) return 0;
                                    return d1.compareTo(d2);
                                } catch (ParseException e) {
                                    return 0;
                                }
                            }
                        });

                        updateBookingUI(bookings.get(0));
                    } else {
                        resetBookingUI();
                    }
                });
    }

    private void updateBookingUI(QueryDocumentSnapshot doc) {
        String newBookingId = doc.getId();
        nearestBookingTime = doc.getString("time");
        nearestBookingHaircut = doc.getString("hairstyleName");

        if (tvBookingDate != null) tvBookingDate.setText(doc.getString("date"));
        if (tvBookingTime != null) tvBookingTime.setText(nearestBookingTime);
        if (tvHairstylist != null) tvHairstylist.setText(doc.getString("employeeName"));
        if (tvHaircut != null) tvHaircut.setText(nearestBookingHaircut);

        if (ivHairPreview != null) {
            loadHaircutImage(nearestBookingHaircut);
        }

        // Handle visibility and animation
        if (tvNoBookingPlaceholder != null) tvNoBookingPlaceholder.setVisibility(View.GONE);
        if (llBookingDetailsRow != null && llBookingDetailsRow.getVisibility() != View.VISIBLE) {
            animateBookingArrival();
        } else {
            if (llBookingDetailsRow != null) llBookingDetailsRow.setVisibility(View.VISIBLE);
        }
        
        // Context Check for AI Jabs
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        boolean isOpen = hour >= 10 && hour < 24;
        
        boolean contextChanged = !newBookingId.equals(lastUsedBookingId) || (isOpen != lastUsedShopStatusOpen);
        if (!isPoolInitialized || contextChanged) {
            lastUsedBookingId = newBookingId;
            lastUsedShopStatusOpen = isOpen;
            isPoolInitialized = true;
            updateAiJab(isOpen, true);
        } else if (jabPool.isEmpty()) {
            updateAiJab(isOpen, true);
        } else {
            showNextJabFromPool();
        }
    }

    private void animateBookingArrival() {
        if (llBookingDetailsRow == null || hasAnimatedBooking) {
            if (llBookingDetailsRow != null) llBookingDetailsRow.setVisibility(View.VISIBLE);
            return;
        }
        hasAnimatedBooking = true;

        // 1. Prepare elements for animation
        llBookingDetailsRow.setVisibility(View.VISIBLE);
        llBookingDetailsRow.setAlpha(0f);
        
        // Hide values and buttons initially
        final View[] labels = {tvLabelDate, tvLabelTime, tvLabelStylist, tvLabelChosenHaircut};
        final View[] values = {tvBookingDate, tvBookingTime, tvHairstylist, tvHaircut, ivHairPreview};
        final View[] buttons = {btnCallStylist, btnCancelBooking, btnEditBooking};

        for (View v : labels) if (v != null) { v.setAlpha(0f); v.setTranslationX(-50f); }
        for (View v : values) if (v != null) { v.setAlpha(0f); v.setTranslationX(-30f); }
        for (View v : buttons) if (v != null) { v.setAlpha(0f); v.setTranslationY(20f); }

        if (llRightCol != null) {
            llRightCol.setAlpha(0f);
            llRightCol.setTranslationX(100f);
        }

        // 2. Measure target height
        llBookingDetailsRow.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        final int targetHeight = llBookingDetailsRow.getMeasuredHeight();

        // 3. Animate Height and Alpha of the row (Slowed down to 1.5s)
        ValueAnimator heightAnimator = ValueAnimator.ofInt(0, targetHeight);
        heightAnimator.setDuration(1500); 
        heightAnimator.setInterpolator(new android.view.animation.BounceInterpolator());
        heightAnimator.addUpdateListener(animation -> {
            int val = (Integer) animation.getAnimatedValue();
            ViewGroup.LayoutParams layoutParams = llBookingDetailsRow.getLayoutParams();
            layoutParams.height = val;
            llBookingDetailsRow.setLayoutParams(layoutParams);
            llBookingDetailsRow.setAlpha((float) val / targetHeight);
        });

        // 4. Staggered animation for elements
        heightAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                ViewGroup.LayoutParams layoutParams = llBookingDetailsRow.getLayoutParams();
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                llBookingDetailsRow.setLayoutParams(layoutParams);

                // A. Animate Labels (Starts immediately after row expansion begins or near end)
                for (View v : labels) {
                    if (v != null) v.animate().alpha(1f).translationX(0f).setDuration(800).start();
                }

                // B. Animate Values (0.8s later)
                handler.postDelayed(() -> {
                    for (View v : values) {
                        if (v != null) v.animate().alpha(1f).translationX(0f).setDuration(800).start();
                    }
                    if (llRightCol != null) {
                        llRightCol.animate().alpha(1f).translationX(0f).setDuration(1000).start();
                    }
                }, 800);

                // C. Animate Buttons (Last)
                handler.postDelayed(() -> {
                    for (View v : buttons) {
                        if (v != null) v.animate().alpha(1f).translationY(0f).setDuration(800).start();
                    }
                }, 1400);
            }
        });

        heightAnimator.start();
    }

    private void loadHaircutImage(String name) {
        if (name == null || ivHairPreview == null) return;
        
        try {
            String[] images = requireContext().getAssets().list("images");
            if (images != null) {
                String cleanName = name.toLowerCase().replace(" ", "").replace("-", "");
                for (String imageName : images) {
                    String cleanImg = imageName.toLowerCase().split("\\.")[0].replace(" ", "").replace("-", "");
                    if (cleanName.contains(cleanImg) || cleanImg.contains(cleanName)) {
                        try (java.io.InputStream is = requireContext().getAssets().open("images/" + imageName)) {
                            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
                            ivHairPreview.setImageBitmap(bitmap);
                            return;
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        ivHairPreview.setImageResource(R.drawable.ic_hair);
    }

    private void resetBookingUI() {
        nearestBookingTime = null;
        nearestBookingHaircut = null;
        
        // Handle visibility
        if (llBookingDetailsRow != null) llBookingDetailsRow.setVisibility(View.GONE);
        if (tvNoBookingPlaceholder != null) tvNoBookingPlaceholder.setVisibility(View.VISIBLE);
        hasAnimatedBooking = false; // Allow re-animation if they book again later
        
        // Context tracking for "no booking"
        String newBookingId = "no_booking";
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        boolean isOpen = hour >= 10 && hour < 24;

        boolean contextChanged = !newBookingId.equals(lastUsedBookingId) || (isOpen != lastUsedShopStatusOpen);
        if (!isPoolInitialized || contextChanged) {
            lastUsedBookingId = newBookingId;
            lastUsedShopStatusOpen = isOpen;
            isPoolInitialized = true;
            updateAiJab(isOpen, true);
        } else if (jabPool.isEmpty()) {
            updateAiJab(isOpen, true);
        } else {
            showNextJabFromPool();
        }

        if (tvBookingDate != null) tvBookingDate.setText("No active booking");
        if (tvBookingTime != null) tvBookingTime.setText("--:--");
        if (tvHairstylist != null) tvHairstylist.setText("N/A");
        if (tvHaircut != null) tvHaircut.setText("Select a style");
        if (ivHairPreview != null) ivHairPreview.setImageResource(R.drawable.ic_hair);
    }

    /**
     * Populates the welcome message with the user's username from encrypted preferences.
     */
    private void populateWelcomeMessage(View view) {
        TextView tvWelcome = view.findViewById(R.id.tv_welcome_home);
        if (tvWelcome == null) return;

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

            String username = prefs.getString("username", "User");
            String welcomeTemplate = getString(R.string.welcome_user);
            tvWelcome.setText(welcomeTemplate.replace("(User)", username));
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the scroll listener to dynamically resize the top bar and hide/show the AI jab.
     */
    private void setupScrollingLogic() {
        svHomeContent.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (isAnimating || !isAdded()) return;

            int scrollY = svHomeContent.getScrollY();
            View child = svHomeContent.getChildAt(0);
            if (child == null) return;
            
            int maxScroll = child.getHeight() - svHomeContent.getHeight();
            int threshold = maxScroll / 2; // Threshold for full collapse

            if (threshold <= 0) return;

            float ratio = (float) scrollY / threshold;
            if (ratio > 1f) ratio = 1f;

            int startHeight = dpToPx(100);
            int endHeight = dpToPx(70);
            
            if (!isLockedAtMin) {
                // Interpolate height between 100dp and 70dp
                int currentHeight = (int) (startHeight - (startHeight - endHeight) * ratio);

                if (currentHeight <= endHeight) {
                    currentHeight = endHeight;
                    isLockedAtMin = true;
                }

                ViewGroup.LayoutParams params = mcvTopBar.getLayoutParams();
                params.height = currentHeight;
                mcvTopBar.setLayoutParams(params);

                // Gradually hide AI jab based on scroll progress
                float jabAlpha = 1f - ratio;
                tvAiJab.setAlpha(jabAlpha);
                if (jabAlpha < 0.1f) {
                    tvAiJab.setVisibility(View.GONE);
                    isExpanded = false;
                } else {
                    tvAiJab.setVisibility(View.VISIBLE);
                    isExpanded = true;
                }
            } else {
                // Maintain collapsed state even when scrolling beyond the threshold
                ViewGroup.LayoutParams params = mcvTopBar.getLayoutParams();
                if (params.height != endHeight) {
                    params.height = endHeight;
                    mcvTopBar.setLayoutParams(params);
                    tvAiJab.setVisibility(View.GONE);
                    isExpanded = false;
                }
            }
        });
    }

    /**
     * Handles clicks on the top bar to manually show the next jab from the pool.
     */
    private void setupClickLogic() {
        mcvTopBar.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastJabTime < JAB_COOLDOWN) {
                // Show cooldown message if clicked too frequently
                long timeLeft = (JAB_COOLDOWN - (currentTime - lastJabTime)) / 1000;
                String message = getString(R.string.jab_cooldown, (int) timeLeft);
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            } else {
                lastJabTime = currentTime;
                showNextJabFromPool();
            }

            // Expand top bar if it's currently collapsed
            if (!isExpanded && !isAnimating) {
                isLockedAtMin = false;
                animateTopBar(dpToPx(70), dpToPx(100), 500);

                // Scroll back to top for better visibility of expanded content
                if (svHomeContent != null) {
                    svHomeContent.smoothScrollTo(0, 0);
                }
            }
        });
    }

    /**
     * Animates the height change of the top bar.
     */
    private void animateTopBar(int from, int to, int duration) {
        isAnimating = true;
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(duration);
        animator.addUpdateListener(animation -> {
            int val = (Integer) animation.getAnimatedValue();
            ViewGroup.LayoutParams params = mcvTopBar.getLayoutParams();
            params.height = val;
            mcvTopBar.setLayoutParams(params);

            float progress = (float) (val - from) / (to - from);
            tvAiJab.setVisibility(View.VISIBLE);
            tvAiJab.setAlpha(progress);
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                isAnimating = false;
                isExpanded = true;
            }
        });
        animator.start();
    }

    /**
     * Converts DP units to pixels based on the device's screen density.
     */
    private int dpToPx(int dp) {
        if (!isAdded()) return dp * 3; // Approximate fallback for detached state
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    /**
     * Fetches or updates the AI jab pool (roast).
     * Uses Gemini AI with personalized booking context to generate 100 jabs.
     */
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
        promptBuilder.append("Generate EXACTLY 100 funny, unique, very short (max 20 words each) jabs/roasts for a user at a barber shop app. ");
        promptBuilder.append("Separate each jab with a '/' character. DO NOT NUMBER THEM. No list formatting. ");
        promptBuilder.append("Context: The shop is currently ").append(shopStatus).append(". ");
        
        if (nearestBookingTime != null && nearestBookingHaircut != null) {
            promptBuilder.append("The user has an upcoming appointment for a '")
                    .append(nearestBookingHaircut).append("' at ").append(nearestBookingTime).append(". ");
            promptBuilder.append("Make them personal about this specific haircut choice or timing. ");
        } else {
            promptBuilder.append("The user hasn't booked anything yet. Roast them for their potentially messy hair and encourage them to book NOW. ");
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
                android.util.Log.e("HomeFragment", "Gemini API Error: " + t.getMessage(), t);
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

        // Pick a random jab from the pool and remove it so we don't repeat immediately
        int index = new Random().nextInt(jabPool.size());
        String jab = jabPool.get(index);
        jabPool.remove(index);
        saveJabPool();
        
        startTypingAnimation(jab);
    }
    
    /**
     * Starts the typewriter effect for displaying the jab text.
     */
    private void startTypingAnimation(final String text) {
        if (handler == null || tvAiJab == null) return;

        if (typingRunnable != null) {
            handler.removeCallbacks(typingRunnable);
        }

        final String oldText = tvAiJab.getText().toString();
        // If there's already text, backspace it first
        if (!oldText.isEmpty() && !oldText.equals(text)) {
            startBackspaceAnimation(oldText, text);
            return;
        }

        final int typeDelay = 50; // Delay per character in milliseconds

        typingRunnable = new Runnable() {
            int index = 0;
            boolean cursorVisible = true;

            @Override
            public void run() {
                if (index <= text.length()) {
                    String displayed = text.substring(0, index) + (cursorVisible ? " |" : "  ");
                    tvAiJab.setText(displayed);
                    index++;
                    cursorVisible = !cursorVisible;
                    handler.postDelayed(this, typeDelay);
                } else {
                    tvAiJab.setText(text); // Final text display
                }
            }
        };
        handler.post(typingRunnable);
    }

    /**
     * Backspace effect before typing a new message.
     */
    private void startBackspaceAnimation(final String oldText, final String newText) {
        final int totalDuration = 1000;
        final int charCount = oldText.length();
        final int delay = charCount > 0 ? totalDuration / charCount : 0;

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

    /**
     * Selects a random jab from local resources based on the shop's open/closed status.
     */
    private void useFallbackJab(boolean isOpen) {
        String[] fallbackJabs;
        if (isOpen) {
            fallbackJabs = new String[]{
                    getString(R.string.jab_open_1),
                    getString(R.string.jab_open_2)
            };
        } else {
            fallbackJabs = new String[]{
                    getString(R.string.jab_closed_1),
                    getString(R.string.jab_closed_2)
            };
        }
        String jab = fallbackJabs[new Random().nextInt(fallbackJabs.length)];
        startTypingAnimation(jab);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (handler != null) {
            handler.post(updateTimeThemeRunnable);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (handler != null) {
            handler.removeCallbacks(updateTimeThemeRunnable);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (bookingListener != null) {
            bookingListener.remove();
        }
    }

    /**
     * Updates UI elements based on the current time of day (Theme, background image, greeting).
     */
    private void updateTimeTheme() {
        View view = getView();
        if (view == null || !isAdded()) return;

        ImageView ivBg = view.findViewById(R.id.iv_time_bg);
        TextView tvTimeName = view.findViewById(R.id.tv_time_name);
        TextView tvAmPm = view.findViewById(R.id.tv_ampm_home);
        TextView tvShopStatus = view.findViewById(R.id.tv_shop_status);

        if (ivBg == null || tvTimeName == null) return;

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        // Update AM/PM display
        if (tvAmPm != null) {
            String amPm = calendar.get(Calendar.AM_PM) == Calendar.AM ? 
                    getString(R.string.am_label) : getString(R.string.pm_label);
            tvAmPm.setText(amPm);
        }

        // Update Shop Open/Closed status visual
        if (tvShopStatus != null) {
            boolean isOpen = hour >= 10 && hour < 24;
            String status = isOpen ? getString(R.string.shop_status_open) : getString(R.string.shop_status_closed);
            int color = isOpen ? Color.parseColor("#05B109") : Color.RED;

            SpannableStringBuilder builder = new SpannableStringBuilder(getString(R.string.shop_status_prefix));
            builder.append(" "); // Explicitly add a space to prevent trimming issues
            int start = builder.length();
            builder.append(status);
            builder.setSpan(new ForegroundColorSpan(color), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvShopStatus.setText(builder);
        }

        String timeName;
        int bgResId;

        // Determine theme based on hour of the day
        if (hour < 6) {
            timeName = getString(R.string.time_midnight);
            bgResId = R.drawable.bg_time_midnight;
        } else if (hour < 8) {
            timeName = getString(R.string.time_sunrise);
            bgResId = R.drawable.bg_time_sunrise;
        } else if (hour < 12) {
            timeName = getString(R.string.time_morning);
            bgResId = R.drawable.bg_time_morning;
        } else if (hour < 14) {
            timeName = getString(R.string.time_noon);
            bgResId = R.drawable.bg_time_noon;
        } else if (hour < 18) {
            timeName = getString(R.string.time_evening);
            bgResId = R.drawable.bg_time_evening;
        } else if (hour < 20) {
            timeName = getString(R.string.time_sunset);
            bgResId = R.drawable.bg_time_sunset;
        } else {
            timeName = getString(R.string.time_night);
            bgResId = R.drawable.bg_time_night;
        }

        tvTimeName.setText(timeName);
        ivBg.setImageResource(bgResId);
    }
}
