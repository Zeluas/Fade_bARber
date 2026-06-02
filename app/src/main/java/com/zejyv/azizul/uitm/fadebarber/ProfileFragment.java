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
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import com.google.android.material.card.MaterialCardView;

/**
 * ProfileFragment manages the user profile screen.
 * Features:
 * - Collapsing Top Bar with floating bubbles.
 * - Profile header that fades during collapse.
 * - Coordinated swipe and scroll gestures.
 * - Optimized flat layout and localized strings.
 */
public class ProfileFragment extends Fragment {

    // --- UI Components ---
    private ScrollView svProfile;
    private ConstraintLayout clTopBar;
    private MaterialCardView mcvProfileHeader;
    private MaterialCardView mcvTopBar;
    private TextView tvUserName, tvUserId;

    // --- State & Constants ---
    private boolean isAnimating = false;
    private boolean isDraggingHeader = false;
    
    private int expandedHeightPx;
    private int collapsedHeightPx;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initializeViews(view);
        calculateDimensions();
        setupCollapsingTopBar();
        populateUserData();
    }

    /**
     * Initializes all view references from the fragment layout.
     */
    private void initializeViews(View view) {
        svProfile = view.findViewById(R.id.sv_profile);
        clTopBar = view.findViewById(R.id.cl_top_bar);
        mcvProfileHeader = view.findViewById(R.id.mcv_profile_header);
        mcvTopBar = view.findViewById(R.id.mcv_top_bar);
        tvUserName = view.findViewById(R.id.tv_user_name);
        tvUserId = view.findViewById(R.id.tv_user_id);
    }

    /**
     * Calculates density-independent pixel values for the header states.
     */
    private void calculateDimensions() {
        final float density = getResources().getDisplayMetrics().density;
        expandedHeightPx = (int) (160 * density);
        collapsedHeightPx = (int) (70 * density);
    }

    /**
     * Fills the profile views with localized user data.
     */
    private void populateUserData() {
        if (tvUserName != null) {
            tvUserName.setText(getString(R.string.profile_user_name_default));
        }
        if (tvUserId != null) {
            // Using placeholder ID for now
            tvUserId.setText(getString(R.string.profile_user_id_label, "123456"));
        }
    }

    /**
     * Configures the complex interaction between the collapsing header and the scrollable content.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void setupCollapsingTopBar() {
        View rootView = getView();
        if (rootView == null || svProfile == null || clTopBar == null || mcvProfileHeader == null || mcvTopBar == null) return;

        // Click to expand and scroll back up
        mcvTopBar.setOnClickListener(v -> {
            if (clTopBar.getHeight() < expandedHeightPx && !isAnimating) {
                animateTopBar(clTopBar.getHeight(), expandedHeightPx);
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
                        
                        // Detect drag start based on threshold
                        if (!isDraggingHeader && Math.abs(deltaY) > 10) {
                            if (deltaY < -10) { // SWIPE UP (Finger Up) -> Collapse
                                // Only collapse if expanded/partly expanded and content is scrollable
                                if (clTopBar.getHeight() > collapsedHeightPx && isContentScrollable()) {
                                    isDraggingHeader = true;
                                }
                            } else if (deltaY > 10) { // SWIPE DOWN (Finger Down) -> Expand
                                // Only expand if collapsed/partly expanded and at start of scroll
                                if (clTopBar.getHeight() < expandedHeightPx && svProfile.getScrollY() <= 0) {
                                    isDraggingHeader = true;
                                }
                            }
                        }

                        if (isDraggingHeader) {
                            // Intercept touches to prevent parent/sibling interference
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            if (getActivity() != null) {
                                ViewGroup vp = getActivity().findViewById(R.id.viewPager);
                                if (vp != null) vp.requestDisallowInterceptTouchEvent(true);
                            }

                            // Update height manually based on drag delta
                            int newHeight = Math.max(collapsedHeightPx, Math.min(expandedHeightPx, (int) (initialHeight + deltaY)));
                            updateHeaderHeight(newHeight);
                            return true; // Stop ScrollView from scrolling during drag
                        }

                        // Rule: If Top Bar is expanded, disable content scrolling entirely to prioritize header interactions
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
                            // Snap to expanded or collapsed state based on threshold
                            int currentHeight = clTopBar.getHeight();
                            if (currentHeight > (expandedHeightPx + collapsedHeightPx) / 2) {
                                animateTopBar(currentHeight, expandedHeightPx);
                            } else {
                                animateTopBar(currentHeight, collapsedHeightPx);
                            }
                            isDraggingHeader = false;
                            return true;
                        }
                        return false;
                }
                return false;
            }
        };

        // Apply swipe listener to all relevant components for universal gesture detection
        rootView.setOnTouchListener(swipeListener);
        svProfile.setOnTouchListener(swipeListener);
        mcvTopBar.setOnTouchListener(swipeListener);
        
        View scrollContent = rootView.findViewById(R.id.ll_scroll_content);
        if (scrollContent != null) scrollContent.setOnTouchListener(swipeListener);
        
        // Apply to all card items in the scroll view
        int[] cardIds = {R.id.mcv_cut_history, R.id.mcv_settings, R.id.mcv_privacy_policy, R.id.mcv_user_agreement, R.id.mcv_about};
        for (int id : cardIds) {
            View card = rootView.findViewById(id);
            if (card != null) card.setOnTouchListener(swipeListener);
        }

        // Disable default scroll change behavior
        svProfile.setOnScrollChangeListener(null);

        // Initial state sync (Start expanded)
        updateHeaderHeight(expandedHeightPx);
    }

    /**
     * Checks if the ScrollView content is taller than the container.
     */
    private boolean isContentScrollable() {
        if (svProfile == null || svProfile.getChildCount() == 0) return false;
        View child = svProfile.getChildAt(0);
        return child.getHeight() > svProfile.getHeight();
    }

    /**
     * Updates the header height and manages the alpha fading of the profile info.
     * @param val The new height in pixels.
     */
    private void updateHeaderHeight(int val) {
        ViewGroup.LayoutParams params = clTopBar.getLayoutParams();
        if (params != null) {
            params.height = val;
            clTopBar.setLayoutParams(params);
        }

        // Calculate fade ratio
        float ratio = (float) (val - collapsedHeightPx) / (expandedHeightPx - collapsedHeightPx);
        mcvProfileHeader.setAlpha(Math.max(0.0f, Math.min(1.0f, ratio)));
        
        // Optimization: Set visibility GONE when fully collapsed to skip rendering
        if (ratio > 0.05f) {
            mcvProfileHeader.setVisibility(View.VISIBLE);
        } else {
            mcvProfileHeader.setVisibility(View.GONE);
        }

        // Disable card clickability when fully expanded to allow interaction with profile header elements
        boolean isFullyExpanded = val >= expandedHeightPx - 2;
        mcvTopBar.setClickable(!isFullyExpanded);
        mcvTopBar.setFocusable(!isFullyExpanded);
    }

    /**
     * Smoothly animates the header between two height states.
     * @param from Start height.
     * @param to End height.
     */
    private void animateTopBar(int from, int to) {
        isAnimating = true;
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(500);
        animator.addUpdateListener(animation -> {
            int val = (Integer) animation.getAnimatedValue();
            updateHeaderHeight(val);
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
