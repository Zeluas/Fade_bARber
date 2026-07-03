package com.zejyv.azizul.uitm.fadebarber;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class NotificationDetailActivity extends AppCompatActivity {

    private View layoutCallHairstylist, mcvCallDialog;
    private TextView tvHairstylistPhone;
    private String rawPhone = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_detail);

        findViewById(R.id.iv_back_notif_detail).setOnClickListener(v -> finish());

        String title = getIntent().getStringExtra("title");
        String message = getIntent().getStringExtra("message");
        String type = getIntent().getStringExtra("type");
        String bookingId = getIntent().getStringExtra("bookingId");
        String docId = getIntent().getStringExtra("NOTIFICATION_DOC_ID");
        String senderId = getIntent().getStringExtra("senderId");
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

        if (bookingId != null) {
            if ("NOSHOW".equals(type) || "CANCELLATION".equals(type)) {
                showExtraBookingDetails(bookingId, type);
            } else if ("UPDATE".equals(type)) {
                showUpdateDetails(docId, bookingId);
            }
        }

        setupCallOverlay();
        setupCallButton(senderId, type);
        setupBackPressed();
    }

    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutCallHairstylist != null && layoutCallHairstylist.getVisibility() == View.VISIBLE) {
                    hideCallDialog();
                } else {
                    finish();
                }
            }
        });
    }

    private void setupCallOverlay() {
        layoutCallHairstylist = findViewById(R.id.layout_call_hairstylist);
        mcvCallDialog = findViewById(R.id.mcv_call_dialog);
        tvHairstylistPhone = findViewById(R.id.tv_hairstylist_phone_display);
        View btnCallNow = findViewById(R.id.btn_call_now);

        if (btnCallNow != null) {
            btnCallNow.setOnClickListener(v -> {
                if (rawPhone != null && !rawPhone.isEmpty()) {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_DIAL);
                    intent.setData(android.net.Uri.parse("tel:" + rawPhone));
                    startActivity(intent);
                }
                hideCallDialog();
            });
        }

        if (layoutCallHairstylist != null) {
            layoutCallHairstylist.setOnClickListener(v -> hideCallDialog());
        }
    }

    private void showCallDialog(String phone, String title) {
        if (layoutCallHairstylist == null || mcvCallDialog == null || tvHairstylistPhone == null) return;
        this.rawPhone = phone;

        TextView tvHeader = findViewById(R.id.tv_call_dialog_header);
        if (tvHeader != null) tvHeader.setText(title);

        tvHairstylistPhone.setText(formatPhoneNumber(phone));
        layoutCallHairstylist.setVisibility(View.VISIBLE);
        layoutCallHairstylist.setAlpha(0f);
        layoutCallHairstylist.animate().alpha(1f).setDuration(200).start();

        mcvCallDialog.post(() -> {
            mcvCallDialog.setTranslationY(mcvCallDialog.getHeight());
            mcvCallDialog.animate().translationY(0).setDuration(300).start();
        });
    }

    private void hideCallDialog() {
        if (layoutCallHairstylist == null || mcvCallDialog == null) return;
        mcvCallDialog.animate().translationY(mcvCallDialog.getHeight()).setDuration(200).start();
        layoutCallHairstylist.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> layoutCallHairstylist.setVisibility(View.GONE)).start();
    }

    private String formatPhoneNumber(String raw) {
        if (raw == null || raw.isEmpty()) return "-";
        if (raw.startsWith("0")) return "+60 " + raw.substring(1, 3) + "-" + raw.substring(3, 7) + " " + raw.substring(7);
        return raw;
    }

    private void setupCallButton(String senderId, String type) {
        if (senderId == null) return;
        
        com.google.android.material.button.MaterialButton btnCall = findViewById(R.id.btn_detail_call);

        // Fetch sender details to see if it's an employee or customer
        FirebaseFirestore.getInstance().collection("employees").document(senderId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    // It's an employee, show call button for these types (for customer)
                    if ("UPDATE".equals(type) || "CANCELLATION_LOCK".equals(type) || "AUTO_CANCELLATION".equals(type) || "NOSHOW".equals(type)) {
                        btnCall.setVisibility(View.VISIBLE);
                        btnCall.setText("Call Hairstylist");
                        String phone = doc.getString("phone");
                        btnCall.setOnClickListener(v -> showCallDialog(phone, "Call Hairstylist"));
                    } else {
                        btnCall.setVisibility(View.GONE);
                    }
                } else {
                    // Check if it's a customer
                    FirebaseFirestore.getInstance().collection("customers").document(senderId).get()
                        .addOnSuccessListener(custDoc -> {
                            // If sender is customer, we don't show call button for employee (as requested previously)
                            btnCall.setVisibility(View.GONE);
                        });
                }
            });
    }

    private void showUpdateDetails(String docId, String bookingId) {
        if (docId == null) return;
        findViewById(R.id.ll_update_details).setVisibility(View.VISIBLE);

        FirebaseFirestore.getInstance().collection("notifications").document(docId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    populateMiniCard(findViewById(R.id.layout_before_update),
                        doc.getString("oldEmployeeId"),
                        doc.getString("oldDate"),
                        doc.getString("oldTime"),
                        doc.getString("oldHairstyleName"),
                        bookingId);

                    populateMiniCard(findViewById(R.id.layout_after_update),
                        doc.getString("newEmployeeId"),
                        doc.getString("newDate"),
                        doc.getString("newTime"),
                        doc.getString("newHairstyleName"),
                        bookingId);
                }
            });
    }

    private void populateMiniCard(View cardView, String employeeId, String date, String time, String styleName, String bookingId) {
        if (cardView == null) return;

        TextView tvBarber = cardView.findViewById(R.id.tv_mini_barber_name);
        TextView tvDate = cardView.findViewById(R.id.tv_mini_date);
        TextView tvTime = cardView.findViewById(R.id.tv_mini_time);
        TextView tvStyle = cardView.findViewById(R.id.tv_mini_style_name);
        TextView tvBookingId = cardView.findViewById(R.id.tv_mini_booking_id);
        ImageView ivBarber = cardView.findViewById(R.id.iv_mini_barber);
        ImageView ivStyle = cardView.findViewById(R.id.iv_mini_style);

        tvDate.setText(date);
        tvTime.setText(time);
        tvStyle.setText(styleName);
        tvBookingId.setText("#" + bookingId.substring(0, 8).toUpperCase());

        loadHairstyleImage(styleName, ivStyle);

        if (employeeId != null) {
            FirebaseFirestore.getInstance().collection("employees").document(employeeId).get()
                .addOnSuccessListener(empDoc -> {
                    if (empDoc.exists()) {
                        tvBarber.setText(empDoc.getString("fullname"));
                    }
                });

            FirebaseFirestore.getInstance().collection("profile_pics").document(employeeId).get()
                .addOnSuccessListener(picDoc -> {
                    if (picDoc.exists()) {
                        String url = picDoc.getString("url");
                        Glide.with(this).load(url).placeholder(R.drawable.ic_profile).into(ivBarber);
                    }
                });
        }
    }

    private void showExtraBookingDetails(String bookingId, String type) {
        findViewById(R.id.ll_extra_details).setVisibility(View.VISIBLE);
        TextView tvStatus = findViewById(R.id.tv_noshow_status);
        
        if ("CANCELLATION".equals(type)) {
            tvStatus.setText("Cancelled");
        } else {
            tvStatus.setText("Cancelled"); // User requested No-Show to also show "Cancelled" for consistency
        }
        
        FirebaseFirestore.getInstance().collection("bookings").document(bookingId).get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String employeeId = doc.getString("employeeId");
                    String styleName = doc.getString("hairstyleName");
                    
                    TextView tvBookingId = findViewById(R.id.tv_noshow_booking_id);
                    TextView tvBookingDate = findViewById(R.id.tv_noshow_date);
                    TextView tvBookingTime = findViewById(R.id.tv_noshow_time);
                    TextView tvStyle = findViewById(R.id.tv_noshow_style);
                    ImageView ivHair = findViewById(R.id.iv_noshow_hair);
                    ImageView ivHairSmall = findViewById(R.id.iv_noshow_hair_small);

                    String bId = doc.getString("bookingId");
                    tvBookingId.setText(bId != null ? "#" + bId : "#" + bookingId.substring(0, 8).toUpperCase());
                    tvBookingDate.setText(doc.getString("date"));
                    tvBookingTime.setText(doc.getString("time"));
                    tvStyle.setText(styleName);

                    // Load Style Image from Assets
                    loadHairstyleImage(styleName, ivHairSmall);

                    // Fetch Barber Details
                    if (employeeId != null) {
                        FirebaseFirestore.getInstance().collection("employees").document(employeeId).get()
                            .addOnSuccessListener(empDoc -> {
                                if (empDoc.exists()) {
                                    ((TextView)findViewById(R.id.tv_noshow_barber)).setText(empDoc.getString("fullname"));
                                }
                            });
                        
                        FirebaseFirestore.getInstance().collection("profile_pics").document(employeeId).get()
                            .addOnSuccessListener(picDoc -> {
                                if (picDoc.exists()) {
                                    String url = picDoc.getString("url");
                                    Glide.with(this).load(url).placeholder(R.drawable.ic_profile).into(ivHair);
                                }
                            });
                    }
                }
            });
    }

    private void loadHairstyleImage(String name, ImageView iv) {
        if (name == null) { iv.setImageResource(R.drawable.ic_hair); return; }
        try {
            String[] images = getAssets().list("images");
            if (images != null) {
                String cleanName = name.toLowerCase().replace(" ", "").replace("-", "");
                for (String imageName : images) {
                    String cleanImg = imageName.toLowerCase().split("\\.")[0].replace(" ", "").replace("-", "");
                    if (cleanName.contains(cleanImg) || cleanImg.contains(cleanName)) {
                        try (java.io.InputStream is = getAssets().open("images/" + imageName)) {
                            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
                            iv.setImageBitmap(bitmap);
                            return;
                        }
                    }
                }
            }
        } catch (java.io.IOException e) { e.printStackTrace(); }
        iv.setImageResource(R.drawable.ic_hair);
    }
}
