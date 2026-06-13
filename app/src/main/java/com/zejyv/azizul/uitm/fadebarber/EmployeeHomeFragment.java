package com.zejyv.azizul.uitm.fadebarber;

import android.animation.LayoutTransition;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.material.card.MaterialCardView;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Calendar;
import java.util.Random;

/**
 * EmployeeHomeFragment: Dashboard for barbers/employees.
 * Features time-based themes, booking counters, and customer management.
 */
public class EmployeeHomeFragment extends Fragment {

    private Handler handler;
    private MaterialCardView mcvTopBar;
    private ScrollView svHomeContent;
    private TextView tvAiJab, tvWelcome, tvBookingCounter, tvTotalUpcoming, tvShopStatus;
    
    private String sessionAiJab = null;
    private Runnable typingRunnable;
    private int completedBookings = 0;
    private int totalBookings = 7;

    private final Runnable updateTimeThemeRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimeTheme();
            if (handler != null) {
                handler.postDelayed(this, 60000);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_employee_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        handler = new Handler(Looper.getMainLooper());

        initializeViews(view);
        setupClickLogic(view);
        populateWelcomeMessage();
        
        if (sessionAiJab == null) {
            updateAiJab();
        }
    }

    private void initializeViews(View view) {
        mcvTopBar = view.findViewById(R.id.mcv_employee_top_bar);
        svHomeContent = view.findViewById(R.id.sv_employee_home_content);
        tvAiJab = view.findViewById(R.id.tv_employee_ai_jab);
        tvWelcome = view.findViewById(R.id.tv_welcome_employee);
        tvBookingCounter = view.findViewById(R.id.tv_booking_counter);
        tvTotalUpcoming = view.findViewById(R.id.tv_total_upcoming);
        tvShopStatus = view.findViewById(R.id.tv_shop_status_employee);
        
        LinearLayout llTextContainer = view.findViewById(R.id.ll_employee_top_text_container);
        if (llTextContainer != null) {
            LayoutTransition transition = new LayoutTransition();
            llTextContainer.setLayoutTransition(transition);
            transition.enableTransitionType(LayoutTransition.CHANGING);
        }

        // Initialize counter
        updateBookingCounter();
        tvTotalUpcoming.setText(getString(R.string.total_bookings_today_label, totalBookings)); // Mock value
    }

    private void updateBookingCounter() {
        if (tvBookingCounter != null) {
            tvBookingCounter.setText((completedBookings + 1) + "/" + totalBookings);
        }
    }

