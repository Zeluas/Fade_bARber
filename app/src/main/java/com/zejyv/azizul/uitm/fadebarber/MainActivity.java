package com.zejyv.azizul.uitm.fadebarber;

import android.animation.ValueAnimator;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * MainActivity: The primary host for the customer application interface.
 * Features:
 * - ViewPager2 for seamless fragment navigation.
 * - Synchronized BottomNavigationView with custom animations.
 * - Notification badges and quick-access FAB for bookings.
 */
public class MainActivity extends AppCompatActivity {

    // --- Core UI Components ---
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigationView;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fab;

    // --- Exit Dialog Components ---
    private View layoutExitConfirmation, mcvExitDialog;

    // --- Logout Dialog Components ---
    private View layoutLogoutConfirmation, mcvLogoutDialog;
    private Button btnLogoutCancel;

    // --- Call Stylist Dialog Components ---
    private View layoutCallStylist, mcvCallDialog;
    private TextView tvStylistPhone;
    private String rawStylistPhone = "";

    // --- Cancel Booking Dialog Components ---
    private View layoutCancelConfirmation, mcvCancelDialog;
    private Runnable onCancelConfirmAction;

    // --- Error Banner Components ---
    private View layoutErrorBannerRoot;
    private android.widget.TextView tvErrorBannerMsg;
    private View viewRadarPulse;
    private android.animation.AnimatorSet radarAnimatorSet;
    private boolean isErrorPersistent = false;
    private android.net.ConnectivityManager.NetworkCallback networkCallback;

    // --- Profile Image Preview Components ---
    private View layoutImagePreview, vPreviewTopBar;
    private ImageView ivFullPreview;
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private final android.graphics.Matrix matrix = new android.graphics.Matrix();
    private float scaleFactor = 1.0f;
    private float minScale = 1.0f;
    private final float[] lastTouch = new float[2];
    private boolean isPanning = false;

