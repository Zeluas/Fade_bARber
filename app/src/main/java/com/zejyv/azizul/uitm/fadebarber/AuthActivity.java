package com.zejyv.azizul.uitm.fadebarber;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.splashscreen.SplashScreen;

public class AuthActivity extends AppCompatActivity {

    private enum AuthMode { LOGIN, SIGNUP }
    private AuthMode currentMode = AuthMode.LOGIN;
    private int signupStep = 1; // 1: Account Info, 2: Identity, 3: Password, 4: Success, 5: Failed
    private boolean isHeaderSuppressed = false;

    private TextView tvFormTitle, tvStepNumber, tvSignupErrorDetails;
    private View llStepIndicator;
    private LinearLayout llLoginStep, llSignupStep1, llSignupStep2, llSignupStep3, llSignupSuccess, llSignupFailed, llAuthHeader;
    private Button btnAuthAction, btnSwitchAuthMode;
    private ImageView ivAuthBack;
    private View mcvAuthContainer, layoutExitConfirmation, mcvExitDialog;
    private Button btnExitCancel, btnExitConfirm;
    private CheckBox cbRememberMe;

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
        tvSignupErrorDetails = findViewById(R.id.tv_signup_error_details);
        llStepIndicator = findViewById(R.id.ll_step_indicator);
        llLoginStep = findViewById(R.id.ll_login_step);
        llSignupStep1 = findViewById(R.id.ll_signup_step_1);
        llSignupStep2 = findViewById(R.id.ll_signup_step_2);
        llSignupStep3 = findViewById(R.id.ll_signup_step_3);
        llSignupSuccess = findViewById(R.id.ll_signup_success);
        llSignupFailed = findViewById(R.id.ll_signup_failed);
        llAuthHeader = findViewById(R.id.ll_auth_header);
        mcvAuthContainer = findViewById(R.id.mcv_auth_container);
        layoutExitConfirmation = findViewById(R.id.layout_exit_confirmation);
        mcvExitDialog = findViewById(R.id.mcv_exit_dialog);
        btnExitCancel = findViewById(R.id.btn_exit_cancel);
        btnExitConfirm = findViewById(R.id.btn_exit_confirm);
        cbRememberMe = findViewById(R.id.cb_remember_me);
        
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
        setupBackPressed();

        mcvAuthContainer.getViewTreeObserver().addOnGlobalLayoutListener(this::checkHeaderOverlap);

