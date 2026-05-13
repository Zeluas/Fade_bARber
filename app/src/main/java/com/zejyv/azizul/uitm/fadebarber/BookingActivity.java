package com.zejyv.azizul.uitm.fadebarber;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.util.Calendar;
import java.util.Locale;

public class BookingActivity extends AppCompatActivity {

    private TextView tvDate, tvTime;
    
    private LinearLayout llStylist1, llStylist2, llStylist3;
    private CheckBox cbStylist1, cbStylist2, cbStylist3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking);

        // Initialize views
        ImageView ivBack = findViewById(R.id.iv_back_booking);
        LinearLayout llDatePicker = findViewById(R.id.ll_date_picker_controls);
        LinearLayout llTimePicker = findViewById(R.id.ll_time_picker_controls);
        tvDate = findViewById(R.id.tv_booking_date);
        tvTime = findViewById(R.id.tv_booking_time);

        llStylist1 = findViewById(R.id.ll_stylist_item_1);
        llStylist2 = findViewById(R.id.ll_stylist_item_2);
        llStylist3 = findViewById(R.id.ll_stylist_item_3);
        
        cbStylist1 = findViewById(R.id.cb_stylist_1);
        cbStylist2 = findViewById(R.id.cb_stylist_2);
        cbStylist3 = findViewById(R.id.cb_stylist_3);

        MaterialButton btnChooseHairstyle = findViewById(R.id.btn_choose_hairstyle);
        MaterialButton btnConfirmBooking = findViewById(R.id.btn_confirm_booking);

        // Back button
        ivBack.setOnClickListener(v -> finish());

        // Date Picker
        View.OnClickListener dateClick = v -> showDatePicker();
        llDatePicker.setOnClickListener(dateClick);
        tvDate.setOnClickListener(dateClick);
        findViewById(R.id.iv_calendar_button).setOnClickListener(dateClick);

        // Time Picker
        View.OnClickListener timeClick = v -> showCustomTimePicker();
        llTimePicker.setOnClickListener(timeClick);
        tvTime.setOnClickListener(timeClick);
        findViewById(R.id.iv_clock_button).setOnClickListener(timeClick);

        // Stylist Selection (Radio button logic)
        setupStylistSelection();

        // Choose Hairstyle
        btnChooseHairstyle.setOnClickListener(v -> {
            Intent intent = new Intent(BookingActivity.this, TryOnActivity.class);
            intent.putExtra("FROM_BOOKING", true);
            startActivity(intent);
        });

        // Confirm Booking
        btnConfirmBooking.setOnClickListener(v -> Toast.makeText(this, "Booking Confirmed!", Toast.LENGTH_SHORT).show());
    }

    private void showDatePicker() {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year1, monthOfYear, dayOfMonth) -> {
                    String date = String.format(Locale.getDefault(), "%02d/%02d/%02d", dayOfMonth, monthOfYear + 1, year1 % 100);
                    tvDate.setText(date);
                }, year, month, day);
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePickerDialog.show();
    }

    private void showCustomTimePicker() {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_time_picker, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        final MaterialButton[] hourButtons = new MaterialButton[12];
        final int[] selectedHour = {10}; // Default 10
        final String[] selectedAmPm = {"AM"}; // Default AM

        for (int i = 0; i < 12; i++) {
            int hour = i + 1;
            int resId = getResources().getIdentifier("btn_hour_" + hour, "id", getPackageName());
            hourButtons[i] = dialogView.findViewById(resId);
            hourButtons[i].setOnClickListener(v -> {
                selectedHour[0] = hour;
                for (int j = 0; j < 12; j++) {
                    updateButtonStyle(hourButtons[j], (j + 1) == selectedHour[0]);
                }
            });
        }

        MaterialButton btnAm = dialogView.findViewById(R.id.btn_am);
        MaterialButton btnPm = dialogView.findViewById(R.id.btn_pm);

        View.OnClickListener amPmClick = v -> {
            selectedAmPm[0] = (v.getId() == R.id.btn_am) ? "AM" : "PM";
            updateButtonStyle(btnAm, selectedAmPm[0].equals("AM"));
            updateButtonStyle(btnPm, selectedAmPm[0].equals("PM"));
        };

        btnAm.setOnClickListener(amPmClick);
        btnPm.setOnClickListener(amPmClick);

        // Initial Selection
        for (int i = 0; i < 12; i++) {
            updateButtonStyle(hourButtons[i], (i + 1) == selectedHour[0]);
        }
        updateButtonStyle(btnAm, true);
        updateButtonStyle(btnPm, false);

        final AlertDialog dialog = builder.create();

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_ok).setOnClickListener(v -> {
            tvTime.setText(String.format(Locale.getDefault(), "%d %s", selectedHour[0], selectedAmPm[0]));
            dialog.dismiss();
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private void updateButtonStyle(MaterialButton button, boolean isSelected) {
        if (isSelected) {
            button.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary_color));
            button.setTextColor(ContextCompat.getColor(this, R.color.white));
            button.setStrokeWidth(0);
        } else {
            button.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.white));
            button.setTextColor(ContextCompat.getColor(this, R.color.primary_color));
            button.setStrokeWidth((int) (1 * getResources().getDisplayMetrics().density));
        }
    }

    private void setupStylistSelection() {
        // Initial state: all unmarked
        cbStylist1.setChecked(false);
        cbStylist2.setChecked(false);
        cbStylist3.setChecked(false);

        llStylist1.setOnClickListener(v -> selectStylist(1));
        llStylist2.setOnClickListener(v -> selectStylist(2));
        llStylist3.setOnClickListener(v -> selectStylist(3));
        
        // Also allow clicking the checkbox directly
        cbStylist1.setOnClickListener(v -> selectStylist(1));
        cbStylist2.setOnClickListener(v -> selectStylist(2));
        cbStylist3.setOnClickListener(v -> selectStylist(3));
    }

    private void selectStylist(int stylistIndex) {
        cbStylist1.setChecked(stylistIndex == 1);
        cbStylist2.setChecked(stylistIndex == 2);
        cbStylist3.setChecked(stylistIndex == 3);
    }
}