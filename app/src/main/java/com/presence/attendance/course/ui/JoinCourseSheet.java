package com.presence.attendance.course.ui;

import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.presence.attendance.R;
import com.presence.attendance.core.Constants;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.SheetFragment;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.databinding.SheetJoinCourseBinding;

/** Student: type a join code, confirm the course and teacher, then join. */
public final class JoinCourseSheet extends SheetFragment {

    public JoinCourseSheet() {
        super(R.layout.sheet_join_course);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final SheetJoinCourseBinding binding = SheetJoinCourseBinding.bind(view);
        final JoinCourseViewModel viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new JoinCourseViewModel(c.auth(), c.courses(), c.online())))
                        .get(JoinCourseViewModel.class);

        // Upper-case as typed or pasted, so the field shows exactly what will be looked up.
        binding.joinCode.setFilters(
                new InputFilter[] {
                    new InputFilter.AllCaps(),
                    new InputFilter.LengthFilter(Constants.JOIN_CODE_LENGTH)
                });

        final Runnable lookUp = () -> viewModel.lookUp(binding.joinCode.getText().toString());
        binding.joinContinue.setOnClickListener(v -> lookUp.run());
        binding.joinSubmit.setOnClickListener(v -> viewModel.join());
        binding.joinBack.setOnClickListener(v -> viewModel.useDifferentCode());
        runOnDone(binding.joinCode, lookUp);
        viewModel.getState().observe(getViewLifecycleOwner(), state -> render(binding, state));
    }

    private void render(
            @NonNull SheetJoinCourseBinding binding, @NonNull JoinCourseViewModel.State state) {
        if (state.step == JoinCourseViewModel.Step.JOINED) {
            getParentFragmentManager()
                    .setFragmentResult(HomeFragment.RESULT_COURSES_CHANGED, Bundle.EMPTY);
            dismiss();
            return;
        }
        final boolean confirming = state.step == JoinCourseViewModel.Step.CONFIRM;
        binding.joinEnter.setVisibility(confirming ? View.GONE : View.VISIBLE);
        binding.joinConfirm.setVisibility(confirming ? View.VISIBLE : View.GONE);

        final boolean enabled = state.online && !state.busy;
        if (confirming && state.course != null) {
            binding.joinCourseName.setText(state.course.getCourseName());
            binding.joinTeacher.setText(
                    getString(R.string.join_taught_by, state.course.getTeacherName()));
            binding.joinAs.setText(getString(R.string.join_as_roll, state.rollNo));
            binding.joinSubmit.setEnabled(enabled);
            binding.joinSubmit.setText(
                    state.busy ? R.string.join_submitting : R.string.join_submit);
            binding.joinBack.setEnabled(!state.busy);
            renderStatus(binding.joinConfirmStatus, state);
        } else {
            binding.joinCodeError.setVisibility(state.codeInvalid ? View.VISIBLE : View.GONE);
            binding.joinContinue.setEnabled(enabled);
            binding.joinContinue.setText(
                    state.busy ? R.string.join_checking : R.string.join_continue);
            renderStatus(binding.joinEnterStatus, state);
        }
    }

    /** Offline explanation first, since it is why the action is disabled; otherwise the error. */
    private static void renderStatus(
            @NonNull TextView status, @NonNull JoinCourseViewModel.State state) {
        if (!state.online) {
            status.setText(R.string.join_offline);
        } else if (state.error != null) {
            status.setText(AppErrorMessages.of(state.error));
        }
        status.setVisibility(!state.online || state.error != null ? View.VISIBLE : View.GONE);
    }

    private static void runOnDone(@NonNull TextView field, @NonNull Runnable action) {
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                action.run();
            }
            // Not consumed, so the keyboard still closes.
            return false;
        });
    }
}
