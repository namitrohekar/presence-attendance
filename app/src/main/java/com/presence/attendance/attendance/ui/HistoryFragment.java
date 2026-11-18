package com.presence.attendance.attendance.ui;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.presence.attendance.R;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.AttendanceStats;
import com.presence.attendance.attendance.domain.MonthGrid;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.databinding.FragmentListBinding;
import com.presence.attendance.databinding.ItemHistoryHeaderBinding;
import com.presence.attendance.session.domain.Session;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A student's attendance in one course: the overall figure and register row, a month at a time on a
 * calendar, and the classes of that month or of a picked day. The student sees their own; a teacher
 * opens the same screen for one of their students.
 */
public final class HistoryFragment extends Fragment {

    private static final int DATE_FLAGS =
            DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_TIME
                    | DateUtils.FORMAT_SHOW_WEEKDAY
                    | DateUtils.FORMAT_ABBREV_ALL;
    private static final String KEY_WHOLE_MONTH = "month";

    private HistoryFragmentArgs args;
    private HistoryViewModel viewModel;
    private final Header header = new Header();

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
        final String studentId = args.getStudentId().isEmpty() ? null : args.getStudentId();
        final Locale locale = getResources().getConfiguration().getLocales().get(0);
        final DayOfWeek firstDay = WeekFields.of(locale).getFirstDayOfWeek();

        viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new HistoryViewModel(
                                                c.auth(),
                                                c.sessions(),
                                                c.attendance(),
                                                args.getCourseId(),
                                                studentId,
                                                System::currentTimeMillis,
                                                ZoneId.systemDefault(),
                                                firstDay)))
                        .get(HistoryViewModel.class);

        binding.listToolbar.setTitle(
                studentId != null ? args.getStudentName() : args.getCourseName());
        binding.listToolbar.setNavigationOnClickListener(
                v -> NavHostFragment.findNavController(this).popBackStack());
        final LineAdapter lines =
                new LineAdapter(key -> {
                    if (KEY_WHOLE_MONTH.equals(key)) {
                        viewModel.clearDay();
                    }
                });
        binding.listRecycler.setAdapter(new ConcatAdapter(header, lines));
        binding.listMessageAction.setText(R.string.action_try_again);
        binding.listMessageAction.setOnClickListener(v -> viewModel.retry());
        viewModel
                .getState()
                .observe(getViewLifecycleOwner(), state -> render(binding, lines, state));
    }

    private void render(
            @NonNull FragmentListBinding binding,
            @NonNull LineAdapter adapter,
            @NonNull HistoryViewModel.State state) {
        final boolean empty = state.status == HistoryViewModel.Status.EMPTY;
        final boolean error = state.status == HistoryViewModel.Status.ERROR;
        final boolean content = state.status == HistoryViewModel.Status.CONTENT;
        header.show(content ? state : null);
        adapter.submitList(content ? lines(state) : new ArrayList<>());
        binding.listProgress.setVisibility(
                visibleIf(state.status == HistoryViewModel.Status.LOADING));
        binding.listRecycler.setVisibility(visibleIf(content));
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

    /** The strikes, then the classes of the picked day, or else of the month on view. */
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
        if (state.month == null) {
            return Line.grouped(lines);
        }
        final ZoneId zone = ZoneId.systemDefault();
        final String monthName = monthName(state.month);
        final List<Line> classes = new ArrayList<>();
        for (Session session : MonthGrid.inMonth(state.sessions, state.month, zone)) {
            if (state.day == null || MonthGrid.dayOf(session, zone).equals(state.day)) {
                classes.add(sessionLine(state, session));
            }
        }
        lines.add(
                Line.header(
                        "h_sessions",
                        state.day == null
                                ? getString(R.string.history_sessions_month, monthName)
                                : getString(R.string.history_sessions_day, dayName(state.day))));
        if (classes.isEmpty()) {
            lines.add(Line.row("none", getString(R.string.history_no_sessions_month), null));
        }
        lines.addAll(classes);
        if (state.day != null) {
            lines.add(Line.header("h_month", ""));
            lines.add(
                    Line.row(
                            KEY_WHOLE_MONTH,
                            getString(R.string.history_show_month, monthName),
                            null));
        }
        return Line.grouped(lines);
    }

    @NonNull
    private Line sessionLine(@NonNull HistoryViewModel.State state, @NonNull Session session) {
        final AttendanceRecord record = state.recordBySession.get(session.getId());
        // A class still running with no mark yet is not a miss.
        final boolean open = record == null && session.isLive(state.loadedAtMillis);
        return Line.row(
                session.getId(),
                DateUtils.formatDateTime(
                        requireContext(), session.getStartedAtMillis(), DATE_FLAGS),
                // The teacher's reason, when they revoked or changed it.
                record != null && record.getRevision() > 0 ? record.getNote() : null,
                getString(open ? R.string.status_in_progress : AttendanceLabels.label(record)),
                open ? Line.Tone.NEUTRAL : AttendanceLabels.tone(record));
    }

    @NonNull
    private String monthName(@NonNull YearMonth month) {
        final long millis =
                month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return DateUtils.formatDateTime(
                requireContext(),
                millis,
                DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_NO_MONTH_DAY
                        | DateUtils.FORMAT_SHOW_YEAR);
    }

    @NonNull
    private String dayName(@NonNull LocalDate day) {
        final long millis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return DateUtils.formatDateTime(
                requireContext(),
                millis,
                DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_SHOW_WEEKDAY
                        | DateUtils.FORMAT_ABBREV_ALL);
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }

    /** The figure, the register row and the month: one block that scrolls with the list. */
    private final class Header extends RecyclerView.Adapter<Header.Holder> {

        @Nullable private HistoryViewModel.State state;

        final class Holder extends RecyclerView.ViewHolder {
            final ItemHistoryHeaderBinding binding;

            Holder(@NonNull ItemHistoryHeaderBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }

        /** Null hides the block. */
        void show(@Nullable HistoryViewModel.State next) {
            final boolean had = state != null;
            state = next != null && next.stats != null && next.stats.hasSessions() ? next : null;
            if (had && state != null) {
                notifyItemChanged(0);
            } else if (had) {
                notifyItemRemoved(0);
            } else if (state != null) {
                notifyItemInserted(0);
            }
        }

        @Override
        public int getItemCount() {
            return state == null ? 0 : 1;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final Holder holder =
                    new Holder(
                            ItemHistoryHeaderBinding.inflate(
                                    LayoutInflater.from(parent.getContext()), parent, false));
            holder.binding.historyPrevious.setOnClickListener(v -> viewModel.previousMonth());
            holder.binding.historyNext.setOnClickListener(v -> viewModel.nextMonth());
            holder.binding.historyCalendar.setOnDayClickListener(viewModel::selectDay);
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            final HistoryViewModel.State s = state;
            if (s == null || s.stats == null || s.month == null) {
                return;
            }
            final ItemHistoryHeaderBinding b = holder.binding;
            final AttendanceStats stats = s.stats;
            final int threshold = args.getThreshold();
            final boolean below = stats.isBelow(threshold);
            b.historyFigure.setText(getString(R.string.history_figure, stats.percent()));
            b.historyFigure.setTextColor(
                    requireContext().getColor(below ? R.color.danger_text : R.color.text));
            b.historyCaption.setText(
                    getString(
                            below ? R.string.history_caption_below : R.string.history_caption,
                            stats.getPresent(),
                            stats.getTotal(),
                            threshold));
            b.historyStrip.setMarks(register(s));

            final String month = monthName(s.month);
            final AttendanceStats inMonth = s.monthStats;
            final String line;
            if (inMonth == null || !inMonth.hasSessions()) {
                line = getString(R.string.calendar_month_none);
            } else if (inMonth.isBelow(threshold)) {
                line =
                        getString(
                                R.string.calendar_month_below,
                                inMonth.percent(),
                                inMonth.getPresent(),
                                inMonth.getTotal(),
                                threshold);
            } else {
                line =
                        getString(
                                R.string.calendar_month_line,
                                inMonth.percent(),
                                inMonth.getPresent(),
                                inMonth.getTotal());
            }
            b.historyMonth.setText(month);
            b.historyMonthLine.setText(line);
            b.historyMonthLine.setTextColor(
                    requireContext()
                            .getColor(
                                    inMonth != null && inMonth.isBelow(threshold)
                                            ? R.color.danger_text
                                            : R.color.text_secondary));
            b.historyPrevious.setEnabled(s.hasEarlierMonth);
            b.historyPrevious.setAlpha(s.hasEarlierMonth ? 1f : 0.3f);
            b.historyNext.setEnabled(s.hasLaterMonth);
            b.historyNext.setAlpha(s.hasLaterMonth ? 1f : 0.3f);
            b.historyCalendar.setDays(s.days, s.day, LocalDate.now(ZoneId.systemDefault()));
            b.historyCalendar.setContentDescription(
                    getString(R.string.calendar_description, month, line));
        }

        /** Oldest first, as a register is filled in. Only classes that are over. */
        @NonNull
        private List<RegisterStripView.Mark> register(@NonNull HistoryViewModel.State s) {
            final List<RegisterStripView.Mark> marks = new ArrayList<>();
            for (int i = s.sessions.size() - 1; i >= 0; i--) {
                final Session session = s.sessions.get(i);
                if (session.isLive(s.loadedAtMillis)) {
                    continue;
                }
                final AttendanceRecord record = s.recordBySession.get(session.getId());
                if (record != null && record.getStatus().countsAsPresent()) {
                    marks.add(RegisterStripView.Mark.ATTENDED);
                } else if (record != null
                        && record.getStatus() == AttendanceRecord.Status.REVOKED) {
                    marks.add(RegisterStripView.Mark.REVOKED);
                } else {
                    marks.add(RegisterStripView.Mark.MISSED);
                }
            }
            return marks;
        }
    }
}
