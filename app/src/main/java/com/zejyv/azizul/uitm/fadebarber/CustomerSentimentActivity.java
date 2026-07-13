package com.zejyv.azizul.uitm.fadebarber;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class CustomerSentimentActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private TextView tvOverallRating, tvTotalReviews, tvAiSentiment;
    private RatingBar rbOverall;
    private LinearLayout llReviewsContainer;
    private View[] ratingRows = new View[5];
    private int[] starCounts = new int[5];
    private Handler handler;

    private static final String PREFS_NAME = "AiSentimentPrefs";
    private static final String KEY_CACHED_ANALYSIS = "cached_analysis";
    private static final String KEY_LAST_UPDATE_TIME = "last_update_time";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_sentiment);

        db = FirebaseFirestore.getInstance();
        handler = new Handler(Looper.getMainLooper());

        initializeViews();
        fetchData();
    }

    private void initializeViews() {
        findViewById(R.id.iv_back_sentiment).setOnClickListener(v -> finish());

        tvOverallRating = findViewById(R.id.tv_overall_rating_large);
        rbOverall = findViewById(R.id.rb_overall_stars);
        tvTotalReviews = findViewById(R.id.tv_total_reviews_count);
        tvAiSentiment = findViewById(R.id.tv_ai_sentiment_detailed);
        llReviewsContainer = findViewById(R.id.ll_reviews_container);

        ratingRows[0] = findViewById(R.id.rating_bar_5);
        ratingRows[1] = findViewById(R.id.rating_bar_4);
        ratingRows[2] = findViewById(R.id.rating_bar_3);
        ratingRows[3] = findViewById(R.id.rating_bar_2);
        ratingRows[4] = findViewById(R.id.rating_bar_1);

        for (int i = 0; i < 5; i++) {
            TextView label = ratingRows[i].findViewById(R.id.tv_star_label);
            label.setText(String.valueOf(5 - i));
        }
    }

    private void fetchData() {
        db.collection("hairstylist_ratings")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<QueryDocumentSnapshot> reviews = new ArrayList<>();
                    double totalRating = 0;
                    for (int i = 0; i < 5; i++) starCounts[i] = 0;

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        reviews.add(doc);
                        Double rating = doc.getDouble("rating");
                        if (rating != null) {
                            totalRating += rating;
                            int starIndex = (int) Math.round(rating);
                            if (starIndex >= 1 && starIndex <= 5) {
                                starCounts[5 - starIndex]++;
                            }
                        }
                    }

                    int count = reviews.size();
                    if (count > 0) {
                        double avg = totalRating / count;
                        updateSummaryUI(avg, count);
                        updateReviewsUI(reviews);
                        checkAndGenerateAiSentiment(avg, count, reviews);
                    } else {
                        findViewById(R.id.tv_no_reviews).setVisibility(View.VISIBLE);
                    }
                });
    }

    private void updateSummaryUI(double avg, int count) {
        tvOverallRating.setText(String.format(Locale.getDefault(), "%.1f", avg));
        rbOverall.setRating((float) avg);
        tvTotalReviews.setText(count + (count == 1 ? " review" : " reviews"));

        for (int i = 0; i < 5; i++) {
            LinearProgressIndicator lpi = ratingRows[i].findViewById(R.id.lpi_rating_bar);
            TextView countTv = ratingRows[i].findViewById(R.id.tv_rating_count);

            int percent = (int) ((starCounts[i] / (float) count) * 100);
            lpi.setProgress(percent);
            countTv.setText(String.valueOf(starCounts[i]));
        }
    }

    private void updateReviewsUI(List<QueryDocumentSnapshot> reviews) {
        llReviewsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < reviews.size(); i++) {
            QueryDocumentSnapshot doc = reviews.get(i);
            View itemView = inflater.inflate(R.layout.item_review_employee, llReviewsContainer, false);

            TextView tvName = itemView.findViewById(R.id.tv_reviewer_name);
            TextView tvComment = itemView.findViewById(R.id.tv_review_comment);
            TextView tvDate = itemView.findViewById(R.id.tv_review_date);
            TextView tvTime = itemView.findViewById(R.id.tv_review_time);
            TextView tvBookingId = itemView.findViewById(R.id.tv_review_booking_id);
            RatingBar rbStars = itemView.findViewById(R.id.rb_review_stars);
            ImageView ivReviewer = itemView.findViewById(R.id.iv_reviewer_profile);

            View mcvBarberBadge = itemView.findViewById(R.id.mcv_review_barber_badge);
            TextView tvBarberName = itemView.findViewById(R.id.tv_review_barber_name);

            Double rating = doc.getDouble("rating");
            if (rating != null) rbStars.setRating(rating.floatValue());

            tvComment.setText(doc.getString("comment"));

            String bId = doc.getString("bookingId");
            if (bId != null) {
                tvBookingId.setText("#" + bId);
                db.collection("bookings").document(bId).get().addOnSuccessListener(bookingDoc -> {
                    if (bookingDoc.exists()) {
                        tvTime.setText(bookingDoc.getString("time"));
                        String date = bookingDoc.getString("date");
                        if (date != null) tvDate.setText(date);
                    }
                });
            }

            String employeeId = doc.getString("employeeId");
            if (employeeId != null && tvBarberName != null) {
                mcvBarberBadge.setVisibility(View.VISIBLE);
                db.collection("employees").document(employeeId).get().addOnSuccessListener(empDoc -> {
                    if (empDoc.exists()) {
                        tvBarberName.setText(empDoc.getString("fullname"));
                    }
                });
            }

            Timestamp ts = doc.getTimestamp("timestamp");
            if (ts != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
                tvDate.setText(sdf.format(ts.toDate()));
            }

            String customerId = doc.getString("customerId");
            if (customerId != null) {
                db.collection("customers").document(customerId).get().addOnSuccessListener(userDoc -> {
                    if (userDoc.exists()) {
                        tvName.setText(userDoc.getString("name"));
                    }
                });
                db.collection("profile_pics").document(customerId).get().addOnSuccessListener(picDoc -> {
                    if (picDoc.exists()) {
                        String url = picDoc.getString("url");
                        if (url != null && !url.isEmpty()) Glide.with(CustomerSentimentActivity.this).load(url).placeholder(R.drawable.ic_profile).into(ivReviewer);
                    }
                });
            }

            llReviewsContainer.addView(itemView);

            if (i < reviews.size() - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(Color.parseColor("#EEEEEE"));
                llReviewsContainer.addView(divider);
            }
        }
    }

    private void checkAndGenerateAiSentiment(double avg, int count, List<QueryDocumentSnapshot> reviews) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String cachedAnalysis = prefs.getString(KEY_CACHED_ANALYSIS, "");
        long lastUpdateTime = prefs.getLong(KEY_LAST_UPDATE_TIME, 0);

        long currentTime = NetworkTimeManager.getInstance().getCurrentTime();

        if (cachedAnalysis.isEmpty() || isNewDay(lastUpdateTime, currentTime)) {
            generateAiSentiment(avg, count, reviews, currentTime);
        } else {
            tvAiSentiment.setText(cachedAnalysis);
        }
    }

    private boolean isNewDay(long lastMillis, long currentMillis) {
        Calendar lastCal = Calendar.getInstance();
        lastCal.setTimeInMillis(lastMillis);
        Calendar currentCal = Calendar.getInstance();
        currentCal.setTimeInMillis(currentMillis);

        return currentCal.get(Calendar.YEAR) != lastCal.get(Calendar.YEAR) ||
                currentCal.get(Calendar.DAY_OF_YEAR) != lastCal.get(Calendar.DAY_OF_YEAR);
    }

    private void generateAiSentiment(double avg, int count, List<QueryDocumentSnapshot> reviews, long currentTime) {
        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("PASTE_YOUR_API_KEY_HERE")) {
            tvAiSentiment.setText("AI analysis is currently unavailable. Please check your API key.");
            return;
        }

        GenerativeModel gm = new GenerativeModel("gemini-3.1-flash-lite", apiKey);
        GenerativeModelFutures model = GenerativeModelFutures.from(gm);

        StringBuilder prompt = new StringBuilder();
        prompt.append("Perform a sentiment analysis for a barber shop admin based on customer comments. ");
        prompt.append("Overall rating: ").append(String.format(Locale.getDefault(), "%.1f", avg)).append("/5.0. ");
        prompt.append("Total reviews: ").append(count).append(".\n\n");
        prompt.append("Comments:\n");
        
        int limit = Math.min(reviews.size(), 20); // Analyze up to 20 recent comments
        for (int i = 0; i < limit; i++) {
            String comment = reviews.get(i).getString("comment");
            if (comment != null && !comment.isEmpty()) {
                prompt.append("- ").append(comment).append("\n");
            }
        }

        prompt.append("\nRequirements:\n");
        prompt.append("1. Provide a short paragraph for 'Pros', identifying up to 5 good things mentioned.\n");
        prompt.append("2. Provide a short paragraph for 'Cons', identifying up to 5 recurring complaints or bad things.\n");
        prompt.append("3. Provide an 'Overall Vibe' summary.\n");
        prompt.append("Strictly no emojis, no markdown headers, no personal information (names of customers or barbers). Keep it professional and concise.");

        Content content = new Content.Builder().addText(prompt.toString()).build();
        Futures.addCallback(model.generateContent(content), new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String text = result.getText();
                if (text != null) {
                    handler.post(() -> {
                        String analysis = text.trim();
                        tvAiSentiment.setText(analysis);
                        
                        // Cache result
                        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                        editor.putString(KEY_CACHED_ANALYSIS, analysis);
                        editor.putLong(KEY_LAST_UPDATE_TIME, currentTime);
                        editor.apply();
                    });
                }
            }
            @Override
            public void onFailure(@NonNull Throwable t) {
                handler.post(() -> tvAiSentiment.setText("Unable to generate AI analysis at this time."));
            }
        }, Executors.newSingleThreadExecutor());
    }
}
