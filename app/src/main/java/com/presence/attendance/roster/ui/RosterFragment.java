package com.presence.attendance.roster.ui;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
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
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.databinding.FragmentListBinding;
import com.presence.attendance.roster.domain.RosterEntry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Faculty: the student roster, grouped by cohort, with add, CSV import and correction. */
public final class RosterFragment extends Fragment {

    /** Fragment result a sheet sends when the roster has changed. */
    static final String RESULT_ROSTER_CHANGED = "roster_changed";

    private static final String TAG = "RosterFragment";
    private static final String TAG_SHEET = "roster_sheet";
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

        // Scoped to this destination rather than the fragment, so the import screen shares them.
        final ViewModelStoreOwner owner =
                NavHostFragment.findNavController(this).getBackStackEntry(R.id.rosterFragment);
        rosterViewModel =
                new ViewModelProvider(
                                owner, ViewModels.factory(c -> new RosterViewModel(c.roster())))
                        .get(RosterViewModel.class);
        importViewModel =
                new ViewModelProvider(
                                owner,
                                ViewModels.factory(c ->
                                        new RosterImportViewModel(
                                                c.auth(), c.roster(), c.online())))
                        .get(RosterImportViewModel.class);

        binding.listToolbar.setTitle(R.string.roster_title);
        binding.listToolbar.inflateMenu(R.menu.menu_roster);
        binding.listToolbar.setNavigationOnClickListener(
                v -> NavHostFragment.findNavController(this).popBackStack());
        binding.listToolbar.setOnMenuItemClickListener(this::onMenuItem);

        final LineAdapter adapter = new LineAdapter(this::openEntry);
        binding.listRecycler.setAdapter(adapter);
        binding.listMessageAction.setOnClickListener(v -> onMessageAction());
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

    private void render(
            @NonNull FragmentListBinding binding,
            @NonNull LineAdapter adapter,
            @NonNull RosterViewModel.State state) {
        adapter.submitList(lines(state.entries));
        binding.listProgress.setVisibility(
                visibleIf(state.status == RosterViewModel.Status.LOADING));
        binding.listRecycler.setVisibility(
                visibleIf(state.status == RosterViewModel.Status.CONTENT));
        final boolean empty = state.status == RosterViewModel.Status.EMPTY;
        final boolean error = state.status == RosterViewModel.Status.ERROR;
        binding.listMessage.setVisibility(visibleIf(empty || error));
        if (empty) {
            binding.listMessageTitle.setText(R.string.roster_empty_title);
            binding.listMessageBody.setText(R.string.roster_empty_body);
            binding.listMessageAction.setText(R.string.add_title);
        } else if (error) {
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
        } else if (state.invalidFile) {
            binding.listNotice.setText(R.string.import_invalid_file);
        }
        binding.listNotice.setVisibility(visibleIf(state.fileError != null || state.invalidFile));
        if (state.openPreview) {
            importViewModel.onPreviewOpened();
            final NavController navController = NavHostFragment.findNavController(this);
            final NavDestination current = navController.getCurrentDestination();
            if (current != null && current.getId() == R.id.rosterFragment) {
                navController.navigate(RosterFragmentDirections.actionRosterToImport());
            }
        }
    }

    /** One section per cohort; each row is a roll number with its address. */
    @NonNull
    private List<Line> lines(@NonNull List<RosterEntry> entries) {
        final List<Line> lines = new ArrayList<>();
        String cohort = null;
        for (RosterEntry entry : entries) {
            if (!entry.getCohort().equals(cohort)) {
                cohort = entry.getCohort();
                lines.add(Line.header(cohort, getString(R.string.roster_cohort_header, cohort)));
            }
            lines.add(
                    Line.row(
                            entry.getEmail(),
                            entry.getRollNo(),
                            entry.getEmail(),
                            entry.isRegistered() ? getString(R.string.roster_registered) : null,
                            Line.Tone.NEUTRAL));
        }
        return Line.grouped(lines);
    }

    private boolean onMenuItem(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_add_student) {
            showSheet(new AddStudentSheet());
            return true;
        }
        if (item.getItemId() == R.id.action_import) {
            pickCsv.launch(CSV_TYPES);
            return true;
        }
        return false;
    }

    private void onMessageAction() {
        final RosterViewModel.State state = rosterViewModel.getState().getValue();
        if (state != null && state.status == RosterViewModel.Status.ERROR) {
            rosterViewModel.refresh();
        } else {
            showSheet(new AddStudentSheet());
        }
    }

    private void openEntry(@NonNull String email) {
        for (RosterEntry entry : rosterViewModel.entries()) {
            if (entry.getEmail().equals(email)) {
                showSheet(RosterEntrySheet.newInstance(entry));
                return;
            }
        }
    }

    private void showSheet(@NonNull androidx.fragment.app.DialogFragment sheet) {
        if (getChildFragmentManager().findFragmentByTag(TAG_SHEET) == null) {
            sheet.show(getChildFragmentManager(), TAG_SHEET);
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
        final RosterViewModel roster = rosterViewModel;
        final RosterImportViewModel importer = importViewModel;
        executors
                .io()
                .execute(() -> {
                    final String text = readText(resolver, uri);
                    // Delivered to the ViewModels, which outlive this fragment's view.
                    executors.main().execute(() -> importer.onFile(text, roster.entries()));
                });
    }

    /** The file as UTF-8 text, or null if it cannot be read or is larger than the limit. */
    @Nullable
    @WorkerThread
    private static String readText(@NonNull ContentResolver resolver, @NonNull Uri uri) {
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