    private void populateWelcomeMessage() {
        try {
            MasterKey masterKey = new MasterKey.Builder(requireContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            android.content.SharedPreferences prefs = EncryptedSharedPreferences.create(
                    requireContext(),
                    "secret_shared_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            String name = prefs.getString("name", "Barber");
            String welcomeTemplate = getString(R.string.welcome_employee);
            tvWelcome.setText(welcomeTemplate.replace("(Barber)", name));
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    private void setupClickLogic(View view) {
        view.findViewById(R.id.btn_start_session).setOnClickListener(v -> {
            if (completedBookings < totalBookings - 1) {
                completedBookings++;
                updateBookingCounter();
                Toast.makeText(getContext(), "Session Started!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "All bookings completed for today!", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.btn_no_show).setOnClickListener(v -> {
            Toast.makeText(getContext(), "Marked as No-Show", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btn_call_customer).setOnClickListener(v -> {
            Toast.makeText(getContext(), "Calling customer...", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btn_edit_booking_employee).setOnClickListener(v -> {
            Toast.makeText(getContext(), "Opening Edit Booking...", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btn_call_upcoming_1).setOnClickListener(v -> Toast.makeText(getContext(), "Calling Haziq...", Toast.LENGTH_SHORT).show());
        view.findViewById(R.id.btn_call_upcoming_2).setOnClickListener(v -> Toast.makeText(getContext(), "Calling Irfan...", Toast.LENGTH_SHORT).show());

        mcvTopBar.setOnClickListener(v -> {
            updateAiJab(); // Simple refresh on click
            if (svHomeContent != null) {
                svHomeContent.smoothScrollTo(0, 0);
            }
        });
    }

    private void updateAiJab() {
        String[] jabs = {getString(R.string.employee_jab_1), getString(R.string.employee_jab_2)};
        sessionAiJab = jabs[new Random().nextInt(jabs.length)];
        startTypingAnimation(sessionAiJab);
    }

    private void startTypingAnimation(final String text) {
        if (handler == null || tvAiJab == null) return;
        if (typingRunnable != null) handler.removeCallbacks(typingRunnable);

        typingRunnable = new Runnable() {
            int index = 0;
            @Override
            public void run() {
                if (index <= text.length()) {
                    tvAiJab.setText(text.substring(0, index) + " |");
                    index++;
                    handler.postDelayed(this, 50);
                } else {
                    tvAiJab.setText(text);
                }
            }
        };
        handler.post(typingRunnable);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (handler != null) handler.post(updateTimeThemeRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (handler != null) handler.removeCallbacks(updateTimeThemeRunnable);
    }

    private void updateTimeTheme() {
        View view = getView();
        if (view == null || !isAdded()) return;

        ImageView ivBg = view.findViewById(R.id.iv_time_bg_employee);
        TextView tvTimeName = view.findViewById(R.id.tv_time_name_employee);
        TextView tvAmPm = view.findViewById(R.id.tv_ampm_employee);
        TextView tvShopStatus = view.findViewById(R.id.tv_shop_status_employee);

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        if (tvAmPm != null) {
            tvAmPm.setText(calendar.get(Calendar.AM_PM) == Calendar.AM ? 
                    getString(R.string.am_label) : getString(R.string.pm_label));
        }

        if (tvShopStatus != null) {
            String shiftStatus;
            int statusColor;

            if (hour >= 9 && hour < 10) {
                shiftStatus = getString(R.string.shift_about_to_start);
                statusColor = android.graphics.Color.parseColor("#FFBD00"); // Yellow/Alert
            } else if ((hour >= 10 && hour < 13) || (hour >= 14 && hour < 20)) {
                shiftStatus = getString(R.string.shift_starting);
                statusColor = android.graphics.Color.parseColor("#05B109"); // Green
            } else if (hour >= 13 && hour < 14) {
                shiftStatus = getString(R.string.shift_taking_a_break);
                statusColor = android.graphics.Color.parseColor("#004CA2"); // Blue/Info
            } else if (hour >= 20 && hour < 22) {
                shiftStatus = getString(R.string.shift_near_the_end);
                statusColor = android.graphics.Color.parseColor("#FFBD00"); // Yellow
            } else {
                shiftStatus = getString(R.string.shift_over);
                statusColor = android.graphics.Color.RED;
            }

            android.text.SpannableStringBuilder builder = new android.text.SpannableStringBuilder(getString(R.string.shift_status_prefix));
            builder.append(" ");
            int start = builder.length();
            builder.append(shiftStatus);
            builder.setSpan(new android.text.style.ForegroundColorSpan(statusColor), start, builder.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, builder.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvShopStatus.setText(builder);
        }

        String timeName;
        int bgResId;

        if (hour < 6) { timeName = getString(R.string.time_midnight); bgResId = R.drawable.bg_time_midnight; }
        else if (hour < 8) { timeName = getString(R.string.time_sunrise); bgResId = R.drawable.bg_time_sunrise; }
        else if (hour < 12) { timeName = getString(R.string.time_morning); bgResId = R.drawable.bg_time_morning; }
        else if (hour < 14) { timeName = getString(R.string.time_noon); bgResId = R.drawable.bg_time_noon; }
        else if (hour < 18) { timeName = getString(R.string.time_evening); bgResId = R.drawable.bg_time_evening; }
        else if (hour < 20) { timeName = getString(R.string.time_sunset); bgResId = R.drawable.bg_time_sunset; }
        else { timeName = getString(R.string.time_night); bgResId = R.drawable.bg_time_night; }

        if (tvTimeName != null) tvTimeName.setText(timeName);
        if (ivBg != null) ivBg.setImageResource(bgResId);
    }
}
