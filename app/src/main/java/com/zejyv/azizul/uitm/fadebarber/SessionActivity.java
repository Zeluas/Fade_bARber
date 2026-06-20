package com.zejyv.azizul.uitm.fadebarber;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SessionActivity extends AppCompatActivity {

    public static boolean isRunning = false;
    private View viewRadarPulse, clStartContainer, llTimerContainer;
    private View layoutEndConfirmation, mcvEndDialog;
    private MaterialButton btnStartMain, btnEndSession, btnPauseSession;
    private TextView tvSessionTimer, tvSessionTitle;

    // Payment Workflow Views
    private View llAmountInputContainer, llPaymentSelectionContainer, llWaitingContainer, llPaymentDetailsContainer;
    private View layoutCompletionConfirmation, mcvCompletionDialog;
    private View layoutRatingOverlay;
    private android.widget.RatingBar rbSessionRating;
    private com.google.android.material.textfield.TextInputEditText etRatingComment;
    private TextView tvRatingBarberName;
    private com.google.android.material.checkbox.MaterialCheckBox cbCompletionConfirm;
    private android.widget.EditText etPaymentAmount;
    private MaterialButton btnSubmitAmount, btnPaymentDone;
    private TextView tvPaymentAmountDisplay, tvCustomerPaymentMsg, tvPaymentMethodDisplay;
    private View ivPaymentQr;
    
    private AnimatorSet pulseAnimatorSet;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String bookingId;
    private String barberName = "the hairstylist";
    private String barberId = "";
    private boolean isEmployee = false;
    private ListenerRegistration bookingStatusListener;
    private ListenerRegistration paymentStatusListener;
    
    private long startTimeMillis = 0;
    private long pausedAtMillis = 0;
    private long totalPausedMillis = 0;
    private boolean isPaused = false;
    
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);
        
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        bookingId = getIntent().getStringExtra("BOOKING_ID");
        
        if (bookingId == null || mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Error: No booking data found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        setupAnimations();
        setupClickListeners();
        determineRoleAndCheckSession();
    }

    private void initializeViews() {
        viewRadarPulse = findViewById(R.id.view_radar_pulse);
        clStartContainer = findViewById(R.id.cl_start_container);
        llTimerContainer = findViewById(R.id.ll_timer_container);
        btnStartMain = findViewById(R.id.btn_start_main);
        btnEndSession = findViewById(R.id.btn_end_session);
        btnPauseSession = findViewById(R.id.btn_pause_session);
        tvSessionTimer = findViewById(R.id.tv_session_timer);
        tvSessionTitle = findViewById(R.id.tv_session_title);
        
        layoutEndConfirmation = findViewById(R.id.layout_end_confirmation);
        mcvEndDialog = findViewById(R.id.mcv_end_dialog);
        
        // Payment Views
        llAmountInputContainer = findViewById(R.id.ll_amount_input_container);
        llPaymentSelectionContainer = findViewById(R.id.ll_payment_selection_container);
        llWaitingContainer = findViewById(R.id.ll_waiting_container);
        llPaymentDetailsContainer = findViewById(R.id.ll_payment_details_container);
        layoutCompletionConfirmation = findViewById(R.id.layout_completion_confirmation);
        mcvCompletionDialog = findViewById(R.id.mcv_completion_dialog);
        cbCompletionConfirm = findViewById(R.id.cb_completion_confirm);
        etPaymentAmount = findViewById(R.id.et_payment_amount);
        btnSubmitAmount = findViewById(R.id.btn_submit_amount);
        btnPaymentDone = findViewById(R.id.btn_payment_done);
        tvPaymentAmountDisplay = findViewById(R.id.tv_payment_amount_display);
        tvPaymentMethodDisplay = findViewById(R.id.tv_payment_method_display);
        tvCustomerPaymentMsg = findViewById(R.id.tv_customer_payment_msg);
        ivPaymentQr = findViewById(R.id.iv_payment_qr);

        layoutRatingOverlay = findViewById(R.id.layout_rating_overlay);
        rbSessionRating = findViewById(R.id.rb_session_rating);
        etRatingComment = findViewById(R.id.et_rating_comment);
        tvRatingBarberName = findViewById(R.id.tv_rating_barber_name);

        findViewById(R.id.btn_back_session).setOnClickListener(v -> finish());

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                View btnBack = findViewById(R.id.btn_back_session);
                if (btnBack != null && btnBack.getVisibility() == View.GONE) {
                    Toast.makeText(SessionActivity.this, "Session in progress. Cannot go back.", Toast.LENGTH_SHORT).show();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void setupAnimations() {
        // Button Pulse Animation
        ObjectAnimator btnScaleX = ObjectAnimator.ofFloat(btnStartMain, "scaleX", 1f, 1.15f);
        ObjectAnimator btnScaleY = ObjectAnimator.ofFloat(btnStartMain, "scaleY", 1f, 1.15f);

        btnScaleX.setRepeatCount(ObjectAnimator.INFINITE);
        btnScaleX.setRepeatMode(ObjectAnimator.REVERSE);
        btnScaleY.setRepeatCount(ObjectAnimator.INFINITE);
        btnScaleY.setRepeatMode(ObjectAnimator.REVERSE);

        pulseAnimatorSet = new AnimatorSet();
        pulseAnimatorSet.playTogether(btnScaleX, btnScaleY);
        pulseAnimatorSet.setDuration(1000);
        pulseAnimatorSet.setInterpolator(new AccelerateDecelerateInterpolator());

        // Radar Pulse sync
        btnScaleX.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            // Trigger radar when button reaches peak (approx 1.15f)
            if (value >= 1.14f && viewRadarPulse.getAlpha() <= 0.1f) {
                triggerRadarPulse();
            }
        });

        pulseAnimatorSet.start();
    }

    private void triggerRadarPulse() {
        viewRadarPulse.setScaleX(1f);
        viewRadarPulse.setScaleY(1f);
        viewRadarPulse.setAlpha(0.8f);

        ObjectAnimator radarX = ObjectAnimator.ofFloat(viewRadarPulse, "scaleX", 1f, 1.3f);
        ObjectAnimator radarY = ObjectAnimator.ofFloat(viewRadarPulse, "scaleY", 1f, 1.3f);
        ObjectAnimator radarAlpha = ObjectAnimator.ofFloat(viewRadarPulse, "alpha", 0.8f, 0f);

        AnimatorSet radarSet = new AnimatorSet();
        radarSet.playTogether(radarX, radarY, radarAlpha);
        radarSet.setDuration(800);
        radarSet.start();
    }

    private void setupClickListeners() {
        btnStartMain.setOnClickListener(v -> startSession());
        btnEndSession.setOnClickListener(v -> showEndConfirmation());
        btnPauseSession.setOnClickListener(v -> togglePause());
        
        layoutEndConfirmation.setOnClickListener(v -> hideEndConfirmation());
        mcvEndDialog.setOnClickListener(v -> {}); // Prevent closing when clicking dialog itself
        
        findViewById(R.id.btn_end_confirm).setOnClickListener(v -> endSession());

        // Payment Workflow Listeners
        btnSubmitAmount.setOnClickListener(v -> submitAmount());
        btnPaymentDone.setOnClickListener(v -> showFinalCompletionConfirmation());
        
        findViewById(R.id.mcv_pay_cash).setOnClickListener(v -> selectPaymentMethod("Cash"));
        findViewById(R.id.mcv_pay_qr).setOnClickListener(v -> selectPaymentMethod("QR Code"));
        
        findViewById(R.id.btn_completion_cancel).setOnClickListener(v -> hideFinalCompletionConfirmation());
        findViewById(R.id.btn_completion_confirm).setOnClickListener(v -> completeBooking());

        findViewById(R.id.btn_submit_rating).setOnClickListener(v -> submitRating());
    }

    private void determineRoleAndCheckSession() {
        db.collection("bookings").document(bookingId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String currentUid = mAuth.getCurrentUser().getUid();
                barberId = doc.getString("employeeId");
                isEmployee = currentUid.equals(barberId);
                
                if (barberId != null) {
                    db.collection("employees").document(barberId).get().addOnSuccessListener(empDoc -> {
                        if (empDoc.exists()) {
                            barberName = empDoc.getString("fullname");
                            if (tvRatingBarberName != null && !isFinishing()) {
                                tvRatingBarberName.setText("With " + barberName);
                            }
                        }
                    });
                }

                startStatusListener();
                startPaymentListener();
                checkOngoingSession();
            }
        });
    }

    private void startPaymentListener() {
        paymentStatusListener = db.collection("session_payments").document(bookingId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null) return;
                    
                    // Trigger workflow UI update on payment data change
                    db.collection("bookings").document(bookingId).get().addOnSuccessListener(bookingDoc -> {
                        if (bookingDoc.exists()) {
                            updateWorkflowUi(bookingDoc.getString("status"));
                        }
                    });
                });
    }

    private void startStatusListener() {
        bookingStatusListener = db.collection("bookings").document(bookingId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;
                    
                    String status = doc.getString("status");
                    updateWorkflowUi(status);
                });
    }

    private void updateTitlePosition(boolean atStart) {
        androidx.constraintlayout.widget.ConstraintLayout root = (androidx.constraintlayout.widget.ConstraintLayout) tvSessionTitle.getParent();
        androidx.constraintlayout.widget.ConstraintSet set = new androidx.constraintlayout.widget.ConstraintSet();
        set.clone(root);
        if (atStart) {
            set.connect(R.id.tv_session_title, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START, dpToPx(24));
            set.clear(R.id.tv_session_title, androidx.constraintlayout.widget.ConstraintSet.END);
        } else {
            set.connect(R.id.tv_session_title, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START, 0);
            set.connect(R.id.tv_session_title, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END, 0);
        }
        set.applyTo(root);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void updateWorkflowUi(String status) {
        // Hide all major workflow containers first
        View[] containers = {clStartContainer, llTimerContainer, llAmountInputContainer, 
                            llPaymentSelectionContainer, llWaitingContainer, llPaymentDetailsContainer,
                            layoutRatingOverlay};
        for (View v : containers) v.setVisibility(View.GONE);

        // Manage Back Button visibility
        View btnBack = findViewById(R.id.btn_back_session);
        if (btnBack != null) {
            if (!isEmployee && ("Starting".equals(status) || "Paying".equals(status) || "Rating".equals(status))) {
                btnBack.setVisibility(View.GONE);
                updateTitlePosition(true);
            } else {
                btnBack.setVisibility(View.VISIBLE);
                updateTitlePosition(false);
            }
        }

        if ("Starting".equals(status)) {
            tvSessionTitle.setText("Session in Progress");
            showTimerUi(false);
            clStartContainer.setVisibility(View.GONE);
        } else if ("Paying".equals(status)) {
            pulseAnimatorSet.cancel();
            tvSessionTitle.setText("Payment & Settlement");
            
            db.collection("session_payments").document(bookingId).get().addOnSuccessListener(payDoc -> {
                Double amount = payDoc.getDouble("paymentAmount");
                String method = payDoc.getString("paymentMethod");
                
                if (isEmployee) {
                    if (amount == null) {
                        fadeInLayout(llAmountInputContainer);
                    } else if (method == null) {
                        fadeInLayout(llWaitingContainer);
                    } else {
                        showPaymentDetails(amount, method);
                    }
                } else {
                    if (method == null) {
                        fadeInLayout(llPaymentSelectionContainer);
                    } else {
                        showPaymentDetails(amount, method);
                    }
                }
            });
        } else if ("Rating".equals(status)) {
            if (isEmployee) {
                Toast.makeText(this, "Booking Completed. Waiting for rating.", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                tvSessionTitle.setText("Rate Experience");
                fadeInLayout(layoutRatingOverlay);
            }
        } else if ("Completed".equals(status)) {
            Toast.makeText(this, "Thank you for visiting!", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            // Probably Pending
            tvSessionTitle.setText("Session Management");
            clStartContainer.setVisibility(View.VISIBLE);
            if (!pulseAnimatorSet.isRunning()) pulseAnimatorSet.start();
        }
    }

    private void fadeInLayout(View view) {
        if (view == null || view.getVisibility() == View.VISIBLE) return;
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0f);
        view.setTranslationY(30f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private void showPaymentDetails(Double amount, String method) {
        fadeInLayout(llPaymentDetailsContainer);
        tvPaymentAmountDisplay.setText(String.format(Locale.getDefault(), "RM %.2f", amount));
        
        if (isEmployee) {
            btnPaymentDone.setVisibility(View.VISIBLE);
            tvCustomerPaymentMsg.setVisibility(View.GONE);
            tvPaymentMethodDisplay.setVisibility(View.VISIBLE);
            tvPaymentMethodDisplay.setText("Method: " + method);
            if ("QR Code".equals(method)) {
                ivPaymentQr.setVisibility(View.VISIBLE);
            } else {
                ivPaymentQr.setVisibility(View.GONE);
            }
        } else {
            btnPaymentDone.setVisibility(View.GONE);
            tvCustomerPaymentMsg.setVisibility(View.VISIBLE);
            tvPaymentMethodDisplay.setVisibility(View.GONE);
            tvCustomerPaymentMsg.setText("Proceed to pay to the hairstylist via " + method);
            if ("QR Code".equals(method)) {
                ivPaymentQr.setVisibility(View.VISIBLE);
            } else {
                ivPaymentQr.setVisibility(View.GONE);
            }
        }
    }

    private void submitAmount() {
        String amountStr = etPaymentAmount.getText().toString();
        if (amountStr.isEmpty()) {
            etPaymentAmount.setError("Required");
            return;
        }

        // Hide Keyboard
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
        
        double amount = Double.parseDouble(amountStr);
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("bookingId", bookingId);
        paymentData.put("paymentId", db.collection("session_payments").document().getId());
        paymentData.put("paymentAmount", amount);
        paymentData.put("timestamp", FieldValue.serverTimestamp());

        db.collection("session_payments").document(bookingId)
                .set(paymentData, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to set amount", Toast.LENGTH_SHORT).show());
    }

    private void selectPaymentMethod(String method) {
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("bookingId", bookingId);
        paymentData.put("paymentId", bookingId);
        paymentData.put("paymentMethod", method);

        db.collection("session_payments").document(bookingId)
                .set(paymentData, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to select method", Toast.LENGTH_SHORT).show());
    }

    private void showFinalCompletionConfirmation() {
        layoutCompletionConfirmation.setVisibility(View.VISIBLE);
        layoutCompletionConfirmation.setAlpha(0f);
        layoutCompletionConfirmation.animate().alpha(1f).setDuration(200).start();
        mcvCompletionDialog.setTranslationY(500f);
        mcvCompletionDialog.animate().translationY(0f).setDuration(300).setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    private void hideFinalCompletionConfirmation() {
        mcvCompletionDialog.animate().translationY(500f).setDuration(300).start();
        layoutCompletionConfirmation.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutCompletionConfirmation.setVisibility(View.GONE)).start();
    }

    private void completeBooking() {
        if (!cbCompletionConfirm.isChecked()) {
            Toast.makeText(this, "Please confirm payment received first", Toast.LENGTH_SHORT).show();
            return;
        }
        db.collection("bookings").document(bookingId).update("status", "Rating")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Booking marked as finished", Toast.LENGTH_SHORT).show();
                    if (isEmployee) finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to finish booking", Toast.LENGTH_SHORT).show());
    }

    private void submitRating() {
        float rating = rbSessionRating.getRating();
        if (rating == 0) {
            Toast.makeText(this, "Please select a rating star", Toast.LENGTH_SHORT).show();
            return;
        }

        String comment = etRatingComment.getText().toString();
        Map<String, Object> ratingData = new HashMap<>();
        ratingData.put("ratingId", db.collection("hairstylist_ratings").document().getId());
        ratingData.put("bookingId", bookingId);
        ratingData.put("customerId", mAuth.getCurrentUser().getUid());
        ratingData.put("employeeId", barberId);
        ratingData.put("rating", rating);
        ratingData.put("comment", comment);
        ratingData.put("timestamp", FieldValue.serverTimestamp());

        db.collection("hairstylist_ratings").document(bookingId).set(ratingData)
                .addOnSuccessListener(aVoid -> {
                    db.collection("bookings").document(bookingId).update("status", "Completed")
                            .addOnSuccessListener(v -> {
                                Toast.makeText(this, "Thank you for your feedback!", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to submit rating", Toast.LENGTH_SHORT).show());
    }

    private void checkOngoingSession() {
        db.collection("session_timers").document(bookingId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Timestamp ts = documentSnapshot.getTimestamp("startTime");
                        isPaused = documentSnapshot.getBoolean("isPaused") != null && documentSnapshot.getBoolean("isPaused");
                        totalPausedMillis = documentSnapshot.getLong("totalPausedMillis") != null ? documentSnapshot.getLong("totalPausedMillis") : 0;
                        
                        if (isPaused) {
                            Timestamp pts = documentSnapshot.getTimestamp("pausedAt");
                            pausedAtMillis = pts != null ? pts.toDate().getTime() : System.currentTimeMillis();
                            btnPauseSession.setText("Resume Session");
                            btnPauseSession.setIconResource(R.drawable.ic_play);
                        } else {
                            btnPauseSession.setText("Pause Session Timer");
                            btnPauseSession.setIconResource(R.drawable.ic_pause);
                        }

                        if (ts != null) {
                            startTimeMillis = ts.toDate().getTime();
                            if (!isPaused) {
                                startTimer();
                            }
                        }
                    }
                });
    }

    private void startSession() {
        btnStartMain.setEnabled(false);
        
        Map<String, Object> timerData = new HashMap<>();
        timerData.put("bookingId", bookingId);
        timerData.put("sessionTimerId", db.collection("session_timers").document().getId());
        timerData.put("startTime", FieldValue.serverTimestamp());
        timerData.put("isPaused", false);
        timerData.put("totalPausedMillis", 0);
        
        db.collection("session_timers").document(bookingId).set(timerData)
                .addOnSuccessListener(aVoid -> {
                    db.collection("bookings").document(bookingId).update("status", "Starting")
                            .addOnSuccessListener(v -> {
                                db.collection("session_timers").document(bookingId).get()
                                        .addOnSuccessListener(documentSnapshot -> {
                                            Timestamp ts = documentSnapshot.getTimestamp("startTime");
                                            startTimeMillis = (ts != null) ? ts.toDate().getTime() : System.currentTimeMillis();
                                            transitionToTimer();
                                        });
                            });
                })
                .addOnFailureListener(e -> {
                    btnStartMain.setEnabled(true);
                    Toast.makeText(this, "Failed to start: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void togglePause() {
        isPaused = !isPaused;
        if (isPaused) {
            pausedAtMillis = System.currentTimeMillis();
            btnPauseSession.setText("Resume Session");
            btnPauseSession.setIconResource(R.drawable.ic_play);
            db.collection("session_timers").document(bookingId).update("isPaused", true, "pausedAt", FieldValue.serverTimestamp());
        } else {
            long pauseDuration = System.currentTimeMillis() - pausedAtMillis;
            totalPausedMillis += pauseDuration;
            btnPauseSession.setText("Pause Session Timer");
            btnPauseSession.setIconResource(R.drawable.ic_pause);
            db.collection("session_timers").document(bookingId).update("isPaused", false, "totalPausedMillis", totalPausedMillis);
        }
    }

    private void transitionToTimer() {
        tvSessionTitle.setText("Session in Progress");
        clStartContainer.animate()
                .alpha(0f)
                .scaleX(0.2f)
                .scaleY(0.2f)
                .setDuration(600)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        clStartContainer.setVisibility(View.GONE);
                        pulseAnimatorSet.cancel();
                        showTimerUi(true);
                    }
                }).start();
    }

    private void showTimerUi(boolean animate) {
        if (animate) {
            fadeInLayout(llTimerContainer);
        } else {
            llTimerContainer.setVisibility(View.VISIBLE);
            llTimerContainer.setAlpha(1f);
            llTimerContainer.setTranslationY(0f);
        }
        startTimer();
    }

    private void startTimer() {
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPaused) {
                    long now = System.currentTimeMillis();
                    long diff = now - startTimeMillis - totalPausedMillis;
                    if (diff < 0) diff = 0;
                    
                    int seconds = (int) (diff / 1000);
                    int minutes = seconds / 60;
                    int hours = minutes / 60;
                    seconds = seconds % 60;
                    minutes = minutes % 60;

                    tvSessionTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
                }
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void showEndConfirmation() {
        layoutEndConfirmation.setVisibility(View.VISIBLE);
        layoutEndConfirmation.setAlpha(0f);
        layoutEndConfirmation.animate().alpha(1f).setDuration(200).start();
        mcvEndDialog.setTranslationY(500f);
        mcvEndDialog.animate().translationY(0f).setDuration(300).setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    private void hideEndConfirmation() {
        mcvEndDialog.animate().translationY(500f).setDuration(300).start();
        layoutEndConfirmation.animate().alpha(0f).setDuration(200).withEndAction(() -> layoutEndConfirmation.setVisibility(View.GONE)).start();
    }

    private void endSession() {
        hideEndConfirmation();
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        long finalTotalPaused = totalPausedMillis;
        if (isPaused) {
            long pauseDuration = System.currentTimeMillis() - pausedAtMillis;
            finalTotalPaused += pauseDuration;
        }

        Map<String, Object> endData = new HashMap<>();
        endData.put("endTime", FieldValue.serverTimestamp());
        endData.put("totalPausedMillis", finalTotalPaused);
        endData.put("isPaused", false);

        db.collection("session_timers").document(bookingId)
                .update(endData)
                .addOnSuccessListener(aVoid -> {
                    db.collection("bookings").document(bookingId)
                            .update("status", "Paying")
                            .addOnSuccessListener(v -> {
                                Toast.makeText(this, "Session Ended. Proceeding to Payment.", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Error updating status", Toast.LENGTH_SHORT).show());
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        isRunning = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        isRunning = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pulseAnimatorSet != null) pulseAnimatorSet.cancel();
        if (timerHandler != null && timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        if (bookingStatusListener != null) bookingStatusListener.remove();
    }
}
