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

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_customer);

        viewPager = findViewById(R.id.viewPager);
        bottomNavigationView = findViewById(R.id.bottomNavigation);

        MainPagerAdapter adapter = new MainPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // Add badge to Activity menu
        BadgeDrawable badge = bottomNavigationView.getOrCreateBadge(R.id.navigation_notifications);
        badge.setVisible(true);
        badge.setNumber(3);
        badge.setBackgroundColor(Color.parseColor("#D81B60"));
        badge.setBadgeTextColor(Color.WHITE);

        // Reposition badge
        int verticalOffset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        int horizontalOffset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        badge.setVerticalOffset(verticalOffset);
        badge.setHorizontalOffset(horizontalOffset);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            animateBottomNavigationItem(itemId);
            if (itemId == R.id.navigation_home) {
                viewPager.setCurrentItem(0);
                return true;
            } else if (itemId == R.id.navigation_try_on) {
                Intent intent = new Intent(MainActivity.this, TryOnActivity.class);
                startActivity(intent);

                // Delay and revert to last visited page
                int lastPosition = viewPager.getCurrentItem();
                bottomNavigationView.postDelayed(() -> {
                    int lastItemId;
                    if (lastPosition == 1) {
                        lastItemId = R.id.navigation_notifications;
                    } else if (lastPosition == 2) {
                        lastItemId = R.id.navigation_profile;
                    } else {
                        lastItemId = R.id.navigation_home;
                    }
                    bottomNavigationView.setSelectedItemId(lastItemId);
                }, 1500);

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

        bottomNavigationView.post(() -> animateBottomNavigationItem(R.id.navigation_home));
        bottomNavigationView.setOnItemReselectedListener(item -> {
            // Prevent re-triggering logic on re-selection
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 0:
                        bottomNavigationView.setSelectedItemId(R.id.navigation_home);
                        break;
                    case 1:
                        bottomNavigationView.setSelectedItemId(R.id.navigation_notifications);
                        break;
                    case 2:
                        bottomNavigationView.setSelectedItemId(R.id.navigation_profile);
                        break;
                }
            }
        });

        findViewById(R.id.fab).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BookingActivity.class);
            startActivity(intent);
        });

    }

    private void animateBottomNavigationItem(int itemId) {
        final View itemView = bottomNavigationView.findViewById(itemId);
        if (itemView == null) return;

        itemView.post(() -> {
            Drawable background = itemView.getBackground();
            if (background instanceof StateListDrawable) {
                Drawable current = background.getCurrent();
                if (current instanceof LayerDrawable) {
                    LayerDrawable layerDrawable = (LayerDrawable) current;

                    // 1. Animate Pill Underline Width
                    Drawable underline = layerDrawable.findDrawableByLayerId(R.id.pill_underline);
                    if (underline != null) {
                        ValueAnimator scaleAnimator = ValueAnimator.ofInt(0, 10000);
                        scaleAnimator.setDuration(150);
                        scaleAnimator.addUpdateListener(animation -> underline.setLevel((int) animation.getAnimatedValue()));
                        scaleAnimator.start();
                    }

                    // 2. Animate Pill Background Scale
                    Drawable pillBg = layerDrawable.findDrawableByLayerId(R.id.pill_background);
                    if (pillBg != null) {
                        ValueAnimator scaleAnimator = ValueAnimator.ofInt(0, 10000);
                        scaleAnimator.setDuration(150);
                        scaleAnimator.addUpdateListener(animation -> pillBg.setLevel((int) animation.getAnimatedValue()));
                        scaleAnimator.start();
                    }
                }
            }
        });
    }
}
