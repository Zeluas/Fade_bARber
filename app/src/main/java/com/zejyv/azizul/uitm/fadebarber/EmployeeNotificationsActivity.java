package com.zejyv.azizul.uitm.fadebarber;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

public class EmployeeNotificationsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_notifications);

        findViewById(R.id.iv_back_notifications).setOnClickListener(v -> finish());

        if (savedInstanceState == null) {
            ActivityFragment fragment = new ActivityFragment();
            Bundle args = new Bundle();
            args.putBoolean("SHOW_BACK", true);
            fragment.setArguments(args);

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, fragment);
            transaction.commit();
        }
    }
}
