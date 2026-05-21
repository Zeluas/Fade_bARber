package com.zejyv.azizul.uitm.fadebarber;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.splashscreen.SplashScreen;

public class AuthActivity extends AppCompatActivity {

    private enum AuthMode { LOGIN, SIGNUP }
    private AuthMode currentMode = AuthMode.LOGIN;
    private int signupStep = 1;

    private TextView tvFormTitle, tvStepNumber;
    private View llStepIndicator;
    private LinearLayout llLoginStep, llSignupStep1, llSignupStep2, llSignupStep3, llSignupSuccess, llAuthHeader;
    private Button btnAuthAction, btnSwitchAuthMode;
    private ImageView ivAuthBack;
    private View mcvAuthContainer;

    private String prevTitle = "Login";
    private String prevActionText = "LOGIN";
    private int prevStep = 1;
    private AuthMode prevMode = AuthMode.LOGIN;
    
    // Fields
    private EditText etLoginEmail, etLoginPassword;
    private EditText etSignupEmail, etSignupUsername, etSignupName, etSignupPhone, etSignupPassword, etSignupConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        // Initialize views
        tvFormTitle = findViewById(R.id.tv_form_title);
        tvStepNumber = findViewById(R.id.tv_step_number);
        llStepIndicator = findViewById(R.id.ll_step_indicator);
        llLoginStep = findViewById(R.id.ll_login_step);
        llSignupStep1 = findViewById(R.id.ll_signup_step_1);
        llSignupStep2 = findViewById(R.id.ll_signup_step_2);
        llSignupStep3 = findViewById(R.id.ll_signup_step_3);
        llSignupSuccess = findViewById(R.id.ll_signup_success);
        llAuthHeader = findViewById(R.id.ll_auth_header);
        mcvAuthContainer = findViewById(R.id.mcv_auth_container);
        
        btnAuthAction = findViewById(R.id.btn_auth_action);
        btnSwitchAuthMode = findViewById(R.id.btn_switch_auth_mode);
        ivAuthBack = findViewById(R.id.iv_auth_back);

        etLoginEmail = findViewById(R.id.et_login_email);
        etLoginPassword = findViewById(R.id.et_login_password);
        etSignupEmail = findViewById(R.id.et_signup_email);
        etSignupUsername = findViewById(R.id.et_signup_username);
        etSignupName = findViewById(R.id.et_signup_name);
        etSignupPhone = findViewById(R.id.et_signup_phone);
        etSignupPassword = findViewById(R.id.et_signup_password);
        etSignupConfirm = findViewById(R.id.et_signup_confirm);

