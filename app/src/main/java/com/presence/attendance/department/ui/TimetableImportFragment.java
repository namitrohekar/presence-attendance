package com.presence.attendance.department.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.R;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.databinding.FragmentListBinding;
import com.presence.attendance.department.domain.TimetableCsv;
import com.presence.attendance.department.domain.TimetablePlan;
import com.presence.attendance.department.ui.SubjectsViewModel.Change;
import com.presence.attendance.department.ui.SubjectsViewModel.Phase;
import java.util.ArrayList;
import java.util.List;

/**
 * HOD: what a timetable file will make, before anything is written. Each subject with the teacher
 * the file's initials or name were matched to, then the rows that cannot be used and why.
 */
public final class TimetableImportFragment extends Fragment {

    private SubjectsViewModel viewModel;

    public TimetableImportFragment() {
        super(R.layout.fragment_list);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentListBinding binding = FragmentListBinding.bind(view);
        WindowInsetsPadding.applyHorizontal(binding.getRoot());
        WindowInsetsPadding.applyBottom(binding.listContent);
        final Bundle subjectsArgs =
                NavHostFragment.findNavController(this)
                        .getBackStackEntry(R.id.subjectsFragment)
                        .getArguments();
        viewModel =
                SubjectsFragment.viewModel(
                        this,
                        subjectsArgs != null
                                ? SubjectsFragmentArgs.fromBundle(subjectsArgs).getBatchId()
                                : "");

        binding.listToolbar.setTitle(R.string.timetable_import_title);
        binding.listToolbar.setNavigationOnClickListener(v -> leave());
        requireActivity()
                .getOnBackPressedDispatcher()
                .addCallback(
                        getViewLifecycleOwner(),
                        new OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                leave();
                            }
                        });

        final LineAdapter adapter = new LineAdapter(null);
        binding.listRecycler.setAdapter(adapter);
        binding.listRecycler.setVisibility(View.VISIBLE);
        binding.listProgress.setVisibility(View.GONE);
        binding.listFooter.setVisibility(View.VISIBLE);
        binding.listPrimary.setOnClickListener(v -> onPrimary());
        viewModel
                .getState()
                .observe(getViewLifecycleOwner(), state -> render(binding, adapter, state));
    }

    private void render(
            @NonNull FragmentListBinding binding,
            @NonNull LineAdapter adapter,
            @NonNull SubjectsViewModel.State state) {
        final TimetablePlan plan = state.plan;
        if (state.phase == Phase.IDLE || plan == null) {
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }
        adapter.submitList(lines(state, plan));
        final int subjects = plan.subjects.size();
        final int classes = plan.timetable.getSlots().size();
        switch (state.phase) {
            case PREVIEW -> {
                binding.listSummary.setText(
                        state.online
                                ? getString(
                                        R.string.timetable_summary,
                                        state.count(Change.NEW),
                                        state.count(Change.UPDATE),
                                        classes,
                                        state.rejected())
                                : getString(R.string.subjects_offline));
                binding.listPrimary.setText(
                        classes == 0 ? R.string.timetable_nothing : R.string.timetable_save);
                binding.listPrimary.setEnabled(classes > 0 && state.online);
            }
            case SAVING -> {
                binding.listSummary.setText(
                        getString(R.string.timetable_progress, state.saved, subjects));
                binding.listPrimary.setText(R.string.timetable_saving);
                binding.listPrimary.setEnabled(false);
            }
            default -> {
                binding.listSummary.setText(
                        state.fileError != null
                                ? getString(
                                        R.string.timetable_not_saved,
                                        getString(AppErrorMessages.of(state.fileError)))
                                : getString(
                                        R.string.timetable_done,
                                        subjects - state.count(Change.FAILED),
                                        state.count(Change.FAILED)));
                binding.listPrimary.setText(R.string.action_done);
                binding.listPrimary.setEnabled(true);
            }
        }
    }

    @NonNull
    private List<Line> lines(@NonNull SubjectsViewModel.State state, @NonNull TimetablePlan plan) {
        final List<Line> lines = new ArrayList<>();
        lines.add(
                Line.header(
                        "h_subjects", getString(R.string.subjects_header, plan.subjects.size())));
        for (int i = 0; i < plan.subjects.size(); i++) {
            final TimetablePlan.Subject subject = plan.subjects.get(i);
            final Change change = state.changes.get(i);
            final List<String> where = new ArrayList<>();
            where.add(subject.code);
            if (subject.labGroup > 0) {
                where.add(getString(R.string.roster_group, subject.labGroup));
            }
            final String who;
            if (subject.teacher != null) {
                who = getString(R.string.timetable_matched, subject.teacher.getName());
            } else if (subject.facultyText.isEmpty()) {
                who = getString(R.string.subjects_no_teacher);
            } else {
                who = getString(R.string.timetable_unmatched, subject.facultyText);
            }
            lines.add(
                    Line.row(
                            "s" + i,
                            subject.name,
                            TextUtils.join(" · ", where) + "\n" + who,
                            getString(badge(change)),
                            change == Change.FAILED
                                    ? Line.Tone.DANGER
                                    : subject.teacher == null
                                            ? Line.Tone.WARNING
                                            : Line.Tone.NEUTRAL));
        }
        final List<Line> rejected = new ArrayList<>();
        for (TimetableCsv.Row row : state.rows) {
            if (row.check != TimetableCsv.Check.READY) {
                rejected.add(
                        Line.row(
                                "r" + row.line,
                                getString(R.string.import_row_line, row.line),
                                row.subject.isEmpty()
                                        ? getString(label(row.check))
                                        : row.subject + "\n" + getString(label(row.check)),
                                getString(R.string.badge_rejected),
                                Line.Tone.DANGER));
            }
        }
        if (!rejected.isEmpty()) {
            lines.add(
                    Line.header(
                            "h_rejected",
                            getString(R.string.timetable_rejected_header, rejected.size())));
            lines.addAll(rejected);
        }
        return Line.grouped(lines);
    }

    @StringRes
    private static int badge(@NonNull Change change) {
        return switch (change) {
            case NEW -> R.string.badge_new;
            case UPDATE -> R.string.badge_update;
            case SAME -> R.string.badge_no_change;
            case FAILED -> R.string.badge_not_saved;
        };
    }

    @StringRes
    private static int label(@NonNull TimetableCsv.Check check) {
        return switch (check) {
            case READY -> R.string.check_ready;
            case MALFORMED -> R.string.check_malformed;
            case INVALID_DAY -> R.string.timetable_check_day;
            case INVALID_TIME -> R.string.timetable_check_time;
            case INVALID_TYPE -> R.string.timetable_check_type;
            case INVALID_SUBJECT -> R.string.timetable_check_subject;
            case INVALID_LAB_GROUP -> R.string.check_invalid_group;
        };
    }

    private void onPrimary() {
        final SubjectsViewModel.State state = viewModel.getState().getValue();
        if (state != null && state.phase == Phase.PREVIEW) {
            viewModel.save();
        } else {
            leave();
        }
    }

    /** Back is ignored while subjects are being written; otherwise the file is forgotten. */
    private void leave() {
        final SubjectsViewModel.State state = viewModel.getState().getValue();
        if (state != null && state.phase == Phase.SAVING) {
            return;
        }
        viewModel.reset();
    }
}
