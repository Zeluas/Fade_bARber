package com.zejyv.azizul.uitm.fadebarber;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * EmployeeBookFragment: Manages the history and schedule of bookings for employees.
 */
public class EmployeeBookFragment extends Fragment {

    private enum TagType { FILTER, KEYWORD, SORT }

    private EditText etSearch;
    private ViewGroup cgFilterTags; 
    private View filterBarContainer;
    private MaterialButton btnReset;
    private RecyclerView rvBookings;
    private TextView tvNoBookingsInfo;
    
    // Filter Overlay Components
    private View overlayContainer;
    private final List<Chip> overlayFilterChips = new ArrayList<>();
    private AutoCompleteTextView sortDropdown;

    // Filter State
    private final List<String> statusHaircutFilters = new ArrayList<>();
    private final List<String> activeKeywords = new ArrayList<>();
    private String activeSort = null; // Will set in initialize
    private boolean isAscending = true;

    // To prevent re-animating existing tags
    private final Set<String> existingTags = new HashSet<>();

    private CalendarView calendarView;
    private TextView tvEarnToday, tvEarnMonth, tvLabelMonth;

    // Firebase
    private FirebaseFirestore db;
    private String currentEmployeeId;

    // Data
    private List<Booking> allBookings = new ArrayList<>();
    private List<Booking> filteredBookings = new ArrayList<>();
    private java.util.Map<String, String> customerNames = new java.util.HashMap<>();
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

        initializeViews(view);
        setupCalendarLogic();
        setupSearchLogic(view);
        setupOverlayLogic(view);
        
        // Default to today
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
        selectedDate = sdf.format(new Date());
        fetchBookings();
        
