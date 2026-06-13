package com.zejyv.azizul.uitm.fadebarber;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    
    // Filter Overlay Components
    private View overlayContainer;
    private final List<Chip> overlayFilterChips = new ArrayList<>();
    private AutoCompleteTextView sortDropdown;

    // Filter State
    private final List<String> statusHaircutFilters = new ArrayList<>();
    private String activeKeyword = null;
    private String activeSort = "ID"; 
    private boolean isAscending = true;

    // To prevent re-animating existing tags
    private final Set<String> existingTags = new HashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_employee_book, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupSearchLogic(view);
        setupOverlayLogic(view);
        setupDummyData();
        
        // Initial build
        rebuildFilterBar();
    }

    private void initializeViews(View view) {
        etSearch = view.findViewById(R.id.et_search_bookings);
        cgFilterTags = view.findViewById(R.id.cg_filter_tags);
        filterBarContainer = view.findViewById(R.id.ll_filter_bar_container);
        btnReset = view.findViewById(R.id.btn_reset_filters);
        
        overlayContainer = view.findViewById(R.id.layout_overlay_container);
        rvBookings = view.findViewById(R.id.rv_all_bookings);

        // Overlay specific views
        overlayFilterChips.clear();
        overlayFilterChips.add(view.findViewById(R.id.chip_filter_pending));
        overlayFilterChips.add(view.findViewById(R.id.chip_filter_completed));
        overlayFilterChips.add(view.findViewById(R.id.chip_filter_brazilian));
        overlayFilterChips.add(view.findViewById(R.id.chip_filter_bird));
        overlayFilterChips.add(view.findViewById(R.id.chip_filter_side_sweep));
        overlayFilterChips.add(view.findViewById(R.id.chip_filter_wolf));
        overlayFilterChips.add(view.findViewById(R.id.chip_filter_afro));
        overlayFilterChips.add(view.findViewById(R.id.chip_filter_curtain));
        overlayFilterChips.add(view.findViewById(R.id.chip_filter_disconnect));
        overlayFilterChips.add(view.findViewById(R.id.chip_filter_afro_tapper));
        overlayFilterChips.add(view.findViewById(R.id.chip_filter_punk));
        
        sortDropdown = view.findViewById(R.id.actv_sort_dropdown);
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
                String keyword = etSearch.getText().toString().trim();
                if (!keyword.isEmpty()) {
                    activeKeyword = keyword;
                    rebuildFilterBar();
                    etSearch.setText("");
                    hideKeyboard();
                }
                return true;
            }
            return false;
        });

        btnReset.setOnClickListener(v -> animateOutAllChips(() -> {
            statusHaircutFilters.clear();
            activeKeyword = null;
            activeSort = "ID"; 
            isAscending = true;
            existingTags.clear();
            rebuildFilterBar();
            
            for (Chip chip : overlayFilterChips) {
                if (chip != null) {
                    chip.setChecked(false);
                    resetChipStyle(chip);
                }
            }
        }));
    }

    private void setupOverlayLogic(View view) {
        String[] sortOptions = {"ID", getString(R.string.sort_time), getString(R.string.sort_name)};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, sortOptions);
        sortDropdown.setAdapter(adapter);

        sortDropdown.setOnItemClickListener((parent, view1, position, id) -> {
            activeSort = sortOptions[position];
        });

        for (Chip chip : overlayFilterChips) {
            if (chip != null) setupToggleChip(chip);
        }

        overlayContainer.setOnClickListener(v -> showOverlay(false));
        View dialog = view.findViewById(R.id.mcv_filter_dialog);
        if (dialog != null) dialog.setOnClickListener(v -> {});

        view.findViewById(R.id.btn_apply_filters).setOnClickListener(v -> {
            statusHaircutFilters.clear();
            for (Chip chip : overlayFilterChips) {
                if (chip != null && chip.isChecked()) {
                    statusHaircutFilters.add(chip.getText().toString());
                }
            }
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
        
        if (activeKeyword != null) {
            if (hasContent) addDivider();
            List<String> list = new ArrayList<>();
            list.add(activeKeyword);
            addCategorySection(getString(R.string.label_keyword), list, TagType.KEYWORD, true);
            newExistingTags.add("KEYWORD:" + activeKeyword);
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
                activeKeyword = null;
                break;
            case SORT:
                return;
        }
        rebuildFilterBar();
    }

    private void updateFilterUI(boolean hasContent) {
        if (hasContent) {
            if (filterBarContainer.getVisibility() == View.GONE) {
                animateFilterBar(true);
            }
            boolean canReset = !statusHaircutFilters.isEmpty() || activeKeyword != null || (activeSort != null && !activeSort.equals("ID"));
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

    private void setupDummyData() {
        rvBookings.setLayoutManager(new LinearLayoutManager(getContext()));
        rvBookings.setAdapter(new RecyclerView.Adapter<DummyViewHolder>() {
            @NonNull
            @Override
            public DummyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_employee_booking, parent, false);
                return new DummyViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull DummyViewHolder holder, int position) {
                String[] names = {"Azizul", "Haziq", "Irfan", "Zul", "Ahmad"};
                String[] styles = {getString(R.string.style_fade), getString(R.string.style_side_part), 
                                 getString(R.string.style_buzz_cut), getString(R.string.style_pompadour), 
                                 getString(R.string.style_undercut)};
                holder.tvName.setText(names[position % names.length]);
                holder.tvStyle.setText(styles[position % styles.length]);
                holder.tvId.setText("#FB-" + (1024 + position));
            }

            @Override
            public int getItemCount() {
                return 10;
            }
        });
    }

    static class DummyViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvStyle, tvId;
        DummyViewHolder(View v) {
            super(v);
            tvName = v.findViewById(R.id.tv_customer_name_book);
            tvStyle = v.findViewById(R.id.tv_hairstyle_name_book);
            tvId = v.findViewById(R.id.tv_booking_id);
        }
    }
}
