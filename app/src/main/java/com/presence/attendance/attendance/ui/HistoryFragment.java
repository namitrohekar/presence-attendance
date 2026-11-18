package com.presence.attendance.attendance.ui;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.R;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.AttendanceStats;
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

/** Student: their attendance in one course, as a figure and session by session. */
public final class HistoryFragment extends Fragment {

    private static final int DATE_FLAGS =
            DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_TIME
                    | DateUtils.FORMAT_SHOW_WEEKDAY
                    | DateUtils.FORMAT_ABBREV_ALL;

    private HistoryFragmentArgs args;

    public HistoryFragment() {
        super(R.layout.fragment_list);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentListBinding binding = FragmentListBinding.bind(view);
        WindowInsetsPadding.applyHorizontal(binding.getRoot());
        WindowInsetsPadding.applyBottom(binding.listContent);
        args = HistoryFragmentArgs.fromBundle(requireArguments());

        final HistoryViewModel viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new HistoryViewModel(
                                                c.auth(),
                                                c.sessions(),
                                                c.attendance(),
                                                args.getCourseId(),
                                                System::currentTimeMillis)))
                        .get(HistoryViewModel.class);

        binding.listToolbar.setTitle(args.getCourseName());
        binding.listToolbar.setNavigationOnClickListener(
                v -> NavHostFragment.findNavController(this).popBackStack());
        final LineAdapter adapter = new LineAdapter(null);
        binding.listRecycler.setAdapter(adapter);
        binding.listMessageAction.setText(R.string.action_try_again);
        binding.listMessageAction.setOnClickListener(v -> viewModel.retry());
        viewModel
                .getState()
                .observe(getViewLifecycleOwner(), state -> render(binding, adapter, state));
    }

    private void render(
            @NonNull FragmentListBinding binding,
            @NonNull LineAdapter adapter,
            @NonNull HistoryViewModel.State state) {
        final boolean empty = state.status == HistoryViewModel.Status.EMPTY;
        final boolean error = state.status == HistoryViewModel.Status.ERROR;
        adapter.submitList(lines(state));
        binding.listProgress.setVisibility(
                visibleIf(state.status == HistoryViewModel.Status.LOADING));
        binding.listRecycler.setVisibility(
                visibleIf(state.status == HistoryViewModel.Status.CONTENT));
        binding.listMessage.setVisibility(visibleIf(empty || error));
        binding.listMessageAction.setVisibility(visibleIf(error));
        if (empty) {
            binding.listMessageTitle.setText(R.string.course_no_sessions_title);
            binding.listMessageBody.setText(R.string.history_empty_body);
        } else if (error) {
            final AppError cause = state.error != null ? state.error : AppError.UNKNOWN;
            binding.listMessageTitle.setText(R.string.history_error_title);
            binding.listMessageBody.setText(AppErrorMessages.of(cause));
        }
    }

    @NonNull
    private List<Line> lines(@NonNull HistoryViewModel.State state) {
        final List<Line> lines = new ArrayList<>();
        final AttendanceStats stats = state.stats;
        if (stats != null && stats.hasSessions()) {
            final boolean below = stats.isBelow(args.getThreshold());
            lines.add(
                    Line.row(
                            "summary",
                            getString(R.string.history_summary_title, stats.percent()),
                            getString(
                                    R.string.history_summary_detail,
                                    stats.getPresent(),
                                    stats.getTotal()),
                            below ? getString(R.string.history_below, args.getThreshold()) : null,
                            Line.Tone.DANGER));
        }
        if (stats != null && stats.getStrikes() > 0) {
            lines.add(
                    Line.row(
                            "strikes",
                            getString(R.string.history_strikes_title, stats.getStrikes()),
                            getString(R.string.history_strikes_detail),
                            null,
                            Line.Tone.DANGER));
        }
        lines.add(Line.header("h_sessions", getString(R.string.course_sessions_header)));
        for (Session session : state.sessions) {
            final AttendanceRecord record = state.recordBySession.get(session.getId());
            // A class still running with no mark yet is not a miss.
            final boolean open = record == null && session.isLive(state.loadedAtMillis);
            lines.add(
                    Line.row(
                            session.getId(),
                            DateUtils.formatDateTime(
                                    requireContext(), session.getStartedAtMillis(), DATE_FLAGS),
                            // The teacher's reason, when they revoked or changed it.
                            record != null && record.getRevision() > 0 ? record.getNote() : null,
                            getString(
                                    open
                                            ? R.string.status_in_progress
                                            : AttendanceLabels.label(record)),
                            AttendanceLabels.tone(record)));
        }
        return Line.grouped(lines);
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}
