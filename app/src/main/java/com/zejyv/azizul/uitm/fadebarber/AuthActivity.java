package com.zejyv.azizul.uitm.fadebarber;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;

public class AuthActivity extends AppCompatActivity {

    private FrameLayout layoutTabLogin, layoutTabSignup;
    private TextView textTabLogin, textTabSignup;
    private View slidingUnderline;
    private LinearLayout layoutLoginForm, layoutSignupForm;
    private Button btnAuthAction;
    private int currentTab = 0; // 0 for Login, 1 for Signup

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        // Initialize views
        layoutTabLogin = findViewById(R.id.layout_tab_login);
        layoutTabSignup = findViewById(R.id.layout_tab_signup);
        textTabLogin = findViewById(R.id.text_tab_login);
        textTabSignup = findViewById(R.id.text_tab_signup);
        slidingUnderline = findViewById(R.id.view_sliding_underline);
        layoutLoginForm = findViewById(R.id.layout_login_form);
        layoutSignupForm = findViewById(R.id.layout_signup_form);
        btnAuthAction = findViewById(R.id.btn_auth_action);

        setupTabs();
        setupActionButton();
    }

    private void setupTabs() {
        layoutTabLogin.setOnClickListener(v -> {
            currentTab = 0;
            updateTabStyles(0);
            animateUnderline(0);
            showForm(0);
        });

        layoutTabSignup.setOnClickListener(v -> {
            currentTab = 1;
            updateTabStyles(1);
            animateUnderline(1);
            showForm(1);
        });
    }

    private void setupActionButton() {
        btnAuthAction.setOnClickListener(v -> {
            if (currentTab == 1) {
                // SIGN UP -> Go to Customer Space (MainActivity)
                Intent intent = new Intent(AuthActivity.this, MainActivity.class);
                startActivity(intent);
                finish(); // Close AuthActivity so back doesn't return here
            } else {
                // LOGIN -> Reserved for Employee Space
                Toast.makeText(this, "Employee space is under development", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateTabStyles(int position) {
        if (position == 0) {
            // Login On
            layoutTabLogin.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_color));
            layoutTabLogin.setElevation(convertDpToPx());
            layoutTabLogin.setForeground(null);
            textTabLogin.setAlpha(1.0f);

            // Signup Off
            layoutTabSignup.setBackgroundColor(Color.parseColor("#208000"));
            layoutTabSignup.setElevation(0);
            layoutTabSignup.setForeground(ContextCompat.getDrawable(this, R.drawable.bg_inner_shadow));
            textTabSignup.setAlpha(0.6f);
        } else {
            // Login Off
            layoutTabLogin.setBackgroundColor(Color.parseColor("#208000"));
            layoutTabLogin.setElevation(0);
            layoutTabLogin.setForeground(ContextCompat.getDrawable(this, R.drawable.bg_inner_shadow));
            textTabLogin.setAlpha(0.6f);

            // Signup On
            layoutTabSignup.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_color));
            layoutTabSignup.setElevation(convertDpToPx());
            layoutTabSignup.setForeground(null);
            textTabSignup.setAlpha(1.0f);
        }
    }

    private void animateUnderline(int position) {
        slidingUnderline.animate()
                .translationX(position * slidingUnderline.getWidth())
                .setDuration(250)
                .start();
    }

    private void showForm(int position) {
        if (position == 0) {
            layoutLoginForm.setVisibility(View.VISIBLE);
            layoutSignupForm.setVisibility(View.GONE);
            btnAuthAction.setText("LOGIN");
        } else {
            layoutLoginForm.setVisibility(View.GONE);
            layoutSignupForm.setVisibility(View.VISIBLE);
            btnAuthAction.setText("SIGN UP");
        }
    }

    private float convertDpToPx() {
        return 6 * getResources().getDisplayMetrics().density;
    }
}
