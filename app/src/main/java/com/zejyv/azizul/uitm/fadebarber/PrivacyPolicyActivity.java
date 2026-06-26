package com.zejyv.azizul.uitm.fadebarber;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class PrivacyPolicyActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_policy);
        findViewById(R.id.iv_back_privacy).setOnClickListener(v -> finish());
    }
}
