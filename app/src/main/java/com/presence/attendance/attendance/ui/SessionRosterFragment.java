package com.presence.attendance.attendance.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.R;
import com.presence.attendance.attendance.domain.AttendanceRecord;
import com.presence.attendance.attendance.domain.SessionReview;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.Monogram;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.course.domain.Member;
import com.presence.attendance.databinding.FragmentListBinding;
import java.util.ArrayList;
import java.util.List;

/**
 * Faculty: the summary of a session. How many marked out of everyone enrolled, the headcount and
 * whether it matches, the marks worth a second look, and the full list on request. Tapping a
 * student opens a correction, where a proxy is revoked.
 */
public final class SessionRosterFragment extends Fragment {

    private static final String TAG_SHEET = "sheet";
    private static final String KEY_PRESENT = "present";
    private static final String KEY_HEADCOUNT = "headcount";
    private static final String KEY_TOGGLE = "toggle";
    // A student can be listed twice (to review, and in the full list); rows need distinct keys.
    private static final String REVIEW = "review:";
    private static final String LISTED = "listed:";

    private SessionRosterFragmentArgs args;
    private SessionRosterViewModel viewModel;

    public SessionRosterFragment() {
        super(R.layout.fragment_list);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentListBinding binding = FragmentListBinding.bind(view);
        WindowInsetsPadding.applyHorizontal(binding.getRoot());
        WindowInsetsPadding.applyBottom(binding.listContent);
        args = SessionRosterFragmentArgs.fromBundle(requireArguments());

        viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new SessionRosterViewModel(
                                                c.auth(),
                                                c.courses(),
                                                c.sessions(),
                                                c.attendance(),
                                                args.getCourseId(),
                                                args.getSessionId())))
                        .get(SessionRosterViewModel.class);

        binding.listToolbar.setTitle(args.getTitle());
        binding.listToolbar.setNavigationOnClickListener(
                v -> NavHostFragment.findNavController(this).popBackStack());
        final LineAdapter adapter = new LineAdapter(this::onRowClicked);
        binding.listRecycler.setAdapter(adapter);
        binding.listFooter.setVisibility(View.GONE);
        binding.listMessageAction.setText(R.string.action_try_again);
        binding.listMessageAction.setOnClickListener(v -> viewModel.retry());
        getChildFragmentManager()
                .setFragmentResultListener(
                        HeadcountSheet.RESULT_HEADCOUNT,
                        getViewLifecycleOwner(),
                        (key, result) ->
                                viewModel.saveHeadcount(
                                        result.getInt(HeadcountSheet.KEY_HEADCOUNT)));
        viewModel
                .getState()
                .observe(getViewLifecycleOwner(), state -> render(binding, adapter, state));
    }

    private void render(
            @NonNull FragmentListBinding binding,
            @NonNull LineAdapter adapter,
            @NonNull SessionRosterViewModel.State state) {
        final boolean content = state.status == SessionRosterViewModel.Status.CONTENT;
        final boolean empty = state.status == SessionRosterViewModel.Status.EMPTY;
        final boolean error = state.status == SessionRosterViewModel.Status.ERROR;
        adapter.submitList(content ? lines(state) : new ArrayList<>());
        binding.listProgress.setVisibility(
                visibleIf(state.status == SessionRosterViewModel.Status.LOADING));
        binding.listRecycler.setVisibility(visibleIf(content));
        binding.listMessage.setVisibility(visibleIf(empty || error));
        binding.listMessageAction.setVisibility(visibleIf(error));
        if (empty) {
            binding.listMessageTitle.setText(R.string.session_roster_empty_title);
            binding.listMessageBody.setText(R.string.session_roster_empty_body);
        } else if (error) {
            final AppError cause = state.error != null ? state.error : AppError.UNKNOWN;
            binding.listMessageTitle.setText(R.string.session_roster_error_title);
            binding.listMessageBody.setText(AppErrorMessages.of(cause));
        }
    }

    @NonNull
    private List<Line> lines(@NonNull SessionRosterViewModel.State state) {
        final List<Line> lines = new ArrayList<>();
        lines.add(
                Line.row(
                        KEY_PRESENT,
                        getString(
                                R.string.session_roster_summary,
                                state.presentCount(),
                                state.members.size()),
                        null));
        lines.add(headcount(state).navigates());

        final List<Line> review = new ArrayList<>();
        for (Member member : state.members) {
            final SessionReview.Flag flag = state.flags.get(member.getStudentId());
            if (flag != null) {
                review.add(student(REVIEW, member, state, reason(flag, state)));
            }
        }
        if (!review.isEmpty()) {
            lines.add(Line.header("h_review", getString(R.string.review_header)));
            lines.addAll(review);
        }

        lines.add(Line.header("h_toggle", getString(R.string.students_title)));
        lines.add(
                Line.row(
                        KEY_TOGGLE,
                        state.expanded
                                ? getString(R.string.roster_hide_all)
                                : getString(R.string.roster_show_all, state.members.size()),
                        null));
        if (state.expanded) {
            final List<Line> present = new ArrayList<>();
            final List<Line> absent = new ArrayList<>();
            for (Member member : state.members) {
                final AttendanceRecord record = state.recordByStudent.get(member.getStudentId());
                final boolean counts = record != null && record.getStatus().countsAsPresent();
                (counts ? present : absent).add(student(LISTED, member, state, member.getRollNo()));
            }
            lines.add(
                    Line.header(
                            "h_present",
                            getString(R.string.roster_present_header, present.size())));
            lines.addAll(present);
            lines.add(
                    Line.header(
                            "h_absent", getString(R.string.roster_absent_header, absent.size())));
            lines.addAll(absent);
        }
        return Line.grouped(lines);
    }

    /** The headcount row: the number, and whether it matches the marks. */
    @NonNull
    private Line headcount(@NonNull SessionRosterViewModel.State state) {
        final Integer counted = state.headcount;
        final String title =
                counted == null
                        ? getString(R.string.headcount_not_entered)
                        : getString(R.string.headcount_value, counted);
        final Integer difference = state.difference();
        if (state.headcountSaving) {
            return Line.row(KEY_HEADCOUNT, title, getString(R.string.headcount_saving));
        }
        if (state.headcountError != null) {
            return Line.row(
                    KEY_HEADCOUNT, title, getString(AppErrorMessages.of(state.headcountError)));
        }
        if (difference == null) {
            return Line.row(KEY_HEADCOUNT, title, getString(R.string.headcount_prompt));
        }
        if (difference == 0) {
            return Line.row(
                    KEY_HEADCOUNT,
                    title,
                    getString(R.string.headcount_matches),
                    getString(R.string.badge_matches),
                    Line.Tone.SUCCESS);
        }
        final int gap = Math.abs(difference);
        final String detail =
                getResources()
                        .getQuantityString(
                                difference > 0
                                        ? R.plurals.headcount_more_marks
                                        : R.plurals.headcount_unmarked,
                                gap,
                                gap);
        return Line.row(
                KEY_HEADCOUNT, title, detail, getString(R.string.badge_check), Line.Tone.WARNING);
    }

    @NonNull
    private Line student(
            @NonNull String prefix,
            @NonNull Member member,
            @NonNull SessionRosterViewModel.State state,
            @NonNull String detail) {
        final AttendanceRecord record = state.recordByStudent.get(member.getStudentId());
        return Line.row(
                        prefix + member.getStudentId(),
                        member.getName(),
                        detail,
                        getString(AttendanceLabels.label(record)),
                        AttendanceLabels.tone(record))
                .withAvatar(Monogram.of(member.getName()))
                .navigates();
    }

    @NonNull
    private String reason(
            @NonNull SessionReview.Flag flag, @NonNull SessionRosterViewModel.State state) {
        if (flag.reason == SessionReview.Reason.SENT_LATE) {
            return getString(R.string.review_sent_late);
        }
        for (Member other : state.members) {
            if (other.getStudentId().equals(flag.otherStudentId)) {
                return getString(R.string.review_same_place, other.getName());
            }
        }
        return getString(R.string.review_same_place_unknown);
    }

    private void onRowClicked(@NonNull String key) {
        if (getChildFragmentManager().findFragmentByTag(TAG_SHEET) != null) {
            return;
        }
        final SessionRosterViewModel.State state = viewModel.getState().getValue();
        if (state == null) {
            return;
        }
        if (KEY_HEADCOUNT.equals(key)) {
            HeadcountSheet.newInstance(state.headcount).show(getChildFragmentManager(), TAG_SHEET);
        } else if (KEY_TOGGLE.equals(key)) {
            viewModel.toggleExpanded();
        } else if (key.startsWith(REVIEW) || key.startsWith(LISTED)) {
            openCorrection(state, key.substring(key.indexOf(':') + 1));
        }
    }

    private void openCorrection(
            @NonNull SessionRosterViewModel.State state, @NonNull String studentId) {
        for (Member member : state.members) {
            if (member.getStudentId().equals(studentId)) {
                final AttendanceRecord record = state.recordByStudent.get(studentId);
                CorrectionSheet.newInstance(
                                args.getCourseId(),
                                args.getSessionId(),
                                studentId,
                                member.getName(),
                                getString(AttendanceLabels.label(record)),
                                record)
                        .show(getChildFragmentManager(), TAG_SHEET);
                return;
            }
        }
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}
