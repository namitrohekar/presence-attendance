package com.presence.attendance.attendance.ui;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.PresenceApp;
import com.presence.attendance.R;
import com.presence.attendance.attendance.domain.AttendanceCsv;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.AppExecutors;
import com.presence.attendance.core.Log;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.databinding.FragmentListBinding;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Faculty: each student's attendance in the course, with those below the requirement marked. */
public final class CourseStudentsFragment extends Fragment {

    private static final String TAG = "CourseStudents";

    private CourseStudentsFragmentArgs args;
    private CourseStudentsViewModel viewModel;
    @Nullable private FragmentListBinding binding;
    // Shown in place of the summary once an export has been tried.
    @StringRes private int exportMessage;

    private final ActivityResultLauncher<String> createCsv =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("text/csv"), this::export);

    public CourseStudentsFragment() {
        super(R.layout.fragment_list);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentListBinding binding = FragmentListBinding.bind(view);
        this.binding = binding;
        WindowInsetsPadding.applyHorizontal(binding.getRoot());
        WindowInsetsPadding.applyBottom(binding.listContent);
        args = CourseStudentsFragmentArgs.fromBundle(requireArguments());

        viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new CourseStudentsViewModel(
                                                c.auth(),
                                                c.courses(),
                                                c.sessions(),
                                                c.attendance(),
                                                args.getCourseId(),
                                                args.getThreshold(),
                                                System::currentTimeMillis)))
                        .get(CourseStudentsViewModel.class);

        binding.listToolbar.setTitle(R.string.students_title);
        binding.listToolbar.setNavigationOnClickListener(
                v -> NavHostFragment.findNavController(this).popBackStack());
        final LineAdapter adapter = new LineAdapter(null);
        binding.listRecycler.setAdapter(adapter);
        binding.listPrimary.setText(R.string.students_export);
        binding.listPrimary.setOnClickListener(
                v -> createCsv.launch(getString(R.string.students_export_name)));
        binding.listMessageAction.setText(R.string.action_try_again);
        binding.listMessageAction.setOnClickListener(v -> viewModel.retry());
        viewModel
                .getState()
                .observe(getViewLifecycleOwner(), state -> render(binding, adapter, state));
    }

    private void render(
            @NonNull FragmentListBinding binding,
            @NonNull LineAdapter adapter,
            @NonNull CourseStudentsViewModel.State state) {
        final boolean content = state.status == CourseStudentsViewModel.Status.CONTENT;
        final boolean empty = state.status == CourseStudentsViewModel.Status.EMPTY;
        final boolean error = state.status == CourseStudentsViewModel.Status.ERROR;
        adapter.submitList(lines(state));
        binding.listProgress.setVisibility(
                visibleIf(state.status == CourseStudentsViewModel.Status.LOADING));
        binding.listRecycler.setVisibility(visibleIf(content));
        binding.listFooter.setVisibility(visibleIf(content));
        final boolean held = content && state.rows.get(0).stats.hasSessions();
        binding.listPrimary.setVisibility(visibleIf(held));
        binding.listSummary.setText(
                exportMessage != 0
                        ? getString(exportMessage)
                        : held
                                ? getString(
                                        R.string.students_summary,
                                        args.getThreshold(),
                                        state.atRiskCount,
                                        state.rows.size())
                                : getString(R.string.course_no_sessions_title));
        binding.listMessage.setVisibility(visibleIf(empty || error));
        binding.listMessageAction.setVisibility(visibleIf(error));
        if (empty) {
            binding.listMessageTitle.setText(R.string.session_roster_empty_title);
            binding.listMessageBody.setText(R.string.session_roster_empty_body);
        } else if (error) {
            final AppError cause = state.error != null ? state.error : AppError.UNKNOWN;
            binding.listMessageTitle.setText(R.string.students_error_title);
            binding.listMessageBody.setText(AppErrorMessages.of(cause));
        }
    }

    @NonNull
    private List<Line> lines(@NonNull CourseStudentsViewModel.State state) {
        final List<Line> lines = new ArrayList<>();
        for (CourseStudentsViewModel.Row row : state.rows) {
            final String detail =
                    row.stats.getStrikes() > 0
                            ? getString(
                                    R.string.students_row_detail_strikes,
                                    row.member.getRollNo(),
                                    row.stats.getPresent(),
                                    row.stats.getTotal(),
                                    row.stats.getStrikes())
                            : getString(
                                    R.string.students_row_detail,
                                    row.member.getRollNo(),
                                    row.stats.getPresent(),
                                    row.stats.getTotal());
            // The words "At risk" carry the meaning; the colour only reinforces it.
            final String badge =
                    !row.stats.hasSessions()
                            ? null
                            : getString(
                                    row.atRisk
                                            ? R.string.students_at_risk
                                            : R.string.students_percent,
                                    row.stats.percent());
            lines.add(
                    Line.row(
                            row.member.getStudentId(),
                            row.member.getName(),
                            detail,
                            badge,
                            row.atRisk ? Line.Tone.DANGER : Line.Tone.NEUTRAL));
        }
        return Line.grouped(lines);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /** Writes the report to the place the teacher picked. Null means they cancelled. */
    private void export(@Nullable Uri target) {
        final CourseStudentsViewModel.State state = viewModel.getState().getValue();
        if (target == null || state == null) {
            return;
        }
        final List<AttendanceCsv.Row> rows = new ArrayList<>(state.rows.size());
        for (CourseStudentsViewModel.Row row : state.rows) {
            rows.add(
                    new AttendanceCsv.Row(
                            row.member.getRollNo(),
                            row.member.getName(),
                            row.member.getEmail(),
                            row.stats.getPresent(),
                            row.stats.getTotal(),
                            row.stats.percent(),
                            row.stats.getStrikes(),
                            row.atRisk));
        }
        final String csv = AttendanceCsv.build(rows);
        final Context app = requireContext().getApplicationContext();
        final AppExecutors executors = ((PresenceApp) app).getContainer().executors();
        executors
                .io()
                .execute(() -> {
                    boolean saved = false;
                    try (OutputStream out = app.getContentResolver().openOutputStream(target)) {
                        if (out != null) {
                            out.write(csv.getBytes(StandardCharsets.UTF_8));
                            saved = true;
                        }
                    } catch (IOException | SecurityException e) {
                        Log.w(TAG, "The report could not be written", e);
                    }
                    final boolean ok = saved;
                    executors.main().execute(() -> onExported(ok));
                });
    }

    private void onExported(boolean saved) {
        exportMessage = saved ? R.string.students_exported : R.string.students_export_failed;
        final FragmentListBinding bound = binding;
        if (bound != null) {
            bound.listSummary.setText(exportMessage);
        }
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}