    // --- Cleanup Task ---
    private final android.os.Handler cleanupHandler = new android.os.Handler();
    private final Runnable cleanupRunnable = new Runnable() {
        @Override
        public void run() {
            BookingUtils.performBookingCleanup();

            // Calculate timing for next check: 
            // If minute < 50, wait until minute 50.
            // If minute >= 50, check every minute until next hour starts.
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
            int minute = cal.get(Calendar.MINUTE);
            long delay;

            if (minute < 50) {
                delay = TimeUnit.MINUTES.toMillis(50 - minute);
            } else {
                delay = TimeUnit.MINUTES.toMillis(1);
            }

            cleanupHandler.postDelayed(this, delay);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_customer);

        checkNotificationPermission();
        FCMUtils.updateTokenInFirestore();
        startNotificationService();
        initializeViews();
        setupBadge();
        setupNavigationSync();
        setupFab();
        setupExitDialog();
        setupLogoutDialog();
        setupCallStylistDialog();
        setupCancelDialog();
        setupImagePreview();
        setupErrorBanner();
        setupConnectivityListener();
        setupBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start periodic cleanup
        cleanupHandler.post(cleanupRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop periodic cleanup to save battery/data when app is in background
        cleanupHandler.removeCallbacks(cleanupRunnable);
    }

    /**
     * Initializes UI references and sets up the pager adapter.
     */
    private void initializeViews() {
        viewPager = findViewById(R.id.viewPager);
        bottomNavigationView = findViewById(R.id.bottomNavigation);
        fab = findViewById(R.id.fab);

        MainPagerAdapter adapter = new MainPagerAdapter(this);
        viewPager.setAdapter(adapter);
    }

    private com.google.firebase.firestore.ListenerRegistration badgeListener;

    /**
     * Configures notification badges on the Activity/Notification tab.
     * Dynamically updates based on unread notifications in Firestore.
     */
    private void setupBadge() {
        final BadgeDrawable badge = bottomNavigationView.getOrCreateBadge(R.id.navigation_notifications);
        badge.setBackgroundColor(Color.parseColor("#D81B60"));
        badge.setBadgeTextColor(Color.WHITE);

        // Adjust badge position
        int offset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        badge.setVerticalOffset(offset);
        badge.setHorizontalOffset(offset);

        // Fetch unread count from Firestore
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            badge.setVisible(false);
            return;
        }

        if (badgeListener != null) badgeListener.remove();
        badgeListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("notifications")
                .whereEqualTo("receiverId", uid)
                .whereEqualTo("isRead", false)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value != null) {
                        int count = value.size();
                        if (count > 0) {
                            badge.setVisible(true);
                            badge.setNumber(count);
                        } else {
                            badge.setVisible(false);
                        }
                    }
                });
    }

    /**
     * Synchronizes BottomNavigationView clicks with ViewPager2 swipes.
     */
    private void setupNavigationSync() {
        // Handle menu item selections
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            animateBottomNavigationItem(itemId);

            if (itemId == R.id.navigation_home) {
                viewPager.setCurrentItem(0);
                return true;
            } else if (itemId == R.id.navigation_try_on) {
                // Quick launch for Try-On tool
                startActivity(new Intent(MainActivity.this, TryOnActivity.class));
                revertSelectionAfterDelay();
                return true;
            } else if (itemId == R.id.navigation_notifications) {
                viewPager.setCurrentItem(1);
                return true;
            } else if (itemId == R.id.navigation_profile) {
                viewPager.setCurrentItem(2);
                return true;
            }
            return false;
        });

        // Update Nav selection based on Pager swipe
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 0: bottomNavigationView.setSelectedItemId(R.id.navigation_home); break;
                    case 1: bottomNavigationView.setSelectedItemId(R.id.navigation_notifications); break;
                    case 2: bottomNavigationView.setSelectedItemId(R.id.navigation_profile); break;
                }
            }
        });

        // Trigger initial animation for the default tab
        bottomNavigationView.post(() -> animateBottomNavigationItem(R.id.navigation_home));
        
        // Disable actions on re-selection
        bottomNavigationView.setOnItemReselectedListener(item -> {});
    }

    /**
     * Feature: Briefly shows Try-On selection, then reverts to the last active tab.
     */
    private void revertSelectionAfterDelay() {
        int lastPos = viewPager.getCurrentItem();
        bottomNavigationView.postDelayed(() -> {
            int targetId;
            if (lastPos == 1) targetId = R.id.navigation_notifications;
            else if (lastPos == 2) targetId = R.id.navigation_profile;
            else targetId = R.id.navigation_home;
            bottomNavigationView.setSelectedItemId(targetId);
        }, 1500);
    }

    /**
     * Configures the primary booking trigger button.
     */
    private void setupFab() {
        findViewById(R.id.fab).setOnClickListener(v -> 
            startActivity(new Intent(MainActivity.this, BookingActivity.class))
        );
    }

    /**
     * Initializes and configures the custom exit confirmation dialog.
     */
    private void setupExitDialog() {
        layoutExitConfirmation = findViewById(R.id.layout_exit_confirmation);
        mcvExitDialog = findViewById(R.id.mcv_exit_dialog);
        Button btnExitCancel = findViewById(R.id.btn_exit_cancel);
        Button btnExitConfirm = findViewById(R.id.btn_exit_confirm);

        btnExitCancel.setOnClickListener(v -> hideExitDialog());
        btnExitConfirm.setOnClickListener(v -> finish());

        // Clicking outside the dialog (on the overlay) hides it
        if (layoutExitConfirmation != null) {
            layoutExitConfirmation.setOnClickListener(v -> hideExitDialog());
        }
    }

    /**
     * Initializes and configures the logout confirmation dialog.
     */
    private void setupLogoutDialog() {
        layoutLogoutConfirmation = findViewById(R.id.layout_logout_confirmation);
        mcvLogoutDialog = findViewById(R.id.mcv_logout_dialog);
        Button btnLogoutConfirm = findViewById(R.id.btn_logout_confirm);

        if (btnLogoutConfirm != null) btnLogoutConfirm.setOnClickListener(v -> {
            stopService(new Intent(MainActivity.this, BookingNotificationService.class));
            clearStoredCredentials();
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(MainActivity.this, AuthActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        if (layoutLogoutConfirmation != null) {
            layoutLogoutConfirmation.setOnClickListener(v -> hideLogoutDialog());
        }
    }

    /**
     * Initializes and configures the call stylist dialog.
     */
    private void setupCallStylistDialog() {
        layoutCallStylist = findViewById(R.id.layout_call_hairstylist);
        mcvCallDialog = findViewById(R.id.mcv_call_dialog);
        tvStylistPhone = findViewById(R.id.tv_hairstylist_phone_display);
        View btnCallNow = findViewById(R.id.btn_call_now);

        if (btnCallNow != null) {
            btnCallNow.setOnClickListener(v -> {
                if (rawStylistPhone != null && !rawStylistPhone.isEmpty()) {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(android.net.Uri.parse("tel:" + rawStylistPhone));
                    startActivity(intent);
                }
                hideCallStylistDialog();
            });
        }

        if (layoutCallStylist != null) {
            layoutCallStylist.setOnClickListener(v -> hideCallStylistDialog());
        }
    }

    /**
     * Shows the call stylist dialog with a formatted phone number.
     * @param rawPhone Number in format like "012345678911"
     */
    public void showCallStylistDialog(String rawPhone) {
        if (layoutCallStylist == null || mcvCallDialog == null || tvStylistPhone == null) return;

        this.rawStylistPhone = rawPhone;

        // Logic for formatting: "+60 12-3456 789..."
        // Format: +60 (2 digits)-(4 digits) (remaining digits in groups of 4)
        String formatted = rawPhone;
        if (rawPhone != null && !rawPhone.isEmpty()) {
            String digits = rawPhone;
            if (rawPhone.startsWith("0")) {
                digits = rawPhone.substring(1); // Remove leading 0 for +60
            } else if (rawPhone.startsWith("+60")) {
                digits = rawPhone.substring(3);
            } else if (rawPhone.startsWith("60")) {
                digits = rawPhone.substring(2);
            }

            if (digits.length() >= 6) {
                StringBuilder sb = new StringBuilder("+60 ");
                sb.append(digits.substring(0, 2)); // 12
                sb.append("-");
                sb.append(digits.substring(2, 6)); // 3245
                
                String remaining = digits.substring(6);
                for (int i = 0; i < remaining.length(); i++) {
                    if (i > 0 && i % 4 == 0) sb.append(" ");
                    if (i == 0) sb.append(" ");
                    sb.append(remaining.charAt(i));
                }
                formatted = sb.toString();
            } else {
                formatted = "+60 " + digits;
            }
        }

        tvStylistPhone.setText(formatted);

        layoutCallStylist.setVisibility(View.VISIBLE);
        layoutCallStylist.setAlpha(0f);
        layoutCallStylist.animate().alpha(1f).setDuration(200).start();

        mcvCallDialog.post(() -> {
            mcvCallDialog.setTranslationY(mcvCallDialog.getHeight());
            mcvCallDialog.animate().translationY(0).setDuration(300).start();
        });
    }

    /**
     * Hides the call stylist dialog with animation.
     */
    public void hideCallStylistDialog() {
        if (layoutCallStylist == null || mcvCallDialog == null) return;

        mcvCallDialog.animate().translationY(mcvCallDialog.getHeight()).setDuration(200).start();
        layoutCallStylist.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutCallStylist.setVisibility(View.GONE)).start();
    }

    /**
     * Clears all stored credentials from EncryptedSharedPreferences.
     */
    private void clearStoredCredentials() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            android.content.SharedPreferences prefs = EncryptedSharedPreferences.create(
                    this,
                    "secret_shared_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            prefs.edit().clear().apply();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Shows the logout confirmation dialog with animation.
     */
    public void showLogoutDialog() {
        if (layoutLogoutConfirmation == null || mcvLogoutDialog == null) return;

        layoutLogoutConfirmation.setVisibility(View.VISIBLE);
        layoutLogoutConfirmation.setAlpha(0f);
        layoutLogoutConfirmation.animate().alpha(1f).setDuration(200).start();

        mcvLogoutDialog.post(() -> {
            mcvLogoutDialog.setTranslationY(mcvLogoutDialog.getHeight());
            mcvLogoutDialog.animate().translationY(0).setDuration(300).start();
        });
    }

    /**
     * Hides the logout confirmation dialog with animation.
     */
    public void hideLogoutDialog() {
        if (layoutLogoutConfirmation == null || mcvLogoutDialog == null) return;

        mcvLogoutDialog.animate().translationY(mcvLogoutDialog.getHeight()).setDuration(200).start();
        layoutLogoutConfirmation.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutLogoutConfirmation.setVisibility(View.GONE)).start();
    }

    /**
     * Shows the custom exit confirmation dialog with animation.
     */
    private void showExitDialog() {
        layoutExitConfirmation.setVisibility(View.VISIBLE);
        layoutExitConfirmation.setAlpha(0f);
        layoutExitConfirmation.animate().alpha(1f).setDuration(200).start();

        mcvExitDialog.setScaleX(0f);
        mcvExitDialog.setScaleY(0f);
        mcvExitDialog.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
    }

    /**
     * Initializes and configures the cancel booking confirmation dialog.
     */
    private void setupCancelDialog() {
        layoutCancelConfirmation = findViewById(R.id.layout_cancel_confirmation);
        mcvCancelDialog = findViewById(R.id.mcv_cancel_dialog);
        Button btnCancelBack = findViewById(R.id.btn_cancel_dialog_back);
        Button btnCancelConfirm = findViewById(R.id.btn_cancel_dialog_confirm);
        com.google.android.material.checkbox.MaterialCheckBox cbConfirm = findViewById(R.id.cb_cancel_confirm);

        if (btnCancelBack != null) btnCancelBack.setOnClickListener(v -> hideCancelDialog());
        if (btnCancelConfirm != null) {
            btnCancelConfirm.setEnabled(false);
            btnCancelConfirm.setAlpha(0.5f);
            btnCancelConfirm.setOnClickListener(v -> {
                if (onCancelConfirmAction != null) onCancelConfirmAction.run();
                hideCancelDialog();
            });
        }
        
        if (cbConfirm != null && btnCancelConfirm != null) {
            cbConfirm.setOnCheckedChangeListener((buttonView, isChecked) -> {
                btnCancelConfirm.setEnabled(isChecked);
                btnCancelConfirm.setAlpha(isChecked ? 1.0f : 0.5f);
            });
        }

        if (layoutCancelConfirmation != null) {
            layoutCancelConfirmation.setOnClickListener(v -> hideCancelDialog());
        }
    }

    /**
     * Shows the cancel booking confirmation dialog.
     * @param confirmAction Callback to run when confirmed.
     */
    public void showCancelDialog(Runnable confirmAction) {
        if (layoutCancelConfirmation == null || mcvCancelDialog == null) return;
        this.onCancelConfirmAction = confirmAction;

        // Reset state
        com.google.android.material.checkbox.MaterialCheckBox cbConfirm = findViewById(R.id.cb_cancel_confirm);
        Button btnCancelConfirm = findViewById(R.id.btn_cancel_dialog_confirm);
        if (cbConfirm != null) cbConfirm.setChecked(false);
        if (btnCancelConfirm != null) {
            btnCancelConfirm.setEnabled(false);
            btnCancelConfirm.setAlpha(0.5f);
        }

        layoutCancelConfirmation.setVisibility(View.VISIBLE);
        layoutCancelConfirmation.setAlpha(0f);
        layoutCancelConfirmation.animate().alpha(1f).setDuration(200).start();

        mcvCancelDialog.setScaleX(0f);
        mcvCancelDialog.setScaleY(0f);
        mcvCancelDialog.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
    }

    /**
     * Hides the cancel booking confirmation dialog.
     */
    public void hideCancelDialog() {
        if (layoutCancelConfirmation == null || mcvCancelDialog == null) return;

        mcvCancelDialog.animate().scaleX(0f).scaleY(0f).setDuration(200).start();
        layoutCancelConfirmation.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutCancelConfirmation.setVisibility(View.GONE)).start();
    }

    public void hideExitDialog() {
        if (mcvExitDialog == null || layoutExitConfirmation == null) return;
        mcvExitDialog.animate().scaleX(0f).scaleY(0f).setDuration(200).start();
        layoutExitConfirmation.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutExitConfirmation.setVisibility(View.GONE)).start();
    }

    public void navigateToProfile() {
        if (viewPager != null) {
            viewPager.setCurrentItem(2, true);
        }
    }

    private void setupImagePreview() {
        layoutImagePreview = findViewById(R.id.layout_image_preview);
        ivFullPreview = findViewById(R.id.iv_full_preview);
        vPreviewTopBar = findViewById(R.id.v_preview_top_bar);
        View btnClose = findViewById(R.id.btn_close_preview);

        if (btnClose != null) btnClose.setOnClickListener(v -> hideImagePreview());

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float nextScale = scaleFactor * factor;
                
                if (nextScale > 0.5f && nextScale < 6.0f) {
                    scaleFactor = nextScale;
                    matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                    ivFullPreview.setImageMatrix(matrix);
                }
                return true;
            }
        });

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(android.view.MotionEvent e) {
                if (scaleFactor > minScale * 1.1f) {
                    animateMatrixReset();
                } else {
                    animateMatrixZoom(2.25f, e.getX(), e.getY());
                }
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                if (!isPanning) togglePreviewTopBar();
                return true;
            }
        });

        ivFullPreview.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            
            int action = event.getAction() & android.view.MotionEvent.ACTION_MASK;
            switch (action) {
                case android.view.MotionEvent.ACTION_DOWN:
                    lastTouch[0] = event.getX();
                    lastTouch[1] = event.getY();
                    isPanning = false;
                    break;

                case android.view.MotionEvent.ACTION_POINTER_DOWN:
                    // Stop panning when second finger touches
                    isPanning = false;
                    break;

                case android.view.MotionEvent.ACTION_MOVE:
                    if (!scaleGestureDetector.isInProgress() && event.getPointerCount() == 1) {
                        float dx = event.getX() - lastTouch[0];
                        float dy = event.getY() - lastTouch[1];
                        
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                            isPanning = true;
                            matrix.postTranslate(dx, dy);
                            ivFullPreview.setImageMatrix(matrix);
                            lastTouch[0] = event.getX();
                            lastTouch[1] = event.getY();
                        }
                    }
                    break;

                case android.view.MotionEvent.ACTION_POINTER_UP:
                    // Reset lastTouch to the remaining finger to prevent jitter
                    int remainingPointerIndex = (event.getActionIndex() == 0) ? 1 : 0;
                    lastTouch[0] = event.getX(remainingPointerIndex);
                    lastTouch[1] = event.getY(remainingPointerIndex);
                    break;
            }
            return true;
        });
    }

    private void animateMatrixZoom(float targetFactor, float focusX, float focusY) {
        float startScale = scaleFactor;
        float endScale = scaleFactor * targetFactor;
        
        if (endScale > 6.0f) endScale = 6.0f;
        final float finalEndScale = endScale;
        
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(1.0f, targetFactor);
        animator.setDuration(300);
        animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        
        final float[] lastVal = {1.0f};
        animator.addUpdateListener(animation -> {
            float currentVal = (float) animation.getAnimatedValue();
            float deltaScale = currentVal / lastVal[0];
            lastVal[0] = currentVal;
            
            scaleFactor *= deltaScale;
            matrix.postScale(deltaScale, deltaScale, focusX, focusY);
            ivFullPreview.setImageMatrix(matrix);
        });
        animator.start();
    }

    private void animateMatrixReset() {
        if (ivFullPreview.getDrawable() == null) return;

        float viewWidth = ivFullPreview.getWidth();
        float viewHeight = ivFullPreview.getHeight();
        float imgWidth = ivFullPreview.getDrawable().getIntrinsicWidth();
        float imgHeight = ivFullPreview.getDrawable().getIntrinsicHeight();

        float targetScale = Math.min(viewWidth / imgWidth, viewHeight / imgHeight);
        float targetDx = (viewWidth - imgWidth * targetScale) / 2;
        float targetDy = (viewHeight - imgHeight * targetScale) / 2;

        float[] startValues = new float[9];
        matrix.getValues(startValues);

        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(300);
        animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            float currScale = startValues[android.graphics.Matrix.MSCALE_X] + (targetScale - startValues[android.graphics.Matrix.MSCALE_X]) * fraction;
            float currDx = startValues[android.graphics.Matrix.MTRANS_X] + (targetDx - startValues[android.graphics.Matrix.MTRANS_X]) * fraction;
            float currDy = startValues[android.graphics.Matrix.MTRANS_Y] + (targetDy - startValues[android.graphics.Matrix.MTRANS_Y]) * fraction;

            matrix.reset();
            matrix.postScale(currScale, currScale);
            matrix.postTranslate(currDx, currDy);
            scaleFactor = currScale;
            ivFullPreview.setImageMatrix(matrix);
        });
        animator.start();
    }

    private void resetImageMatrix() {
        if (ivFullPreview.getDrawable() == null) return;
        
        float viewWidth = ivFullPreview.getWidth();
        float viewHeight = ivFullPreview.getHeight();
        float imgWidth = ivFullPreview.getDrawable().getIntrinsicWidth();
        float imgHeight = ivFullPreview.getDrawable().getIntrinsicHeight();

        float scaleX = viewWidth / imgWidth;
        float scaleY = viewHeight / imgHeight;
        scaleFactor = Math.min(scaleX, scaleY);
        minScale = scaleFactor;

        matrix.reset();
        matrix.postScale(scaleFactor, scaleFactor);
        
        float dx = (viewWidth - imgWidth * scaleFactor) / 2;
        float dy = (viewHeight - imgHeight * scaleFactor) / 2;
        matrix.postTranslate(dx, dy);
        
        ivFullPreview.setImageMatrix(matrix);
    }

    public void showImagePreview(String imageUrl) {
        if (layoutImagePreview == null || ivFullPreview == null || imageUrl == null || imageUrl.isEmpty()) return;

        Glide.with(this)
                .asBitmap()
                .load(imageUrl)
                .placeholder(R.drawable.ic_profile)
                .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull android.graphics.Bitmap resource, @Nullable com.bumptech.glide.request.transition.Transition<? super android.graphics.Bitmap> transition) {
                        ivFullPreview.setImageBitmap(resource);
                        resetImageMatrix();
                    }

                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                        ivFullPreview.setImageDrawable(placeholder);
                    }
                });

        layoutImagePreview.setVisibility(View.VISIBLE);
        layoutImagePreview.setAlpha(0f);
        layoutImagePreview.animate().alpha(1f).setDuration(200).start();

        layoutImagePreview.setScaleX(0.7f);
        layoutImagePreview.setScaleY(0.7f);
        layoutImagePreview.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start();

        vPreviewTopBar.setVisibility(View.VISIBLE);
        vPreviewTopBar.setAlpha(1f);
        View btnClose = findViewById(R.id.btn_close_preview);
        View tvTitle = findViewById(R.id.tv_preview_title);
        if (btnClose != null) { btnClose.setVisibility(View.VISIBLE); btnClose.setAlpha(1f); }
        if (tvTitle != null) { tvTitle.setVisibility(View.VISIBLE); tvTitle.setAlpha(1f); }
    }

    public void hideImagePreview() {
        if (layoutImagePreview == null) return;
        layoutImagePreview.animate().scaleX(0f).scaleY(0f).setDuration(200).start();
        layoutImagePreview.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutImagePreview.setVisibility(View.GONE)).start();
    }

    private void togglePreviewTopBar() {
        if (vPreviewTopBar == null) return;
        View btnClose = findViewById(R.id.btn_close_preview);
        View tvTitle = findViewById(R.id.tv_preview_title);

        boolean isVisible = vPreviewTopBar.getVisibility() == View.VISIBLE;
        if (isVisible) {
            vPreviewTopBar.animate().alpha(0f).setDuration(300).withEndAction(() -> vPreviewTopBar.setVisibility(View.GONE)).start();
            if (btnClose != null) btnClose.animate().alpha(0f).setDuration(300).withEndAction(() -> btnClose.setVisibility(View.GONE)).start();
            if (tvTitle != null) tvTitle.animate().alpha(0f).setDuration(300).withEndAction(() -> tvTitle.setVisibility(View.GONE)).start();
        } else {
            vPreviewTopBar.setVisibility(View.VISIBLE);
            vPreviewTopBar.setAlpha(0f);
            vPreviewTopBar.animate().alpha(1f).setDuration(300).start();
            if (btnClose != null) { btnClose.setVisibility(View.VISIBLE); btnClose.setAlpha(0f); btnClose.animate().alpha(1f).setDuration(300).start(); }
            if (tvTitle != null) { tvTitle.setVisibility(View.VISIBLE); tvTitle.setAlpha(0f); tvTitle.animate().alpha(1f).setDuration(300).start(); }
        }
    }

    private void setupErrorBanner() {
        layoutErrorBannerRoot = findViewById(R.id.layout_error_banner_root);
        tvErrorBannerMsg = findViewById(R.id.tv_error_banner_msg);
        viewRadarPulse = findViewById(R.id.view_radar_pulse);

        if (layoutErrorBannerRoot != null) {
            layoutErrorBannerRoot.setOnClickListener(v -> {
                if (!isErrorPersistent) hideErrorBanner();
            });
        }
    }

    private void setupConnectivityListener() {
        android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        // Initial check
        if (!isNetworkAvailable()) {
            showErrorBanner("No internet connection. Please check your network.", true);
        }

        networkCallback = new android.net.ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull android.net.Network network) {
                runOnUiThread(() -> {
                    if (isErrorPersistent && tvErrorBannerMsg != null && 
                        "No internet connection. Please check your network.".equals(tvErrorBannerMsg.getText().toString())) {
                        hideErrorBanner();
                    }
                });
            }

            @Override
            public void onLost(@NonNull android.net.Network network) {
                runOnUiThread(() -> {
                    if (!isNetworkAvailable()) {
                        showErrorBanner("No internet connection. Please check your network.", true);
                    }
                });
            }
        };

        connectivityManager.registerDefaultNetworkCallback(networkCallback);
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void startNotificationService() {
        android.content.Intent intent = new android.content.Intent(this, BookingNotificationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            android.net.NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
            return capabilities != null && (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
        }
        return false;
    }

    public void showErrorBanner(String message) {
        showErrorBanner(message, false);
    }

    public void showErrorBanner(String message, boolean persistent) {
        if (layoutErrorBannerRoot == null || tvErrorBannerMsg == null || viewRadarPulse == null) return;

        this.isErrorPersistent = persistent;
        tvErrorBannerMsg.setText(message);
        layoutErrorBannerRoot.setVisibility(View.VISIBLE);
        layoutErrorBannerRoot.setAlpha(1f);

        if (radarAnimatorSet == null) {
            android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator.ofFloat(viewRadarPulse, "scaleX", 1f, 6f);
            android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator.ofFloat(viewRadarPulse, "scaleY", 1f, 3f);
            android.animation.ObjectAnimator alpha = android.animation.ObjectAnimator.ofFloat(viewRadarPulse, "alpha", 0.7f, 0f);

            scaleX.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            scaleY.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            alpha.setRepeatCount(android.animation.ValueAnimator.INFINITE);

            radarAnimatorSet = new android.animation.AnimatorSet();
            radarAnimatorSet.playTogether(scaleX, scaleY, alpha);
            radarAnimatorSet.setDuration(2000);
        }
        if (!radarAnimatorSet.isRunning()) radarAnimatorSet.start();
    }

    public void hideErrorBanner() {
        if (layoutErrorBannerRoot == null) return;
        isErrorPersistent = false;
        if (radarAnimatorSet != null) radarAnimatorSet.cancel();
        layoutErrorBannerRoot.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            layoutErrorBannerRoot.setVisibility(View.GONE);
            viewRadarPulse.setScaleX(1f);
            viewRadarPulse.setScaleY(1f);
            viewRadarPulse.setAlpha(0f);
        }).start();
    }

    public String formatError(Exception e) {
        if (e == null) return "An unexpected error occurred. Please try again.";
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("network") || msg.contains("unavailable") || msg.contains("offline") || msg.contains("failed to get document") || msg.contains("grpc")) {
            return "No internet connection. Please check your network.";
        } else if (msg.contains("timeout") || msg.contains("deadline")) {
            return "Connection timed out. Please try again.";
        } else if (msg.contains("quota exceeded") || msg.contains("too many requests")) {
            return "Too many requests. Please try again later.";
        }
        return "Something went wrong. Please try again.";
    }

    /**
     * Configures the system back button behavior to show the exit dialog.
     */
    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutErrorBannerRoot != null && layoutErrorBannerRoot.getVisibility() == View.VISIBLE) {
                    if (!isErrorPersistent) hideErrorBanner();
                } else if (layoutImagePreview != null && layoutImagePreview.getVisibility() == View.VISIBLE) {
                    hideImagePreview();
                } else if (layoutCancelConfirmation != null && layoutCancelConfirmation.getVisibility() == View.VISIBLE) {
                    hideCancelDialog();
                } else if (layoutCallStylist.getVisibility() == View.VISIBLE) {
                    hideCallStylistDialog();
                } else if (layoutLogoutConfirmation.getVisibility() == View.VISIBLE) {
                    hideLogoutDialog();
                } else if (layoutExitConfirmation.getVisibility() == View.VISIBLE) {
                    hideExitDialog();
                } else {
                    showExitDialog();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (badgeListener != null) badgeListener.remove();
        super.onDestroy();
        if (networkCallback != null) {
            android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }

    /**
     * Executes the custom "Pill" expansion animation for navigation items.
     */
    private void animateBottomNavigationItem(int itemId) {
        final View itemView = bottomNavigationView.findViewById(itemId);
        if (itemView == null) return;

        itemView.post(() -> {
            Drawable bg = itemView.getBackground();
            if (bg instanceof StateListDrawable) {
                Drawable current = bg.getCurrent();
                if (current instanceof LayerDrawable) {
                    LayerDrawable layers = (LayerDrawable) current;

                    // Animate underline and background fill levels (0-10000)
                    animateLayer(layers.findDrawableByLayerId(R.id.pill_underline));
                    animateLayer(layers.findDrawableByLayerId(R.id.pill_background));
                }
            }
        });
    }

    private void animateLayer(Drawable drawable) {
        if (drawable != null) {
            ValueAnimator anim = ValueAnimator.ofInt(0, 10000);
            anim.setDuration(150);
            anim.addUpdateListener(a -> drawable.setLevel((int) a.getAnimatedValue()));
            anim.start();
        }
    }
}
