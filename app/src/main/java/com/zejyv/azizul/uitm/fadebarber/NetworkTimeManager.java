package com.zejyv.azizul.uitm.fadebarber;

import android.util.Log;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.util.Date;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Singleton manager to provide synchronized network time.
 */
public class NetworkTimeManager {
    private static NetworkTimeManager instance;
    private final OkHttpClient httpClient = new OkHttpClient();
    private long timeOffset = 0; // Difference between network and local time
    private boolean isTimeSynced = false;

    private NetworkTimeManager() {}

    public static synchronized NetworkTimeManager getInstance() {
        if (instance == null) {
            instance = new NetworkTimeManager();
        }
        return instance;
    }

    /**
     * Synchronizes local time offset with a reliable network source (Google).
     */
    public void syncTime(OnTimeSyncedListener listener) {
        Request request = new Request.Builder()
                .url("https://www.google.com")
                .head()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("NetworkTime", "Failed to sync time", e);
                if (listener != null) listener.onSyncFailed();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                Date networkDate = response.headers().getDate("Date");
                if (networkDate != null) {
                    timeOffset = networkDate.getTime() - System.currentTimeMillis();
                    isTimeSynced = true;
                    Log.d("NetworkTime", "Time synced. Offset: " + timeOffset + "ms");
                    if (listener != null) listener.onSyncSuccess(getCurrentTime());
                } else {
                    if (listener != null) listener.onSyncFailed();
                }
            }
        });
    }

    /**
     * Returns the current synchronized network time.
     * Falls back to local time if not synced.
     */
    public long getCurrentTime() {
        return System.currentTimeMillis() + timeOffset;
    }

    public boolean isSynced() {
        return isTimeSynced;
    }

    public interface OnTimeSyncedListener {
        void onSyncSuccess(long networkTime);
        void onSyncFailed();
    }
}
