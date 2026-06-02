package com.zejyv.azizul.uitm.fadebarber;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * MainPagerAdapter: Manages the fragment lifecycle for the main application tabs.
 * Tabs:
 * 0: HomeFragment (Dashboard)
 * 1: ActivityFragment (Notifications)
 * 2: ProfileFragment (User Settings)
 */
public class MainPagerAdapter extends FragmentStateAdapter {

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        // Core Tab Logic
        switch (position) {
            case 0: return new HomeFragment();
            case 1: return new ActivityFragment();
            case 2: return new ProfileFragment();
            default: return new HomeFragment();
        }
    }

    @Override
    public int getItemCount() {
        // Fixed count: Home, Activity, Profile
        return 3;
    }
}
