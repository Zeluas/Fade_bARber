package com.zejyv.azizul.uitm.fadebarber;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * BookingActivity handles the appointment booking process.
 * It allows users to pick a date, select a time slot, choose a stylist,
 * and navigate to the AR Try-On experience.
 */
public class BookingActivity extends AppCompatActivity {

    // --- UI Components ---
    private TextView tvDate, tvTime;
    private LinearLayout llStylist1, llStylist2, llStylist3;
    private CheckBox cbStylist1, cbStylist2, cbStylist3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking);

        // --- View Initialization ---
        
        // Navigation and Header elements
        ImageView ivBack = findViewById(R.id.iv_back_booking);
        
        // Picker triggers
        LinearLayout llDatePicker = findViewById(R.id.ll_date_picker_controls);
        LinearLayout llTimePicker = findViewById(R.id.ll_time_picker_controls);
        
        // Display fields
        tvDate = findViewById(R.id.tv_booking_date);
        tvTime = findViewById(R.id.tv_booking_time);

        // Stylist Selection containers
        llStylist1 = findViewById(R.id.ll_stylist_item_1);
        llStylist2 = findViewById(R.id.ll_stylist_item_2);
        llStylist3 = findViewById(R.id.ll_stylist_item_3);
        
        // Stylist Selection indicators
        cbStylist1 = findViewById(R.id.cb_stylist_1);
        cbStylist2 = findViewById(R.id.cb_stylist_2);
        cbStylist3 = findViewById(R.id.cb_stylist_3);

        // Action Buttons
        MaterialButton btnChooseHairstyle = findViewById(R.id.btn_choose_hairstyle);
        MaterialButton btnConfirmBooking = findViewById(R.id.btn_confirm_booking);

        // --- Initial Setup ---
        
        // Set default date and time (localized logic)
        setDefaultDateTime();
        
        // Configure mutual exclusivity for stylist selection
        setupStylistSelection();

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
            Intent intent = new Intent(BookingActivity.this, TryOnActivity.class);
            intent.putExtra("FROM_BOOKING", true);
            startActivity(intent);
        });

        // Final Booking Confirmation
        btnConfirmBooking.setOnClickListener(v -> 
            Toast.makeText(this, getString(R.string.booking_success_toast), Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Initializes the date and time displays with default values.
     * Logic: If today is Friday, defaults to Saturday.
     */
    private void setDefaultDateTime() {
        Calendar c = Calendar.getInstance();
        // If today is Friday (shop closed), default to tomorrow (Saturday)
        if (c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);
        
        // Format: DD/MM/YY
        String date = String.format(Locale.getDefault(), "%02d/%02d/%02d", day, month + 1, year % 100);
        tvDate.setText(date);
        tvTime.setText("10:00 AM");
    }

    /**
     * Displays a MaterialDatePicker with constraints to disable past dates and Fridays.
     */
    private void showDatePicker() {
        CalendarConstraints.Builder constraintsBuilder = new CalendarConstraints.Builder();

        // Combine validators: disable past dates and disable Fridays
        List<CalendarConstraints.DateValidator> validators = new ArrayList<>();
        validators.add(DateValidatorPointForward.now());
        validators.add(new DisableFridaysValidator());

        constraintsBuilder.setValidator(CompositeDateValidator.allOf(validators));

        // Default selection: today, or tomorrow if today is Friday
        long selection = MaterialDatePicker.todayInUtcMilliseconds();
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        if (c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
            c.add(Calendar.DAY_OF_MONTH, 1);
            selection = c.getTimeInMillis();
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
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
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
     * Displays a custom dialog for selecting hour-based time slots.
     */
    private void showCustomTimePicker() {
        LayoutInflater inflater = getLayoutInflater();
        // Inflate the custom time picker view
        View dialogView = inflater.inflate(R.layout.dialog_time_picker, null);

        // Use a standard Dialog to avoid the default framing of AlertDialog
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);

        if (dialog.getWindow() != null) {
            // Set window background to transparent to allow for rounded card corners
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().getDecorView().setPadding(0, 0, 0, 0);
        }

        final MaterialButton[] hourButtons = new MaterialButton[12];
        final int[] selectedHour = {10}; // Default selection: 10
        final String[] selectedAmPm = {"AM"}; // Default selection: AM

        // Bind and configure the 12 hour buttons (1 to 12)
        for (int i = 0; i < 12; i++) {
            int hour = i + 1;
            int resId = getResources().getIdentifier("btn_hour_" + hour, "id", getPackageName());
            hourButtons[i] = dialogView.findViewById(resId);
            if (hourButtons[i] != null) {
                hourButtons[i].setOnClickListener(v -> {
                    selectedHour[0] = hour;
                    // Update visual state for all hour buttons
                    for (int j = 0; j < 12; j++) {
                        if (hourButtons[j] != null) {
                            updateTimeButtonStyle(hourButtons[j], (j + 1) == selectedHour[0]);
                        }
                    }
                });
            }
        }

        // Bind and configure AM/PM toggle buttons
        MaterialButton btnAm = dialogView.findViewById(R.id.btn_am);
        MaterialButton btnPm = dialogView.findViewById(R.id.btn_pm);

        View.OnClickListener amPmClick = v -> {
            selectedAmPm[0] = (v.getId() == R.id.btn_am) ? "AM" : "PM";
            updateTimeButtonStyle(btnAm, selectedAmPm[0].equals("AM"));
            updateTimeButtonStyle(btnPm, selectedAmPm[0].equals("PM"));
        };

        btnAm.setOnClickListener(amPmClick);
        btnPm.setOnClickListener(amPmClick);

        // Set initial visual selection states
        for (int i = 0; i < 12; i++) {
            if (hourButtons[i] != null) {
                updateTimeButtonStyle(hourButtons[i], (i + 1) == selectedHour[0]);
            }
        }
        updateTimeButtonStyle(btnAm, true);
        updateTimeButtonStyle(btnPm, false);

        // Dialog action buttons
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_ok).setOnClickListener(v -> {
            // Apply selected time to main UI
            tvTime.setText(String.format(Locale.getDefault(), "%02d:00 %s", selectedHour[0], selectedAmPm[0]));
            dialog.dismiss();
        });

        dialog.show();
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
     * Configures the mutual exclusivity logic for stylist selection (Radio-button behavior).
     */
    private void setupStylistSelection() {
        // Initial state: all unmarked
        cbStylist1.setChecked(false);
        cbStylist2.setChecked(false);
        cbStylist3.setChecked(false);

        // Container-level click listeners
        llStylist1.setOnClickListener(v -> selectStylist(1));
        llStylist2.setOnClickListener(v -> selectStylist(2));
        llStylist3.setOnClickListener(v -> selectStylist(3));
        
        // Direct checkbox click listeners
        cbStylist1.setOnClickListener(v -> selectStylist(1));
        cbStylist2.setOnClickListener(v -> selectStylist(2));
        cbStylist3.setOnClickListener(v -> selectStylist(3));
    }

    /**
     * Updates the checkboxes to ensure only one stylist is selected at a time.
     */
    private void selectStylist(int stylistIndex) {
        cbStylist1.setChecked(stylistIndex == 1);
        cbStylist2.setChecked(stylistIndex == 2);
        cbStylist3.setChecked(stylistIndex == 3);
    }
}
