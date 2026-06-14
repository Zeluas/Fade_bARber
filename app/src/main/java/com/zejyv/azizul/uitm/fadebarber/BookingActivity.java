package com.zejyv.azizul.uitm.fadebarber;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.FieldValue;
import com.zejyv.azizul.uitm.fadebarber.adapters.StylistAdapter;
import com.zejyv.azizul.uitm.fadebarber.models.Booking;
import com.zejyv.azizul.uitm.fadebarber.models.Employee;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * BookingActivity handles the appointment booking process.
 * It allows users to pick a date, select a time slot, choose a stylist,
 * and navigate to the AR Try-On experience.
 */
public class BookingActivity extends AppCompatActivity {

    // --- UI Components ---
    private TextView tvDate, tvTime;
    private RecyclerView rvStylists;
    private ProgressBar pbStylists;
    private TextView tvNoStylists;
    private StylistAdapter stylistAdapter;
    private List<Employee> stylistList = new ArrayList<>();
    private View loadingOverlay;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private SharedPreferences encryptedPrefs;
    private Employee selectedEmployee = null;
    private String selectedHairstyleName = "";
    private String selectedHairstyleId = "";
    private List<String> bookedTimes = new ArrayList<>();
    private List<String> fullDates = new ArrayList<>();
    private long serverTimeOffset = 0;

    // --- Time Picker State (Dynamic UI) ---
    private Dialog timePickerDialog;
    private MaterialButton[] dialogHourButtons;
    private MaterialButton btnAm, btnPm;
    private String currentSelectedAmPm = "AM";
    private int currentSelectedHour = 10;
    private ListenerRegistration bookedTimesListener;
    private ListenerRegistration fullDatesListener;

    // Result Launcher for TryOnActivity
    private final androidx.activity.result.ActivityResultLauncher<Intent> tryOnLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            String name = result.getData().getStringExtra("HAIRSTYLE_NAME");
                            String desc = result.getData().getStringExtra("HAIRSTYLE_DESC");
                            String key = result.getData().getStringExtra("HAIRSTYLE_KEY");
                            String id = result.getData().getStringExtra("HAIRSTYLE_ID");

                            selectedHairstyleName = name;
                            selectedHairstyleId = id;
                            updateHairstylePreview(name, desc, key);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        initEncryptedPrefs();

        // --- View Initialization ---

        // Navigation and Header elements
        ImageView ivBack = findViewById(R.id.iv_back_booking);
        
        // Picker triggers
        LinearLayout llDatePicker = findViewById(R.id.ll_date_picker_controls);
        LinearLayout llTimePicker = findViewById(R.id.ll_time_picker_controls);
        
        // Display fields
        tvDate = findViewById(R.id.tv_booking_date);
        tvTime = findViewById(R.id.tv_booking_time);

        // Stylist Selection Components
        rvStylists = findViewById(R.id.rv_stylist_list);
        pbStylists = findViewById(R.id.pb_stylist_loading);
        tvNoStylists = findViewById(R.id.tv_no_stylists);

        // Action Buttons
        MaterialButton btnChooseHairstyle = findViewById(R.id.btn_choose_hairstyle);
        MaterialButton btnConfirmBooking = findViewById(R.id.btn_confirm_booking);

        // Loading Overlay
        loadingOverlay = findViewById(R.id.loading_overlay);

        // --- Initial Setup ---
        
        // Synchronize with Firebase Server Time before setting defaults
        syncServerTimeAndInit();
        
        // Configure stylist selection RecyclerView
        setupStylistRecyclerView();

        // Fetch stylists from Firebase
        fetchStylists();

        // --- Click Listeners ---

        // Back button navigation
        ivBack.setOnClickListener(v -> finish());

        // Date Picker logic
        View.OnClickListener dateClick = v -> showDatePicker();
        llDatePicker.setOnClickListener(dateClick);
        tvDate.setOnClickListener(dateClick);
        findViewById(R.id.iv_calendar_button).setOnClickListener(dateClick);

        // Time Picker logic
        View.OnClickListener timeClick = v -> showCustomTimePicker();
        llTimePicker.setOnClickListener(timeClick);
        tvTime.setOnClickListener(timeClick);
        findViewById(R.id.iv_clock_button).setOnClickListener(timeClick);

