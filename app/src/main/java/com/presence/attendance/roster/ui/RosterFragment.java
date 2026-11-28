package com.presence.attendance.roster.ui;

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
import androidx.annotation.WorkerThread;
import androidx.fragment.app.DialogFragment;
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
import com.presence.attendance.core.Log;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.Monogram;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.databinding.FragmentListBinding;
import com.presence.attendance.department.domain.Batch;
import com.presence.attendance.department.ui.BatchSheet;
import com.presence.attendance.roster.domain.RosterEntry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * HOD: one batch. Its semester, how many students it has against its intake, its lab groups, and
 * the students themselves, grouped by lab group, with add, CSV import and correction.
 */
public final class RosterFragment extends Fragment {

    /** Fragment result a sheet sends when the batch or its students have changed. */
    public static final String RESULT_ROSTER_CHANGED = "roster_changed";

    private static final String TAG = "RosterFragment";
    private static final String TAG_SHEET = "roster_sheet";
    private static final String KEY_SEMESTER = "semester";
    private static final String KEY_STUDENTS = "students";
    private static final String KEY_GROUPS = "groups";
    private static final String KEY_EMPTY = "empty";
    private static final String KEY_SUBJECTS = "subjects";
    private static final String STUDENT = "student:";
    private static final int MAX_FILE_BYTES = 256 * 1024;
    private static final int READ_CHUNK = 8 * 1024;
    private static final String[] CSV_TYPES = {
        "text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel"
    };

    private RosterViewModel rosterViewModel;
    private RosterImportViewModel importViewModel;

