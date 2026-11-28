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
import com.presence.attendance.databinding.SheetBatchBinding;
import com.presence.attendance.department.domain.Batch;
import java.util.Calendar;

/** HOD: create a batch, or edit one. Tells its parent through a fragment result when done. */
public final class BatchSheet extends SheetFragment {

    private static final String ARG_RESULT_KEY = "resultKey";
    private static final String ARG_PROGRAMME = "programme";
    private static final String ARG_START_YEAR = "startYear";
    private static final String ARG_YEARS = "years";
    private static final String ARG_SEMESTER = "semester";
    private static final String ARG_INTAKE = "intake";
    private static final String ARG_LAB_GROUPS = "labGroups";

    /** What a new batch starts from: a two-year MCA of 70 seats in four lab groups. */
    private static final String DEFAULT_PROGRAMME = "MCA";

    private static final int DEFAULT_YEARS = 2;
    private static final int DEFAULT_INTAKE = 70;
    private static final int DEFAULT_LAB_GROUPS = 4;

    public BatchSheet() {
        super(R.layout.sheet_batch);
    }

    /** A sheet for a new batch. @param resultKey the fragment result sent when it is created */
    @NonNull
    public static BatchSheet create(@NonNull String resultKey) {
        final Bundle args = new Bundle();
        args.putString(ARG_RESULT_KEY, resultKey);
        final BatchSheet sheet = new BatchSheet();
        sheet.setArguments(args);
        return sheet;
    }

    /** A sheet for changing a batch. @param resultKey the fragment result sent when it is saved */
    @NonNull
    public static BatchSheet edit(@NonNull Batch batch, @NonNull String resultKey) {
        final Bundle args = new Bundle();
        args.putString(ARG_RESULT_KEY, resultKey);
        args.putString(ARG_PROGRAMME, batch.getProgramme());
        args.putInt(ARG_START_YEAR, batch.getStartYear());
        args.putInt(ARG_YEARS, batch.getYears());
        args.putInt(ARG_SEMESTER, batch.getSemester());
        args.putInt(ARG_INTAKE, batch.getIntake());
        args.putInt(ARG_LAB_GROUPS, batch.getLabGroups());
        final BatchSheet sheet = new BatchSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final SheetBatchBinding binding = SheetBatchBinding.bind(view);
        final Bundle args = requireArguments();
        final String programme = args.getString(ARG_PROGRAMME);
        final Batch editing =
                programme == null
                        ? null
                        : new Batch(
                                programme,
                                args.getInt(ARG_START_YEAR),
                                args.getInt(ARG_YEARS),
                                args.getInt(ARG_SEMESTER),
                                args.getInt(ARG_INTAKE),
                                args.getInt(ARG_LAB_GROUPS));
        final BatchSheetViewModel viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new BatchSheetViewModel(
                                                c.auth(), c.batches(), c.online(), editing)))
                        .get(BatchSheetViewModel.class);

        // The programme and year make the id: they are chosen once, when the batch is created.
        binding.batchIdentity.setVisibility(editing == null ? View.VISIBLE : View.GONE);
        binding.batchSemesterGroup.setVisibility(editing == null ? View.GONE : View.VISIBLE);
        binding.batchTitle.setText(
                editing == null ? getString(R.string.batch_new) : editing.displayName());
        if (savedInstanceState == null) {
            if (editing == null) {
                binding.batchProgramme.setText(DEFAULT_PROGRAMME);
                binding.batchStart.setText(
                        String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));
                binding.batchYears.setText(String.valueOf(DEFAULT_YEARS));
                binding.batchIntake.setText(String.valueOf(DEFAULT_INTAKE));
                binding.batchGroups.setText(String.valueOf(DEFAULT_LAB_GROUPS));
            } else {
                binding.batchYears.setText(String.valueOf(editing.getYears()));
                binding.batchSemester.setText(String.valueOf(editing.getSemester()));
                binding.batchIntake.setText(String.valueOf(editing.getIntake()));
                binding.batchGroups.setText(String.valueOf(editing.getLabGroups()));
            }
        }
        binding.batchSubmit.setOnClickListener(v ->
                viewModel.submit(
                        binding.batchProgramme.getText().toString(),
                        binding.batchStart.getText().toString(),
                        binding.batchYears.getText().toString(),
                        binding.batchSemester.getText().toString(),
                        binding.batchIntake.getText().toString(),
                        binding.batchGroups.getText().toString()));
        final int idleLabel = editing == null ? R.string.batch_create : R.string.batch_save;
        final String resultKey = args.getString(ARG_RESULT_KEY, "");
        viewModel
                .getState()
                .observe(
                        getViewLifecycleOwner(),
                        state -> render(binding, state, idleLabel, resultKey));
    }

    private void render(
            @NonNull SheetBatchBinding binding,
            @NonNull BatchSheetViewModel.State state,
            int idleLabel,
            @NonNull String resultKey) {
        if (state.done) {
            getParentFragmentManager().setFragmentResult(resultKey, Bundle.EMPTY);
            dismiss();
            return;
        }
        binding.batchProgrammeError.setVisibility(visibleIf(state.invalid.programme));
        binding.batchStartError.setVisibility(visibleIf(state.invalid.startYear));
        binding.batchYearsError.setVisibility(visibleIf(state.invalid.years));
        binding.batchSemesterError.setVisibility(visibleIf(state.invalid.semester));
        binding.batchIntakeError.setVisibility(visibleIf(state.invalid.intake));
        binding.batchGroupsError.setVisibility(visibleIf(state.invalid.labGroups));
        if (!state.online) {
            binding.batchStatus.setText(R.string.roster_offline);
        } else if (state.exists) {
            binding.batchStatus.setText(R.string.batch_exists);
        } else if (state.error != null) {
            binding.batchStatus.setText(AppErrorMessages.of(state.error));
        }
        binding.batchStatus.setVisibility(
                visibleIf(!state.online || state.exists || state.error != null));
        binding.batchSubmit.setEnabled(state.online && !state.busy);
        binding.batchSubmit.setText(state.busy ? R.string.batch_saving : idleLabel);
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}
