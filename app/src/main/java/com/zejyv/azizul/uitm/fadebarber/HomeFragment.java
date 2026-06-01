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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.card.MaterialCardView;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Calendar;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    private Handler handler;
    private MaterialCardView mcvTopBar;
    private ScrollView svHomeContent;
    private TextView tvWelcome, tvAiJab;
    private ImageView ivProfile;
    private boolean isExpanded = true;
    private boolean isAnimating = false;
    private boolean isLockedAtMin = false;
    private static String sessionAiJab = null;
    private long lastJabTime = 0;
    private static final long JAB_COOLDOWN = 30000; // 30 seconds
    private Runnable typingRunnable;
    private boolean isTyping = false;

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
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        handler = new Handler(Looper.getMainLooper());

        mcvTopBar = view.findViewById(R.id.mcv_top_bar);
        svHomeContent = view.findViewById(R.id.sv_home_content);
        tvWelcome = view.findViewById(R.id.tv_welcome_home);
        tvAiJab = view.findViewById(R.id.tv_ai_jab);
        ivProfile = view.findViewById(R.id.iv_profile);
        LinearLayout llTextContainer = view.findViewById(R.id.ll_top_bar_text_container);

        // Enable smooth layout transitions for text container
        if (llTextContainer != null) {
            LayoutTransition transition = llTextContainer.getLayoutTransition();
            if (transition == null) {
                transition = new LayoutTransition();
                llTextContainer.setLayoutTransition(transition);
            }
            transition.enableTransitionType(LayoutTransition.CHANGING);
            transition.setDuration(300); // Smooth movement
        }

        // Delay initial animations by 1 second
        handler.postDelayed(this::setupInitialAnimations, 1000);

        // Pre-fetch AI jab on startup if not already fetched
        if (sessionAiJab == null) {
            Calendar calendar = Calendar.getInstance();
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            boolean isOpen = hour >= 10 && hour < 24;
            updateAiJab(isOpen, false);
        }
        
        setupScrollingLogic();
        setupClickLogic();
    }

    private void setupInitialAnimations() {
        // Fade slide in from top for welcome text
        AnimationSet welcomeAnim = new AnimationSet(true);
        welcomeAnim.addAnimation(new AlphaAnimation(0f, 1f));
        welcomeAnim.addAnimation(new TranslateAnimation(0, 0, -50, 0));
        welcomeAnim.setDuration(1000);
        tvWelcome.startAnimation(welcomeAnim);

        // Same animation for AI jab
        AnimationSet jabAnim = new AnimationSet(true);
        jabAnim.addAnimation(new AlphaAnimation(0f, 1f));
        jabAnim.addAnimation(new TranslateAnimation(0, 0, -50, 0));
        jabAnim.setDuration(1000);
        tvAiJab.startAnimation(jabAnim);

        // Fade in for profile image
        AlphaAnimation profileAnim = new AlphaAnimation(0f, 1f);
        profileAnim.setDuration(1200);
        ivProfile.startAnimation(profileAnim);
    }

    private void setupScrollingLogic() {
        svHomeContent.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (isAnimating || !isAdded()) return;

            int scrollY = svHomeContent.getScrollY();
            View child = svHomeContent.getChildAt(0);
            if (child == null) return;
            
            int maxScroll = child.getHeight() - svHomeContent.getHeight();
            int threshold = maxScroll / 2;

            if (threshold <= 0) return;

            float ratio = (float) scrollY / threshold;
            if (ratio > 1f) ratio = 1f;

            int startHeight = dpToPx(100);
            int endHeight = dpToPx(70);
            
            if (!isLockedAtMin) {
                int currentHeight = (int) (startHeight - (startHeight - endHeight) * ratio);
                
                if (currentHeight <= endHeight) {
                    currentHeight = endHeight;
                    isLockedAtMin = true;
                }

                ViewGroup.LayoutParams params = mcvTopBar.getLayoutParams();
                params.height = currentHeight;
                mcvTopBar.setLayoutParams(params);

                // Gradually hide AI jab
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
                // Keep it at 70dp even when scrolling back up
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

    private void setupClickLogic() {
        mcvTopBar.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastJabTime < JAB_COOLDOWN) {
                long timeLeft = (JAB_COOLDOWN - (currentTime - lastJabTime)) / 1000;
                Toast.makeText(getContext(), "Jab cooldown: " + timeLeft + "s remaining", Toast.LENGTH_SHORT).show();
            } else {
                lastJabTime = currentTime;
                Calendar calendar = Calendar.getInstance();
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                boolean isOpen = hour >= 10 && hour < 24;
                updateAiJab(isOpen, true); // Force new jab on click
            }

            if (!isExpanded && !isAnimating) {
                isLockedAtMin = false; // Reset lock on manual expand
                animateTopBar(dpToPx(70), dpToPx(100), 500);
                
                // Auto scroll to top on expand
                if (svHomeContent != null) {
                    svHomeContent.smoothScrollTo(0, 0);
                }
            }
        });
    }

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

    private int dpToPx(int dp) {
        if (!isAdded()) return dp * 3; // Fallback
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void updateAiJab(boolean isOpen, boolean forceNew) {
        if (tvAiJab == null) return;

        if (sessionAiJab != null && !forceNew) {
            startTypingAnimation(sessionAiJab);
            return;
        }

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
    }

    private void startTypingAnimation(final String text) {
        if (handler == null || tvAiJab == null) return;
        
        if (typingRunnable != null) {
            handler.removeCallbacks(typingRunnable);
        }

        final String oldText = tvAiJab.getText().toString();
        if (!oldText.isEmpty() && !oldText.equals(text)) {
            startBackspaceAnimation(oldText, text);
            return;
        }

        isTyping = true;
        final int typeDelay = 50; // ms per char

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
                    isTyping = false;
                    tvAiJab.setText(text); // Final text without cursor
                }
            }
        };
        handler.post(typingRunnable);
    }

    private void startBackspaceAnimation(final String oldText, final String newText) {
        final int totalDuration = 1000; // 1 second total
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

    private void useFallbackJab(boolean isOpen) {
        String[] fallbackJabs = isOpen ? new String[]{
                "Shop is OPEN. You're READY. Stop looking at the mirror and come over!",
                "Doors are open! Your hair is screaming for help. Don't leave it hanging."
        } : new String[]{
                "We're CLOSED. Even your hair needs some sleep. See you tomorrow!",
                "Shop's shut! That mess on your head will have to wait until we open."
        };
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

        // Update AM/PM with dots
        if (tvAmPm != null) {
            String amPm = calendar.get(Calendar.AM_PM) == Calendar.AM ? "A.M." : "P.M.";
            tvAmPm.setText(amPm);
        }

        // Shop Status Logic: Open 10 AM to 12 AM (midnight)
        if (tvShopStatus != null) {
            boolean isOpen = hour >= 10 && hour < 24;
            // updateAiJab(isOpen, false); // Removed from here to prevent constant updates
            String status = isOpen ? "OPEN" : "CLOSED";
            int color = isOpen ? Color.parseColor("#05B109") : Color.RED;

            SpannableStringBuilder builder = new SpannableStringBuilder("The Shop is now ");
            int start = builder.length();
            builder.append(status);
            builder.setSpan(new ForegroundColorSpan(color), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvShopStatus.setText(builder);
        }

        String timeName;
        int bgResId;

        if (hour < 6) {
            timeName = "Midnight";
            bgResId = R.drawable.bg_time_midnight;
        } else if (hour < 8) {
            timeName = "Sunrise";
            bgResId = R.drawable.bg_time_sunrise;
        } else if (hour < 12) {
            timeName = "Morning";
            bgResId = R.drawable.bg_time_morning;
        } else if (hour < 14) {
            timeName = "Noon";
            bgResId = R.drawable.bg_time_noon;
        } else if (hour < 18) {
            timeName = "Evening";
            bgResId = R.drawable.bg_time_evening;
        } else if (hour < 20) {
            timeName = "Sunset";
            bgResId = R.drawable.bg_time_sunset;
        } else {
            timeName = "Night";
            bgResId = R.drawable.bg_time_night;
        }

        tvTimeName.setText(timeName);
        ivBg.setImageResource(bgResId);
    }
}
