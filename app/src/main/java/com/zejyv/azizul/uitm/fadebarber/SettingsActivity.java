package com.zejyv.azizul.uitm.fadebarber;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.constraintlayout.widget.ConstraintLayout;

public class SettingsActivity extends AppCompatActivity {

    private boolean isEnglish = true;
    private View vIndicator;
    private TextView tvBM, tvEN;
    private int primaryColor, whiteColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        primaryColor = ContextCompat.getColor(this, R.color.primary_color);
        whiteColor = ContextCompat.getColor(this, R.color.white);

        ImageView ivBack = findViewById(R.id.iv_back_settings);
        ivBack.setOnClickListener(v -> finish());

        View layoutToggle = findViewById(R.id.layout_toggle_root);
        vIndicator = findViewById(R.id.v_toggle_indicator);
        tvBM = findViewById(R.id.tv_toggle_bm);
        tvEN = findViewById(R.id.tv_toggle_en);

        layoutToggle.setOnClickListener(v -> {
            isEnglish = !isEnglish;
            updateToggleUI(true);
        });
        
        // Initialize state after layout
        vIndicator.post(() -> updateToggleUI(false));
    }

    private void updateToggleUI(boolean animate) {
        float translationX = isEnglish ? vIndicator.getWidth() : 0;
        
        if (animate) {
            vIndicator.animate().translationX(translationX).setDuration(200).start();
        } else {
            vIndicator.setTranslationX(translationX);
        }

        if (isEnglish) {
            tvEN.setTextColor(whiteColor);
            tvBM.setTextColor(primaryColor);
        } else {
            tvBM.setTextColor(whiteColor);
            tvEN.setTextColor(primaryColor);
        }
    }
}
