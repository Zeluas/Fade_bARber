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

    private boolean isEmployee = false;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String userId;

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

        findViewById(R.id.iv_back_profile_edit).setOnClickListener(v -> finish());
        
        setupPasswordVisibilityToggle(etPassword);
        setupPasswordVisibilityToggle(etConfirmPassword);
        setupPasswordVisibilityToggle(etReauthPassword);

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

            String fullnameInPrefs = prefs.getString("fullname", "");
            isEmployee = !fullnameInPrefs.isEmpty();

            if (isEmployee) {
                mcvSpecialty.setVisibility(View.VISIBLE);
                loadEmployeeData();
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

    private void loadCustomerData() {
        if (userId == null) return;
        loadingOverlay.setVisibility(View.VISIBLE);
        db.collection("customers").document(userId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                etFullname.setText(doc.getString("name"));
                etUsername.setText(doc.getString("username"));
                etPhone.setText(doc.getString("phone"));
                etEmail.setText(mAuth.getCurrentUser().getEmail());
            }
            fetchLatestProfilePic();
        }).addOnFailureListener(e -> loadingOverlay.setVisibility(View.GONE));
    }

    private void loadEmployeeData() {
        if (userId == null) return;
        loadingOverlay.setVisibility(View.VISIBLE);
        db.collection("employees").document(userId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                etFullname.setText(doc.getString("fullname"));
                etUsername.setText(doc.getString("shortname"));
                etSpecialty.setText(doc.getString("specialty"));
                etPhone.setText(doc.getString("phone"));
                etEmail.setText(mAuth.getCurrentUser().getEmail());
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
        if (isEmployee && etSpecialty.getText().toString().trim().isEmpty()) {
            setFieldError(etSpecialty, "Specialty is required", requestFocus);
            return false;
        }
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
        if (phone.length() < 11) { 
            setFieldError(etPhone, "Phone number must be at least 11 digits", requestFocus); 
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
        String collection = isEmployee ? "employees" : "customers";

        if (isEmployee) {
            updates.put("fullname", name);
            updates.put("shortname", user);
            updates.put("specialty", etSpecialty.getText().toString().trim());
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
                }
            })
            .addOnFailureListener(e -> {
                loadingOverlay.setVisibility(View.GONE);
                showStatusOverlay(false, "Update Failed", e.getMessage());
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
                    forceLogout();
                }).addOnFailureListener(e -> {
                    loadingOverlay.setVisibility(View.GONE);
                    showStatusOverlay(false, "Auth Error", "Email updated, but password failed.");
                });
            }).addOnFailureListener(e -> {
                loadingOverlay.setVisibility(View.GONE);
                showStatusOverlay(false, "Auth Error", "Failed to update email.");
            });
        } else if (!newEmail.equals(user.getEmail())) {
            user.updateEmail(newEmail).addOnSuccessListener(v1 -> {
                forceLogout();
            }).addOnFailureListener(e -> {
                loadingOverlay.setVisibility(View.GONE);
                showStatusOverlay(false, "Auth Error", "Failed to update email.");
            });
        } else if (!newPassword.isEmpty()) {
            user.updatePassword(newPassword).addOnSuccessListener(v2 -> {
                forceLogout();
            }).addOnFailureListener(e -> {
                loadingOverlay.setVisibility(View.GONE);
                showStatusOverlay(false, "Auth Error", "Failed to update password.");
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
            if (isEmployee) {
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
}
