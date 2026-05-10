package com.zejyv.azizul.uitm.fadebarber;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

public class ActivityFragment extends Fragment {

    private ViewPager2 viewPager;
    private FrameLayout layoutTabInbox, layoutTabRead;
    private TextView textTabInbox, textTabRead;
    private View slidingUnderline;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_activity, container, false);

        viewPager = view.findViewById(R.id.view_pager_activity);
        layoutTabInbox = view.findViewById(R.id.layout_tab_inbox);
        layoutTabRead = view.findViewById(R.id.layout_tab_read);
        textTabInbox = view.findViewById(R.id.text_tab_inbox);
        textTabRead = view.findViewById(R.id.text_tab_read);
        slidingUnderline = view.findViewById(R.id.view_sliding_underline);

        setupViewPager();
        setupTabs();

        return view;
    }

    private void setupViewPager() {
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == 0) {
                    return new ActivityListFragment(); // Inbox
                } else {
                    return new ReadFragment(); // Read
                }
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                
                // Using post to ensure width is measured
                slidingUnderline.post(() -> {
                    float translationX = (position + positionOffset) * slidingUnderline.getWidth();
                    slidingUnderline.setTranslationX(translationX);
                });
            }

            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateTabStyles(position);
            }
        });
    }

    private void setupTabs() {
        layoutTabInbox.setOnClickListener(v -> viewPager.setCurrentItem(0));
        layoutTabRead.setOnClickListener(v -> viewPager.setCurrentItem(1));
    }

    private void updateTabStyles(int position) {
        if (position == 0) {
            // Inbox On
            layoutTabInbox.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary_color));
            layoutTabInbox.setElevation(convertDpToPx());
            layoutTabInbox.setForeground(null);
            textTabInbox.setAlpha(1.0f);

            // Read Off
            layoutTabRead.setBackgroundColor(Color.parseColor("#208000"));
            layoutTabRead.setElevation(0);
            layoutTabRead.setForeground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_inner_shadow));
            textTabRead.setAlpha(0.6f);
        } else {
            // Inbox Off
            layoutTabInbox.setBackgroundColor(Color.parseColor("#208000"));
            layoutTabInbox.setElevation(0);
            layoutTabInbox.setForeground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_inner_shadow));
            textTabInbox.setAlpha(0.6f);

            // Read On
            layoutTabRead.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary_color));
            layoutTabRead.setElevation(convertDpToPx());
            layoutTabRead.setForeground(null);
            textTabRead.setAlpha(1.0f);
        }
    }

    private float convertDpToPx() {
        return 6 * getResources().getDisplayMetrics().density;
    }
}