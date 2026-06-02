package com.zejyv.azizul.uitm.fadebarber;

import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.card.MaterialCardView;
import java.util.Calendar;
import java.util.Random;

/**
 * HomeFragment: The main dashboard for the Fade bARber app.
 * Features dynamic UI updates based on time of day, AI-generated jabs/roasts,
 * and a collapsible top bar that responds to scrolling.
 */
public class HomeFragment extends Fragment {

    private Handler handler;
    private MaterialCardView mcvTopBar;
    private ScrollView svHomeContent;
    private TextView tvAiJab;
    
    // UI State flags
    private boolean isExpanded = true;
    private boolean isAnimating = false;
    private boolean isLockedAtMin = false;
    
    // AI Jab related state
    private static String sessionAiJab = null;
    private long lastJabTime = 0;
    private static final long JAB_COOLDOWN = 30000; // 30 seconds cooldown for AI jab generation
    private Runnable typingRunnable;

    /**
     * Runnable to periodically update the time-based theme (e.g., background and greeting).
     */
    private final Runnable updateTimeThemeRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimeTheme();
            if (handler != null) {
                handler.postDelayed(this, 60000); // Update every minute
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        handler = new Handler(Looper.getMainLooper());

        // Initialize UI components
        mcvTopBar = view.findViewById(R.id.mcv_top_bar);
        svHomeContent = view.findViewById(R.id.sv_home_content);
        tvAiJab = view.findViewById(R.id.tv_ai_jab);
        LinearLayout llTextContainer = view.findViewById(R.id.ll_top_bar_text_container);

        // Configure smooth layout transitions for the top bar's text container
        if (llTextContainer != null) {
            LayoutTransition transition = llTextContainer.getLayoutTransition();
            if (transition == null) {
                transition = new LayoutTransition();
                llTextContainer.setLayoutTransition(transition);
            }
            transition.enableTransitionType(LayoutTransition.CHANGING);
            transition.setDuration(300); // Duration for text movement during resize
        }

        // Initialize AI jab if not already present in the current session
        if (sessionAiJab == null) {
            Calendar calendar = Calendar.getInstance();
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            boolean isOpen = hour >= 10 && hour < 24; // Shop hours: 10 AM to 12 AM
            updateAiJab(isOpen, false);
        }

        setupScrollingLogic();
        setupClickLogic();
    }

