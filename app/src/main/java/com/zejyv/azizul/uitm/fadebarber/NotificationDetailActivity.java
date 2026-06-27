package com.zejyv.azizul.uitm.fadebarber;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.firebase.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class NotificationDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_detail);

        findViewById(R.id.iv_back_notif_detail).setOnClickListener(v -> finish());

        String title = getIntent().getStringExtra("title");
        String message = getIntent().getStringExtra("message");
        long timestampMillis = getIntent().getLongExtra("timestamp", 0);

        TextView tvTitle = findViewById(R.id.tv_detail_title);
        TextView tvDate = findViewById(R.id.tv_detail_date);
        TextView tvMessage = findViewById(R.id.tv_detail_message);

        tvTitle.setText(title);
        tvMessage.setText(message);

        if (timestampMillis != 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault());
            tvDate.setText(sdf.format(new java.util.Date(timestampMillis)));
        }
    }
}
