package com.presence.attendance.department.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.R;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.Monogram;
import com.presence.attendance.core.ui.SheetFragment;
import com.presence.attendance.course.domain.Course;
import com.presence.attendance.databinding.SheetTeacherBinding;
import com.presence.attendance.department.domain.FacultyMember;
import java.util.ArrayList;
import java.util.List;

/**
 * HOD: who teaches a subject. Picking someone else gives it to them; they claim it the next time
 * they open the app, and its students follow it there.
 */
public final class TeacherSheet extends SheetFragment {

    private static final String ARG_COURSE = "courseId";
    private static final String NOBODY = "nobody";
    private static final String TEACHER = "teacher:";

    public TeacherSheet() {
        super(R.layout.sheet_teacher);
    }

    @NonNull
    static TeacherSheet newInstance(@NonNull String courseId) {
        final TeacherSheet sheet = new TeacherSheet();
        final Bundle args = new Bundle();
        args.putString(ARG_COURSE, courseId);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final SheetTeacherBinding binding = SheetTeacherBinding.bind(view);
        final String batchId =
                SubjectsFragmentArgs.fromBundle(requireParentFragment().requireArguments())
                        .getBatchId();
        final SubjectsViewModel viewModel = SubjectsFragment.viewModel(this, batchId);
        final SubjectsViewModel.State state = viewModel.getState().getValue();
        final Course subject =
                state != null ? find(state, requireArguments().getString(ARG_COURSE)) : null;
        if (subject == null) {
            dismiss();
            return;
        }
        binding.teacherTitle.setText(subject.getName());
        final LineAdapter adapter =
                new LineAdapter(key -> {
                    final String email =
                            key.startsWith(TEACHER) ? key.substring(TEACHER.length()) : "";
                    if (email.equals(subject.getTeacherEmail())) {
                        dismiss();
                        return;
                    }
                    binding.teacherStatus.setVisibility(View.GONE);
                    binding.teacherList.setEnabled(false);
                    viewModel.assign(
                            subject,
                            email,
                            outcome -> {
                                if (!isAdded()) {
                                    return;
                                }
                                if (outcome.isSuccess()) {
                                    dismiss();
                                } else {
                                    binding.teacherList.setEnabled(true);
                                    binding.teacherStatus.setVisibility(View.VISIBLE);
                                    binding.teacherStatus.setText(
                                            AppErrorMessages.of(outcome.getError()));
                                }
                            });
                });
        binding.teacherList.setAdapter(adapter);
        adapter.submitList(lines(state, subject));
    }

    @NonNull
    private List<Line> lines(@NonNull SubjectsViewModel.State state, @NonNull Course subject) {
        final String current = getString(R.string.badge_current);
        final List<Line> lines = new ArrayList<>();
        for (FacultyMember member : state.faculty) {
            final boolean assigned = member.getEmail().equals(subject.getTeacherEmail());
            lines.add(
                    Line.row(
                                    TEACHER + member.getEmail(),
                                    member.getName(),
                                    member.getInitials() + " · " + member.getEmail(),
                                    assigned ? current : null,
                                    Line.Tone.NEUTRAL)
                            .withAvatar(Monogram.of(member.getName())));
        }
        lines.add(
                Line.row(
                        NOBODY,
                        getString(R.string.subjects_no_teacher),
                        getString(R.string.teacher_nobody_detail),
                        subject.getTeacherEmail().isEmpty() ? current : null,
                        Line.Tone.NEUTRAL));
        return Line.grouped(lines);
    }

    @Nullable
    private static Course find(@NonNull SubjectsViewModel.State state, @Nullable String id) {
        for (Course subject : state.subjects) {
            if (subject.getId().equals(id)) {
                return subject;
            }
        }
        return null;
    }
}