    private final ActivityResultLauncher<String[]> pickCsv =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(), this::onCsvPicked);

    public RosterFragment() {
        super(R.layout.fragment_list);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentListBinding binding = FragmentListBinding.bind(view);
        WindowInsetsPadding.applyHorizontal(binding.getRoot());
        WindowInsetsPadding.applyBottom(binding.listContent);
        final RosterFragmentArgs args = RosterFragmentArgs.fromBundle(requireArguments());

        // Scoped to this destination rather than the fragment, so the sheets and the import
        // screen share them.
        final ViewModelStoreOwner owner = owner(this);
        rosterViewModel = rosterViewModel(this, args.getBatchId());
        importViewModel =
                new ViewModelProvider(
                                owner,
                                ViewModels.factory(c ->
                                        new RosterImportViewModel(
                                                c.auth(), c.roster(), c.online())))
                        .get(RosterImportViewModel.class);

        binding.listToolbar.setTitle(args.getBatchName());
        binding.listToolbar.inflateMenu(R.menu.menu_roster);
        binding.listToolbar.setNavigationOnClickListener(
                v -> NavHostFragment.findNavController(this).popBackStack());
        binding.listToolbar.setOnMenuItemClickListener(this::onMenuItem);

        final LineAdapter adapter = new LineAdapter(this::onRowClicked);
        binding.listRecycler.setAdapter(adapter);
        binding.listMessageAction.setOnClickListener(v -> rosterViewModel.refresh());
        getChildFragmentManager()
                .setFragmentResultListener(
                        RESULT_ROSTER_CHANGED,
                        getViewLifecycleOwner(),
                        (key, result) -> rosterViewModel.refresh());

        rosterViewModel
                .getState()
                .observe(getViewLifecycleOwner(), state -> render(binding, adapter, state));
        importViewModel
                .getState()
                .observe(getViewLifecycleOwner(), state -> renderImport(binding, state));
    }

    /** The navigation entry of the batch screen, which owns its shared ViewModels. */
    @NonNull
    static ViewModelStoreOwner owner(@NonNull Fragment fragment) {
        return NavHostFragment.findNavController(fragment).getBackStackEntry(R.id.rosterFragment);
    }

    /** The batch screen's ViewModel, for the screen itself and for its sheets. */
    @NonNull
    static RosterViewModel rosterViewModel(@NonNull Fragment fragment, @NonNull String batchId) {
        return new ViewModelProvider(
                        owner(fragment),
                        ViewModels.factory(c ->
                                new RosterViewModel(c.roster(), c.batches(), c.online(), batchId)))
                .get(RosterViewModel.class);
    }

    private void render(
            @NonNull FragmentListBinding binding,
            @NonNull LineAdapter adapter,
            @NonNull RosterViewModel.State state) {
        final Batch batch = state.batch;
        if (batch != null) {
            binding.listToolbar.setTitle(batch.displayName());
        }
        binding.listToolbar.getMenu().setGroupEnabled(0, batch != null);
        adapter.submitList(batch != null ? lines(batch, state.entries) : new ArrayList<>());
        binding.listProgress.setVisibility(
                visibleIf(state.status == RosterViewModel.Status.LOADING));
        binding.listRecycler.setVisibility(visibleIf(batch != null));
        final boolean error = state.status == RosterViewModel.Status.ERROR;
        binding.listMessage.setVisibility(visibleIf(error));
        if (error) {
            final AppError cause = state.error != null ? state.error : AppError.UNKNOWN;
            binding.listMessageTitle.setText(R.string.roster_error_title);
            binding.listMessageBody.setText(AppErrorMessages.of(cause));
            binding.listMessageAction.setText(R.string.action_try_again);
        }
    }

    /** Shows why a chosen file was refused, or opens the preview once it has been validated. */
    private void renderImport(
            @NonNull FragmentListBinding binding, @NonNull RosterImportViewModel.State state) {
        if (state.fileError != null) {
            binding.listNotice.setText(AppErrorMessages.of(state.fileError));
        } else if (state.fileProblem != null) {
            binding.listNotice.setText(
                    switch (state.fileProblem) {
                        case NO_HEADER -> R.string.import_no_header;
                        case EMPTY -> R.string.import_empty;
                        case TOO_LARGE -> R.string.import_too_large;
                    });
        }
        binding.listNotice.setVisibility(
                visibleIf(state.fileError != null || state.fileProblem != null));
        if (state.openPreview) {
            importViewModel.onPreviewOpened();
            final NavController navController = NavHostFragment.findNavController(this);
            final NavDestination current = navController.getCurrentDestination();
            if (current != null && current.getId() == R.id.rosterFragment) {
                navController.navigate(RosterFragmentDirections.actionRosterToImport());
            }
        }
    }

    /**
     * The batch's summary, then its students: those without a lab group first, so they are not
     * missed, then one section per group, each in roll-number order.
     */
    @NonNull
    private List<Line> lines(@NonNull Batch batch, @NonNull List<RosterEntry> entries) {
        final List<Line> lines = new ArrayList<>();
        lines.add(Line.header("summary", batch.displayName()));
        lines.add(semesterLine(batch));
        final int count = entries.size();
        lines.add(
                Line.row(
                        KEY_STUDENTS,
                        getString(R.string.batch_students_row),
                        getString(
                                count > batch.getIntake()
                                        ? R.string.batch_students_over
                                        : R.string.batch_students_detail,
                                count,
                                batch.getIntake())));
        lines.add(groupsLine(batch, entries).navigates());
        lines.add(
                Line.row(
                                KEY_SUBJECTS,
                                getString(R.string.batch_subjects_row),
                                getString(R.string.batch_subjects_detail, batch.getSemester()))
                        .navigates());

        if (entries.isEmpty()) {
            lines.add(Line.header("students", getString(R.string.batch_students_row)));
            lines.add(
                    Line.row(
                            KEY_EMPTY,
                            getString(R.string.batch_no_students),
                            getString(R.string.roster_empty_body)));
            return Line.grouped(lines);
        }
        final List<RosterEntry> ungrouped = new ArrayList<>();
        for (RosterEntry entry : entries) {
            if (entry.getLabGroup() == null || entry.getLabGroup() > batch.getLabGroups()) {
                ungrouped.add(entry);
            }
        }
        if (!ungrouped.isEmpty()) {
            lines.add(
                    Line.header(
                            "group0", getString(R.string.batch_no_group_header, ungrouped.size())));
            for (RosterEntry entry : ungrouped) {
                lines.add(studentLine(entry));
            }
        }
        for (int group = 1; group <= batch.getLabGroups(); group++) {
            final List<RosterEntry> members = new ArrayList<>();
            for (RosterEntry entry : entries) {
                if (entry.getLabGroup() != null && entry.getLabGroup() == group) {
                    members.add(entry);
                }
            }
            if (members.isEmpty()) {
                continue;
            }
            lines.add(
                    Line.header(
                            "group" + group,
                            getString(R.string.batch_group_header, group, members.size())));
            for (RosterEntry entry : members) {
                lines.add(studentLine(entry));
            }
        }
        return Line.grouped(lines);
    }

    @NonNull
    private Line semesterLine(@NonNull Batch batch) {
        final String title =
                getString(R.string.batch_semester_row, batch.getSemester(), batch.semesterCount());
        if (batch.isInLastSemester()) {
            return Line.row(KEY_SEMESTER, title, getString(R.string.batch_semester_last));
        }
        return Line.row(
                        KEY_SEMESTER,
                        title,
                        getString(R.string.batch_semester_move, batch.getSemester() + 1))
                .navigates();
    }

    /** "4 groups · 18, 18, 17, 17", or how many students are still without a group. */
    @NonNull
    private Line groupsLine(@NonNull Batch batch, @NonNull List<RosterEntry> entries) {
        final int[] sizes = new int[batch.getLabGroups()];
        int without = 0;
        for (RosterEntry entry : entries) {
            final Integer group = entry.getLabGroup();
            if (group == null || group > batch.getLabGroups()) {
                without++;
            } else {
                sizes[group - 1]++;
            }
        }
        final String detail;
        if (without == entries.size()) {
            detail = getString(R.string.batch_groups_none, batch.getLabGroups());
        } else if (without > 0) {
            detail = getString(R.string.batch_groups_some, batch.getLabGroups(), without);
        } else {
            final List<String> counts = new ArrayList<>();
            for (int size : sizes) {
                counts.add(String.valueOf(size));
            }
            detail =
                    getString(
                            R.string.batch_groups_sizes,
                            batch.getLabGroups(),
                            TextUtils.join(", ", counts));
        }
        return Line.row(KEY_GROUPS, getString(R.string.batch_groups_row), detail);
    }

    @NonNull
    private Line studentLine(@NonNull RosterEntry entry) {
        return Line.row(
                        STUDENT + entry.getEmail(),
                        entry.displayName(),
                        entry.getName() != null
                                ? entry.getRollNo() + "\n" + entry.getEmail()
                                : entry.getRollNo(),
                        entry.isRegistered() ? getString(R.string.roster_registered) : null,
                        Line.Tone.NEUTRAL)
                .withAvatar(Monogram.of(entry.displayName()));
    }

    private boolean onMenuItem(@NonNull MenuItem item) {
        final Batch batch = rosterViewModel.batch();
        if (batch == null) {
            return false;
        }
        if (item.getItemId() == R.id.action_add_student) {
            showSheet(AddStudentSheet.newInstance(batch.getId(), batch.getLabGroups()));
            return true;
        }
        if (item.getItemId() == R.id.action_import) {
            pickCsv.launch(CSV_TYPES);
            return true;
        }
        if (item.getItemId() == R.id.action_edit_batch) {
            showSheet(BatchSheet.edit(batch, RESULT_ROSTER_CHANGED));
            return true;
        }
        return false;
    }

    private void onRowClicked(@NonNull String key) {
        final Batch batch = rosterViewModel.batch();
        if (batch == null) {
            return;
        }
        if (KEY_SEMESTER.equals(key) && !batch.isInLastSemester()) {
            showSheet(new SemesterSheet());
        } else if (KEY_GROUPS.equals(key)) {
            showSheet(new LabGroupsSheet());
        } else if (KEY_SUBJECTS.equals(key)) {
            final NavController navController = NavHostFragment.findNavController(this);
            final NavDestination current = navController.getCurrentDestination();
            if (current != null && current.getId() == R.id.rosterFragment) {
                navController.navigate(
                        RosterFragmentDirections.actionRosterToSubjects(
                                batch.getId(), batch.displayName()));
            }
        } else if (key.startsWith(STUDENT)) {
            final String email = key.substring(STUDENT.length());
            for (RosterEntry entry : rosterViewModel.entries()) {
                if (entry.getEmail().equals(email)) {
                    showSheet(RosterEntrySheet.newInstance(entry, batch.getLabGroups()));
                    return;
                }
            }
        }
    }

    private void showSheet(@NonNull DialogFragment sheet) {
        if (getChildFragmentManager().findFragmentByTag(TAG_SHEET) == null) {
            sheet.show(getChildFragmentManager(), TAG_SHEET);
        }
    }

    private void onCsvPicked(@Nullable Uri uri) {
        final Batch batch = rosterViewModel.batch();
        if (uri == null || batch == null) {
            return;
        }
        final ContentResolver resolver =
                requireContext().getApplicationContext().getContentResolver();
        final AppExecutors executors =
                ((PresenceApp) requireContext().getApplicationContext()).getContainer().executors();
        final RosterImportViewModel importer = importViewModel;
        executors
                .io()
                .execute(() -> {
                    final String text = readText(resolver, uri);
                    // Delivered to the ViewModel, which outlives this fragment's view.
                    executors.main().execute(() -> importer.onFile(text, batch));
                });
    }

    /** The file as UTF-8 text, or null if it cannot be read or is larger than the limit. */
    @Nullable
    @WorkerThread
    public static String readText(@NonNull ContentResolver resolver, @NonNull Uri uri) {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final byte[] chunk = new byte[READ_CHUNK];
            int read;
            while ((read = in.read(chunk)) != -1) {
                bytes.write(chunk, 0, read);
                if (bytes.size() > MAX_FILE_BYTES) {
                    return null;
                }
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | SecurityException unreadable) {
            Log.w(TAG, "Could not read the chosen file", unreadable);
            return null;
        }
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}
