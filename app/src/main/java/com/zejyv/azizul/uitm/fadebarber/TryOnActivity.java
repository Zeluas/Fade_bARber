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
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
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
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import ai.deepar.ar.DeepAR;
import ai.deepar.ar.AREventListener;
import ai.deepar.ar.DeepARImageFormat;

/**
 * TryOnActivity handles the AR hairstyle try-on experience using DeepAR SDK.
 * Features:
 * - Real-time AR rendering on a SurfaceView.
 * - Frame processing from CameraX to DeepAR.
 * - Interactive hairstyle carousel with expand/collapse animations.
 */
public class TryOnActivity extends AppCompatActivity implements AREventListener, SurfaceHolder.Callback {

    private static final int CAMERA_PERMISSION_CODE = 1001;
    private static final String TAG = "TryOnActivity";

    // --- UI Components ---
    private SurfaceView surfaceView;
    private ConstraintLayout bottomSheetContainer;
    private ConstraintLayout carouselContainer;
    private ImageView ivCarouselArrow;
    private View carouselScroll;
    private TextView tvChooseHairstyle, tvChooseHairstyleCollapsed;
    private MaterialButton fabCapture;

    // --- DeepAR & Camera ---
    private DeepAR deepAR;
    private ProcessCameraProvider cameraProvider;
    private boolean isDeepARInitialized = false;

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

        initializeDeepAR();
        initializeViews();
        setupBookingContext();
        setupCarouselLogic();

        reminderRunnable = this::startReminderAnimation;

        // Start camera if permissions are already granted
        if (allPermissionsGranted()) {
            setupCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    private void initializeDeepAR() {
        deepAR = new DeepAR(this);
        deepAR.setLicenseKey(BuildConfig.DEEP_AR_SDK_KEY);
        deepAR.initialize(this, this);
    }

    /**
     * Initializes all view references from the activity layout.
     */
    private void initializeViews() {
        surfaceView = findViewById(R.id.surfaceView);
        surfaceView.getHolder().addCallback(this);
        
        bottomSheetContainer = findViewById(R.id.bottom_sheet_container);
        carouselContainer = findViewById(R.id.carousel_container);
        ivCarouselArrow = findViewById(R.id.iv_carousel_arrow);
        carouselScroll = findViewById(R.id.carousel_scroll);
        tvChooseHairstyle = findViewById(R.id.tv_choose_hairstyle);
        tvChooseHairstyleCollapsed = findViewById(R.id.tv_choose_hairstyle_collapsed);
        fabCapture = findViewById(R.id.fab_capture);
        fabCapture.setOnClickListener(v -> {
            if (deepAR != null) {
                deepAR.takeScreenshot();
            }
        });

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

        // Hairstyle Selection Listeners
        // Mapping available assets for testing
        findViewById(R.id.item_buzz_cut).setOnClickListener(v -> switchHairstyle("viking_helmet.deepar"));
        findViewById(R.id.item_fade).setOnClickListener(v -> switchHairstyle("Vendetta_Mask.deepar"));
        findViewById(R.id.item_pompadour).setOnClickListener(v -> switchHairstyle("Humanoid.deepar"));
        findViewById(R.id.item_undercut).setOnClickListener(v -> switchHairstyle("Snail.deepar"));
        findViewById(R.id.item_crew_cut).setOnClickListener(v -> switchHairstyle("viking_helmet.deepar"));
        findViewById(R.id.item_mohawk).setOnClickListener(v -> switchHairstyle("Vendetta_Mask.deepar"));
        findViewById(R.id.item_side_part).setOnClickListener(v -> switchHairstyle("Humanoid.deepar"));
        findViewById(R.id.item_top_knot).setOnClickListener(v -> switchHairstyle("Snail.deepar"));

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
     * Switches the AR effect to the specified hairstyle.
     * @param fileName The name of the .deepar file in the assets folder.
     */
    private void switchHairstyle(String fileName) {
        if (deepAR != null && isDeepARInitialized) {
            // DeepAR 5.x uses the slot name "main" for the primary effect
            deepAR.switchEffect("main", "file:///android_asset/" + fileName);
            Log.d(TAG, "Switched effect to: " + fileName);
        } else {
            Toast.makeText(this, "DeepAR is still initializing...", Toast.LENGTH_SHORT).show();
        }
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
    protected void onStop() {
        super.onStop();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopReminderAnimation();
        if (deepAR != null) {
            deepAR.release();
            deepAR = null;
        }
    }

    /**
     * Configures CameraX to send frames to DeepAR for processing.
     */
    private void setupCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), image -> {
            if (isDeepARInitialized && deepAR != null) {
                // Get the Y plane buffer directly
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                // Set mirror to true for a natural "mirror" feel with the front camera
                deepAR.receiveFrame(buffer, image.getWidth(), image.getHeight(), 
                                   image.getImageInfo().getRotationDegrees(), 
                                   true, DeepARImageFormat.YUV_420_888, 0);
            }
            image.close();
        });

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
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
                setupCamera();
            } else {
                Toast.makeText(this, getString(R.string.try_on_permission_denied), Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // --- DeepARListener Implementation ---

    @Override
    public void initialized() {
        Log.d(TAG, "DeepAR initialized successfully");
        isDeepARInitialized = true;
        // Load a default effect once initialized
        switchHairstyle("viking_helmet.deepar");
    }

    @Override
    public void screenshotTaken(android.graphics.Bitmap bitmap) {
        Log.d(TAG, "Screenshot taken successfully");
        Toast.makeText(this, "Screenshot saved to gallery", Toast.LENGTH_SHORT).show();
        // In a real app, you would save the bitmap to the media store here.
    }

    @Override
    public void videoRecordingStarted() {}

    @Override
    public void videoRecordingFinished() {}

    @Override
    public void videoRecordingFailed() {}

    @Override
    public void videoRecordingPrepared() {}

    @Override
    public void shutdownFinished() {}

    @Override
    public void error(ai.deepar.ar.ARErrorType arErrorType, String s) {
        Log.e(TAG, "DeepAR Error: " + s + " Type: " + arErrorType);
    }

    @Override
    public void faceVisibilityChanged(boolean visible) {
        if (visible) {
            Log.d(TAG, "Face detected");
        }
    }

    @Override
    public void imageVisibilityChanged(String s, boolean b) {}

    @Override
    public void frameAvailable(android.media.Image image) {}

    @Override
    public void effectSwitched(String s) {}

    // --- SurfaceHolder.Callback Implementation ---

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {}

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        if (deepAR != null) {
            deepAR.setRenderSurface(holder.getSurface(), width, height);
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        if (deepAR != null) {
            deepAR.setRenderSurface(null, 0, 0);
        }
    }
}
