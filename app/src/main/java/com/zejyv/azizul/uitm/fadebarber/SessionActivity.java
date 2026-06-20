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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SessionActivity extends AppCompatActivity {

    private View viewRadarPulse, clStartContainer, llTimerContainer;
    private View layoutEndConfirmation, mcvEndDialog;
    private MaterialButton btnStartMain, btnEndSession, btnPauseSession;
    private TextView tvSessionTimer;
    
    private AnimatorSet pulseAnimatorSet;
    
    private FirebaseFirestore db;
    private String bookingId;
    
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
        bookingId = getIntent().getStringExtra("BOOKING_ID");
        
        if (bookingId == null) {
            Toast.makeText(this, "Error: No booking data found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        setupAnimations();
        setupClickListeners();
        checkOngoingSession();
    }

    private void initializeViews() {
        viewRadarPulse = findViewById(R.id.view_radar_pulse);
        clStartContainer = findViewById(R.id.cl_start_container);
        llTimerContainer = findViewById(R.id.ll_timer_container);
        btnStartMain = findViewById(R.id.btn_start_main);
        btnEndSession = findViewById(R.id.btn_end_session);
        btnPauseSession = findViewById(R.id.btn_pause_session);
        tvSessionTimer = findViewById(R.id.tv_session_timer);
        
        layoutEndConfirmation = findViewById(R.id.layout_end_confirmation);
        mcvEndDialog = findViewById(R.id.mcv_end_dialog);
        
        findViewById(R.id.btn_back_session).setOnClickListener(v -> finish());
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
                            pulseAnimatorSet.cancel();
                            clStartContainer.setVisibility(View.GONE);
                            showTimerUi(false);
                        }
                    } else {
                        // Check booking status if timer doc doesn't exist yet
                        db.collection("bookings").document(bookingId).get().addOnSuccessListener(doc -> {
                           if (doc.exists() && "Starting".equals(doc.getString("status"))) {
                               // Recovery if timer doc was deleted but booking says starting
                           }
                        });
                    }
                });
    }

    private void startSession() {
        btnStartMain.setEnabled(false);
        
        Map<String, Object> timerData = new HashMap<>();
        timerData.put("bookingId", bookingId);
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
        llTimerContainer.setVisibility(View.VISIBLE);
        if (animate) {
            llTimerContainer.setAlpha(0f);
            llTimerContainer.setScaleX(0.5f);
            llTimerContainer.setScaleY(0.5f);
            llTimerContainer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600).start();
        } else {
            llTimerContainer.setAlpha(1f);
            llTimerContainer.setScaleX(1f);
            llTimerContainer.setScaleY(1f);
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
        btnEndConfirmAction();
    }

    private void btnEndConfirmAction() {
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
                            .update("status", "Completed")
                            .addOnSuccessListener(v -> {
                                Toast.makeText(this, "Session Completed", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Error updating booking status", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> {
                    // If update fails, still try to mark booking as completed
                    db.collection("bookings").document(bookingId).update("status", "Completed")
                            .addOnSuccessListener(v -> finish());
                    Toast.makeText(this, "Session Completed with minor sync error", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pulseAnimatorSet != null) pulseAnimatorSet.cancel();
        if (timerHandler != null && timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
    }
}
