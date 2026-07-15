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
import android.widget.RatingBar;
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
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
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
    private com.google.android.material.textfield.TextInputLayout tilEditReason;
    private com.google.android.material.textfield.TextInputEditText etEditReason;
    private StylistAdapter stylistAdapter;
    private List<Employee> stylistList = new ArrayList<>();
    private View loadingOverlay;
    private ListenerRegistration employeesListener;
    private ListenerRegistration innerEmployeesListener;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private SharedPreferences encryptedPrefs;
    private Employee selectedEmployee = null;
    private String selectedHairstyleName = "";
    private String selectedHairstyleId = "";
    private String editingBookingId = null;
    private boolean isEditMode = false;
    private boolean isEmployeeEdit = false;
    private String originalTime = "";
    private String originalDate = "";
    private String originalEmployeeId = "";
    private String originalCustomerId = "";
    private String originalCustomerName = "";
    private String originalHairstyleName = "";
    private String originalHairstyleId = "";
    private List<String> bookedTimes = new ArrayList<>();
    private List<String> fullDates = new ArrayList<>();
    private java.util.Set<String> unavailableEmployees = new java.util.HashSet<>();
    private java.util.Set<String> offTimes = new java.util.HashSet<>();
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

        // Check if we are in Edit Mode
        if (getIntent().hasExtra("EDIT_BOOKING_ID")) {
            editingBookingId = getIntent().getStringExtra("EDIT_BOOKING_ID");
            isEditMode = true;
            isEmployeeEdit = getIntent().getBooleanExtra("IS_EMPLOYEE_EDIT", false);
        }

        // --- View Initialization ---

        // Navigation and Header elements
        ImageView ivBack = findViewById(R.id.iv_back_booking);
        if (isEditMode) {
            ivBack.setVisibility(View.GONE);
        }
        
        // Picker triggers
        LinearLayout llDatePicker = findViewById(R.id.ll_date_picker_controls);
        LinearLayout llTimePicker = findViewById(R.id.ll_time_picker_controls);
        
        // Display fields
        tvDate = findViewById(R.id.tv_booking_date);
        tvTime = findViewById(R.id.tv_booking_time);

        tilEditReason = findViewById(R.id.til_edit_reason);
        etEditReason = findViewById(R.id.et_edit_reason);
        if (etEditReason != null) {
            etEditReason.setRawInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            etEditReason.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    etEditReason.clearFocus();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(etEditReason.getWindowToken(), 0);
                    return true;
                }
                return false;
            });
        }

        // Stylist Selection Components
        rvStylists = findViewById(R.id.rv_stylist_list);
        pbStylists = findViewById(R.id.pb_stylist_loading);
        tvNoStylists = findViewById(R.id.tv_no_stylists);

        // Action Buttons
        MaterialButton btnChooseHairstyle = findViewById(R.id.btn_choose_hairstyle);
        MaterialButton btnConfirmBooking = findViewById(R.id.btn_confirm_booking);
        MaterialButton btnCancelEdit = findViewById(R.id.btn_cancel_edit_booking);

        if (isEditMode) {
            btnConfirmBooking.setText("Update Booking");
            if (btnCancelEdit != null) {
                btnCancelEdit.setVisibility(View.VISIBLE);
                btnCancelEdit.setOnClickListener(v -> finish());
            }
        }

        // Loading Overlay
        loadingOverlay = findViewById(R.id.loading_overlay);

        // --- Initial Setup ---
        
        // Synchronize with Firebase Server Time
        syncServerTimeAndInit();
        
        // Configure stylist selection RecyclerView
        setupStylistRecyclerView();

        if (isEditMode) {
            fetchBookingDetails();
        } else {
            // Fetch stylists normally
            fetchStylists();
        }

        if (isEmployeeEdit) {
            View mcvCustomer = findViewById(R.id.mcv_customer_name);
            if (mcvCustomer != null) mcvCustomer.setVisibility(View.VISIBLE);
        }

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

    private void fetchBookingDetails() {
        if (editingBookingId == null) return;
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);

        db.collection("bookings").document(editingBookingId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                originalDate = doc.getString("date");
                originalTime = doc.getString("time");
                originalEmployeeId = doc.getString("employeeId");
                originalCustomerId = doc.getString("customerId");
                originalHairstyleName = doc.getString("hairstyleName");
                originalHairstyleId = doc.getString("hairstyleId");
                selectedHairstyleName = originalHairstyleName;
                selectedHairstyleId = originalHairstyleId;

                if (tvDate != null) tvDate.setText(originalDate);
                if (tvTime != null) tvTime.setText(originalTime);

                // Synchronize internal hour/AM-PM state with the fetched booking time
                if (originalTime != null && originalTime.length() >= 8) {
                    try {
                        currentSelectedHour = Integer.parseInt(originalTime.substring(0, 2));
                        currentSelectedAmPm = originalTime.substring(6);
                    } catch (Exception e) {
                        android.util.Log.e("BookingActivity", "Error parsing original time: " + e.getMessage());
                    }
                }

                if (isEmployeeEdit) {
                    // Fetch customer info
                    if (originalCustomerId != null) {
                        db.collection("customers").document(originalCustomerId).get().addOnSuccessListener(userDoc -> {
                            if (userDoc.exists()) {
                                TextView tvCustName = findViewById(R.id.tv_booking_customer_name);
                                TextView tvUsername = findViewById(R.id.tv_booking_customer_username);
                                TextView tvPhone = findViewById(R.id.tv_booking_customer_phone);

                                String name = userDoc.getString("name");
                                originalCustomerName = name != null ? name : "A customer";
                                String username = userDoc.getString("username");
                                String phone = userDoc.getString("phone");

                                if (tvCustName != null) tvCustName.setText(name != null ? name : "-");
                                if (tvUsername != null) tvUsername.setText(username != null ? username : "-");
                                if (tvPhone != null) tvPhone.setText(phone != null ? formatPhoneNumber(phone) : "-");
                            }
                        });
                    }
                }

                // Now that we have the originalEmployeeId, fetch the stylists
                fetchStylists();
                
                // Prefill hairstyle preview - derive key from ID
                String derivedKey = "";
                if (selectedHairstyleId != null && selectedHairstyleId.startsWith("hs_")) {
                    derivedKey = selectedHairstyleId.substring(3);
                } else if (selectedHairstyleName != null) {
                    derivedKey = selectedHairstyleName.toLowerCase().replace(" ", "_");
                }
                updateHairstylePreview(selectedHairstyleName, "Current selection", derivedKey);
            } else {
                // If booking not found, fallback to defaults
                setDefaultDateTime();
                fetchStylists();
            }
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
        }).addOnFailureListener(e -> {
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load booking details", Toast.LENGTH_SHORT).show();
            // Fallback
            setDefaultDateTime();
            fetchStylists();
        });
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
                                
                                // Only set default date/time if we are NOT in edit mode
                                // because edit mode will fetch its own from Firestore
                                if (!isEditMode) {
                                    setDefaultDateTime();
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                                if (!isEditMode) {
                                    setDefaultDateTime();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                    if (!isEditMode) {
                        setDefaultDateTime();
                    }
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

        String bookingId = isEditMode ? editingBookingId : "FB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        // Preserve original customer data if in edit mode
        String customerId = isEditMode ? originalCustomerId : currentUser.getUid();

        String employeeId = selectedEmployee.getUid();
        String date = tvDate.getText().toString();
        String time = tvTime.getText().toString();
        String status = "Pending";

        // Final check before sending to Firestore
        // In Edit Mode, we allow the slot if it matches the original slot
        boolean isSameSlotAsOriginal = isEditMode && date.equals(originalDate) && time.equals(originalTime) && employeeId.equals(originalEmployeeId);
        
        if (bookedTimes.contains(time) && !isSameSlotAsOriginal) {
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
            showStatusDialog(false, "Slot Unavailable", "This time slot was just taken. Please choose another.", false);
            confirmButton.setEnabled(true);
            return;
        }

        java.util.Map<String, Object> bookingMap = new java.util.HashMap<>();
        bookingMap.put("bookingId", bookingId);
        bookingMap.put("customerId", customerId);
        bookingMap.put("employeeId", employeeId);
        bookingMap.put("date", date);
        bookingMap.put("time", time);
        bookingMap.put("hairstyleName", selectedHairstyleName);
        bookingMap.put("hairstyleId", selectedHairstyleId);
        bookingMap.put("status", status);
        if (!isEditMode) {
            bookingMap.put("createdAt", FieldValue.serverTimestamp());
        } else {
            bookingMap.put("updatedAt", FieldValue.serverTimestamp());
        }

        db.collection("bookings").document(bookingId).set(bookingMap, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);

                    if (isEditMode) {
                        sendEditNotification(customerId, employeeId, date, time);
                    }

                    String successTitle = isEditMode ? "Booking Updated!" : "Booking Successful!";
                    showStatusDialog(true, successTitle, "Your appointment has been scheduled. We look forward to seeing you!", true);
                })
                .addOnFailureListener(e -> {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                    confirmButton.setEnabled(true);
                    String errorMsg = getFriendlyError(e.getMessage());
                    String failTitle = isEditMode ? "Update Failed" : "Booking Failed";
                    showStatusDialog(false, failTitle, errorMsg, false);
                    android.util.Log.e("BookingActivity", "Firebase Error: " + e.getMessage());
                });
    }

    private void showStatusDialog(boolean isSuccess, String title, String message, boolean shouldExit) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_booking_status);
        dialog.setCancelable(false); // Disable clicking outside

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Handle physical back button to act as "OK"
        dialog.setOnKeyListener((dialogInterface, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                dialog.dismiss();
                if (shouldExit) finish();
                return true;
            }
            return false;
        });

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
     * Follows the 10-minute leeway rule: if minutes > 10, jumps to next hour.
     */
    private void setDefaultDateTime() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        c.setTimeInMillis(System.currentTimeMillis() + serverTimeOffset);

        // Initial formatting for today
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);
        tvDate.setText(String.format(Locale.getDefault(), "%02d/%02d/%02d", day, month + 1, year % 100));

        // Start with current hour adjusted by 10-min rule
        if (c.get(Calendar.MINUTE) > 10) {
            c.add(Calendar.HOUR_OF_DAY, 1);
        }
        int hour = c.get(Calendar.HOUR);
        if (hour == 0) hour = 12;
        currentSelectedHour = hour;
        currentSelectedAmPm = (c.get(Calendar.AM_PM) == Calendar.AM) ? "AM" : "PM";

        // If today is Friday (shop closed), default to tomorrow (Saturday) 10 AM
        if (c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
            c.add(Calendar.DAY_OF_MONTH, 1);
            tvDate.setText(String.format(Locale.getDefault(), "%02d/%02d/%02d",
                    c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR) % 100));
            currentSelectedHour = 10;
            currentSelectedAmPm = "AM";
        }

        // Validate if this slot is actually available (not booked, within hours, not in the past)
        if (isSlotDisabled(currentSelectedHour, currentSelectedAmPm)) {
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
            boolean isOff = offTimes.contains(timeStr);
            boolean isDisabled = isSlotDisabled(hour, currentSelectedAmPm);

            if (dialogHourButtons[i] != null) {
                dialogHourButtons[i].setEnabled(!isDisabled);
                
                boolean isSelected = (hour == currentSelectedHour);
                if (isSelected && isDisabled) {
                    currentIsInvalid = true;
                }
                
                if (isOff) {
                    dialogHourButtons[i].setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.warning_red_icon));
                    dialogHourButtons[i].setTextColor(Color.WHITE);
                    dialogHourButtons[i].setStrokeColorResource(R.color.warning_red_icon);
                    dialogHourButtons[i].setStrokeWidth((int) (1 * getResources().getDisplayMetrics().density));
                    dialogHourButtons[i].setAlpha(1.0f);
                } else {
                    // Reset to standard appearance first to clear red styling
                    dialogHourButtons[i].setStrokeColorResource(R.color.primary_color);
                    dialogHourButtons[i].setAlpha(isDisabled ? 0.3f : 1.0f);
                    updateTimeButtonStyle(dialogHourButtons[i], isSelected && !isDisabled);
                }
            }
        }
        
        // If the current selection was booked, passed, or is outside hours, shift to first available
        if (currentIsInvalid) {
            findFirstAvailableSlot();
            // Re-run the visual update for the new selection
            for (int i = 0; i < 12; i++) {
                int hour = i + 1;
                boolean isDisabled = isSlotDisabled(hour, currentSelectedAmPm);
                
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
        int[] hours = amPm.equals("AM") ? new int[]{10, 11} : new int[]{12, 1, 3, 4, 5, 6, 8, 9, 10, 11};
        for (int h : hours) {
            if (!isSlotDisabled(h, amPm)) {
                return false; // Found at least one valid and available slot
            }
        }
        return true; // All business hours/valid slots for this period are booked or past
    }

    private boolean isWithinBusinessHours(int hour, String amPm) {
        if (amPm.equals("AM")) {
            // Shop opens at 10 AM. 12 AM (Midnight) is closed.
            return hour == 10 || hour == 11;
        } else {
            // PM
            // 12 PM (Noon) is valid.
            // 2 PM is break time. 7 PM is now disabled.
            if (hour == 2 || hour == 7) return false;
            // All other PM hours are valid.
            return true;
        }
    }

    /**
     * Comprehensive check for a time slot's availability.
     * Considers: Firestore bookings, business hours, and current time (for today's dates).
     */
    private boolean isSlotDisabled(int hour, String amPm) {
        String timeStr = String.format(Locale.getDefault(), "%02d:00 %s", hour, amPm);
        
        // In Edit Mode, if the slot is the ORIGINAL slot of this booking, it's NOT disabled
        if (isEditMode && 
            tvDate.getText().toString().equals(originalDate) && 
            timeStr.equals(originalTime) && 
            selectedEmployee != null && 
            selectedEmployee.getUid().equals(originalEmployeeId)) {
            return false;
        }

        return bookedTimes.contains(timeStr) || offTimes.contains(timeStr) || !isWithinBusinessHours(hour, amPm) || isSlotInPast(hour, amPm);
    }

    /**
     * Checks if a specific time slot has already passed based on server time and leeway.
     */
    private boolean isSlotInPast(int hour, String amPm) {
        Calendar serverCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        serverCal.setTimeInMillis(System.currentTimeMillis() + serverTimeOffset);

        String todayDate = String.format(Locale.getDefault(), "%02d/%02d/%02d",
                serverCal.get(Calendar.DAY_OF_MONTH), serverCal.get(Calendar.MONTH) + 1, serverCal.get(Calendar.YEAR) % 100);

        if (tvDate.getText().toString().equals(todayDate)) {
            // Apply 10-minute leeway rule
            Calendar cutoffCal = (Calendar) serverCal.clone();
            if (cutoffCal.get(Calendar.MINUTE) > 10) {
                cutoffCal.add(Calendar.HOUR_OF_DAY, 1);
            }
            cutoffCal.set(Calendar.MINUTE, 0);
            cutoffCal.set(Calendar.SECOND, 0);
            cutoffCal.set(Calendar.MILLISECOND, 0);

            Calendar slotCal = (Calendar) serverCal.clone();
            slotCal.set(Calendar.HOUR, hour == 12 ? 0 : hour);
            slotCal.set(Calendar.AM_PM, amPm.equals("AM") ? Calendar.AM : Calendar.PM);
            slotCal.set(Calendar.MINUTE, 0);
            slotCal.set(Calendar.SECOND, 0);
            slotCal.set(Calendar.MILLISECOND, 0);

            return slotCal.before(cutoffCal);
        }
        return false;
    }

    private void findFirstAvailableSlot() {
        // Targeted search for business hours only to avoid scanning invalid slots
        int[] amHours = {10, 11};
        int[] pmHours = {12, 1, 3, 4, 5, 6, 8, 9, 10, 11};

        if (currentSelectedAmPm.equals("AM")) {
            for (int h : amHours) {
                if (!isSlotDisabled(h, "AM")) {
                    currentSelectedHour = h;
                    return;
                }
            }
            // If AM is full, try PM
            for (int h : pmHours) {
                if (!isSlotDisabled(h, "PM")) {
                    currentSelectedHour = h;
                    currentSelectedAmPm = "PM";
                    return;
                }
            }
        } else {
            for (int h : pmHours) {
                if (!isSlotDisabled(h, "PM")) {
                    currentSelectedHour = h;
                    return;
                }
            }
            // If PM is full, try AM
            for (int h : amHours) {
                if (!isSlotDisabled(h, "AM")) {
                    currentSelectedHour = h;
                    currentSelectedAmPm = "AM";
                    return;
                }
            }
        }

        // If no slots available today, jump to next day
        findNextAvailableDate();
    }

    /**
     * Formats a raw phone string into the standardized Malaysian format.
     * Logic synchronized with MainActivityEmployee: "+60 XX-XXXX XXXX..."
     */
    private String formatPhoneNumber(String rawPhone) {
        if (rawPhone == null || rawPhone.isEmpty()) return "-";
        
        String digits = rawPhone;
        if (rawPhone.startsWith("0")) {
            digits = rawPhone.substring(1);
        } else if (rawPhone.startsWith("+60")) {
            digits = rawPhone.substring(3);
        } else if (rawPhone.startsWith("60")) {
            digits = rawPhone.substring(2);
        }

        if (digits.length() >= 6) {
            StringBuilder sb = new StringBuilder("+60 ");
            sb.append(digits.substring(0, 2));
            sb.append("-");
            sb.append(digits.substring(2, 6));

            String remaining = digits.substring(6);
            for (int i = 0; i < remaining.length(); i++) {
                if (i > 0 && i % 4 == 0) sb.append(" ");
                if (i == 0) sb.append(" ");
                sb.append(remaining.charAt(i));
            }
            return sb.toString();
        } else {
            return "+60 " + digits;
        }
    }

    private void fetchBookedTimes() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet. Availability might be outdated.", Toast.LENGTH_SHORT).show();
            return;
        }

        String date = tvDate.getText().toString();
        fetchUnavailableStylists(date);

        if (selectedEmployee == null) return;
        
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
                        String currentUid = mAuth.getUid();
                        boolean conflictFromOtherUser = false;
                        String selectedTime = String.format(Locale.getDefault(), "%02d:00 %s", currentSelectedHour, currentSelectedAmPm);

                        for (QueryDocumentSnapshot doc : value) {
                            String bid = doc.getString("bookingId");
                            String status = doc.getString("status");
                            String time = doc.getString("time");
                            String cid = doc.getString("customerId");

                            // Skip the current booking being edited to allow re-selection of the same slot
                            if (isEditMode && editingBookingId != null && editingBookingId.equals(bid)) {
                                continue;
                            }

                            // Only count non-cancelled bookings
                            if (time != null && !"Cancelled".equalsIgnoreCase(status)) {
                                bookedTimes.add(time);
                                
                                // Check if someone ELSE took our currently selected slot
                                if (time.equals(selectedTime) && currentUid != null && !currentUid.equals(cid)) {
                                    conflictFromOtherUser = true;
                                }
                            }
                        }
                        
                        // Re-validate current selection
                        // We ONLY auto-switch if it's taken by someone else, or now in the past, or out of business hours.
                        // However, if it's the ORIGINAL slot of the booking being edited, we allow it.
                        boolean isOriginalSlot = isEditMode && date.equals(originalDate) &&
                                               selectedTime.equals(originalTime) &&
                                               selectedEmployee != null && selectedEmployee.getUid().equals(originalEmployeeId);

                        boolean isInvalid = (conflictFromOtherUser ||
                                           !isWithinBusinessHours(currentSelectedHour, currentSelectedAmPm) ||
                                           isSlotInPast(currentSelectedHour, currentSelectedAmPm)) && !isOriginalSlot;

                        if (isInvalid) {
                            findFirstAvailableSlot();
                            tvTime.setText(String.format(Locale.getDefault(), "%02d:00 %s", currentSelectedHour, currentSelectedAmPm));
                            
                            if (conflictFromOtherUser) {
                                if (!isOriginalSlot) {
                                    Toast.makeText(BookingActivity.this, "The selected slot was just taken. Switched to next available.", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                if (!isOriginalSlot) {
                                    Toast.makeText(BookingActivity.this, "Slot no longer available. Switched to next available.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }

                        // Refresh UI dynamically if dialog is open
                        if (timePickerDialog != null && timePickerDialog.isShowing()) {
                            runOnUiThread(BookingActivity.this::updateDialogHourButtons);
                        }
                    }
                });

        fetchFullDates();
    }

    private ListenerRegistration unavailableStylistsListener;

    private void fetchUnavailableStylists(String date) {
        if (unavailableStylistsListener != null) {
            unavailableStylistsListener.remove();
        }
        unavailableStylistsListener = db.collection("off_day_requests")
                .whereEqualTo("status", "APPROVED")
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    
                    java.util.Set<String> newlyUnavailable = new java.util.HashSet<>();
                    java.util.Set<String> newlyOffTimes = new java.util.HashSet<>();
                    
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : value) {
                        String reqDateStr = doc.getString("offDateRequest");
                        String empId = doc.getString("employeeId");
                        
                        if (reqDateStr == null || empId == null) continue;
                        
                        boolean dateMatches = false;
                        if (reqDateStr.contains(" to ")) {
                            String[] parts = reqDateStr.split(" to ");
                            if (isDateInRange(date, parts[0], parts[1])) dateMatches = true;
                        } else if (reqDateStr.equals(date)) {
                            dateMatches = true;
                        }
                        
                        if (dateMatches) {
                            boolean isWholeDay = Boolean.TRUE.equals(doc.getBoolean("wholeDay"));
                            if (isWholeDay) {
                                newlyUnavailable.add(empId);
                                if (selectedEmployee != null && empId.equals(selectedEmployee.getUid())) {
                                    // If current selected is off the whole day, add all slots (not perfectly efficient but clear)
                                    // Actually we handle whole day check separately in UI if possible
                                }
                            } else {
                                // Partial off day
                                if (selectedEmployee != null && empId.equals(selectedEmployee.getUid())) {
                                    String start = doc.getString("startTime");
                                    String end = doc.getString("endTime");
                                    // Check each business hour slot
                                    addOffTimesInRange(newlyOffTimes, start, end);
                                }
                            }
                        }
                    }
                    
                    unavailableEmployees = newlyUnavailable;
                    offTimes = newlyOffTimes;
                    
                    if (stylistAdapter != null) {
                        stylistAdapter.setUnavailableStylists(unavailableEmployees);
                    }
                    
                    // If selected stylist just became unavailable for the whole day
                    if (selectedEmployee != null && unavailableEmployees.contains(selectedEmployee.getUid())) {
                        selectedEmployee = null;
                        Toast.makeText(BookingActivity.this, "Your selected stylist is now unavailable for today.", Toast.LENGTH_LONG).show();
                    }
                    
                    // Re-run revalidation if current time is now "OFF"
                    String currentTime = tvTime.getText().toString();
                    if (offTimes.contains(currentTime)) {
                         findFirstAvailableSlot();
                         tvTime.setText(String.format(Locale.getDefault(), "%02d:00 %s", currentSelectedHour, currentSelectedAmPm));
                         Toast.makeText(BookingActivity.this, "The selected slot is now an off-period for this stylist.", Toast.LENGTH_SHORT).show();
                    }

                    if (timePickerDialog != null && timePickerDialog.isShowing()) {
                        updateDialogHourButtons();
                    }
                });
    }

    private void addOffTimesInRange(java.util.Set<String> set, String start, String end) {
        // Business slots: 10AM, 11AM, 12PM, 1PM, 3PM, 4PM, 5PM, 6PM, 8PM, 9PM, 10PM, 11PM
        String[] slots = {
            "10:00 AM", "11:00 AM", "12:00 PM", "01:00 PM", "03:00 PM", "04:00 PM",
            "05:00 PM", "06:00 PM", "08:00 PM", "09:00 PM", "10:00 PM", "11:00 PM"
        };
        for (String slot : slots) {
            if (isTimeOverlap(slot, start, end)) {
                set.add(slot);
            }
        }
    }

    private boolean isDateInRange(String target, String start, String end) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            java.util.Date dTarget = sdf.parse(target);
            java.util.Date dStart = sdf.parse(start);
            java.util.Date dEnd = sdf.parse(end);
            return dTarget != null && dStart != null && dEnd != null && 
                   !dTarget.before(dStart) && !dTarget.after(dEnd);
        } catch (Exception e) { return false; }
    }

    private boolean isTimeOverlap(String bookingTime, String start, String end) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("hh:mm a", Locale.getDefault());
            java.util.Date dBooking = sdf.parse(bookingTime);
            java.util.Date dStart = sdf.parse(start);
            java.util.Date dEnd = sdf.parse(end);
            
            return dBooking != null && dStart != null && dEnd != null && 
                   !dBooking.before(dStart) && dBooking.before(dEnd);
        } catch (Exception e) { return false; }
    }

    private void fetchFullDates() {
        if (selectedEmployee == null) return;

        if (fullDatesListener != null) {
            fullDatesListener.remove();
        }

        // Total possible slots based on business hours:
        // AM: 10, 11 (2)
        // PM: 12, 1, 3, 4, 5, 6, 8, 9, 10, 11 (10)
        // Total = 12
        final int TOTAL_DAILY_SLOTS = 12;

        fullDatesListener = db.collection("bookings")
                .whereEqualTo("employeeId", selectedEmployee.getUid())
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value != null) {
                        java.util.Map<String, Integer> dateCounts = new java.util.HashMap<>();
                        for (QueryDocumentSnapshot doc : value) {
                            String bid = doc.getString("bookingId");
                            String status = doc.getString("status");
                            String d = doc.getString("date");

                            // Skip the current booking being edited
                            if (isEditMode && editingBookingId != null && editingBookingId.equals(bid)) {
                                continue;
                            }

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
                
                // Only toast if we actually changed to a date that isn't the original one
                boolean isOriginalDate = isEditMode && dateStr.equals(originalDate);
                if (!isOriginalDate) {
                    Toast.makeText(this, "The selected date is full. Switched to " + dateStr, Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bookedTimesListener != null) bookedTimesListener.remove();
        if (fullDatesListener != null) fullDatesListener.remove();
        if (employeesListener != null) employeesListener.remove();
        if (innerEmployeesListener != null) innerEmployeesListener.remove();
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
            updateReasonFieldVisibility();
        });
        rvStylists.setAdapter(stylistAdapter);
    }

    private void updateReasonFieldVisibility() {
        if (tilEditReason != null) {
            if (isEmployeeEdit && selectedEmployee != null &&
                    originalEmployeeId != null && !originalEmployeeId.isEmpty() &&
                    !selectedEmployee.getUid().equals(originalEmployeeId)) {
                tilEditReason.setVisibility(View.VISIBLE);
            } else {
                tilEditReason.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Fetches the list of active employees from Firestore.
     */
    private void fetchStylists() {
        if (!isNetworkAvailable()) {
            tvNoStylists.setVisibility(View.VISIBLE);
            tvNoStylists.setText("No internet connection.");
            return;
        }

        pbStylists.setVisibility(View.VISIBLE);
        
        if (employeesListener != null) employeesListener.remove();
        
        // Stage 1: Fetch users who are active employees
        employeesListener = db.collection("users")
                .whereEqualTo("role", "employee")
                .addSnapshotListener((userSnapshot, userError) -> {
                    if (userError != null) {
                        pbStylists.setVisibility(View.GONE);
                        Toast.makeText(this, "Failed to fetch users.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (userSnapshot != null) {
                        List<String> activeEmployeeIds = new ArrayList<>();
                        for (QueryDocumentSnapshot userDoc : userSnapshot) {
                            activeEmployeeIds.add(userDoc.getId());
                        }

                        if (activeEmployeeIds.isEmpty()) {
                            pbStylists.setVisibility(View.GONE);
                            stylistList.clear();
                            stylistAdapter.notifyDataSetChanged();
                            tvNoStylists.setVisibility(View.VISIBLE);
                            return;
                        }

                        // Stage 2: Fetch employee details for these active IDs
                        if (innerEmployeesListener != null) innerEmployeesListener.remove();
                        innerEmployeesListener = db.collection("employees").addSnapshotListener((value, error) -> {
                            pbStylists.setVisibility(View.GONE);
                            if (error != null) {
                                Toast.makeText(this, "Failed to fetch stylists.", Toast.LENGTH_SHORT).show();
                                tvNoStylists.setVisibility(View.VISIBLE);
                                tvNoStylists.setText("Error loading stylists.");
                                return;
                            }
                            
                            if (value != null) {
                                stylistList.clear();
                                int targetPosition = -1;
                                for (QueryDocumentSnapshot document : value) {
                                    if (activeEmployeeIds.contains(document.getId())) {
                                        Employee employee = document.toObject(Employee.class);
                                        stylistList.add(employee);

                                        // If in edit mode and this is the original employee, select them
                                        if (isEditMode && employee.getUid() != null && employee.getUid().equals(originalEmployeeId)) {
                                            selectedEmployee = employee;
                                            targetPosition = stylistList.size() - 1;
                                        }
                                    }
                                }
                                stylistAdapter.notifyDataSetChanged();

                                // Correctly set the selection in the adapter
                                if (targetPosition != -1) {
                                    stylistAdapter.setSelectedIndex(targetPosition);
                                }

                                // If we selected an employee (pre-selection in edit mode), fetch their booked times
                                if (selectedEmployee != null) {
                                    fetchBookedTimes();
                                }

                                if (stylistList.isEmpty()) {
                                    tvNoStylists.setVisibility(View.VISIBLE);
                                } else {
                                    tvNoStylists.setVisibility(View.GONE);
                                }
                            }
                        });
                    }
                });
    }

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
                            // Small padding (same as stroke width) so the image sits INSIDE the border
                            int strokePadding = (int) (1.0 * getResources().getDisplayMetrics().density);
                            ivPreview.setPadding(strokePadding, strokePadding, strokePadding, strokePadding);

                            // Load image using Glide with rounding
                            Glide.with(this)
                                .load("file:///android_asset/images/" + imageName)
                                .transform(new CenterCrop(), new RoundedCorners((int) (12 * getResources().getDisplayMetrics().density)))
                                .into(ivPreview);
                            return;
                        }
                    }
                }
                
                // Fallback if no specific image found
                ivPreview.setPadding(32, 32, 32, 32); // Center the generic icon
                ivPreview.setImageResource(R.drawable.ic_hair);
            } catch (java.io.IOException e) {
                ivPreview.setPadding(32, 32, 32, 32);
                ivPreview.setImageResource(R.drawable.ic_hair);
            }
        }
    }

    private void sendEditNotification(String customerId, String employeeId, String date, String time) {
        if (customerId == null || employeeId == null) return;

        String reason = (etEditReason != null && etEditReason.getVisibility() == View.VISIBLE) 
                ? etEditReason.getText().toString().trim() : "";

        if (isEmployeeEdit) {
            String employeeName = selectedEmployee != null ? selectedEmployee.getFullname() : "";
            String messagePrefix = employeeName.isEmpty() ? "your hairstylist" : "your hairstylist, " + employeeName;
            
            java.util.Map<String, Object> notification = new java.util.HashMap<>();
            notification.put("receiverId", customerId);
            notification.put("title", "Booking Updated");
            notification.put("message", messagePrefix + " has updated your appointment for " + date + " at " + time + ". If this is not an agreeable decision, please call your hairstylist.");
            notification.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
            notification.put("type", "UPDATE");
            notification.put("bookingId", editingBookingId);
            notification.put("isRead", false);
            notification.put("isSeen", false);
            notification.put("reason", reason);
            
            // Sender Info (for callback)
            notification.put("senderId", mAuth.getUid()); // The employee who performed the update
            
            // Before/After Data
            notification.put("oldDate", originalDate);
            notification.put("oldTime", originalTime);
            notification.put("oldEmployeeId", originalEmployeeId);
            notification.put("oldHairstyleName", originalHairstyleName);
            notification.put("oldHairstyleId", originalHairstyleId);
            
            notification.put("newDate", date);
            notification.put("newTime", time);
            notification.put("newEmployeeId", employeeId);
            notification.put("newHairstyleName", selectedHairstyleName);
            notification.put("newHairstyleId", selectedHairstyleId);

            db.collection("notifications").add(notification);

            // Also notify the new employee if they were changed
            if (!employeeId.equals(originalEmployeeId)) {
                java.util.Map<String, Object> empNotification = new java.util.HashMap<>(notification);
                empNotification.put("receiverId", employeeId);
                empNotification.put("message", "You have been assigned to a new booking (re-assigned from another hairstylist) for " + date + " at " + time + ".");
                db.collection("notifications").add(empNotification);
            }
        } else {
            db.collection("customers").document(customerId).get().addOnSuccessListener(doc -> {
                String customerName = doc.exists() ? doc.getString("name") : "A customer";
                
                java.util.Map<String, Object> notification = new java.util.HashMap<>();
                notification.put("receiverId", employeeId);
                notification.put("title", "Booking Updated");
                notification.put("message", customerName + " has updated their appointment for " + date + " at " + time + ".");
                notification.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
                notification.put("type", "UPDATE");
                notification.put("bookingId", editingBookingId);
                notification.put("isRead", false);
                notification.put("isSeen", false);
                
                // Sender Info (for callback)
                notification.put("senderId", customerId); // The customer who updated it
                
                // Before/After Data
                notification.put("oldDate", originalDate);
                notification.put("oldTime", originalTime);
                notification.put("oldEmployeeId", originalEmployeeId);
                notification.put("oldHairstyleName", originalHairstyleName);
                notification.put("oldHairstyleId", originalHairstyleId);

                notification.put("newDate", date);
                notification.put("newTime", time);
                notification.put("newEmployeeId", employeeId);
                notification.put("newHairstyleName", selectedHairstyleName);
                notification.put("newHairstyleId", selectedHairstyleId);

                db.collection("notifications").add(notification);
            });
        }
    }
}
