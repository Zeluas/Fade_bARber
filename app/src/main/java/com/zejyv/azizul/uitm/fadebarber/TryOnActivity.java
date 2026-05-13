package com.zejyv.azizul.uitm.fadebarber;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.BounceInterpolator;
import androidx.appcompat.widget.TooltipCompat;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

public class TryOnActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 1001;
    private PreviewView viewFinder;
    private ConstraintLayout bottomSheetContainer;
    private ConstraintLayout carouselContainer;
    private ImageView ivCarouselArrow;
    private View carouselScroll;
    private boolean isCarouselExpanded = true;
    private float initialY;
    private float dY;

    private TextView tvChooseHairstyle;
    private TextView tvChooseHairstyleCollapsed;

    private final Handler reminderHandler = new Handler(Looper.getMainLooper());
    private Runnable reminderRunnable;
    private AnimatorSet reminderAnimatorSet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Optimize for performance: Use a full-screen, hardware-accelerated window
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        setContentView(R.layout.activity_try_on);

        // System UI visibility handled by default or custom logic if needed
        
        viewFinder = findViewById(R.id.viewFinder);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        bottomSheetContainer = findViewById(R.id.bottom_sheet_container);
        carouselContainer = findViewById(R.id.carousel_container);
        ivCarouselArrow = findViewById(R.id.iv_carousel_arrow);
        carouselScroll = findViewById(R.id.carousel_scroll);
        tvChooseHairstyle = findViewById(R.id.tv_choose_hairstyle);
        tvChooseHairstyleCollapsed = findViewById(R.id.tv_choose_hairstyle_collapsed);

        // Change icon if opened from BookingActivity
        boolean fromBooking = getIntent().getBooleanExtra("FROM_BOOKING", false);
        if (fromBooking) {
            com.google.android.material.button.MaterialButton fabCapture = findViewById(R.id.fab_capture);
            fabCapture.setIconResource(R.drawable.ic_check_circle);
            fabCapture.setContentDescription("confirm hairstyle");
            TooltipCompat.setTooltipText(fabCapture, "Select Hairstyle");
            // Scale icon only by increasing iconSize (original is 32dp)
            int iconSizePx = (int) (32 * getResources().getDisplayMetrics().density * 1.5f);
            fabCapture.setIconSize(iconSizePx);
        }

        reminderRunnable = this::startReminderAnimation;

        setupCarousel();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    private void setupCarousel() {
        ivCarouselArrow.setOnClickListener(v -> toggleCarousel(!isCarouselExpanded));
        tvChooseHairstyleCollapsed.setOnClickListener(v -> toggleCarousel(!isCarouselExpanded));
        carouselContainer.setOnClickListener(v -> toggleCarousel(!isCarouselExpanded));

        View rootView = findViewById(android.R.id.content);
        rootView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    dY = bottomSheetContainer.getTranslationY() - event.getRawY();
                    initialY = event.getRawY();
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    float newTranslationY = event.getRawY() + dY;
                    float maxTranslationY = Math.max(0, carouselContainer.getHeight() - carouselScroll.getTop());
                    if (newTranslationY >= 0 && newTranslationY <= maxTranslationY) {
                        bottomSheetContainer.setTranslationY(newTranslationY);
                        float progress = maxTranslationY > 0 ? newTranslationY / maxTranslationY : 0;
                        ivCarouselArrow.setRotation(180 - (progress * 180));
                    }
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    float finalY = event.getRawY();
                    if (Math.abs(finalY - initialY) < 10) {
                        // Check if the tap was on the arrow specifically
                        int[] location = new int[2];
                        ivCarouselArrow.getLocationOnScreen(location);
                        if (event.getRawX() >= location[0] && event.getRawX() <= location[0] + ivCarouselArrow.getWidth() &&
                            event.getRawY() >= location[1] && event.getRawY() <= location[1] + ivCarouselArrow.getHeight()) {
                            ivCarouselArrow.performClick();
                        }
                    } else {
                        float maxTransY = Math.max(0, carouselContainer.getHeight() - carouselScroll.getTop());
                        toggleCarousel(!(bottomSheetContainer.getTranslationY() > maxTransY / 2));
                    }
                    return true;
            }
            return false;
        });
    }

    private void toggleCarousel(boolean expand) {
        // Calculate distance to hide everything but the arrow
        float maxTranslationY = Math.max(0, carouselContainer.getHeight() - carouselScroll.getTop());
        float targetTranslationY = expand ? 0 : maxTranslationY;
        float targetRotation = expand ? 180 : 0;

        bottomSheetContainer.animate()
                .translationY(targetTranslationY)
                .setDuration(300)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .start();

        ivCarouselArrow.animate()
                .rotation(targetRotation)
                .setDuration(300)
                .start();

        // Fade animations for the text views
        if (expand) {
            tvChooseHairstyle.animate().alpha(1.0f).setDuration(300).start();
            tvChooseHairstyleCollapsed.animate().alpha(0.0f).setDuration(300).start();
        } else {
            tvChooseHairstyle.animate().alpha(0.0f).setDuration(300).start();
            tvChooseHairstyleCollapsed.animate().alpha(1.0f).setDuration(300).start();
        }

        isCarouselExpanded = expand;

        stopReminderAnimation();
        if (!expand) {
            reminderHandler.postDelayed(reminderRunnable, 5000);
        }
    }

    private void startReminderAnimation() {
        if (isCarouselExpanded) return;

        if (reminderAnimatorSet != null) {
            reminderAnimatorSet.cancel();
        }

        // Bounce up by 50 pixels
        float bounceHeight = -50f;

        ObjectAnimator arrowBounce = ObjectAnimator.ofFloat(ivCarouselArrow, "translationY", 0f, bounceHeight, 0f);
        ObjectAnimator textBounce = ObjectAnimator.ofFloat(tvChooseHairstyleCollapsed, "translationY", 0f, bounceHeight, 0f);

        reminderAnimatorSet = new AnimatorSet();
        reminderAnimatorSet.playTogether(arrowBounce, textBounce);
        reminderAnimatorSet.setDuration(2000);
        reminderAnimatorSet.setInterpolator(new BounceInterpolator());

        reminderAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isCarouselExpanded) {
                    reminderHandler.postDelayed(reminderRunnable, 5000);
                }
            }
        });

        reminderAnimatorSet.start();
    }

    private void stopReminderAnimation() {
        reminderHandler.removeCallbacks(reminderRunnable);
        if (reminderAnimatorSet != null) {
            reminderAnimatorSet.cancel();
        }
        ivCarouselArrow.setTranslationY(0f);
        tvChooseHairstyleCollapsed.setTranslationY(0f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopReminderAnimation();
    }

    private void startCamera() {
        // High-priority camera preview
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview);

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Error starting camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
