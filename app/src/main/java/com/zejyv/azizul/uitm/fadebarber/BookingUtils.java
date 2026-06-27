package com.zejyv.azizul.uitm.fadebarber;

import android.util.Log;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Utility class for booking-related operations.
 */
public class BookingUtils {
    
    /**
     * Scans the 'bookings' collection for 'Pending' bookings that are more than 50 minutes past 
     * their scheduled time and automatically cancels them.
     */
    public static void performBookingCleanup() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        // Use the same format and timezone as BookingActivity
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy hh:mm a", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        long fiftyMinutesInMillis = 50 * 60 * 1000;

        db.collection("bookings")
            .whereEqualTo("status", "Pending")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                long currentTime = System.currentTimeMillis();

                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    String dateStr = doc.getString("date");
                    String timeStr = doc.getString("time");

                    if (dateStr == null || timeStr == null) continue;

                    try {
                        Date bookingDate = sdf.parse(dateStr + " " + timeStr);
                        if (bookingDate != null) {
                            // If current time is past (booking time + 50 minutes)
                            if (currentTime > (bookingDate.getTime() + fiftyMinutesInMillis)) {
                                doc.getReference().update("status", "Cancelled")
                                    .addOnSuccessListener(aVoid -> Log.d("BookingCleanup", "Auto-cancelled expired booking: " + doc.getId()))
                                    .addOnFailureListener(e -> Log.e("BookingCleanup", "Failed to auto-cancel booking: " + doc.getId(), e));
                            }
                        }
                    } catch (ParseException e) {
                        Log.e("BookingCleanup", "Error parsing date/time for booking: " + doc.getId(), e);
                    }
                }
            })
            .addOnFailureListener(e -> Log.e("BookingCleanup", "Error fetching bookings for cleanup", e));
    }
}
