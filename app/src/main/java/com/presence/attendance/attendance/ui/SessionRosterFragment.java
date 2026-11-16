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
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.course.domain.Member;
import com.presence.attendance.databinding.FragmentListBinding;
import java.util.ArrayList;
import java.util.List;

/** Faculty: who was present in one session. Tapping a student opens a manual correction. */
public final class SessionRosterFragment extends Fragment {

    private static final String TAG_SHEET = "correction";

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
                                                c.attendance(),
                                                args.getCourseId(),
                                                args.getSessionId())))
                        .get(SessionRosterViewModel.class);

        binding.listToolbar.setTitle(args.getTitle());
        binding.listToolbar.setNavigationOnClickListener(
                v -> NavHostFragment.findNavController(this).popBackStack());
        final LineAdapter adapter = new LineAdapter(this::openCorrection);
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
            @NonNull SessionRosterViewModel.State state) {
        final boolean content = state.status == SessionRosterViewModel.Status.CONTENT;
        final boolean empty = state.status == SessionRosterViewModel.Status.EMPTY;
        final boolean error = state.status == SessionRosterViewModel.Status.ERROR;
        adapter.submitList(lines(state));
        binding.listProgress.setVisibility(
                visibleIf(state.status == SessionRosterViewModel.Status.LOADING));
        binding.listRecycler.setVisibility(visibleIf(content));
        binding.listFooter.setVisibility(visibleIf(content));
        binding.listSummary.setText(
                getString(
                        R.string.session_roster_summary,
                        state.presentCount(),
                        state.members.size()));
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
        for (Member member : state.members) {
            final AttendanceRecord record = state.recordByStudent.get(member.getStudentId());
            lines.add(
                    Line.row(
                                    member.getStudentId(),
                                    member.getName(),
                                    member.getRollNo(),
                                    getString(AttendanceLabels.label(record)),
                                    AttendanceLabels.tone(record))
                            .navigates());
        }
        return Line.grouped(lines);
    }

    private void openCorrection(@NonNull String studentId) {
        final SessionRosterViewModel.State state = viewModel.getState().getValue();
        if (state == null || getChildFragmentManager().findFragmentByTag(TAG_SHEET) != null) {
            return;
        }
        for (Member member : state.members) {
            if (member.getStudentId().equals(studentId)) {
                final AttendanceRecord record = state.recordByStudent.get(studentId);
                CorrectionSheet.newInstance(
                                args.getCourseId(),
                                args.getSessionId(),
                                studentId,
                                member.getName(),
                                getString(AttendanceLabels.label(record)),
                                record != null,
                                record != null && record.getStatus().countsAsPresent())
                        .show(getChildFragmentManager(), TAG_SHEET);
                return;
            }
        }
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}
