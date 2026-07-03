package com.zejyv.azizul.uitm.fadebarber;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.MemoryCacheSettings;
import com.google.firebase.firestore.Source;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

/**
 * AuthActivity handles the user authentication process including Login and a multi-step Signup.
 */
public class AuthActivity extends AppCompatActivity {

    private enum AuthMode { LOGIN, SIGNUP }
    private AuthMode currentMode = AuthMode.LOGIN;
    private int signupStep = 1;
    private boolean isHeaderSuppressed = false;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private String prevTitle = "";
    private String prevActionText = "";
    private int prevStep = 1;
    private AuthMode prevMode = AuthMode.LOGIN;

    private LinearLayout llAuthHeader;
    private View mcvAuthContainer;
    private View layoutExitConfirmation, mcvExitDialog;
    private View llStepIndicator;
    private LinearLayout llLoginStep, llSignupStep1, llSignupStep2, llSignupStep3, llSignupSuccess, llSignupFailed;
    private View layoutLoadingOverlay;

    private TextView tvFormTitle, tvStepNumber, tvSignupErrorDetails;
    private MaterialButton btnAuthAction, btnSwitchAuthMode, btnExitCancel, btnExitConfirm;
    private TextView btnForgotPassword;
    private ImageView ivAuthBack;
    private CheckBox cbRememberMe;
    
    private EditText etLoginEmail, etLoginPassword;
    private EditText etSignupEmail, etSignupUsername, etSignupName, etSignupPhone, etSignupPassword, etSignupConfirm;

    // Error Banner Components
    private View layoutErrorBannerRoot;
    private TextView tvErrorBannerMsg;
    private View viewRadarPulse;
    private AnimatorSet radarAnimatorSet;

    private final Handler errorCleanupHandler = new Handler(Looper.getMainLooper());
    private final Map<EditText, Runnable> activeErrorCleanups = new HashMap<>();
    private CircularProgressDrawable progressDrawable;
    private android.content.SharedPreferences encryptedPrefs;
    private boolean isReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        
        splashScreen.setKeepOnScreenCondition(() -> !isReady);

        setContentView(R.layout.activity_auth);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initEncryptedPrefs();

