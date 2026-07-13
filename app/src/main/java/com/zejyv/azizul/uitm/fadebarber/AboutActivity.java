package com.zejyv.azizul.uitm.fadebarber;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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

        findViewById(R.id.cv_github_repo).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://github.com/Zeluas/Fade_bARber"));
            startActivity(intent);
        });

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

            String role = prefs.getString("role", "");
            if ("employee".equals(role)) {
                tvVersion.setText("Version 1.0.0 (Employee Side)");
            } else if ("admin".equals(role)) {
                tvVersion.setText("Version 1.0.0 (Admin Side)");
            } else {
                tvVersion.setText("Version 1.0.0");
            }
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            tvVersion.setText("Version 1.0.0");
        }

        TextView tvFyp = findViewById(R.id.tv_fyp_journey);
        final int[] clickCount = {0};
        final Toast[] currentToast = {null};

        tvFyp.setOnClickListener(v -> {
            clickCount[0]++;

            if (currentToast[0] != null) {
                currentToast[0].cancel();
            }

            if (clickCount[0] >= 10) {
                String baseText = "Working on this project, I went in as just an average guy trying to complete the Final Year Project (FYP), nothing more, nothing less. At first, I felt genuinely content and ambitious about what I was building, energized by the possibility of creating something meaningful, even as a University Project. But as the weeks progressed, that initial excitement slowly began to fade, worn down by an avalanche of other problems that kept tumbling into my path. Assignments piled up, personal issues emerged and worst of all, I found myself locked in a constant battle with my own procrastination, which dragged the project’s progress to a crawl and stagnated stage. Still, I remained determined. Day after day, week after week, I chipped away at it, even when motivation abandoned me entirely. Eventually, I crossed the finish line, submitting the project and its accompanying report just before the deadline closed.";
                String hiddenText = " But the journey had taken its toll. Everything that happened during those months, the stress, the sleepless nights, the constant self-doubt. It all had damaged something inside me. I finished, yes and to everyone else it looked like a success, but the cost was hidden deep within, concealed behind a smile that nobody thought to question. The scars were invisible, but they were very much there.";
                
                String fullText = baseText + hiddenText;
                SpannableString spannableString = new SpannableString(fullText);
                
                int start = baseText.length();
                int end = fullText.length();
                
                spannableString.setSpan(
                    new BackgroundColorSpan(ContextCompat.getColor(this, R.color.warning_red)),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );

                tvFyp.setText(spannableString);
                tvFyp.setOnClickListener(null);
                tvFyp.setClickable(false);
                tvFyp.setForeground(null);

                currentToast[0] = Toast.makeText(this, "The full story has been revealed.", Toast.LENGTH_LONG);
                currentToast[0].show();
            } else if (clickCount[0] >= 2) {
                int remaining = 10 - clickCount[0];
                currentToast[0] = Toast.makeText(this, "Keep clicking! " + remaining + " more times", Toast.LENGTH_SHORT);
                currentToast[0].show();
            }
        });
    }
}
