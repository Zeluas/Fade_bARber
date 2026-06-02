package com.zejyv.azizul.uitm.fadebarber;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;

/**
 * TryOnActivity handles the AR hairstyle try-on experience.
 * Features:
 * - Real-time camera preview using CameraX.
 * - Interactive hairstyle carousel with expand/collapse animations.
 * - Auto-reminder bounce animation for the carousel.
 * - Optimized full-screen performance.
 */
public class TryOnActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 1001;

    // --- UI Components ---
    private PreviewView viewFinder;
    private ConstraintLayout bottomSheetContainer;
    private ConstraintLayout carouselContainer;
    private ImageView ivCarouselArrow;
    private View carouselScroll;
    private TextView tvChooseHairstyle, tvChooseHairstyleCollapsed;
    private MaterialButton fabCapture;

    // --- State & Animation Management ---
    private boolean isCarouselExpanded = true;
    private float initialY, dY;
    
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

        initializeViews();
        setupBookingContext();
        setupCarouselLogic();
        
        reminderRunnable = this::startReminderAnimation;

        // Start camera if permissions are already granted
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    /**
     * Initializes all view references from the activity layout.
     */
    private void initializeViews() {
        viewFinder = findViewById(R.id.viewFinder);
        bottomSheetContainer = findViewById(R.id.bottom_sheet_container);
        carouselContainer = findViewById(R.id.carousel_container);
        ivCarouselArrow = findViewById(R.id.iv_carousel_arrow);
        carouselScroll = findViewById(R.id.carousel_scroll);
        tvChooseHairstyle = findViewById(R.id.tv_choose_hairstyle);
        tvChooseHairstyleCollapsed = findViewById(R.id.tv_choose_hairstyle_collapsed);
        fabCapture = findViewById(R.id.fab_capture);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    /**
     * Adjusts the UI if the activity was launched from the Booking flow.
     */
    private void setupBookingContext() {
        boolean fromBooking = getIntent().getBooleanExtra("FROM_BOOKING", false);
        if (fromBooking && fabCapture != null) {
            fabCapture.setIconResource(R.drawable.ic_check_circle);
            fabCapture.setContentDescription(getString(R.string.try_on_confirm_hairstyle));
            TooltipCompat.setTooltipText(fabCapture, getString(R.string.try_on_select_hairstyle_tooltip));
            
            // Scale icon for better visibility in selection mode
            int iconSizePx = (int) (32 * getResources().getDisplayMetrics().density * 1.5f);
            fabCapture.setIconSize(iconSizePx);
        }
    }

    /**
     * Configures the interactive gesture and click logic for the hairstyle carousel.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void setupCarouselLogic() {
        // Toggle carousel on direct clicks
        ivCarouselArrow.setOnClickListener(v -> toggleCarousel(!isCarouselExpanded));
        tvChooseHairstyleCollapsed.setOnClickListener(v -> toggleCarousel(!isCarouselExpanded));
        carouselContainer.setOnClickListener(v -> toggleCarousel(!isCarouselExpanded));

        // Swipe-to-collapse/expand logic
        View rootView = findViewById(android.R.id.content);
        rootView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dY = bottomSheetContainer.getTranslationY() - event.getRawY();
                    initialY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float newTranslationY = event.getRawY() + dY;
                    float maxTranslationY = Math.max(0, carouselContainer.getHeight() - carouselScroll.getTop());
                    if (newTranslationY >= 0 && newTranslationY <= maxTranslationY) {
                        bottomSheetContainer.setTranslationY(newTranslationY);
                        // Rotate arrow dynamically based on drag progress
                        float progress = maxTranslationY > 0 ? newTranslationY / maxTranslationY : 0;
                        ivCarouselArrow.setRotation(180 - (progress * 180));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    float finalY = event.getRawY();
                    if (Math.abs(finalY - initialY) < 10) {
                        // Handle potential clicks on the arrow during touch events
                        int[] location = new int[2];
                        ivCarouselArrow.getLocationOnScreen(location);
                        if (event.getRawX() >= location[0] && event.getRawX() <= location[0] + ivCarouselArrow.getWidth() &&
                            event.getRawY() >= location[1] && event.getRawY() <= location[1] + ivCarouselArrow.getHeight()) {
                            ivCarouselArrow.performClick();
                        }
                    } else {
                        // Snap to state based on drag threshold
                        float maxTransY = Math.max(0, carouselContainer.getHeight() - carouselScroll.getTop());
                        toggleCarousel(!(bottomSheetContainer.getTranslationY() > maxTransY / 2));
                    }
                    return true;
            }
            return false;
        });
    }

    /**
     * Animates the expansion or collapse of the bottom carousel.
     * @param expand True to expand, false to collapse.
     */
    private void toggleCarousel(boolean expand) {
        float maxTranslationY = Math.max(0, carouselContainer.getHeight() - carouselScroll.getTop());
        float targetTranslationY = expand ? 0 : maxTranslationY;
        float targetRotation = expand ? 180 : 0;

        bottomSheetContainer.animate()
                .translationY(targetTranslationY)
                .setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        ivCarouselArrow.animate()
                .rotation(targetRotation)
                .setDuration(300)
                .start();

        // Fade transitions for the selection labels
        float activeAlpha = 1.0f;
        float inactiveAlpha = 0.0f;
        tvChooseHairstyle.animate().alpha(expand ? activeAlpha : inactiveAlpha).setDuration(300).start();
        tvChooseHairstyleCollapsed.animate().alpha(expand ? inactiveAlpha : activeAlpha).setDuration(300).start();

        isCarouselExpanded = expand;

        // Handle auto-reminder animation
        stopReminderAnimation();
        if (!expand) {
            reminderHandler.postDelayed(reminderRunnable, 5000);
        }
    }

    /**
     * Starts a subtle bounce animation to remind the user that the carousel can be expanded.
     */
    private void startReminderAnimation() {
        if (isCarouselExpanded) return;

        if (reminderAnimatorSet != null) {
            reminderAnimatorSet.cancel();
        }

        float bounceHeight = -50f; // Pixels to bounce up

        ObjectAnimator arrowBounce = ObjectAnimator.ofFloat(ivCarouselArrow, "translationY", 0f, bounceHeight, 0f);
        ObjectAnimator textBounce = ObjectAnimator.ofFloat(tvChooseHairstyleCollapsed, "translationY", 0f, bounceHeight, 0f);

        reminderAnimatorSet = new AnimatorSet();
        reminderAnimatorSet.playTogether(arrowBounce, textBounce);
        reminderAnimatorSet.setDuration(2000);
        reminderAnimatorSet.setInterpolator(new BounceInterpolator());

        reminderAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Loop the reminder if still collapsed
                if (!isCarouselExpanded) {
                    reminderHandler.postDelayed(reminderRunnable, 5000);
                }
            }
        });

        reminderAnimatorSet.start();
    }

    /**
     * Safely cancels and resets the reminder animation.
     */
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

    /**
     * Configures and starts the CameraX front-facing camera preview.
     */
    private void startCamera() {
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
                Toast.makeText(this, getString(R.string.try_on_camera_error, e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Checks if the required camera permissions are granted.
     */
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
                Toast.makeText(this, getString(R.string.try_on_permission_denied), Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}
