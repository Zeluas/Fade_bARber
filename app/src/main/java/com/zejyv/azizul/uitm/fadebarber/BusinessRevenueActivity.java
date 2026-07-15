package com.zejyv.azizul.uitm.fadebarber;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class BusinessRevenueActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private TextView tvToday, tvMonth, tvLabelMonth;
    private LinearLayout llChartYAxis;
    private TextView tvChartBubbleText;
    private CardView mcvChartBubble;
    private LinearLayout llChartContainer, llTopStyles, llTopBarbers, llPaymentMethods;
    private RecyclerView rvTransactions;
    private TransactionAdapter adapter;
    private List<Transaction> transactionList = new ArrayList<>();

    private Map<String, String> barberNames = new HashMap<>();
    private Map<String, String> customerNames = new HashMap<>();
    private Map<String, String> profilePicUrls = new HashMap<>();
    
    private String currentMonthName;
    private View mainContent, loadingOverlay;
    private NumberFormat currencyFormat = NumberFormat.getNumberInstance(Locale.US);

    private Map<Integer, Double> weeklyRevenueMap = new HashMap<>();
    private Map<Integer, String> weeklyDateMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_revenue);

        db = FirebaseFirestore.getInstance();
        initializeViews();
        fetchData();
    }

    private void initializeViews() {
        findViewById(R.id.btn_back_revenue).setOnClickListener(v -> finish());
        findViewById(R.id.iv_refresh_revenue).setOnClickListener(v -> fetchData());

        mainContent = findViewById(R.id.sv_admin_content);
        loadingOverlay = findViewById(R.id.loading_overlay);
        
        tvToday = findViewById(R.id.tv_revenue_today);
        tvMonth = findViewById(R.id.tv_revenue_month);
        tvLabelMonth = findViewById(R.id.tv_label_month);
        
        llChartYAxis = findViewById(R.id.ll_chart_yaxis);
        mcvChartBubble = findViewById(R.id.mcv_chart_bubble);
        tvChartBubbleText = findViewById(R.id.tv_chart_bubble_text);
        
        llChartContainer = findViewById(R.id.ll_revenue_chart_container);
        llTopStyles = findViewById(R.id.ll_top_styles_container);
        llTopBarbers = findViewById(R.id.ll_top_barbers_container);
        llPaymentMethods = findViewById(R.id.ll_payment_methods_container);
        rvTransactions = findViewById(R.id.rv_revenue_transactions);

        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter();
        rvTransactions.setAdapter(adapter);

        setupXAxisClicks();
        updateTimeDependentLabels();
    }

    private void setupXAxisClicks() {
        int[] ids = {R.id.tv_xaxis_sun, R.id.tv_xaxis_mon, R.id.tv_xaxis_tue, R.id.tv_xaxis_wed, R.id.tv_xaxis_thu, R.id.tv_xaxis_fri, R.id.tv_xaxis_sat};
        for (int i = 0; i < ids.length; i++) {
            final int day = i + 1; // Calendar.SUNDAY = 1
            findViewById(ids[i]).setOnClickListener(v -> {
                Double rev = weeklyRevenueMap.get(day);
                String date = weeklyDateMap.get(day);
                showChartBubble(date != null ? date : "", rev != null ? rev : 0.0);
            });
        }
    }

    private void updateTimeDependentLabels() {
        long now = NetworkTimeManager.getInstance().getCurrentTime();
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        cal.setTimeInMillis(now);
        currentMonthName = new SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.getTime());
        tvLabelMonth.setText(currentMonthName + " Revenue");
        
        ((TextView)findViewById(R.id.tv_label_top_hairstyle)).setText("Top Hairstyle for " + currentMonthName);
        ((TextView)findViewById(R.id.tv_label_top_barber)).setText("Top Hairstylist for " + currentMonthName);
    }

    private void fetchData() {
        loadingOverlay.setVisibility(View.VISIBLE);
        db.collection("employees").get().addOnSuccessListener(emps -> {
            for (QueryDocumentSnapshot d : emps) barberNames.put(d.getId(), d.getString("fullname"));
            
            db.collection("customers").get().addOnSuccessListener(custs -> {
                for (QueryDocumentSnapshot d : custs) customerNames.put(d.getId(), d.getString("name"));
                
                db.collection("profile_pics").get().addOnSuccessListener(pics -> {
                    for (QueryDocumentSnapshot d : pics) profilePicUrls.put(d.getId(), d.getString("url"));
                    
                    loadRevenueData();
                });
            });
        });
    }

    private void loadRevenueData() {
        long now = NetworkTimeManager.getInstance().getCurrentTime();
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
        cal.setTimeInMillis(now);
        
        String todayStr = String.format(Locale.getDefault(), "%02d/%02d/%02d",
                cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR) % 100);
        String monthSuffix = String.format(Locale.getDefault(), "/%02d/%02d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR) % 100);

        Calendar weekStart = (Calendar) cal.clone();
        weekStart.set(Calendar.HOUR_OF_DAY, 0);
        weekStart.set(Calendar.MINUTE, 0);
        weekStart.set(Calendar.SECOND, 0);
        weekStart.set(Calendar.MILLISECOND, 0);
        // Robust way to get the start of the current week (Sunday) locale-independently
        while (weekStart.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            weekStart.add(Calendar.DAY_OF_YEAR, -1);
        }

        db.collection("bookings").get().addOnSuccessListener(bookingDocs -> {
            Map<String, BookingInfo> bookingMap = new HashMap<>();
            for (QueryDocumentSnapshot d : bookingDocs) {
                bookingMap.put(d.getId(), new BookingInfo(
                        d.getId(),
                        d.getString("customerId"),
                        d.getString("employeeId"),
                        d.getString("hairstyleName"),
                        d.getString("hairstyleId"),
                        d.getString("date"),
                        d.getString("time")
                ));
            }

            db.collection("session_payments")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get().addOnSuccessListener(paymentDocs -> {
                
                double totalToday = 0;
                double totalMonth = 0;
                weeklyRevenueMap.clear();
                weeklyDateMap.clear();
                Map<String, List<BookingInfo>> styleBookings = new HashMap<>();
                Map<String, Double> styleRevenue = new HashMap<>();
                Map<String, Double> barberRevenue = new HashMap<>();
                Map<String, Integer> methodCount = new HashMap<>();

                transactionList.clear();

                for (QueryDocumentSnapshot doc : paymentDocs) {
                    Double amount = doc.getDouble("paymentAmount");
                    String bId = doc.getString("bookingId");
                    String method = doc.getString("paymentMethod");
                    Timestamp ts = doc.getTimestamp("timestamp");

                    if (amount == null || bId == null) continue;

                    BookingInfo b = bookingMap.get(bId);
                    if (b == null) continue;

                    boolean isThisMonth = b.date != null && b.date.contains(monthSuffix);

                    if (todayStr.equals(b.date)) totalToday += amount;
                    if (isThisMonth) totalMonth += amount;

                    if (ts != null) {
                        Calendar tCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));
                        tCal.setTime(ts.toDate());
                        if (ts.toDate().getTime() >= weekStart.getTimeInMillis()) {
                            int day = tCal.get(Calendar.DAY_OF_WEEK);
                            Double currentVal = weeklyRevenueMap.get(day);
                            weeklyRevenueMap.put(day, (currentVal != null ? currentVal : 0.0) + amount);
                            weeklyDateMap.put(day, b.date);
                        }
                    }

                    if (isThisMonth) {
                        if (b.hairstyle != null) {
                            styleRevenue.put(b.hairstyle, styleRevenue.getOrDefault(b.hairstyle, 0.0) + amount);
                            List<BookingInfo> bookings = styleBookings.get(b.hairstyle);
                            if (bookings == null) {
                                bookings = new ArrayList<>();
                                styleBookings.put(b.hairstyle, bookings);
                            }
                            bookings.add(b);
                        }
                        if (b.employeeId != null) barberRevenue.put(b.employeeId, barberRevenue.getOrDefault(b.employeeId, 0.0) + amount);
                    }
                    if (method != null) methodCount.put(method, methodCount.getOrDefault(method, 0) + 1);

                    if (transactionList.size() < 5) {
                        transactionList.add(new Transaction(
                                b.bookingId,
                                customerNames.getOrDefault(b.customerId, "Unknown"),
                                b.hairstyle,
                                barberNames.getOrDefault(b.employeeId, "Unknown"),
                                amount,
                                method,
                                b.date,
                                b.time
                        ));
                    }
                }

                updateUI(totalToday, totalMonth, weeklyRevenueMap, weeklyDateMap, styleRevenue, barberRevenue, methodCount, styleBookings);
                
                loadingOverlay.setVisibility(View.GONE);
                Animation anim = AnimationUtils.loadAnimation(BusinessRevenueActivity.this, R.anim.slide_in_left);
                mainContent.startAnimation(anim);
            });
        });
    }

    private void updateUI(double today, double month, Map<Integer, Double> weekly, Map<Integer, String> dates, Map<String, Double> styles, Map<String, Double> barbers, Map<String, Integer> methods, Map<String, List<BookingInfo>> styleBookings) {
        tvToday.setText(String.format(Locale.US, "RM %.2f", today));
        tvMonth.setText(String.format(Locale.US, "RM %.2f", month));

        updateChart(weekly, dates);
        updateBreakdown(llTopStyles, styles, false, styleBookings);
        updateBreakdown(llTopBarbers, barbers, true, null);
        updatePaymentMethods(methods);
        adapter.notifyDataSetChanged();
    }

    private void updateChart(Map<Integer, Double> weekly, Map<Integer, String> dates) {
        llChartContainer.removeAllViews();
        llChartYAxis.removeAllViews();
        
        double max = 0;
        for (double v : weekly.values()) if (v > max) max = v;
        if (max == 0) max = 100;
        
        int interval = 20;
        int steps = (int)(max / interval);
        if (steps > 4) interval = (int)Math.ceil(max / 4.0 / 20.0) * 20;
        
        List<Double> yLabels = new ArrayList<>();
        yLabels.add(0.0);
        double currentY = interval;
        while (currentY < max) {
            yLabels.add(currentY);
            currentY += interval;
        }
        if (max > 0 && (yLabels.isEmpty() || yLabels.get(yLabels.size()-1) != max)) yLabels.add(max);
        Collections.sort(yLabels, Collections.reverseOrder());
        
        TextView tvRm = new TextView(this);
        tvRm.setText("(RM)");
        tvRm.setTextSize(7);
        tvRm.setTypeface(null, android.graphics.Typeface.BOLD);
        tvRm.setTextColor(ContextCompat.getColor(this, R.color.primary_color));
        LinearLayout.LayoutParams rmParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rmParams.bottomMargin = dpToPx(2);
        tvRm.setLayoutParams(rmParams);
        llChartYAxis.addView(tvRm);

        for (int i = 0; i < yLabels.size(); i++) {
            double labelVal = yLabels.get(i);
            TextView tv = new TextView(this);
            tv.setText(currencyFormat.format(labelVal));
            tv.setTextSize(8);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setTextColor(ContextCompat.getColor(this, R.color.primary_color));
            llChartYAxis.addView(tv);
            if (i < yLabels.size() - 1) {
                View spacer = new View(this);
                LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
                spacerParams.weight = 1;
                spacer.setLayoutParams(spacerParams);
                llChartYAxis.addView(spacer);
            }
        }

        for (int i = 1; i <= 7; i++) {
            double val = weekly.getOrDefault(i, 0.0);
            String date = dates.getOrDefault(i, "");
            View bar = new View(this);
            int maxHeightPx = dpToPx(160);
            int heightPx = (int) ((val / max) * maxHeightPx);
            
            // Ensure minimum visibility for non-zero values and prevent overflow
            if (heightPx < dpToPx(4) && val > 0) heightPx = dpToPx(4);
            if (heightPx > maxHeightPx) heightPx = maxHeightPx;
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, heightPx);
            params.weight = 1;
            params.setMargins(dpToPx(4), 0, dpToPx(4), 0);
            bar.setLayoutParams(params);
            bar.setBackgroundResource(R.drawable.bg_pill_primary);
            
            final double fVal = val;
            final String fDate = date;
            bar.setOnClickListener(v -> showChartBubble(fDate, fVal));
            llChartContainer.addView(bar);
        }
    }

    private void showChartBubble(String date, double revenue) {
        mcvChartBubble.setVisibility(View.VISIBLE);
        tvChartBubbleText.setText((date.isEmpty() ? "No Date" : date) + ": RM " + currencyFormat.format(revenue));
        new Handler().postDelayed(() -> mcvChartBubble.setVisibility(View.GONE), 3000);
    }

    private void updateBreakdown(LinearLayout container, Map<String, Double> data, boolean isBarber, Map<String, List<BookingInfo>> styleBookings) {
        container.removeAllViews();
        List<Map.Entry<String, Double>> list = new ArrayList<>(data.entrySet());
        Collections.sort(list, (e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        int limit = Math.min(list.size(), 3);
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Double> entry = list.get(i);
            String id = entry.getKey();
            String name = isBarber ? barberNames.getOrDefault(id, "Unknown") : id;
            
            View itemView = LayoutInflater.from(this).inflate(R.layout.item_top_ranking, container, false);
            TextView tvRankNum = itemView.findViewById(R.id.tv_rank_number);
            TextView tvName = itemView.findViewById(R.id.tv_rank_name);
            TextView tvRev = itemView.findViewById(R.id.tv_rank_revenue);
            ImageView ivDisplay = itemView.findViewById(R.id.iv_rank_display);
            CardView cvBadge = itemView.findViewById(R.id.mcv_rank_badge);

            tvRankNum.setText(String.valueOf(i + 1));
            tvName.setText(name);
            tvRev.setText(String.format(Locale.US, "RM %.2f", entry.getValue()));

            int badgeColor;
            if (i == 0) badgeColor = ContextCompat.getColor(this, R.color.rank_gold);
            else if (i == 1) badgeColor = ContextCompat.getColor(this, R.color.rank_silver);
            else badgeColor = ContextCompat.getColor(this, R.color.rank_bronze);
            cvBadge.setCardBackgroundColor(badgeColor);

            if (isBarber) {
                String url = profilePicUrls.get(id);
                ivDisplay.setPadding(0, 0, 0, 0);
                Glide.with(this)
                        .load(url)
                        .placeholder(R.drawable.ic_profile)
                        .transform(new CenterCrop(), new RoundedCorners(dpToPx(12)))
                        .into(ivDisplay);
            } else {
                List<BookingInfo> bookings = styleBookings.get(id);
                String styleId = (bookings != null && !bookings.isEmpty()) ? bookings.get(0).hairstyleId : null;
                loadServiceImage(id, styleId, ivDisplay);
            }
            container.addView(itemView);
        }
    }

    private void loadServiceImage(String name, String id, ImageView imageView) {
        if (name == null || imageView == null) return;
        try {
            String[] images = getAssets().list("images");
            if (images != null) {
                String key = (id != null && id.startsWith("hs_")) ? id.substring(3) : "";
                String cleanKey = key.toLowerCase().replace(" ", "").replace("-", "");
                String cleanName = name.toLowerCase().replace(" ", "").replace("-", "");

                for (String imageName : images) {
                    String cleanImg = imageName.toLowerCase().split("\\.")[0].replace(" ", "").replace("-", "");
                    boolean matchFound = (!cleanKey.isEmpty() && (cleanImg.contains(cleanKey) || cleanKey.contains(cleanImg))) ||
                                       (!cleanName.isEmpty() && (cleanName.contains(cleanImg) || cleanImg.contains(cleanName)));

                    if (matchFound) {
                        imageView.setPadding(0, 0, 0, 0);
                        Glide.with(this)
                                .load("file:///android_asset/images/" + imageName)
                                .transform(new CenterCrop(), new RoundedCorners(dpToPx(12)))
                                .into(imageView);
                        return;
                    }
                }
            }
        } catch (IOException e) { e.printStackTrace(); }

        imageView.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        imageView.setImageResource(R.drawable.ic_hair);
    }

    private void updatePaymentMethods(Map<String, Integer> methods) {
        llPaymentMethods.removeAllViews();
        int total = 0;
        for (int c : methods.values()) total += c;
        if (total == 0) return;

        for (Map.Entry<String, Integer> entry : methods.entrySet()) {
            View v = LayoutInflater.from(this).inflate(R.layout.view_payment_distribution_item, llPaymentMethods, false);
            TextView tvLabel = v.findViewById(R.id.tv_dist_label);
            ProgressBar pb = v.findViewById(R.id.pb_dist);
            TextView tvVal = v.findViewById(R.id.tv_dist_value);
            ImageView ivIcon = v.findViewById(R.id.iv_dist_icon);

            String key = entry.getKey();
            tvLabel.setText(key);
            int pct = (entry.getValue() * 100) / total;
            pb.setProgress(pct);
            tvVal.setText(pct + "%");
            if ("QR Code".equalsIgnoreCase(key)) ivIcon.setImageResource(R.drawable.ic_qrcode);
            else ivIcon.setImageResource(R.drawable.ic_cash);
            llPaymentMethods.addView(v);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private static class BookingInfo {
        String bookingId, customerId, employeeId, hairstyle, hairstyleId, date, time;
        BookingInfo(String id, String c, String e, String h, String hid, String d, String t) {
            this.bookingId = id; this.customerId = c; this.employeeId = e; this.hairstyle = h; this.hairstyleId = hid; this.date = d; this.time = t;
        }
    }

    private static class Transaction {
        String bookingId, customer, hairstyle, barber, method, date, time;
        double amount;
        Transaction(String id, String c, String h, String b, double a, String m, String d, String t) {
            this.bookingId = id; this.customer = c; this.hairstyle = h; this.barber = b; this.amount = a; this.method = m; this.date = d; this.time = t;
        }
    }

    private class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new ViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_revenue_transaction, p, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
            Transaction t = transactionList.get(pos);
            h.tvBookingId.setText("#" + t.bookingId.substring(Math.max(0, t.bookingId.length() - 8)));
            h.tvTitle.setText(t.customer);

            // Subtitle: Hairstyle • Barber
            SpannableStringBuilder sub = new SpannableStringBuilder(t.hairstyle + " • ");
            int start = sub.length();
            sub.append(t.barber);
            sub.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, sub.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sub.setSpan(new ForegroundColorSpan(ContextCompat.getColor(BusinessRevenueActivity.this, R.color.primary_color)), start, sub.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            h.tvSub.setText(sub);

            // Date & Time: Seperated
            h.tvDate.setText(t.date);
            h.tvDate.setTextColor(ContextCompat.getColor(BusinessRevenueActivity.this, R.color.primary_color));
            h.tvDate.setTypeface(null, android.graphics.Typeface.BOLD);
            
            h.tvTime.setText(t.time);

            h.tvAmount.setText(String.format(Locale.US, "+RM %.2f", t.amount));
            h.tvMethod.setText(t.method);
            int icon = "QR Code".equalsIgnoreCase(t.method) ? R.drawable.ic_qrcode : R.drawable.ic_cash;
            h.ivIcon.setImageResource(icon);
        }
        @Override public int getItemCount() { return transactionList.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvSub, tvDate, tvTime, tvAmount, tvMethod, tvBookingId;
            ImageView ivIcon;
            ViewHolder(View v) {
                super(v);
                tvBookingId = v.findViewById(R.id.tv_transaction_booking_id);
                tvTitle = v.findViewById(R.id.tv_transaction_title);
                tvSub = v.findViewById(R.id.tv_transaction_subtitle);
                tvDate = v.findViewById(R.id.tv_transaction_date);
                tvTime = v.findViewById(R.id.tv_transaction_time);
                tvAmount = v.findViewById(R.id.tv_transaction_amount);
                tvMethod = v.findViewById(R.id.tv_transaction_method);
                ivIcon = v.findViewById(R.id.iv_transaction_icon);
            }
        }
    }
}
