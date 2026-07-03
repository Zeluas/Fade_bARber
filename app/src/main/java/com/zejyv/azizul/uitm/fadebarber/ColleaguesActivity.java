package com.zejyv.azizul.uitm.fadebarber;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

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
                if (layoutCallCustomer != null && layoutCallCustomer.getVisibility() == View.VISIBLE) {
                    hideCallCustomerDialog();
                } else {
                    finish();
                }
            }
        });
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
