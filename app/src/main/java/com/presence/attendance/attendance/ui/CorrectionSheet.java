package com.presence.attendance.attendance.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.presence.attendance.R;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.SheetFragment;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.databinding.SheetCorrectionBinding;

/**
 * Faculty: one student's attendance for one session, changed by hand. It offers the one change that
 * makes sense: present if they are absent, absent if they are present.
 */
public final class CorrectionSheet extends SheetFragment {

    private static final String ARG_COURSE_ID = "courseId";
    private static final String ARG_SESSION_ID = "sessionId";
    private static final String ARG_STUDENT_ID = "studentId";
    private static final String ARG_NAME = "name";
    private static final String ARG_STATUS = "status";
    private static final String ARG_PRESENT = "present";
    private static final String ARG_HAS_RECORD = "hasRecord";

    public CorrectionSheet() {
        super(R.layout.sheet_correction);
    }

    @NonNull
    static CorrectionSheet newInstance(
            @NonNull String courseId,
            @NonNull String sessionId,
            @NonNull String studentId,
            @NonNull String name,
            @NonNull String statusText,
            boolean hasRecord,
            boolean present) {
        final Bundle args = new Bundle();
        args.putString(ARG_COURSE_ID, courseId);
        args.putString(ARG_SESSION_ID, sessionId);
        args.putString(ARG_STUDENT_ID, studentId);
        args.putString(ARG_NAME, name);
        args.putString(ARG_STATUS, statusText);
        args.putBoolean(ARG_PRESENT, present);
        args.putBoolean(ARG_HAS_RECORD, hasRecord);
        final CorrectionSheet sheet = new CorrectionSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final SheetCorrectionBinding binding = SheetCorrectionBinding.bind(view);
        final Bundle args = requireArguments();
        final boolean present = args.getBoolean(ARG_PRESENT);

        final CorrectionViewModel viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new CorrectionViewModel(
                                                c.auth(),
                                                c.attendance(),
                                                c.online(),
                                                args.getString(ARG_COURSE_ID, ""),
                                                args.getString(ARG_SESSION_ID, ""),
                                                args.getString(ARG_STUDENT_ID, ""),
                                                args.getBoolean(ARG_HAS_RECORD))))
                        .get(CorrectionViewModel.class);

        binding.correctionTitle.setText(args.getString(ARG_NAME, ""));
        binding.correctionCurrent.setText(args.getString(ARG_STATUS, ""));
        binding.correctionSave.setOnClickListener(
                v -> viewModel.save(!present, binding.correctionNote.getText().toString()));
        viewModel
                .getState()
                .observe(getViewLifecycleOwner(), state -> render(binding, state, present));
    }

    private void render(
            @NonNull SheetCorrectionBinding binding,
            @NonNull CorrectionViewModel.State state,
            boolean present) {
        if (state.done) {
            // The roster behind this sheet listens to the records and updates by itself.
            dismiss();
            return;
        }
        binding.correctionNoteError.setVisibility(state.noteMissing ? View.VISIBLE : View.GONE);
        if (!state.online) {
            binding.correctionStatus.setText(R.string.correction_offline);
        } else if (state.error != null) {
            binding.correctionStatus.setText(AppErrorMessages.of(state.error));
        }
        binding.correctionStatus.setVisibility(
                !state.online || state.error != null ? View.VISIBLE : View.GONE);
        binding.correctionSave.setEnabled(state.canSave());
        if (state.saving) {
            binding.correctionSave.setText(R.string.correction_saving);
        } else {
            binding.correctionSave.setText(
                    present ? R.string.correction_mark_absent : R.string.correction_mark_present);
        }
    }
}
