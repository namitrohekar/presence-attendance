package com.presence.attendance.course.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.presence.attendance.R;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.SheetFragment;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.databinding.SheetCreateCourseBinding;

/** Faculty: name and code in, a course and its join code out. */
public final class CreateCourseSheet extends SheetFragment {

    public CreateCourseSheet() {
        super(R.layout.sheet_create_course);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final SheetCreateCourseBinding binding = SheetCreateCourseBinding.bind(view);
        final CreateCourseViewModel viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new CreateCourseViewModel(
                                                c.auth(), c.courses(), c.online())))
                        .get(CreateCourseViewModel.class);

        final Runnable submit =
                () ->
                        viewModel.submit(
                                binding.createName.getText().toString(),
                                binding.createCode.getText().toString());
        binding.createSubmit.setOnClickListener(v -> submit.run());
        binding.createCode.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit.run();
            }
            // Not consumed, so the keyboard still closes.
            return false;
        });
        binding.createDoneButton.setOnClickListener(v -> dismiss());
        viewModel.getState().observe(getViewLifecycleOwner(), state -> render(binding, state));
    }

    private void render(
            @NonNull SheetCreateCourseBinding binding, @NonNull CreateCourseViewModel.State state) {
        final boolean done = state.created != null;
        binding.createForm.setVisibility(done ? View.GONE : View.VISIBLE);
        binding.createDone.setVisibility(done ? View.VISIBLE : View.GONE);
        if (state.created != null) {
            final String joinCode = state.created.getJoinCode();
            binding.createJoinCode.setText(joinCode);
            // Spaced out so TalkBack reads the code letter by letter instead of as a word.
            binding.createJoinCode.setContentDescription(
                    getString(R.string.join_code_spoken, String.join(" ", joinCode.split(""))));
            getParentFragmentManager()
                    .setFragmentResult(HomeFragment.RESULT_COURSES_CHANGED, Bundle.EMPTY);
            hideKeyboard(binding.getRoot());
            return;
        }
        binding.createNameError.setVisibility(state.nameMissing ? View.VISIBLE : View.GONE);
        binding.createCodeError.setVisibility(state.codeMissing ? View.VISIBLE : View.GONE);
        if (!state.online) {
            binding.createStatus.setText(R.string.create_offline);
        } else if (state.error != null) {
            binding.createStatus.setText(AppErrorMessages.of(state.error));
        }
        final boolean showStatus = !state.online || state.error != null;
        binding.createStatus.setVisibility(showStatus ? View.VISIBLE : View.GONE);
        binding.createSubmit.setEnabled(state.online && !state.submitting);
        binding.createSubmit.setText(
                state.submitting ? R.string.create_submitting : R.string.create_submit);
    }

    /** The form is gone once the course exists, so nothing is left to type into. */
    private void hideKeyboard(@NonNull View view) {
        final Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            WindowCompat.getInsetsController(dialog.getWindow(), view)
                    .hide(WindowInsetsCompat.Type.ime());
        }
    }
}
