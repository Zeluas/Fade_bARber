package com.zejyv.azizul.uitm.fadebarber;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.zejyv.azizul.uitm.fadebarber.adapters.ColleagueAdapter;
import com.zejyv.azizul.uitm.fadebarber.models.Employee;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ColleaguesActivity extends AppCompatActivity {

    private ProgressBar pbColleagues;
    private android.widget.TextView tvTitle, tvSwitchLabel;
    private FirebaseFirestore db;
    private List<Employee> colleagueList;
    private ColleagueAdapter adapter;
    private boolean isShowingAdmins = false;
    private String loggedInRole = "employee"; // Default to employee for safety

    // --- Call Dialog Components ---
    private View layoutCallCustomer, mcvCallDialog;
    private TextView tvCustomerPhone;
    private String rawCustomerPhone = "";

    // --- Profile Image Preview Components ---
    private View layoutImagePreview, vPreviewTopBar;
    private ImageView ivFullPreview;
    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector gestureDetector;
    private final android.graphics.Matrix matrix = new android.graphics.Matrix();
    private float scaleFactor = 1.0f;
    private float minScale = 1.0f;
    private final float[] lastTouch = new float[2];
    private boolean isPanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_colleagues);

        // Entry Animation
        View root = findViewById(android.R.id.content);
        root.setAlpha(0f);
        root.setTranslationY(50f);
        root.animate().alpha(1f).translationY(0f).setDuration(400).start();

        loadUserRole();
        db = FirebaseFirestore.getInstance();
        colleagueList = new ArrayList<>();

        RecyclerView rvColleagues = findViewById(R.id.rv_colleagues);
        pbColleagues = findViewById(R.id.pb_colleagues);
        tvTitle = findViewById(R.id.tv_title_colleagues);
        tvSwitchLabel = findViewById(R.id.tv_switch_role_label);
        View llSwitchRole = findViewById(R.id.ll_switch_role_colleagues);

        updateUiForPerspective();
        findViewById(R.id.iv_back_colleagues).setOnClickListener(v -> finish());

        if (llSwitchRole != null) {
            llSwitchRole.setOnClickListener(v -> {
                isShowingAdmins = !isShowingAdmins;
                adapter.setShowingAdmins(isShowingAdmins);

                // Animate out
                RecyclerView rv = findViewById(R.id.rv_colleagues);
                rv.animate().alpha(0f).translationX(isShowingAdmins ? -100f : 100f).setDuration(200).withEndAction(() -> {
                    updateUiForPerspective();
                    fetchColleagues();

                    // Animate in
                    rv.setTranslationX(isShowingAdmins ? 100f : -100f);
                    rv.animate().alpha(1f).translationX(0f).setDuration(300).start();
                }).start();
            });
        }

        rvColleagues.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ColleagueAdapter(colleagueList);
        setupAdapterListeners();
        rvColleagues.setAdapter(adapter);

        setupCallCustomerDialog();
        setupImagePreview();
        setupBackPressed();
        fetchColleagues();
    }

    private void setupAdapterListeners() {
        adapter.setOnColleagueClickListener(new ColleagueAdapter.OnColleagueClickListener() {
            @Override
            public void onPhoneClick(String rawPhone) {
                showCallCustomerDialog(rawPhone);
            }

            @Override
            public void onUidClick(String uid) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Employee UID", uid);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(ColleaguesActivity.this, "UID copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onPicClick(String imageUrl) {
                showImagePreview(imageUrl);
            }
        });
    }

    private void setupCallCustomerDialog() {
        layoutCallCustomer = findViewById(R.id.layout_call_customer);
        mcvCallDialog = findViewById(R.id.mcv_call_dialog);
        tvCustomerPhone = findViewById(R.id.tv_customer_phone_display);
        View btnCallNow = findViewById(R.id.btn_call_now);

        if (btnCallNow != null) {
            btnCallNow.setOnClickListener(v -> {
                if (rawCustomerPhone != null && !rawCustomerPhone.isEmpty()) {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + rawCustomerPhone));
                    startActivity(intent);
                }
                hideCallCustomerDialog();
            });
        }

        if (layoutCallCustomer != null) {
            layoutCallCustomer.setOnClickListener(v -> hideCallCustomerDialog());
        }
    }

    public void showCallCustomerDialog(String rawPhone) {
        if (layoutCallCustomer == null || mcvCallDialog == null || tvCustomerPhone == null) return;
        this.rawCustomerPhone = rawPhone;

        TextView tvHeader = findViewById(R.id.tv_call_dialog_header);
        if (tvHeader != null) {
            String roleTerm = isShowingAdmins ? "Admin" : (loggedInRole.equals("employee") ? "Colleague" : "Employee");
            tvHeader.setText("Call " + roleTerm);
        }

        String formatted = rawPhone;
        if (rawPhone != null && !rawPhone.isEmpty()) {
            String digits = rawPhone;
            if (rawPhone.startsWith("0")) digits = rawPhone.substring(1);
            else if (rawPhone.startsWith("+60")) digits = rawPhone.substring(3);
            else if (rawPhone.startsWith("60")) digits = rawPhone.substring(2);

            if (digits.length() >= 6) {
                StringBuilder sb = new StringBuilder("+60 ");
                sb.append(digits.substring(0, 2)).append("-").append(digits.substring(2, 6));
                String remaining = digits.substring(6);
                for (int i = 0; i < remaining.length(); i++) {
                    if (i > 0 && i % 4 == 0) sb.append(" ");
                    if (i == 0) sb.append(" ");
                    sb.append(remaining.charAt(i));
                }
                formatted = sb.toString();
            } else {
                formatted = "+60 " + digits;
            }
        }

        tvCustomerPhone.setText(formatted);
        layoutCallCustomer.setVisibility(View.VISIBLE);
        layoutCallCustomer.setAlpha(0f);
        layoutCallCustomer.animate().alpha(1f).setDuration(200).start();

        mcvCallDialog.post(() -> {
            mcvCallDialog.setTranslationY(mcvCallDialog.getHeight());
            mcvCallDialog.animate().translationY(0).setDuration(300).start();
        });
    }

    public void hideCallCustomerDialog() {
        if (layoutCallCustomer == null || mcvCallDialog == null) return;
        mcvCallDialog.animate().translationY(mcvCallDialog.getHeight()).setDuration(200).start();
        layoutCallCustomer.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutCallCustomer.setVisibility(View.GONE)).start();
    }

    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutImagePreview != null && layoutImagePreview.getVisibility() == View.VISIBLE) {
                    hideImagePreview();
                } else if (layoutCallCustomer != null && layoutCallCustomer.getVisibility() == View.VISIBLE) {
                    hideCallCustomerDialog();
                } else {
                    finish();
                }
            }
        });
    }

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
                if (scaleFactor > minScale * 1.1f) {
                    animateMatrixReset();
                } else {
                    animateMatrixZoom(2.25f, e.getX(), e.getY());
                }
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
                    lastTouch[0] = event.getX();
                    lastTouch[1] = event.getY();
                    isPanning = false;
                    break;

                case android.view.MotionEvent.ACTION_POINTER_DOWN:
                    isPanning = false;
                    break;

                case android.view.MotionEvent.ACTION_MOVE:
                    if (!scaleGestureDetector.isInProgress() && event.getPointerCount() == 1) {
                        float dx = event.getX() - lastTouch[0];
                        float dy = event.getY() - lastTouch[1];

                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                            isPanning = true;
                            matrix.postTranslate(dx, dy);
                            ivFullPreview.setImageMatrix(matrix);
                            lastTouch[0] = event.getX();
                            lastTouch[1] = event.getY();
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

    private void animateMatrixZoom(float targetFactor, float focusX, float focusY) {
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(1.0f, targetFactor);
        animator.setDuration(300);
        animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

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

        float viewWidth = ivFullPreview.getWidth();
        float viewHeight = ivFullPreview.getHeight();
        float imgWidth = ivFullPreview.getDrawable().getIntrinsicWidth();
        float imgHeight = ivFullPreview.getDrawable().getIntrinsicHeight();

        float targetScale = Math.min(viewWidth / imgWidth, viewHeight / imgHeight);
        float targetDx = (viewWidth - imgWidth * targetScale) / 2;
        float targetDy = (viewHeight - imgHeight * targetScale) / 2;

        float[] startValues = new float[9];
        matrix.getValues(startValues);

        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(300);
        animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            float currScale = startValues[android.graphics.Matrix.MSCALE_X] + (targetScale - startValues[android.graphics.Matrix.MSCALE_X]) * fraction;
            float currDx = startValues[android.graphics.Matrix.MTRANS_X] + (targetDx - startValues[android.graphics.Matrix.MTRANS_X]) * fraction;
            float currDy = startValues[android.graphics.Matrix.MTRANS_Y] + (targetDy - startValues[android.graphics.Matrix.MTRANS_Y]) * fraction;

            matrix.reset();
            matrix.postScale(currScale, currScale);
            matrix.postTranslate(currDx, currDy);
            scaleFactor = currScale;
            ivFullPreview.setImageMatrix(matrix);
        });
        animator.start();
    }

    private void resetImageMatrix() {
        if (ivFullPreview.getDrawable() == null) return;

        float viewWidth = ivFullPreview.getWidth();
        float viewHeight = ivFullPreview.getHeight();
        float imgWidth = ivFullPreview.getDrawable().getIntrinsicWidth();
        float imgHeight = ivFullPreview.getDrawable().getIntrinsicHeight();

        float scaleX = viewWidth / imgWidth;
        float scaleY = viewHeight / imgHeight;
        scaleFactor = Math.min(scaleX, scaleY);
        minScale = scaleFactor;

        matrix.reset();
        matrix.postScale(scaleFactor, scaleFactor);

        float dx = (viewWidth - imgWidth * scaleFactor) / 2;
        float dy = (viewHeight - imgHeight * scaleFactor) / 2;
        matrix.postTranslate(dx, dy);

        ivFullPreview.setImageMatrix(matrix);
    }

    public void showImagePreview(String imageUrl) {
        if (layoutImagePreview == null || ivFullPreview == null || imageUrl == null || imageUrl.isEmpty()) return;

        Glide.with(this)
                .asBitmap()
                .load(imageUrl)
                .placeholder(R.drawable.ic_profile)
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
        layoutImagePreview.animate().alpha(1f).setDuration(200).start();

        layoutImagePreview.setScaleX(0.7f);
        layoutImagePreview.setScaleY(0.7f);
        layoutImagePreview.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start();

        vPreviewTopBar.setVisibility(View.VISIBLE);
        vPreviewTopBar.setAlpha(1f);
        View btnClose = findViewById(R.id.btn_close_preview);
        View tvTitle = findViewById(R.id.tv_preview_title);
        if (btnClose != null) { btnClose.setVisibility(View.VISIBLE); btnClose.setAlpha(1f); }
        if (tvTitle != null) { tvTitle.setVisibility(View.VISIBLE); tvTitle.setAlpha(1f); }
    }

    public void hideImagePreview() {
        if (layoutImagePreview == null) return;
        layoutImagePreview.animate().scaleX(0f).scaleY(0f).setDuration(200).start();
        layoutImagePreview.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutImagePreview.setVisibility(View.GONE)).start();
    }

    private void togglePreviewTopBar() {
        if (vPreviewTopBar == null) return;
        View btnClose = findViewById(R.id.btn_close_preview);
        View tvTitle = findViewById(R.id.tv_preview_title);

        boolean isVisible = vPreviewTopBar.getVisibility() == View.VISIBLE;
        if (isVisible) {
            vPreviewTopBar.animate().alpha(0f).setDuration(300).withEndAction(() -> vPreviewTopBar.setVisibility(View.GONE)).start();
            if (btnClose != null) btnClose.animate().alpha(0f).setDuration(300).withEndAction(() -> btnClose.setVisibility(View.GONE)).start();
            if (tvTitle != null) tvTitle.animate().alpha(0f).setDuration(300).withEndAction(() -> tvTitle.setVisibility(View.GONE)).start();
        } else {
            vPreviewTopBar.setVisibility(View.VISIBLE);
            vPreviewTopBar.setAlpha(0f);
            vPreviewTopBar.animate().alpha(1f).setDuration(300).start();
            if (btnClose != null) { btnClose.setVisibility(View.VISIBLE); btnClose.setAlpha(0f); btnClose.animate().alpha(1f).setDuration(300).start(); }
            if (tvTitle != null) { tvTitle.setVisibility(View.VISIBLE); tvTitle.setAlpha(0f); tvTitle.animate().alpha(1f).setDuration(300).start(); }
        }
    }

    private void loadUserRole() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            android.content.SharedPreferences prefs = EncryptedSharedPreferences.create(
                    this, "secret_shared_prefs", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            loggedInRole = prefs.getString("role", "employee");
        } catch (Exception e) {
            Log.e("ColleaguesActivity", "Error loading role", e);
        }
    }

    private void updateUiForPerspective() {
        if (isShowingAdmins) {
            tvTitle.setText("Admins");
            tvSwitchLabel.setText(loggedInRole.equals("employee") ? "Colleague" : "Employee");
        } else {
            tvTitle.setText(loggedInRole.equals("employee") ? "Colleagues" : "Employees");
            tvSwitchLabel.setText("Admin");
        }
    }

    private void fetchColleagues() {
        pbColleagues.setVisibility(View.VISIBLE);
        String targetRole = isShowingAdmins ? "admin" : "employee";
        
        db.collection("users")
                .whereEqualTo("role", targetRole)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        pbColleagues.setVisibility(View.GONE);
                        colleagueList.clear();
                        adapter.notifyDataSetChanged();
                        String emptyMsg = isShowingAdmins ? "No admins found" : "No colleagues found";
                        Toast.makeText(this, emptyMsg, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<Task<Void>> tasks = new ArrayList<>();
                    colleagueList.clear();

                    for (DocumentSnapshot userDoc : queryDocumentSnapshots.getDocuments()) {
                        String uid = userDoc.getId();
                        Timestamp createdAt = userDoc.getTimestamp("createdAt");
                        
                        Employee employee = new Employee();
                        employee.setUid(uid);
                        employee.setJoinedDate(createdAt);
                        
                        colleagueList.add(employee);
                        tasks.add(fetchExtraData(employee));
                    }

                    Tasks.whenAllComplete(tasks).addOnCompleteListener(t -> {
                        Collections.sort(colleagueList, (e1, e2) -> {
                            if (e1.getJoinedDate() == null || e2.getJoinedDate() == null) return 0;
                            return e1.getJoinedDate().compareTo(e2.getJoinedDate());
                        });

                        pbColleagues.setVisibility(View.GONE);
                        adapter.notifyDataSetChanged();
                    });

                })
                .addOnFailureListener(e -> {
                    pbColleagues.setVisibility(View.GONE);
                    Log.e("Colleagues", "Error fetching employees", e);
                    Toast.makeText(this, "Failed to load colleagues", Toast.LENGTH_SHORT).show();
                });
    }

    private Task<Void> fetchExtraData(Employee employee) {
        String uid = employee.getUid();
        String profileCollection = isShowingAdmins ? "admins" : "employees";
        
        Task<DocumentSnapshot> profileTask = db.collection(profileCollection).document(uid).get();
        Task<DocumentSnapshot> picTask = db.collection("profile_pics").document(uid).get();
        
        if (isShowingAdmins) {
            // Admins don't have specialties or ratings in this system
            return Tasks.whenAllComplete(profileTask, picTask).continueWith(task -> {
                if (profileTask.isSuccessful() && profileTask.getResult().exists()) {
                    DocumentSnapshot doc = profileTask.getResult();
                    employee.setFullname(doc.getString("fullname"));
                    employee.setShortname(doc.getString("shortname"));
                    employee.setPhone(doc.getString("phone"));
                    employee.setSpecialty("");
                }
                if (picTask.isSuccessful() && picTask.getResult().exists()) {
                    employee.setProfilePicUrl(picTask.getResult().getString("url"));
                }
                employee.setOverallRating(0.0);
                return null;
            });
        }

        Task<com.google.firebase.firestore.QuerySnapshot> ratingTask = db.collection("hairstylist_ratings")
                .whereEqualTo("employeeId", uid).get();

        return Tasks.whenAllComplete(profileTask, picTask, ratingTask).continueWith(task -> {
            if (profileTask.isSuccessful() && profileTask.getResult().exists()) {
                DocumentSnapshot doc = profileTask.getResult();
                employee.setFullname(doc.getString("fullname"));
                employee.setShortname(doc.getString("shortname"));
                employee.setPhone(doc.getString("phone"));
                employee.setSpecialty(doc.getString("specialty"));
            }
            
            if (picTask.isSuccessful() && picTask.getResult().exists()) {
                String url = picTask.getResult().getString("url");
                employee.setProfilePicUrl(url);
            }

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
            
            return null;
        });
    }
}
