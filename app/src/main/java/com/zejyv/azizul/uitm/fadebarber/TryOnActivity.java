package com.zejyv.azizul.uitm.fadebarber;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
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

import androidx.activity.OnBackPressedCallback;
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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
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
    private View vFlash;
    private ImageView ivCapturedPreview;
    private View layoutPostCapture;
    private MaterialButton btnCancel, btnSave;
    private android.graphics.Bitmap capturedBitmap;

    // --- DeepAR & Camera ---
    private DeepAR deepAR;
    private ProcessCameraProvider cameraProvider;
    private boolean isDeepARInitialized = false;

    // --- State & Animation Management ---
    private boolean isCarouselExpanded = true;
    private float initialY, dY;
    private String currentHairstyleName = "Brazilian"; // Default
    private String currentHairstyleDesc = "";
    private String currentHairstyleKey = "brazilian";

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
        setupBackNavigation();

        reminderRunnable = this::startReminderAnimation;

        // Start camera if permissions are already granted
        if (allPermissionsGranted()) {
            setupCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    /**
     * Configures the system back button behavior.
     */
    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutPostCapture != null && layoutPostCapture.getVisibility() == View.VISIBLE) {
                    resetFromCapture();
                } else {
                    finish();
                }
            }
        });
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
        vFlash = findViewById(R.id.v_flash);
        ivCapturedPreview = findViewById(R.id.iv_captured_preview);
        layoutPostCapture = findViewById(R.id.layout_post_capture);
        btnCancel = findViewById(R.id.btn_cancel);
        btnSave = findViewById(R.id.btn_save);

        fabCapture.setOnClickListener(v -> performCapture());

        btnCancel.setOnClickListener(v -> resetFromCapture());
        btnSave.setOnClickListener(v -> saveCapturedImage());

        findViewById(R.id.btn_back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        loadCarouselPreviews();
    }

    /**
     * Dynamically loads hairstyle preview images from assets/images folder.
     * Each ImageView in the carousel is updated if a matching image exists.
     */
    private void loadCarouselPreviews() {
        try {
            String[] images = getAssets().list("images");
            if (images == null) return;

            java.util.Map<String, Integer> viewMap = new java.util.HashMap<>();
            viewMap.put("viking_helmet", R.id.v_icon_viking_helmet);
            viewMap.put("vendetta_mask", R.id.v_icon_vendetta_mask);
            viewMap.put("brazilian", R.id.v_icon_brazilian);
            viewMap.put("bird_hair", R.id.v_icon_bird_hair);
            viewMap.put("side_sweep", R.id.v_icon_side_sweep);
            viewMap.put("wolf_hair", R.id.v_icon_wolf_hair);
            viewMap.put("70s_afro", R.id.v_icon_70s_afro);
            viewMap.put("curtain", R.id.v_icon_curtain);
            viewMap.put("disconnect_undercut", R.id.v_icon_disconnect_undercut);
            viewMap.put("afro_tapper", R.id.v_icon_afro_tapper);
            viewMap.put("punk", R.id.v_icon_punk);

            for (String imageName : images) {
                for (String key : viewMap.keySet()) {
                    if (imageName.toLowerCase().startsWith(key)) {
                        View container = findViewById(viewMap.get(key));
                        if (container instanceof android.widget.FrameLayout) {
                            android.widget.ImageView iv = (android.widget.ImageView) ((android.widget.FrameLayout) container).getChildAt(1);
                            try (java.io.InputStream is = getAssets().open("images/" + imageName)) {
                                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
                                iv.setImageBitmap(bitmap);
                                iv.setImageTintList(null); // Remove white tint if a real image is loaded
                                iv.setPadding(0, 0, 0, 0); // Remove padding for full image display
                            }
                        }
                    }
                }
            }
        } catch (java.io.IOException e) {
            Log.e(TAG, "Error loading carousel previews: " + e.getMessage());
        }
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
        // Mapping available assets based on updated carousel items
        findViewById(R.id.item_brazilian).setOnClickListener(v -> switchHairstyle("brazilian.deepar", getString(R.string.style_brazilian), getString(R.string.desc_brazilian), "brazilian"));
        findViewById(R.id.item_bird_hair).setOnClickListener(v -> switchHairstyle("birdhair.deepar", getString(R.string.style_bird_hair), getString(R.string.desc_bird_hair), "bird_hair"));
        findViewById(R.id.item_side_sweep).setOnClickListener(v -> switchHairstyle("sidesweep.deepar", getString(R.string.style_side_sweep), getString(R.string.desc_side_sweep), "side_sweep"));
        findViewById(R.id.item_wolf_hair).setOnClickListener(v -> switchHairstyle("wolfhair.deepar", getString(R.string.style_wolf_hair), getString(R.string.desc_wolf_hair), "wolf_hair"));
        findViewById(R.id.item_70s_afro).setOnClickListener(v -> switchHairstyle("70s_afro_tapper.deepar", getString(R.string.style_70s_afro), getString(R.string.desc_70s_afro), "70s_afro"));
        findViewById(R.id.item_curtain).setOnClickListener(v -> switchHairstyle("curtain_haircut.deepar", getString(R.string.style_curtain), getString(R.string.desc_curtain), "curtain"));
        findViewById(R.id.item_disconnect_undercut).setOnClickListener(v -> switchHairstyle("disconnect_undercut.deepar", getString(R.string.style_disconnect_undercut), getString(R.string.desc_disconnect_undercut), "disconnect_undercut"));
        findViewById(R.id.item_afro_tapper).setOnClickListener(v -> switchHairstyle("afro_tapper.deepar", getString(R.string.style_afro_tapper), getString(R.string.desc_afro_tapper), "afro_tapper"));
        findViewById(R.id.item_punk).setOnClickListener(v -> switchHairstyle("punk.deepar", getString(R.string.style_punk), getString(R.string.desc_punk), "punk"));
        findViewById(R.id.item_viking_helmet).setOnClickListener(v -> switchHairstyle("viking_helmet.deepar", getString(R.string.style_viking_helmet), getString(R.string.desc_viking_helmet), "viking_helmet"));
        findViewById(R.id.item_vendetta_mask).setOnClickListener(v -> switchHairstyle("Vendetta_Mask.deepar", getString(R.string.style_vendetta_mask), getString(R.string.desc_vendetta_mask), "vendetta_mask"));

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
     * @param name The display name of the hairstyle.
     * @param desc The description of the hairstyle.
     * @param key The key to identify the hairstyle (for images).
     */
    private void switchHairstyle(String fileName, String name, String desc, String key) {
        if (deepAR != null && isDeepARInitialized) {
            // DeepAR 5.x uses the slot name "main" for the primary effect
            deepAR.switchEffect("main", "file:///android_asset/" + fileName);
            Log.d(TAG, "Switched effect to: " + fileName);
            
            // Update state
            this.currentHairstyleName = name;
            this.currentHairstyleDesc = desc;
            this.currentHairstyleKey = key;
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

    /**
     * Performs the capture sequence: flash animation, UI hiding, and triggering DeepAR screenshot.
     */
    private void performCapture() {
        if (deepAR == null) return;

        // 1. Grey Flash Animation (0.2s fade in, 0.2s fade out)
        vFlash.setAlpha(0f);
        vFlash.setVisibility(View.VISIBLE);
        vFlash.animate()
                .alpha(1f)
                .setDuration(200)
                .withEndAction(() -> {
                    // Hide all UI elements at the peak of the flash
                    setUIVisibility(View.GONE);
                    
                    // 2. Trigger screenshot
                    deepAR.takeScreenshot();

                    vFlash.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction(() -> vFlash.setVisibility(View.GONE))
                            .start();
                })
                .start();
    }

    /**
     * Toggles visibility for all primary UI elements.
     */
    private void setUIVisibility(int visibility) {
        View topBar = findViewById(R.id.view_top_bar_overlay);
        View backBtn = findViewById(R.id.btn_back);
        View title = findViewById(R.id.tv_try_on_title);
        
        if (topBar != null) topBar.setVisibility(visibility);
        if (backBtn != null) backBtn.setVisibility(visibility);
        if (title != null) title.setVisibility(visibility);
        
        if (bottomSheetContainer != null) bottomSheetContainer.setVisibility(visibility);
        if (fabCapture != null) fabCapture.setVisibility(visibility);
    }

    /**
     * Resets the activity state back to preview mode after a capture.
     */
    private void resetFromCapture() {
        ivCapturedPreview.setVisibility(View.GONE);
        layoutPostCapture.setVisibility(View.GONE);
        setUIVisibility(View.VISIBLE);
        capturedBitmap = null;
    }

    /**
     * Saves the captured bitmap to the standard Android Pictures folder,
     * or returns the selection if in booking mode.
     */
    private void saveCapturedImage() {
        if (capturedBitmap == null) {
            Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean fromBooking = getIntent().getBooleanExtra("FROM_BOOKING", false);
        if (fromBooking) {
            // Return selection to BookingActivity instead of saving to gallery
            android.content.Intent resultIntent = new android.content.Intent();
            resultIntent.putExtra("HAIRSTYLE_NAME", currentHairstyleName);
            resultIntent.putExtra("HAIRSTYLE_DESC", currentHairstyleDesc);
            resultIntent.putExtra("HAIRSTYLE_KEY", currentHairstyleKey);
            setResult(RESULT_OK, resultIntent);
            finish();
            return;
        }

        String appName = getString(R.string.app_name);
        // Using dots or dashes for date/time to avoid invalid filename characters
        String dateStr = new SimpleDateFormat("dd-MM-yy", Locale.getDefault()).format(new Date());
        String timeStr = new SimpleDateFormat("HH-mm-ss", Locale.getDefault()).format(new Date());
        
        // Format: (hairstyle) (app name) - (date dd/mm/yy) (time hh:mm:ss)
        String fileName = String.format("%s %s - %s %s.png", currentHairstyleName, appName, dateStr, timeStr);

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + appName);
        }

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        if (uri != null) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                // Save as lossless PNG
                if (capturedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    Toast.makeText(this, "Image saved to Pictures/" + appName, Toast.LENGTH_LONG).show();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error saving image", e);
                Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show();
            }
        }

        resetFromCapture();
    }

    // --- DeepARListener Implementation ---

    @Override
    public void initialized() {
        Log.d(TAG, "DeepAR initialized successfully");
        isDeepARInitialized = true;
        // Load a default effect once initialized
        switchHairstyle("brazilian.deepar", getString(R.string.style_brazilian), getString(R.string.desc_brazilian), "brazilian");
    }

    @Override
    public void screenshotTaken(android.graphics.Bitmap bitmap) {
        Log.d(TAG, "Screenshot taken successfully");
        capturedBitmap = bitmap;
        
        runOnUiThread(() -> {
            ivCapturedPreview.setImageBitmap(bitmap);
            ivCapturedPreview.setVisibility(View.VISIBLE);
            layoutPostCapture.setVisibility(View.VISIBLE);
        });
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