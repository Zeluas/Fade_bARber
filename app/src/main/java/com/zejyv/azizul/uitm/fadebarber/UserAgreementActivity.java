package com.zejyv.azizul.uitm.fadebarber;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class UserAgreementActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_agreement);
        findViewById(R.id.iv_back_agreement).setOnClickListener(v -> finish());
    }
}
