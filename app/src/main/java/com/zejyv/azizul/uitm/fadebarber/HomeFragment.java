package com.zejyv.azizul.uitm.fadebarber;

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
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.Calendar;

public class HomeFragment extends Fragment {

    private Handler handler;
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
