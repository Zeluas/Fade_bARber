package com.zejyv.azizul.uitm.fadebarber;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private FirebaseFirestore db;
    private List<Employee> colleagueList;
    private ColleagueAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_colleagues);

        db = FirebaseFirestore.getInstance();
        colleagueList = new ArrayList<>();

        RecyclerView rvColleagues = findViewById(R.id.rv_colleagues);
        pbColleagues = findViewById(R.id.pb_colleagues);

        findViewById(R.id.iv_back_colleagues).setOnClickListener(v -> finish());

        rvColleagues.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ColleagueAdapter(colleagueList);
        rvColleagues.setAdapter(adapter);

        fetchColleagues();
    }

    private void fetchColleagues() {
        pbColleagues.setVisibility(View.VISIBLE);
        
        db.collection("users")
                .whereEqualTo("role", "employee")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        pbColleagues.setVisibility(View.GONE);
                        Toast.makeText(this, "No colleagues found", Toast.LENGTH_SHORT).show();
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
        
        Task<DocumentSnapshot> profileTask = db.collection("employees").document(uid).get();
        Task<DocumentSnapshot> picTask = db.collection("profile_pics").document(uid).get();
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
