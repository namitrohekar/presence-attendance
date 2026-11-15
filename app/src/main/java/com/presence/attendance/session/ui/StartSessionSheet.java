package com.presence.attendance.session.ui;

import android.Manifest;
import android.os.Bundle;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.presence.attendance.R;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.SheetFragment;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.databinding.SheetStartSessionBinding;
import com.presence.attendance.session.ui.StartSessionViewModel.Fix;
import com.presence.attendance.session.ui.StartSessionViewModel.Mode;

/** Faculty: choose how long a session runs and where students must be, then start it. */
public final class StartSessionSheet extends SheetFragment {

    private static final String ARG_COURSE_ID = "courseId";

    private StartSessionViewModel viewModel;

    // Fine and coarse together: Android may then offer "precise", which the geofence needs.
    private final ActivityResultLauncher<String[]> requestLocation =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    granted -> viewModel.onPermissionResult());

    public StartSessionSheet() {
        super(R.layout.sheet_start_session);
    }

    @NonNull
    static StartSessionSheet newInstance(@NonNull String courseId) {
        final Bundle args = new Bundle();
        args.putString(ARG_COURSE_ID, courseId);
        final StartSessionSheet sheet = new StartSessionSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final SheetStartSessionBinding binding = SheetStartSessionBinding.bind(view);
        final String courseId = requireArguments().getString(ARG_COURSE_ID, "");
        viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new StartSessionViewModel(
                                                c.auth(),
                                                c.sessions(),
                                                c.location(),
                                                c.online(),
                                                courseId)))
                        .get(StartSessionViewModel.class);

        binding.startDuration.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                viewModel.setDuration(Math.round(value));
            }
        });
        binding.startRadius.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                viewModel.setRadius(Math.round(value));
            }
        });
        binding.startMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                viewModel.setMode(checkedId == R.id.start_mode_here ? Mode.HERE : Mode.CAMPUS);
            }
        });
        binding.startAllowLocation.setOnClickListener(v ->
                requestLocation.launch(
                        new String[] {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        }));
        binding.startSubmit.setOnClickListener(v -> viewModel.start());
        viewModel.getState().observe(getViewLifecycleOwner(), state -> render(binding, state));
    }

    private void render(
            @NonNull SheetStartSessionBinding binding, @NonNull StartSessionViewModel.State state) {
        if (state.started != null) {
            final Bundle result = new Bundle();
            result.putString(CourseFragment.KEY_SESSION_ID, state.started.getId());
            getParentFragmentManager()
                    .setFragmentResult(CourseFragment.RESULT_SESSION_STARTED, result);
            dismiss();
            return;
        }
        binding.startDurationLabel.setText(
                getString(R.string.start_duration_label, state.durationMin));
        binding.startDuration.setValue(state.durationMin);
        // The label's text changes with the value, so it is given to the slider directly.
        binding.startDuration.setContentDescription(binding.startDurationLabel.getText());
        binding.startRadiusLabel.setText(getString(R.string.start_radius_label, state.radiusM));
        binding.startRadius.setValue(state.radiusM);
        binding.startRadius.setContentDescription(binding.startRadiusLabel.getText());
        binding.startMode.check(
                state.mode == Mode.HERE ? R.id.start_mode_here : R.id.start_mode_campus);

        final boolean here = state.mode == Mode.HERE;
        binding.startHereGroup.setVisibility(here ? View.VISIBLE : View.GONE);
        binding.startAllowLocation.setVisibility(
                state.fix == Fix.NEEDS_PERMISSION ? View.VISIBLE : View.GONE);
        switch (state.fix) {
            case NEEDS_PERMISSION -> binding.startLocationStatus.setText(R.string.start_permission);
            case LOCATING -> binding.startLocationStatus.setText(R.string.start_locating);
            case READY ->
                    binding.startLocationStatus.setText(
                            getString(R.string.start_location_ready, state.accuracyM));
            default -> binding.startLocationStatus.setText(null);
        }

        if (!state.online) {
            binding.startStatus.setText(R.string.start_offline);
        } else if (state.error != null) {
            binding.startStatus.setText(AppErrorMessages.of(state.error));
        }
        final boolean showStatus = !state.online || state.error != null;
        binding.startStatus.setVisibility(showStatus ? View.VISIBLE : View.GONE);
        binding.startSubmit.setEnabled(state.canStart());
        binding.startSubmit.setText(state.busy ? R.string.start_submitting : R.string.start_submit);
    }
}
