package com.zejyv.azizul.uitm.fadebarber;

import android.graphics.Matrix;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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
import com.zejyv.azizul.uitm.fadebarber.adapters.EmployeeManagementAdapter;
import com.zejyv.azizul.uitm.fadebarber.models.Employee;

import java.util.ArrayList;
import java.util.List;

public class EmployeeManagementActivity extends AppCompatActivity {

    private RecyclerView rvEmployees;
    private EmployeeManagementAdapter adapter;
    private final List<Employee> employeeList = new ArrayList<>();
    private final List<Employee> filteredList = new ArrayList<>();
    private FirebaseFirestore db;
    private View loadingOverlay, llEmptyState;
    private TextView tvEmployeeCount;
    private EditText etSearch;
    private ListenerRegistration employeesListener;
    private ListenerRegistration innerEmployeesListener;

    // --- Add Employee Overlay ---
    private View layoutAddEmployee, mcvAddDialog;
    private EditText etNewEmpEmailPrefix;
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
        setContentView(R.layout.activity_employee_management);

        db = FirebaseFirestore.getInstance();
        initializeViews();
        setupRecyclerView();
        setupImagePreview();
        setupAddEmployeeOverlay();
        setupBackPressed();
        fetchEmployees();
    }

    private void initializeViews() {
        rvEmployees = findViewById(R.id.rv_employees);
        loadingOverlay = findViewById(R.id.loading_overlay);
        llEmptyState = findViewById(R.id.ll_empty_state);
        tvEmployeeCount = findViewById(R.id.tv_employee_count);
        etSearch = findViewById(R.id.et_search_employee);

        findViewById(R.id.iv_back_mgmt).setOnClickListener(v -> finish());
        
        TextView tvAdd = findViewById(R.id.tv_add_employee_link);
        if (tvAdd != null) {
            tvAdd.setPaintFlags(tvAdd.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            tvAdd.setOnClickListener(v -> showAddEmployeeOverlay());
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
        adapter = new EmployeeManagementAdapter(filteredList, new EmployeeManagementAdapter.OnEmployeeActionListener() {
            @Override
            public void onEdit(Employee employee) {
                showEditEmployeeDialog(employee);
            }

            @Override
            public void onDelete(Employee employee) {
                confirmDeleteEmployee(employee);
            }

            @Override
            public void onPicClick(String imageUrl) {
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    showImagePreview(imageUrl);
                }
            }
        });
        rvEmployees.setLayoutManager(new LinearLayoutManager(this));
        rvEmployees.setAdapter(adapter);
    }

    private void fetchEmployees() {
        loadingOverlay.setVisibility(View.VISIBLE);
        
        if (employeesListener != null) employeesListener.remove();

        // Fetch users who are active employees first
        employeesListener = db.collection("users")
                .whereEqualTo("role", "employee")
                .addSnapshotListener((userSnapshot, userError) -> {
                    if (userError != null) {
                        loadingOverlay.setVisibility(View.GONE);
                        Toast.makeText(this, "Error fetching users: " + userError.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (userSnapshot != null) {
                        List<String> activeEmployeeIds = new ArrayList<>();
                        for (QueryDocumentSnapshot userDoc : userSnapshot) {
                            activeEmployeeIds.add(userDoc.getId());
                        }

                        if (activeEmployeeIds.isEmpty()) {
                            loadingOverlay.setVisibility(View.GONE);
                            employeeList.clear();
                            filter("");
                            return;
                        }

                        // Now fetch the actual employee details for these IDs
                        if (innerEmployeesListener != null) innerEmployeesListener.remove();
                        innerEmployeesListener = db.collection("employees")
                                .orderBy("fullname", Query.Direction.ASCENDING)
                                .addSnapshotListener((value, error) -> {
                                    if (error != null) {
                                        loadingOverlay.setVisibility(View.GONE);
                                        Toast.makeText(this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    if (value != null) {
                                        List<Task<Void>> tasks = new ArrayList<>();
                                        List<Employee> tempList = new ArrayList<>();

                                        for (QueryDocumentSnapshot doc : value) {
                                            if (activeEmployeeIds.contains(doc.getId())) {
                                                Employee employee = doc.toObject(Employee.class);
                                                employee.setUid(doc.getId());
                                                tempList.add(employee);
                                                tasks.add(fetchExtraData(employee));
                                            }
                                        }

                                        Tasks.whenAllComplete(tasks).addOnCompleteListener(t -> {
                                            loadingOverlay.setVisibility(View.GONE);
                                            employeeList.clear();
                                            employeeList.addAll(tempList);
                                            filter(etSearch.getText().toString());
                                        });
                                    }
                                });
                    }
                });
    }

    private Task<Void> fetchExtraData(Employee employee) {
        String uid = employee.getUid();
        
        // Fetch rating
        Task<com.google.firebase.firestore.QuerySnapshot> ratingTask = db.collection("hairstylist_ratings")
                .whereEqualTo("employeeId", uid).get();
        
        // Fetch profile pic if it might be in separate collection
        Task<DocumentSnapshot> picTask = db.collection("profile_pics").document(uid).get();

        return Tasks.whenAllComplete(ratingTask, picTask).continueWith(task -> {
            if (ratingTask.isSuccessful()) {
                List<DocumentSnapshot> ratings = ratingTask.getResult().getDocuments();
                if (!ratings.isEmpty()) {
                    double sum = 0;
                    for (DocumentSnapshot rDoc : ratings) {
                        Double r = rDoc.getDouble("rating");
                        if (r != null) sum += r;
                    }
                    employee.setOverallRating(sum / ratings.size());
                } else {
                    employee.setOverallRating(0.0);
                }
            }
            
            if (picTask.isSuccessful() && picTask.getResult().exists()) {
                String url = picTask.getResult().getString("url");
                if (url != null) employee.setProfilePicUrl(url);
            }
            
            return null;
        });
    }

    private void filter(String text) {
        filteredList.clear();
        if (text.isEmpty()) {
            filteredList.addAll(employeeList);
        } else {
            for (Employee e : employeeList) {
                if (e.getFullname().toLowerCase().contains(text.toLowerCase()) ||
                    (e.getShortname() != null && e.getShortname().toLowerCase().contains(text.toLowerCase()))) {
                    filteredList.add(e);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateUIState();
    }

    private void updateUIState() {
        tvEmployeeCount.setText("Showing " + filteredList.size() + " employees");
        llEmptyState.setVisibility(filteredList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // --- Image Preview Logic (Ported from ColleaguesActivity) ---

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

    // --- Add Employee Logic ---

    private void setupAddEmployeeOverlay() {
        layoutAddEmployee = findViewById(R.id.layout_add_employee);
        mcvAddDialog = findViewById(R.id.mcv_add_dialog);
        etNewEmpEmailPrefix = findViewById(R.id.et_new_emp_email_prefix);
        btnAddCancel = findViewById(R.id.btn_add_cancel);
        btnAddConfirm = findViewById(R.id.btn_add_confirm);

        if (btnAddCancel != null) btnAddCancel.setOnClickListener(v -> hideAddEmployeeOverlay());
        if (btnAddConfirm != null) btnAddConfirm.setOnClickListener(v -> createEmployeeAccount());
        
        if (layoutAddEmployee != null) layoutAddEmployee.setOnClickListener(v -> hideAddEmployeeOverlay());
        if (mcvAddDialog != null) mcvAddDialog.setOnClickListener(v -> {});
    }

    private void showAddEmployeeOverlay() {
        if (layoutAddEmployee == null) return;
        etNewEmpEmailPrefix.setText("");
        layoutAddEmployee.setVisibility(View.VISIBLE);
        layoutAddEmployee.setAlpha(0f);
        layoutAddEmployee.animate().alpha(1f).setDuration(200).start();
        mcvAddDialog.setScaleX(0.7f); mcvAddDialog.setScaleY(0.7f);
        mcvAddDialog.animate().scaleX(1f).scaleY(1f).setDuration(300).withEndAction(() -> {
            etNewEmpEmailPrefix.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etNewEmpEmailPrefix, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }).start();
    }

    private void hideAddEmployeeOverlay() {
        if (layoutAddEmployee == null) return;
        hideKeyboard();
        layoutAddEmployee.animate().alpha(0f).setDuration(200).withEndAction(() -> layoutAddEmployee.setVisibility(View.GONE)).start();
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void createEmployeeAccount() {
        String prefix = etNewEmpEmailPrefix.getText().toString().trim();
        if (prefix.isEmpty()) {
            etNewEmpEmailPrefix.setError("Email prefix required");
            return;
        }

        String email = prefix + "@fade.emp";
        String password = "12345678Aa";

        loadingOverlay.setVisibility(View.VISIBLE);
        hideAddEmployeeOverlay();

        // Use a secondary FirebaseApp to create the user without signing out the current admin
        FirebaseOptions options = FirebaseApp.getInstance().getOptions();
        FirebaseApp secondaryApp;
        try {
            secondaryApp = FirebaseApp.initializeApp(this, options, "SecondaryApp");
        } catch (IllegalStateException e) {
            secondaryApp = FirebaseApp.getInstance("SecondaryApp");
        }

        FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);
        secondaryAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    secondaryAuth.signOut();
                    loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(this, "Employee " + email + " registered successfully!", Toast.LENGTH_LONG).show();
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
                else if (layoutAddEmployee != null && layoutAddEmployee.getVisibility() == View.VISIBLE) hideAddEmployeeOverlay();
                else finish();
            }
        });
    }

    // --- CRUD Operations ---

    private void showEditEmployeeDialog(Employee employee) {
        android.content.Intent intent = new android.content.Intent(this, ProfileEditActivity.class);
        intent.putExtra("ADMIN_TARGET_USER_ID", employee.getUid());
        startActivity(intent);
    }

    private void confirmDeleteEmployee(Employee employee) {
        new MaterialAlertDialogBuilder(this).setTitle("Delete Employee").setMessage("Delete " + employee.getFullname() + "?")
                .setPositiveButton("Delete", (d, w) -> deleteEmployee(employee)).setNegativeButton("Cancel", null).show();
    }

    private void deleteEmployee(Employee employee) {
        loadingOverlay.setVisibility(View.VISIBLE);
        db.collection("employees").document(employee.getUid()).delete()
                .addOnSuccessListener(aVoid -> { loadingOverlay.setVisibility(View.GONE); Toast.makeText(this, "Employee deleted", Toast.LENGTH_SHORT).show(); })
                .addOnFailureListener(e -> { loadingOverlay.setVisibility(View.GONE); Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show(); });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (employeesListener != null) employeesListener.remove();
        if (innerEmployeesListener != null) innerEmployeesListener.remove();
    }
}
