package com.presence.attendance.attendance.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.R;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.databinding.FragmentListBinding;
import java.util.ArrayList;
import java.util.List;

/** Faculty: each student's attendance in the course, with those below the requirement marked. */
public final class CourseStudentsFragment extends Fragment {

    private CourseStudentsFragmentArgs args;

    public CourseStudentsFragment() {
        super(R.layout.fragment_list);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentListBinding binding = FragmentListBinding.bind(view);
        WindowInsetsPadding.applyHorizontal(binding.getRoot());
        WindowInsetsPadding.applyBottom(binding.listContent);
        args = CourseStudentsFragmentArgs.fromBundle(requireArguments());

        final CourseStudentsViewModel viewModel =
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
        binding.listPrimary.setVisibility(View.GONE);
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
        binding.listSummary.setText(
                held
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
                    getString(
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

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}
