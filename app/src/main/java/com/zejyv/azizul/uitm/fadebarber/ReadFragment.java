package com.zejyv.azizul.uitm.fadebarber;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * ReadFragment displays the history of notifications that have already been read.
 * Currently shows an empty state message.
 */
public class ReadFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the simple empty state layout
        return inflater.inflate(R.layout.fragment_activity_read, container, false);
    }
}
