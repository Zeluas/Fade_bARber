package com.zejyv.azizul.uitm.fadebarber;

import android.graphics.Matrix;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.zejyv.azizul.uitm.fadebarber.adapters.AdminManagementAdapter;
import com.zejyv.azizul.uitm.fadebarber.models.Admin;

import java.util.ArrayList;
import java.util.List;

public class AdminManagementActivity extends AppCompatActivity {

    private RecyclerView rvAdmins;
    private AdminManagementAdapter adapter;
    private final List<Admin> adminList = new ArrayList<>();
    private final List<Admin> filteredList = new ArrayList<>();
    private FirebaseFirestore db;
    private View loadingOverlay, llEmptyState;
    private TextView tvAdminCount;
    private EditText etSearch;
    private ListenerRegistration adminsListener;
    private ListenerRegistration innerAdminsListener;

    // --- Add Admin Overlay ---
    private View layoutAddAdmin, mcvAddDialog;
    private EditText etNewAdminEmailPrefix;
    private com.google.android.material.button.MaterialButton btnAddCancel, btnAddConfirm;

    // --- Profile Image Preview Components ---
    private View layoutImagePreview, vPreviewTopBar;
    private ImageView ivFullPreview;
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private final Matrix matrix = new Matrix();
    private float scaleFactor = 1.0f;
    private float minScale = 1.0f;
    private final float[] lastTouch = new float[2];
    private boolean isPanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_management);

        db = FirebaseFirestore.getInstance();
        initializeViews();
        setupRecyclerView();
        setupImagePreview();
        setupAddAdminOverlay();
        setupBackPressed();
        fetchAdmins();
    }

    private void initializeViews() {
        rvAdmins = findViewById(R.id.rv_admins);
        loadingOverlay = findViewById(R.id.loading_overlay);
        llEmptyState = findViewById(R.id.ll_empty_state);
        tvAdminCount = findViewById(R.id.tv_admin_count);
        etSearch = findViewById(R.id.et_search_admin);

        findViewById(R.id.iv_back_mgmt).setOnClickListener(v -> finish());
        
        TextView tvAdd = findViewById(R.id.tv_add_admin_link);
        if (tvAdd != null) {
            tvAdd.setPaintFlags(tvAdd.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            tvAdd.setOnClickListener(v -> showAddAdminOverlay());
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupRecyclerView() {
        adapter = new AdminManagementAdapter(filteredList, new AdminManagementAdapter.OnAdminActionListener() {
            @Override
            public void onEdit(Admin admin) {
                showEditAdminDialog(admin);
            }

            @Override
            public void onDelete(Admin admin) {
                confirmDeleteAdmin(admin);
            }

            @Override
            public void onPicClick(String imageUrl) {
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    showImagePreview(imageUrl);
                }
            }
        });
        rvAdmins.setLayoutManager(new LinearLayoutManager(this));
        rvAdmins.setAdapter(adapter);
    }

    private void fetchAdmins() {
        loadingOverlay.setVisibility(View.VISIBLE);
        
        if (adminsListener != null) adminsListener.remove();

        // Fetch users who are active admins first
        adminsListener = db.collection("users")
                .whereEqualTo("role", "admin")
                .addSnapshotListener((userSnapshot, userError) -> {
                    if (userError != null) {
                        loadingOverlay.setVisibility(View.GONE);
                        Toast.makeText(this, "Error fetching users: " + userError.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (userSnapshot != null) {
                        List<String> activeAdminIds = new ArrayList<>();
                        for (QueryDocumentSnapshot userDoc : userSnapshot) {
                            activeAdminIds.add(userDoc.getId());
                        }

                        if (activeAdminIds.isEmpty()) {
                            loadingOverlay.setVisibility(View.GONE);
                            adminList.clear();
                            filter("");
                            return;
                        }

                        // Now fetch the actual admin details for these IDs
                        if (innerAdminsListener != null) innerAdminsListener.remove();
                        innerAdminsListener = db.collection("admins")
                                .orderBy("fullname", Query.Direction.ASCENDING)
                                .addSnapshotListener((value, error) -> {
                                    if (error != null) {
                                        loadingOverlay.setVisibility(View.GONE);
                                        Toast.makeText(this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    if (value != null) {
                                        List<Task<Void>> tasks = new ArrayList<>();
                                        List<Admin> tempList = new ArrayList<>();

                                        for (QueryDocumentSnapshot doc : value) {
                                            if (activeAdminIds.contains(doc.getId())) {
                                                Admin admin = doc.toObject(Admin.class);
                                                admin.setUid(doc.getId());
                                                tempList.add(admin);
                                                tasks.add(fetchExtraData(admin));
                                            }
                                        }

                                        Tasks.whenAllComplete(tasks).addOnCompleteListener(t -> {
                                            loadingOverlay.setVisibility(View.GONE);
                                            adminList.clear();
                                            adminList.addAll(tempList);
                                            filter(etSearch.getText().toString());
                                        });
                                    }
                                });
                    }
                });
    }

    private Task<Void> fetchExtraData(Admin admin) {
        String uid = admin.getUid();
        
        // Fetch profile pic if it might be in separate collection
        Task<DocumentSnapshot> picTask = db.collection("profile_pics").document(uid).get();

        return picTask.continueWith(task -> {
            if (picTask.isSuccessful() && picTask.getResult().exists()) {
                String url = picTask.getResult().getString("url");
                if (url != null) admin.setProfilePicUrl(url);
            }
            return null;
        });
    }

    private void filter(String text) {
        filteredList.clear();
        if (text.isEmpty()) {
            filteredList.addAll(adminList);
        } else {
            for (Admin a : adminList) {
                if (a.getFullname().toLowerCase().contains(text.toLowerCase()) ||
                    (a.getShortname() != null && a.getShortname().toLowerCase().contains(text.toLowerCase()))) {
                    filteredList.add(a);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateUIState();
    }

    private void updateUIState() {
        tvAdminCount.setText("Showing " + filteredList.size() + " admins");
        llEmptyState.setVisibility(filteredList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // --- Image Preview Logic ---

    private void setupImagePreview() {
        layoutImagePreview = findViewById(R.id.layout_image_preview);
        ivFullPreview = findViewById(R.id.iv_full_preview);
        vPreviewTopBar = findViewById(R.id.v_preview_top_bar);
        View btnClose = findViewById(R.id.btn_close_preview);

        if (btnClose != null) btnClose.setOnClickListener(v -> hideImagePreview());

        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float nextScale = scaleFactor * factor;
                if (nextScale > 0.5f && nextScale < 6.0f) {
                    scaleFactor = nextScale;
                    matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                    ivFullPreview.setImageMatrix(matrix);
                }
                return true;
            }
        });

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(android.view.MotionEvent e) {
                if (scaleFactor > minScale * 1.1f) animateMatrixReset();
                else animateMatrixZoom(2.25f, e.getX(), e.getY());
                return true;
            }
            @Override
            public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                if (!isPanning) togglePreviewTopBar();
                return true;
            }
        });

        ivFullPreview.setOnTouchListener((v, event) -> {
            scaleGestureDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);
            int action = event.getAction() & android.view.MotionEvent.ACTION_MASK;
            switch (action) {
                case android.view.MotionEvent.ACTION_DOWN:
                    lastTouch[0] = event.getX(); lastTouch[1] = event.getY();
                    isPanning = false; break;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (!scaleGestureDetector.isInProgress() && event.getPointerCount() == 1) {
                        float dx = event.getX() - lastTouch[0];
                        float dy = event.getY() - lastTouch[1];
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                            isPanning = true;
                            matrix.postTranslate(dx, dy);
                            ivFullPreview.setImageMatrix(matrix);
                            lastTouch[0] = event.getX(); lastTouch[1] = event.getY();
                        }
                    }
                    break;
                case android.view.MotionEvent.ACTION_POINTER_UP:
                    int remainingPointerIndex = (event.getActionIndex() == 0) ? 1 : 0;
                    lastTouch[0] = event.getX(remainingPointerIndex);
                    lastTouch[1] = event.getY(remainingPointerIndex);
                    break;
            }
            return true;
        });
    }

    private void showImagePreview(String imageUrl) {
        if (layoutImagePreview == null || ivFullPreview == null) return;
        Glide.with(this).asBitmap().load(imageUrl).placeholder(R.drawable.ic_profile)
                .into(new CustomTarget<android.graphics.Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull android.graphics.Bitmap resource, @Nullable Transition<? super android.graphics.Bitmap> transition) {
                        ivFullPreview.setImageBitmap(resource);
                        resetImageMatrix();
                    }
                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                        ivFullPreview.setImageDrawable(placeholder);
                    }
                });
        layoutImagePreview.setVisibility(View.VISIBLE);
        layoutImagePreview.setAlpha(0f);
        layoutImagePreview.setScaleX(0.7f); layoutImagePreview.setScaleY(0.7f);
        layoutImagePreview.animate().alpha(1f).scaleX(1.0f).scaleY(1.0f).setDuration(300).start();
    }

    private void hideImagePreview() {
        if (layoutImagePreview == null) return;
        layoutImagePreview.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(200)
                .withEndAction(() -> layoutImagePreview.setVisibility(View.GONE)).start();
    }

    private void resetImageMatrix() {
        if (ivFullPreview.getDrawable() == null) return;
        float viewWidth = ivFullPreview.getWidth(), viewHeight = ivFullPreview.getHeight();
        float imgWidth = ivFullPreview.getDrawable().getIntrinsicWidth(), imgHeight = ivFullPreview.getDrawable().getIntrinsicHeight();
        scaleFactor = Math.min(viewWidth / imgWidth, viewHeight / imgHeight);
        minScale = scaleFactor;
        matrix.reset(); matrix.postScale(scaleFactor, scaleFactor);
        matrix.postTranslate((viewWidth - imgWidth * scaleFactor) / 2, (viewHeight - imgHeight * scaleFactor) / 2);
        ivFullPreview.setImageMatrix(matrix);
    }

    private void animateMatrixZoom(float targetFactor, float focusX, float focusY) {
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(1.0f, targetFactor);
        animator.setDuration(300);
        final float[] lastVal = {1.0f};
        animator.addUpdateListener(animation -> {
            float currentVal = (float) animation.getAnimatedValue();
            float deltaScale = currentVal / lastVal[0];
            lastVal[0] = currentVal;
            scaleFactor *= deltaScale;
            matrix.postScale(deltaScale, deltaScale, focusX, focusY);
            ivFullPreview.setImageMatrix(matrix);
        });
        animator.start();
    }

    private void animateMatrixReset() {
        if (ivFullPreview.getDrawable() == null) return;
        float viewWidth = ivFullPreview.getWidth(), viewHeight = ivFullPreview.getHeight();
        float imgWidth = ivFullPreview.getDrawable().getIntrinsicWidth(), imgHeight = ivFullPreview.getDrawable().getIntrinsicHeight();
        float targetScale = Math.min(viewWidth / imgWidth, viewHeight / imgHeight);
        float targetDx = (viewWidth - imgWidth * targetScale) / 2, targetDy = (viewHeight - imgHeight * targetScale) / 2;
        float[] startValues = new float[9]; matrix.getValues(startValues);
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(300);
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            float currScale = startValues[Matrix.MSCALE_X] + (targetScale - startValues[Matrix.MSCALE_X]) * fraction;
            float currDx = startValues[Matrix.MTRANS_X] + (targetDx - startValues[Matrix.MTRANS_X]) * fraction;
            float currDy = startValues[Matrix.MTRANS_Y] + (targetDy - startValues[Matrix.MTRANS_Y]) * fraction;
            matrix.reset(); matrix.postScale(currScale, currScale); matrix.postTranslate(currDx, currDy);
            scaleFactor = currScale; ivFullPreview.setImageMatrix(matrix);
        });
        animator.start();
    }

    private void togglePreviewTopBar() {
        if (vPreviewTopBar == null) return;
        boolean isVisible = vPreviewTopBar.getVisibility() == View.VISIBLE;
        float targetAlpha = isVisible ? 0f : 1f;
        int targetVisibility = isVisible ? View.GONE : View.VISIBLE;
        vPreviewTopBar.animate().alpha(targetAlpha).setDuration(300).withEndAction(() -> vPreviewTopBar.setVisibility(targetVisibility)).start();
        findViewById(R.id.btn_close_preview).animate().alpha(targetAlpha).setDuration(300).withEndAction(() -> findViewById(R.id.btn_close_preview).setVisibility(targetVisibility)).start();
        findViewById(R.id.tv_preview_title).animate().alpha(targetAlpha).setDuration(300).withEndAction(() -> findViewById(R.id.tv_preview_title).setVisibility(targetVisibility)).start();
    }

    // --- Add Admin Logic ---

    private void setupAddAdminOverlay() {
        layoutAddAdmin = findViewById(R.id.layout_add_admin);
        mcvAddDialog = findViewById(R.id.mcv_add_dialog);
        etNewAdminEmailPrefix = findViewById(R.id.et_new_admin_email_prefix);
        btnAddCancel = findViewById(R.id.btn_add_cancel);
        btnAddConfirm = findViewById(R.id.btn_add_confirm);

        if (btnAddCancel != null) btnAddCancel.setOnClickListener(v -> hideAddAdminOverlay());
        if (btnAddConfirm != null) btnAddConfirm.setOnClickListener(v -> createAdminAccount());
        
        if (layoutAddAdmin != null) layoutAddAdmin.setOnClickListener(v -> hideAddAdminOverlay());
        if (mcvAddDialog != null) mcvAddDialog.setOnClickListener(v -> {});
    }

    private void showAddAdminOverlay() {
        if (layoutAddAdmin == null) return;
        etNewAdminEmailPrefix.setText("");
        layoutAddAdmin.setVisibility(View.VISIBLE);
        layoutAddAdmin.setAlpha(0f);
        layoutAddAdmin.animate().alpha(1f).setDuration(200).start();
        mcvAddDialog.setScaleX(0.7f); mcvAddDialog.setScaleY(0.7f);
        mcvAddDialog.animate().scaleX(1f).scaleY(1f).setDuration(300).withEndAction(() -> {
            etNewAdminEmailPrefix.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etNewAdminEmailPrefix, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }).start();
    }

    private void hideAddAdminOverlay() {
        if (layoutAddAdmin == null) return;
        hideKeyboard();
        layoutAddAdmin.animate().alpha(0f).setDuration(200).withEndAction(() -> layoutAddAdmin.setVisibility(View.GONE)).start();
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void createAdminAccount() {
        String prefix = etNewAdminEmailPrefix.getText().toString().trim();
        if (prefix.isEmpty()) {
            etNewAdminEmailPrefix.setError("Email prefix required");
            return;
        }

        String email = prefix + "@fade.adm";
        String password = "12345678Aa";

        loadingOverlay.setVisibility(View.VISIBLE);
        hideAddAdminOverlay();

        FirebaseOptions options = FirebaseApp.getInstance().getOptions();
        FirebaseApp secondaryApp;
        try {
            secondaryApp = FirebaseApp.initializeApp(this, options, "SecondaryAppAdminMgmt");
        } catch (IllegalStateException e) {
            secondaryApp = FirebaseApp.getInstance("SecondaryAppAdminMgmt");
        }

        FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);
        secondaryAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    secondaryAuth.signOut();
                    loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(this, "Admin " + email + " registered successfully!", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(this, "Registration failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutImagePreview != null && layoutImagePreview.getVisibility() == View.VISIBLE) hideImagePreview();
                else if (layoutAddAdmin != null && layoutAddAdmin.getVisibility() == View.VISIBLE) hideAddAdminOverlay();
                else finish();
            }
        });
    }

    // --- CRUD Operations ---

    private void showEditAdminDialog(Admin admin) {
        android.content.Intent intent = new android.content.Intent(this, ProfileEditActivity.class);
        intent.putExtra("ADMIN_TARGET_USER_ID", admin.getUid());
        startActivity(intent);
    }

    private void confirmDeleteAdmin(Admin admin) {
        new MaterialAlertDialogBuilder(this).setTitle("Delete Admin").setMessage("Delete " + admin.getFullname() + "?")
                .setPositiveButton("Delete", (d, w) -> deleteAdmin(admin)).setNegativeButton("Cancel", null).show();
    }

    private void deleteAdmin(Admin admin) {
        loadingOverlay.setVisibility(View.VISIBLE);
        db.collection("admins").document(admin.getUid()).delete()
                .addOnSuccessListener(aVoid -> { loadingOverlay.setVisibility(View.GONE); Toast.makeText(this, "Admin deleted", Toast.LENGTH_SHORT).show(); })
                .addOnFailureListener(e -> { loadingOverlay.setVisibility(View.GONE); Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show(); });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adminsListener != null) adminsListener.remove();
        if (innerAdminsListener != null) innerAdminsListener.remove();
    }
}
