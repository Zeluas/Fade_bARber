package com.zejyv.azizul.uitm.fadebarber;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class EmployeePagerAdapter extends FragmentStateAdapter {

    public EmployeePagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new EmployeeBookFragment();
            case 1: return new EmployeeHomeFragment();
            case 2: return new ProfileFragment();
            default: return new EmployeeHomeFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