        // Disable Offline Persistence for Firestore (prevents caching of previous queries)
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()) // Use memory only, no disk
                .build();
        db.setFirestoreSettings(settings);

        initializeViews();
        setupListeners();
        setupImeActions();
        setupBackPressed();

        checkAutoLogin();

        mcvAuthContainer.getViewTreeObserver().addOnGlobalLayoutListener(this::checkHeaderOverlap);

        prevTitle = getString(R.string.auth_login_title);
        prevActionText = getString(R.string.login_btn_text);

        progressDrawable = new CircularProgressDrawable(this);
        progressDrawable.setStrokeWidth(10f);
        progressDrawable.setCenterRadius(40f);
        progressDrawable.setColorSchemeColors(ContextCompat.getColor(this, R.color.softer_primary_color));

        updateUI(false);
    }

    private void initializeViews() {
        llAuthHeader = findViewById(R.id.ll_auth_header);
        mcvAuthContainer = findViewById(R.id.mcv_auth_container);
        layoutExitConfirmation = findViewById(R.id.layout_exit_confirmation);
        mcvExitDialog = findViewById(R.id.mcv_exit_dialog);
        llStepIndicator = findViewById(R.id.ll_step_indicator);
        layoutLoadingOverlay = findViewById(R.id.layout_loading_overlay);

        llLoginStep = findViewById(R.id.ll_login_step);
        llSignupStep1 = findViewById(R.id.ll_signup_step_1);
        llSignupStep2 = findViewById(R.id.ll_signup_step_2);
        llSignupStep3 = findViewById(R.id.ll_signup_step_3);
        llSignupSuccess = findViewById(R.id.ll_signup_success);
        llSignupFailed = findViewById(R.id.ll_signup_failed);

        tvFormTitle = findViewById(R.id.tv_form_title);
        tvStepNumber = findViewById(R.id.tv_step_number);
        tvSignupErrorDetails = findViewById(R.id.tv_signup_error_details);
        btnAuthAction = findViewById(R.id.btn_auth_action);
        btnSwitchAuthMode = findViewById(R.id.btn_switch_auth_mode);
        ivAuthBack = findViewById(R.id.iv_auth_back);
        cbRememberMe = findViewById(R.id.cb_remember_me);
        btnExitCancel = findViewById(R.id.btn_exit_cancel);
        btnExitConfirm = findViewById(R.id.btn_exit_confirm);
        btnForgotPassword = findViewById(R.id.btn_forgot_password);

        etLoginEmail = findViewById(R.id.et_login_email);
        etLoginEmail.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        etLoginPassword = findViewById(R.id.et_login_password);
        etSignupEmail = findViewById(R.id.et_signup_email);
        etSignupUsername = findViewById(R.id.et_signup_username);
        etSignupName = findViewById(R.id.et_signup_name);
        etSignupPhone = findViewById(R.id.et_signup_phone);
        etSignupPassword = findViewById(R.id.et_signup_password);
        etSignupConfirm = findViewById(R.id.et_signup_confirm);

        setupPasswordVisibilityToggle(etLoginPassword);
        setupPasswordVisibilityToggle(etSignupPassword);
        setupPasswordVisibilityToggle(etSignupConfirm);

        layoutErrorBannerRoot = findViewById(R.id.layout_error_banner_root);
        tvErrorBannerMsg = findViewById(R.id.tv_error_banner_msg);
        viewRadarPulse = findViewById(R.id.view_radar_pulse);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners() {
        findViewById(R.id.layout_auth_root).setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                clearAllErrors();
            }
            return false;
        });

        btnSwitchAuthMode.setOnClickListener(v -> {
            clearAllErrors();
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
            if (layoutErrorBannerRoot.getVisibility() == View.VISIBLE) {
                hideErrorBanner();
                return;
            }
            if (currentMode == AuthMode.LOGIN) {
                handleLogin();
            } else {
                handleSignupNext();
            }
        });

        ivAuthBack.setOnClickListener(v -> handleBackStep());
        btnExitCancel.setOnClickListener(v -> hideExitDialog());
        btnExitConfirm.setOnClickListener(v -> finish());
        layoutExitConfirmation.setOnClickListener(v -> hideExitDialog());

        btnForgotPassword.setPaintFlags(btnForgotPassword.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        btnForgotPassword.setOnClickListener(v -> {
            clearAllErrors();
            handleForgotPassword();
        });

        layoutErrorBannerRoot.setOnClickListener(v -> hideErrorBanner());
    }

    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutErrorBannerRoot.getVisibility() == View.VISIBLE) {
                    hideErrorBanner();
                } else if (layoutExitConfirmation.getVisibility() == View.VISIBLE) {
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
        etLoginEmail.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etLoginPassword.requestFocus();
                return true;
            }
            return false;
        });

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

    private void handleForgotPassword() {
        String email = etLoginEmail.getText().toString().trim();
        if (email.isEmpty()) {
            setFieldError(etLoginEmail, "Please enter your email to reset password");
            etLoginEmail.requestFocus();
            return;
        }
        if (!email.contains("@")) {
            setFieldError(etLoginEmail, "Email missing '@' symbol");
            etLoginEmail.requestFocus();
            return;
        }
        if (!email.contains(".")) {
            setFieldError(etLoginEmail, "Email missing '.' (dot)");
            etLoginEmail.requestFocus();
            return;
        }

        showGlobalLoading(true);
        hideKeyboard();
        db.collection("users").whereEqualTo("email", email).get(Source.SERVER)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().getMetadata().isFromCache()) {
                        showGlobalLoading(false);
                        showErrorBanner("No internet connection. Please check your network.");
                    } else if (task.isSuccessful() && task.getResult().isEmpty()) {
                        showGlobalLoading(false);
                        setFieldError(etLoginEmail, "Incorrect email");
                        etLoginEmail.requestFocus();
                    } else if (task.isSuccessful()) {
                        mAuth.sendPasswordResetEmail(email)
                                .addOnCompleteListener(resetTask -> {
                                    showGlobalLoading(false);
                                    if (resetTask.isSuccessful()) {
                                        Toast.makeText(AuthActivity.this, "Password reset email sent!", Toast.LENGTH_LONG).show();
                                    } else {
                                        showErrorBanner(formatError(resetTask.getException()));
                                    }
                                });
                    } else {
                        showGlobalLoading(false);
                        showErrorBanner(formatError(task.getException()));
                    }
                });
    }

    private void initEncryptedPrefs() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            encryptedPrefs = EncryptedSharedPreferences.create(
                    this,
                    "secret_shared_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    private void checkAutoLogin() {
        if (encryptedPrefs != null && encryptedPrefs.getBoolean("remember_me", false)) {
            // Not logged in via Firebase, but we have saved credentials
            String savedEmail = encryptedPrefs.getString("email", null);
            String savedPassword = encryptedPrefs.getString("password", null);

            if (savedEmail != null && savedPassword != null) {
                mAuth.signInWithEmailAndPassword(savedEmail, savedPassword)
                        .addOnCompleteListener(this, authTask -> {
                            if (authTask.isSuccessful()) {
                                FirebaseUser user = mAuth.getCurrentUser();
                                if (user != null) {
                                    // Verify role from metadata registry
                                    db.collection("users").document(user.getUid()).get(Source.SERVER)
                                            .addOnCompleteListener(userTask -> {
                                                if (userTask.isSuccessful() && userTask.getResult().exists()) {
                                                    String role = userTask.getResult().getString("role");
                                                    
                                                    // Ensure role is updated in local prefs for consistent redirection logic
                                                    if (encryptedPrefs != null) encryptedPrefs.edit().putString("role", role).apply();

                                                    if ("employee".equals(role)) {
                                                        startMainWithExtras(MainActivityEmployee.class);
                                                    } else if ("admin".equals(role)) {
                                                        startMainWithExtras(MainActivityAdmin.class);
                                                    } else {
                                                        startMainWithExtras(MainActivity.class);
                                                    }
                                                } else {
                                                    // Network error during role check or metadata missing
                                                    handleAutoLoginFailure(savedEmail, userTask.getException());
                                                }
                                            });
                                }
                            } else {
                                handleAutoLoginFailure(savedEmail, authTask.getException());
                            }
                        });
            } else {
                isReady = true;
            }
        } else {
            // Ensure any existing Firebase session is cleared if "Remember Me" wasn't used
            mAuth.signOut();
            isReady = true;
        }
    }

    private void startMainWithExtras(Class<?> targetClass) {
        Intent intent = new Intent(this, targetClass);
        if (getIntent().getBooleanExtra("FROM_NOTIFICATION", false)) {
            intent.putExtra("NOTIFICATION_DOC_ID", getIntent().getStringExtra("NOTIFICATION_DOC_ID"));
            intent.putExtra("NOTIFICATION_TITLE", getIntent().getStringExtra("NOTIFICATION_TITLE"));
            intent.putExtra("NOTIFICATION_MESSAGE", getIntent().getStringExtra("NOTIFICATION_MESSAGE"));
            intent.putExtra("NOTIFICATION_TYPE", getIntent().getStringExtra("NOTIFICATION_TYPE"));
            intent.putExtra("NOTIFICATION_BOOKING_ID", getIntent().getStringExtra("NOTIFICATION_BOOKING_ID"));
            intent.putExtra("NOTIFICATION_SENDER_ID", getIntent().getStringExtra("NOTIFICATION_SENDER_ID"));
            intent.putExtra("NOTIFICATION_TIMESTAMP", getIntent().getLongExtra("NOTIFICATION_TIMESTAMP", 0));
            intent.putExtra("FROM_NOTIFICATION", true);
        }
        startActivity(intent);
        finish();
    }

    private void handleAutoLoginFailure(String email, Exception e) {
        String errorMsg = formatError(e);
        // If it's a network issue or Source.SERVER failure, follow the specific teardown request
        if (errorMsg.contains("No internet connection") || (e != null && e.getMessage() != null && e.getMessage().contains("failed to get document"))) {
            clearSavedCredentials();
            mAuth.signOut();
            etLoginEmail.setText(email);
            showErrorBanner("No internet connection. Please check your network.");
        } else {
            // For other errors (like changed password), just clear credentials and stay on login
            clearSavedCredentials();
            mAuth.signOut();
        }
        isReady = true;
    }

    private void saveCredentials(String email, String password) {
        if (encryptedPrefs != null) {
            encryptedPrefs.edit()
                    .putString("email", email)
                    .putString("password", password)
                    .putBoolean("remember_me", true)
                    .apply();
        }
    }

    private void saveProfileData(String uid, String name, String username, String fullname, String role) {
        if (encryptedPrefs != null) {
            encryptedPrefs.edit()
                    .putString("uid", uid)
                    .putString("name", name)
                    .putString("username", username)
                    .putString("fullname", fullname)
                    .putString("role", role)
                    .apply();
        }
    }

    private void clearSavedCredentials() {
        if (encryptedPrefs != null) {
            encryptedPrefs.edit()
                    .remove("email")
                    .remove("password")
                    .remove("uid")
                    .remove("name")
                    .remove("username")
                    .remove("fullname")
                    .putBoolean("remember_me", false)
                    .apply();
        }
    }

    private void handleLogin() {
        String email = etLoginEmail.getText().toString().trim();
        String password = etLoginPassword.getText().toString().trim();

        if (email.isEmpty()) {
            setFieldError(etLoginEmail, getString(R.string.login_error_empty_email));
            etLoginEmail.requestFocus();
            return;
        }
        if (!email.contains("@")) {
            setFieldError(etLoginEmail, "Email missing '@' symbol");
            etLoginEmail.requestFocus();
            return;
        }
        if (!email.contains(".")) {
            setFieldError(etLoginEmail, "Email missing '.' (dot)");
            etLoginEmail.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            setFieldError(etLoginPassword, "Please enter your password");
            etLoginPassword.requestFocus();
            return;
        }
        if (!validatePasswordComplexity(etLoginPassword, password)) return;

        showButtonLoading(true);
        hideKeyboard();

        // 1. Verify if email exists in the core 'users' registry (Firestore) first to determine role and existence
        db.collection("users").whereEqualTo("email", email).limit(1).get(Source.SERVER)
                .addOnCompleteListener(checkTask -> {
                    if (checkTask.isSuccessful()) {
                        boolean emailExists = checkTask.getResult() != null && !checkTask.getResult().isEmpty();
                        String role = emailExists ? checkTask.getResult().getDocuments().get(0).getString("role") : null;
                        boolean shouldMask = "employee".equals(role) || "admin".equals(role);

                        if (!emailExists && !email.contains(".emp") && !email.contains(".adm")) {
                            // Email not found in registry and not a potential first-time employee or admin
                            showButtonLoading(false);
                            setFieldError(etLoginEmail, "Incorrect email");
                            etLoginEmail.requestFocus();
                        } else {
                            // 2. Email is valid (or special .emp case), proceed to sign in via Auth
                            mAuth.signInWithEmailAndPassword(email, password)
                                    .addOnCompleteListener(this, authTask -> {
                                        if (authTask.isSuccessful()) {
                                            FirebaseUser user = mAuth.getCurrentUser();
                                            if (user != null) {
                                                // Check for metadata in 'users' collection to determine role and route
                                                db.collection("users").document(user.getUid()).get(Source.SERVER)
                                                        .addOnCompleteListener(userTask -> {
                                                            if (userTask.isSuccessful()) {
                                                                if (!userTask.getResult().exists()) {
                                                                    if (email.contains(".emp")) {
                                                                        // First-time Employee: Initialize 'users' (metadata) and 'employees' (profile)
                                                                        Map<String, Object> userData = new HashMap<>();
                                                                        userData.put("uid", user.getUid());
                                                                        userData.put("email", email);
                                                                        userData.put("role", "employee");
                                                                        userData.put("createdAt", com.google.firebase.Timestamp.now());

                                                                        Map<String, Object> empProfile = new HashMap<>();
                                                                        empProfile.put("uid", user.getUid());
                                                                        empProfile.put("fullname", "");
                                                                        empProfile.put("phone", "");
                                                                        empProfile.put("shortname", "");
                                                                        empProfile.put("specialty", "");

                                                                        db.collection("users").document(user.getUid()).set(userData)
                                                                                .addOnSuccessListener(aVoid -> {
                                                                                    db.collection("employees").document(user.getUid()).set(empProfile)
                                                                                            .addOnSuccessListener(aVoid2 -> {
                                                                                                if (cbRememberMe.isChecked()) {
                                                                                                    saveCredentials(email, password);
                                                                                                    saveProfileData(user.getUid(), "", "", "", "employee");
                                                                                                } else {
                                                                                                    clearSavedCredentials();
                                                                                                    saveProfileData(user.getUid(), "", "", "", "employee");
                                                                                                }
                                                                                                showButtonLoading(false);
                                                                                                startMainWithExtras(MainActivityEmployee.class);
                                                                                            })
                                                                                            .addOnFailureListener(e -> {
                                                                                                showButtonLoading(false);
                                                                                                showErrorBanner(formatError(e));
                                                                                            });
                                                                                })
                                                                                .addOnFailureListener(e -> {
                                                                                    showButtonLoading(false);
                                                                                    showErrorBanner(formatError(e));
                                                                                });
                                                                    } else if (email.contains(".adm")) {
                                                                        // First-time Admin: Initialize 'users' (metadata) and 'admins' (profile)
                                                                        Map<String, Object> userData = new HashMap<>();
                                                                        userData.put("uid", user.getUid());
                                                                        userData.put("email", email);
                                                                        userData.put("role", "admin");
                                                                        userData.put("createdAt", com.google.firebase.Timestamp.now());

                                                                        Map<String, Object> adminProfile = new HashMap<>();
                                                                        adminProfile.put("uid", user.getUid());
                                                                        adminProfile.put("fullname", "");
                                                                        adminProfile.put("phone", "");
                                                                        adminProfile.put("shortname", "");

                                                                        db.collection("users").document(user.getUid()).set(userData)
                                                                                .addOnSuccessListener(aVoid -> {
                                                                                    db.collection("admins").document(user.getUid()).set(adminProfile)
                                                                                            .addOnSuccessListener(aVoid2 -> {
                                                                                                if (cbRememberMe.isChecked()) {
                                                                                                    saveCredentials(email, password);
                                                                                                    saveProfileData(user.getUid(), "", "", "", "admin");
                                                                                                } else {
                                                                                                    clearSavedCredentials();
                                                                                                    saveProfileData(user.getUid(), "", "", "", "admin");
                                                                                                }
                                                                                                showButtonLoading(false);
                                                                                                startMainWithExtras(MainActivityAdmin.class);
                                                                                            })
                                                                                            .addOnFailureListener(e -> {
                                                                                                showButtonLoading(false);
                                                                                                showErrorBanner(formatError(e));
                                                                                            });
                                                                                })
                                                                                .addOnFailureListener(e -> {
                                                                                    showButtonLoading(false);
                                                                                    showErrorBanner(formatError(e));
                                                                                });
                                                                    } else {
                                                                        // This should not happen if user registered via signup form
                                                                        showButtonLoading(false);
                                                                        showErrorBanner("Access denied: Account metadata missing.");
                                                                    }
                                                                } else {
                                                                    // Existing User: Verify role and email for extra measure
                                                                    String r = userTask.getResult().getString("role");
                                                                    String registeredEmail = userTask.getResult().getString("email");

                                                                    if ("employee".equals(r) && email.equals(registeredEmail)) {
                                                                        // Fetch employee profile
                                                                        db.collection("employees").document(user.getUid()).get(Source.SERVER)
                                                                                .addOnCompleteListener(empTask -> {
                                                                                    showButtonLoading(false);
                                                                                    if (empTask.isSuccessful() && empTask.getResult().exists()) {
                                                                                        String fullname = empTask.getResult().getString("fullname");
                                                                                        String shortname = empTask.getResult().getString("shortname");
                                                                                        if (cbRememberMe.isChecked()) {
                                                                                            saveCredentials(email, password);
                                                                                            saveProfileData(user.getUid(), shortname, "", fullname, "employee");
                                                                                        } else {
                                                                                            clearSavedCredentials();
                                                                                            saveProfileData(user.getUid(), shortname, "", fullname, "employee");
                                                                                        }
                                                                                        startMainWithExtras(MainActivityEmployee.class);
                                                                                    } else {
                                                                                        showErrorBanner("Access denied: Employee profile not found.");
                                                                                    }
                                                                                });
                                                                    } else if ("admin".equals(r) && email.equals(registeredEmail)) {
                                                                        // Fetch admin profile
                                                                        db.collection("admins").document(user.getUid()).get(Source.SERVER)
                                                                                .addOnCompleteListener(adminTask -> {
                                                                                    showButtonLoading(false);
                                                                                    if (adminTask.isSuccessful() && adminTask.getResult().exists()) {
                                                                                        String fullname = adminTask.getResult().getString("fullname");
                                                                                        String shortname = adminTask.getResult().getString("shortname");
                                                                                        if (cbRememberMe.isChecked()) {
                                                                                            saveCredentials(email, password);
                                                                                            saveProfileData(user.getUid(), shortname, "", fullname, "admin");
                                                                                        } else {
                                                                                            clearSavedCredentials();
                                                                                            saveProfileData(user.getUid(), shortname, "", fullname, "admin");
                                                                                        }
                                                                                        startMainWithExtras(MainActivityAdmin.class);
                                                                                    } else {
                                                                                        showErrorBanner("Access denied: Admin profile not found.");
                                                                                    }
                                                                                });
                                                                    } else if ("customer".equals(r)) {
                                                                        // Verify customer profile exists
                                                                        db.collection("customers").document(user.getUid()).get(Source.SERVER)
                                                                                .addOnCompleteListener(custTask -> {
                                                                                    showButtonLoading(false);
                                                                                    if (custTask.isSuccessful() && custTask.getResult().exists()) {
                                                                                        String name = custTask.getResult().getString("name");
                                                                                        String username = custTask.getResult().getString("username");

                                                                                        if (cbRememberMe.isChecked()) {
                                                                                            saveCredentials(email, password);
                                                                                            saveProfileData(user.getUid(), name, username, "", "customer");
                                                                                        } else {
                                                                                            clearSavedCredentials();
                                                                                            // Even if not "remembered", we save for the current session's fragments
                                                                                            saveProfileData(user.getUid(), name, username, "", "customer");
                                                                                        }
                                                                                        startMainWithExtras(MainActivity.class);
                                                                                    } else {
                                                                                        showErrorBanner("Access denied: Customer profile not found.");
                                                                                    }
                                                                                });
                                                                    } else {
                                                                        showButtonLoading(false);
                                                                        showErrorBanner("Access denied: Invalid account configuration.");
                                                                    }
                                                                }
                                                            } else {
                                                                showButtonLoading(false);
                                                                showErrorBanner(formatError(userTask.getException()));
                                                            }
                                                        });
                                            }
                                        } else {
                                            // Handle direct Auth errors (like incorrect password)
                                            showButtonLoading(false);
                                            if (shouldMask) {
                                                // Masked error for employees: prevent account discovery/brute-force feedback
                                                setFieldError(etLoginEmail, "Incorrect email");
                                                etLoginEmail.requestFocus();
                                            } else {
                                                handleAuthError(authTask.getException());
                                            }
                                        }
                                    });
                        }
                    } else {
                        // Technical error during check
                        showButtonLoading(false);
                        showErrorBanner(formatError(checkTask.getException()));
                    }
                });
    }

    private void handleAuthError(Exception e) {
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            setFieldError(etLoginPassword, "Incorrect password");
            etLoginPassword.requestFocus();
        } else if (e instanceof FirebaseAuthInvalidUserException) {
            setFieldError(etLoginEmail, "Incorrect email");
            etLoginEmail.requestFocus();
        } else {
            showErrorBanner(formatError(e));
        }
    }

    private void handleSignupNext() {
        clearAllErrors();
        if (signupStep == 1) {
            if (!validateAccountInfo()) return;
            String email = etSignupEmail.getText().toString().trim();
            String username = etSignupUsername.getText().toString().trim();
            showButtonLoading(true);
            hideKeyboard();

            // Check if email exists in core 'users' registry (Registry for all accounts)
            db.collection("users").whereEqualTo("email", email).get(Source.SERVER).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    if (task.getResult() != null && !task.getResult().isEmpty()) {
                        showButtonLoading(false);
                        setFieldError(etSignupEmail, "Email already registered");
                        etSignupEmail.requestFocus();
                    } else {
                        // Email is available (or collection doesn't exist yet), check username in 'customers'
                        db.collection("customers").whereEqualTo("username", username).get(Source.SERVER).addOnCompleteListener(uTask -> {
                            showButtonLoading(false); // Stop loading before moving or showing error
                            if (uTask.isSuccessful()) {
                                if (uTask.getResult() != null && !uTask.getResult().isEmpty()) {
                                    setFieldError(etSignupUsername, "Username already taken");
                                    etSignupUsername.requestFocus();
                                } else {
                                    // Collection is empty or username available - Proceed
                                    signupStep = 2;
                                    updateUI(true);
                                    etSignupName.requestFocus();
                                }
                            } else {
                                showErrorBanner(formatError(uTask.getException()));
                            }
                        });
                    }
                } else {
                    showButtonLoading(false);
                    showErrorBanner(formatError(task.getException()));
                }
            });
        } else if (signupStep == 2) {
            if (validateIdentityInfo()) { signupStep = 3; updateUI(true); etSignupPassword.requestFocus(); }
        } else if (signupStep == 3) {
            if (validateCredentials()) { hideKeyboard(); clearFocus(); performRegistration(); }
        } else {
            if (signupStep == 4) prepareLoginFromSignup();
            currentMode = AuthMode.LOGIN;
            hideKeyboard();
            clearFocus();
            updateUI(true);
        }
    }

    private void performRegistration() {
        String email = etSignupEmail.getText().toString().trim();
        String password = etSignupPassword.getText().toString().trim();
        showButtonLoading(true);
        mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                FirebaseUser fUser = mAuth.getCurrentUser();
                if (fUser != null) {
                    // Core user record (Metadata)
                    Map<String, Object> userMeta = new HashMap<>();
                    userMeta.put("uid", fUser.getUid());
                    userMeta.put("email", email);
                    userMeta.put("role", "customer");
                    userMeta.put("createdAt", com.google.firebase.Timestamp.now());

                    // Customer profile record
                    Map<String, Object> custProfile = new HashMap<>();
                    custProfile.put("uid", fUser.getUid());
                    custProfile.put("username", etSignupUsername.getText().toString().trim());
                    custProfile.put("name", etSignupName.getText().toString().trim());
                    custProfile.put("phone", etSignupPhone.getText().toString().trim());

                    db.collection("users").document(fUser.getUid()).set(userMeta)
                            .addOnSuccessListener(aVoid -> {
                                db.collection("customers").document(fUser.getUid()).set(custProfile)
                                        .addOnSuccessListener(aVoid2 -> {
                                            saveProfileData(fUser.getUid(), (String) custProfile.get("name"), (String) custProfile.get("username"), "", "customer");
                                            showButtonLoading(false);
                                            signupStep = 4;
                                            updateUI(true);
                                        })
                                        .addOnFailureListener(e -> {
                                            showButtonLoading(false);
                                            signupStep = 5;
                                            String technicalMsg = formatError(e);
                                            tvSignupErrorDetails.setText(technicalMsg);
                                            showErrorBanner(technicalMsg);
                                            updateUI(true);
                                        });
                            })
                            .addOnFailureListener(e -> {
                                showButtonLoading(false);
                                signupStep = 5;
                                String technicalMsg = formatError(e);
                                tvSignupErrorDetails.setText(technicalMsg);
                                showErrorBanner(technicalMsg);
                                updateUI(true);
                            });
                }
            } else {
                showButtonLoading(false);
                signupStep = 5;
                Exception e = task.getException();
                String technicalMsg = formatError(e);
                tvSignupErrorDetails.setText(technicalMsg);
                showErrorBanner(technicalMsg);
                updateUI(true);
            }
        });
    }

    private void handleBackStep() {
        if (currentMode == AuthMode.SIGNUP) {
            if (signupStep == 4 || signupStep == 5) {
                if (signupStep == 4) prepareLoginFromSignup();
                currentMode = AuthMode.LOGIN;
                hideKeyboard();
                clearFocus();
            } else if (signupStep > 1) {
                signupStep--;
            } else {
                currentMode = AuthMode.LOGIN;
                hideKeyboard();
                clearFocus();
            }
            updateUI(true);
        }
    }

    private void showButtonLoading(boolean loading) {
        setInputsEnabled(!loading);
        if (loading) {
            btnAuthAction.setText("");
            btnAuthAction.setIcon(progressDrawable);
            btnAuthAction.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            btnAuthAction.setIconPadding(0);
            btnAuthAction.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.grey_grey)));
            btnAuthAction.setClickable(false);
            btnAuthAction.setFocusable(false);
            progressDrawable.start();
        } else {
            progressDrawable.stop();
            btnAuthAction.setIcon(null);
            btnAuthAction.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_color)));
            btnAuthAction.setClickable(true);
            btnAuthAction.setFocusable(true);
            updateUI(false);
        }
    }

    private void showGlobalLoading(boolean loading) {
        setInputsEnabled(!loading);
        if (layoutLoadingOverlay != null) layoutLoadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void setInputsEnabled(boolean enabled) {
        View[] inputViews = {etLoginEmail, etLoginPassword, etSignupEmail, etSignupUsername, etSignupName, etSignupPhone, etSignupPassword, etSignupConfirm, btnSwitchAuthMode, btnForgotPassword, cbRememberMe, ivAuthBack};
        for (View v : inputViews) {
            if (v != null) {
                v.setEnabled(enabled);
                if (v instanceof EditText) v.setAlpha(enabled ? 1f : 0.8f);
            }
        }
    }

    private void showErrorBanner(String message) {
        tvErrorBannerMsg.setText(message);
        layoutErrorBannerRoot.setVisibility(View.VISIBLE);
        layoutErrorBannerRoot.setAlpha(1f);

        if (radarAnimatorSet == null) {
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(viewRadarPulse, "scaleX", 1f, 6f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(viewRadarPulse, "scaleY", 1f, 3f);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(viewRadarPulse, "alpha", 0.7f, 0f);

            scaleX.setRepeatCount(ValueAnimator.INFINITE);
            scaleY.setRepeatCount(ValueAnimator.INFINITE);
            alpha.setRepeatCount(ValueAnimator.INFINITE);

            radarAnimatorSet = new AnimatorSet();
            radarAnimatorSet.playTogether(scaleX, scaleY, alpha);
            radarAnimatorSet.setDuration(2000);
        }
        if (!radarAnimatorSet.isRunning()) radarAnimatorSet.start();
    }

    private void hideErrorBanner() {
        if (radarAnimatorSet != null) radarAnimatorSet.cancel();
        layoutErrorBannerRoot.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            layoutErrorBannerRoot.setVisibility(View.GONE);
            viewRadarPulse.setScaleX(1f);
            viewRadarPulse.setScaleY(1f);
            viewRadarPulse.setAlpha(0f);
        }).start();
    }

    private String formatError(Exception e) {
        if (e == null) return "An unexpected error occurred. Please try again.";
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("network") || msg.contains("unavailable") || msg.contains("offline") || msg.contains("failed to get document") || msg.contains("grpc")) {
            return "No internet connection. Please check your network.";
        } else if (msg.contains("timeout") || msg.contains("deadline")) {
            return "Connection timed out. Please try again.";
        } else if (msg.contains("quota exceeded") || msg.contains("too many requests")) {
            return "Too many requests. Please try again later.";
        }
        return "Something went wrong. Please try again.";
    }

    private void setFieldError(EditText et, String error) {
        if (et == null) return;
        et.setError(error);
        if (activeErrorCleanups.containsKey(et)) errorCleanupHandler.removeCallbacks(activeErrorCleanups.get(et));
        Runnable r = () -> et.setError(null);
        activeErrorCleanups.put(et, r);
        errorCleanupHandler.postDelayed(r, 5000);
    }

    private void clearAllErrors() {
        hideErrorBanner();
        EditText[] fields = {etLoginEmail, etLoginPassword, etSignupEmail, etSignupUsername, etSignupName, etSignupPhone, etSignupPassword, etSignupConfirm};
        for (EditText et : fields) {
            if (et != null) {
                et.setError(null);
                if (activeErrorCleanups.containsKey(et)) {
                    errorCleanupHandler.removeCallbacks(activeErrorCleanups.get(et));
                    activeErrorCleanups.remove(et);
                }
            }
        }
    }

    private void updateUI(boolean animate) {
        View targetForm; 
        String currentTitle;
        String currentActionText;
        String currentSwitchText; 
        boolean showStepIndicator = false; 
        int actionBtnTopMargin;
        if (currentMode == AuthMode.LOGIN) {
            targetForm = llLoginStep; 
            currentTitle = getString(R.string.auth_login_title); 
            currentActionText = getString(R.string.login_btn_text); 
            currentSwitchText = getString(R.string.login_switch_to_signup); 
            actionBtnTopMargin = 0;
        } else {
            currentTitle = getString(R.string.auth_signup_title); 
            currentSwitchText = getString(R.string.signup_switch_to_login); 
            actionBtnTopMargin = (int) (30 * getResources().getDisplayMetrics().density);
            switch (signupStep) {
                case 1: targetForm = llSignupStep1; showStepIndicator = true; currentActionText = getString(R.string.signup_btn_next); break;
                case 2: targetForm = llSignupStep2; showStepIndicator = true; currentActionText = getString(R.string.signup_btn_next); break;
                case 3: targetForm = llSignupStep3; showStepIndicator = true; currentActionText = getString(R.string.signup_btn_confirm); break;
                case 4: targetForm = llSignupSuccess; currentTitle = getString(R.string.auth_success_title); currentActionText = getString(R.string.signup_btn_back_to_login); break;
                default: targetForm = llSignupFailed; currentTitle = getString(R.string.auth_failed_title); currentActionText = getString(R.string.signup_btn_back_to_login); break;
            }
        }
        if (animate) animateComplexTransition(targetForm, currentTitle, showStepIndicator, currentActionText, currentSwitchText, actionBtnTopMargin);
        else applyUiState(targetForm, currentTitle, showStepIndicator, currentActionText, currentSwitchText, actionBtnTopMargin);
        prevTitle = currentTitle; prevActionText = currentActionText; prevStep = signupStep; prevMode = currentMode;
    }

    private void applyUiState(View targetForm, String title, boolean showStepIndicator, String actionText, String switchText, int actionBtnTopMargin) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) btnAuthAction.getLayoutParams();
        params.topMargin = actionBtnTopMargin;
        btnAuthAction.setLayoutParams(params);
        tvFormTitle.setText(title); tvStepNumber.setText(String.valueOf(signupStep)); btnAuthAction.setText(actionText); btnSwitchAuthMode.setText(switchText);
        llStepIndicator.setVisibility(showStepIndicator ? View.VISIBLE : View.GONE); cbRememberMe.setVisibility(currentMode == AuthMode.LOGIN ? View.VISIBLE : View.GONE);
        llAuthHeader.setVisibility((currentMode == AuthMode.LOGIN && !isHeaderSuppressed) ? View.VISIBLE : View.GONE);
        updateFixedComponentVisibilities();
        llLoginStep.setVisibility(View.INVISIBLE); llSignupStep1.setVisibility(View.INVISIBLE); llSignupStep2.setVisibility(View.INVISIBLE); llSignupStep3.setVisibility(View.INVISIBLE); llSignupSuccess.setVisibility(View.INVISIBLE); llSignupFailed.setVisibility(View.INVISIBLE);
        targetForm.setVisibility(View.VISIBLE); targetForm.setAlpha(1f); targetForm.setTranslationX(0f);
    }

    private void updateFixedComponentVisibilities() {
        if (currentMode == AuthMode.LOGIN) {
            ivAuthBack.setVisibility(View.GONE); btnSwitchAuthMode.setVisibility(View.VISIBLE); btnSwitchAuthMode.setClickable(true); tvSignupErrorDetails.setVisibility(View.GONE);
        } else {
            if (signupStep == 1) { ivAuthBack.setVisibility(View.INVISIBLE); ivAuthBack.setEnabled(false); btnSwitchAuthMode.setVisibility(View.VISIBLE); btnSwitchAuthMode.setClickable(true); tvSignupErrorDetails.setVisibility(View.GONE); }
            else if (signupStep == 4 || signupStep == 5) { ivAuthBack.setVisibility(View.GONE); btnSwitchAuthMode.setVisibility(View.INVISIBLE); btnSwitchAuthMode.setClickable(false); tvSignupErrorDetails.setVisibility(signupStep == 5 ? View.VISIBLE : View.GONE); }
            else { ivAuthBack.setVisibility(View.VISIBLE); ivAuthBack.setEnabled(true); btnSwitchAuthMode.setVisibility(View.INVISIBLE); btnSwitchAuthMode.setClickable(false); tvSignupErrorDetails.setVisibility(View.GONE); }
        }
    }

    private void animateComplexTransition(View targetForm, String newTitle, boolean showIndicator, String newActionText, String newSwitchText, int actionBtnTopMargin) {
        int direction = 1;
        if (prevMode == AuthMode.SIGNUP && currentMode == AuthMode.LOGIN) direction = -1;
        else if (currentMode == AuthMode.SIGNUP && prevMode == AuthMode.SIGNUP && signupStep < prevStep) direction = -1;
        final float slideOffset = 50f * direction;
        
        final boolean isModeChange = prevMode != currentMode;
        final boolean isEnteringResult = currentMode == AuthMode.SIGNUP && (signupStep == 4 || signupStep == 5) && prevStep == 3;
        final int capturedPrevStep = prevStep;
        final int capturedCurrentStep = signupStep;

        if (isModeChange || isEnteringResult) {
            animateOut(mcvAuthContainer, slideOffset); animateOut(btnSwitchAuthMode, slideOffset);
            if (prevMode == AuthMode.LOGIN && currentMode == AuthMode.SIGNUP && llAuthHeader.getVisibility() == View.VISIBLE) animateOutToGone(llAuthHeader, slideOffset);
            mcvAuthContainer.postDelayed(() -> {
                applyUiState(targetForm, newTitle, showIndicator, newActionText, newSwitchText, actionBtnTopMargin);
                resetAllAnimations();
                if (currentMode == AuthMode.LOGIN && !isHeaderSuppressed) animateIn(llAuthHeader, slideOffset);
                animateIn(mcvAuthContainer, slideOffset);
                if (btnSwitchAuthMode.getVisibility() == View.VISIBLE) animateIn(btnSwitchAuthMode, slideOffset);
            }, 250);
            return;
        }
        
        View currentForm = getVisibleForm();
        if (currentForm != null && currentForm != targetForm) {
            animateOut(currentForm, slideOffset);

            boolean stepChanged = (currentMode == AuthMode.SIGNUP && prevMode == AuthMode.SIGNUP && capturedCurrentStep != capturedPrevStep);
            if (stepChanged) {
                animateOut(tvStepNumber, slideOffset);
                if (capturedCurrentStep == 1 && capturedPrevStep == 2) animateOut(ivAuthBack, slideOffset);
            }

            targetForm.postDelayed(() -> {
                applyUiState(targetForm, newTitle, showIndicator, newActionText, newSwitchText, actionBtnTopMargin);
                animateIn(targetForm, slideOffset);

                if (stepChanged) {
                    animateIn(tvStepNumber, slideOffset);
                    if (capturedCurrentStep == 2 && capturedPrevStep == 1) animateIn(ivAuthBack, slideOffset);
                }
            }, 250);
        } else {
            targetForm.setVisibility(View.VISIBLE); targetForm.setAlpha(1f); targetForm.setTranslationX(0f);
        }
    }

    private void resetAllAnimations() {
        View[] views = {tvFormTitle, tvStepNumber, llStepIndicator, btnAuthAction, btnSwitchAuthMode, ivAuthBack, cbRememberMe, llLoginStep, llSignupStep1, llSignupStep2, llSignupStep3, llSignupSuccess, llSignupFailed};
        for (View v : views) { if (v != null) { v.setAlpha(1f); v.setTranslationX(0f); } }
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
        v.animate().alpha(0f).translationX(-offset).setDuration(200).withEndAction(() -> { v.setVisibility(View.INVISIBLE); v.setTranslationX(0f); }).start();
    }

    private void animateOutToGone(View v, float offset) {
        v.animate().alpha(0f).translationX(-offset).setDuration(200).withEndAction(() -> { v.setVisibility(View.GONE); v.setTranslationX(0f); }).start();
    }

    private void animateIn(View v, float offset) {
        v.setAlpha(0f); v.setTranslationX(offset); v.setVisibility(View.VISIBLE);
        v.animate().alpha(1f).translationX(0f).setDuration(200).start();
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void clearFocus() {
        View view = this.getCurrentFocus();
        if (view != null) view.clearFocus();
    }

    private boolean validateAccountInfo() {
        String email = etSignupEmail.getText().toString().trim();
        String username = etSignupUsername.getText().toString().trim();
        if (email.isEmpty()) { setFieldError(etSignupEmail, getString(R.string.signup_error_email_required)); etSignupEmail.requestFocus(); return false; }
        if (!email.contains("@")) { setFieldError(etSignupEmail, "Email missing '@' symbol"); etSignupEmail.requestFocus(); return false; }
        if (!email.contains(".")) { setFieldError(etSignupEmail, "Email missing '.' (dot)"); etSignupEmail.requestFocus(); return false; }
        if (username.isEmpty()) { setFieldError(etSignupUsername, getString(R.string.signup_error_username_required)); etSignupUsername.requestFocus(); return false; }
        if (username.length() > 16) { setFieldError(etSignupUsername, "Username must be 16 characters or less"); etSignupUsername.requestFocus(); return false; }
        if (!username.matches("^[a-zA-Z0-9]+$")) { setFieldError(etSignupUsername, "Username can only contain letters and numbers (no spaces or special characters)"); etSignupUsername.requestFocus(); return false; }
        return true;
    }

    private boolean validateIdentityInfo() {
        String name = etSignupName.getText().toString().trim();
        String phone = etSignupPhone.getText().toString().trim();
        if (name.isEmpty()) { setFieldError(etSignupName, getString(R.string.signup_error_name_required)); etSignupName.requestFocus(); return false; }
        if (!name.matches("^[a-zA-Z\\s@\\-]+$")) { setFieldError(etSignupName, "Name can only contain letters, spaces, @ and -"); etSignupName.requestFocus(); return false; }
        if (phone.isEmpty()) { setFieldError(etSignupPhone, getString(R.string.signup_error_phone_required)); etSignupPhone.requestFocus(); return false; }
        if (!phone.matches("[0-9]+")) { setFieldError(etSignupPhone, "Phone number can only contain digits"); etSignupPhone.requestFocus(); return false; }
        if (phone.length() < 9) { setFieldError(etSignupPhone, "Phone number must be at least 9 digits"); etSignupPhone.requestFocus(); return false; }
        return true;
    }

    private boolean validateCredentials() {
        String pass = etSignupPassword.getText().toString();
        String confirm = etSignupConfirm.getText().toString();
        if (pass.isEmpty()) { setFieldError(etSignupPassword, getString(R.string.signup_error_password_required)); etSignupPassword.requestFocus(); return false; }
        if (!validatePasswordComplexity(etSignupPassword, pass)) return false;
        if (!pass.equals(confirm)) { setFieldError(etSignupConfirm, getString(R.string.signup_error_password_mismatch)); etSignupConfirm.requestFocus(); return false; }
        return true;
    }

    private boolean validatePasswordComplexity(EditText editText, String password) {
        if (password.length() < 8) { setFieldError(editText, "Password must be at least 8 characters"); editText.requestFocus(); return false; }
        if (!password.matches(".*[A-Z].*")) { setFieldError(editText, "Missing uppercase letter"); editText.requestFocus(); return false; }
        if (!password.matches(".*[a-z].*")) { setFieldError(editText, "Missing lowercase letter"); editText.requestFocus(); return false; }
        if (!password.matches(".*[0-9].*")) { setFieldError(editText, "Missing a number"); editText.requestFocus(); return false; }
        return true;
    }

    private void prepareLoginFromSignup() {
        String email = etSignupEmail.getText().toString().trim();
        etLoginEmail.setText(email);
        etLoginPassword.setText("");
        EditText[] fields = {etSignupEmail, etSignupUsername, etSignupName, etSignupPhone, etSignupPassword, etSignupConfirm};
        for (EditText f : fields) f.setText("");
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

    private void checkHeaderOverlap() {
        if (mcvAuthContainer.getHeight() == 0) return;
        int containerTop = mcvAuthContainer.getTop();
        if (containerTop <= 0) return;
        llAuthHeader.measure(View.MeasureSpec.makeMeasureSpec(mcvAuthContainer.getWidth(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int headerHeight = llAuthHeader.getMeasuredHeight();
        float margin = 12 * getResources().getDisplayMetrics().density;
        boolean shouldSuppress = headerHeight > 0 && (headerHeight + margin > containerTop);
        if (shouldSuppress != isHeaderSuppressed) {
            isHeaderSuppressed = shouldSuppress;
            if (currentMode == AuthMode.LOGIN) llAuthHeader.setVisibility(isHeaderSuppressed ? View.GONE : View.VISIBLE);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupPasswordVisibilityToggle(EditText editText) {
        editText.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                // Check if the tap is on the right drawable (index 2)
                if (editText.getCompoundDrawables()[2] != null) {
                    int drawableWidth = editText.getCompoundDrawables()[2].getBounds().width();
                    if (event.getRawX() >= (editText.getRight() - drawableWidth - editText.getPaddingEnd())) {
                        togglePasswordVisibility(editText);
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private void togglePasswordVisibility(EditText editText) {
        int selection = editText.getSelectionEnd();
        if (editText.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_visibility, 0);
        } else {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_visibility_off, 0);
        }
        editText.setSelection(selection);
        // Force the bold typeface to remain active
        editText.setTypeface(editText.getTypeface(), Typeface.BOLD);
    }
}
