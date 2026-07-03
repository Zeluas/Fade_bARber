package com.zejyv.azizul.uitm.fadebarber;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.zejyv.azizul.uitm.fadebarber.adapters.EmployeeBookingAdapter;
import com.zejyv.azizul.uitm.fadebarber.models.Booking;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class EmployeeBookFragment extends Fragment {

    private enum TagType { FILTER, KEYWORD, SORT, DATE, DATE_SORT }

    private EditText etSearch;
    private ViewGroup cgFilterTags; 
    private View filterBarContainer;
    private MaterialButton btnReset;
    private RecyclerView rvBookings;
    private TextView tvNoBookingsInfo;
    
    // Filter Overlay Components (from Activity)
    private View overlayContainer, mcvFilterDialog;
    private final List<Chip> overlayFilterChips = new ArrayList<>();
    private AutoCompleteTextView sortDropdown;

    // Call Customer Overlay Components (Local to Fragment)
    private View callCustomerOverlay, mcvCallDialogEmp;
    private TextView tvPhoneDisplay;
    private MaterialButton btnCallNow;
    private String pendingPhone = "";

    // Filter State
    private final List<String> statusFilters = new ArrayList<>();
    private final List<String> haircutFilters = new ArrayList<>();
    private final List<String> activeKeywords = new ArrayList<>();
    private String activeSort = null;
    private boolean isAscending = false;
    private boolean isDateAscending = false;
    private boolean ignoreDate = false;

    private final Set<String> existingTags = new HashSet<>();

    private CalendarView calendarView;
    private TextView tvEarnToday, tvEarnMonth, tvLabelMonth, tvLabelToday;

    private FirebaseFirestore db;
    private String currentEmployeeId;

    public static class BookingItem {
        public Booking booking;
        public String customerName = "";
        public String customerPhone = "";
        public String customerProfileUrl = "";
        public double amount = 0.0;
        public long durationMillis = 0;
        public float rating = 0.0f;
        public String comment = "";
        public BookingItem(Booking b) { this.booking = b; }
    }

    private List<BookingItem> allBookingItems = new ArrayList<>();
    private List<BookingItem> filteredBookingItems = new ArrayList<>();
    private String selectedDate; // format dd/MM/yy

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_employee_book, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentEmployeeId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        activeSort = getString(R.string.sort_time);
        isAscending = false;

        initializeViews(view);
        setupCalendarLogic();
        setupSearchLogic(view);
        setupOverlayLogic(view);
        
        long currentTime = NetworkTimeManager.getInstance().getCurrentTime();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
        selectedDate = sdf.format(new Date(currentTime));
        
        if (calendarView != null) {
            calendarView.setDate(currentTime, false, true);
        }

        fetchBookings();
        rebuildFilterBar();
    }

    private void initializeViews(View view) {
        etSearch = view.findViewById(R.id.et_search_bookings);
        cgFilterTags = view.findViewById(R.id.cg_filter_tags);
        filterBarContainer = view.findViewById(R.id.ll_filter_bar_container);
        btnReset = view.findViewById(R.id.btn_reset_filters);
        
        calendarView = view.findViewById(R.id.calendar_view_employee);
        tvEarnToday = view.findViewById(R.id.tv_earn_today_book);
        tvLabelToday = view.findViewById(R.id.tv_label_today);
        tvEarnMonth = view.findViewById(R.id.tv_earn_month_book);
        tvLabelMonth = view.findViewById(R.id.tv_label_month);

        rvBookings = view.findViewById(R.id.rv_all_bookings);
        rvBookings.setLayoutManager(new LinearLayoutManager(getContext()));
        tvNoBookingsInfo = view.findViewById(R.id.tv_no_bookings_info);

        callCustomerOverlay = view.findViewById(R.id.layout_call_customer);
        mcvCallDialogEmp = view.findViewById(R.id.mcv_call_dialog_emp);
        tvPhoneDisplay = view.findViewById(R.id.tv_customer_phone_display);
        btnCallNow = view.findViewById(R.id.btn_call_now_emp);

        if (getActivity() != null) {
            overlayContainer = getActivity().findViewById(R.id.layout_filter_overlay_container);
            mcvFilterDialog = getActivity().findViewById(R.id.mcv_filter_dialog);
            sortDropdown = getActivity().findViewById(R.id.actv_sort_dropdown);

            overlayFilterChips.clear();
            int[] chipIds = {
                R.id.chip_filter_pending, R.id.chip_filter_completed, R.id.chip_filter_cancelled,
                R.id.chip_filter_starting, R.id.chip_filter_paying, R.id.chip_filter_rating,
                R.id.chip_filter_brazilian, R.id.chip_filter_bird, R.id.chip_filter_side_sweep,
                R.id.chip_filter_wolf, R.id.chip_filter_afro, R.id.chip_filter_curtain,
                R.id.chip_filter_disconnect, R.id.chip_filter_afro_tapper, R.id.chip_filter_punk
            };
            for (int id : chipIds) {
                Chip chip = getActivity().findViewById(id);
                if (chip != null) {
                    overlayFilterChips.add(chip);
                    setupToggleChip(chip);
                }
            }
        }
    }

    private void setupCalendarLogic() {
        if (calendarView != null) {
            calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
                if (ignoreDate) return;
                Calendar calendar = Calendar.getInstance();
                calendar.set(year, month, dayOfMonth);
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
                selectedDate = sdf.format(calendar.getTime());
                fetchBookings();
                rebuildFilterBar();
            });
        }
    }

    private void fetchBookings() {
        if (currentEmployeeId == null) return;

        com.google.firebase.firestore.Query query = db.collection("bookings")
                .whereEqualTo("employeeId", currentEmployeeId);
        if (!ignoreDate) {
            query = query.whereEqualTo("date", selectedDate);
        }

        query.addSnapshotListener((value, error) -> {
            if (error != null || value == null) return;
            allBookingItems.clear();
            if (value.isEmpty()) {
                applyFiltersAndSort();
                calculateEarnings();
                return;
            }
            
            List<Booking> bookings = value.toObjects(Booking.class);
            java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(bookings.size());
            for (Booking b : bookings) {
                BookingItem item = new BookingItem(b);
                allBookingItems.add(item);
                fetchItemDetails(item, () -> {
                    if (pending.decrementAndGet() == 0) {
                        applyFiltersAndSort();
                        calculateEarnings();
                    }
                });
            }
        });
    }

    private void fetchItemDetails(BookingItem item, Runnable onDone) {
        java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(5);
        db.collection("customers").document(item.booking.getCustomerId()).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                item.customerName = doc.getString("name");
                item.customerPhone = doc.getString("phone");
            }
            checkDone(pending, onDone);
        }).addOnFailureListener(e -> checkDone(pending, onDone));

        db.collection("profile_pics").document(item.booking.getCustomerId()).get().addOnSuccessListener(doc -> {
            if (doc.exists()) item.customerProfileUrl = doc.getString("url");
            checkDone(pending, onDone);
        }).addOnFailureListener(e -> checkDone(pending, onDone));

        db.collection("session_payments").document(item.booking.getBookingId()).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                Double amt = doc.getDouble("paymentAmount");
                if (amt != null) item.amount = amt;
            }
            checkDone(pending, onDone);
        }).addOnFailureListener(e -> checkDone(pending, onDone));

        db.collection("session_timers").document(item.booking.getBookingId()).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                com.google.firebase.Timestamp start = doc.getTimestamp("startTime");
                com.google.firebase.Timestamp end = doc.getTimestamp("endTime");
                Long paused = doc.getLong("totalPausedMillis");
                if (start != null && end != null) {
                    item.durationMillis = end.toDate().getTime() - start.toDate().getTime() - (paused != null ? paused : 0);
                }
            }
            checkDone(pending, onDone);
        }).addOnFailureListener(e -> checkDone(pending, onDone));

        db.collection("hairstylist_ratings").document(item.booking.getBookingId()).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                Double r = doc.getDouble("rating");
                if (r != null) item.rating = r.floatValue();
                item.comment = doc.getString("comment");
            }
            checkDone(pending, onDone);
        }).addOnFailureListener(e -> checkDone(pending, onDone));
    }

    private void checkDone(java.util.concurrent.atomic.AtomicInteger p, Runnable d) { if (p.decrementAndGet() == 0) d.run(); }

    private void calculateEarnings() {
        if (tvLabelToday != null) {
            tvLabelToday.setText(ignoreDate ? getString(R.string.all_time_label) : getString(R.string.today_label));
        }

        double total = 0;
        for (BookingItem item : allBookingItems) {
            if ("Completed".equalsIgnoreCase(item.booking.getStatus())) total += item.amount;
        }
        if (tvEarnToday != null) {
            java.text.NumberFormat formatter = java.text.NumberFormat.getNumberInstance(Locale.US);
            formatter.setMinimumFractionDigits(2);
            formatter.setMaximumFractionDigits(2);
            tvEarnToday.setText("RM " + formatter.format(total));
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            Date d = sdf.parse(selectedDate);
            if (d != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(d);
                int month = cal.get(Calendar.MONTH) + 1;
                int year = cal.get(Calendar.YEAR);
                String monthYearSuffix = String.format(Locale.getDefault(), "/%02d/%02d", month, year % 100);
                String monthName = new SimpleDateFormat("MMMM", Locale.getDefault()).format(d);
                if (tvLabelMonth != null) tvLabelMonth.setText(monthName);

                db.collection("bookings")
                        .whereEqualTo("employeeId", currentEmployeeId)
                        .whereEqualTo("status", "Completed")
                        .get().addOnSuccessListener(queryDocumentSnapshots -> {
                            double[] monthlyTotal = {0};
                            if (queryDocumentSnapshots.isEmpty()) { if (tvEarnMonth != null) tvEarnMonth.setText("RM 0.00"); return; }
                            int totalDocs = queryDocumentSnapshots.size();
                            java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
                            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                                String date = doc.getString("date");
                                if (date != null && date.contains(monthYearSuffix)) {
                                    db.collection("session_payments").document(doc.getId()).get().addOnSuccessListener(payDoc -> {
                                        Double amt = payDoc.getDouble("paymentAmount");
                                        if (amt != null) monthlyTotal[0] += amt;
                                        if (count.incrementAndGet() == totalDocs && isAdded() && tvEarnMonth != null) {
                                            java.text.NumberFormat formatter = java.text.NumberFormat.getNumberInstance(Locale.US);
                                            formatter.setMinimumFractionDigits(2);
                                            formatter.setMaximumFractionDigits(2);
                                            tvEarnMonth.setText("RM " + formatter.format(monthlyTotal[0]));
                                        }
                                    }).addOnFailureListener(e -> {
                                        if (count.incrementAndGet() == totalDocs && isAdded() && tvEarnMonth != null) {
                                            java.text.NumberFormat formatter = java.text.NumberFormat.getNumberInstance(Locale.US);
                                            formatter.setMinimumFractionDigits(2);
                                            formatter.setMaximumFractionDigits(2);
                                            tvEarnMonth.setText("RM " + formatter.format(monthlyTotal[0]));
                                        }
                                    });
                                } else {
                                    if (count.incrementAndGet() == totalDocs && isAdded() && tvEarnMonth != null) {
                                        java.text.NumberFormat formatter = java.text.NumberFormat.getNumberInstance(Locale.US);
                                        formatter.setMinimumFractionDigits(2);
                                        formatter.setMaximumFractionDigits(2);
                                        tvEarnMonth.setText("RM " + formatter.format(monthlyTotal[0]));
                                    }
                                }
                            }
                        });
            }
        } catch (Exception e) { if (tvEarnMonth != null) tvEarnMonth.setText("RM ---"); }
    }

    private void applyFiltersAndSort() {
        filteredBookingItems = new ArrayList<>(allBookingItems);
        if (!statusFilters.isEmpty()) {
            filteredBookingItems = filteredBookingItems.stream().filter(item -> statusFilters.contains(item.booking.getStatus())).collect(Collectors.toList());
        }
        if (!haircutFilters.isEmpty()) {
            filteredBookingItems = filteredBookingItems.stream().filter(item -> haircutFilters.contains(item.booking.getHairstyleName())).collect(Collectors.toList());
        }
        if (!activeKeywords.isEmpty()) {
            filteredBookingItems = filteredBookingItems.stream().filter(item -> {
                String name = item.customerName.toLowerCase();
                String id = item.booking.getBookingId().toLowerCase();
                String style = item.booking.getHairstyleName().toLowerCase();
                String date = item.booking.getDate().toLowerCase();
                String time = item.booking.getTime().toLowerCase();
                String amount = String.format(Locale.getDefault(), "%.2f", item.amount);
                String comment = item.comment != null ? item.comment.toLowerCase() : "";

                for (String kw : activeKeywords) {
                    String lkw = kw.toLowerCase();
                    if (name.contains(lkw) || id.contains(lkw) || style.contains(lkw) || 
                        date.contains(lkw) || time.contains(lkw) || amount.contains(lkw) || 
                        comment.contains(lkw)) return true;
                }
                return false;
            }).collect(Collectors.toList());
        }

        Collections.sort(filteredBookingItems, (i1, i2) -> {
            if (activeSort.equals(getString(R.string.sort_amount))) {
                int res = Double.compare(i1.amount, i2.amount);
                return isAscending ? res : -res;
            }

            int dateRes = 0;
            if (ignoreDate) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
                    dateRes = sdf.parse(i1.booking.getDate()).compareTo(sdf.parse(i2.booking.getDate()));
                } catch (Exception e) {}
            }
            int primaryRes = isDateAscending ? dateRes : -dateRes;
            if (primaryRes != 0) return primaryRes;

            int secondaryRes = 0;
            if (activeSort.equals(getString(R.string.sort_time))) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                    secondaryRes = sdf.parse(i1.booking.getTime()).compareTo(sdf.parse(i2.booking.getTime()));
                } catch (Exception e) { secondaryRes = i1.booking.getCreatedAt().compareTo(i2.booking.getCreatedAt()); }
            } else if (activeSort.equals(getString(R.string.sort_name))) {
                secondaryRes = i1.customerName.compareTo(i2.customerName);
            } else if (activeSort.equals("ID")) {
                secondaryRes = i1.booking.getBookingId().compareTo(i2.booking.getBookingId());
            }
            return isAscending ? secondaryRes : -secondaryRes;
        });
        updateRecyclerView();
    }

    private void updateRecyclerView() {
        if (tvNoBookingsInfo != null) tvNoBookingsInfo.setVisibility(filteredBookingItems.isEmpty() ? View.VISIBLE : View.GONE);
        if (rvBookings.getAdapter() == null) {
            EmployeeBookingAdapter adapter = new EmployeeBookingAdapter(getContext(), filteredBookingItems, new EmployeeBookingAdapter.OnBookingActionListener() {
                @Override public void onCancelBooking(Booking booking) { showCancelConfirmation(booking); }
                @Override public void onEditBooking(Booking booking) {
                    android.content.Intent intent = new android.content.Intent(getActivity(), BookingActivity.class);
                    intent.putExtra("EDIT_BOOKING_ID", booking.getBookingId());
                    intent.putExtra("IS_EMPLOYEE_EDIT", true);
                    startActivity(intent);
                }
            });
            rvBookings.setAdapter(adapter);
        } else {
            ((EmployeeBookingAdapter) rvBookings.getAdapter()).updateData(filteredBookingItems);
        }
        runLayoutAnimation(rvBookings);
    }

    private void runLayoutAnimation(final RecyclerView recyclerView) {
        final android.view.animation.LayoutAnimationController controller = android.view.animation.AnimationUtils.loadLayoutAnimation(recyclerView.getContext(), R.anim.layout_animation_pop_up);
        recyclerView.setLayoutAnimation(controller);
        if (recyclerView.getAdapter() != null) recyclerView.getAdapter().notifyDataSetChanged();
        recyclerView.scheduleLayoutAnimation();
    }

    private void showCancelConfirmation(Booking booking) {
        if (getActivity() instanceof MainActivityEmployee) {
             ((MainActivityEmployee) getActivity()).showNoShowDialog(() -> {
                 db.collection("bookings").document(booking.getBookingId()).update(
                                 "status", "Cancelled",
                                 "updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp()
                         )
                         .addOnSuccessListener(aVoid -> { if (isAdded()) Toast.makeText(getContext(), "Booking Cancelled", Toast.LENGTH_SHORT).show(); });
             });
        }
    }

    private void setupSearchLogic(View view) {
        view.findViewById(R.id.mcv_search_container).setOnClickListener(v -> {
            etSearch.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
        });
        view.findViewById(R.id.btn_filter_sort).setOnClickListener(v -> showOverlay(true));
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                String text = etSearch.getText().toString().trim();
                if (!text.isEmpty() && !activeKeywords.contains(text.toLowerCase())) {
                    activeKeywords.add(text);
                    applyFiltersAndSort(); rebuildFilterBar();
                }
                etSearch.setText(""); hideKeyboard(); return true;
            }
            return false;
        });

        btnReset.setOnClickListener(v -> animateOutAllChips(() -> {
            statusFilters.clear(); haircutFilters.clear(); activeKeywords.clear();
            activeSort = getString(R.string.sort_time); isAscending = false; isDateAscending = false; ignoreDate = false;
            existingTags.clear(); etSearch.setText("");
            if (sortDropdown != null) sortDropdown.setText(getString(R.string.sort_time), false);
            for (Chip chip : overlayFilterChips) { chip.setChecked(false); resetChipStyle(chip); }
            applyFiltersAndSort(); fetchBookings(); rebuildFilterBar(); hideKeyboard();
        }));
    }

    private void setupOverlayLogic(View view) {
        if (sortDropdown != null) {
            String[] sortOptions = {getString(R.string.sort_time), "ID", getString(R.string.sort_amount), getString(R.string.sort_name)};
            sortDropdown.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, sortOptions));
            sortDropdown.setText(getString(R.string.sort_time), false);
            sortDropdown.setOnItemClickListener((parent, v, position, id) -> {
                activeSort = sortOptions[position]; applyFiltersAndSort(); rebuildFilterBar();
            });
        }

        if (overlayContainer != null) {
            overlayContainer.setOnClickListener(v -> {
                if (getActivity() != null) getActivity().getOnBackPressedDispatcher().onBackPressed();
            });
            View mcvFilter = overlayContainer.findViewById(R.id.mcv_filter_dialog);
            if (mcvFilter != null) {
                mcvFilter.setOnClickListener(v -> {
                    // Click inside the filter card should not close it or trigger anything else
                });
            }
        }
        if (getActivity() != null) {
            View btnApply = getActivity().findViewById(R.id.btn_apply_filters);
            if (btnApply != null) btnApply.setOnClickListener(v -> {
                statusFilters.clear(); haircutFilters.clear();
                ChipGroup cgS = getActivity().findViewById(R.id.cg_filter_status);
                ChipGroup cgH = getActivity().findViewById(R.id.cg_filter_hair);
                if (cgS != null) for (int i = 0; i < cgS.getChildCount(); i++) { Chip c = (Chip) cgS.getChildAt(i); if (c.isChecked()) statusFilters.add(c.getText().toString()); }
                if (cgH != null) for (int i = 0; i < cgH.getChildCount(); i++) { Chip c = (Chip) cgH.getChildAt(i); if (c.isChecked()) haircutFilters.add(c.getText().toString()); }
                applyFiltersAndSort(); rebuildFilterBar(); showOverlay(false);
            });
        }

        if (callCustomerOverlay != null) callCustomerOverlay.setOnClickListener(v -> showCallOverlay(false, ""));
        if (btnCallNow != null) btnCallNow.setOnClickListener(v -> {
            if (!pendingPhone.isEmpty()) {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_DIAL);
                intent.setData(android.net.Uri.parse("tel:" + pendingPhone));
                startActivity(intent); showCallOverlay(false, "");
            }
        });
    }

    public void showCallCustomerDialog(String phone) {
        if (phone == null || phone.isEmpty()) { Toast.makeText(getContext(), "Phone not available", Toast.LENGTH_SHORT).show(); return; }
        showCallOverlay(true, phone);
    }

    private void showCallOverlay(boolean show, String phone) {
        if (show) {
            pendingPhone = phone;
            String formatted = phone;
            if (phone.startsWith("0")) formatted = "+60 " + phone.substring(1, 3) + "-" + phone.substring(3, 7) + " " + phone.substring(7);
            if (tvPhoneDisplay != null) tvPhoneDisplay.setText(formatted);
            callCustomerOverlay.setVisibility(View.VISIBLE);
            callCustomerOverlay.setAlpha(0f);
            callCustomerOverlay.animate().alpha(1f).setDuration(200).start();
            if (mcvCallDialogEmp != null) {
                mcvCallDialogEmp.post(() -> {
                    mcvCallDialogEmp.setTranslationY(mcvCallDialogEmp.getHeight());
                    mcvCallDialogEmp.animate().translationY(0).setDuration(300).start();
                });
            }
        } else if (callCustomerOverlay != null) {
            if (mcvCallDialogEmp != null) mcvCallDialogEmp.animate().translationY(mcvCallDialogEmp.getHeight()).setDuration(200).start();
            callCustomerOverlay.animate().alpha(0f).setDuration(200).withEndAction(() -> callCustomerOverlay.setVisibility(View.GONE)).start();
        }
    }

    private void setupToggleChip(Chip chip) {
        int primary = ContextCompat.getColor(requireContext(), R.color.primary_color);
        int white = ContextCompat.getColor(requireContext(), R.color.white);
        chip.setOnCheckedChangeListener((bv, isChecked) -> {
            chip.setChipBackgroundColor(ColorStateList.valueOf(isChecked ? primary : white));
            chip.setTextColor(isChecked ? white : primary);
        });
    }

    private void resetChipStyle(Chip chip) {
        chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white)));
        chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_color));
    }

    public boolean isOverlayVisible() {
        return (overlayContainer != null && overlayContainer.getVisibility() == View.VISIBLE) ||
                (callCustomerOverlay != null && callCustomerOverlay.getVisibility() == View.VISIBLE);
    }

    public void hideAllOverlays() {
        if (overlayContainer != null && overlayContainer.getVisibility() == View.VISIBLE) showOverlay(false);
        if (callCustomerOverlay != null && callCustomerOverlay.getVisibility() == View.VISIBLE) showCallOverlay(false, "");
    }

    public void showOverlay(boolean show) {
        if (overlayContainer == null) return;
        if (show) {
            overlayContainer.setVisibility(View.VISIBLE); overlayContainer.setAlpha(0f);
            overlayContainer.animate().alpha(1f).setDuration(200).setListener(null).start();
            if (mcvFilterDialog != null) {
                mcvFilterDialog.setScaleX(0f); mcvFilterDialog.setScaleY(0f);
                mcvFilterDialog.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
            }
        } else {
            if (mcvFilterDialog != null) mcvFilterDialog.animate().scaleX(0f).scaleY(0f).setDuration(200).start();
            overlayContainer.animate().alpha(0f).setDuration(200).setListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { overlayContainer.setVisibility(View.GONE); }
            }).start();
        }
    }

    private void rebuildFilterBar() {
        cgFilterTags.removeAllViews();
        boolean hasContent = false;
        Set<String> newTags = new HashSet<>();

        List<Pair<String, TagType>> sortList = new ArrayList<>();
        if (activeSort != null) {
            String sortText = activeSort + (isAscending ? " (asc)" : " (desc)");
            sortList.add(new Pair<>(sortText, TagType.SORT));
            newTags.add("SORT:" + sortText);
        }
        if (ignoreDate) {
            String dateSortText = "Date " + (isDateAscending ? "(asc)" : "(desc)");
            sortList.add(new Pair<>(dateSortText, TagType.DATE_SORT));
            newTags.add("DATE_SORT:" + dateSortText);
        }
        if (!sortList.isEmpty()) { addCategorySection(getString(R.string.label_sort), sortList, false); hasContent = true; }

        List<Pair<String, TagType>> dateSection = new ArrayList<>();
        if (ignoreDate) { dateSection.add(new Pair<>("Showing All Dates", TagType.DATE)); newTags.add("DATE:Showing All Dates"); }
        else { dateSection.add(new Pair<>(selectedDate, TagType.DATE)); newTags.add("DATE:" + selectedDate); }
        if (hasContent) addDivider();
        addCategorySection(getString(R.string.label_date), dateSection, !ignoreDate);
        hasContent = true;

        if (!statusFilters.isEmpty()) {
            if (hasContent) addDivider();
            List<Pair<String, TagType>> list = statusFilters.stream().map(s -> new Pair<>(s, TagType.FILTER)).collect(Collectors.toList());
            addCategorySection(getString(R.string.filter_status) + ":", list, true);
            for (String s : statusFilters) newTags.add("FILTER:" + s);
            hasContent = true;
        }
        if (!haircutFilters.isEmpty()) {
            if (hasContent) addDivider();
            List<Pair<String, TagType>> list = haircutFilters.stream().map(s -> new Pair<>(s, TagType.FILTER)).collect(Collectors.toList());
            addCategorySection(getString(R.string.filter_haircut) + ":", list, true);
            for (String s : haircutFilters) newTags.add("FILTER:" + s);
            hasContent = true;
        }
        if (!activeKeywords.isEmpty()) {
            if (hasContent) addDivider();
            List<Pair<String, TagType>> list = activeKeywords.stream().map(s -> new Pair<>(s, TagType.KEYWORD)).collect(Collectors.toList());
            addCategorySection(getString(R.string.label_keyword), list, true);
            for (String s : activeKeywords) newTags.add("KEYWORD:" + s);
            hasContent = true;
        }
        existingTags.retainAll(newTags); existingTags.addAll(newTags); updateFilterUI(hasContent);
    }

    private void addDivider() {
        View divider = new View(requireContext());
        divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.trans_white));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1));
        params.setMargins(0, dpToPx(4), 0, dpToPx(8));
        divider.setLayoutParams(params);
        cgFilterTags.addView(divider);
    }

    private static class Pair<A, B> {
        public final A first; public final B second;
        public Pair(A first, B second) { this.first = first; this.second = second; }
    }

    private void addCategorySection(String label, List<Pair<String, TagType>> items, boolean removable) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(android.view.Gravity.TOP);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dpToPx(4)); row.setLayoutParams(rowParams);
        TextView tvLabel = new TextView(requireContext());
        tvLabel.setText(label); tvLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        tvLabel.setTextSize(14); tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setMinWidth(dpToPx(70)); tvLabel.setPadding(0, dpToPx(10), dpToPx(8), 0);
        row.addView(tvLabel);
        ChipGroup group = new ChipGroup(requireContext());
        group.setChipSpacingHorizontal(dpToPx(8)); group.setChipSpacingVertical(dpToPx(4));
        group.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        for (Pair<String, TagType> item : items) group.addView(createTagChip(item.first, item.second, removable, !existingTags.contains(item.second.name() + ":" + item.first)));
        row.addView(group); cgFilterTags.addView(row);
    }

    private Chip createTagChip(String text, TagType type, boolean removable, boolean animate) {
        Chip chip = new Chip(requireContext()); chip.setText(text); chip.setCloseIconVisible(removable);
        chip.setChipBackgroundColorResource(R.color.white); chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_color));
        chip.setCloseIconTintResource(R.color.primary_color); chip.setChipStartPadding(dpToPx(8)); chip.setChipEndPadding(dpToPx(8));
        
        if (type == TagType.SORT) {
            chip.setChipIconVisible(true); chip.setChipIcon(ContextCompat.getDrawable(requireContext(), isAscending ? R.drawable.ic_sort_asc : R.drawable.ic_sort_desc));
            chip.setChipIconTint(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary_color)));
            chip.setOnClickListener(v -> { isAscending = !isAscending; applyFiltersAndSort(); rebuildFilterBar(); });
        } else if (type == TagType.DATE_SORT) {
            chip.setChipIconVisible(true); chip.setChipIcon(ContextCompat.getDrawable(requireContext(), isDateAscending ? R.drawable.ic_sort_asc : R.drawable.ic_sort_desc));
            chip.setChipIconTint(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary_color)));
            chip.setOnClickListener(v -> { isDateAscending = !isDateAscending; applyFiltersAndSort(); rebuildFilterBar(); });
        } else if (type == TagType.DATE) {
            chip.setChipIconVisible(true); chip.setChipIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_calendar));
            chip.setChipIconTint(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary_color)));
            if (text.equals("Showing All Dates")) {
                chip.setCloseIconVisible(true);
                chip.setOnClickListener(v -> { ignoreDate = false; fetchBookings(); rebuildFilterBar(); });
                chip.setOnCloseIconClickListener(v -> { ignoreDate = false; fetchBookings(); rebuildFilterBar(); });
            } else if (removable) {
                chip.setOnClickListener(v -> animateOut(chip, () -> removeTag(text, type)));
                chip.setOnCloseIconClickListener(v -> animateOut(chip, () -> removeTag(text, type)));
            }
        } else if (removable) {
            chip.setOnClickListener(v -> animateOut(chip, () -> removeTag(text, type)));
            chip.setOnCloseIconClickListener(v -> animateOut(chip, () -> removeTag(text, type)));
        }
        if (animate) { chip.setScaleX(0f); chip.setScaleY(0f); chip.animate().scaleX(1f).scaleY(1f).setDuration(200).start(); }
        return chip;
    }

    private void animateOut(View view, Runnable onEnd) {
        view.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(200).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { onEnd.run(); }
        }).start();
    }

    private void animateOutAllChips(Runnable onEnd) {
        List<View> chips = new ArrayList<>();
        getAllChips(cgFilterTags, chips);
        if (chips.isEmpty()) { onEnd.run(); return; }
        int count = chips.size(); final int[] finished = {0};
        for (View chip : chips) {
            chip.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(200).setListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { finished[0]++; if (finished[0] == count) onEnd.run(); }
            }).start();
        }
    }

    private void getAllChips(ViewGroup parent, List<View> chips) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof Chip) chips.add(child);
            else if (child instanceof ViewGroup) getAllChips((ViewGroup) child, chips);
        }
    }

    private void removeTag(String text, TagType type) {
        if (type == TagType.FILTER) {
            statusFilters.remove(text); haircutFilters.remove(text);
            for (Chip c : overlayFilterChips) if (c != null && c.getText().toString().equals(text)) { c.setChecked(false); resetChipStyle(c); break; }
        } else if (type == TagType.KEYWORD) activeKeywords.remove(text);
        else if (type == TagType.DATE) { ignoreDate = (text.equals("Showing All Dates") ? false : true); fetchBookings(); }
        applyFiltersAndSort(); rebuildFilterBar();
    }

    private void updateFilterUI(boolean hasContent) {
        if (hasContent) {
            if (filterBarContainer.getVisibility() == View.GONE) animateFilterBar(true);
            boolean isDefaultSort = activeSort != null && activeSort.equals(getString(R.string.sort_time)) && !isAscending && !isDateAscending;
            btnReset.setVisibility((!statusFilters.isEmpty() || !haircutFilters.isEmpty() || !activeKeywords.isEmpty() || !isDefaultSort || ignoreDate) ? View.VISIBLE : View.GONE);
        } else {
            animateFilterBar(false); btnReset.setVisibility(View.GONE);
        }
    }

    private int dpToPx(int dp) { return (int) (dp * getResources().getDisplayMetrics().density); }

    private void animateFilterBar(boolean show) {
        if (show) {
            filterBarContainer.setVisibility(View.VISIBLE); filterBarContainer.setAlpha(0f); filterBarContainer.setTranslationY(-20f);
            filterBarContainer.animate().alpha(1f).translationY(0f).setDuration(300).setListener(null).start();
        } else {
            filterBarContainer.animate().alpha(0f).translationY(-20f).setDuration(300).setListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { filterBarContainer.setVisibility(View.GONE); }
            }).start();
        }
    }

    private void hideKeyboard() {
        if (getActivity() != null && getActivity().getCurrentFocus() != null) {
            ((InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);
        }
    }
}
