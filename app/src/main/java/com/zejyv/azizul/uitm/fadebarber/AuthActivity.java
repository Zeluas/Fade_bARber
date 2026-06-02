package com.zejyv.azizul.uitm.fadebarber;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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

/**
 * AuthActivity handles the user authentication process including Login and a multi-step Signup.
 * Features:
 * - Login form with 'Remember Me'
 * - 3-step Signup process (Account -> Identity -> Security)
 * - Animated transitions between forms
 * - Dynamic header suppression based on screen space
 * - Custom exit confirmation dialog
 * - Optimized flat layout for performance
 * - Full localization support via strings.xml
 */
public class AuthActivity extends AppCompatActivity {

    // --- State Management ---
    private enum AuthMode { LOGIN, SIGNUP }
    private AuthMode currentMode = AuthMode.LOGIN;
    private int signupStep = 1; // 1: Account, 2: Identity, 3: Credentials, 4: Success, 5: Failed
    private boolean isHeaderSuppressed = false;

    // Previous state trackers for animation direction and delta checks
    private String prevTitle = "";
    private String prevActionText = "";
    private int prevStep = 1;
    private AuthMode prevMode = AuthMode.LOGIN;

    // --- UI Components ---
    
    // Containers & Layouts
    private LinearLayout llAuthHeader;
    private View mcvAuthContainer;
    private View layoutExitConfirmation, mcvExitDialog;
    private View llStepIndicator;
    private LinearLayout llLoginStep, llSignupStep1, llSignupStep2, llSignupStep3, llSignupSuccess, llSignupFailed;
    
    // Text Views
    private TextView tvFormTitle, tvStepNumber, tvSignupErrorDetails;
    
    // Buttons & Icons
    private Button btnAuthAction, btnSwitchAuthMode, btnExitCancel, btnExitConfirm;
    private ImageView ivAuthBack;
    private CheckBox cbRememberMe;
    
    // Input Fields - Login
    private EditText etLoginEmail, etLoginPassword;
    
    // Input Fields - Signup
    private EditText etSignupEmail, etSignupUsername, etSignupName, etSignupPhone, etSignupPassword, etSignupConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Install Splash Screen before super.onCreate() as per API 31+
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        initializeViews();
        setupListeners();
        setupImeActions();
        setupBackPressed();

        // Monitor layout changes to suppress header if card overlaps on small screens or when keyboard is shown
        mcvAuthContainer.getViewTreeObserver().addOnGlobalLayoutListener(this::checkHeaderOverlap);

        // Initialize state trackers for the first time
        prevTitle = getString(R.string.auth_login_title);
        prevActionText = getString(R.string.login_btn_text);