        updateUI(false);
    }

    private void setupListeners() {
        btnSwitchAuthMode.setOnClickListener(v -> {
            if (currentMode == AuthMode.LOGIN) {
                currentMode = AuthMode.SIGNUP;
                signupStep = 1;
            } else {
                currentMode = AuthMode.LOGIN;
                hideKeyboard();
                clearFocus();
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

        btnExitCancel.setOnClickListener(v -> hideExitDialog());
        btnExitConfirm.setOnClickListener(v -> finish());
    }

    private void showExitDialog() {
        layoutExitConfirmation.setVisibility(View.VISIBLE);
        layoutExitConfirmation.setAlpha(0f);
        layoutExitConfirmation.animate().alpha(1f).setDuration(200).start();
        
        mcvExitDialog.setScaleX(0f);
        mcvExitDialog.setScaleY(0f);
        mcvExitDialog.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
    }

    private void hideExitDialog() {
        mcvExitDialog.animate().scaleX(0f).scaleY(0f).setDuration(200).start();
        layoutExitConfirmation.animate().alpha(0f).setDuration(200).withEndAction(() -> layoutExitConfirmation.setVisibility(View.GONE)).start();
    }

    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutExitConfirmation.getVisibility() == View.VISIBLE) {
                    hideExitDialog();
                } else if (currentMode == AuthMode.SIGNUP) {
                    handleBackStep();
                } else {
                    showExitDialog();
                }
            }
        });
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
            if (signupStep == 4 || signupStep == 5) { // Result screen, return to login
                currentMode = AuthMode.LOGIN;
                hideKeyboard();
                clearFocus();
                updateUI(true);
            } else if (signupStep > 1) {
                signupStep--;
                updateUI(true);
            } else {
                currentMode = AuthMode.LOGIN;
                hideKeyboard();
                clearFocus();
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
                if (validatePasswords()) {
                    hideKeyboard();
                    clearFocus();
                    // Simulate registration
                    if (etSignupPassword.getText().toString().equals("asd")) {
                        signupStep = 5; // Failed
                        tvSignupErrorDetails.setText("QA Test: Registration failed because password was 'asd'.");
                    } else {
                        signupStep = 4; // Success
                    }
                    updateUI(true);
                }
                break;
            case 4:
            case 5:
                currentMode = AuthMode.LOGIN;
                hideKeyboard();
                clearFocus();
                updateUI(true);
                break;
        }
    }

    private void checkHeaderOverlap() {
        if (mcvAuthContainer.getHeight() == 0) return;

        int containerTop = mcvAuthContainer.getTop();
        if (containerTop <= 0) return;

        llAuthHeader.measure(
                View.MeasureSpec.makeMeasureSpec(mcvAuthContainer.getWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int headerHeight = llAuthHeader.getMeasuredHeight();
        float margin = 12 * getResources().getDisplayMetrics().density;

        boolean shouldSuppress = headerHeight > 0 && (headerHeight + margin > containerTop);

        if (shouldSuppress != isHeaderSuppressed) {
            isHeaderSuppressed = shouldSuppress;
            if (currentMode == AuthMode.LOGIN) {
                llAuthHeader.setVisibility(isHeaderSuppressed ? View.GONE : View.VISIBLE);
            }
        }
    }

    private void updateUI(boolean animate) {
        View targetView;
        String title;
        boolean showIndicator = false;
        String actionText;
        String switchText;
        
        int targetTopMargin;

        if (currentMode == AuthMode.LOGIN) {
            targetView = llLoginStep;
            title = "Login";
            actionText = "LOGIN";
            switchText = "Create an account";
            targetTopMargin = 0;
        } else {
            title = "Sign Up";
            switchText = "Already have an account? Login";
            targetTopMargin = (int) (30 * getResources().getDisplayMetrics().density);

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
                case 4:
                    targetView = llSignupSuccess;
                    title = "Success";
                    actionText = "BACK TO LOGIN";
                    break;
                default: // 5: Failed
                    targetView = llSignupFailed;
                    title = "Failed";
                    actionText = "BACK TO LOGIN";
                    break;
            }
        }

        if (animate) {
            animateComplexTransition(targetView, title, showIndicator, actionText, switchText, targetTopMargin);
        } else {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) btnAuthAction.getLayoutParams();
            params.topMargin = targetTopMargin;
            btnAuthAction.setLayoutParams(params);
            
            tvFormTitle.setText(title);
            tvStepNumber.setText(String.valueOf(signupStep));
            llStepIndicator.setVisibility(showIndicator ? View.VISIBLE : View.GONE);
            btnAuthAction.setText(actionText);
            btnSwitchAuthMode.setText(switchText);
            updateFixedVisibilities();
            
            llLoginStep.setVisibility(View.INVISIBLE);
            llSignupStep1.setVisibility(View.INVISIBLE);
            llSignupStep2.setVisibility(View.INVISIBLE);
            llSignupStep3.setVisibility(View.INVISIBLE);
            llSignupSuccess.setVisibility(View.INVISIBLE);
            llSignupFailed.setVisibility(View.INVISIBLE);
            cbRememberMe.setVisibility(currentMode == AuthMode.LOGIN ? View.VISIBLE : View.GONE);
            llAuthHeader.setVisibility((currentMode == AuthMode.LOGIN && !isHeaderSuppressed) ? View.VISIBLE : View.GONE);

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
            tvSignupErrorDetails.setVisibility(View.GONE);
        } else {
            if (signupStep == 1) {
                ivAuthBack.setVisibility(View.INVISIBLE);
                ivAuthBack.setEnabled(false);
                btnSwitchAuthMode.setVisibility(View.VISIBLE);
                btnSwitchAuthMode.setClickable(true);
                tvSignupErrorDetails.setVisibility(View.GONE);
            } else if (signupStep == 4 || signupStep == 5) {
                ivAuthBack.setVisibility(View.GONE);
                btnSwitchAuthMode.setVisibility(View.INVISIBLE);
                btnSwitchAuthMode.setClickable(false);
                tvSignupErrorDetails.setVisibility(signupStep == 5 ? View.VISIBLE : View.GONE);
            } else {
                ivAuthBack.setVisibility(View.VISIBLE);
                ivAuthBack.setEnabled(true);
                btnSwitchAuthMode.setVisibility(View.INVISIBLE);
                btnSwitchAuthMode.setClickable(false);
                tvSignupErrorDetails.setVisibility(View.GONE);
            }
        }
    }

    private void animateComplexTransition(View targetView, String newTitle, boolean showIndicator, String newActionText, String newSwitchText, int targetTopMargin) {
        int direction = 1;
        if (prevMode == AuthMode.SIGNUP && currentMode == AuthMode.LOGIN) direction = -1;
        else if (currentMode == AuthMode.SIGNUP && prevMode == AuthMode.SIGNUP && signupStep < prevStep) direction = -1;
        final float slideOffset = 50f * direction;

        boolean isModeChange = prevMode != currentMode;
        boolean isToResult = currentMode == AuthMode.SIGNUP && (signupStep == 4 || signupStep == 5) && prevStep == 3;

        if (isModeChange || isToResult) {
            // Whole card animation
            animateOut(mcvAuthContainer, slideOffset);
            animateOut(btnSwitchAuthMode, slideOffset);
            if (prevMode == AuthMode.LOGIN && currentMode == AuthMode.SIGNUP && llAuthHeader.getVisibility() == View.VISIBLE) {
                animateOutToGone(llAuthHeader, slideOffset);
            }

            mcvAuthContainer.postDelayed(() -> {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) btnAuthAction.getLayoutParams();
                params.topMargin = targetTopMargin;
                btnAuthAction.setLayoutParams(params);

                tvFormTitle.setText(newTitle);
                tvStepNumber.setText(String.valueOf(signupStep));
                llStepIndicator.setVisibility(showIndicator ? View.VISIBLE : View.GONE);
                btnAuthAction.setText(newActionText);
                btnSwitchAuthMode.setText(newSwitchText);
                updateFixedVisibilities();

                // Prep views
                llLoginStep.setVisibility(currentMode == AuthMode.LOGIN ? View.VISIBLE : View.INVISIBLE);
                llSignupStep1.setVisibility(currentMode == AuthMode.SIGNUP && signupStep == 1 ? View.VISIBLE : View.INVISIBLE);
                llSignupStep2.setVisibility(currentMode == AuthMode.SIGNUP && signupStep == 2 ? View.VISIBLE : View.INVISIBLE);
                llSignupStep3.setVisibility(currentMode == AuthMode.SIGNUP && signupStep == 3 ? View.VISIBLE : View.INVISIBLE);
                llSignupSuccess.setVisibility(currentMode == AuthMode.SIGNUP && signupStep == 4 ? View.VISIBLE : View.INVISIBLE);
                llSignupFailed.setVisibility(currentMode == AuthMode.SIGNUP && signupStep == 5 ? View.VISIBLE : View.INVISIBLE);
                cbRememberMe.setVisibility(currentMode == AuthMode.LOGIN ? View.VISIBLE : View.GONE);

                resetInternalAnimations();

                if (currentMode == AuthMode.LOGIN) {
                    if (!isHeaderSuppressed) {
                        llAuthHeader.setVisibility(View.VISIBLE);
                        animateIn(llAuthHeader, slideOffset);
                    } else {
                        llAuthHeader.setVisibility(View.GONE);
                    }
                } else {
                    llAuthHeader.setVisibility(View.GONE);
                }

                animateIn(mcvAuthContainer, slideOffset);
                if (btnSwitchAuthMode.getVisibility() == View.VISIBLE) {
                    animateIn(btnSwitchAuthMode, slideOffset);
                }
            }, 250);
            return;
        }

        View currentForm = getVisibleForm();
        final boolean shouldAnimateTitle = !prevTitle.equals(newTitle);
        final boolean shouldAnimateAction = !prevActionText.equals(newActionText);
        final boolean shouldAnimateStep = (prevStep != signupStep && signupStep <= 3 && prevStep <= 3);
        final boolean shouldAnimateIndicator = (signupStep == 4 || prevStep == 4 || signupStep == 5 || prevStep == 5);

        final boolean backVisibilityChanged = currentMode == AuthMode.SIGNUP && ((prevStep == 1 && signupStep == 2) || (prevStep == 2 && signupStep == 1) || (prevStep == 3 && (signupStep == 4 || signupStep == 5)) || ((prevStep == 4 || prevStep == 5) && signupStep == 3));

        if (currentForm != null && currentForm != targetView) {
            animateOut(currentForm, slideOffset);
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
        }
    }

    private void resetInternalAnimations() {
        View[] views = {tvFormTitle, tvStepNumber, llStepIndicator, btnAuthAction, btnSwitchAuthMode, ivAuthBack, cbRememberMe, llLoginStep, llSignupStep1, llSignupStep2, llSignupStep3, llSignupSuccess, llSignupFailed};
        for (View v : views) {
            if (v != null) {
                v.setAlpha(1f);
                v.setTranslationX(0f);
            }
        }
    }

    private View getVisibleForm() {
        if (llLoginStep.getVisibility() == View.VISIBLE) return llLoginStep;
        if (llSignupStep1.getVisibility() == View.VISIBLE) return llSignupStep1;
        if (llSignupStep2.getVisibility() == View.VISIBLE) return llSignupStep2;
        if (llSignupStep3.getVisibility() == View.VISIBLE) return llSignupStep3;
        if (llSignupSuccess.getVisibility() == View.VISIBLE) return llSignupSuccess;
        if (llSignupFailed.getVisibility() == View.VISIBLE) return llSignupFailed;
        return null;
    }

    private void animateOut(View v, float offset) {
        v.animate()
                .alpha(0f)
                .translationX(-offset)
                .setDuration(200)
                .withEndAction(() -> {
                    v.setVisibility(View.INVISIBLE);
                    v.setTranslationX(0f);
                }).start();
    }

    private void animateOutToGone(View v, float offset) {
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

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private void clearFocus() {
        View view = this.getCurrentFocus();
        if (view != null) {
            view.clearFocus();
        }
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