        // Navigation to Try-On Activity
        btnChooseHairstyle.setOnClickListener(v -> {
            btnChooseHairstyle.setEnabled(false);
            Intent intent = new Intent(BookingActivity.this, TryOnActivity.class);
            intent.putExtra("FROM_BOOKING", true);
            tryOnLauncher.launch(intent);
            btnChooseHairstyle.postDelayed(() -> btnChooseHairstyle.setEnabled(true), 1000);
        });

        // Final Booking Confirmation
        btnConfirmBooking.setOnClickListener(v -> {
            if (selectedEmployee == null) {
                Toast.makeText(this, "Please select a hairstylist", Toast.LENGTH_SHORT).show();
                return;
            }

            String currentTime = tvTime.getText().toString();
            if (currentTime.equals("00:00")) {
                Toast.makeText(this, "Please select an appointment time", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedHairstyleId == null || selectedHairstyleId.isEmpty()) {
                Toast.makeText(this, "Please choose a hairstyle", Toast.LENGTH_SHORT).show();
                return;
            }

            if (bookedTimes.contains(currentTime)) {
                Toast.makeText(this, "This time slot is already booked. Please choose another.", Toast.LENGTH_LONG).show();
                return;
            }

            btnConfirmBooking.setEnabled(false); // Prevent double-clicks
            saveBookingToFirebase(btnConfirmBooking);
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

    /**
     * Checks if the device has an active internet connection.
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
            return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        }
        return false;
    }

    private void syncServerTimeAndInit() {
        if (!isNetworkAvailable()) {
            setDefaultDateTime();
            return;
        }

        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);

        // Fetch server time by writing a dummy timestamp to a specific metadata document
        String uid = mAuth.getUid() != null ? mAuth.getUid() : "anonymous_sync";
        java.util.Map<String, Object> syncData = new java.util.HashMap<>();
        syncData.put("t", FieldValue.serverTimestamp());

        db.collection("metadata").document("time_sync_" + uid).set(syncData)
                .addOnSuccessListener(aVoid -> {
                    db.collection("metadata").document("time_sync_" + uid).get(Source.SERVER)
                            .addOnSuccessListener(documentSnapshot -> {
                                Timestamp serverTime = documentSnapshot.getTimestamp("t");
                                if (serverTime != null) {
                                    serverTimeOffset = serverTime.toDate().getTime() - System.currentTimeMillis();
                                }
                                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                                setDefaultDateTime();
                            })
                            .addOnFailureListener(e -> {
                                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                                setDefaultDateTime();
                            });
                })
                .addOnFailureListener(e -> {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                    setDefaultDateTime();
                });
    }

    private void saveBookingToFirebase(MaterialButton confirmButton) {
        if (!isNetworkAvailable()) {
            showStatusDialog(false, "No Connection", "Please check your internet connection and try again.", true);
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            showStatusDialog(false, "Login Required", "Please log in to confirm your booking.", true);
            confirmButton.setEnabled(true);
            return;
        }

        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);

        String bookingId = "FB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String customerId = currentUser.getUid();
        String customerName = encryptedPrefs != null ? encryptedPrefs.getString("name", "Unknown Customer") : "Unknown Customer";
        String employeeId = selectedEmployee.getUid();
        String employeeName = selectedEmployee.getFullname();
        String date = tvDate.getText().toString();
        String time = tvTime.getText().toString();
        String status = "Pending";

        // Final check before sending to Firestore
        if (bookedTimes.contains(time)) {
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
            showStatusDialog(false, "Slot Unavailable", "This time slot was just taken. Please choose another.", false);
            confirmButton.setEnabled(true);
            return;
        }

        java.util.Map<String, Object> bookingMap = new java.util.HashMap<>();
        bookingMap.put("bookingId", bookingId);
        bookingMap.put("customerId", customerId);
        bookingMap.put("customerName", customerName);
        bookingMap.put("employeeId", employeeId);
        bookingMap.put("employeeName", employeeName);
        bookingMap.put("date", date);
        bookingMap.put("time", time);
        bookingMap.put("hairstyleName", selectedHairstyleName);
        bookingMap.put("hairstyleId", selectedHairstyleId);
        bookingMap.put("status", status);
        bookingMap.put("createdAt", FieldValue.serverTimestamp());

        db.collection("bookings").document(bookingId).set(bookingMap)
                .addOnSuccessListener(aVoid -> {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                    showStatusDialog(true, "Booking Successful!", "Your appointment has been scheduled. We look forward to seeing you!", true);
                })
                .addOnFailureListener(e -> {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                    confirmButton.setEnabled(true);
                    String errorMsg = getFriendlyError(e.getMessage());
                    showStatusDialog(false, "Booking Failed", errorMsg, false);
                    android.util.Log.e("BookingActivity", "Firebase Error: " + e.getMessage());
                });
    }

    private void showStatusDialog(boolean isSuccess, String title, String message, boolean shouldExit) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_booking_status);
        dialog.setCancelable(false);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        ImageView ivIcon = dialog.findViewById(R.id.iv_status_icon);
        TextView tvTitle = dialog.findViewById(R.id.tv_status_title);
        TextView tvMessage = dialog.findViewById(R.id.tv_status_message);
        MaterialButton btnOk = dialog.findViewById(R.id.btn_status_ok);

        tvTitle.setText(title);
        tvMessage.setText(message);

        if (isSuccess) {
            ivIcon.setImageResource(R.drawable.ic_check_circle);
            ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary_color));
        } else {
            ivIcon.setImageResource(R.drawable.ic_warning_circle);
            ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.warning_red_icon));
        }

        btnOk.setOnClickListener(v -> {
            dialog.dismiss();
            if (shouldExit) finish();
        });

        dialog.show();
    }

    private String getFriendlyError(String firebaseError) {
        if (firebaseError == null) return "An unknown error occurred. Please try again.";
        if (firebaseError.contains("permission-denied")) return "You don't have permission to perform this action.";
        if (firebaseError.contains("unavailable")) return "The service is temporarily unavailable. Check your internet connection.";
        if (firebaseError.contains("network-error")) return "Network error. Please check your connection.";
        return "Oops! Something went wrong on our end. Please try again later.";
    }

    /**
     * Initializes the date and time displays with default values.
     * Logic: Uses synced server time (Kuala Lumpur), adjusted for business hours and availability.
     */
    private void setDefaultDateTime() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        c.setTimeInMillis(System.currentTimeMillis() + serverTimeOffset);
        
        // If today is Friday (shop closed), default to tomorrow (Saturday)
        if (c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
            c.add(Calendar.DAY_OF_MONTH, 1);
            // Default to start of business day on Saturday
            c.set(Calendar.HOUR_OF_DAY, 10);
            c.set(Calendar.MINUTE, 0);
        }

        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);
        
        // Format: DD/MM/YY
        String date = String.format(Locale.getDefault(), "%02d/%02d/%02d", day, month + 1, year % 100);
        tvDate.setText(date);

        // Initial hour/amPm from synced KL time
        int hour = c.get(Calendar.HOUR);
        if (hour == 0) hour = 12;
        currentSelectedHour = hour;
        currentSelectedAmPm = (c.get(Calendar.AM_PM) == Calendar.AM) ? "AM" : "PM";

        // Validate synced time against business hours and bookings
        if (!isWithinBusinessHours(currentSelectedHour, currentSelectedAmPm) ||
            bookedTimes.contains(String.format(Locale.getDefault(), "%02d:00 %s", currentSelectedHour, currentSelectedAmPm))) {
            findFirstAvailableSlot();
        }

        tvTime.setText(String.format(Locale.getDefault(), "%02d:00 %s", currentSelectedHour, currentSelectedAmPm));
    }

    /**
     * Displays a MaterialDatePicker with constraints to disable past dates and Fridays.
     */
    private void showDatePicker() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);

