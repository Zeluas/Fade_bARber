package com.zejyv.azizul.uitm.fadebarber;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class AdminPagerAdapter extends FragmentStateAdapter {

    public AdminPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new AdminPanelFragment();
            case 1: return new AdminHomeFragment();
            case 2: return new ProfileFragment();
            default: return new AdminHomeFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}