package com.zejyv.azizul.uitm.fadebarber;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ProfileEditActivity extends AppCompatActivity {

    private EditText etFullname, etUsername, etSpecialty, etPhone, etEmail, etPassword, etConfirmPassword;
    private View mcvSpecialty;
    private View loadingOverlay;
    
    private ImageView ivProfileLarge;
    private com.google.android.material.button.MaterialButton btnUploadPicture;
    private Uri selectedImageUri;

    // Status Overlay components
    private View layoutStatusOverlay, mcvStatusDialog;
    private ImageView ivStatusIcon;
    private TextView tvStatusTitle, tvStatusMessage;
    private com.google.android.material.button.MaterialButton btnStatusOk;

    // Re-auth Overlay components
    private View layoutReauthConfirmation, mcvReauthDialog;
    private EditText etReauthPassword;
    private com.google.android.material.button.MaterialButton btnReauthCancel, btnReauthConfirm;

    // Delete Account components
    private com.google.android.material.button.MaterialButton btnDeleteAccount;
    private View layoutDeleteConfirmation, mcvDeleteDialog;
    private com.google.android.material.checkbox.MaterialCheckBox cbDeleteConfirm;
    private com.google.android.material.button.MaterialButton btnDeleteCancel, btnDeleteConfirmAction;

    private View layoutReauthDelete, mcvReauthDeleteDialog;
    private EditText etReauthDeletePassword;
    private com.google.android.material.button.MaterialButton btnReauthDeleteCancel, btnReauthDeleteConfirm;

    private boolean isEmployee = false;
    private boolean isAdmin = false;
    private boolean isFirstTime = false;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String userId;

    private String initialFullname, initialUsername, initialSpecialty, initialPhone, initialEmail;

    private final Handler errorCleanupHandler = new Handler(Looper.getMainLooper());
    private final Map<EditText, Runnable> activeErrorCleanups = new HashMap<>();

    private final ActivityResultLauncher<Intent> cropMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri croppedUri = result.getData().getData();
                    if (croppedUri != null) {
                        ivProfileLarge.setImageURI(null);
                        ivProfileLarge.setImageURI(croppedUri);
                        selectedImageUri = croppedUri;
                    }
                }
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    String mimeType = getContentResolver().getType(uri);
                    if (mimeType != null && mimeType.contains("gif")) {
                        Toast.makeText(this, "GIFs are not supported. Please select a static image.", Toast.LENGTH_SHORT).show();
                    } else {
                        Intent intent = new Intent(this, ImageCropActivity.class);
                        intent.setData(uri);
                        cropMediaLauncher.launch(intent);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_edit);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            userId = user.getUid();
        }

        isFirstTime = getIntent().getBooleanExtra("IS_FIRST_TIME", false);

        initializeViews();
        checkUserTypeAndLoadData();
        setupActions();
    }

    private void initializeViews() {
        etFullname = findViewById(R.id.et_profile_fullname);
        etUsername = findViewById(R.id.et_profile_username);
        etSpecialty = findViewById(R.id.et_profile_specialty);
        etPhone = findViewById(R.id.et_profile_phone);
        etEmail = findViewById(R.id.et_profile_email);
        etPassword = findViewById(R.id.et_profile_password);
        etConfirmPassword = findViewById(R.id.et_profile_confirm_password);
        mcvSpecialty = findViewById(R.id.mcv_profile_specialty);
        loadingOverlay = findViewById(R.id.loading_overlay);
        ivProfileLarge = findViewById(R.id.iv_profile_large);
        btnUploadPicture = findViewById(R.id.btn_upload_picture);

        // Status Overlay
        layoutStatusOverlay = findViewById(R.id.layout_status_overlay);
        mcvStatusDialog = findViewById(R.id.mcv_status_dialog);
        ivStatusIcon = findViewById(R.id.iv_status_icon);
        tvStatusTitle = findViewById(R.id.tv_status_title);
        tvStatusMessage = findViewById(R.id.tv_status_message);
        btnStatusOk = findViewById(R.id.btn_status_ok);

        // Re-auth Overlay
        layoutReauthConfirmation = findViewById(R.id.layout_reauth_confirmation);
        mcvReauthDialog = findViewById(R.id.mcv_reauth_dialog);
        etReauthPassword = findViewById(R.id.et_reauth_password);
        btnReauthCancel = findViewById(R.id.btn_reauth_cancel);
        btnReauthConfirm = findViewById(R.id.btn_reauth_confirm);

        btnDeleteAccount = findViewById(R.id.btn_delete_account);

        // Delete Confirmation
        layoutDeleteConfirmation = findViewById(R.id.layout_delete_confirmation);
        mcvDeleteDialog = findViewById(R.id.mcv_delete_dialog);
        cbDeleteConfirm = findViewById(R.id.cb_delete_confirm);
        btnDeleteCancel = findViewById(R.id.btn_delete_cancel);
        btnDeleteConfirmAction = findViewById(R.id.btn_delete_confirm_action);

        // Re-auth Delete
        layoutReauthDelete = findViewById(R.id.layout_reauth_delete);
        mcvReauthDeleteDialog = findViewById(R.id.mcv_reauth_delete_dialog);
        etReauthDeletePassword = findViewById(R.id.et_reauth_delete_password);
        btnReauthDeleteCancel = findViewById(R.id.btn_reauth_delete_cancel);
        btnReauthDeleteConfirm = findViewById(R.id.btn_reauth_delete_confirm);

        ImageView btnBack = findViewById(R.id.iv_back_profile_edit);
        btnBack.setOnClickListener(v -> finish());
        
        if (isFirstTime) {
            btnBack.setVisibility(View.GONE);
            TextView tvTitle = findViewById(R.id.tv_title_profile_edit);
            if (tvTitle != null) tvTitle.setText("First Time Login");
        }
        
        setupPasswordVisibilityToggle(etPassword);
        setupPasswordVisibilityToggle(etConfirmPassword);
        setupPasswordVisibilityToggle(etReauthPassword);
        setupPasswordVisibilityToggle(etReauthDeletePassword);

        setupFocusValidation();

        if (btnStatusOk != null) {
            btnStatusOk.setOnClickListener(v -> {
                hideStatusOverlay();
                if (tvStatusTitle.getText().toString().contains("Successful")) {
                    finish();
                }
            });
        }

        if (btnReauthCancel != null) btnReauthCancel.setOnClickListener(v -> hideReauthOverlay());
        if (btnReauthConfirm != null) btnReauthConfirm.setOnClickListener(v -> proceedWithValidatedUpdate());

        if (btnDeleteAccount != null) btnDeleteAccount.setOnClickListener(v -> showDeleteConfirmation());
        if (btnDeleteCancel != null) btnDeleteCancel.setOnClickListener(v -> hideDeleteConfirmation());
        
        if (cbDeleteConfirm != null) {
            cbDeleteConfirm.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (btnDeleteConfirmAction != null) {
                    btnDeleteConfirmAction.setAlpha(isChecked ? 1.0f : 0.5f);
                }
            });
        }

        if (btnDeleteConfirmAction != null) {
            btnDeleteConfirmAction.setOnClickListener(v -> {
                if (cbDeleteConfirm.isChecked()) {
                    hideDeleteConfirmation();
                    showReauthDelete();
                } else {
                    Toast.makeText(this, "Please confirm by checking the box first.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnReauthDeleteCancel != null) btnReauthDeleteCancel.setOnClickListener(v -> hideReauthDelete());
        if (btnReauthDeleteConfirm != null) btnReauthDeleteConfirm.setOnClickListener(v -> proceedWithAccountDeletion());

        // Dismiss overlays on outside click
        if (layoutReauthConfirmation != null) layoutReauthConfirmation.setOnClickListener(v -> hideReauthOverlay());
        if (layoutDeleteConfirmation != null) layoutDeleteConfirmation.setOnClickListener(v -> hideDeleteConfirmation());
        if (layoutReauthDelete != null) layoutReauthDelete.setOnClickListener(v -> hideReauthDelete());
        if (layoutStatusOverlay != null) layoutStatusOverlay.setOnClickListener(v -> hideStatusOverlay());

        // Prevent dismissal when clicking inside the dialog cards
        if (mcvReauthDialog != null) mcvReauthDialog.setOnClickListener(v -> {});
        if (mcvDeleteDialog != null) mcvDeleteDialog.setOnClickListener(v -> {});
        if (mcvReauthDeleteDialog != null) mcvReauthDeleteDialog.setOnClickListener(v -> {});
        if (mcvStatusDialog != null) mcvStatusDialog.setOnClickListener(v -> {});
    }

    @Override
    public void onBackPressed() {
        if (layoutStatusOverlay != null && layoutStatusOverlay.getVisibility() == View.VISIBLE) {
            hideStatusOverlay();
        } else if (layoutReauthDelete != null && layoutReauthDelete.getVisibility() == View.VISIBLE) {
            hideReauthDelete();
        } else if (layoutDeleteConfirmation != null && layoutDeleteConfirmation.getVisibility() == View.VISIBLE) {
            hideDeleteConfirmation();
        } else if (layoutReauthConfirmation != null && layoutReauthConfirmation.getVisibility() == View.VISIBLE) {
            hideReauthOverlay();
        } else if (isFirstTime) {
            // Prevent going back during first-time setup
            Toast.makeText(this, "Please complete your profile setup first.", Toast.LENGTH_SHORT).show();
        } else {
            super.onBackPressed();
        }
    }

    private void setupPasswordVisibilityToggle(EditText editText) {
        editText.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
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
        editText.setTypeface(editText.getTypeface(), Typeface.BOLD);
    }

    private void checkUserTypeAndLoadData() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            android.content.SharedPreferences prefs = EncryptedSharedPreferences.create(
                    this,
                    "secret_shared_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            String role = prefs.getString("role", "customer");
            isEmployee = "employee".equals(role);
            isAdmin = "admin".equals(role);

            if (btnDeleteAccount != null) {
                btnDeleteAccount.setVisibility((isEmployee || isAdmin) ? View.GONE : View.VISIBLE);
            }

            if (isEmployee) {
                mcvSpecialty.setVisibility(View.VISIBLE);
                loadEmployeeData();
            } else if (isAdmin) {
                mcvSpecialty.setVisibility(View.GONE);
                loadAdminData();
            } else {
                mcvSpecialty.setVisibility(View.GONE);
                loadCustomerData();
            }

            // Always fetch latest from Firestore instead of using cached URL
            fetchLatestProfilePic();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            loadCustomerData();
        }
    }

    private void loadAdminData() {
        if (userId == null) return;
        loadingOverlay.setVisibility(View.VISIBLE);
        db.collection("admins").document(userId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                initialFullname = doc.getString("fullname");
                initialUsername = doc.getString("shortname");
                initialPhone = doc.getString("phone");
                initialEmail = mAuth.getCurrentUser().getEmail();

                etFullname.setText(initialFullname);
                etUsername.setText(initialUsername);
                etPhone.setText(initialPhone);
                etEmail.setText(initialEmail);
            }
            fetchLatestProfilePic();
        }).addOnFailureListener(e -> loadingOverlay.setVisibility(View.GONE));
    }

    private void loadCustomerData() {
        if (userId == null) return;
        loadingOverlay.setVisibility(View.VISIBLE);
        db.collection("customers").document(userId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                initialFullname = doc.getString("name");
                initialUsername = doc.getString("username");
                initialPhone = doc.getString("phone");
                initialEmail = mAuth.getCurrentUser().getEmail();

                etFullname.setText(initialFullname);
                etUsername.setText(initialUsername);
                etPhone.setText(initialPhone);
                etEmail.setText(initialEmail);
            }
            fetchLatestProfilePic();
        }).addOnFailureListener(e -> loadingOverlay.setVisibility(View.GONE));
    }

    private void loadEmployeeData() {
        if (userId == null) return;
        loadingOverlay.setVisibility(View.VISIBLE);
        db.collection("employees").document(userId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                initialFullname = doc.getString("fullname");
                initialUsername = doc.getString("shortname");
                initialSpecialty = doc.getString("specialty");
                initialPhone = doc.getString("phone");
                initialEmail = mAuth.getCurrentUser().getEmail();

                etFullname.setText(initialFullname);
                etUsername.setText(initialUsername);
                etSpecialty.setText(initialSpecialty);
                etPhone.setText(initialPhone);
                etEmail.setText(initialEmail);
            }
            fetchLatestProfilePic();
        }).addOnFailureListener(e -> loadingOverlay.setVisibility(View.GONE));
    }

    private void fetchLatestProfilePic() {
        db.collection("profile_pics").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    loadingOverlay.setVisibility(View.GONE);
                    if (documentSnapshot.exists()) {
                        String url = documentSnapshot.getString("url");
                        if (url != null && !url.isEmpty()) {
                            Glide.with(this).load(url).placeholder(R.drawable.ic_profile).into(ivProfileLarge);
                            saveUrlToLocalPrefs(url);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    loadingOverlay.setVisibility(View.GONE);
                    Log.e("ProfileEdit", "Error fetching pic: " + e.getMessage());
                });
    }

    private void saveUrlToLocalPrefs(String url) {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            android.content.SharedPreferences prefs = EncryptedSharedPreferences.create(
                    this, "secret_shared_prefs", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            prefs.edit().putString("profile_pic_url", url).apply();
        } catch (Exception ignored) {}
    }

    private void setupActions() {
        findViewById(R.id.btn_save_profile).setOnClickListener(v -> saveProfileChanges());
        if (btnUploadPicture != null) {
            btnUploadPicture.setOnClickListener(v -> pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build()));
        }
    }

    private void setupFocusValidation() {
        etFullname.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) validateFullName(false);
        });
        etUsername.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) validateUsername(false);
        });
        etSpecialty.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && isEmployee) validateSpecialty(false);
        });
        etPhone.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) validatePhone(false);
        });
        etEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) validateEmail(false);
        });
        etPassword.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) validatePassword(false);
        });
        etConfirmPassword.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) validateConfirmPassword(false);
        });
    }

    private boolean validateFullName(boolean requestFocus) {
        String name = etFullname.getText().toString().trim();
        if (name.isEmpty()) { 
            setFieldError(etFullname, getString(R.string.signup_error_name_required), requestFocus); 
            return false; 
        }
        if (!name.matches("^[a-zA-Z\\s@\\-]+$")) { 
            setFieldError(etFullname, "Name can only contain letters, spaces, @ and -", requestFocus); 
            return false; 
        }
        return true;
    }

    private boolean validateUsername(boolean requestFocus) {
        String user = etUsername.getText().toString().trim();
        if (user.isEmpty()) { 
            setFieldError(etUsername, getString(R.string.signup_error_username_required), requestFocus); 
            return false; 
        }
        if (user.length() > 16) { 
            setFieldError(etUsername, "Username must be 16 characters or less", requestFocus); 
            return false; 
        }
        if (!user.matches("^[a-zA-Z0-9]+$")) { 
            setFieldError(etUsername, "Username can only contain letters and numbers (no spaces or special characters)", requestFocus); 
            return false; 
        }
        return true;
    }

    private boolean validateSpecialty(boolean requestFocus) {
        // Specialty is now optional for employees.
        return true;
    }

    private boolean validatePhone(boolean requestFocus) {
        String phone = etPhone.getText().toString().trim();
        if (phone.isEmpty()) { 
            setFieldError(etPhone, getString(R.string.signup_error_phone_required), requestFocus); 
            return false; 
        }
        if (!phone.matches("[0-9]+")) { 
            setFieldError(etPhone, "Phone number can only contain digits", requestFocus); 
            return false; 
        }
        if (phone.length() < 9) { 
            setFieldError(etPhone, "Phone number must be at least 9 digits", requestFocus);
            return false; 
        }
        return true;
    }

    private boolean validateEmail(boolean requestFocus) {
        String email = etEmail.getText().toString().trim();
        if (email.isEmpty()) { 
            setFieldError(etEmail, getString(R.string.signup_error_email_required), requestFocus); 
            return false; 
        }
        if (!email.contains("@")) { 
            setFieldError(etEmail, "Email missing '@' symbol", requestFocus); 
            return false; 
        }
        if (!email.contains(".")) { 
            setFieldError(etEmail, "Email missing '.' (dot)", requestFocus); 
            return false; 
        }
        return true;
    }

    private boolean validatePassword(boolean requestFocus) {
        String password = etPassword.getText().toString();
        if (!password.isEmpty()) {
            return validatePasswordComplexity(etPassword, password, requestFocus);
        }
        return true;
    }

    private boolean validateConfirmPassword(boolean requestFocus) {
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();
        if (!password.isEmpty() && !password.equals(confirmPassword)) {
            setFieldError(etConfirmPassword, getString(R.string.signup_error_password_mismatch), requestFocus);
            return false;
        }
        return true;
    }

    private void saveProfileChanges() {
        clearAllErrors();
        
        if (!validateFullName(true)) return;
        if (!validateUsername(true)) return;
        if (isEmployee && !validateSpecialty(true)) return;
        if (!validatePhone(true)) return;
        if (!validateEmail(true)) return;
        if (!validatePassword(true)) return;
        if (!validateConfirmPassword(true)) return;

        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        // Sensitive changes detection (Email or Password)
        FirebaseUser fUser = mAuth.getCurrentUser();
        boolean emailChanged = fUser != null && !email.equals(fUser.getEmail());
        boolean passwordChanged = !password.isEmpty();

        if (emailChanged || passwordChanged) {
            showReauthOverlay();
        } else {
            // Only profile info changes, no re-auth needed
            performProfileUpdate();
        }
    }

    private boolean validatePasswordComplexity(EditText editText, String password, boolean requestFocus) {
        if (password.length() < 8) { 
            setFieldError(editText, "Password must be at least 8 characters", requestFocus); 
            return false; 
        }
        if (!password.matches(".*[A-Z].*")) { 
            setFieldError(editText, "Missing uppercase letter", requestFocus); 
            return false; 
        }
        if (!password.matches(".*[a-z].*")) { 
            setFieldError(editText, "Missing lowercase letter", requestFocus); 
            return false; 
        }
        if (!password.matches(".*[0-9].*")) { 
            setFieldError(editText, "Missing a number", requestFocus); 
            return false; 
        }
        return true;
    }

    private void showReauthOverlay() {
        if (layoutReauthConfirmation == null || mcvReauthDialog == null) return;
        etReauthPassword.setText("");
        layoutReauthConfirmation.setVisibility(View.VISIBLE);
        layoutReauthConfirmation.setAlpha(0f);
        layoutReauthConfirmation.animate().alpha(1f).setDuration(200).start();
        mcvReauthDialog.setScaleX(0f);
        mcvReauthDialog.setScaleY(0f);
        mcvReauthDialog.animate().scaleX(1f).scaleY(1f).setDuration(300).withEndAction(() -> {
            etReauthPassword.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etReauthPassword, InputMethodManager.SHOW_IMPLICIT);
        }).start();
    }

    private void hideReauthOverlay() {
        if (layoutReauthConfirmation == null || mcvReauthDialog == null) return;
        hideKeyboard();
        mcvReauthDialog.animate().scaleX(0f).scaleY(0f).setDuration(200).start();
        layoutReauthConfirmation.animate().alpha(0f).setDuration(200).withEndAction(() -> layoutReauthConfirmation.setVisibility(View.GONE)).start();
    }

    private void proceedWithValidatedUpdate() {
        String currentPassword = etReauthPassword.getText().toString();
        if (currentPassword.isEmpty()) {
            etReauthPassword.setError("Password required to verify");
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        hideReauthOverlay();
        hideKeyboard();
        loadingOverlay.setVisibility(View.VISIBLE);

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential).addOnSuccessListener(aVoid -> {
            performProfileUpdate();
        }).addOnFailureListener(e -> {
            loadingOverlay.setVisibility(View.GONE);
            showStatusOverlay(false, "Verification Failed", "Incorrect current password or verification error.");
        });
    }

    private void performProfileUpdate() {
        hideKeyboard();
        loadingOverlay.setVisibility(View.VISIBLE);

        if (selectedImageUri != null) {
            uploadImageToImgBB();
        } else {
            proceedWithFirestoreUpdate(null);
        }
    }

    private void uploadImageToImgBB() {
        String imageId = UUID.randomUUID().toString().substring(0, 8);
        long timestamp = System.currentTimeMillis();
        String fileName = String.format("%s_%s_%d", imageId, userId, timestamp);

        OkHttpClient client = new OkHttpClient();
        
        try {
            InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
            if (inputStream == null) {
                loadingOverlay.setVisibility(View.GONE);
                showStatusOverlay(false, "Upload Error", "Failed to read image file.");
                return;
            }
            
            byte[] bytes = getBytes(inputStream);
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("key", BuildConfig.IMGBB_API_KEY)
                    .addFormDataPart("image", fileName, RequestBody.create(bytes, okhttp3.MediaType.parse("image/*")))
                    .addFormDataPart("name", fileName)
                    .build();

            Request request = new Request.Builder()
                    .url("https://api.imgbb.com/1/upload")
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        loadingOverlay.setVisibility(View.GONE);
                        showStatusOverlay(false, "Upload Failed", "Connection error: " + e.getMessage());
                        sendProfileUpdateNotification(false, "Image upload connection error.");
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful() && responseBody.contains("\"url\":\"")) {
                        // Extract URL from JSON
                        String url = responseBody.split("\"url\":\"")[1].split("\"")[0].replace("\\/", "/");
                        runOnUiThread(() -> saveToProfilePicsAndContinue(imageId, url, timestamp));
                    } else {
                        runOnUiThread(() -> {
                            loadingOverlay.setVisibility(View.GONE);
                            Log.e("ImgBB", "Error response: " + responseBody);
                            showStatusOverlay(false, "Upload Failed", "ImgBB service error.");
                            sendProfileUpdateNotification(false, "ImgBB service error.");
                        });
                    }
                }
            });
        } catch (IOException e) {
            loadingOverlay.setVisibility(View.GONE);
            showStatusOverlay(false, "Upload Error", e.getMessage());
        }
    }

    private byte[] getBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    private void saveToProfilePicsAndContinue(String imageId, String url, long timestamp) {
        db.collection("profile_pics").document(userId).get().addOnCompleteListener(task -> {
            Map<String, Object> picData = new HashMap<>();
            picData.put("uid", userId);
            picData.put("imageid", imageId);
            picData.put("url", url);
            picData.put("updatedat", timestamp);

            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                // If it already exists, keep the original createdat
                Object createdAt = task.getResult().get("createdat");
                if (createdAt != null) {
                    picData.put("createdat", createdAt);
                } else {
                    picData.put("createdat", timestamp);
                }
            } else {
                // First time upload
                picData.put("createdat", timestamp);
            }

            db.collection("profile_pics").document(userId).set(picData)
                .addOnSuccessListener(aVoid -> proceedWithFirestoreUpdate(url))
                .addOnFailureListener(e -> {
                    loadingOverlay.setVisibility(View.GONE);
                    showStatusOverlay(false, "Database Error", "Failed to save image metadata.");
                    sendProfileUpdateNotification(false, "Failed to save image metadata.");
                });
        });
    }

    private void proceedWithFirestoreUpdate(String imageUrl) {
        String name = etFullname.getText().toString().trim();
        String user = etUsername.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        FirebaseUser fUser = mAuth.getCurrentUser();
        boolean emailChanged = fUser != null && !email.equals(fUser.getEmail());

        Map<String, Object> updates = new HashMap<>();
        String collection;
        if (isEmployee) collection = "employees";
        else if (isAdmin) collection = "admins";
        else collection = "customers";

        if (isEmployee) {
            updates.put("fullname", name);
            updates.put("shortname", user);
            updates.put("specialty", etSpecialty.getText().toString().trim());
        } else if (isAdmin) {
            updates.put("fullname", name);
            updates.put("shortname", user);
        } else {
            updates.put("name", name);
            updates.put("username", user);
        }
        updates.put("phone", phone);
        updates.put("email", email);

        db.collection(collection).document(userId).update(updates)
            .addOnSuccessListener(aVoid -> {
                // Update Users collection as well
                Map<String, Object> userUpdate = new HashMap<>();
                userUpdate.put("email", email);
                db.collection("users").document(userId).update(userUpdate);

                saveUpdatesToLocalPrefs(name, user, email, imageUrl);

                if (emailChanged || !password.isEmpty()) {
                    finalizeAuthUpdates(email, password);
                } else {
                    loadingOverlay.setVisibility(View.GONE);
                    showStatusOverlay(true, "Update Successful!", "Your profile has been updated successfully.");
                    sendProfileUpdateNotification(true, null);
                }
            })
            .addOnFailureListener(e -> {
                loadingOverlay.setVisibility(View.GONE);
                showStatusOverlay(false, "Update Failed", e.getMessage());
                sendProfileUpdateNotification(false, e.getMessage());
            });
    }

    private void finalizeAuthUpdates(String newEmail, String newPassword) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            loadingOverlay.setVisibility(View.GONE);
            return;
        }

        if (!newEmail.equals(user.getEmail()) && !newPassword.isEmpty()) {
            user.updateEmail(newEmail).addOnSuccessListener(v1 -> {
                user.updatePassword(newPassword).addOnSuccessListener(v2 -> {
                    sendProfileUpdateNotification(true, null);
                    forceLogout();
                }).addOnFailureListener(e -> {
                    loadingOverlay.setVisibility(View.GONE);
                    showStatusOverlay(false, "Auth Error", "Email updated, but password failed.");
                    sendProfileUpdateNotification(false, "Email updated, but password failed.");
                });
            }).addOnFailureListener(e -> {
                loadingOverlay.setVisibility(View.GONE);
                showStatusOverlay(false, "Auth Error", "Failed to update email.");
                sendProfileUpdateNotification(false, "Failed to update email.");
            });
        } else if (!newEmail.equals(user.getEmail())) {
            user.updateEmail(newEmail).addOnSuccessListener(v1 -> {
                sendProfileUpdateNotification(true, null);
                forceLogout();
            }).addOnFailureListener(e -> {
                loadingOverlay.setVisibility(View.GONE);
                showStatusOverlay(false, "Auth Error", "Failed to update email.");
                sendProfileUpdateNotification(false, "Failed to update email.");
            });
        } else if (!newPassword.isEmpty()) {
            user.updatePassword(newPassword).addOnSuccessListener(v2 -> {
                sendProfileUpdateNotification(true, null);
                forceLogout();
            }).addOnFailureListener(e -> {
                loadingOverlay.setVisibility(View.GONE);
                showStatusOverlay(false, "Auth Error", "Failed to update password.");
                sendProfileUpdateNotification(false, "Failed to update password.");
            });
        }
    }

    private void forceLogout() {
        loadingOverlay.setVisibility(View.GONE);
        mAuth.signOut();
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            android.content.SharedPreferences prefs = EncryptedSharedPreferences.create(
                    this, "secret_shared_prefs", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            prefs.edit().clear().apply();
        } catch (Exception ignored) {}

        Intent intent = new Intent(this, AuthActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        Toast.makeText(this, "Security settings changed. Please log in again.", Toast.LENGTH_LONG).show();
    }

    private void saveUpdatesToLocalPrefs(String name, String user, String email, String imageUrl) {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            android.content.SharedPreferences prefs = EncryptedSharedPreferences.create(
                    this,
                    "secret_shared_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            android.content.SharedPreferences.Editor editor = prefs.edit();
            if (isEmployee || isAdmin) {
                editor.putString("fullname", name);
                editor.putString("name", user); // shortname
            } else {
                editor.putString("name", name);
                editor.putString("username", user);
            }
            editor.putString("email", email);
            if (imageUrl != null) {
                editor.putString("profile_pic_url", imageUrl);
            }
            editor.apply();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    private void showStatusOverlay(boolean isSuccess, String title, String message) {
        tvStatusTitle.setText(title);
        tvStatusMessage.setText(message);
        ivStatusIcon.setImageResource(isSuccess ? R.drawable.ic_check_circle : R.drawable.ic_cancel);
        ivStatusIcon.setColorFilter(ContextCompat.getColor(this, isSuccess ? R.color.primary_color : R.color.warning_red_icon));
        
        layoutStatusOverlay.setVisibility(View.VISIBLE);
        layoutStatusOverlay.setAlpha(0f);
        layoutStatusOverlay.animate().alpha(1f).setDuration(200).start();
        
        mcvStatusDialog.setScaleX(0f);
        mcvStatusDialog.setScaleY(0f);
        mcvStatusDialog.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
    }

    private void hideStatusOverlay() {
        mcvStatusDialog.animate().scaleX(0f).scaleY(0f).setDuration(200).start();
        layoutStatusOverlay.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutStatusOverlay.setVisibility(View.GONE)).start();
    }

    private void showDeleteConfirmation() {
        if (layoutDeleteConfirmation == null || mcvDeleteDialog == null) return;
        cbDeleteConfirm.setChecked(false);
        if (btnDeleteConfirmAction != null) btnDeleteConfirmAction.setAlpha(0.5f);
        layoutDeleteConfirmation.setVisibility(View.VISIBLE);
        layoutDeleteConfirmation.setAlpha(0f);
        layoutDeleteConfirmation.animate().alpha(1f).setDuration(200).start();
        mcvDeleteDialog.setScaleX(0f);
        mcvDeleteDialog.setScaleY(0f);
        mcvDeleteDialog.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
    }

    private void hideDeleteConfirmation() {
        if (layoutDeleteConfirmation == null || mcvDeleteDialog == null) return;
        mcvDeleteDialog.animate().scaleX(0f).scaleY(0f).setDuration(200).start();
        layoutDeleteConfirmation.animate().alpha(0f).setDuration(200).withEndAction(() -> layoutDeleteConfirmation.setVisibility(View.GONE)).start();
    }

    private void showReauthDelete() {
        if (layoutReauthDelete == null || mcvReauthDeleteDialog == null) return;
        etReauthDeletePassword.setText("");
        layoutReauthDelete.setVisibility(View.VISIBLE);
        layoutReauthDelete.setAlpha(0f);
        layoutReauthDelete.animate().alpha(1f).setDuration(200).start();
        mcvReauthDeleteDialog.setScaleX(0f);
        mcvReauthDeleteDialog.setScaleY(0f);
        mcvReauthDeleteDialog.animate().scaleX(1f).scaleY(1f).setDuration(300).withEndAction(() -> {
            etReauthDeletePassword.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etReauthDeletePassword, InputMethodManager.SHOW_IMPLICIT);
        }).start();
    }

    private void hideReauthDelete() {
        if (layoutReauthDelete == null || mcvReauthDeleteDialog == null) return;
        hideKeyboard();
        mcvReauthDeleteDialog.animate().scaleX(0f).scaleY(0f).setDuration(200).start();
        layoutReauthDelete.animate().alpha(0f).setDuration(200).withEndAction(() -> layoutReauthDelete.setVisibility(View.GONE)).start();
    }

    private void proceedWithAccountDeletion() {
        String currentPassword = etReauthDeletePassword.getText().toString();
        if (currentPassword.isEmpty()) {
            etReauthDeletePassword.setError("Password required to confirm deletion");
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        hideReauthDelete();
        hideKeyboard();
        loadingOverlay.setVisibility(View.VISIBLE);

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential).addOnSuccessListener(aVoid -> {
            performAccountDeletion();
        }).addOnFailureListener(e -> {
            loadingOverlay.setVisibility(View.GONE);
            showStatusOverlay(false, "Verification Failed", "Incorrect current password. Deletion aborted.");
        });
    }

    private void performAccountDeletion() {
        loadingOverlay.setVisibility(View.VISIBLE);

        // 1. Update Firestore 'users' collection
        Map<String, Object> userUpdate = new HashMap<>();
        userUpdate.put("email", "deleted");
        userUpdate.put("role", "deleted");

        db.collection("users").document(userId).update(userUpdate)
                .addOnSuccessListener(aVoid1 -> {
                    // 1b. Cancel all pending bookings for this user
                    cancelUserBookings();
                })
                .addOnFailureListener(e -> {
                    loadingOverlay.setVisibility(View.GONE);
                    showStatusOverlay(false, "Deletion Error", "Failed to update user record: " + e.getMessage());
                });
    }

    private void cancelUserBookings() {
        db.collection("bookings")
                .whereEqualTo("customerId", userId)
                .whereEqualTo("status", "Pending")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        deleteProfilePicAndAuth();
                        return;
                    }

                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        batch.update(doc.getReference(), "status", "Cancelled");
                    }

                    batch.commit().addOnCompleteListener(task -> deleteProfilePicAndAuth());
                })
                .addOnFailureListener(e -> {
                    // Proceed anyway to ensure account deletion isn't blocked by secondary cleanup failure
                    deleteProfilePicAndAuth();
                });
    }

    private void deleteProfilePicAndAuth() {
        // 2. Delete profile pic document
        db.collection("profile_pics").document(userId).delete()
                .addOnCompleteListener(task -> {
                    // 3. Delete Firebase Auth User
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        user.delete().addOnSuccessListener(aVoid2 -> {
                            forceLogoutForDeletion();
                        }).addOnFailureListener(e -> {
                            loadingOverlay.setVisibility(View.GONE);
                            showStatusOverlay(false, "Deletion Error", "Account updated in DB but Auth deletion failed: " + e.getMessage());
                        });
                    } else {
                        forceLogoutForDeletion();
                    }
                });
    }

    private void forceLogoutForDeletion() {
        loadingOverlay.setVisibility(View.GONE);
        mAuth.signOut();
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            android.content.SharedPreferences prefs = EncryptedSharedPreferences.create(
                    this, "secret_shared_prefs", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            prefs.edit().clear().apply();
        } catch (Exception ignored) {}

        Intent intent = new Intent(this, AuthActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        Toast.makeText(this, "Your account has been permanently deleted.", Toast.LENGTH_LONG).show();
    }

    private void setFieldError(EditText et, String error, boolean requestFocus) {
        et.setError(error);
        if (requestFocus) et.requestFocus();
        if (activeErrorCleanups.containsKey(et)) errorCleanupHandler.removeCallbacks(activeErrorCleanups.get(et));
        Runnable r = () -> et.setError(null);
        activeErrorCleanups.put(et, r);
        errorCleanupHandler.postDelayed(r, 5000);
    }

    private void clearAllErrors() {
        EditText[] fields = {etFullname, etUsername, etSpecialty, etPhone, etEmail, etPassword, etConfirmPassword};
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

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void sendProfileUpdateNotification(boolean isSuccess, String errorMsg) {
        if (userId == null) return;

        Map<String, Object> notification = new HashMap<>();
        notification.put("receiverId", userId);
        notification.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
        notification.put("isRead", false);
        notification.put("isSeen", false);
        notification.put("senderId", "system");

        if (isSuccess) {
            notification.put("type", "PROFILE_UPDATE");
            notification.put("title", "Profile Updated");

            StringBuilder sb = new StringBuilder("You have successfully updated your ");
            java.util.List<String> changes = new java.util.ArrayList<>();

            if (selectedImageUri != null) changes.add("Profile Picture");
            if (!etFullname.getText().toString().trim().equals(initialFullname)) changes.add("Fullname");
            if (!etUsername.getText().toString().trim().equals(initialUsername)) changes.add("Username");
            
            String currentSpecialty = etSpecialty.getText().toString().trim();
            String oldSpecialty = initialSpecialty != null ? initialSpecialty : "";
            if (isEmployee && !currentSpecialty.equals(oldSpecialty)) changes.add("Specialty");

            if (!etPhone.getText().toString().trim().equals(initialPhone)) changes.add("Phone Number");
            if (!etEmail.getText().toString().trim().equals(initialEmail)) changes.add("Email");
            if (!etPassword.getText().toString().isEmpty()) changes.add("Password");

            if (changes.isEmpty()) {
                sb.append("profile.");
            } else {
                for (int i = 0; i < changes.size(); i++) {
                    sb.append(changes.get(i));
                    if (i < changes.size() - 2) sb.append(", ");
                    else if (i == changes.size() - 2) sb.append(" and ");
                }
                sb.append(".");
            }
            notification.put("message", sb.toString());
        } else {
            notification.put("type", "PROFILE_ERROR");
            notification.put("title", "Update Failed");
            notification.put("message", "We couldn't update your profile: " + errorMsg);
        }

        db.collection("notifications").add(notification);
    }
}