    /**
     * Configures the scroll listener to dynamically resize the top bar and hide/show the AI jab.
     */
    private void setupScrollingLogic() {
        svHomeContent.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (isAnimating || !isAdded()) return;

            int scrollY = svHomeContent.getScrollY();
            View child = svHomeContent.getChildAt(0);
            if (child == null) return;
            
            int maxScroll = child.getHeight() - svHomeContent.getHeight();
            int threshold = maxScroll / 2; // Threshold for full collapse

            if (threshold <= 0) return;

            float ratio = (float) scrollY / threshold;
            if (ratio > 1f) ratio = 1f;

            int startHeight = dpToPx(100);
            int endHeight = dpToPx(70);
            
            if (!isLockedAtMin) {
                // Interpolate height between 100dp and 70dp
                int currentHeight = (int) (startHeight - (startHeight - endHeight) * ratio);

                if (currentHeight <= endHeight) {
                    currentHeight = endHeight;
                    isLockedAtMin = true;
                }

                ViewGroup.LayoutParams params = mcvTopBar.getLayoutParams();
                params.height = currentHeight;
                mcvTopBar.setLayoutParams(params);

                // Gradually hide AI jab based on scroll progress
                float jabAlpha = 1f - ratio;
                tvAiJab.setAlpha(jabAlpha);
                if (jabAlpha < 0.1f) {
                    tvAiJab.setVisibility(View.GONE);
                    isExpanded = false;
                } else {
                    tvAiJab.setVisibility(View.VISIBLE);
                    isExpanded = true;
                }
            } else {
                // Maintain collapsed state even when scrolling beyond the threshold
                ViewGroup.LayoutParams params = mcvTopBar.getLayoutParams();
                if (params.height != endHeight) {
                    params.height = endHeight;
                    mcvTopBar.setLayoutParams(params);
                    tvAiJab.setVisibility(View.GONE);
                    isExpanded = false;
                }
            }
        });
    }

    /**
     * Handles clicks on the top bar to manually expand it and refresh the AI jab.
     */
    private void setupClickLogic() {
        mcvTopBar.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastJabTime < JAB_COOLDOWN) {
                // Show cooldown message if clicked too frequently
                long timeLeft = (JAB_COOLDOWN - (currentTime - lastJabTime)) / 1000;
                String message = getString(R.string.jab_cooldown, (int) timeLeft);
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            } else {
                lastJabTime = currentTime;
                Calendar calendar = Calendar.getInstance();
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                boolean isOpen = hour >= 10 && hour < 24;
                updateAiJab(isOpen, true); // Force a new jab on click
            }

            // Expand top bar if it's currently collapsed
            if (!isExpanded && !isAnimating) {
                isLockedAtMin = false;
                animateTopBar(dpToPx(70), dpToPx(100), 500);

                // Scroll back to top for better visibility of expanded content
                if (svHomeContent != null) {
                    svHomeContent.smoothScrollTo(0, 0);
                }
            }
        });
    }

    /**
     * Animates the height change of the top bar.
     */
    private void animateTopBar(int from, int to, int duration) {
        isAnimating = true;
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(duration);
        animator.addUpdateListener(animation -> {
            int val = (Integer) animation.getAnimatedValue();
            ViewGroup.LayoutParams params = mcvTopBar.getLayoutParams();
            params.height = val;
            mcvTopBar.setLayoutParams(params);

            float progress = (float) (val - from) / (to - from);
            tvAiJab.setVisibility(View.VISIBLE);
            tvAiJab.setAlpha(progress);
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                isAnimating = false;
                isExpanded = true;
            }
        });
        animator.start();
    }

    /**
     * Converts DP units to pixels based on the device's screen density.
     */
    private int dpToPx(int dp) {
        if (!isAdded()) return dp * 3; // Approximate fallback for detached state
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    /**
     * Fetches or updates the AI jab (roast).
     * Currently uses fallback jabs to save API usage, but includes logic for Gemini AI.
     */
    private void updateAiJab(boolean isOpen, boolean forceNew) {
        if (tvAiJab == null) return;

        if (sessionAiJab != null && !forceNew) {
            startTypingAnimation(sessionAiJab);
            return;
        }

        // Use pre-defined fallback jabs for multilingual support and stability
        useFallbackJab(isOpen);
    }
    
    /*
        // Use API Key from local.properties via BuildConfig
        String apiKey = BuildConfig.GEMINI_API_KEY;
        Log.d("HomeFragment", "API Key length: " + apiKey.length());

        GenerativeModel gm = new GenerativeModel("gemini-3.1-flash-lite", apiKey);
        GenerativeModelFutures model = GenerativeModelFutures.from(gm);

        String shopStatus = isOpen ? "OPEN" : "CLOSED";
        String prompt = "Generate a funny, short (max 20 words) jab/roast for a user at a barber shop app. " +
                "Context: The shop is currently " + shopStatus + ". The user is looking at their home screen. " +
                "Make it personal and cheeky about their potentially messy hair or need for a trim. " +
                (forceNew ? "Make it different from the previous response, something more hurtful and biting but playful." : "");

        Content content = new Content.Builder().addText(prompt).build();
        Executor executor = Executors.newSingleThreadExecutor();
        ListenableFuture<GenerateContentResponse> response = model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<>() {
            @Override
            public void onSuccess(@NonNull GenerateContentResponse result) {
                String jab = result.getText();
                Log.d("HomeFragment", "Gemini AI Jab generated: " + jab);
                if (jab != null && isAdded()) {
                    handler.post(() -> {
                        sessionAiJab = jab.trim();
                        startTypingAnimation(sessionAiJab);
                    });
                }
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Log.e("HomeFragment", "Gemini API Error: " + t.getMessage(), t);
                // Fallback to local jabs if API fails
                handler.post(() -> useFallbackJab(isOpen));
            }
        }, executor);
        */
    
    /**
     * Starts the typewriter effect for displaying the jab text.
     */
    private void startTypingAnimation(final String text) {
        if (handler == null || tvAiJab == null) return;

        if (typingRunnable != null) {
            handler.removeCallbacks(typingRunnable);
        }

        final String oldText = tvAiJab.getText().toString();
        // If there's already text, backspace it first
        if (!oldText.isEmpty() && !oldText.equals(text)) {
            startBackspaceAnimation(oldText, text);
            return;
        }

        final int typeDelay = 50; // Delay per character in milliseconds

        typingRunnable = new Runnable() {
            int index = 0;
            boolean cursorVisible = true;

            @Override
            public void run() {
                if (index <= text.length()) {
                    String displayed = text.substring(0, index) + (cursorVisible ? " |" : "  ");
                    tvAiJab.setText(displayed);
                    index++;
                    cursorVisible = !cursorVisible;
                    handler.postDelayed(this, typeDelay);
                } else {
                    tvAiJab.setText(text); // Final text display
                }
            }
        };
        handler.post(typingRunnable);
    }

    /**
     * Backspace effect before typing a new message.
     */
    private void startBackspaceAnimation(final String oldText, final String newText) {
        final int totalDuration = 1000;
        final int charCount = oldText.length();
        final int delay = charCount > 0 ? totalDuration / charCount : 0;

        typingRunnable = new Runnable() {
            int index = charCount;

            @Override
            public void run() {
                if (index > 0) {
                    index--;
                    tvAiJab.setText(oldText.substring(0, index) + " |");
                    handler.postDelayed(this, delay);
                } else {
                    tvAiJab.setText("");
                    startTypingAnimation(newText);
                }
            }
        };
        handler.post(typingRunnable);
    }

    /**
     * Selects a random jab from local resources based on the shop's open/closed status.
     */
    private void useFallbackJab(boolean isOpen) {
        String[] fallbackJabs;
        if (isOpen) {
            fallbackJabs = new String[]{
                    getString(R.string.jab_open_1),
                    getString(R.string.jab_open_2)
            };
        } else {
            fallbackJabs = new String[]{
                    getString(R.string.jab_closed_1),
                    getString(R.string.jab_closed_2)
            };
        }
        sessionAiJab = fallbackJabs[new Random().nextInt(fallbackJabs.length)];
        startTypingAnimation(sessionAiJab);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (handler != null) {
            handler.post(updateTimeThemeRunnable);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (handler != null) {
            handler.removeCallbacks(updateTimeThemeRunnable);
        }
    }

    /**
     * Updates UI elements based on the current time of day (Theme, background image, greeting).
     */
    private void updateTimeTheme() {
        View view = getView();
        if (view == null || !isAdded()) return;

        ImageView ivBg = view.findViewById(R.id.iv_time_bg);
        TextView tvTimeName = view.findViewById(R.id.tv_time_name);
        TextView tvAmPm = view.findViewById(R.id.tv_ampm_home);
        TextView tvShopStatus = view.findViewById(R.id.tv_shop_status);

        if (ivBg == null || tvTimeName == null) return;

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        // Update AM/PM display
        if (tvAmPm != null) {
            String amPm = calendar.get(Calendar.AM_PM) == Calendar.AM ? 
                    getString(R.string.am_label) : getString(R.string.pm_label);
            tvAmPm.setText(amPm);
        }

        // Update Shop Open/Closed status visual
        if (tvShopStatus != null) {
            boolean isOpen = hour >= 10 && hour < 24;
            String status = isOpen ? getString(R.string.shop_status_open) : getString(R.string.shop_status_closed);
            int color = isOpen ? Color.parseColor("#05B109") : Color.RED;

            SpannableStringBuilder builder = new SpannableStringBuilder(getString(R.string.shop_status_prefix));
            builder.append(" "); // Explicitly add a space to prevent trimming issues
            int start = builder.length();
            builder.append(status);
            builder.setSpan(new ForegroundColorSpan(color), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvShopStatus.setText(builder);
        }

        String timeName;
        int bgResId;

        // Determine theme based on hour of the day
        if (hour < 6) {
            timeName = getString(R.string.time_midnight);
            bgResId = R.drawable.bg_time_midnight;
        } else if (hour < 8) {
            timeName = getString(R.string.time_sunrise);
            bgResId = R.drawable.bg_time_sunrise;
        } else if (hour < 12) {
            timeName = getString(R.string.time_morning);
            bgResId = R.drawable.bg_time_morning;
        } else if (hour < 14) {
            timeName = getString(R.string.time_noon);
            bgResId = R.drawable.bg_time_noon;
        } else if (hour < 18) {
            timeName = getString(R.string.time_evening);
            bgResId = R.drawable.bg_time_evening;
        } else if (hour < 20) {
            timeName = getString(R.string.time_sunset);
            bgResId = R.drawable.bg_time_sunset;
        } else {
            timeName = getString(R.string.time_night);
            bgResId = R.drawable.bg_time_night;
        }

        tvTimeName.setText(timeName);
        ivBg.setImageResource(bgResId);
    }
}
