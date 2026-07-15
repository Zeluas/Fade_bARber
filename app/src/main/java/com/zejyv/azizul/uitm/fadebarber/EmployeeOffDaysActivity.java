package com.zejyv.azizul.uitm.fadebarber;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.zejyv.azizul.uitm.fadebarber.adapters.OffDayRequestAdapter;
import com.zejyv.azizul.uitm.fadebarber.models.OffDayRequest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class EmployeeOffDaysActivity extends AppCompatActivity {

    private MaterialButton btnPickDate, btnPickEndDate, btnPickStartTime, btnPickEndTime, btnSubmit;
    private MaterialCheckBox cbWholeDay, cbSingleDay;
    private EditText etReason;
    private RecyclerView rvOffDays;
    private TextView tvNoRequests;
    private View cardRequestOffDay;
    private OffDayRequestAdapter adapter;
    private final List<OffDayRequest> requestList = new ArrayList<>();
    
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String currentUid;
    private boolean isAdmin = false;
    private ListenerRegistration registration;

    private String DEFAULT_START_LABEL;
    private String DEFAULT_END_LABEL;
    private String DEFAULT_DATE_LABEL;
    private String DEFAULT_START_DATE_LABEL;
    private String DEFAULT_END_DATE_LABEL;

    // Custom Time Picker State
    private Dialog timePickerDialog;
    private MaterialButton[] dialogHourButtons;
    private MaterialButton btnAm, btnPm;
    private int currentSelectedHour = 12;
    private String currentSelectedAmPm = "PM";
    
    private int selectedStartHour24 = -1;
    private int selectedEndHour24 = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_off_days);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUid = auth.getUid();

        DEFAULT_START_LABEL = getString(R.string.off_days_start_time_label);
        DEFAULT_END_LABEL = getString(R.string.off_days_end_time_label);
        DEFAULT_DATE_LABEL = getString(R.string.off_days_date_label);
        DEFAULT_START_DATE_LABEL = getString(R.string.off_days_start_date_label);
        DEFAULT_END_DATE_LABEL = getString(R.string.off_days_end_date_label);

        initializeViews();
        // Check role first, then setup RV and fetch
        checkUserRoleAndInitialize();
    }

    private void checkUserRoleAndInitialize() {
        db.collection("users").document(currentUid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String role = doc.getString("role");
                isAdmin = "admin".equalsIgnoreCase(role);
                if (isAdmin) {
                    cardRequestOffDay.setVisibility(View.GONE);
                }
            }
            // Now that we know the role, setup RV and fetch data
            setupRecyclerView();
            fetchOffDayRequests();
        }).addOnFailureListener(e -> {
            // Fallback
            setupRecyclerView();
            fetchOffDayRequests();
        });
    }

    private void initializeViews() {
        findViewById(R.id.btn_back_off_days).setOnClickListener(v -> finish());

        cardRequestOffDay = findViewById(R.id.card_request_off_day);
        btnPickDate = findViewById(R.id.btn_pick_date);
        btnPickEndDate = findViewById(R.id.btn_pick_end_date);
        btnPickStartTime = findViewById(R.id.btn_pick_start_time);
        btnPickEndTime = findViewById(R.id.btn_pick_end_time);
        btnSubmit = findViewById(R.id.btn_submit_off_day);
        cbWholeDay = findViewById(R.id.cb_whole_day);
        cbSingleDay = findViewById(R.id.cb_single_day);
        etReason = findViewById(R.id.et_off_reason);
        rvOffDays = findViewById(R.id.rv_off_days);
        tvNoRequests = findViewById(R.id.tv_no_off_days_placeholder);

        btnPickDate.setText(DEFAULT_DATE_LABEL);
        btnPickEndDate.setText(DEFAULT_END_DATE_LABEL);
        btnPickStartTime.setText(DEFAULT_START_LABEL);
        btnPickEndTime.setText(DEFAULT_END_LABEL);

        btnPickDate.setOnClickListener(v -> showDatePicker(true));
        btnPickEndDate.setOnClickListener(v -> showDatePicker(false));
        btnPickStartTime.setOnClickListener(v -> showCustomTimePicker(true));
        btnPickEndTime.setOnClickListener(v -> showCustomTimePicker(false));
        
        cbWholeDay.setOnCheckedChangeListener((buttonView, isChecked) -> updateTimeButtonsState(isChecked));
        
        cbSingleDay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            btnPickEndDate.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            if (!isChecked) {
                btnPickDate.setText(DEFAULT_START_DATE_LABEL);
                cbWholeDay.setChecked(true);
                cbWholeDay.setEnabled(false);
                cbWholeDay.setAlpha(0.5f);
            } else {
                btnPickDate.setText(DEFAULT_DATE_LABEL);
                cbWholeDay.setEnabled(true);
                cbWholeDay.setAlpha(1.0f);
            }
        });

        btnSubmit.setOnClickListener(v -> submitRequest());
        
        btnPickEndDate.setVisibility(cbSingleDay.isChecked() ? View.GONE : View.VISIBLE);
    }

    private void updateTimeButtonsState(boolean isWholeDay) {
        if (isWholeDay) {
            btnPickStartTime.setEnabled(false);
            btnPickEndTime.setEnabled(false);
            btnPickStartTime.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.darker_gray));
            btnPickEndTime.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.darker_gray));
            btnPickStartTime.setIconTintResource(android.R.color.white);
            btnPickEndTime.setIconTintResource(android.R.color.white);
            btnPickStartTime.setText(DEFAULT_START_LABEL);
            btnPickEndTime.setText(DEFAULT_END_LABEL);
            selectedStartHour24 = -1;
            selectedEndHour24 = -1;
        } else {
            btnPickStartTime.setEnabled(true);
            btnPickEndTime.setEnabled(true);
            btnPickStartTime.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary_color));
            btnPickEndTime.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary_color));
            btnPickStartTime.setIconTintResource(android.R.color.white);
            btnPickEndTime.setIconTintResource(android.R.color.white);
        }
    }

    private void showDatePicker(boolean isStart) {
        CalendarConstraints.Builder constraintsBuilder = new CalendarConstraints.Builder();
        
        long networkTime = NetworkTimeManager.getInstance().getCurrentTime();
        Calendar klCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        klCal.setTimeInMillis(networkTime);
        
        Calendar utcToday = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utcToday.set(klCal.get(Calendar.YEAR), klCal.get(Calendar.MONTH), klCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        utcToday.set(Calendar.MILLISECOND, 0);
        long todayUtcMillis = utcToday.getTimeInMillis();

        List<CalendarConstraints.DateValidator> validators = new ArrayList<>();
        validators.add(DateValidatorPointForward.from(todayUtcMillis));
        validators.add(new DisableFridaysValidator());
        constraintsBuilder.setValidator(CompositeDateValidator.allOf(validators));

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(isStart ? "Select Start Date" : "Select End Date")
                .setSelection(todayUtcMillis)
                .setCalendarConstraints(constraintsBuilder.build())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(selection);
            String date = String.format(Locale.getDefault(), "%02d/%02d/%02d", 
                    calendar.get(Calendar.DAY_OF_MONTH), 
                    calendar.get(Calendar.MONTH) + 1, 
                    calendar.get(Calendar.YEAR) % 100);
            if (isStart) btnPickDate.setText(date);
            else btnPickEndDate.setText(date);
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    private void showCustomTimePicker(boolean isStart) {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_time_picker, null);

        timePickerDialog = new Dialog(this);
        timePickerDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        timePickerDialog.setContentView(dialogView);

        if (timePickerDialog.getWindow() != null) {
            timePickerDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            timePickerDialog.getWindow().getDecorView().setPadding(0, 0, 0, 0);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        if (tvTitle != null) {
            tvTitle.setText(isStart ? "Choose Start Time" : "Choose End Time");
        }

        currentSelectedHour = 12;
        currentSelectedAmPm = "PM";

        int[] hourButtonIds = {
            R.id.btn_hour_1, R.id.btn_hour_2, R.id.btn_hour_3, R.id.btn_hour_4,
            R.id.btn_hour_5, R.id.btn_hour_6, R.id.btn_hour_7, R.id.btn_hour_8,
            R.id.btn_hour_9, R.id.btn_hour_10, R.id.btn_hour_11, R.id.btn_hour_12
        };

        dialogHourButtons = new MaterialButton[12];
        for (int i = 0; i < 12; i++) {
            final int hour = i + 1;
            dialogHourButtons[i] = dialogView.findViewById(hourButtonIds[i]);
            if (dialogHourButtons[i] != null) {
                dialogHourButtons[i].setOnClickListener(v -> {
                    currentSelectedHour = hour;
                    updateDialogHourButtons(isStart);
                });
            }
        }

        btnAm = dialogView.findViewById(R.id.btn_am);
        btnPm = dialogView.findViewById(R.id.btn_pm);

        View.OnClickListener amPmClick = v -> {
            currentSelectedAmPm = (v.getId() == R.id.btn_am) ? "AM" : "PM";
            updateDialogHourButtons(isStart);
        };

        if (btnAm != null) btnAm.setOnClickListener(amPmClick);
        if (btnPm != null) btnPm.setOnClickListener(amPmClick);

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> timePickerDialog.dismiss());
        dialogView.findViewById(R.id.btn_ok).setOnClickListener(v -> {
            String amPm = currentSelectedAmPm;
            int h12 = currentSelectedHour;
            int h24 = (h12 % 12) + (amPm.equals("PM") ? 12 : 0);
            
            String timeStr = String.format(Locale.getDefault(), "%02d:00\n%s", h12, amPm);
            
            if (isStart) {
                selectedStartHour24 = h24;
                btnPickStartTime.setText(timeStr);
                if (selectedEndHour24 != -1 && selectedEndHour24 <= selectedStartHour24 && cbSingleDay.isChecked()) {
                    btnPickEndTime.setText(DEFAULT_END_LABEL);
                    selectedEndHour24 = -1;
                }
            } else {
                selectedEndHour24 = h24;
                btnPickEndTime.setText(timeStr);
            }
            timePickerDialog.dismiss();
        });

        updateDialogHourButtons(isStart);
        timePickerDialog.show();
    }

    private void updateDialogHourButtons(boolean isStart) {
        if (dialogHourButtons == null || timePickerDialog == null) return;

        for (int i = 0; i < 12; i++) {
            int hour12 = i + 1;
            int h24 = (hour12 % 12) + (currentSelectedAmPm.equals("PM") ? 12 : 0);
            
            boolean isInvalid = false;
            if (!isStart && selectedStartHour24 != -1 && cbSingleDay.isChecked()) {
                if (h24 <= selectedStartHour24) {
                    isInvalid = true;
                }
            }

            if (dialogHourButtons[i] != null) {
                dialogHourButtons[i].setEnabled(!isInvalid);
                dialogHourButtons[i].setAlpha(isInvalid ? 0.3f : 1.0f);
                updateTimeButtonStyle(dialogHourButtons[i], hour12 == currentSelectedHour && !isInvalid);
            }
        }

        if (btnAm != null && btnPm != null) {
            boolean amInvalid = false;
            boolean pmInvalid = false;
            
            if (!isStart && selectedStartHour24 != -1 && cbSingleDay.isChecked()) {
                if (selectedStartHour24 >= 12) amInvalid = true;
                if (selectedStartHour24 >= 23) pmInvalid = true;
            }

            btnAm.setEnabled(!amInvalid);
            btnAm.setAlpha(amInvalid ? 0.3f : 1.0f);
            updateTimeButtonStyle(btnAm, currentSelectedAmPm.equals("AM") && !amInvalid);

            btnPm.setEnabled(!pmInvalid);
            btnPm.setAlpha(pmInvalid ? 0.3f : 1.0f);
            updateTimeButtonStyle(btnPm, currentSelectedAmPm.equals("PM") && !pmInvalid);
        }
    }

    private void updateTimeButtonStyle(MaterialButton button, boolean isSelected) {
        if (isSelected) {
            button.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary_color));
            button.setTextColor(Color.WHITE);
            button.setStrokeColorResource(R.color.primary_color);
        } else {
            button.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.white));
            button.setTextColor(ContextCompat.getColor(this, R.color.primary_color));
            button.setStrokeColorResource(R.color.primary_color);
        }
    }

    private void submitRequest() {
        btnSubmit.setEnabled(false);
        String startDateStr = btnPickDate.getText().toString();
        String endDateStr = cbSingleDay.isChecked() ? startDateStr : btnPickEndDate.getText().toString();
        
        if (DEFAULT_DATE_LABEL.equals(startDateStr) || DEFAULT_START_DATE_LABEL.equals(startDateStr)) {
            Toast.makeText(this, "Please pick a start date", Toast.LENGTH_SHORT).show();
            btnSubmit.setEnabled(true);
            return;
        }
        
        if (!cbSingleDay.isChecked() && DEFAULT_END_DATE_LABEL.equals(endDateStr)) {
            Toast.makeText(this, "Please pick an end date", Toast.LENGTH_SHORT).show();
            btnSubmit.setEnabled(true);
            return;
        }

        if (!cbWholeDay.isChecked()) {
            if (selectedStartHour24 == -1) {
                Toast.makeText(this, "Please select a start time", Toast.LENGTH_SHORT).show();
                btnSubmit.setEnabled(true);
                return;
            }
            if (selectedEndHour24 == -1) {
                Toast.makeText(this, "Please select an end time", Toast.LENGTH_SHORT).show();
                btnSubmit.setEnabled(true);
                return;
            }
        }

        OffDayRequest request = new OffDayRequest();
        request.setEmployeeId(currentUid);
        request.setOffDateRequest(startDateStr + (cbSingleDay.isChecked() ? "" : " to " + endDateStr));
        request.setWholeDay(cbWholeDay.isChecked());
        
        if (!request.isWholeDay()) {
            request.setStartTime(btnPickStartTime.getText().toString().replace("\n", " "));
            request.setEndTime(btnPickEndTime.getText().toString().replace("\n", " "));
        } else {
            request.setStartTime("12:00 AM");
            request.setEndTime("11:59 PM");
        }
        
        request.setReason(etReason.getText().toString().trim());

        db.collection("off_day_requests").add(request)
                .addOnSuccessListener(documentReference -> {
                    String id = documentReference.getId();
                    documentReference.update("offDayId", id);
                    Toast.makeText(this, "Request submitted", Toast.LENGTH_SHORT).show();
                    resetForm();
                    btnSubmit.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSubmit.setEnabled(true);
                });
    }

    private void resetForm() {
        btnPickDate.setText(DEFAULT_DATE_LABEL);
        btnPickEndDate.setText(DEFAULT_END_DATE_LABEL);
        cbSingleDay.setChecked(true);
        cbWholeDay.setChecked(false);
        cbWholeDay.setEnabled(true);
        cbWholeDay.setAlpha(1.0f);
        updateTimeButtonsState(false);
        btnPickStartTime.setText(DEFAULT_START_LABEL);
        btnPickEndTime.setText(DEFAULT_END_LABEL);
        selectedStartHour24 = -1;
        selectedEndHour24 = -1;
        etReason.setText("");
    }

    private void setupRecyclerView() {
        adapter = new OffDayRequestAdapter(requestList, currentUid, isAdmin, new OffDayRequestAdapter.OnRequestActionListener() {
            @Override
            public void onVote(OffDayRequest request, boolean accept) {
                db.collection("off_day_requests").document(request.getOffDayId())
                        .update("votes." + currentUid, accept);
            }

            @Override
            public void onAdminDecision(OffDayRequest request, boolean approve) {
                db.collection("off_day_requests").document(request.getOffDayId())
                        .update("status", approve ? "APPROVED" : "REJECTED");
            }
        });
        rvOffDays.setLayoutManager(new LinearLayoutManager(this));
        rvOffDays.setAdapter(adapter);
    }

    private void fetchOffDayRequests() {
        registration = db.collection("off_day_requests")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value != null) {
                        requestList.clear();
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : value) {
                            requestList.add(doc.toObject(OffDayRequest.class));
                        }
                        adapter.notifyDataSetChanged();
                        tvNoRequests.setVisibility(requestList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (registration != null) registration.remove();
    }

    static class DisableFridaysValidator implements CalendarConstraints.DateValidator {
        public static final Parcelable.Creator<DisableFridaysValidator> CREATOR =
                new Parcelable.Creator<>() {
                    @Override
                    public DisableFridaysValidator createFromParcel(Parcel in) {
                        return new DisableFridaysValidator();
                    }

                    @Override
                    public DisableFridaysValidator[] newArray(int size) {
                        return new DisableFridaysValidator[size];
                    }
                };

        @Override
        public boolean isValid(long date) {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(date);
            int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            return dayOfWeek != Calendar.FRIDAY;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
        }
    }
}
