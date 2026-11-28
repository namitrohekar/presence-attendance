package com.presence.attendance.department.ui;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.PresenceApp;
import com.presence.attendance.R;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.AppExecutors;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.course.domain.Course;
import com.presence.attendance.databinding.FragmentListBinding;
import com.presence.attendance.department.domain.FacultyMember;
import com.presence.attendance.roster.ui.RosterFragment;
import java.util.ArrayList;
import java.util.List;

/**
 * HOD: a batch's subjects for its current semester and who teaches each, made from the timetable.
 * Import a timetable CSV to make or update them; tap one to give it to another teacher.
 */
public final class SubjectsFragment extends Fragment {

    private static final String TAG_SHEET = "subjects_sheet";
    private static final String KEY_TIMETABLE = "timetable";
    private static final String KEY_EMPTY = "empty";
    static final String SUBJECT = "subject:";
    private static final String[] CSV_TYPES = {
        "text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel"
    };

    private SubjectsViewModel viewModel;

    private final ActivityResultLauncher<String[]> pickCsv =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(), this::onCsvPicked);

    public SubjectsFragment() {
        super(R.layout.fragment_list);
    }

    /** The subjects screen's ViewModel, shared with its preview and the teacher sheet. */
    @NonNull
    static SubjectsViewModel viewModel(@NonNull Fragment fragment, @NonNull String batchId) {
        final ViewModelStoreOwner owner =
                NavHostFragment.findNavController(fragment)
                        .getBackStackEntry(R.id.subjectsFragment);
        return new ViewModelProvider(
                        owner,
                        ViewModels.factory(c ->
                                new SubjectsViewModel(
                                        c.auth(),
                                        c.courses(),
                                        c.batches(),
                                        c.faculty(),
                                        c.online(),
                                        batchId)))
                .get(SubjectsViewModel.class);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentListBinding binding = FragmentListBinding.bind(view);
        WindowInsetsPadding.applyHorizontal(binding.getRoot());
        WindowInsetsPadding.applyBottom(binding.listContent);
        final SubjectsFragmentArgs args = SubjectsFragmentArgs.fromBundle(requireArguments());
        viewModel = viewModel(this, args.getBatchId());

        binding.listToolbar.setTitle(R.string.subjects_title);
        binding.listToolbar.setSubtitle(args.getBatchName());
        binding.listToolbar.inflateMenu(R.menu.menu_subjects);
        binding.listToolbar.setNavigationOnClickListener(
                v -> NavHostFragment.findNavController(this).popBackStack());
        binding.listToolbar.setOnMenuItemClickListener(this::onMenuItem);

        final LineAdapter adapter = new LineAdapter(this::onRowClicked);
        binding.listRecycler.setAdapter(adapter);
        binding.listMessageAction.setOnClickListener(v -> viewModel.refresh());
        viewModel
                .getState()
                .observe(getViewLifecycleOwner(), state -> render(binding, adapter, state));
    }

    private void render(
            @NonNull FragmentListBinding binding,
            @NonNull LineAdapter adapter,
            @NonNull SubjectsViewModel.State state) {
        final boolean content = state.status == SubjectsViewModel.Status.CONTENT;
        adapter.submitList(content ? lines(state) : new ArrayList<>());
        binding.listToolbar.getMenu().setGroupEnabled(0, content);
        binding.listProgress.setVisibility(
                visibleIf(state.status == SubjectsViewModel.Status.LOADING));
        binding.listRecycler.setVisibility(visibleIf(content));
        final boolean error = state.status == SubjectsViewModel.Status.ERROR;
        binding.listMessage.setVisibility(visibleIf(error));
        if (error) {
            binding.listMessageTitle.setText(R.string.subjects_error_title);
            binding.listMessageBody.setText(
                    AppErrorMessages.of(state.error != null ? state.error : AppError.UNKNOWN));
            binding.listMessageAction.setText(R.string.action_try_again);
        }
        renderFile(binding, state);
    }

    /** Why a chosen file was refused, or the preview once it has been checked. */
    private void renderFile(
            @NonNull FragmentListBinding binding, @NonNull SubjectsViewModel.State state) {
        final boolean refused =
                state.phase == SubjectsViewModel.Phase.IDLE
                        && (state.fileError != null || state.fileProblem != null);
        binding.listNotice.setVisibility(visibleIf(refused));
        if (state.fileError != null) {
            binding.listNotice.setText(AppErrorMessages.of(state.fileError));
        } else if (state.fileProblem != null) {
            binding.listNotice.setText(
                    switch (state.fileProblem) {
                        case NO_HEADER -> R.string.timetable_no_header;
                        case EMPTY -> R.string.timetable_empty;
                        case TOO_LARGE -> R.string.timetable_too_large;
                    });
        }
        if (state.openPreview) {
            viewModel.onPreviewOpened();
            final NavController navController = NavHostFragment.findNavController(this);
            final NavDestination current = navController.getCurrentDestination();
            if (current != null && current.getId() == R.id.subjectsFragment) {
                navController.navigate(
                        SubjectsFragmentDirections.actionSubjectsToTimetableImport());
            }
        }
    }

    @NonNull
    private List<Line> lines(@NonNull SubjectsViewModel.State state) {
        final List<Line> lines = new ArrayList<>();
        final int semester = state.batch != null ? state.batch.getSemester() : 0;
        lines.add(
                Line.header(
                        "h_timetable", getString(R.string.subjects_timetable_header, semester)));
        lines.add(
                Line.row(
                                KEY_TIMETABLE,
                                state.timetable == null
                                        ? getString(R.string.subjects_no_timetable)
                                        : getResources()
                                                .getQuantityString(
                                                        R.plurals.subjects_classes,
                                                        state.timetable.getSlots().size(),
                                                        state.timetable.getSlots().size()),
                                getString(
                                        state.timetable == null
                                                ? R.string.subjects_import_detail
                                                : R.string.subjects_reimport_detail))
                        .navigates());
        lines.add(
                Line.header(
                        "h_subjects", getString(R.string.subjects_header, state.subjects.size())));
        if (state.subjects.isEmpty()) {
            lines.add(
                    Line.row(
                            KEY_EMPTY,
                            getString(R.string.subjects_empty_title),
                            getString(R.string.subjects_empty_body)));
        }
        for (Course subject : state.subjects) {
            lines.add(subjectLine(state, subject));
        }
        return Line.grouped(lines);
    }

    @NonNull
    private Line subjectLine(@NonNull SubjectsViewModel.State state, @NonNull Course subject) {
        final List<String> where = new ArrayList<>();
        where.add(subject.getCode());
        if (subject.getLabGroup() > 0) {
            where.add(getString(R.string.roster_group, subject.getLabGroup()));
        }
        final String email = subject.getTeacherEmail();
        final FacultyMember teacher = state.teacher(email);
        final String who;
        final String badge;
        final Line.Tone tone;
        if (email.isEmpty()) {
            who = getString(R.string.subjects_no_teacher);
            badge = getString(R.string.badge_no_teacher);
            tone = Line.Tone.DANGER;
        } else {
            who = teacher != null ? teacher.getName() : email;
            badge =
                    subject.getTeacherId().isEmpty()
                            ? getString(R.string.badge_not_signed_in)
                            : null;
            tone = Line.Tone.NEUTRAL;
        }
        return Line.row(
                        SUBJECT + subject.getId(),
                        subject.getName(),
                        TextUtils.join(" · ", where) + "\n" + who,
                        badge,
                        tone)
                .navigates();
    }

    private boolean onMenuItem(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_import_timetable) {
            pickCsv.launch(CSV_TYPES);
            return true;
        }
        return false;
    }

    private void onRowClicked(@NonNull String key) {
        if (KEY_TIMETABLE.equals(key)) {
            pickCsv.launch(CSV_TYPES);
        } else if (key.startsWith(SUBJECT)
                && getChildFragmentManager().findFragmentByTag(TAG_SHEET) == null) {
            TeacherSheet.newInstance(key.substring(SUBJECT.length()))
                    .show(getChildFragmentManager(), TAG_SHEET);
        }
    }

    private void onCsvPicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        final ContentResolver resolver =
                requireContext().getApplicationContext().getContentResolver();
        final AppExecutors executors =
                ((PresenceApp) requireContext().getApplicationContext()).getContainer().executors();
        final SubjectsViewModel model = viewModel;
        executors
                .io()
                .execute(() -> {
                    final String text = RosterFragment.readText(resolver, uri);
                    executors.main().execute(() -> model.onFile(text));
                });
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}
