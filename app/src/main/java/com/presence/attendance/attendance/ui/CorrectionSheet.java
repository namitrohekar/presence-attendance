package com.presence.attendance.attendance.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;
import com.presence.attendance.R;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.SheetFragment;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.databinding.SheetCorrectionBinding;

/**
 * Faculty: one student's attendance for one session, changed by hand. It offers what makes sense
 * for the record: present if they are absent, absent if they are present, restore if it was
 * revoked; and for a scan that counts as present, revoke as a proxy.
 */
public final class CorrectionSheet extends SheetFragment {

    private static final String ARG_COURSE_ID = "courseId";
    private static final String ARG_SESSION_ID = "sessionId";
    private static final String ARG_STUDENT_ID = "studentId";
    private static final String ARG_NAME = "name";
    private static final String ARG_STATUS = "status";
    private static final String ARG_RECORD_STATUS = "recordStatus";
    private static final String ARG_FROM_SCAN = "fromScan";
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
            @Nullable AttendanceRecord record) {
        final Bundle args = new Bundle();
        args.putString(ARG_COURSE_ID, courseId);
        args.putString(ARG_SESSION_ID, sessionId);
        args.putString(ARG_STUDENT_ID, studentId);
        args.putString(ARG_NAME, name);
        args.putString(ARG_STATUS, statusText);
        args.putBoolean(ARG_HAS_RECORD, record != null);
        args.putString(ARG_RECORD_STATUS, record != null ? record.getStatus().name() : null);
        args.putBoolean(ARG_FROM_SCAN, record != null && record.isFromScan());
        final CorrectionSheet sheet = new CorrectionSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final SheetCorrectionBinding binding = SheetCorrectionBinding.bind(view);
        final Bundle args = requireArguments();
        final AttendanceRecord.Status current =
                AttendanceRecord.Status.fromWire(args.getString(ARG_RECORD_STATUS));
        final boolean present = current != null && current.countsAsPresent();
        // The main action: restore a revoked mark, otherwise the opposite of what it is now.
        final AttendanceRecord.Status target =
                present
                        ? AttendanceRecord.Status.MANUAL_ABSENT
                        : AttendanceRecord.Status.MANUAL_PRESENT;
        final boolean revocable = present && args.getBoolean(ARG_FROM_SCAN);

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
                v -> viewModel.save(target, binding.correctionNote.getText().toString()));
        binding.correctionRevoke.setVisibility(revocable ? View.VISIBLE : View.GONE);
        binding.correctionPenalty.setVisibility(revocable ? View.VISIBLE : View.GONE);
        binding.correctionRevoke.setOnClickListener(v ->
                viewModel.save(
                        AttendanceRecord.Status.REVOKED,
                        binding.correctionNote.getText().toString()));
        viewModel
                .getState()
                .observe(
                        getViewLifecycleOwner(),
                        state ->
                                render(
                                        binding,
                                        state,
                                        current == AttendanceRecord.Status.REVOKED
                                                ? R.string.correction_restore
                                                : present
                                                        ? R.string.correction_mark_absent
                                                        : R.string.correction_mark_present));
    }

    private void render(
            @NonNull SheetCorrectionBinding binding,
            @NonNull CorrectionViewModel.State state,
            @StringRes int action) {
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
        binding.correctionRevoke.setEnabled(state.canSave());
        if (state.saving) {
            binding.correctionSave.setText(R.string.correction_saving);
        } else {
            binding.correctionSave.setText(action);
        }
    }
}
