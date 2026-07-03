package com.zejyv.azizul.uitm.fadebarber;

import android.util.Log;
import com.google.firebase.firestore.FieldValue;
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
     * Scans the 'bookings' collection for 'Pending' bookings and handles 
     * lock notifications and auto-cancellations using Network Time for security.
     */
    public static void performBookingCleanup() {
        NetworkTimeManager.getInstance().syncTime(new NetworkTimeManager.OnTimeSyncedListener() {
            @Override
            public void onSyncSuccess(long networkTime) {
                processCleanup(networkTime);
            }

            @Override
            public void onSyncFailed() {
                Log.e("BookingCleanup", "Failed to fetch network time, skipping cleanup for security");
            }
        });
    }

    private static void processCleanup(long currentTime) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy hh:mm a", Locale.getDefault());
        TimeZone klTimeZone = TimeZone.getTimeZone("Asia/Kuala_Lumpur");
        sdf.setTimeZone(klTimeZone);
        
        SimpleDateFormat dateSdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
        dateSdf.setTimeZone(klTimeZone);
        String todayStr = dateSdf.format(new Date(currentTime));

        long fiftyMinutesInMillis = 50 * 60 * 1000;
        long oneHourInMillis = 60 * 60 * 1000;

        // Fetch ALL pending bookings (unfiltered by date) to catch past "ghost" bookings
        db.collection("bookings")
            .whereEqualTo("status", "Pending")
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                    String dateStr = doc.getString("date");
                    String timeStr = doc.getString("time");
                    String customerId = doc.getString("customerId");
                    String employeeId = doc.getString("employeeId");

                    if (dateStr == null || timeStr == null || customerId == null) continue;

                    try {
                        Date bookingDate = sdf.parse(dateStr + " " + timeStr);
                        if (bookingDate != null) {
                            long bookingTime = bookingDate.getTime();
                            
                            // 1. Handle 1-hour lock notification reminder (ONLY for TODAY)
                            if (dateStr.equals(todayStr)) {
                                boolean lockNotifSent = doc.contains("lockNotificationSent") && Boolean.TRUE.equals(doc.getBoolean("lockNotificationSent"));
                                if (!lockNotifSent && currentTime > (bookingTime - oneHourInMillis) && currentTime < (bookingTime + fiftyMinutesInMillis)) {
                                    doc.getReference().update("lockNotificationSent", true);

                                    java.util.Map<String, Object> notif = new java.util.HashMap<>();
                                    notif.put("receiverId", customerId);
                                    notif.put("senderId", employeeId != null ? employeeId : "SYSTEM");
                                    notif.put("title", "Cancellation Locked");
                                    notif.put("message", "Cancellation is locked 1 hour before your appointment. If urgent, please call your hairstylist directly.");
                                    notif.put("timestamp", FieldValue.serverTimestamp());
                                    notif.put("type", "CANCELLATION_LOCK");
                                    notif.put("bookingId", doc.getId());
                                    notif.put("isRead", false); 
                                    notif.put("isSeen", false); 

                                    db.collection("notifications").add(notif);
                                }
                            }

                            // 2. Handle auto-cancellation for no-show (50 minutes past OR any past date)
                            if (currentTime > (bookingTime + fiftyMinutesInMillis)) {
                                doc.getReference().update(
                                        "status", "Cancelled",
                                        "updatedAt", FieldValue.serverTimestamp()
                                    )
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d("BookingCleanup", "Auto-cancelled expired booking: " + doc.getId());
                                        sendAutoCancelNotifications(db, doc.getId(), customerId, employeeId, dateStr, timeStr);
                                    })
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

    private static void sendAutoCancelNotifications(FirebaseFirestore db, String bookingId, String customerId, String employeeId, String date, String time) {
        String message = "Booking on " + date + " at " + time + " has been auto-cancelled due to no-show.";

        // Notification to Customer
        java.util.Map<String, Object> customerNotif = new java.util.HashMap<>();
        customerNotif.put("receiverId", customerId);
        customerNotif.put("senderId", employeeId != null ? employeeId : "SYSTEM");
        customerNotif.put("title", "Booking Auto-Cancelled");
        customerNotif.put("message", message);
        customerNotif.put("timestamp", FieldValue.serverTimestamp());
        customerNotif.put("type", "AUTO_CANCELLATION");
        customerNotif.put("bookingId", bookingId);
        customerNotif.put("isRead", false);
        customerNotif.put("isSeen", false);
        db.collection("notifications").add(customerNotif);

        // Notification to Employee
        if (employeeId != null) {
            java.util.Map<String, Object> employeeNotif = new java.util.HashMap<>();
            employeeNotif.put("receiverId", employeeId);
            employeeNotif.put("senderId", customerId);
            employeeNotif.put("title", "Booking Auto-Cancelled");
            employeeNotif.put("message", message);
            employeeNotif.put("timestamp", FieldValue.serverTimestamp());
            employeeNotif.put("type", "AUTO_CANCELLATION");
            employeeNotif.put("bookingId", bookingId);
            employeeNotif.put("isRead", false);
            employeeNotif.put("isSeen", false);
            db.collection("notifications").add(employeeNotif);
        }
    }
}
