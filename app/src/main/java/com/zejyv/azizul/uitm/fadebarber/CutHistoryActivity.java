package com.zejyv.azizul.uitm.fadebarber;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.zejyv.azizul.uitm.fadebarber.adapters.CutHistoryAdapter;
import com.zejyv.azizul.uitm.fadebarber.models.Booking;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CutHistoryActivity extends AppCompatActivity {

    private enum TagType { FILTER, KEYWORD, SORT, DATE, DATE_SORT }

    private EditText etSearch;
    private ViewGroup cgFilterTags;
    private View filterBarContainer;
    private MaterialButton btnReset;
    private RecyclerView rvHistory;
    private ProgressBar pbHistory;
    private TextView tvEmpty;

    // Filter Overlay Components
    private View overlayContainer, mcvFilterDialog;
    private final List<Chip> overlayFilterChips = new ArrayList<>();
    private AutoCompleteTextView sortDropdown;

    // Call Hairstylist Overlay Components
    private View callHairstylistOverlay;
    private TextView tvPhoneDisplay;
    private MaterialButton btnCallNow;
    private String pendingPhone = "";

    // Filter State
    private final List<String> styleFilters = new ArrayList<>();
    private final List<String> statusFilters = new ArrayList<>();
    private final List<String> activeKeywords = new ArrayList<>();
    private String activeSort = null;
    private boolean isAscending = false;
    private boolean isDateAscending = false;
    private String selectedDateFilter = null; // dd/MM/yy

    private final Set<String> existingTags = new HashSet<>();

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private CutHistoryAdapter adapter;

    // Data
    private final List<HistoryItem> allHistory = new ArrayList<>();
    private List<HistoryItem> filteredHistory = new ArrayList<>();

    public static class HistoryItem {
        public Booking booking;
        public String barberName = "";
        public String barberPhone = "";
        public String barberProfileUrl = "";
        public double amount = 0.0;
        public long durationMillis = 0;
        public com.google.firebase.Timestamp endTime = null;
        public float rating = 0.0f;
        public String comment = "";

        public HistoryItem(Booking booking) {
            this.booking = booking;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cut_history);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        activeSort = getString(R.string.sort_time);
        isAscending = false;

        initializeViews();
        setupSearchLogic();
        setupOverlayLogic();

        rebuildFilterBar();
        fetchHistory();
    }

    private void initializeViews() {
        etSearch = findViewById(R.id.et_search_history);
        cgFilterTags = findViewById(R.id.cg_filter_tags);
        filterBarContainer = findViewById(R.id.ll_filter_bar_container);
        btnReset = findViewById(R.id.btn_reset_filters);
        rvHistory = findViewById(R.id.rv_cut_history);
        pbHistory = findViewById(R.id.pb_history);
        tvEmpty = findViewById(R.id.tv_empty_history);
        overlayContainer = findViewById(R.id.layout_filter_overlay_container);
        mcvFilterDialog = findViewById(R.id.mcv_filter_dialog);

        // Call Overlay
        callHairstylistOverlay = findViewById(R.id.layout_call_hairstylist);
        tvPhoneDisplay = findViewById(R.id.tv_hairstylist_phone_display);
        btnCallNow = findViewById(R.id.btn_call_now);

        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CutHistoryAdapter(filteredHistory);
        rvHistory.setAdapter(adapter);

        findViewById(R.id.iv_back_history).setOnClickListener(v -> finish());
        setupBackPressed();

        // Overlay specific views
        overlayFilterChips.clear();
        // Status Chips
        overlayFilterChips.add(findViewById(R.id.chip_filter_completed));
        overlayFilterChips.add(findViewById(R.id.chip_filter_cancelled));

        // Hide others for customers
        findViewById(R.id.chip_filter_pending).setVisibility(View.GONE);
        findViewById(R.id.chip_filter_starting).setVisibility(View.GONE);
        findViewById(R.id.chip_filter_paying).setVisibility(View.GONE);
        findViewById(R.id.chip_filter_rating).setVisibility(View.GONE);

        // Style Chips
        overlayFilterChips.add(findViewById(R.id.chip_filter_brazilian));
        overlayFilterChips.add(findViewById(R.id.chip_filter_bird));
        overlayFilterChips.add(findViewById(R.id.chip_filter_side_sweep));
        overlayFilterChips.add(findViewById(R.id.chip_filter_wolf));
        overlayFilterChips.add(findViewById(R.id.chip_filter_afro));
        overlayFilterChips.add(findViewById(R.id.chip_filter_curtain));
        overlayFilterChips.add(findViewById(R.id.chip_filter_disconnect));
        overlayFilterChips.add(findViewById(R.id.chip_filter_afro_tapper));
        overlayFilterChips.add(findViewById(R.id.chip_filter_punk));

        sortDropdown = findViewById(R.id.actv_sort_dropdown);
    }

    private void fetchHistory() {
        if (mAuth.getCurrentUser() == null) return;

        pbHistory.setVisibility(View.VISIBLE);
        String uid = mAuth.getCurrentUser().getUid();

        db.collection("bookings")
                .whereEqualTo("customerId", uid)
                .whereIn("status", java.util.Arrays.asList("Completed", "Cancelled"))
                .get()
                .addOnSuccessListener(value -> {
                    allHistory.clear();
                    if (value.isEmpty()) {
                        pbHistory.setVisibility(View.GONE);
                        applyFiltersAndSort();
                        return;
                    }

                    List<Booking> bookings = value.toObjects(Booking.class);
                    AtomicInteger pending = new AtomicInteger(bookings.size());

                    for (Booking b : bookings) {
                        HistoryItem item = new HistoryItem(b);
                        allHistory.add(item);

                        // Fetch Details
                        fetchItemDetails(item, () -> {
                            if (pending.decrementAndGet() == 0) {
                                pbHistory.setVisibility(View.GONE);
                                applyFiltersAndSort();
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    pbHistory.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load history", Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchItemDetails(HistoryItem item, Runnable onDone) {
        AtomicInteger pending = new AtomicInteger(5); // Barber, ProfilePic, Payment, Timer, Rating

        // 1. Barber Name & Phone
        db.collection("employees").document(item.booking.getEmployeeId()).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                item.barberName = doc.getString("fullname");
                item.barberPhone = doc.getString("phone");
            }
            checkDone(pending, onDone);
        }).addOnFailureListener(e -> checkDone(pending, onDone));

        // 2. Barber Profile Pic
        db.collection("profile_pics").document(item.booking.getEmployeeId()).get().addOnSuccessListener(doc -> {
            if (doc.exists()) item.barberProfileUrl = doc.getString("url");
            checkDone(pending, onDone);
        }).addOnFailureListener(e -> checkDone(pending, onDone));

        // 3. Payment
        db.collection("session_payments").document(item.booking.getBookingId()).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                Double amt = doc.getDouble("paymentAmount");
                if (amt != null) item.amount = amt;
            }
            checkDone(pending, onDone);
        }).addOnFailureListener(e -> checkDone(pending, onDone));

        // 3. Timer
        db.collection("session_timers").document(item.booking.getBookingId()).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                com.google.firebase.Timestamp start = doc.getTimestamp("startTime");
                com.google.firebase.Timestamp end = doc.getTimestamp("endTime");
                Long paused = doc.getLong("totalPausedMillis");
                if (start != null && end != null) {
                    item.endTime = end;
                    item.durationMillis = end.toDate().getTime() - start.toDate().getTime() - (paused != null ? paused : 0);
                }
            }
            checkDone(pending, onDone);
        }).addOnFailureListener(e -> checkDone(pending, onDone));

        // 4. Rating
        db.collection("hairstylist_ratings").document(item.booking.getBookingId()).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                Double r = doc.getDouble("rating");
                if (r != null) item.rating = r.floatValue();
                item.comment = doc.getString("comment");
            }
            checkDone(pending, onDone);
        }).addOnFailureListener(e -> checkDone(pending, onDone));
    }

    private void checkDone(AtomicInteger pending, Runnable onDone) {
        if (pending.decrementAndGet() == 0) onDone.run();
    }

    private void setupSearchLogic() {
        findViewById(R.id.mcv_search_container).setOnClickListener(v -> {
            etSearch.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
        });

        findViewById(R.id.btn_filter_date).setOnClickListener(v -> showDatePicker());
        findViewById(R.id.btn_filter_sort).setOnClickListener(v -> showOverlay(true));

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                String text = etSearch.getText().toString().trim();
                if (!text.isEmpty() && !activeKeywords.contains(text.toLowerCase())) {
                    activeKeywords.add(text);
                    applyFiltersAndSort();
                    rebuildFilterBar();
                }
                etSearch.setText("");
                hideKeyboard();
                return true;
            }
            return false;
        });

        btnReset.setOnClickListener(v -> animateOutAllChips(() -> {
            styleFilters.clear();
            statusFilters.clear();
            activeKeywords.clear();
            selectedDateFilter = null;
            activeSort = getString(R.string.sort_time);
            isAscending = false;
            isDateAscending = false;
            existingTags.clear();
            etSearch.setText("");
            sortDropdown.setText(getString(R.string.sort_time), false);
            for (Chip chip : overlayFilterChips) {
                if (chip != null) {
                    chip.setChecked(false);
                    resetChipStyle(chip);
                }
            }
            applyFiltersAndSort();
            rebuildFilterBar();
        }));
    }

    private void setupOverlayLogic() {
        String[] sortOptions = {getString(R.string.sort_time), getString(R.string.sort_amount), getString(R.string.sort_name)};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, sortOptions);
        sortDropdown.setAdapter(adapter);
        sortDropdown.setText(getString(R.string.sort_time), false);
        sortDropdown.setOnItemClickListener((parent, view, position, id) -> {
            activeSort = sortOptions[position];
            applyFiltersAndSort();
            rebuildFilterBar();
        });

        for (Chip chip : overlayFilterChips) {
            if (chip != null) setupToggleChip(chip);
        }

        overlayContainer.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.mcv_filter_dialog).setOnClickListener(v -> {
            // Click inside the filter card should not close it or trigger anything else
        });
        findViewById(R.id.btn_apply_filters).setOnClickListener(v -> {
            styleFilters.clear();
            statusFilters.clear();

            ChipGroup cgStatus = findViewById(R.id.cg_filter_status);
            ChipGroup cgHair = findViewById(R.id.cg_filter_hair);

            for (int i = 0; i < cgStatus.getChildCount(); i++) {
                Chip chip = (Chip) cgStatus.getChildAt(i);
                if (chip.isChecked()) statusFilters.add(chip.getText().toString());
            }
            for (int i = 0; i < cgHair.getChildCount(); i++) {
                Chip chip = (Chip) cgHair.getChildAt(i);
                if (chip.isChecked()) styleFilters.add(chip.getText().toString());
            }

            applyFiltersAndSort();
            rebuildFilterBar();
            showOverlay(false);
        });

        // Call Overlay logic
        callHairstylistOverlay.setOnClickListener(v -> showCallOverlay(false, ""));
        findViewById(R.id.mcv_call_dialog).setOnClickListener(v -> {});
        btnCallNow.setOnClickListener(v -> {
            if (!pendingPhone.isEmpty()) {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_DIAL);
                intent.setData(android.net.Uri.parse("tel:" + pendingPhone));
                startActivity(intent);
                showCallOverlay(false, "");
            }
        });
    }

    private void showCallOverlay(boolean show, String phone) {
        if (show) {
            pendingPhone = phone;
            // Format phone number for display: +60 XX-XXXX XXXX
            String formatted = phone;
            if (phone.startsWith("0")) formatted = "+60 " + phone.substring(1, 3) + "-" + phone.substring(3, 7) + " " + phone.substring(7);
            else if (phone.startsWith("60")) formatted = "+" + phone.substring(0, 2) + " " + phone.substring(2, 4) + "-" + phone.substring(4, 8) + " " + phone.substring(8);
            tvPhoneDisplay.setText(formatted);

            callHairstylistOverlay.setVisibility(View.VISIBLE);
            callHairstylistOverlay.setAlpha(0f);
            callHairstylistOverlay.animate().alpha(1f).setDuration(200).start();

            View mcvCallDialog = findViewById(R.id.mcv_call_dialog);
            mcvCallDialog.post(() -> {
                mcvCallDialog.setTranslationY(mcvCallDialog.getHeight());
                mcvCallDialog.animate().translationY(0).setDuration(300).start();
            });
        } else {
            View mcvCallDialog = findViewById(R.id.mcv_call_dialog);
            mcvCallDialog.animate().translationY(mcvCallDialog.getHeight()).setDuration(200).start();
            callHairstylistOverlay.animate().alpha(0f).setDuration(200).withEndAction(() -> callHairstylistOverlay.setVisibility(View.GONE)).start();
        }
    }

    private void showDatePicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(selection);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            selectedDateFilter = sdf.format(calendar.getTime());
            applyFiltersAndSort();
            rebuildFilterBar();
        });
        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    private void applyFiltersAndSort() {
        filteredHistory = new ArrayList<>(allHistory);

        // Filter by Status (Inclusive within category)
        if (!statusFilters.isEmpty()) {
            filteredHistory = filteredHistory.stream()
                    .filter(item -> statusFilters.contains(item.booking.getStatus()))
                    .collect(Collectors.toList());
        }

        // Filter by Style (Inclusive within category)
        if (!styleFilters.isEmpty()) {
            filteredHistory = filteredHistory.stream()
                    .filter(item -> styleFilters.contains(item.booking.getHairstyleName()))
                    .collect(Collectors.toList());
        }

        // Filter by Date
        if (selectedDateFilter != null) {
            filteredHistory = filteredHistory.stream()
                    .filter(item -> selectedDateFilter.equals(item.booking.getDate()))
                    .collect(Collectors.toList());
        }

        // Filter by Keywords (Loose, multi-field, partial)
        if (!activeKeywords.isEmpty()) {
            filteredHistory = filteredHistory.stream().filter(item -> {
                String barber = item.barberName.toLowerCase();
                String style = item.booking.getHairstyleName().toLowerCase();
                String id = item.booking.getBookingId().toLowerCase();
                String date = item.booking.getDate().toLowerCase();
                String time = item.booking.getTime().toLowerCase();
                String amount = String.format(Locale.getDefault(), "%.2f", item.amount);
                String comment = item.comment.toLowerCase();

                for (String kw : activeKeywords) {
                    String lkw = kw.toLowerCase();
                    if (barber.contains(lkw) || style.contains(lkw) || id.contains(lkw) ||
                        date.contains(lkw) || time.contains(lkw) || amount.contains(lkw) ||
                        comment.contains(lkw)) return true;
                }
                return false;
            }).collect(Collectors.toList());
        }

        // Sort
        Collections.sort(filteredHistory, (h1, h2) -> {
            // Priority: Date is Primary, selectedSort is Secondary
            // Exception: Amount Sort (sole focus)

            if (activeSort.equals(getString(R.string.sort_amount))) {
                int res = Double.compare(h1.amount, h2.amount);
                return isAscending ? res : -res;
            }

            // 1. Primary Sort: Date
            int dateRes = 0;
            SimpleDateFormat dateSdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            try {
                Date d1 = dateSdf.parse(h1.booking.getDate());
                Date d2 = dateSdf.parse(h2.booking.getDate());
                dateRes = d1.compareTo(d2);
            } catch (Exception e) {}
            int primaryRes = isDateAscending ? dateRes : -dateRes;

            if (primaryRes != 0) return primaryRes;

            // 2. Secondary Sort: Time/Name
            int secondaryRes = 0;
            if (activeSort.equals(getString(R.string.sort_time))) {
                if (h1.endTime != null && h2.endTime != null) {
                    secondaryRes = h1.endTime.compareTo(h2.endTime);
                } else {
                    SimpleDateFormat timeSdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                    try {
                        Date t1 = timeSdf.parse(h1.booking.getTime());
                        Date t2 = timeSdf.parse(h2.booking.getTime());
                        secondaryRes = t1.compareTo(t2);
                    } catch (Exception e) {
                        secondaryRes = h1.booking.getCreatedAt().compareTo(h2.booking.getCreatedAt());
                    }
                }
            } else if (activeSort.equals(getString(R.string.sort_name))) {
                secondaryRes = h1.barberName.compareTo(h2.barberName);
            }

            return isAscending ? secondaryRes : -secondaryRes;
        });

        updateUI();
    }

    private void updateUI() {
        tvEmpty.setVisibility(filteredHistory.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.updateData(filteredHistory);
        runLayoutAnimation(rvHistory);
    }

    public void showCallBarberDialog(String phone) {
        if (phone == null || phone.isEmpty()) {
            Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show();
            return;
        }
        showCallOverlay(true, phone);
    }

    private void runLayoutAnimation(final RecyclerView recyclerView) {
        final Context context = recyclerView.getContext();
        final android.view.animation.LayoutAnimationController controller =
                android.view.animation.AnimationUtils.loadLayoutAnimation(context, R.anim.layout_animation_pop_up);

        recyclerView.setLayoutAnimation(controller);
        if (recyclerView.getAdapter() != null) {
            recyclerView.getAdapter().notifyDataSetChanged();
        }
        recyclerView.scheduleLayoutAnimation();
    }

    private void rebuildFilterBar() {
        cgFilterTags.removeAllViews();
        boolean hasContent = false;
        Set<String> newTags = new HashSet<>();

        // 1. Sort Section
        List<Pair<String, TagType>> sortList = new ArrayList<>();
        if (activeSort != null) {
            String sortText = activeSort + (isAscending ? " (asc)" : " (desc)");
            sortList.add(new Pair<>(sortText, TagType.SORT));
            newTags.add("SORT:" + sortText);
        }
        if (selectedDateFilter == null && !activeSort.equals(getString(R.string.sort_amount))) {
            String dateSortText = "Date " + (isDateAscending ? "(asc)" : "(desc)");
            sortList.add(new Pair<>(dateSortText, TagType.DATE_SORT));
            newTags.add("DATE_SORT:" + dateSortText);
        }
        if (!sortList.isEmpty()) {
            addCategorySection(getString(R.string.label_sort), sortList, false);
            hasContent = true;
        }

        // 2. Date Section
        if (selectedDateFilter != null) {
            if (hasContent) addDivider();
            addCategorySection(getString(R.string.label_date), Collections.singletonList(new Pair<>(selectedDateFilter, TagType.DATE)), true);
            newTags.add("DATE:" + selectedDateFilter);
            hasContent = true;
        }

        // 3. Status Section
        if (!statusFilters.isEmpty()) {
            if (hasContent) addDivider();
            List<Pair<String, TagType>> list = statusFilters.stream().map(s -> new Pair<>(s, TagType.FILTER)).collect(Collectors.toList());
            addCategorySection(getString(R.string.filter_status) + ":", list, true);
            for (String s : statusFilters) newTags.add("FILTER:" + s);
            hasContent = true;
        }

        // 4. Haircut Section
        if (!styleFilters.isEmpty()) {
            if (hasContent) addDivider();
            List<Pair<String, TagType>> list = styleFilters.stream().map(s -> new Pair<>(s, TagType.FILTER)).collect(Collectors.toList());
            addCategorySection(getString(R.string.filter_haircut) + ":", list, true);
            for (String s : styleFilters) newTags.add("FILTER:" + s);
            hasContent = true;
        }

        // 5. Keyword Section
        if (!activeKeywords.isEmpty()) {
            if (hasContent) addDivider();
            List<Pair<String, TagType>> list = activeKeywords.stream().map(s -> new Pair<>(s, TagType.KEYWORD)).collect(Collectors.toList());
            addCategorySection(getString(R.string.label_keyword), list, true);
            for (String s : activeKeywords) newTags.add("KEYWORD:" + s);
            hasContent = true;
        }

        existingTags.retainAll(newTags);
        existingTags.addAll(newTags);
        updateFilterContainerVisibility(hasContent);
    }

    private void addDivider() {
        View d = new View(this);
        d.setBackgroundColor(ContextCompat.getColor(this, R.color.trans_white));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1));
        p.setMargins(0, dpToPx(4), 0, dpToPx(8));
        d.setLayoutParams(p);
        cgFilterTags.addView(d);
    }

    private static class Pair<A, B> {
        public final A first;
        public final B second;
        public Pair(A first, B second) { this.first = first; this.second = second; }
    }

    private void addCategorySection(String label, List<Pair<String, TagType>> items, boolean removable) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.TOP);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dpToPx(4));
        row.setLayoutParams(p);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(ContextCompat.getColor(this, R.color.white));
        tv.setTextSize(14);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setMinWidth(dpToPx(70));
        tv.setPadding(0, dpToPx(10), dpToPx(8), 0);
        row.addView(tv);

        ChipGroup g = new ChipGroup(this);
        g.setChipSpacingHorizontal(dpToPx(8));
        g.setChipSpacingVertical(dpToPx(4));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        g.setLayoutParams(gp);

        for (Pair<String, TagType> item : items) {
            g.addView(createTagChip(item.first, item.second, removable, !existingTags.contains(item.second.name() + ":" + item.first)));
        }
        row.addView(g);
        cgFilterTags.addView(row);
    }

    private Chip createTagChip(String text, TagType type, boolean removable, boolean animate) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setCloseIconVisible(removable);
        chip.setChipBackgroundColorResource(R.color.white);
        chip.setTextColor(ContextCompat.getColor(this, R.color.primary_color));
        chip.setCloseIconTintResource(R.color.primary_color);
        chip.setChipStartPadding(dpToPx(8));
        chip.setChipEndPadding(dpToPx(8));

        if (type == TagType.SORT) {
            chip.setChipIconVisible(true);
            chip.setChipIcon(ContextCompat.getDrawable(this, isAscending ? R.drawable.ic_sort_asc : R.drawable.ic_sort_desc));
            chip.setChipIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_color)));
            chip.setOnClickListener(v -> {
                isAscending = !isAscending;
                applyFiltersAndSort();
                rebuildFilterBar();
            });
        } else if (type == TagType.DATE_SORT) {
            chip.setChipIconVisible(true);
            chip.setChipIcon(ContextCompat.getDrawable(this, isDateAscending ? R.drawable.ic_sort_asc : R.drawable.ic_sort_desc));
            chip.setChipIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_color)));
            chip.setOnClickListener(v -> {
                isDateAscending = !isDateAscending;
                applyFiltersAndSort();
                rebuildFilterBar();
            });
        } else if (removable) {
            chip.setOnClickListener(v -> animateOut(chip, () -> removeTag(text, type)));
            chip.setOnCloseIconClickListener(v -> animateOut(chip, () -> removeTag(text, type)));
        }

        if (animate) {
            chip.setScaleX(0f); chip.setScaleY(0f);
            chip.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        }
        return chip;
    }

    private void removeTag(String text, TagType type) {
        if (type == TagType.FILTER) {
            styleFilters.remove(text);
            statusFilters.remove(text);
            for (Chip c : overlayFilterChips) if (c != null && c.getText().toString().equals(text)) resetChipStyle(c);
        } else if (type == TagType.KEYWORD) activeKeywords.remove(text);
        else if (type == TagType.DATE) selectedDateFilter = null;
        applyFiltersAndSort();
        rebuildFilterBar();
    }

    private void setupToggleChip(Chip chip) {
        int primary = ContextCompat.getColor(this, R.color.primary_color);
        int white = ContextCompat.getColor(this, R.color.white);
        chip.setOnCheckedChangeListener((bv, isChecked) -> {
            chip.setChipBackgroundColor(ColorStateList.valueOf(isChecked ? primary : white));
            chip.setTextColor(isChecked ? white : primary);
        });
    }

    private void resetChipStyle(Chip chip) {
        chip.setChecked(false);
        chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white)));
        chip.setTextColor(ContextCompat.getColor(this, R.color.primary_color));
    }

    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (overlayContainer.getVisibility() == View.VISIBLE) {
                    showOverlay(false);
                } else if (callHairstylistOverlay.getVisibility() == View.VISIBLE) {
                    showCallOverlay(false, "");
                } else {
                    finish();
                }
            }
        });
    }

    private void showOverlay(boolean show) {
        if (show) {
            overlayContainer.setVisibility(View.VISIBLE);
            overlayContainer.setAlpha(0f);
            overlayContainer.animate().alpha(1f).setDuration(200).start();

            mcvFilterDialog.setScaleX(0f);
            mcvFilterDialog.setScaleY(0f);
            mcvFilterDialog.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
        } else {
            mcvFilterDialog.animate().scaleX(0f).scaleY(0f).setDuration(200).start();
            overlayContainer.animate().alpha(0f).setDuration(200)
                    .withEndAction(() -> overlayContainer.setVisibility(View.GONE)).start();
        }
    }

    private void updateFilterContainerVisibility(boolean hasContent) {
        // Bar container should show if ANY tag exists (Sort, Date, Filter, Keyword)
        if (hasContent) {
            if (filterBarContainer.getVisibility() == View.GONE) {
                filterBarContainer.setVisibility(View.VISIBLE);
                filterBarContainer.setAlpha(1f);
                filterBarContainer.setTranslationY(0f);
            }
        } else {
            if (filterBarContainer.getVisibility() == View.VISIBLE) {
                filterBarContainer.animate().alpha(0f).translationY(-20f).setDuration(300).withEndAction(() -> filterBarContainer.setVisibility(View.GONE)).start();
            }
        }

        // Reset button should only show if there's something to reset (Sort count as something to reset if not default)
        boolean isDefaultSort = activeSort != null && activeSort.equals(getString(R.string.sort_time)) && !isAscending && !isDateAscending;
        boolean canReset = (selectedDateFilter != null) || !styleFilters.isEmpty() || !statusFilters.isEmpty() || !activeKeywords.isEmpty() || !isDefaultSort;
        btnReset.setVisibility(canReset ? View.VISIBLE : View.GONE);
    }

    private void animateOut(View v, Runnable onEnd) {
        v.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(200).withEndAction(onEnd).start();
    }

    private void animateOutAllChips(Runnable onEnd) {
        List<View> chips = new ArrayList<>();
        findChips(cgFilterTags, chips);
        if (chips.isEmpty()) { onEnd.run(); return; }
        AtomicInteger count = new AtomicInteger(chips.size());
        for (View c : chips) animateOut(c, () -> { if (count.decrementAndGet() == 0) onEnd.run(); });
    }

    private void findChips(ViewGroup p, List<View> chips) {
        for (int i = 0; i < p.getChildCount(); i++) {
            View c = p.getChildAt(i);
            if (c instanceof Chip) chips.add(c);
            else if (c instanceof ViewGroup) findChips((ViewGroup) c, chips);
        }
    }

    private int dpToPx(int dp) { return (int) (dp * getResources().getDisplayMetrics().density); }
    private void hideKeyboard() {
        View v = getCurrentFocus();
        if (v != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}