        // Initial build
        rebuildFilterBar();
    }

    private void initializeViews(View view) {
        etSearch = view.findViewById(R.id.et_search_bookings);
        cgFilterTags = view.findViewById(R.id.cg_filter_tags);
        filterBarContainer = view.findViewById(R.id.ll_filter_bar_container);
        btnReset = view.findViewById(R.id.btn_reset_filters);
        
        calendarView = view.findViewById(R.id.calendar_view_employee);
        tvEarnToday = view.findViewById(R.id.tv_earn_today_book);
        tvEarnMonth = view.findViewById(R.id.tv_earn_month_book);
        tvLabelMonth = view.findViewById(R.id.tv_label_month);

        overlayContainer = getActivity().findViewById(R.id.layout_filter_overlay_container);
        rvBookings = view.findViewById(R.id.rv_all_bookings);
        rvBookings.setLayoutManager(new LinearLayoutManager(getContext()));
        tvNoBookingsInfo = view.findViewById(R.id.tv_no_bookings_info);

        // Overlay specific views (Found in Activity now)
        overlayFilterChips.clear();
        overlayFilterChips.add(getActivity().findViewById(R.id.chip_filter_pending));
        overlayFilterChips.add(getActivity().findViewById(R.id.chip_filter_completed));
        overlayFilterChips.add(getActivity().findViewById(R.id.chip_filter_cancelled));
        overlayFilterChips.add(getActivity().findViewById(R.id.chip_filter_starting));
        overlayFilterChips.add(getActivity().findViewById(R.id.chip_filter_paying));
        overlayFilterChips.add(getActivity().findViewById(R.id.chip_filter_rating));
        
        overlayFilterChips.add(getActivity().findViewById(R.id.chip_filter_brazilian));
        overlayFilterChips.add(getActivity().findViewById(R.id.chip_filter_bird));
        overlayFilterChips.add(getActivity().findViewById(R.id.chip_filter_side_sweep));
        overlayFilterChips.add(getActivity().findViewById(R.id.chip_filter_wolf));
        overlayFilterChips.add(getActivity().findViewById(R.id.chip_filter_afro));
        overlayFilterChips.add(getActivity().findViewById(R.id.chip_filter_curtain));
        overlayFilterChips.add(getActivity().findViewById(R.id.chip_filter_disconnect));
        overlayFilterChips.add(getActivity().findViewById(R.id.chip_filter_afro_tapper));
        overlayFilterChips.add(getActivity().findViewById(R.id.chip_filter_punk));
        
        sortDropdown = getActivity().findViewById(R.id.actv_sort_dropdown);
    }

    private void setupCalendarLogic() {
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, dayOfMonth);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            selectedDate = sdf.format(calendar.getTime());
            fetchBookings();
        });
    }

    private void fetchBookings() {
        if (currentEmployeeId == null) return;

        db.collection("bookings")
                .whereEqualTo("employeeId", currentEmployeeId)
                .whereEqualTo("date", selectedDate)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        if (isAdded()) Toast.makeText(getContext(), "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (value != null) {
                        allBookings.clear();
                        customerNames.clear();
                        for (QueryDocumentSnapshot doc : value) {
                            Booking booking = doc.toObject(Booking.class);
                            allBookings.add(booking);
                            
                            // Fetch customer name for filtering
                            db.collection("customers").document(booking.getCustomerId()).get()
                                    .addOnSuccessListener(customerDoc -> {
                                        if (customerDoc.exists()) {
                                            customerNames.put(booking.getBookingId(), customerDoc.getString("name"));
                                            applyFiltersAndSort(); // Re-apply to include name keyword match
                                        }
                                    });
                        }
                        applyFiltersAndSort();
                        calculateEarnings();
                    }
                });
    }

    private void calculateEarnings() {
        if (allBookings.isEmpty()) {
            tvEarnToday.setText("RM 0.00");
        }

        // 1. Calculate Today (Selected Date)
        List<String> todayIds = new ArrayList<>();
        for (Booking b : allBookings) {
            if ("Completed".equalsIgnoreCase(b.getStatus())) todayIds.add(b.getBookingId());
        }
        fetchSum(todayIds, tvEarnToday);

        // 2. Calculate Monthly Cumulative
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());
            Date d = sdf.parse(selectedDate);
            if (d != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(d);
                int month = cal.get(Calendar.MONTH) + 1;
                int year = cal.get(Calendar.YEAR);
                String monthYear = String.format(Locale.getDefault(), "%02d/%02d", month, year % 100);
                
                String monthName = new SimpleDateFormat("MMMM", Locale.getDefault()).format(d);
                tvLabelMonth.setText(monthName);

                db.collection("bookings")
                        .whereEqualTo("employeeId", currentEmployeeId)
                        .whereEqualTo("status", "Completed")
                        .get().addOnSuccessListener(queryDocumentSnapshots -> {
                            List<String> monthIds = new ArrayList<>();
                            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                                String date = doc.getString("date");
                                if (date != null && date.endsWith("/" + monthYear.split("/")[1]) && date.contains("/" + monthYear.split("/")[0] + "/")) {
                                    monthIds.add(doc.getId());
                                }
                            }
                            fetchSum(monthIds, tvEarnMonth);
                        });
            }
        } catch (Exception e) {
            tvEarnMonth.setText("RM ---");
        }
    }

    private void fetchSum(List<String> ids, TextView target) {
        if (ids.isEmpty()) {
            target.setText("RM 0.00");
            return;
        }
        double[] total = {0};
        int[] count = {0};
        for (String id : ids) {
            db.collection("session_payments").document(id).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    Double amount = doc.getDouble("paymentAmount");
                    if (amount != null) total[0] += amount;
                }
                count[0]++;
                if (count[0] == ids.size()) {
                    if (isAdded()) target.setText(String.format(Locale.getDefault(), "RM %.2f", total[0]));
                }
            }).addOnFailureListener(e -> {
                count[0]++;
                if (count[0] == ids.size()) {
                    if (isAdded()) target.setText(String.format(Locale.getDefault(), "RM %.2f", total[0]));
                }
            });
        }
    }

    private void applyFiltersAndSort() {
        filteredBookings = new ArrayList<>(allBookings);

        // Filter by Status or Haircut from Chips
        if (!statusHaircutFilters.isEmpty()) {
            filteredBookings = filteredBookings.stream().filter(b -> 
                statusHaircutFilters.contains(b.getStatus()) || 
                statusHaircutFilters.contains(b.getHairstyleName())
            ).collect(Collectors.toList());
        }

        // Filter by Keyword (matching Customer Name or ID or Style)
        if (!activeKeywords.isEmpty()) {
            filteredBookings = filteredBookings.stream().filter(b -> {
                String name = customerNames.get(b.getBookingId());
                String id = b.getBookingId().toLowerCase();
                String style = b.getHairstyleName().toLowerCase();
                String customer = (name != null) ? name.toLowerCase() : "";
                
                // OR matching: if item matches ANY of the keyword tags
                for (String kw : activeKeywords) {
                    String lowerKw = kw.toLowerCase();
                    if (id.contains(lowerKw) || style.contains(lowerKw) || customer.contains(lowerKw)) {
                        return true;
                    }
                }
                return false;
            }).collect(Collectors.toList());
        }

        // Sort
        Collections.sort(filteredBookings, (b1, b2) -> {
            int result = 0;
            if (activeSort.equals("ID")) {
                result = b1.getBookingId().compareTo(b2.getBookingId());
            } else if (activeSort.equals(getString(R.string.sort_time))) {
                result = b1.getTime().compareTo(b2.getTime());
            } else if (activeSort.equals(getString(R.string.sort_name))) {
                String name1 = customerNames.get(b1.getBookingId());
                String name2 = customerNames.get(b2.getBookingId());
                if (name1 == null) name1 = "";
                if (name2 == null) name2 = "";
                result = name1.compareTo(name2);
            }
            return isAscending ? result : -result;
        });

        updateRecyclerView();
    }

    private void updateRecyclerView() {
        if (tvNoBookingsInfo != null) {
            tvNoBookingsInfo.setVisibility(filteredBookings.isEmpty() ? View.VISIBLE : View.GONE);
        }

        if (rvBookings.getAdapter() == null) {
            EmployeeBookingAdapter adapter = new EmployeeBookingAdapter(getContext(), filteredBookings, new EmployeeBookingAdapter.OnBookingActionListener() {
                @Override
                public void onCancelBooking(Booking booking) {
                    showCancelConfirmation(booking);
                }

                @Override
                public void onEditBooking(Booking booking) {
                    android.content.Intent intent = new android.content.Intent(getActivity(), BookingActivity.class);
                    intent.putExtra("EDIT_BOOKING_ID", booking.getBookingId());
                    intent.putExtra("IS_EMPLOYEE_EDIT", true);
                    startActivity(intent);
                }
            });
            rvBookings.setAdapter(adapter);
        } else {
            ((EmployeeBookingAdapter) rvBookings.getAdapter()).updateData(filteredBookings);
        }
        
        // Apply Pop-up animation to the list
        runLayoutAnimation(rvBookings);
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

    private void showCancelConfirmation(Booking booking) {
        if (getActivity() instanceof MainActivityEmployee) {
             ((MainActivityEmployee) getActivity()).showNoShowDialog(() -> {
                 db.collection("bookings").document(booking.getBookingId())
                         .update("status", "Cancelled")
                         .addOnSuccessListener(aVoid -> {
                             if (isAdded()) Toast.makeText(getContext(), "Booking Cancelled", Toast.LENGTH_SHORT).show();
                         })
                         .addOnFailureListener(e -> {
                             if (isAdded()) Toast.makeText(getContext(), "Failed to cancel", Toast.LENGTH_SHORT).show();
                         });
             });
        }
    }

    private void setupSearchLogic(View view) {
        View searchContainer = view.findViewById(R.id.mcv_search_container);
        MaterialButton btnFilter = view.findViewById(R.id.btn_filter_sort);

        searchContainer.setOnClickListener(v -> {
            etSearch.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
        });

        btnFilter.setOnClickListener(v -> showOverlay(true));

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                String text = etSearch.getText().toString().trim();
                if (!text.isEmpty()) {
                    if (!activeKeywords.contains(text.toLowerCase())) {
                        activeKeywords.add(text);
                        applyFiltersAndSort();
                        rebuildFilterBar();
                    }
                    etSearch.setText("");
                }
                hideKeyboard();
                return true;
            }
            return false;
        });

        btnReset.setOnClickListener(v -> animateOutAllChips(() -> {
            statusHaircutFilters.clear();
            activeKeywords.clear();
            activeSort = getString(R.string.sort_time);
            isAscending = true;
            existingTags.clear();
            etSearch.setText("");
            
            for (Chip chip : overlayFilterChips) {
                if (chip != null) {
                    chip.setChecked(false);
                    resetChipStyle(chip);
                }
            }
            applyFiltersAndSort();
            rebuildFilterBar();
            hideKeyboard();
        }));
    }

    private void setupOverlayLogic(View view) {
        String[] sortOptions = {getString(R.string.sort_time), "ID", getString(R.string.sort_name)};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, sortOptions);
        sortDropdown.setAdapter(adapter);

        sortDropdown.setOnItemClickListener((parent, view1, position, id) -> {
            activeSort = sortOptions[position];
        });

        for (Chip chip : overlayFilterChips) {
            if (chip != null) setupToggleChip(chip);
        }

        overlayContainer.setOnClickListener(v -> showOverlay(false));
        View dialog = getActivity().findViewById(R.id.mcv_filter_dialog);
        if (dialog != null) dialog.setOnClickListener(v -> {});

        getActivity().findViewById(R.id.btn_apply_filters).setOnClickListener(v -> {
            statusHaircutFilters.clear();
            for (Chip chip : overlayFilterChips) {
                if (chip != null && chip.isChecked()) {
                    statusHaircutFilters.add(chip.getText().toString());
                }
            }
            applyFiltersAndSort();
            rebuildFilterBar();
            showOverlay(false);
        });
    }

    private void setupToggleChip(Chip chip) {
        int primaryColor = ContextCompat.getColor(requireContext(), R.color.primary_color);
        int whiteColor = ContextCompat.getColor(requireContext(), R.color.white);

        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                chip.setChipBackgroundColor(ColorStateList.valueOf(primaryColor));
                chip.setTextColor(whiteColor);
            } else {
                chip.setChipBackgroundColor(ColorStateList.valueOf(whiteColor));
                chip.setTextColor(primaryColor);
            }
        });
    }

    private void resetChipStyle(Chip chip) {
        int primaryColor = ContextCompat.getColor(requireContext(), R.color.primary_color);
        int whiteColor = ContextCompat.getColor(requireContext(), R.color.white);
        chip.setChipBackgroundColor(ColorStateList.valueOf(whiteColor));
        chip.setTextColor(primaryColor);
    }

    private void showOverlay(boolean show) {
        if (show) {
            overlayContainer.setVisibility(View.VISIBLE);
            overlayContainer.setAlpha(0f);
            overlayContainer.animate().alpha(1f).setDuration(200).setListener(null).start();
        } else {
            overlayContainer.animate().alpha(0f).setDuration(200).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    overlayContainer.setVisibility(View.GONE);
                }
            }).start();
        }
    }

    private void rebuildFilterBar() {
        cgFilterTags.removeAllViews();
        
        boolean hasContent = false;
        Set<String> newExistingTags = new HashSet<>();

        // Order: Sort, Filter, Keyword
        if (activeSort != null) {
            List<String> list = new ArrayList<>();
            String sortText = activeSort + (isAscending ? " (asc)" : " (desc)");
            list.add(sortText);
            addCategorySection(getString(R.string.label_sort), list, TagType.SORT, false);
            newExistingTags.add("SORT:" + sortText);
            hasContent = true;
        }
        
        if (!statusHaircutFilters.isEmpty()) {
            if (hasContent) addDivider();
            addCategorySection(getString(R.string.label_filter), statusHaircutFilters, TagType.FILTER, true);
            for (String s : statusHaircutFilters) newExistingTags.add("FILTER:" + s);
            hasContent = true;
        }
        
        if (!activeKeywords.isEmpty()) {
            if (hasContent) addDivider();
            addCategorySection(getString(R.string.label_keyword), activeKeywords, TagType.KEYWORD, true);
            for (String s : activeKeywords) newExistingTags.add("KEYWORD:" + s);
            hasContent = true;
        }

        existingTags.retainAll(newExistingTags);
        existingTags.addAll(newExistingTags);

        updateFilterUI(hasContent);
    }

    private void addDivider() {
        View divider = new View(requireContext());
        divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.trans_white));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1));
        params.setMargins(0, dpToPx(4), 0, dpToPx(8));
        divider.setLayoutParams(params);
        cgFilterTags.addView(divider);
    }

    private void addCategorySection(String label, List<String> items, TagType type, boolean removable) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.TOP);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dpToPx(4));
        row.setLayoutParams(rowParams);

        TextView tvLabel = new TextView(requireContext());
        tvLabel.setText(label);
        tvLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        tvLabel.setTextSize(14); 
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setAlpha(1.0f);
        tvLabel.setMinWidth(dpToPx(70)); 
        tvLabel.setPadding(0, dpToPx(10), dpToPx(8), 0);
        row.addView(tvLabel);

        ChipGroup group = new ChipGroup(requireContext());
        group.setChipSpacingHorizontal(dpToPx(8));
        group.setChipSpacingVertical(dpToPx(4));
        group.setSingleLine(false);
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        group.setLayoutParams(groupParams);

        for (String text : items) {
            String tagKey = type.name() + ":" + text;
            boolean isNew = !existingTags.contains(tagKey);
            group.addView(createTagChip(text, type, removable, isNew));
        }
        row.addView(group);

        cgFilterTags.addView(row);
    }

    private Chip createTagChip(String text, TagType type, boolean removable, boolean animate) {
        Chip chip = new Chip(requireContext());
        chip.setText(text);
        chip.setCloseIconVisible(removable);
        chip.setChipBackgroundColorResource(R.color.white);
        chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_color));
        chip.setCloseIconTintResource(R.color.primary_color);
        chip.setChipStartPadding(dpToPx(8));
        chip.setChipEndPadding(dpToPx(8));
        
        if (type == TagType.SORT) {
            chip.setChipIconVisible(true);
            // Using build-safe approach: switch between rotated icons
            chip.setChipIcon(ContextCompat.getDrawable(requireContext(), 
                    isAscending ? R.drawable.ic_sort_asc : R.drawable.ic_sort_desc));
            chip.setChipIconTint(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary_color)));
            
            chip.setOnClickListener(v -> {
                isAscending = !isAscending;
                rebuildFilterBar();
            });
        } else if (removable) {
            chip.setOnClickListener(v -> animateOut(chip, () -> removeTag(text, type)));
            chip.setOnCloseIconClickListener(v -> animateOut(chip, () -> removeTag(text, type)));
        }
        
        if (animate) {
            chip.setScaleX(0f);
            chip.setScaleY(0f);
            chip.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        }

        return chip;
    }

    private void animateOut(View view, Runnable onEnd) {
        view.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(200).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onEnd.run();
            }
        }).start();
    }

    private void animateOutAllChips(Runnable onEnd) {
        List<View> chips = new ArrayList<>();
        getAllChips(cgFilterTags, chips);
        
        if (chips.isEmpty()) {
            onEnd.run();
            return;
        }

        int count = chips.size();
        final int[] finished = {0};

        for (View chip : chips) {
            chip.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(200).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    finished[0]++;
                    if (finished[0] == count) {
                        onEnd.run();
                    }
                }
            }).start();
        }
    }

    private void getAllChips(ViewGroup parent, List<View> chips) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof Chip) {
                chips.add(child);
            } else if (child instanceof ViewGroup) {
                getAllChips((ViewGroup) child, chips);
            }
        }
    }

    private void removeTag(String text, TagType type) {
        switch (type) {
            case FILTER:
                statusHaircutFilters.remove(text);
                for (Chip overlayChip : overlayFilterChips) {
                    if (overlayChip != null && overlayChip.getText().toString().equals(text)) {
                        overlayChip.setChecked(false);
                        resetChipStyle(overlayChip);
                        break;
                    }
                }
                break;
            case KEYWORD:
                activeKeywords.remove(text);
                break;
            case SORT:
                return;
        }
        applyFiltersAndSort();
        rebuildFilterBar();
    }

    private void updateFilterUI(boolean hasContent) {
        if (hasContent) {
            if (filterBarContainer.getVisibility() == View.GONE) {
                animateFilterBar(true);
            }
            boolean canReset = !statusHaircutFilters.isEmpty() || !activeKeywords.isEmpty() || (activeSort != null && !activeSort.equals(getString(R.string.sort_time)));
            btnReset.setVisibility(canReset ? View.VISIBLE : View.GONE);
        } else {
            animateFilterBar(false);
            btnReset.setVisibility(View.GONE);
        }
    }

    private int dpToPx(int dp) {
        if (!isAdded()) return dp * 3;
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void animateFilterBar(boolean show) {
        if (show) {
            filterBarContainer.setVisibility(View.VISIBLE);
            filterBarContainer.setAlpha(0f);
            filterBarContainer.setTranslationY(-20f);
            filterBarContainer.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .setListener(null)
                    .start();
        } else {
            filterBarContainer.animate()
                    .alpha(0f)
                    .translationY(-20f)
                    .setDuration(300)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            filterBarContainer.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }
    }

    private void hideKeyboard() {
        if (getActivity() != null && getActivity().getCurrentFocus() != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);
        }
    }
}
