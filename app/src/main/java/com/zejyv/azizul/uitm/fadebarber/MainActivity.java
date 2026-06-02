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

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_customer);

        initializeViews();
        setupBadge();
        setupNavigationSync();
        setupFab();
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
