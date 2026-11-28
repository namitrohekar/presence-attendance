package com.presence.attendance.roster.ui;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.presence.attendance.R;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.SheetFragment;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.databinding.SheetAddStudentBinding;
import com.presence.attendance.roster.domain.RosterRepository.AddOutcome;

/** HOD: add one student to a batch. */
public final class AddStudentSheet extends SheetFragment {

    private static final String ARG_BATCH_ID = "batchId";
    private static final String ARG_LAB_GROUPS = "labGroups";

    public AddStudentSheet() {
        super(R.layout.sheet_add_student);
    }

    @NonNull
    static AddStudentSheet newInstance(@NonNull String batchId, int labGroups) {
        final Bundle args = new Bundle();
        args.putString(ARG_BATCH_ID, batchId);
        args.putInt(ARG_LAB_GROUPS, labGroups);
        final AddStudentSheet sheet = new AddStudentSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final SheetAddStudentBinding binding = SheetAddStudentBinding.bind(view);
        final Bundle args = requireArguments();
        final String batchId = args.getString(ARG_BATCH_ID, "");
        final int labGroups = args.getInt(ARG_LAB_GROUPS);
        final AddStudentViewModel viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new AddStudentViewModel(
                                                c.auth(),
                                                c.roster(),
                                                c.online(),
                                                batchId,
                                                labGroups)))
                        .get(AddStudentViewModel.class);

        binding.addGroup.setHint(getString(R.string.lab_group_hint, labGroups));
        final Runnable submit =
                () ->
                        viewModel.submit(
                                binding.addEmail.getText().toString(),
                                binding.addRoll.getText().toString(),
                                binding.addName.getText().toString(),
                                binding.addGroup.getText().toString());
        binding.addSubmit.setOnClickListener(v -> submit.run());
        binding.addGroup.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit.run();
            }
            // Not consumed, so the keyboard still closes.
            return false;
        });
        viewModel.getState().observe(getViewLifecycleOwner(), state -> render(binding, state));
    }

    private void render(
            @NonNull SheetAddStudentBinding binding, @NonNull AddStudentViewModel.State state) {
        if (state.added) {
            getParentFragmentManager()
                    .setFragmentResult(RosterFragment.RESULT_ROSTER_CHANGED, Bundle.EMPTY);
            dismiss();
            return;
        }
        // A conflict is shown under the field it is about, in place of the format message.
        final boolean emailExists = state.conflict == AddOutcome.EMAIL_EXISTS;
        final boolean rollTaken = state.conflict == AddOutcome.ROLL_TAKEN;
        binding.addEmailError.setText(
                emailExists ? R.string.add_email_exists : R.string.add_email_invalid);
        binding.addEmailError.setVisibility(visibleIf(state.invalid.email || emailExists));
        binding.addRollError.setText(
                rollTaken ? R.string.add_roll_taken : R.string.add_roll_invalid);
        binding.addRollError.setVisibility(visibleIf(state.invalid.rollNo || rollTaken));
        binding.addNameError.setVisibility(visibleIf(state.invalid.name));
        binding.addGroupError.setVisibility(visibleIf(state.invalid.labGroup));

        if (!state.online) {
            binding.addStatus.setText(R.string.roster_offline);
        } else if (state.error != null) {
            binding.addStatus.setText(AppErrorMessages.of(state.error));
        }
        binding.addStatus.setVisibility(visibleIf(!state.online || state.error != null));
        binding.addSubmit.setEnabled(state.online && !state.busy);
        binding.addSubmit.setText(state.busy ? R.string.add_submitting : R.string.add_submit);
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}
