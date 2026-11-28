package com.presence.attendance.roster.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.presence.attendance.R;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.SheetFragment;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.databinding.SheetRosterEntryBinding;
import com.presence.attendance.roster.domain.RosterEntry;
import com.presence.attendance.roster.domain.StudentDetails;
import com.presence.attendance.roster.ui.RosterEntryViewModel.Problem;

/**
 * HOD: one student. The name and lab group are saved without a reason and keep the registration.
 * Correcting the roll number, releasing or removing needs a reason and is written together with its
 * audit record.
 */
public final class RosterEntrySheet extends SheetFragment {

    private static final String ARG_EMAIL = "email";
    private static final String ARG_ROLL_NO = "rollNo";
    private static final String ARG_COHORT = "cohort";
    private static final String ARG_REGISTERED = "registered";
    private static final String ARG_NAME = "name";
    private static final String ARG_LAB_GROUP = "labGroup";
    private static final String ARG_LAB_GROUPS = "labGroups";

    public RosterEntrySheet() {
        super(R.layout.sheet_roster_entry);
    }

    /**
     * @param labGroups how many lab groups the student's batch has
     */
    @NonNull
    static RosterEntrySheet newInstance(@NonNull RosterEntry entry, int labGroups) {
        final Bundle args = new Bundle();
        args.putString(ARG_EMAIL, entry.getEmail());
        args.putString(ARG_ROLL_NO, entry.getRollNo());
        args.putString(ARG_COHORT, entry.getCohort());
        args.putBoolean(ARG_REGISTERED, entry.isRegistered());
        args.putString(ARG_NAME, entry.getName());
        args.putInt(ARG_LAB_GROUP, entry.getLabGroup() != null ? entry.getLabGroup() : 0);
        args.putInt(ARG_LAB_GROUPS, labGroups);
        final RosterEntrySheet sheet = new RosterEntrySheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final SheetRosterEntryBinding binding = SheetRosterEntryBinding.bind(view);
        final Bundle args = requireArguments();
        final String email = args.getString(ARG_EMAIL, "");
        final String rollNo = args.getString(ARG_ROLL_NO, "");
        final String cohort = args.getString(ARG_COHORT, "");
        final boolean registered = args.getBoolean(ARG_REGISTERED);
        final String name = args.getString(ARG_NAME);
        final int labGroup = args.getInt(ARG_LAB_GROUP);
        final int labGroups = args.getInt(ARG_LAB_GROUPS);
        final StudentDetails details =
                name == null ? null : new StudentDetails(name, labGroup > 0 ? labGroup : null);

        final RosterEntryViewModel viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new RosterEntryViewModel(
                                                c.auth(),
                                                c.roster(),
                                                c.online(),
                                                email,
                                                rollNo,
                                                cohort,
                                                details,
                                                labGroups)))
                        .get(RosterEntryViewModel.class);

        binding.entryTitle.setText(email);
        binding.entryNote.setText(
                registered ? R.string.entry_registered_note : R.string.entry_unregistered_note);
        binding.entryGroup.setHint(getString(R.string.lab_group_hint, labGroups));
        if (savedInstanceState == null) {
            binding.entryName.setText(name);
            binding.entryGroup.setText(labGroup > 0 ? String.valueOf(labGroup) : "");
            binding.entryRoll.setText(rollNo);
        }
        // Only a registered entry has a registration to release.
        binding.entryRelease.setVisibility(registered ? View.VISIBLE : View.GONE);

        binding.entrySaveDetails.setOnClickListener(v ->
                viewModel.saveDetails(
                        binding.entryName.getText().toString(),
                        binding.entryGroup.getText().toString()));
        // A student stays in their batch; only the roll number is corrected here.
        binding.entrySave.setOnClickListener(v ->
                viewModel.save(
                        binding.entryRoll.getText().toString(),
                        cohort,
                        binding.entryReason.getText().toString()));
        binding.entryRelease.setOnClickListener(
                v -> viewModel.release(binding.entryReason.getText().toString()));
        binding.entryRemove.setOnClickListener(
                v -> viewModel.remove(binding.entryReason.getText().toString()));
        viewModel.getState().observe(getViewLifecycleOwner(), state -> render(binding, state));
    }

    private void render(
            @NonNull SheetRosterEntryBinding binding, @NonNull RosterEntryViewModel.State state) {
        if (state.done) {
            getParentFragmentManager()
                    .setFragmentResult(RosterFragment.RESULT_ROSTER_CHANGED, Bundle.EMPTY);
            dismiss();
            return;
        }
        final Problem problem = state.problem;
        final boolean rollTaken = problem == Problem.ROLL_TAKEN;
        binding.entryRollError.setText(
                rollTaken ? R.string.add_roll_taken : R.string.add_roll_invalid);
        binding.entryRollError.setVisibility(
                visibleIf(problem == Problem.ROLL_NO_INVALID || rollTaken));
        binding.entryNameError.setVisibility(visibleIf(problem == Problem.NAME_INVALID));
        binding.entryGroupError.setVisibility(visibleIf(problem == Problem.LAB_GROUP_INVALID));
        binding.entryReasonError.setVisibility(visibleIf(problem == Problem.REASON_MISSING));

        if (!state.online) {
            binding.entryStatus.setText(R.string.roster_offline);
        } else if (problem == Problem.UNCHANGED) {
            binding.entryStatus.setText(R.string.entry_unchanged);
        } else if (problem == Problem.GONE) {
            binding.entryStatus.setText(R.string.entry_gone);
        } else if (state.error != null) {
            binding.entryStatus.setText(AppErrorMessages.of(state.error));
        }
        final boolean showStatus =
                !state.online
                        || problem == Problem.UNCHANGED
                        || problem == Problem.GONE
                        || state.error != null;
        binding.entryStatus.setVisibility(visibleIf(showStatus));

        final boolean enabled = state.online && !state.busy;
        binding.entrySaveDetails.setEnabled(enabled);
        binding.entrySave.setEnabled(enabled);
        binding.entrySave.setText(state.busy ? R.string.entry_saving : R.string.entry_save);
        binding.entryRelease.setEnabled(enabled);
        binding.entryRemove.setEnabled(enabled);
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}
