package com.zejyv.azizul.uitm.fadebarber;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import com.google.android.material.card.MaterialCardView;

public class ProfileFragment extends Fragment {

    private ScrollView svProfile;
    private ConstraintLayout clTopBar;
    private MaterialCardView mcvProfileHeader;
    private MaterialCardView mcvTopBar;
    private boolean isAnimating = false;
    private boolean isDraggingHeader = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        svProfile = view.findViewById(R.id.sv_profile);
        clTopBar = view.findViewById(R.id.cl_top_bar);
        mcvProfileHeader = view.findViewById(R.id.mcv_profile_header);
        mcvTopBar = view.findViewById(R.id.mcv_top_bar);

        setupCollapsingTopBar();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupCollapsingTopBar() {
        View rootView = getView();
        if (rootView == null || svProfile == null || clTopBar == null || mcvProfileHeader == null || mcvTopBar == null) return;

        final float density = getResources().getDisplayMetrics().density;
        final int expandedHeightPx = (int) (160 * density);
        final int collapsedHeightPx = (int) (70 * density);

        // Click to expand and scroll back up
        mcvTopBar.setOnClickListener(v -> {
            if (clTopBar.getHeight() < expandedHeightPx && !isAnimating) {
                animateTopBar(clTopBar.getHeight(), expandedHeightPx, collapsedHeightPx);
                svProfile.smoothScrollTo(0, 0);
            }
        });

        // Universal Listener for Swipe & Scroll Coordination
        View.OnTouchListener swipeListener = new View.OnTouchListener() {
            private float startY;
            private int initialHeight;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isAnimating) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getRawY();
                        initialHeight = clTopBar.getHeight();
                        isDraggingHeader = false;
                        return false;

                    case MotionEvent.ACTION_MOVE:
                        float deltaY = event.getRawY() - startY;
                        
                        if (!isDraggingHeader && Math.abs(deltaY) > 10) {
                            if (deltaY < -10) { // SWIPE UP (Finger Up) -> Collapse
                                // Only collapse if:
                                // 1. Expanded or partly expanded
                                // 2. Content is scrollable (has enough items)
                                if (clTopBar.getHeight() > collapsedHeightPx && isContentScrollable()) {
                                    isDraggingHeader = true;
                                }
                            } else if (deltaY > 10) { // SWIPE DOWN (Finger Down) -> Expand
                                // Only expand if:
                                // 1. At the very start of the scroll view
                                if (clTopBar.getHeight() < expandedHeightPx && svProfile.getScrollY() <= 0) {
                                    isDraggingHeader = true;
                                }
                            }
                        }

                        if (isDraggingHeader) {
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            if (getActivity() != null) {
                                ViewGroup vp = getActivity().findViewById(R.id.viewPager);
                                if (vp != null) vp.requestDisallowInterceptTouchEvent(true);
                            }

                            int newHeight = Math.max(collapsedHeightPx, Math.min(expandedHeightPx, (int) (initialHeight + deltaY)));
                            updateHeaderHeight(newHeight, expandedHeightPx, collapsedHeightPx);
                            return true; // Stop ScrollView from scrolling
                        }

                        // Rule: If Top Bar is expanded, disable content scrolling entirely
                        if (clTopBar.getHeight() >= expandedHeightPx - 5) {
                            if (Math.abs(deltaY) > 5) {
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                                return true;
                            }
                        }

                        return false;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isDraggingHeader) {
                            int currentHeight = clTopBar.getHeight();
                            if (currentHeight > (expandedHeightPx + collapsedHeightPx) / 2) {
                                animateTopBar(currentHeight, expandedHeightPx, collapsedHeightPx);
                            } else {
                                animateTopBar(currentHeight, collapsedHeightPx, collapsedHeightPx);
                            }
                            isDraggingHeader = false;
                            return true;
                        }
                        return false;
                }
                return false;
            }
        };

        // Apply swipe listener everywhere for universal gesture detection
        rootView.setOnTouchListener(swipeListener);
        svProfile.setOnTouchListener(swipeListener);
        mcvTopBar.setOnTouchListener(swipeListener);
        View scrollContent = rootView.findViewById(R.id.ll_scroll_content);
        if (scrollContent != null) scrollContent.setOnTouchListener(swipeListener);
        
        int[] cardIds = {R.id.mcv_cut_history, R.id.mcv_settings, R.id.mcv_privacy_policy, R.id.mcv_user_agreement, R.id.mcv_about};
        for (int id : cardIds) {
            View card = rootView.findViewById(id);
            if (card != null) card.setOnTouchListener(swipeListener);
        }

        // Disable automatic scroll listener
        svProfile.setOnScrollChangeListener(null);

        // Sync initial state (Assuming starting expanded as per XML 160dp)
        updateHeaderHeight(expandedHeightPx, expandedHeightPx, collapsedHeightPx);
    }

    private boolean isContentScrollable() {
        if (svProfile == null || svProfile.getChildCount() == 0) return false;
        View child = svProfile.getChildAt(0);
        return child.getHeight() > svProfile.getHeight();
    }

    private void updateHeaderHeight(int val, int expandedHeightPx, int collapsedHeightPx) {
        ViewGroup.LayoutParams params = clTopBar.getLayoutParams();
        if (params != null) {
            params.height = val;
            clTopBar.setLayoutParams(params);
        }

        float ratio = (float) (val - collapsedHeightPx) / (expandedHeightPx - collapsedHeightPx);
        mcvProfileHeader.setAlpha(Math.max(0.0f, Math.min(1.0f, ratio)));
        if (ratio > 0.05f) {
            mcvProfileHeader.setVisibility(View.VISIBLE);
        } else {
            mcvProfileHeader.setVisibility(View.GONE);
        }

        // Disable clickability when fully expanded
        boolean isFullyExpanded = val >= expandedHeightPx - 2;
        mcvTopBar.setClickable(!isFullyExpanded);
        mcvTopBar.setFocusable(!isFullyExpanded);
    }

    private void animateTopBar(int from, int to, final int collapsedHeightPx) {
        isAnimating = true;
        final float density = getResources().getDisplayMetrics().density;
        final int expandedHeightPx = (int) (160 * density);

        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(500);
        animator.addUpdateListener(animation -> {
            int val = (Integer) animation.getAnimatedValue();
            updateHeaderHeight(val, expandedHeightPx, collapsedHeightPx);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimating = false;
            }
        });
        animator.start();
    }
}
