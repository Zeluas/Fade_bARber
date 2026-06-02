package com.zejyv.azizul.uitm.fadebarber;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * ActivityListFragment displays the list of "Inbox" notifications.
 * Currently uses a static layout for demonstration.
 */
public class ActivityListFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_activity_list, container, false);

        initializeStaticNotifications(view);

        return view;
    }

    /**
     * Initializes localized text for the static notification items.
     * In a real app, this would be handled by a RecyclerView adapter.
     */
    private void initializeStaticNotifications(View view) {
        // Duration text for static items
        int[] durationTvIds = {
            R.id.tv_duration_success,
            R.id.tv_duration_promo,
            R.id.tv_duration_reminder,
            R.id.tv_duration_profile
        };

        for (int id : durationTvIds) {
            TextView tv = view.findViewById(id);
            if (tv != null) {
                tv.setText(getString(R.string.notif_duration_placeholder, 30));
            }
        }
    }
}