        // Initial UI state setup (no animation)
        updateUI(false);
    }

    /**
     * Initializes all view references from the layout.
     */
    private void initializeViews() {
        // Containers
        llAuthHeader = findViewById(R.id.ll_auth_header);
        mcvAuthContainer = findViewById(R.id.mcv_auth_container);
        layoutExitConfirmation = findViewById(R.id.layout_exit_confirmation);
        mcvExitDialog = findViewById(R.id.mcv_exit_dialog);
        llStepIndicator = findViewById(R.id.ll_step_indicator);
        
        // Form steps
        llLoginStep = findViewById(R.id.ll_login_step);
        llSignupStep1 = findViewById(R.id.ll_signup_step_1);
        llSignupStep2 = findViewById(R.id.ll_signup_step_2);
        llSignupStep3 = findViewById(R.id.ll_signup_step_3);
        llSignupSuccess = findViewById(R.id.ll_signup_success);
        llSignupFailed = findViewById(R.id.ll_signup_failed);

        // Interactive Elements
        tvFormTitle = findViewById(R.id.tv_form_title);
        tvStepNumber = findViewById(R.id.tv_step_number);
        tvSignupErrorDetails = findViewById(R.id.tv_signup_error_details);
        btnAuthAction = findViewById(R.id.btn_auth_action);
        btnSwitchAuthMode = findViewById(R.id.btn_switch_auth_mode);
        ivAuthBack = findViewById(R.id.iv_auth_back);
        cbRememberMe = findViewById(R.id.cb_remember_me);
        btnExitCancel = findViewById(R.id.btn_exit_cancel);
        btnExitConfirm = findViewById(R.id.btn_exit_confirm);

        // Edit Texts
        etLoginEmail = findViewById(R.id.et_login_email);
        etLoginPassword = findViewById(R.id.et_login_password);
        etSignupEmail = findViewById(R.id.et_signup_email);
        etSignupUsername = findViewById(R.id.et_signup_username);
        etSignupName = findViewById(R.id.et_signup_name);
        etSignupPhone = findViewById(R.id.et_signup_phone);
        etSignupPassword = findViewById(R.id.et_signup_password);
        etSignupConfirm = findViewById(R.id.et_signup_confirm);
    }

    /**
     * Sets up click listeners for all interactive elements.
     */
    private void setupListeners() {
        // Toggle between Login and Signup modes
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

        // Main action button (LOGIN, NEXT, or CONFIRM)
        btnAuthAction.setOnClickListener(v -> {
            if (currentMode == AuthMode.LOGIN) {
                handleLogin();
            } else {
                handleSignupNext();
            }
        });

        // Back button in signup steps
        ivAuthBack.setOnClickListener(v -> handleBackStep());

        // Exit confirmation dialog buttons
        btnExitCancel.setOnClickListener(v -> hideExitDialog());
        btnExitConfirm.setOnClickListener(v -> finish());
    }

    /**
     * Shows the custom exit confirmation dialog with animation.
     */
    private void showExitDialog() {
        layoutExitConfirmation.setVisibility(View.VISIBLE);
        layoutExitConfirmation.setAlpha(0f);
        layoutExitConfirmation.animate().alpha(1f).setDuration(200).start();
        
        mcvExitDialog.setScaleX(0f);
        mcvExitDialog.setScaleY(0f);
        mcvExitDialog.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
    }

    /**
     * Hides the custom exit confirmation dialog with animation.
     */
    private void hideExitDialog() {
        mcvExitDialog.animate().scaleX(0f).scaleY(0f).setDuration(200).start();
        layoutExitConfirmation.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutExitConfirmation.setVisibility(View.GONE)).start();
    }

    /**
     * Configures the system back button behavior.
     */
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

    /**
     * Sets up keyboard 'Done' actions for various input fields.
     */
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

    /**
     * Handles navigation to the previous step or mode.
     */
    private void handleBackStep() {
        if (currentMode == AuthMode.SIGNUP) {
            if (signupStep == 4 || signupStep == 5) { // Return from Result screens
                currentMode = AuthMode.LOGIN;
                hideKeyboard();
                clearFocus();
            } else if (signupStep > 1) { // Back to previous signup step
                signupStep--;
            } else { // Back to login from first signup step
                currentMode = AuthMode.LOGIN;
                hideKeyboard();
                clearFocus();
            }
            updateUI(true);
        }
    }

    /**
     * Processes login logic.
     */
    private void handleLogin() {
        String email = etLoginEmail.getText().toString().trim();
        if (!email.isEmpty()) {
            // Success: Navigate to Main
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, getString(R.string.login_error_empty_email), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Orchestrates the multi-step signup progression and validation.
     */
    private void handleSignupNext() {
        switch (signupStep) {
            case 1: // Account Info Step
                if (validateAccountInfo()) { 
                    signupStep = 2; 
                    updateUI(true); 
                    etSignupName.requestFocus(); 
                }
                break;
            case 2: // Identity Info Step
                if (validateIdentityInfo()) { 
                    signupStep = 3; 
                    updateUI(true); 
                    etSignupPassword.requestFocus(); 
                }
                break;
            case 3: // Security Step (Credentials)
                if (validateCredentials()) {
                    hideKeyboard();
                    clearFocus();
                    simulateRegistration();
                    updateUI(true);
                }
                break;
            case 4: // Success Screen -> Back to Login
            case 5: // Failed Screen -> Back to Login
                currentMode = AuthMode.LOGIN;
                hideKeyboard();
                clearFocus();
                updateUI(true);
                break;
        }
    }

    /**
     * Simulates the registration process result.
     */
    private void simulateRegistration() {
        // QA Hook: Simulate failure if password is 'asd'
        if (etSignupPassword.getText().toString().equals("asd")) {
            signupStep = 5; // Failed state
            tvSignupErrorDetails.setText("QA Test: Registration failed because password was 'asd'.");
        } else {
            signupStep = 4; // Success state
        }
    }

    /**
     * Dynamically hides/shows the top branding header to prevent overlap with the main card.
     */
    private void checkHeaderOverlap() {
        if (mcvAuthContainer.getHeight() == 0) return;

        int containerTop = mcvAuthContainer.getTop();
        if (containerTop <= 0) return;

        // Measure header height including dynamic content
        llAuthHeader.measure(
                View.MeasureSpec.makeMeasureSpec(mcvAuthContainer.getWidth(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int headerHeight = llAuthHeader.getMeasuredHeight();
        float margin = 12 * getResources().getDisplayMetrics().density;

        boolean shouldSuppress = headerHeight > 0 && (headerHeight + margin > containerTop);

        if (shouldSuppress != isHeaderSuppressed) {
            isHeaderSuppressed = shouldSuppress;
            // Only show branding header in LOGIN mode if not suppressed
            if (currentMode == AuthMode.LOGIN) {
                llAuthHeader.setVisibility(isHeaderSuppressed ? View.GONE : View.VISIBLE);
            }
        }
    }

    /**
     * Updates the entire UI state based on the current mode and step.
     * @param animate Whether to use transition animations.
     */
    private void updateUI(boolean animate) {
        View targetForm;
        String title;
        boolean showStepIndicator = false;
        String actionText;
        String switchText;
        int actionBtnTopMargin;

        // Determine parameters based on state
        if (currentMode == AuthMode.LOGIN) {
            targetForm = llLoginStep;
            title = getString(R.string.auth_login_title);
            actionText = getString(R.string.login_btn_text);
            switchText = getString(R.string.login_switch_to_signup);
            actionBtnTopMargin = 0;
        } else {
            title = getString(R.string.auth_signup_title);
            switchText = getString(R.string.signup_switch_to_login);
            actionBtnTopMargin = (int) (30 * getResources().getDisplayMetrics().density);

            switch (signupStep) {
                case 1:
                    targetForm = llSignupStep1;
                    showStepIndicator = true;
                    actionText = getString(R.string.signup_btn_next);
                    break;
                case 2:
                    targetForm = llSignupStep2;
                    showStepIndicator = true;
                    actionText = getString(R.string.signup_btn_next);
                    break;
                case 3:
                    targetForm = llSignupStep3;
                    showStepIndicator = true;
                    actionText = getString(R.string.signup_btn_confirm);
                    break;
                case 4:
                    targetForm = llSignupSuccess;
                    title = getString(R.string.auth_success_title);
                    actionText = getString(R.string.signup_btn_back_to_login);
                    break;
                default: // 5: Failed
                    targetForm = llSignupFailed;
                    title = getString(R.string.auth_failed_title);
                    actionText = getString(R.string.signup_btn_back_to_login);
                    break;
            }
        }

        if (animate) {
            animateComplexTransition(targetForm, title, showStepIndicator, actionText, switchText, actionBtnTopMargin);
        } else {
            applyUiState(targetForm, title, showStepIndicator, actionText, switchText, actionBtnTopMargin);
        }

        // Store current state as previous for next animation
        prevTitle = title;
        prevActionText = actionText;
        prevStep = signupStep;
        prevMode = currentMode;
    }

    /**
     * Directly applies UI states without animation.
     */
    private void applyUiState(View targetForm, String title, boolean showStepIndicator, String actionText, String switchText, int actionBtnTopMargin) {
        // Update Action Button Margin
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) btnAuthAction.getLayoutParams();
        params.topMargin = actionBtnTopMargin;
        btnAuthAction.setLayoutParams(params);
        
        // Update Texts
        tvFormTitle.setText(title);
        tvStepNumber.setText(String.valueOf(signupStep));
        btnAuthAction.setText(actionText);
        btnSwitchAuthMode.setText(switchText);
        
        // Visibility Management
        llStepIndicator.setVisibility(showStepIndicator ? View.VISIBLE : View.GONE);
        cbRememberMe.setVisibility(currentMode == AuthMode.LOGIN ? View.VISIBLE : View.GONE);
        llAuthHeader.setVisibility((currentMode == AuthMode.LOGIN && !isHeaderSuppressed) ? View.VISIBLE : View.GONE);
        
        updateFixedComponentVisibilities();
        
        // Hide all forms and show target
        llLoginStep.setVisibility(View.INVISIBLE);
        llSignupStep1.setVisibility(View.INVISIBLE);
        llSignupStep2.setVisibility(View.INVISIBLE);
        llSignupStep3.setVisibility(View.INVISIBLE);
        llSignupSuccess.setVisibility(View.INVISIBLE);
        llSignupFailed.setVisibility(View.INVISIBLE);

        targetForm.setVisibility(View.VISIBLE);
        targetForm.setAlpha(1f);
        targetForm.setTranslationX(0f);
    }

    /**
     * Updates visibility and enablement of components that don't change based on steps but on mode.
     */
    private void updateFixedComponentVisibilities() {
        if (currentMode == AuthMode.LOGIN) {
            ivAuthBack.setVisibility(View.GONE);
            btnSwitchAuthMode.setVisibility(View.VISIBLE);
            btnSwitchAuthMode.setClickable(true);
            tvSignupErrorDetails.setVisibility(View.GONE);
        } else {
            if (signupStep == 1) { // First step of Signup
                ivAuthBack.setVisibility(View.INVISIBLE); // Invisible to maintain layout but not clickable
                ivAuthBack.setEnabled(false);
                btnSwitchAuthMode.setVisibility(View.VISIBLE);
                btnSwitchAuthMode.setClickable(true);
                tvSignupErrorDetails.setVisibility(View.GONE);
            } else if (signupStep == 4 || signupStep == 5) { // Result screens
                ivAuthBack.setVisibility(View.GONE);
                btnSwitchAuthMode.setVisibility(View.INVISIBLE);
                btnSwitchAuthMode.setClickable(false);
                tvSignupErrorDetails.setVisibility(signupStep == 5 ? View.VISIBLE : View.GONE);
            } else { // Intermediate Signup steps
                ivAuthBack.setVisibility(View.VISIBLE);
                ivAuthBack.setEnabled(true);
                btnSwitchAuthMode.setVisibility(View.INVISIBLE);
                btnSwitchAuthMode.setClickable(false);
                tvSignupErrorDetails.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Manages complex animations for switching between Login/Signup and through Signup steps.
     */
    private void animateComplexTransition(View targetForm, String newTitle, boolean showIndicator, String newActionText, String newSwitchText, int actionBtnTopMargin) {
        // Determine animation direction
        int direction = 1; // Slide left (forward)
        if (prevMode == AuthMode.SIGNUP && currentMode == AuthMode.LOGIN) direction = -1; // Slide right (backward)
        else if (currentMode == AuthMode.SIGNUP && prevMode == AuthMode.SIGNUP && signupStep < prevStep) direction = -1;
        
        final float slideOffset = 50f * direction;

        boolean isModeChange = prevMode != currentMode;
        boolean isEnteringResult = currentMode == AuthMode.SIGNUP && (signupStep == 4 || signupStep == 5) && prevStep == 3;

        // Perform whole-card animation for major state changes
        if (isModeChange || isEnteringResult) {
            animateOut(mcvAuthContainer, slideOffset);
            animateOut(btnSwitchAuthMode, slideOffset);
            if (prevMode == AuthMode.LOGIN && currentMode == AuthMode.SIGNUP && llAuthHeader.getVisibility() == View.VISIBLE) {
                animateOutToGone(llAuthHeader, slideOffset);
            }

            mcvAuthContainer.postDelayed(() -> {
                applyUiState(targetForm, newTitle, showIndicator, newActionText, newSwitchText, actionBtnTopMargin);
                resetAllAnimations();

                if (currentMode == AuthMode.LOGIN && !isHeaderSuppressed) {
                    animateIn(llAuthHeader, slideOffset);
                }

                animateIn(mcvAuthContainer, slideOffset);
                if (btnSwitchAuthMode.getVisibility() == View.VISIBLE) {
                    animateIn(btnSwitchAuthMode, slideOffset);
                }
            }, 250);
            return;
        }

        // Perform internal form animations for step changes
        View currentForm = getVisibleForm();
        final boolean animateTitle = !prevTitle.equals(newTitle);
        final boolean animateAction = !prevActionText.equals(newActionText);
        final boolean animateStepText = (prevStep != signupStep && signupStep <= 3 && prevStep <= 3);
        final boolean animateIndicator = (signupStep == 4 || prevStep == 4 || signupStep == 5 || prevStep == 5);

        // Check if back button visibility is changing
        final boolean backVisibilityToggled = currentMode == AuthMode.SIGNUP && 
                ((prevStep == 1 && signupStep == 2) || (prevStep == 2 && signupStep == 1) || 
                 (prevStep == 3 && (signupStep == 4 || signupStep == 5)) || 
                 ((prevStep == 4 || prevStep == 5) && signupStep == 3));

        if (currentForm != null && currentForm != targetForm) {
            animateOut(currentForm, slideOffset);
            if (animateTitle) animateOut(tvFormTitle, slideOffset);
            if (animateStepText) animateOut(tvStepNumber, slideOffset);
            if (animateIndicator) animateOut(llStepIndicator, slideOffset);
            if (animateAction) animateOut(btnAuthAction, slideOffset);
            if (btnSwitchAuthMode.getVisibility() == View.VISIBLE) animateOut(btnSwitchAuthMode, slideOffset);
            if (backVisibilityToggled) animateOut(ivAuthBack, slideOffset);
            
            targetForm.postDelayed(() -> {
                applyUiState(targetForm, newTitle, showIndicator, newActionText, newSwitchText, actionBtnTopMargin);

                animateIn(targetForm, slideOffset);
                if (animateTitle) animateIn(tvFormTitle, slideOffset);
                if (animateStepText) animateIn(tvStepNumber, slideOffset);
                if (animateIndicator && showIndicator) animateIn(llStepIndicator, slideOffset);
                if (animateAction) animateIn(btnAuthAction, slideOffset);
                if (btnSwitchAuthMode.getVisibility() == View.VISIBLE) animateIn(btnSwitchAuthMode, slideOffset);
                if (backVisibilityToggled && ivAuthBack.getVisibility() == View.VISIBLE) animateIn(ivAuthBack, slideOffset);
                
            }, 250);
        } else {
            // No current form to animate out, just show target
            targetForm.setVisibility(View.VISIBLE);
            targetForm.setAlpha(1f);
            targetForm.setTranslationX(0f);
        }
    }

    /**
     * Resets animation properties for all potentially animated views to their default state.
     */
    private void resetAllAnimations() {
        View[] views = {tvFormTitle, tvStepNumber, llStepIndicator, btnAuthAction, btnSwitchAuthMode, ivAuthBack, 
                        cbRememberMe, llLoginStep, llSignupStep1, llSignupStep2, llSignupStep3, llSignupSuccess, llSignupFailed};
        for (View v : views) {
            if (v != null) {
                v.setAlpha(1f);
                v.setTranslationX(0f);
            }
        }
    }

    /**
     * Identifies which form section is currently visible.
     */
    private View getVisibleForm() {
        if (llLoginStep.getVisibility() == View.VISIBLE) return llLoginStep;
        if (llSignupStep1.getVisibility() == View.VISIBLE) return llSignupStep1;
        if (llSignupStep2.getVisibility() == View.VISIBLE) return llSignupStep2;
        if (llSignupStep3.getVisibility() == View.VISIBLE) return llSignupStep3;
        if (llSignupSuccess.getVisibility() == View.VISIBLE) return llSignupSuccess;
        if (llSignupFailed.getVisibility() == View.VISIBLE) return llSignupFailed;
        return null;
    }

    /**
     * Animation helper to fade out and slide a view.
     */
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

    /**
     * Animation helper to fade out, slide, and set visibility to GONE.
     */
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

    /**
     * Animation helper to fade in and slide a view into place.
     */
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

    /**
     * Helper to hide the software keyboard.
     */
    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    /**
     * Helper to clear focus from any view.
     */
    private void clearFocus() {
        View view = this.getCurrentFocus();
        if (view != null) {
            view.clearFocus();
        }
    }

    // --- Validation Methods ---

    private boolean validateAccountInfo() {
        if (etSignupEmail.getText().toString().trim().isEmpty()) {
            etSignupEmail.setError(getString(R.string.signup_error_email_required));
            return false;
        }
        if (etSignupUsername.getText().toString().trim().isEmpty()) {
            etSignupUsername.setError(getString(R.string.signup_error_username_required));
            return false;
        }
        return true;
    }

    private boolean validateIdentityInfo() {
        if (etSignupName.getText().toString().trim().isEmpty()) {
            etSignupName.setError(getString(R.string.signup_error_name_required));
            return false;
        }
        if (etSignupPhone.getText().toString().trim().isEmpty()) {
            etSignupPhone.setError(getString(R.string.signup_error_phone_required));
            return false;
        }
        return true;
    }

    private boolean validateCredentials() {
        String pass = etSignupPassword.getText().toString();
        String confirm = etSignupConfirm.getText().toString();
        if (pass.isEmpty()) {
            etSignupPassword.setError(getString(R.string.signup_error_password_required));
            return false;
        }
        if (!pass.equals(confirm)) {
            etSignupConfirm.setError(getString(R.string.signup_error_password_mismatch));
            return false;
        }
        return true;
    }
}
