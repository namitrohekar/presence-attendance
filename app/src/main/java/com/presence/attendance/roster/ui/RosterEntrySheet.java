package com.presence.attendance.roster.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.chip.ChipGroup;
import com.presence.attendance.R;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.SheetFragment;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.databinding.SheetRosterEntryBinding;
import com.presence.attendance.roster.domain.RosterEntry;
import com.presence.attendance.roster.domain.StudentDetails;
import com.presence.attendance.roster.domain.StudentEmoji;
import com.presence.attendance.roster.ui.RosterEntryViewModel.Problem;

/**
 * Faculty: one roster entry. The name, batch and emoji are saved without a reason and keep the
 * registration. Correcting, releasing or removing needs a reason and is written together with its
 * audit record.
 */
public final class RosterEntrySheet extends SheetFragment {

    private static final String ARG_EMAIL = "email";
    private static final String ARG_ROLL_NO = "rollNo";
    private static final String ARG_COHORT = "cohort";
    private static final String ARG_REGISTERED = "registered";
    private static final String ARG_NAME = "name";
    private static final String ARG_BATCH = "batch";
    private static final String ARG_EMOJI = "emoji";

    @Nullable private ChipGroup emojiGroup;

    public RosterEntrySheet() {
        super(R.layout.sheet_roster_entry);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (emojiGroup != null) {
            EmojiPicker.save(emojiGroup, outState);
        }
    }

    @Override
    public void onDestroyView() {
        emojiGroup = null;
        super.onDestroyView();
    }

    @NonNull
    static RosterEntrySheet newInstance(@NonNull RosterEntry entry) {
        final Bundle args = new Bundle();
        args.putString(ARG_EMAIL, entry.getEmail());
        args.putString(ARG_ROLL_NO, entry.getRollNo());
        args.putString(ARG_COHORT, entry.getCohort());
        args.putBoolean(ARG_REGISTERED, entry.isRegistered());
        args.putString(ARG_NAME, entry.getName());
        args.putInt(ARG_BATCH, entry.getBatch() != null ? entry.getBatch() : 0);
        args.putString(ARG_EMOJI, entry.shownEmoji().name());
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
        final int batch = args.getInt(ARG_BATCH);
        final StudentEmoji emoji = StudentEmoji.fromName(args.getString(ARG_EMOJI));
        final StudentDetails details =
                name == null || emoji == null
                        ? null
                        : new StudentDetails(name, batch > 0 ? batch : null, emoji);

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
                                                details)))
                        .get(RosterEntryViewModel.class);

        binding.entryTitle.setText(email);
        binding.entryNote.setText(
                registered ? R.string.entry_registered_note : R.string.entry_unregistered_note);
        if (savedInstanceState == null) {
            binding.entryName.setText(name);
            binding.entryBatch.setText(batch > 0 ? String.valueOf(batch) : "");
            binding.entryRoll.setText(rollNo);
            binding.entryCohort.setText(cohort);
        }
        emojiGroup = binding.entryEmoji;
        EmojiPicker.fill(
                binding.entryEmoji,
                savedInstanceState == null ? emoji : EmojiPicker.restore(savedInstanceState));
        // Only a registered entry has a registration to release.
        binding.entryRelease.setVisibility(registered ? View.VISIBLE : View.GONE);

        binding.entrySaveDetails.setOnClickListener(v ->
                viewModel.saveDetails(
                        binding.entryName.getText().toString(),
                        binding.entryBatch.getText().toString(),
                        EmojiPicker.selected(binding.entryEmoji)));
        binding.entrySave.setOnClickListener(v ->
                viewModel.save(
                        binding.entryRoll.getText().toString(),
                        binding.entryCohort.getText().toString(),
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
        binding.entryCohortError.setVisibility(visibleIf(problem == Problem.COHORT_INVALID));
        binding.entryNameError.setVisibility(visibleIf(problem == Problem.NAME_INVALID));
        binding.entryBatchError.setVisibility(visibleIf(problem == Problem.BATCH_INVALID));
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
