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
import com.zejyv.azizul.uitm.fadebarber.adapters.CustomerManagementAdapter;
import com.zejyv.azizul.uitm.fadebarber.models.Customer;

import java.util.ArrayList;
import java.util.List;

public class CustomerManagementActivity extends AppCompatActivity {

    private RecyclerView rvCustomers;
    private CustomerManagementAdapter adapter;
    private final List<Customer> customerList = new ArrayList<>();
    private final List<Customer> filteredList = new ArrayList<>();
    private FirebaseFirestore db;
    private View loadingOverlay, llEmptyState;
    private TextView tvCustomerCount;
    private EditText etSearch;
    private ListenerRegistration customersListener;
    private ListenerRegistration innerCustomersListener;

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
        setContentView(R.layout.activity_customer_management);

        db = FirebaseFirestore.getInstance();
        initializeViews();
        setupRecyclerView();
        setupImagePreview();
        setupBackPressed();
        fetchCustomers();
    }

    private void initializeViews() {
        rvCustomers = findViewById(R.id.rv_customers);
        loadingOverlay = findViewById(R.id.loading_overlay);
        llEmptyState = findViewById(R.id.ll_empty_state);
        tvCustomerCount = findViewById(R.id.tv_customer_count);
        etSearch = findViewById(R.id.et_search_customer);

        findViewById(R.id.iv_back_mgmt).setOnClickListener(v -> finish());

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
        adapter = new CustomerManagementAdapter(filteredList, new CustomerManagementAdapter.OnCustomerActionListener() {
            @Override
            public void onEdit(Customer customer) {
                showEditCustomerDialog(customer);
            }

            @Override
            public void onDelete(Customer customer) {
                confirmDeleteCustomer(customer);
            }

            @Override
            public void onPicClick(String imageUrl) {
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    showImagePreview(imageUrl);
                }
            }
        });
        rvCustomers.setLayoutManager(new LinearLayoutManager(this));
        rvCustomers.setAdapter(adapter);
    }

    private void fetchCustomers() {
        loadingOverlay.setVisibility(View.VISIBLE);
        
        if (customersListener != null) customersListener.remove();

        // Fetch users who are customers first
        customersListener = db.collection("users")
                .whereEqualTo("role", "customer")
                .addSnapshotListener((userSnapshot, userError) -> {
                    if (userError != null) {
                        loadingOverlay.setVisibility(View.GONE);
                        Toast.makeText(this, "Error fetching users: " + userError.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (userSnapshot != null) {
                        List<String> activeCustomerIds = new ArrayList<>();
                        for (QueryDocumentSnapshot userDoc : userSnapshot) {
                            activeCustomerIds.add(userDoc.getId());
                        }

                        if (activeCustomerIds.isEmpty()) {
                            loadingOverlay.setVisibility(View.GONE);
                            customerList.clear();
                            filter("");
                            return;
                        }

                        // Now fetch the actual customer details for these IDs
                        if (innerCustomersListener != null) innerCustomersListener.remove();
                        innerCustomersListener = db.collection("customers")
                                .orderBy("name", Query.Direction.ASCENDING)
                                .addSnapshotListener((value, error) -> {
                                    if (error != null) {
                                        loadingOverlay.setVisibility(View.GONE);
                                        Toast.makeText(this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    if (value != null) {
                                        List<Task<Void>> tasks = new ArrayList<>();
                                        List<Customer> tempList = new ArrayList<>();

                                        for (QueryDocumentSnapshot doc : value) {
                                            if (activeCustomerIds.contains(doc.getId())) {
                                                Customer customer = doc.toObject(Customer.class);
                                                customer.setUid(doc.getId());
                                                tempList.add(customer);
                                                tasks.add(fetchExtraData(customer));
                                            }
                                        }

                                        Tasks.whenAllComplete(tasks).addOnCompleteListener(t -> {
                                            loadingOverlay.setVisibility(View.GONE);
                                            customerList.clear();
                                            customerList.addAll(tempList);
                                            filter(etSearch.getText().toString());
                                        });
                                    }
                                });
                    }
                });
    }

    private Task<Void> fetchExtraData(Customer customer) {
        String uid = customer.getUid();
        Task<DocumentSnapshot> picTask = db.collection("profile_pics").document(uid).get();

        return picTask.continueWith(task -> {
            if (picTask.isSuccessful() && picTask.getResult().exists()) {
                String url = picTask.getResult().getString("url");
                if (url != null) customer.setProfilePicUrl(url);
            }
            return null;
        });
    }

    private void filter(String text) {
        filteredList.clear();
        String query = text.toLowerCase().trim();
        if (query.isEmpty()) {
            filteredList.addAll(customerList);
        } else {
            for (Customer c : customerList) {
                if (c.getName().toLowerCase().contains(query) ||
                    (c.getUsername() != null && c.getUsername().toLowerCase().contains(query)) ||
                    (c.getUid() != null && c.getUid().toLowerCase().contains(query)) ||
                    (c.getPhone() != null && c.getPhone().replace(" ", "").replace("-", "").contains(query.replace(" ", "").replace("-", "")))) {
                    filteredList.add(c);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateUIState();
    }

    private void updateUIState() {
        tvCustomerCount.setText("Showing " + filteredList.size() + " customers");
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

    // --- Add Customer Logic ---

    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutImagePreview != null && layoutImagePreview.getVisibility() == View.VISIBLE) hideImagePreview();
                else finish();
            }
        });
    }

    // --- CRUD Operations ---

    private void showEditCustomerDialog(Customer customer) {
        android.content.Intent intent = new android.content.Intent(this, ProfileEditActivity.class);
        intent.putExtra("ADMIN_TARGET_USER_ID", customer.getUid());
        startActivity(intent);
    }

    private void confirmDeleteCustomer(Customer customer) {
        new MaterialAlertDialogBuilder(this).setTitle("Delete Customer").setMessage("Delete " + customer.getName() + "?")
                .setPositiveButton("Delete", (d, w) -> deleteCustomer(customer)).setNegativeButton("Cancel", null).show();
    }

    private void deleteCustomer(Customer customer) {
        loadingOverlay.setVisibility(View.VISIBLE);
        db.collection("customers").document(customer.getUid()).delete()
                .addOnSuccessListener(aVoid -> { loadingOverlay.setVisibility(View.GONE); Toast.makeText(this, "Customer deleted", Toast.LENGTH_SHORT).show(); })
                .addOnFailureListener(e -> { loadingOverlay.setVisibility(View.GONE); Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show(); });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (customersListener != null) customersListener.remove();
        if (innerCustomersListener != null) innerCustomersListener.remove();
    }
}
