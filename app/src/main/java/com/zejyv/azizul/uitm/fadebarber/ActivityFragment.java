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

/**
 * ActivityFragment manages the notifications section of the app.
 * It uses a ViewPager2 to switch between Inbox (new notifications) and Read (history).
 * Features:
 * - Synchronized custom tab bar with a sliding underline.
 * - Localized tab labels with dynamic counts.
 * - ViewPager2 integration for smooth fragment transitions.
 */
public class ActivityFragment extends Fragment {

    // --- UI Components ---
    private ViewPager2 viewPager;
    private FrameLayout layoutTabInbox, layoutTabRead;
    private TextView textTabInbox, textTabRead;
    private View slidingUnderline;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_activity, container, false);

        initializeViews(view);
        setupViewPager();
        setupTabs();
        populateTabLabels();

        return view;
    }

    /**
     * Initializes all view references from the fragment layout.
     */
    private void initializeViews(View view) {
        viewPager = view.findViewById(R.id.view_pager_activity);
        layoutTabInbox = view.findViewById(R.id.layout_tab_inbox);
        layoutTabRead = view.findViewById(R.id.layout_tab_read);
        textTabInbox = view.findViewById(R.id.text_tab_inbox);
        textTabRead = view.findViewById(R.id.text_tab_read);
        slidingUnderline = view.findViewById(R.id.view_sliding_underline);
    }

    /**
     * Sets up the ViewPager2 with its adapter and page change callbacks.
     */
    private void setupViewPager() {
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                // Fragment 0: Inbox List, Fragment 1: Read History
                return (position == 0) ? new ActivityListFragment() : new ReadFragment();
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
                
                // Animate the sliding underline based on scroll progress
                // Using post to ensure the view is measured before calculating translation
                slidingUnderline.post(() -> {
                    float translationX = (position + positionOffset) * slidingUnderline.getWidth();
                    slidingUnderline.setTranslationX(translationX);
                });
            }

            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                // Update the visual style of tabs to reflect the current selection
                updateTabStyles(position);
            }
        });
    }

    /**
     * Configures click listeners for the custom tab layouts.
     */
    private void setupTabs() {
        layoutTabInbox.setOnClickListener(v -> viewPager.setCurrentItem(0));
        layoutTabRead.setOnClickListener(v -> viewPager.setCurrentItem(1));
    }

    private com.google.firebase.firestore.ListenerRegistration countListener;

    /**
     * Fills the tab labels with localized text and real counts from Firestore.
     */
    private void populateTabLabels() {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        if (countListener != null) countListener.remove();
        
        countListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("notifications")
                .whereEqualTo("receiverId", uid)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value != null) {
                        int unread = 0;
                        int read = 0;
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : value) {
                            Boolean isRead = doc.getBoolean("isRead");
                            if (isRead != null && isRead) read++;
                            else unread++;
                        }
                        if (isAdded()) {
                            textTabInbox.setText(getString(R.string.activity_tab_inbox, unread));
                            textTabRead.setText(getString(R.string.activity_tab_read, read));
                        }
                    }
                });
    }

    @Override
    public void onDestroyView() {
        if (countListener != null) countListener.remove();
        super.onDestroyView();
    }

    public void switchToTab(int position) {
        if (viewPager != null) {
            viewPager.setCurrentItem(position, true);
        }
    }

    /**
     * dynamcially updates the background, elevation, and text opacity of the tabs.
     * @param position The currently selected page index.
     */
    private void updateTabStyles(int position) {
        final int activeColor = ContextCompat.getColor(requireContext(), R.color.primary_color);
        final int inactiveColor = Color.parseColor("#208000"); // Darker green for inactive state
        final float elevationPx = 6 * getResources().getDisplayMetrics().density;

        if (position == 0) {
            // --- Inbox Tab Active ---
            layoutTabInbox.setBackgroundColor(activeColor);
            layoutTabInbox.setElevation(elevationPx);
            layoutTabInbox.setForeground(null);
            textTabInbox.setAlpha(1.0f);

            // --- Read Tab Inactive ---
            layoutTabRead.setBackgroundColor(inactiveColor);
            layoutTabRead.setElevation(0);
            layoutTabRead.setForeground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_inner_shadow));
            textTabRead.setAlpha(0.6f);
        } else {
            // --- Inbox Tab Inactive ---
            layoutTabInbox.setBackgroundColor(inactiveColor);
            layoutTabInbox.setElevation(0);
            layoutTabInbox.setForeground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_inner_shadow));
            textTabInbox.setAlpha(0.6f);

            // --- Read Tab Active ---
            layoutTabRead.setBackgroundColor(activeColor);
            layoutTabRead.setElevation(elevationPx);
            layoutTabRead.setForeground(null);
            textTabRead.setAlpha(1.0f);
        }
    }
}
