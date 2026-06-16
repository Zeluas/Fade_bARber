package com.zejyv.azizul.uitm.fadebarber;

import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class MainActivityEmployee extends AppCompatActivity {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_employee);

        initializeViews();
        setupNavigationSync();
        setupFab();
        setupExitDialog();
        setupLogoutDialog();
        setupCallCustomerDialog();
        setupBackPressed();
    }

    private void initializeViews() {
        viewPager = findViewById(R.id.viewPager);
        bottomNavigationView = findViewById(R.id.bottomNavigation);
        tvFabLabel = findViewById(R.id.tv_fab_label);
        fab = findViewById(R.id.fab);

        EmployeePagerAdapter adapter = new EmployeePagerAdapter(this);
        viewPager.setAdapter(adapter);
    }

    private void setupNavigationSync() {
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == R.id.navigation_book) {
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

        // Default to Home (position 1)
        viewPager.setCurrentItem(1, false);
        updateUiForPage(1);
    }

    private void updateUiForPage(int position) {
        switch (position) {
            case 0:
                bottomNavigationView.setSelectedItemId(R.id.navigation_book);
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

        btnExitCancel.setOnClickListener(v -> hideExitDialog());
        btnExitConfirm.setOnClickListener(v -> finish());

        if (layoutExitConfirmation != null) {
            layoutExitConfirmation.setOnClickListener(v -> hideExitDialog());
        }
    }

    private void setupLogoutDialog() {
        layoutLogoutConfirmation = findViewById(R.id.layout_logout_confirmation);
        mcvLogoutDialog = findViewById(R.id.mcv_logout_dialog);
        Button btnLogoutConfirm = findViewById(R.id.btn_logout_confirm);

        if (btnLogoutConfirm != null) btnLogoutConfirm.setOnClickListener(v -> {
            clearStoredCredentials();
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
            android.content.Intent intent = new android.content.Intent(MainActivityEmployee.this, AuthActivity.class);
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

    /**
     * Initializes and configures the call customer dialog.
     */
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

    /**
     * Shows the call customer dialog with a formatted phone number.
     * @param rawPhone Number in format like "012345678911"
     */
    public void showCallCustomerDialog(String rawPhone) {
        if (layoutCallCustomer == null || mcvCallDialog == null || tvCustomerPhone == null) return;

        this.rawCustomerPhone = rawPhone;

        // Logic for formatting: "+60 12-3456 789..."
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

    /**
     * Hides the call customer dialog with animation.
     */
    public void hideCallCustomerDialog() {
        if (layoutCallCustomer == null || mcvCallDialog == null) return;

        mcvCallDialog.animate().translationY(mcvCallDialog.getHeight()).setDuration(200).start();
        layoutCallCustomer.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutCallCustomer.setVisibility(View.GONE)).start();
    }

    private void hideExitDialog() {
        mcvExitDialog.animate().scaleX(0f).scaleY(0f).setDuration(200).start();
        layoutExitConfirmation.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutExitConfirmation.setVisibility(View.GONE)).start();
    }

    private void showExitDialog() {
        layoutExitConfirmation.setVisibility(View.VISIBLE);
        layoutExitConfirmation.setAlpha(0f);
        layoutExitConfirmation.animate().alpha(1f).setDuration(200).start();

        mcvExitDialog.setScaleX(0f);
        mcvExitDialog.setScaleY(0f);
        mcvExitDialog.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
    }

    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutCallCustomer.getVisibility() == View.VISIBLE) {
                    hideCallCustomerDialog();
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
