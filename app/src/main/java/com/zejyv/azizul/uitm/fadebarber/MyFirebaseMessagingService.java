package com.zejyv.azizul.uitm.fadebarber;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "fcm_notifications";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        FCMUtils.saveTokenToUserCollections(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = remoteMessage.getNotification() != null ? remoteMessage.getNotification().getTitle() : "Notification";
        String body = remoteMessage.getNotification() != null ? remoteMessage.getNotification().getBody() : "";
        
        String docId = null;
        String type = null;
        String bookingId = null;
        String senderId = null;
        long tsMillis = System.currentTimeMillis();

        if (remoteMessage.getData().size() > 0) {
            if (title == null || title.equals("Notification")) title = remoteMessage.getData().get("title");
            if (body == null || body.isEmpty()) body = remoteMessage.getData().get("message");
            
            docId = remoteMessage.getData().get("notificationId");
            type = remoteMessage.getData().get("type");
            bookingId = remoteMessage.getData().get("bookingId");
            senderId = remoteMessage.getData().get("senderId");
            String tsStr = remoteMessage.getData().get("timestamp");
            if (tsStr != null) {
                try { tsMillis = Long.parseLong(tsStr); } catch (Exception ignored) {}
            }
        }

        showNotification(docId, title, body, type, bookingId, tsMillis, senderId);
    }

    private void showNotification(String docId, String title, String message, String type, String bookingId, long tsMillis, String senderId) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Push Notifications", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("NOTIFICATION_DOC_ID", docId);
        intent.putExtra("NOTIFICATION_TITLE", title);
        intent.putExtra("NOTIFICATION_MESSAGE", message);
        intent.putExtra("NOTIFICATION_TYPE", type);
        intent.putExtra("NOTIFICATION_BOOKING_ID", bookingId);
        intent.putExtra("NOTIFICATION_SENDER_ID", senderId);
        intent.putExtra("NOTIFICATION_TIMESTAMP", tsMillis);
        intent.putExtra("FROM_NOTIFICATION", true);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setColor(androidx.core.content.ContextCompat.getColor(this, R.color.primary_color))
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
