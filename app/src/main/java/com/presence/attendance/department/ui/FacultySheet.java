package com.presence.attendance.department.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.presence.attendance.R;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.SheetFragment;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.databinding.SheetFacultyBinding;
import com.presence.attendance.department.domain.FacultyMember;
import com.presence.attendance.department.ui.FacultySheetViewModel.Problem;

/** HOD: add a teacher to the faculty list, or correct or remove one. */
public final class FacultySheet extends SheetFragment {

    private static final String ARG_EMAIL = "email";
    private static final String ARG_NAME = "name";
    private static final String ARG_INITIALS = "initials";

    public FacultySheet() {
        super(R.layout.sheet_faculty);
    }

    @NonNull
    static FacultySheet add() {
        return new FacultySheet();
    }

    @NonNull
    static FacultySheet edit(@NonNull FacultyMember member) {
        final Bundle args = new Bundle();
        args.putString(ARG_EMAIL, member.getEmail());
        args.putString(ARG_NAME, member.getName());
        args.putString(ARG_INITIALS, member.getInitials());
        final FacultySheet sheet = new FacultySheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final SheetFacultyBinding binding = SheetFacultyBinding.bind(view);
        final Bundle args = getArguments();
        final FacultyMember editing =
                args == null || args.getString(ARG_EMAIL) == null
                        ? null
                        : new FacultyMember(
                                args.getString(ARG_EMAIL, ""),
                                args.getString(ARG_NAME, ""),
                                args.getString(ARG_INITIALS, ""));
        final FacultySheetViewModel viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new FacultySheetViewModel(
                                                c.auth(), c.faculty(), c.online(), editing)))
                        .get(FacultySheetViewModel.class);

        if (editing != null) {
            binding.facultyTitle.setText(R.string.faculty_edit);
            binding.facultyNote.setText(getString(R.string.faculty_edit_note, editing.getEmail()));
            binding.facultyEmailGroup.setVisibility(View.GONE);
            binding.facultyRemove.setVisibility(View.VISIBLE);
            binding.facultySave.setText(R.string.faculty_save);
            if (savedInstanceState == null) {
                binding.facultyName.setText(editing.getName());
                binding.facultyInitials.setText(editing.getInitials());
            }
        }
        binding.facultySave.setOnClickListener(v ->
                viewModel.save(
                        binding.facultyName.getText().toString(),
                        binding.facultyInitials.getText().toString(),
                        binding.facultyEmail.getText().toString()));
        binding.facultyRemove.setOnClickListener(v -> viewModel.remove());
        final int idleLabel = editing == null ? R.string.faculty_add : R.string.faculty_save;
        viewModel
                .getState()
                .observe(getViewLifecycleOwner(), state -> render(binding, state, idleLabel));
    }

    private void render(
            @NonNull SheetFacultyBinding binding,
            @NonNull FacultySheetViewModel.State state,
            int idleLabel) {
        if (state.done) {
            getParentFragmentManager()
                    .setFragmentResult(FacultyFragment.RESULT_FACULTY_CHANGED, Bundle.EMPTY);
            dismiss();
            return;
        }
        final Problem problem = state.problem;
        binding.facultyNameError.setVisibility(visibleIf(problem == Problem.NAME_INVALID));
        binding.facultyInitialsError.setVisibility(visibleIf(problem == Problem.INITIALS_INVALID));
        binding.facultyEmailError.setVisibility(visibleIf(problem == Problem.EMAIL_INVALID));
        if (!state.online) {
            binding.facultyStatus.setText(R.string.roster_offline);
        } else if (state.error != null) {
            binding.facultyStatus.setText(AppErrorMessages.of(state.error));
        } else if (problem == Problem.EXISTS) {
            binding.facultyStatus.setText(R.string.faculty_exists);
        } else if (problem == Problem.GONE) {
            binding.facultyStatus.setText(R.string.faculty_gone);
        } else if (problem == Problem.CONFIRM_REMOVE) {
            binding.facultyStatus.setText(R.string.faculty_remove_confirm);
        }
        final boolean showStatus =
                !state.online
                        || state.error != null
                        || problem == Problem.EXISTS
                        || problem == Problem.GONE
                        || problem == Problem.CONFIRM_REMOVE;
        binding.facultyStatus.setVisibility(visibleIf(showStatus));
        final boolean enabled = state.online && !state.busy;
        binding.facultySave.setEnabled(enabled);
        binding.facultyRemove.setEnabled(enabled);
        binding.facultySave.setText(state.busy ? R.string.batch_saving : idleLabel);
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}
