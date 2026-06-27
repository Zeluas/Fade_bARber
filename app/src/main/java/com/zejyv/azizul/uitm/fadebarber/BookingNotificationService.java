package com.zejyv.azizul.uitm.fadebarber;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class BookingNotificationService extends Service {

    private static final String CHANNEL_ID_SERVICE = "booking_service_channel_silent";
    private static final String CHANNEL_ID_ALERTS = "booking_alerts_channel";
    private static final int NOTIF_ID = 999;
    private ListenerRegistration notificationListener;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannels();
        
        Intent notificationIntent = new Intent(this, AuthActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
                .setContentTitle("Fade bARber Active")
                .setContentText("Monitoring your appointments...")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIF_ID, notification);
        }
        
        setupFirestoreListener();
        
        return START_STICKY;
    }

    private void setupFirestoreListener() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            stopSelf();
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (notificationListener != null) notificationListener.remove();

        notificationListener = FirebaseFirestore.getInstance().collection("notifications")
                .whereEqualTo("receiverId", uid)
                .whereEqualTo("isSeen", false)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value != null) {
                        for (DocumentChange dc : value.getDocumentChanges()) {
                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                String title = dc.getDocument().getString("title");
                                String message = dc.getDocument().getString("message");
                                triggerSystemAlert(title, message);

                                // Mark as seen immediately so it doesn't alert again on relaunch
                                dc.getDocument().getReference().update("isSeen", true);
                            }
                        }
                    }
                });
    }

    private void triggerSystemAlert(String title, String message) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        Notification alert = new NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        nm.notify((int) System.currentTimeMillis(), alert);
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            // Channel for the persistent background service (Silent)
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID_SERVICE, "Background Monitor", NotificationManager.IMPORTANCE_MIN);
            serviceChannel.setDescription("Keeps the app active to receive appointment updates.");
            serviceChannel.setShowBadge(false);
            manager.createNotificationChannel(serviceChannel);

            // Channel for actual booking alerts (Loud/Visible)
            NotificationChannel alertChannel = new NotificationChannel(
                    CHANNEL_ID_ALERTS, "Booking Alerts", NotificationManager.IMPORTANCE_HIGH);
            alertChannel.setDescription("Notifications for cancellations and appointment updates.");
            alertChannel.enableVibration(true);
            manager.createNotificationChannel(alertChannel);
        }
    }

    @Override
    public void onDestroy() {
        if (notificationListener != null) notificationListener.remove();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