        setupListeners();
        setupImeActions();
        updateUI(false);
    }

    private void setupListeners() {
        btnSwitchAuthMode.setOnClickListener(v -> {
            if (currentMode == AuthMode.LOGIN) {
                currentMode = AuthMode.SIGNUP;
                signupStep = 1;
            } else {
                currentMode = AuthMode.LOGIN;
            }
            updateUI(true);
        });

        btnAuthAction.setOnClickListener(v -> {
            if (currentMode == AuthMode.LOGIN) {
                handleLogin();
            } else {
                handleSignupNext();
            }
        });

        ivAuthBack.setOnClickListener(v -> handleBackStep());
    }

    private void setupImeActions() {
        etLoginPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleLogin();
                return true;
            }
            return false;
        });

        etSignupUsername.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleSignupNext();
                return true;
            }
            return false;
        });

        etSignupPhone.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleSignupNext();
                return true;
            }
            return false;
        });

        etSignupConfirm.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleSignupNext();
                return true;
            }
            return false;
        });
    }

    private void handleBackStep() {
        if (currentMode == AuthMode.SIGNUP) {
            if (signupStep > 1) {
                signupStep--;
                updateUI(true);
            } else {
                currentMode = AuthMode.LOGIN;
                updateUI(true);
            }
        }
    }

    private void handleLogin() {
        String email = etLoginEmail.getText().toString().trim();
        if (!email.isEmpty()) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSignupNext() {
        switch (signupStep) {
            case 1:
                if (validateStep1()) { signupStep = 2; updateUI(true); etSignupName.requestFocus(); }
                break;
            case 2:
                if (validateNameAndPhone()) { signupStep = 3; updateUI(true); etSignupPassword.requestFocus(); }
                break;
            case 3:
                if (validatePasswords()) { signupStep = 4; updateUI(true); }
                break;
            case 4:
                currentMode = AuthMode.LOGIN;
                updateUI(true);
                break;
        }
    }

    private void updateUI(boolean animate) {
        View targetView;
        String title;
        boolean showIndicator = false;
        String actionText;
        String switchText;
        
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) btnAuthAction.getLayoutParams();

        if (currentMode == AuthMode.LOGIN) {
            targetView = llLoginStep;
            title = "Login";
            actionText = "LOGIN";
            switchText = "Create an account";
            params.topMargin = 0;
        } else {
            title = "Sign Up";
            switchText = "Already have an account? Login";
            params.topMargin = (int) (30 * getResources().getDisplayMetrics().density);

            switch (signupStep) {
                case 1:
                    targetView = llSignupStep1;
                    showIndicator = true;
                    actionText = "NEXT";
                    break;
                case 2:
                    targetView = llSignupStep2;
                    showIndicator = true;
                    actionText = "NEXT";
                    break;
                case 3:
                    targetView = llSignupStep3;
                    showIndicator = true;
                    actionText = "CONFIRM";
                    break;
                default:
                    targetView = llSignupSuccess;
                    title = "Success";
                    actionText = "BACK TO LOGIN";
                    break;
            }
        }
        btnAuthAction.setLayoutParams(params);

        if (animate) {
            animateComplexTransition(targetView, title, showIndicator, actionText, switchText);
        } else {
            tvFormTitle.setText(title);
            tvStepNumber.setText(String.valueOf(signupStep));
            llStepIndicator.setVisibility(showIndicator ? View.VISIBLE : View.GONE);
            btnAuthAction.setText(actionText);
            btnSwitchAuthMode.setText(switchText);
            updateFixedVisibilities();
            
            llLoginStep.setVisibility(View.GONE);
            llSignupStep1.setVisibility(View.GONE);
            llSignupStep2.setVisibility(View.GONE);
            llSignupStep3.setVisibility(View.GONE);
            llSignupSuccess.setVisibility(View.GONE);
            llAuthHeader.setVisibility(currentMode == AuthMode.LOGIN ? View.VISIBLE : View.GONE);
            
            targetView.setVisibility(View.VISIBLE);
            targetView.setAlpha(1f);
            targetView.setTranslationX(0f);
        }
        prevTitle = title;
        prevActionText = actionText;
        prevStep = signupStep;
        prevMode = currentMode;
    }

    private void updateFixedVisibilities() {
        if (currentMode == AuthMode.LOGIN) {
            ivAuthBack.setVisibility(View.GONE);
            btnSwitchAuthMode.setVisibility(View.VISIBLE);
            btnSwitchAuthMode.setClickable(true);
        } else {
            if (signupStep == 1) {
                ivAuthBack.setVisibility(View.INVISIBLE);
                ivAuthBack.setEnabled(false);
                btnSwitchAuthMode.setVisibility(View.VISIBLE);
                btnSwitchAuthMode.setClickable(true);
            } else if (signupStep == 4) {
                ivAuthBack.setVisibility(View.GONE);
                btnSwitchAuthMode.setVisibility(View.INVISIBLE);
                btnSwitchAuthMode.setClickable(false);
            } else {
                ivAuthBack.setVisibility(View.VISIBLE);
                ivAuthBack.setEnabled(true);
                btnSwitchAuthMode.setVisibility(View.INVISIBLE);
                btnSwitchAuthMode.setClickable(false);
            }
        }
    }

    private void animateComplexTransition(View targetView, String newTitle, boolean showIndicator, String newActionText, String newSwitchText) {
        View currentForm = getVisibleForm();

        final boolean shouldAnimateTitle = !prevTitle.equals(newTitle);
        final boolean shouldAnimateAction = !prevActionText.equals(newActionText);
        final boolean shouldAnimateStep = (currentMode == AuthMode.SIGNUP && prevStep != signupStep && signupStep <= 3 && prevStep <= 3);

        // Indicator should animate if its overall visibility changed OR if we are in signup mode changing steps
        final boolean shouldAnimateIndicator = (prevMode != currentMode) ||
                                               (currentMode == AuthMode.SIGNUP && prevStep <= 3 && signupStep == 4) ||
                                               (currentMode == AuthMode.SIGNUP && prevStep == 4 && signupStep <= 3);
        
        // Determine direction: left = 1 (forward), right = -1 (backward)
        int direction = 1;
        if (prevMode == AuthMode.SIGNUP && currentMode == AuthMode.LOGIN) direction = -1;
        else if (currentMode == AuthMode.SIGNUP && prevMode == AuthMode.SIGNUP && signupStep < prevStep) direction = -1;

        final float slideOffset = 50f * direction;

        // Back button animation logic
        boolean backVisTemp = false;
        if (prevMode == AuthMode.LOGIN && currentMode == AuthMode.SIGNUP) backVisTemp = false;
        else if (currentMode == AuthMode.SIGNUP && ((prevStep == 1 && signupStep == 2) || (prevStep == 2 && signupStep == 1) || (prevStep == 3 && signupStep == 4) || (prevStep == 4 && signupStep == 3))) {
            backVisTemp = true;
        }
        final boolean backVisibilityChanged = backVisTemp;

        // Header animation logic
        final boolean shouldAnimateHeader = (prevMode == AuthMode.LOGIN && currentMode == AuthMode.SIGNUP) ||
                                            (prevMode == AuthMode.SIGNUP && currentMode == AuthMode.LOGIN);

        if (currentForm != null && currentForm != targetView) {
            animateOut(currentForm, slideOffset);
            if (shouldAnimateHeader) animateOut(llAuthHeader, slideOffset);
            if (shouldAnimateTitle) animateOut(tvFormTitle, slideOffset);
            if (shouldAnimateStep) animateOut(tvStepNumber, slideOffset);
            if (shouldAnimateIndicator) animateOut(llStepIndicator, slideOffset);
            if (shouldAnimateAction) animateOut(btnAuthAction, slideOffset);
            if (btnSwitchAuthMode.getVisibility() == View.VISIBLE) animateOut(btnSwitchAuthMode, slideOffset);
            if (backVisibilityChanged) animateOut(ivAuthBack, slideOffset);

            targetView.postDelayed(() -> {
                tvFormTitle.setText(newTitle);
                tvStepNumber.setText(String.valueOf(signupStep));
                llStepIndicator.setVisibility(showIndicator ? View.VISIBLE : View.GONE);
                btnAuthAction.setText(newActionText);
                btnSwitchAuthMode.setText(newSwitchText);
                updateFixedVisibilities();

                if (currentMode == AuthMode.LOGIN) {
                    llAuthHeader.setVisibility(View.VISIBLE);
                    if (shouldAnimateHeader) animateIn(llAuthHeader, slideOffset);
                } else {
                    llAuthHeader.setVisibility(View.GONE);
                }

                animateIn(targetView, slideOffset);
                if (shouldAnimateTitle) animateIn(tvFormTitle, slideOffset);
                if (shouldAnimateStep) animateIn(tvStepNumber, slideOffset);
                if (shouldAnimateIndicator && showIndicator) animateIn(llStepIndicator, slideOffset);
                if (shouldAnimateAction) animateIn(btnAuthAction, slideOffset);
                if (btnSwitchAuthMode.getVisibility() == View.VISIBLE) animateIn(btnSwitchAuthMode, slideOffset);
                if (backVisibilityChanged && ivAuthBack.getVisibility() == View.VISIBLE) animateIn(ivAuthBack, slideOffset);
                
            }, 250);
        } else {
            targetView.setVisibility(View.VISIBLE);
            targetView.setAlpha(1f);
            targetView.setTranslationX(0f);
            llAuthHeader.setVisibility(currentMode == AuthMode.LOGIN ? View.VISIBLE : View.GONE);
        }
    }

    private View getVisibleForm() {
        if (llLoginStep.getVisibility() == View.VISIBLE) return llLoginStep;
        if (llSignupStep1.getVisibility() == View.VISIBLE) return llSignupStep1;
        if (llSignupStep2.getVisibility() == View.VISIBLE) return llSignupStep2;
        if (llSignupStep3.getVisibility() == View.VISIBLE) return llSignupStep3;
        if (llSignupSuccess.getVisibility() == View.VISIBLE) return llSignupSuccess;
        return null;
    }

    private void animateOut(View v, float offset) {
        v.animate()
                .alpha(0f)
                .translationX(-offset)
                .setDuration(200)
                .withEndAction(() -> {
                    v.setVisibility(View.GONE);
                    v.setTranslationX(0f);
                }).start();
    }

    private void animateIn(View v, float offset) {
        v.setAlpha(0f);
        v.setTranslationX(offset);
        v.setVisibility(View.VISIBLE);
        v.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(200)
                .start();
    }

    private boolean validateStep1() {
        if (etSignupEmail.getText().toString().isEmpty()) {
            etSignupEmail.setError("Email is required");
            return false;
        }
        if (etSignupUsername.getText().toString().isEmpty()) {
            etSignupUsername.setError("Username is required");
            return false;
        }
        return true;
    }

    private boolean validateNameAndPhone() {
        if (etSignupName.getText().toString().isEmpty()) {
            etSignupName.setError("Name is required");
            return false;
        }
        if (etSignupPhone.getText().toString().isEmpty()) {
            etSignupPhone.setError("Phone is required");
            return false;
        }
        return true;
    }

    private boolean validatePasswords() {
        String pass = etSignupPassword.getText().toString();
        String confirm = etSignupConfirm.getText().toString();
        if (pass.isEmpty()) {
            etSignupPassword.setError("Password is required");
            return false;
        }
        if (!pass.equals(confirm)) {
            etSignupConfirm.setError("Passwords do not match");
            return false;
        }
        return true;
    }
}
