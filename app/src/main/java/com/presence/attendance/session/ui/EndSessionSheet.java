package com.presence.attendance.session.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.R;
import com.presence.attendance.core.ui.SheetFragment;
import com.presence.attendance.databinding.SheetEndSessionBinding;

/** Asks the teacher to confirm before a session is ended, since it cannot be reopened. */
public final class EndSessionSheet extends SheetFragment {

    public EndSessionSheet() {
        super(R.layout.sheet_end_session);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final SheetEndSessionBinding binding = SheetEndSessionBinding.bind(view);
        binding.endConfirm.setOnClickListener(v -> {
            getParentFragmentManager()
                    .setFragmentResult(LiveSessionFragment.RESULT_END_CONFIRMED, Bundle.EMPTY);
            dismiss();
        });
        binding.endKeep.setOnClickListener(v -> dismiss());
    }
}