        CalendarConstraints.Builder constraintsBuilder = new CalendarConstraints.Builder();

        // Combine validators: disable past dates (synced KL), disable Fridays, and disable full dates
        Calendar serverCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        serverCal.setTimeInMillis(System.currentTimeMillis() + serverTimeOffset);
        
        // MaterialDatePicker works in UTC
        Calendar utcToday = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utcToday.set(serverCal.get(Calendar.YEAR), serverCal.get(Calendar.MONTH), serverCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        utcToday.set(Calendar.MILLISECOND, 0);
        long todayUtcMillis = utcToday.getTimeInMillis();

        List<CalendarConstraints.DateValidator> validators = new ArrayList<>();
        validators.add(DateValidatorPointForward.from(todayUtcMillis));
        validators.add(new DisableFridaysValidator());
        validators.add(new FullDatesValidator(fullDates));

        constraintsBuilder.setValidator(CompositeDateValidator.allOf(validators));

        // Default selection: today (synced KL), or tomorrow if today is Friday
        long selection = todayUtcMillis;
        if (serverCal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
            utcToday.add(Calendar.DAY_OF_MONTH, 1);
            selection = utcToday.getTimeInMillis();
        }

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.booking_date_hint))
                .setSelection(selection)
                .setCalendarConstraints(constraintsBuilder.build())
                .build();

        datePicker.addOnPositiveButtonClickListener(selectedDate -> {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(selectedDate);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            int month = calendar.get(Calendar.MONTH) + 1;
            int year = calendar.get(Calendar.YEAR) % 100;
            
            // Apply selected date to UI
            String date = String.format(Locale.getDefault(), "%02d/%02d/%02d", day, month, year);
            tvDate.setText(date);
            fetchBookedTimes();
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
        
        // Hide loading after a short delay to ensure the calendar is rendering
        if (loadingOverlay != null) {
            loadingOverlay.postDelayed(() -> loadingOverlay.setVisibility(View.GONE), 400);
        }
    }

    /**
     * Custom DateValidator to ensure Fridays cannot be selected.
     */
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
            // Shop is closed on Fridays
            return dayOfWeek != Calendar.FRIDAY;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
        }
    }

    /**
     * Custom DateValidator to ensure fully booked dates cannot be selected.
     */
    static class FullDatesValidator implements CalendarConstraints.DateValidator {
        private List<String> fullDates;

        public FullDatesValidator(List<String> fullDates) {
            this.fullDates = fullDates;
        }

        public static final Parcelable.Creator<FullDatesValidator> CREATOR =
                new Parcelable.Creator<>() {
                    @Override
                    public FullDatesValidator createFromParcel(Parcel in) {
                        return new FullDatesValidator(in.createStringArrayList());
                    }

                    @Override
                    public FullDatesValidator[] newArray(int size) {
                        return new FullDatesValidator[size];
                    }
                };

        @Override
        public boolean isValid(long date) {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(date);
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            int month = calendar.get(Calendar.MONTH) + 1;
            int year = calendar.get(Calendar.YEAR) % 100;
            String dateStr = String.format(Locale.getDefault(), "%02d/%02d/%02d", day, month, year);
            return !fullDates.contains(dateStr);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeStringList(fullDates);
        }
    }

    /**
     * Displays a custom dialog for selecting hour-based time slots.
     */
    private void showCustomTimePicker() {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_time_picker, null);

        timePickerDialog = new Dialog(this);
        timePickerDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        timePickerDialog.setContentView(dialogView);

        if (timePickerDialog.getWindow() != null) {
            timePickerDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            timePickerDialog.getWindow().getDecorView().setPadding(0, 0, 0, 0);
        }

        // Initialize dialog state
        String currentTime = tvTime.getText().toString();
        if (currentTime.equals("00:00")) {
            currentSelectedHour = 1;
            currentSelectedAmPm = "PM";
        } else {
            try {
                currentSelectedHour = Integer.parseInt(currentTime.substring(0, 2));
                currentSelectedAmPm = currentTime.substring(6);
            } catch (Exception e) {
                currentSelectedHour = 1;
                currentSelectedAmPm = "PM";
            }
        }

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
                    updateDialogHourButtons();
                });
            }
        }

        btnAm = dialogView.findViewById(R.id.btn_am);
        btnPm = dialogView.findViewById(R.id.btn_pm);

        View.OnClickListener amPmClick = v -> {
            currentSelectedAmPm = (v.getId() == R.id.btn_am) ? "AM" : "PM";
            updateDialogHourButtons();
        };

        if (btnAm != null) btnAm.setOnClickListener(amPmClick);
        if (btnPm != null) btnPm.setOnClickListener(amPmClick);

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> timePickerDialog.dismiss());
        dialogView.findViewById(R.id.btn_ok).setOnClickListener(v -> {
            String selectedTime = String.format(Locale.getDefault(), "%02d:00 %s", currentSelectedHour, currentSelectedAmPm);
            if (bookedTimes.contains(selectedTime)) {
                Toast.makeText(this, "This slot was just taken. Please pick another.", Toast.LENGTH_SHORT).show();
                updateDialogHourButtons();
                return;
            }
            tvTime.setText(selectedTime);
            timePickerDialog.dismiss();
        });

        // Sync visual state before showing
        updateDialogHourButtons();
        timePickerDialog.show();
    }

    private void updateDialogHourButtons() {
        if (dialogHourButtons == null || timePickerDialog == null) return;

        boolean currentIsInvalid = false;

        for (int i = 0; i < 12; i++) {
            int hour = i + 1;
            String timeStr = String.format(Locale.getDefault(), "%02d:00 %s", hour, currentSelectedAmPm);
            
            boolean isBooked = bookedTimes.contains(timeStr);
            boolean isBusinessHours = isWithinBusinessHours(hour, currentSelectedAmPm);
            boolean isDisabled = isBooked || !isBusinessHours;

            if (dialogHourButtons[i] != null) {
                dialogHourButtons[i].setEnabled(!isDisabled);
                dialogHourButtons[i].setAlpha(isDisabled ? 0.3f : 1.0f);
                
                boolean isSelected = (hour == currentSelectedHour);
                if (isSelected && isDisabled) {
                    currentIsInvalid = true;
                }
                updateTimeButtonStyle(dialogHourButtons[i], isSelected && !isDisabled);
            }
        }
        
        // If the current selection was booked or is outside hours, shift to first available
        if (currentIsInvalid) {
            findFirstAvailableSlot();
            // Re-run the visual update for the new selection
            for (int i = 0; i < 12; i++) {
                int hour = i + 1;
                String timeStr = String.format(Locale.getDefault(), "%02d:00 %s", hour, currentSelectedAmPm);
                boolean isBooked = bookedTimes.contains(timeStr);
                boolean isBusinessHours = isWithinBusinessHours(hour, currentSelectedAmPm);
                boolean isDisabled = isBooked || !isBusinessHours;
                
                if (dialogHourButtons[i] != null) {
                    dialogHourButtons[i].setEnabled(!isDisabled);
                    dialogHourButtons[i].setAlpha(isDisabled ? 0.3f : 1.0f);
                    updateTimeButtonStyle(dialogHourButtons[i], hour == currentSelectedHour && !isDisabled);
                }
            }
        }
        
        if (btnAm != null && btnPm != null) {
            boolean amFull = isPeriodFullyBooked("AM");
            boolean pmFull = isPeriodFullyBooked("PM");

            btnAm.setEnabled(!amFull);
            btnAm.setAlpha(amFull ? 0.3f : 1.0f);
            updateTimeButtonStyle(btnAm, currentSelectedAmPm.equals("AM") && !amFull);

            btnPm.setEnabled(!pmFull);
            btnPm.setAlpha(pmFull ? 0.3f : 1.0f);
            updateTimeButtonStyle(btnPm, currentSelectedAmPm.equals("PM") && !pmFull);
        }
    }

    private boolean isPeriodFullyBooked(String amPm) {
        for (int h = 1; h <= 12; h++) {
            if (isWithinBusinessHours(h, amPm)) {
                String timeStr = String.format(Locale.getDefault(), "%02d:00 %s", h, amPm);
                if (!bookedTimes.contains(timeStr)) {
                    return false; // Found at least one valid and available slot
                }
            }
        }
        return true; // All business hours for this period are booked
    }

    private boolean isWithinBusinessHours(int hour, String amPm) {
        if (amPm.equals("AM")) {
            // Shop opens at 10 AM. 12 AM (Midnight) is closed.
            return hour == 10 || hour == 11;
        } else {
            // PM
            // 12 PM (Noon) is valid.
            // 2 PM is break time.
            if (hour == 2) return false;
            // 12 AM was mentioned as shift over, which corresponds to the start of AM cycle.
            // So all PM hours except 2 PM are valid.
            return true;
        }
    }

    private void findFirstAvailableSlot() {
        // Try current AM/PM first
        for (int h = 1; h <= 12; h++) {
            if (isWithinBusinessHours(h, currentSelectedAmPm) &&
                !bookedTimes.contains(String.format(Locale.getDefault(), "%02d:00 %s", h, currentSelectedAmPm))) {
                currentSelectedHour = h;
                return;
            }
        }
        // Try other AM/PM
        String otherAmPm = currentSelectedAmPm.equals("AM") ? "PM" : "AM";
        for (int h = 1; h <= 12; h++) {
            if (isWithinBusinessHours(h, otherAmPm) &&
                !bookedTimes.contains(String.format(Locale.getDefault(), "%02d:00 %s", h, otherAmPm))) {
                currentSelectedHour = h;
                currentSelectedAmPm = otherAmPm;
                return;
            }
        }
    }

    private void fetchBookedTimes() {
        if (selectedEmployee == null) return;
        
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet. Availability might be outdated.", Toast.LENGTH_SHORT).show();
            return;
        }

        String date = tvDate.getText().toString();
        String currentUid = mAuth.getUid();
        
        if (bookedTimesListener != null) {
            bookedTimesListener.remove();
        }

        // Simpler query to avoid mandatory manual composite index for 'whereNotEqualTo'
        bookedTimesListener = db.collection("bookings")
                .whereEqualTo("date", date)
                .whereEqualTo("employeeId", selectedEmployee.getUid())
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        android.util.Log.e("BookingActivity", "Firestore error: " + error.getMessage());
                        return;
                    }
                    if (value != null) {
                        bookedTimes.clear();
                        String currentTime = tvTime.getText().toString();
                        boolean conflictFromOtherUser = false;

                        for (QueryDocumentSnapshot doc : value) {
                            String status = doc.getString("status");
                            String time = doc.getString("time");
                            String cid = doc.getString("customerId");
                            
                            // Only count non-cancelled bookings
                            if (time != null && !"Cancelled".equalsIgnoreCase(status)) {
                                bookedTimes.add(time);
                                
                                // Check if someone ELSE took our currently selected slot
                                if (time.equals(currentTime) && currentUid != null && !currentUid.equals(cid)) {
                                    conflictFromOtherUser = true;
                                }
                            }
                        }
                        
                        // Only show toast and switch if it was booked by ANOTHER user
                        if (conflictFromOtherUser) {
                            findFirstAvailableSlot();
                            tvTime.setText(String.format(Locale.getDefault(), "%02d:00 %s", currentSelectedHour, currentSelectedAmPm));
                            Toast.makeText(this, "The selected slot was just taken by someone else. Switched to next available.", Toast.LENGTH_SHORT).show();
                        }

                        // Refresh UI dynamically if dialog is open
                        if (timePickerDialog != null && timePickerDialog.isShowing()) {
                            runOnUiThread(this::updateDialogHourButtons);
                        }
                    }
                });

        fetchFullDates();
    }

    private void fetchFullDates() {
        if (selectedEmployee == null) return;

        if (fullDatesListener != null) {
            fullDatesListener.remove();
        }

        // Total possible slots based on business hours:
        // AM: 10, 11 (2)
        // PM: 12, 1, 3, 4, 5, 6, 7, 8, 9, 10, 11 (11)
        // Total = 13
        final int TOTAL_DAILY_SLOTS = 13;

        fullDatesListener = db.collection("bookings")
                .whereEqualTo("employeeId", selectedEmployee.getUid())
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value != null) {
                        java.util.Map<String, Integer> dateCounts = new java.util.HashMap<>();
                        for (QueryDocumentSnapshot doc : value) {
                            String status = doc.getString("status");
                            String d = doc.getString("date");
                            if (d != null && !"Cancelled".equalsIgnoreCase(status)) {
                                dateCounts.compute(d, (key, val) -> (val == null) ? 1 : val + 1);
                            }
                        }
                        fullDates.clear();
                        for (java.util.Map.Entry<String, Integer> entry : dateCounts.entrySet()) {
                            if (entry.getValue() >= TOTAL_DAILY_SLOTS) {
                                fullDates.add(entry.getKey());
                            }
                        }

                        // Check if currently selected date is full, if so, jump
                        String currentDate = tvDate.getText().toString();
                        if (fullDates.contains(currentDate)) {
                            findNextAvailableDate();
                        }
                    }
                });
    }

    private void findNextAvailableDate() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        c.setTimeInMillis(System.currentTimeMillis() + serverTimeOffset);
        
        // Attempt to parse current date to start searching from there
        try {
            String[] parts = tvDate.getText().toString().split("/");
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]) - 1;
            int year = 2000 + Integer.parseInt(parts[2]);
            c.set(year, month, day);
        } catch (Exception ignored) {}

        // Search up to 60 days ahead
        for (int i = 0; i < 60; i++) {
            c.add(Calendar.DAY_OF_MONTH, 1);
            
            // Skip Fridays
            if (c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) continue;

            String dateStr = String.format(Locale.getDefault(), "%02d/%02d/%02d",
                    c.get(Calendar.DAY_OF_MONTH),
                    c.get(Calendar.MONTH) + 1,
                    c.get(Calendar.YEAR) % 100);

            if (!fullDates.contains(dateStr)) {
                tvDate.setText(dateStr);
                fetchBookedTimes();
                Toast.makeText(this, "The selected date is full. Switched to " + dateStr, Toast.LENGTH_SHORT).show();
                return;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bookedTimesListener != null) bookedTimesListener.remove();
        if (fullDatesListener != null) fullDatesListener.remove();
    }

    /**
     * Updates the background tint and text color of picker buttons to reflect selection.
     */
    private void updateTimeButtonStyle(View view, boolean isSelected) {
        if (view instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) view;
            if (isSelected) {
                // Highlighted state
                button.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary_color));
                button.setTextColor(ContextCompat.getColor(this, R.color.white));
                button.setStrokeWidth(0);
            } else {
                // Default/Deselected state
                button.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.white));
                button.setTextColor(ContextCompat.getColor(this, R.color.primary_color));
                button.setStrokeWidth((int) (1 * getResources().getDisplayMetrics().density));
            }
        }
    }

    /**
     * Configures the RecyclerView for stylist selection.
     */
    private void setupStylistRecyclerView() {
        rvStylists.setLayoutManager(new LinearLayoutManager(this));
        stylistAdapter = new StylistAdapter(stylistList, employee -> {
            selectedEmployee = employee;
            fetchBookedTimes();
        });
        rvStylists.setAdapter(stylistAdapter);
    }

    /**
     * Fetches the list of employees from Firestore.
     */
    private void fetchStylists() {
        if (!isNetworkAvailable()) {
            tvNoStylists.setVisibility(View.VISIBLE);
            tvNoStylists.setText("No internet connection.");
            return;
        }

        pbStylists.setVisibility(View.VISIBLE);
        db.collection("employees").get(Source.SERVER).addOnCompleteListener(task -> {
            pbStylists.setVisibility(View.GONE);
            if (task.isSuccessful() && task.getResult() != null) {
                stylistList.clear();
                for (QueryDocumentSnapshot document : task.getResult()) {
                    Employee employee = document.toObject(Employee.class);
                    stylistList.add(employee);
                }
                stylistAdapter.notifyDataSetChanged();
                
                if (stylistList.isEmpty()) {
                    tvNoStylists.setVisibility(View.VISIBLE);
                } else {
                    tvNoStylists.setVisibility(View.GONE);
                }
            } else {
                Toast.makeText(this, "Failed to fetch stylists. Please check your network.", Toast.LENGTH_SHORT).show();
                tvNoStylists.setVisibility(View.VISIBLE);
                tvNoStylists.setText("Error loading stylists.");
            }
        });
    }

    /**
     * Updates the hairstyle preview section with selected data from TryOnActivity.
     */
    private void updateHairstylePreview(String name, String desc, String key) {
        TextView tvName = findViewById(R.id.tv_haircut_name_booking);
        TextView tvDesc = findViewById(R.id.tv_haircut_desc_booking);
        ImageView ivPreview = findViewById(R.id.iv_hairPreview_booking);
        TextView tvEmpty = findViewById(R.id.tv_empty_preview);

        if (tvName != null) {
            tvName.setText(name);
            tvName.setVisibility(View.VISIBLE);
        }
        if (tvDesc != null) {
            tvDesc.setText(desc);
            tvDesc.setVisibility(View.VISIBLE);
        }
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE); // Hide placeholder text

        if (ivPreview != null) {
            try {
                // Try to load the corresponding preview image from assets/images
                String[] images = getAssets().list("images");
                if (images != null) {
                    for (String imageName : images) {
                        if (imageName.toLowerCase().startsWith(key.toLowerCase())) {
                            try (java.io.InputStream is = getAssets().open("images/" + imageName)) {
                                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
                                ivPreview.setImageBitmap(bitmap);
                                return;
                            }
                        }
                    }
                }
                // Fallback if no specific image found
                ivPreview.setImageResource(R.drawable.ic_hair);
            } catch (java.io.IOException e) {
                ivPreview.setImageResource(R.drawable.ic_hair);
            }
        }
    }
}
