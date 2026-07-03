package com.zejyv.azizul.uitm.fadebarber;

import android.animation.ValueAnimator;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

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
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.ListenerRegistration;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class MainActivityAdmin extends AppCompatActivity {

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigationView;
    private android.widget.TextView tvFabLabel;

    private FloatingActionButton fab;

    private View layoutExitConfirmation, mcvExitDialog;

    private View layoutLogoutConfirmation, mcvLogoutDialog;

    // --- Call Customer Dialog Components ---
    private View layoutCallCustomer, mcvCallDialog;
    private android.widget.TextView tvCustomerPhone;
    private String rawCustomerPhone = "";

    // --- No-Show Dialog Components ---
    private View layoutNoShowConfirmation, mcvNoShowDialog;
    private Runnable onNoShowConfirmAction;

    // --- Error Banner Components ---
    private View layoutErrorBannerRoot;
    private android.widget.TextView tvErrorBannerMsg;
    private View viewRadarPulse;
    private android.animation.AnimatorSet radarAnimatorSet;
    private boolean isErrorPersistent = false;
    private android.net.ConnectivityManager.NetworkCallback networkCallback;

    private ListenerRegistration notificationListener;
    private static final String CHANNEL_ID = "booking_notifications";

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

            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
            int minute = cal.get(Calendar.MINUTE);
            long delay;

            if (minute < 15 || minute >= 50) {
                delay = TimeUnit.MINUTES.toMillis(1);
            } else {
                delay = TimeUnit.MINUTES.toMillis(50 - minute);
            }

            cleanupHandler.postDelayed(this, delay);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_admin);

        checkNotificationPermission();
        FCMUtils.updateTokenInFirestore();
        startNotificationService();
        initializeViews();
        setupNavigationSync();
        setupFab();
        setupExitDialog();
        setupLogoutDialog();
        setupCallCustomerDialog();
        setupNoShowDialog();
        setupImagePreview();
        setupErrorBanner();
        setupConnectivityListener();
        setupBackPressed();

        checkFirstTimeLogin();
        handleNotificationIntent(getIntent());
    }

    private void checkFirstTimeLogin() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            android.content.SharedPreferences prefs = EncryptedSharedPreferences.create(
                    this, "secret_shared_prefs", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            String fullname = prefs.getString("fullname", "");
            if (fullname == null || fullname.isEmpty()) {
                android.content.Intent intent = new android.content.Intent(this, ProfileEditActivity.class);
                intent.putExtra("IS_FIRST_TIME", true);
                startActivity(intent);
            }
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    private void handleNotificationIntent(android.content.Intent intent) {
        if (intent != null && intent.getBooleanExtra("FROM_NOTIFICATION", false)) {
            String docId = intent.getStringExtra("NOTIFICATION_DOC_ID");
            String title = intent.getStringExtra("NOTIFICATION_TITLE");
            String message = intent.getStringExtra("NOTIFICATION_MESSAGE");
            String type = intent.getStringExtra("NOTIFICATION_TYPE");
            String bookingId = intent.getStringExtra("NOTIFICATION_BOOKING_ID");
            String senderId = intent.getStringExtra("NOTIFICATION_SENDER_ID");
            long ts = intent.getLongExtra("NOTIFICATION_TIMESTAMP", 0);

            if (viewPager != null) viewPager.setCurrentItem(1, false);

            if (docId != null) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("notifications").document(docId)
                        .update("isRead", true, "lastReadTimestamp", com.google.firebase.Timestamp.now());
            }

            android.content.Intent detailIntent = new android.content.Intent(this, NotificationDetailActivity.class);
            detailIntent.putExtra("title", title);
            detailIntent.putExtra("message", message);
            detailIntent.putExtra("type", type);
            detailIntent.putExtra("bookingId", bookingId);
            detailIntent.putExtra("senderId", senderId);
            detailIntent.putExtra("NOTIFICATION_DOC_ID", docId);
            detailIntent.putExtra("timestamp", ts);
            startActivity(detailIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        cleanupHandler.post(cleanupRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        cleanupHandler.removeCallbacks(cleanupRunnable);
    }

    private void initializeViews() {
        viewPager = findViewById(R.id.viewPager);
        bottomNavigationView = findViewById(R.id.bottomNavigation);
        tvFabLabel = findViewById(R.id.tv_fab_label);
        fab = findViewById(R.id.fab);

        AdminPagerAdapter adapter = new AdminPagerAdapter(this);
        viewPager.setAdapter(adapter);
    }

    private void setupNavigationSync() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == R.id.navigation_admin_panel) {
                animateBottomNavigationItem(itemId);
                viewPager.setCurrentItem(0);
                return true;
            } else if (itemId == R.id.navigation_profile) {
                animateBottomNavigationItem(itemId);
                viewPager.setCurrentItem(2);
                return true;
            } else if (itemId == R.id.navigation_placeholder) {
                return false;
            }
            return false;
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateUiForPage(position);
            }
        });

        viewPager.setCurrentItem(1, false);
        updateUiForPage(1);
    }

    private void updateUiForPage(int position) {
        switch (position) {
            case 0:
                bottomNavigationView.setSelectedItemId(R.id.navigation_admin_panel);
                setFabActive(false);
                break;
            case 1:
                bottomNavigationView.getMenu().findItem(R.id.navigation_placeholder).setChecked(true);
                setFabActive(true);
                break;
            case 2:
                bottomNavigationView.setSelectedItemId(R.id.navigation_profile);
                setFabActive(false);
                break;
        }
    }

    private void setFabActive(boolean active) {
        if (active) {
            tvFabLabel.setTextColor(getResources().getColor(R.color.primary_color, getTheme()));
        } else {
            tvFabLabel.setTextColor(android.graphics.Color.parseColor("#808080"));
        }
        if (fab != null) {
            fab.setActivated(active);
        }
    }

    private void setupFab() {
        if (fab != null) {
            fab.setOnClickListener(v -> viewPager.setCurrentItem(1));
        }
    }

    private void setupExitDialog() {
        layoutExitConfirmation = findViewById(R.id.layout_exit_confirmation);
        mcvExitDialog = findViewById(R.id.mcv_exit_dialog);
        Button btnExitCancel = findViewById(R.id.btn_exit_cancel);
        Button btnExitConfirm = findViewById(R.id.btn_exit_confirm);

        if (btnExitCancel != null) btnExitCancel.setOnClickListener(v -> hideExitDialog());
        if (btnExitConfirm != null) btnExitConfirm.setOnClickListener(v -> finish());

        if (layoutExitConfirmation != null) {
            layoutExitConfirmation.setOnClickListener(v -> hideExitDialog());
        }
    }

    private void setupLogoutDialog() {
        layoutLogoutConfirmation = findViewById(R.id.layout_logout_confirmation);
        mcvLogoutDialog = findViewById(R.id.mcv_logout_dialog);
        Button btnLogoutConfirm = findViewById(R.id.btn_logout_confirm);

        if (btnLogoutConfirm != null) btnLogoutConfirm.setOnClickListener(v -> {
            stopService(new android.content.Intent(MainActivityAdmin.this, BookingNotificationService.class));
            clearStoredCredentials();
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
            android.content.Intent intent = new android.content.Intent(MainActivityAdmin.this, AuthActivity.class);
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        if (layoutLogoutConfirmation != null) {
            layoutLogoutConfirmation.setOnClickListener(v -> hideLogoutDialog());
        }
    }

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

    public void hideLogoutDialog() {
        if (layoutLogoutConfirmation == null || mcvLogoutDialog == null) return;

        mcvLogoutDialog.animate().translationY(mcvLogoutDialog.getHeight()).setDuration(200).start();
        layoutLogoutConfirmation.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutLogoutConfirmation.setVisibility(View.GONE)).start();
    }

    private void setupCallCustomerDialog() {
        layoutCallCustomer = findViewById(R.id.layout_call_customer);
        mcvCallDialog = findViewById(R.id.mcv_call_dialog);
        tvCustomerPhone = findViewById(R.id.tv_customer_phone_display);
        View btnCallNow = findViewById(R.id.btn_call_now);

        if (btnCallNow != null) {
            btnCallNow.setOnClickListener(v -> {
                if (rawCustomerPhone != null && !rawCustomerPhone.isEmpty()) {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_DIAL);
                    intent.setData(android.net.Uri.parse("tel:" + rawCustomerPhone));
                    startActivity(intent);
                }
                hideCallCustomerDialog();
            });
        }

        if (layoutCallCustomer != null) {
            layoutCallCustomer.setOnClickListener(v -> hideCallCustomerDialog());
        }
    }

    public void showCallCustomerDialog(String rawPhone) {
        if (layoutCallCustomer == null || mcvCallDialog == null || tvCustomerPhone == null) return;

        this.rawCustomerPhone = rawPhone;

        String formatted = rawPhone;
        if (rawPhone != null && !rawPhone.isEmpty()) {
            String digits = rawPhone;
            if (rawPhone.startsWith("0")) {
                digits = rawPhone.substring(1);
            } else if (rawPhone.startsWith("+60")) {
                digits = rawPhone.substring(3);
            } else if (rawPhone.startsWith("60")) {
                digits = rawPhone.substring(2);
            }

            if (digits.length() >= 6) {
                StringBuilder sb = new StringBuilder("+60 ");
                sb.append(digits.substring(0, 2));
                sb.append("-");
                sb.append(digits.substring(2, 6));

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

        tvCustomerPhone.setText(formatted);

        layoutCallCustomer.setVisibility(View.VISIBLE);
        layoutCallCustomer.setAlpha(0f);
        layoutCallCustomer.animate().alpha(1f).setDuration(200).start();

        mcvCallDialog.post(() -> {
            mcvCallDialog.setTranslationY(mcvCallDialog.getHeight());
            mcvCallDialog.animate().translationY(0).setDuration(300).start();
        });
    }

    public void hideCallCustomerDialog() {
        if (layoutCallCustomer == null || mcvCallDialog == null) return;

        mcvCallDialog.animate().translationY(mcvCallDialog.getHeight()).setDuration(200).start();
        layoutCallCustomer.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutCallCustomer.setVisibility(View.GONE)).start();
    }

    private void setupNoShowDialog() {
        layoutNoShowConfirmation = findViewById(R.id.layout_noshow_confirmation);
        mcvNoShowDialog = findViewById(R.id.mcv_noshow_dialog);
        Button btnNoShowCancel = findViewById(R.id.btn_noshow_cancel);
        Button btnNoShowConfirm = findViewById(R.id.btn_noshow_confirm);
        com.google.android.material.checkbox.MaterialCheckBox cbConfirm = findViewById(R.id.cb_noshow_confirm);

        if (btnNoShowCancel != null) btnNoShowCancel.setOnClickListener(v -> hideNoShowDialog());
        if (btnNoShowConfirm != null) {
            btnNoShowConfirm.setEnabled(false);
            btnNoShowConfirm.setAlpha(0.5f);
            btnNoShowConfirm.setOnClickListener(v -> {
                if (onNoShowConfirmAction != null) onNoShowConfirmAction.run();
                hideNoShowDialog();
            });
        }
        
        if (cbConfirm != null && btnNoShowConfirm != null) {
            cbConfirm.setOnCheckedChangeListener((buttonView, isChecked) -> {
                btnNoShowConfirm.setEnabled(isChecked);
                btnNoShowConfirm.setAlpha(isChecked ? 1.0f : 0.5f);
            });
        }

        if (layoutNoShowConfirmation != null) {
            layoutNoShowConfirmation.setOnClickListener(v -> hideNoShowDialog());
        }
    }

    public void showNoShowDialog(Runnable confirmAction) {
        if (layoutNoShowConfirmation == null || mcvNoShowDialog == null) return;
        this.onNoShowConfirmAction = confirmAction;

        com.google.android.material.checkbox.MaterialCheckBox cbConfirm = findViewById(R.id.cb_noshow_confirm);
        Button btnNoShowConfirm = findViewById(R.id.btn_noshow_confirm);
        if (cbConfirm != null) cbConfirm.setChecked(false);
        if (btnNoShowConfirm != null) {
            btnNoShowConfirm.setEnabled(false);
            btnNoShowConfirm.setAlpha(0.5f);
        }

        layoutNoShowConfirmation.setVisibility(View.VISIBLE);
        layoutNoShowConfirmation.setAlpha(0f);
        layoutNoShowConfirmation.animate().alpha(1f).setDuration(200).start();

        mcvNoShowDialog.setScaleX(0f);
        mcvNoShowDialog.setScaleY(0f);
        mcvNoShowDialog.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
    }

    public void hideNoShowDialog() {
        if (layoutNoShowConfirmation == null || mcvNoShowDialog == null) return;

        mcvNoShowDialog.animate().scaleX(0f).scaleY(0f).setDuration(200).start();
        layoutNoShowConfirmation.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutNoShowConfirmation.setVisibility(View.GONE)).start();
    }

    public void hideExitDialog() {
        if (mcvExitDialog == null || layoutExitConfirmation == null) return;
        mcvExitDialog.animate().scaleX(0f).scaleY(0f).setDuration(200).start();
        layoutExitConfirmation.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutExitConfirmation.setVisibility(View.GONE)).start();
    }

    private void showExitDialog() {
        if (layoutExitConfirmation != null && mcvExitDialog != null) {
            layoutExitConfirmation.setVisibility(View.VISIBLE);
            layoutExitConfirmation.setAlpha(0f);
            layoutExitConfirmation.animate().alpha(1f).setDuration(200).start();

            mcvExitDialog.setScaleX(0f);
            mcvExitDialog.setScaleY(0f);
            mcvExitDialog.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
        }
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

        if (ivFullPreview != null) {
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
                        int remainingPointerIndex = (event.getActionIndex() == 0) ? 1 : 0;
                        lastTouch[0] = event.getX(remainingPointerIndex);
                        lastTouch[1] = event.getY(remainingPointerIndex);
                        break;
                }
                return true;
            });
        }
    }

    private void animateMatrixZoom(float targetFactor, float focusX, float focusY) {
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

    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutErrorBannerRoot != null && layoutErrorBannerRoot.getVisibility() == View.VISIBLE) {
                    if (!isErrorPersistent) hideErrorBanner();
                } else {
                    if (layoutImagePreview != null && layoutImagePreview.getVisibility() == View.VISIBLE) {
                        hideImagePreview();
                    } else if (layoutNoShowConfirmation != null && layoutNoShowConfirmation.getVisibility() == View.VISIBLE) {
                        hideNoShowDialog();
                    } else if (layoutCallCustomer != null && layoutCallCustomer.getVisibility() == View.VISIBLE) {
                        hideCallCustomerDialog();
                    } else if (layoutLogoutConfirmation != null && layoutLogoutConfirmation.getVisibility() == View.VISIBLE) {
                        hideLogoutDialog();
                    } else if (layoutExitConfirmation != null && layoutExitConfirmation.getVisibility() == View.VISIBLE) {
                        hideExitDialog();
                    } else {
                        showExitDialog();
                    }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }

    private void animateBottomNavigationItem(int itemId) {
        final View itemView = bottomNavigationView.findViewById(itemId);
        if (itemView == null) return;

        itemView.post(() -> {
            Drawable bg = itemView.getBackground();
            if (bg instanceof StateListDrawable) {
                Drawable current = bg.getCurrent();
                if (current instanceof LayerDrawable) {
                    LayerDrawable layers = (LayerDrawable) current;
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