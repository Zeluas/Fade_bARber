package com.zejyv.azizul.uitm.fadebarber;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Locale;

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

    // --- Exit Dialog Components ---
    private View layoutExitConfirmation, mcvExitDialog;

    // --- Logout Dialog Components ---
    private View layoutLogoutConfirmation, mcvLogoutDialog;
    private Button btnLogoutCancel;

    // --- Call Stylist Dialog Components ---
    private View layoutCallStylist, mcvCallDialog;
    private TextView tvStylistPhone;
    private String rawStylistPhone = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_customer);

        initializeViews();
        setupBadge();
        setupNavigationSync();
        setupFab();
        setupExitDialog();
        setupLogoutDialog();
        setupCallStylistDialog();
        setupBackPressed();
    }

    /**
     * Initializes UI references and sets up the pager adapter.
     */
    private void initializeViews() {
        viewPager = findViewById(R.id.viewPager);
        bottomNavigationView = findViewById(R.id.bottomNavigation);

        MainPagerAdapter adapter = new MainPagerAdapter(this);
        viewPager.setAdapter(adapter);
    }

    /**
     * Configures notification badges on the Activity/Notification tab.
     */
    private void setupBadge() {
        BadgeDrawable badge = bottomNavigationView.getOrCreateBadge(R.id.navigation_notifications);
        badge.setVisible(true);
        badge.setNumber(3); // Mock count
        badge.setBackgroundColor(Color.parseColor("#D81B60"));
        badge.setBadgeTextColor(Color.WHITE);

        // Adjust badge position
        int offset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        badge.setVerticalOffset(offset);
        badge.setHorizontalOffset(offset);
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
     * Hides the custom exit confirmation dialog with animation.
     */
    private void hideExitDialog() {
        mcvExitDialog.animate().scaleX(0f).scaleY(0f).setDuration(200).start();
        layoutExitConfirmation.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutExitConfirmation.setVisibility(View.GONE)).start();
    }

    /**
     * Configures the system back button behavior to show the exit dialog.
     */
    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutCallStylist.getVisibility() == View.VISIBLE) {
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
