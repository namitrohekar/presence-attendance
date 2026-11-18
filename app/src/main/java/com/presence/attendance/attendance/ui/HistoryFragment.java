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
        renderFigure(binding, state);
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
                            open ? Line.Tone.NEUTRAL : AttendanceLabels.tone(record)));
        }
        return Line.grouped(lines);
    }

    /** The student's figure, what it means against the requirement, and their register row. */
    private void renderFigure(
            @NonNull FragmentListBinding binding, @NonNull HistoryViewModel.State state) {
        final AttendanceStats stats = state.stats;
        final boolean shown =
                state.status == HistoryViewModel.Status.CONTENT
                        && stats != null
                        && stats.hasSessions();
        binding.listHeader.setVisibility(visibleIf(shown));
        if (!shown) {
            return;
        }
        final boolean below = stats.isBelow(args.getThreshold());
        binding.listFigure.setText(getString(R.string.history_figure, stats.percent()));
        binding.listFigure.setTextColor(
                requireContext().getColor(below ? R.color.danger_text : R.color.text));
        binding.listFigureCaption.setText(
                getString(
                        below ? R.string.history_caption_below : R.string.history_caption,
                        stats.getPresent(),
                        stats.getTotal(),
                        args.getThreshold()));
        // Oldest first, as a register is filled in. Only classes that are over.
        final List<RegisterStripView.Mark> marks = new ArrayList<>();
        for (int i = state.sessions.size() - 1; i >= 0; i--) {
            final Session session = state.sessions.get(i);
            if (session.isLive(state.loadedAtMillis)) {
                continue;
            }
            final AttendanceRecord record = state.recordBySession.get(session.getId());
            if (record != null && record.getStatus().countsAsPresent()) {
                marks.add(RegisterStripView.Mark.ATTENDED);
            } else if (record != null && record.getStatus() == AttendanceRecord.Status.REVOKED) {
                marks.add(RegisterStripView.Mark.REVOKED);
            } else {
                marks.add(RegisterStripView.Mark.MISSED);
            }
        }
        binding.listStrip.setMarks(marks);
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}
