package com.presence.attendance.roster.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.R;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.SheetFragment;
import com.presence.attendance.databinding.SheetConfirmBinding;
import com.presence.attendance.department.domain.Batch;
import com.presence.attendance.department.domain.LabGroups;
import java.util.ArrayList;
import java.util.List;

/**
 * HOD: the batch split into lab groups by roll number, as the college does it, shown before it is
 * applied. Only students whose group changes are written.
 */
public final class LabGroupsSheet extends SheetFragment {

    public LabGroupsSheet() {
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
        final LabGroups.Plan plan = LabGroups.plan(viewModel.entries(), batch.getLabGroups());
        binding.confirmTitle.setText(R.string.groups_title);
        binding.confirmBody.setText(body(plan, viewModel.entries().isEmpty()));
        binding.confirmYes.setText(R.string.groups_apply);
        binding.confirmYes.setOnClickListener(v -> viewModel.applyLabGroups(plan));
        binding.confirmNo.setOnClickListener(v -> dismiss());
        viewModel
                .getState()
                .observe(
                        getViewLifecycleOwner(),
                        state -> {
                            final RosterViewModel.Work work = state.work;
                            final boolean mine =
                                    work != null && work.task == RosterViewModel.Task.LAB_GROUPS;
                            if (mine && work.finished && work.error == null) {
                                viewModel.onWorkSeen();
                                dismiss();
                                return;
                            }
                            final boolean running = mine && !work.finished;
                            final boolean failed = mine && work.finished;
                            binding.confirmStatus.setVisibility(
                                    running || failed ? View.VISIBLE : View.GONE);
                            if (running) {
                                binding.confirmStatus.setText(
                                        getString(R.string.groups_progress, work.done, work.total));
                            } else if (failed) {
                                binding.confirmStatus.setText(AppErrorMessages.of(work.error));
                            }
                            binding.confirmYes.setEnabled(!running && !plan.changes.isEmpty());
                        });
    }

    /** One line per group, then how many students move. */
    @NonNull
    private String body(@NonNull LabGroups.Plan plan, boolean noStudents) {
        if (noStudents) {
            return getString(R.string.groups_no_students);
        }
        final List<String> lines = new ArrayList<>();
        for (LabGroups.Range range : plan.ranges) {
            lines.add(
                    range.count == 0
                            ? getString(R.string.groups_line_empty, range.group)
                            : getString(
                                    R.string.groups_line,
                                    range.group,
                                    range.count,
                                    range.firstRollNo,
                                    range.lastRollNo));
        }
        lines.add("");
        lines.add(
                plan.changes.isEmpty()
                        ? getString(R.string.groups_unchanged)
                        : getString(R.string.groups_changes, plan.changes.size()));
        return TextUtils.join("\n", lines);
    }
}
