package com.presence.attendance.roster.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.R;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.SheetFragment;
import com.presence.attendance.databinding.SheetConfirmBinding;
import com.presence.attendance.department.domain.Batch;

/** HOD: confirm moving a batch on to its next semester. */
public final class SemesterSheet extends SheetFragment {

    public SemesterSheet() {
        super(R.layout.sheet_confirm);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final SheetConfirmBinding binding = SheetConfirmBinding.bind(view);
        final String batchId =
                RosterFragmentArgs.fromBundle(requireParentFragment().requireArguments())
                        .getBatchId();
        final RosterViewModel viewModel = RosterFragment.rosterViewModel(this, batchId);
        final Batch batch = viewModel.batch();
        if (batch == null) {
            dismiss();
            return;
        }
        final int next = batch.getSemester() + 1;
        binding.confirmTitle.setText(getString(R.string.semester_title, next));
        binding.confirmBody.setText(
                getString(R.string.semester_body, batch.displayName(), batch.getSemester(), next));
        binding.confirmYes.setText(getString(R.string.semester_confirm, next));
        binding.confirmYes.setOnClickListener(v -> viewModel.moveSemester());
        binding.confirmNo.setOnClickListener(v -> dismiss());
        viewModel
                .getState()
                .observe(
                        getViewLifecycleOwner(),
                        state -> {
                            final RosterViewModel.Work work = state.work;
                            final boolean mine =
                                    work != null && work.task == RosterViewModel.Task.SEMESTER;
                            if (mine && work.finished && work.error == null) {
                                viewModel.onWorkSeen();
                                dismiss();
                                return;
                            }
                            final boolean failed = mine && work.finished;
                            binding.confirmStatus.setVisibility(failed ? View.VISIBLE : View.GONE);
                            if (failed) {
                                binding.confirmStatus.setText(AppErrorMessages.of(work.error));
                            }
                            binding.confirmYes.setEnabled(mine ? work.finished : true);
                        });
    }
}
