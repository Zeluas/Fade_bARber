package com.zejyv.azizul.uitm.fadebarber;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        findViewById(R.id.iv_back_about).setOnClickListener(v -> finish());

        TextView tvVersion = findViewById(R.id.tv_app_version);
        
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            android.content.SharedPreferences prefs = EncryptedSharedPreferences.create(
                    this,
                    "secret_shared_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            String fullname = prefs.getString("fullname", "");
            if (!fullname.isEmpty()) {
                tvVersion.setText("Version 1.0.0 (Employee Side)");
            } else {
                tvVersion.setText("Version 1.0.0");
            }
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            tvVersion.setText("Version 1.0.0");
        }
    }
}
