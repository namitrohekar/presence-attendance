package com.presence.attendance.department.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.R;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.Monogram;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.databinding.FragmentListBinding;
import com.presence.attendance.department.domain.FacultyMember;
import java.util.ArrayList;
import java.util.List;

/** HOD: the faculty list, with add, correct and remove. */
public final class FacultyFragment extends Fragment {

    /** Fragment result a sheet sends when the list has changed. */
    static final String RESULT_FACULTY_CHANGED = "faculty_changed";

    private static final String TAG_SHEET = "faculty_sheet";

    private FacultyViewModel viewModel;

    public FacultyFragment() {
        super(R.layout.fragment_list);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentListBinding binding = FragmentListBinding.bind(view);
        WindowInsetsPadding.applyHorizontal(binding.getRoot());
        WindowInsetsPadding.applyBottom(binding.listContent);
        viewModel =
                new ViewModelProvider(
                                this, ViewModels.factory(c -> new FacultyViewModel(c.faculty())))
                        .get(FacultyViewModel.class);

        binding.listToolbar.setTitle(R.string.faculty_title);
        binding.listToolbar.inflateMenu(R.menu.menu_faculty);
        binding.listToolbar.setNavigationOnClickListener(
                v -> NavHostFragment.findNavController(this).popBackStack());
        binding.listToolbar.setOnMenuItemClickListener(this::onMenuItem);

        final LineAdapter adapter = new LineAdapter(this::openMember);
        binding.listRecycler.setAdapter(adapter);
        binding.listMessageAction.setOnClickListener(v -> onMessageAction());
        getChildFragmentManager()
                .setFragmentResultListener(
                        RESULT_FACULTY_CHANGED,
                        getViewLifecycleOwner(),
                        (key, result) -> viewModel.refresh());
        viewModel
                .getState()
                .observe(getViewLifecycleOwner(), state -> render(binding, adapter, state));
    }

    private void render(
            @NonNull FragmentListBinding binding,
            @NonNull LineAdapter adapter,
            @NonNull FacultyViewModel.State state) {
        adapter.submitList(lines(state.members));
        binding.listProgress.setVisibility(
                visibleIf(state.status == FacultyViewModel.Status.LOADING));
        binding.listRecycler.setVisibility(
                visibleIf(state.status == FacultyViewModel.Status.CONTENT));
        final boolean empty = state.status == FacultyViewModel.Status.EMPTY;
        final boolean error = state.status == FacultyViewModel.Status.ERROR;
        binding.listMessage.setVisibility(visibleIf(empty || error));
        if (empty) {
            binding.listMessageTitle.setText(R.string.faculty_empty_title);
            binding.listMessageBody.setText(R.string.faculty_empty_body);
            binding.listMessageAction.setText(R.string.faculty_add);
        } else if (error) {
            final AppError cause = state.error != null ? state.error : AppError.UNKNOWN;
            binding.listMessageTitle.setText(R.string.faculty_error_title);
            binding.listMessageBody.setText(AppErrorMessages.of(cause));
            binding.listMessageAction.setText(R.string.action_try_again);
        }
    }

    /**
     * One row per teacher: their monogram, name, and the initials and address they sign in with.
     */
    @NonNull
    private List<Line> lines(@NonNull List<FacultyMember> members) {
        final List<Line> lines = new ArrayList<>();
        for (FacultyMember member : members) {
            lines.add(
                    Line.row(
                                    member.getEmail(),
                                    member.getName(),
                                    getString(
                                            R.string.faculty_row_detail,
                                            member.getInitials(),
                                            member.getEmail()))
                            .withAvatar(Monogram.of(member.getName()))
                            .navigates());
        }
        return Line.grouped(lines);
    }

    private boolean onMenuItem(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_add_faculty) {
            showSheet(FacultySheet.add());
            return true;
        }
        return false;
    }

    private void onMessageAction() {
        final FacultyViewModel.State state = viewModel.getState().getValue();
        if (state != null && state.status == FacultyViewModel.Status.ERROR) {
            viewModel.refresh();
        } else {
            showSheet(FacultySheet.add());
        }
    }

    private void openMember(@NonNull String email) {
        for (FacultyMember member : viewModel.members()) {
            if (member.getEmail().equals(email)) {
                showSheet(FacultySheet.edit(member));
                return;
            }
        }
    }

    private void showSheet(@NonNull DialogFragment sheet) {
        if (getChildFragmentManager().findFragmentByTag(TAG_SHEET) == null) {
            sheet.show(getChildFragmentManager(), TAG_SHEET);
        }
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}
