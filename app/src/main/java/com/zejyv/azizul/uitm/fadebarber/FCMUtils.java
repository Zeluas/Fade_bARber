package com.zejyv.azizul.uitm.fadebarber;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class FCMUtils {

    public static void updateTokenInFirestore() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                String token = task.getResult();
                saveTokenToUserCollections(token);
            }
        });
    }

    public static void saveTokenToUserCollections(String token) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("fcmToken", token);

        // Update in core users collection
        db.collection("users").document(uid).update(tokenData);

        // Also update in specific profile collections for convenience
        db.collection("customers").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) db.collection("customers").document(uid).update(tokenData);
        });

        db.collection("employees").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) db.collection("employees").document(uid).update(tokenData);
        });
    }
}
