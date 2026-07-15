package com.zejyv.azizul.uitm.fadebarber;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class AdminPanelFragment extends Fragment {
    private android.widget.TextView tvOffDayBadge;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_panel, container, false);
        
        view.findViewById(R.id.card_business_revenue).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), BusinessRevenueActivity.class));
        });

        view.findViewById(R.id.card_customer_sentiment).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), CustomerSentimentActivity.class));
        });

        view.findViewById(R.id.card_employee_offdays).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), EmployeeOffDaysActivity.class));
        });

        view.findViewById(R.id.card_employee_mgmt).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), EmployeeManagementActivity.class));
        });

        view.findViewById(R.id.card_admin_mgmt).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), AdminManagementActivity.class));
        });

        view.findViewById(R.id.card_customer_mgmt).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), CustomerManagementActivity.class));
        });

        tvOffDayBadge = view.findViewById(R.id.tv_admin_off_day_badge);
        syncInitialBadges();

        return view;
    }

    private void syncInitialBadges() {
        if (getActivity() instanceof MainActivityAdmin) {
            updateOffDayBadge(((MainActivityAdmin) getActivity()).getPendingOffDayCount());
        }
    }

    public void updateOffDayBadge(int count) {
        if (tvOffDayBadge == null) return;
        if (count > 0) {
            tvOffDayBadge.setVisibility(View.VISIBLE);
            tvOffDayBadge.setText(String.valueOf(count));
        } else {
            tvOffDayBadge.setVisibility(View.GONE);
        }
    }
}
