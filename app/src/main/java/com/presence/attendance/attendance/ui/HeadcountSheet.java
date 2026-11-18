package com.presence.attendance.attendance.ui;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.R;
import com.presence.attendance.core.ui.SheetFragment;
import com.presence.attendance.databinding.SheetHeadcountBinding;

/** Faculty: how many people are in the room. Hands a valid number back to the summary. */
public final class HeadcountSheet extends SheetFragment {

    static final String RESULT_HEADCOUNT = "headcount";
    static final String KEY_HEADCOUNT = "value";
    private static final String ARG_CURRENT = "current";

    public HeadcountSheet() {
        super(R.layout.sheet_headcount);
    }

    @NonNull
    static HeadcountSheet newInstance(@Nullable Integer current) {
        final Bundle args = new Bundle();
        if (current != null) {
            args.putInt(ARG_CURRENT, current);
        }
        final HeadcountSheet sheet = new HeadcountSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final SheetHeadcountBinding binding = SheetHeadcountBinding.bind(view);
        final Bundle args = requireArguments();
        if (savedInstanceState == null && args.containsKey(ARG_CURRENT)) {
            binding.headcountInput.setText(String.valueOf(args.getInt(ARG_CURRENT)));
        }
        binding.headcountInput.requestFocus();
        binding.headcountSave.setOnClickListener(v -> submit(binding));
        binding.headcountInput.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) {
                submit(binding);
                return true;
            }
            return false;
        });
    }

    private void submit(@NonNull SheetHeadcountBinding binding) {
        final Integer headcount =
                SessionRosterViewModel.parseHeadcount(binding.headcountInput.getText().toString());
        binding.headcountError.setVisibility(headcount == null ? View.VISIBLE : View.GONE);
        if (headcount == null) {
            return;
        }
        final Bundle result = new Bundle();
        result.putInt(KEY_HEADCOUNT, headcount);
        getParentFragmentManager().setFragmentResult(RESULT_HEADCOUNT, result);
        dismiss();
    }
}
