package com.presence.attendance.session.ui;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.R;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.databinding.FragmentListBinding;
import com.presence.attendance.session.domain.Session;
import java.util.ArrayList;
import java.util.List;

/** Faculty: one course, its join code and sessions, and the action to start a session. */
public final class CourseFragment extends Fragment {

    /** Fragment result the start sheet sends, carrying {@link #KEY_SESSION_ID}. */
    static final String RESULT_SESSION_STARTED = "session_started";

    static final String KEY_SESSION_ID = "sessionId";

    private static final String TAG_SHEET = "course_sheet";
    private static final String KEY_JOIN_CODE = "join_code";
    private static final String KEY_STUDENTS = "students";
    private static final String KEY_NO_SESSIONS = "no_sessions";
    private static final int DATE_FLAGS =
            DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_TIME
                    | DateUtils.FORMAT_SHOW_WEEKDAY
                    | DateUtils.FORMAT_ABBREV_ALL;

    private CourseViewModel viewModel;
    private CourseFragmentArgs args;

    public CourseFragment() {
        super(R.layout.fragment_list);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentListBinding binding = FragmentListBinding.bind(view);
        WindowInsetsPadding.applyHorizontal(binding.getRoot());
        WindowInsetsPadding.applyBottom(binding.listContent);
        args = CourseFragmentArgs.fromBundle(requireArguments());
        final String courseId = args.getCourseId();

        viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new CourseViewModel(
                                                c.auth(),
                                                c.sessions(),
                                                courseId,
                                                System::currentTimeMillis)))
                        .get(CourseViewModel.class);

        binding.listToolbar.setTitle(args.getCourseName());
        binding.listToolbar.setNavigationOnClickListener(
                v -> NavHostFragment.findNavController(this).popBackStack());
        final LineAdapter adapter = new LineAdapter(this::onRowClicked);
        binding.listRecycler.setAdapter(adapter);
        binding.listSummary.setVisibility(View.GONE);
        binding.listPrimary.setOnClickListener(v -> onPrimary());
        binding.listMessageAction.setText(R.string.action_try_again);
        binding.listMessageAction.setOnClickListener(v -> viewModel.refresh());
        getChildFragmentManager()
                .setFragmentResultListener(
                        RESULT_SESSION_STARTED,
                        getViewLifecycleOwner(),
                        (key, result) -> openLive(result.getString(KEY_SESSION_ID, "")));

        viewModel
                .getState()
                .observe(getViewLifecycleOwner(), state -> render(binding, adapter, state));
    }

    @Override
    public void onStart() {
        super.onStart();
        // Back from the live screen: the session may have ended.
        final CourseViewModel.State state = viewModel.getState().getValue();
        if (state != null && state.status == CourseViewModel.Status.CONTENT) {
            viewModel.refresh();
        }
    }

    private void render(
            @NonNull FragmentListBinding binding,
            @NonNull LineAdapter adapter,
            @NonNull CourseViewModel.State state) {
        final boolean content = state.status == CourseViewModel.Status.CONTENT;
        final boolean error = state.status == CourseViewModel.Status.ERROR;
        adapter.submitList(lines(state));
        binding.listProgress.setVisibility(
                visibleIf(state.status == CourseViewModel.Status.LOADING));
        binding.listRecycler.setVisibility(visibleIf(content));
        binding.listFooter.setVisibility(visibleIf(content));
        binding.listMessage.setVisibility(visibleIf(error));
        if (error) {
            final AppError cause = state.error != null ? state.error : AppError.UNKNOWN;
            binding.listMessageTitle.setText(R.string.course_error_title);
            binding.listMessageBody.setText(AppErrorMessages.of(cause));
        }
        // One session at a time: while one is live, the action reopens it.
        binding.listPrimary.setText(
                state.live != null ? R.string.course_open_live : R.string.course_start_session);
    }

    @NonNull
    private List<Line> lines(@NonNull CourseViewModel.State state) {
        final List<Line> lines = new ArrayList<>();
        lines.add(Line.header("h_code", getString(R.string.course_join_code_header)));
        lines.add(Line.row(KEY_JOIN_CODE, args.getJoinCode(), null));
        lines.add(Line.header("h_attendance", getString(R.string.course_attendance_header)));
        lines.add(
                Line.row(
                                KEY_STUDENTS,
                                getString(R.string.students_title),
                                getString(R.string.course_students_detail, args.getThreshold()))
                        .navigates());
        lines.add(Line.header("h_sessions", getString(R.string.course_sessions_header)));
        if (state.sessions.isEmpty()) {
            lines.add(
                    Line.row(
                            KEY_NO_SESSIONS,
                            getString(R.string.course_no_sessions_title),
                            getString(R.string.course_no_sessions_body)));
        }
        for (Session session : state.sessions) {
            final boolean live = state.live != null && state.live.getId().equals(session.getId());
            final Line row =
                    Line.row(
                            session.getId(),
                            DateUtils.formatDateTime(
                                    requireContext(), session.getStartedAtMillis(), DATE_FLAGS),
                            null,
                            getString(live ? R.string.session_live : R.string.session_ended),
                            live ? Line.Tone.SUCCESS : Line.Tone.NEUTRAL);
            lines.add(row.navigates());
        }
        return Line.grouped(lines);
    }

    private void onRowClicked(@NonNull String key) {
        final CourseViewModel.State state = viewModel.getState().getValue();
        if (state == null) {
            return;
        }
        if (KEY_STUDENTS.equals(key)) {
            final NavController navController = NavHostFragment.findNavController(this);
            final NavDestination current = navController.getCurrentDestination();
            if (current != null && current.getId() == R.id.courseFragment) {
                navController.navigate(
                        CourseFragmentDirections.actionCourseToStudents(
                                args.getCourseId(), args.getThreshold()));
            }
            return;
        }
        if (state.live != null && state.live.getId().equals(key)) {
            openLive(key);
            return;
        }
        for (Session session : state.sessions) {
            if (session.getId().equals(key)) {
                openRoster(session);
            }
        }
    }

    /** An ended session opens its roster, where attendance can be corrected by hand. */
    private void openRoster(@NonNull Session session) {
        final NavController navController = NavHostFragment.findNavController(this);
        final NavDestination current = navController.getCurrentDestination();
        if (current != null && current.getId() == R.id.courseFragment) {
            navController.navigate(
                    CourseFragmentDirections.actionCourseToSessionRoster(
                            args.getCourseId(),
                            session.getId(),
                            DateUtils.formatDateTime(
                                    requireContext(), session.getStartedAtMillis(), DATE_FLAGS)));
        }
    }

    private void onPrimary() {
        final CourseViewModel.State state = viewModel.getState().getValue();
        if (state == null || state.status != CourseViewModel.Status.CONTENT) {
            return;
        }
        if (state.live != null) {
            openLive(state.live.getId());
        } else if (getChildFragmentManager().findFragmentByTag(TAG_SHEET) == null) {
            StartSessionSheet.newInstance(args.getCourseId())
                    .show(getChildFragmentManager(), TAG_SHEET);
        }
    }

    private void openLive(@NonNull String sessionId) {
        final NavController navController = NavHostFragment.findNavController(this);
        final NavDestination current = navController.getCurrentDestination();
        if (!sessionId.isEmpty() && current != null && current.getId() == R.id.courseFragment) {
            navController.navigate(
                    CourseFragmentDirections.actionCourseToLive(sessionId, args.getCourseName()));
        }
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}
