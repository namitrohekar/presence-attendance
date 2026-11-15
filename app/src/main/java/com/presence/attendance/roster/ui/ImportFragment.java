package com.presence.attendance.roster.ui;

import android.os.Bundle;
import android.view.View;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.R;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.databinding.FragmentListBinding;
import com.presence.attendance.roster.domain.RosterCsv;
import com.presence.attendance.roster.ui.RosterImportViewModel.Phase;
import com.presence.attendance.roster.ui.RosterImportViewModel.Result;
import java.util.ArrayList;
import java.util.List;

/**
 * Faculty: preview of a validated class list, then the import and its per-row result. Every row of
 * the file is listed with what will happen, or what happened, to it.
 */
public final class ImportFragment extends Fragment {

    private RosterImportViewModel importViewModel;
    private RosterViewModel rosterViewModel;

    public ImportFragment() {
        super(R.layout.fragment_list);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentListBinding binding = FragmentListBinding.bind(view);
        WindowInsetsPadding.applyHorizontal(binding.getRoot());
        WindowInsetsPadding.applyBottom(binding.listContent);

        final ViewModelStoreOwner owner =
                NavHostFragment.findNavController(this).getBackStackEntry(R.id.rosterFragment);
        importViewModel =
                new ViewModelProvider(
                                owner,
                                ViewModels.factory(c ->
                                        new RosterImportViewModel(
                                                c.auth(), c.roster(), c.online())))
                        .get(RosterImportViewModel.class);
        rosterViewModel =
                new ViewModelProvider(
                                owner, ViewModels.factory(c -> new RosterViewModel(c.roster())))
                        .get(RosterViewModel.class);

        binding.listToolbar.setTitle(R.string.import_title);
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
        importViewModel
                .getState()
                .observe(getViewLifecycleOwner(), state -> render(binding, adapter, state));
    }

    private void render(
            @NonNull FragmentListBinding binding,
            @NonNull LineAdapter adapter,
            @NonNull RosterImportViewModel.State state) {
        if (state.phase == Phase.IDLE) {
            // Nothing to show: the file was forgotten, or the process was restarted.
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }
        adapter.submitList(lines(state));
        final int ready = state.total;
        final int skipped = state.count(RosterCsv.Check.ALREADY_ON_ROSTER);
        final int rejected = state.rows.size() - ready - skipped;
        switch (state.phase) {
            case PREVIEW -> {
                binding.listSummary.setText(
                        getString(R.string.import_summary, ready, rejected, skipped));
                binding.listPrimary.setText(
                        ready == 0
                                ? getString(R.string.import_nothing)
                                : getResources()
                                        .getQuantityString(R.plurals.import_button, ready, ready));
                binding.listPrimary.setEnabled(ready > 0 && state.online);
                if (!state.online) {
                    binding.listSummary.setText(R.string.roster_offline);
                }
            }
            case IMPORTING -> {
                binding.listSummary.setText(
                        getString(R.string.import_progress, state.results.size(), ready));
                binding.listPrimary.setText(R.string.add_submitting);
                binding.listPrimary.setEnabled(false);
            }
            default -> {
                final int imported = state.count(Result.IMPORTED);
                binding.listSummary.setText(
                        getString(R.string.import_done_summary, imported, ready - imported));
                binding.listPrimary.setText(R.string.action_done);
                binding.listPrimary.setEnabled(true);
            }
        }
    }

    /** Rejected rows first, so problems are seen without scrolling. */
    @NonNull
    private List<Line> lines(@NonNull RosterImportViewModel.State state) {
        final List<Line> problems = new ArrayList<>();
        final List<Line> others = new ArrayList<>();
        for (RosterCsv.Row row : state.rows) {
            final Result result = state.results.get(row.line);
            final String title =
                    row.email.isEmpty() ? getString(R.string.import_row_line, row.line) : row.email;
            final String status = getString(result != null ? label(result) : label(row.check));
            final String detail =
                    row.rollNo.isEmpty()
                            ? status
                            : getString(R.string.import_row_detail, row.rollNo, row.cohort)
                                    + "\n"
                                    + status;
            final boolean ok =
                    result == Result.IMPORTED
                            || (result == null && row.check == RosterCsv.Check.READY);
            final boolean skipped =
                    result == null && row.check == RosterCsv.Check.ALREADY_ON_ROSTER;
            final Line line =
                    Line.row(
                            String.valueOf(row.line),
                            title,
                            detail,
                            ok
                                    ? null
                                    : getString(
                                            skipped
                                                    ? R.string.badge_skipped
                                                    : R.string.badge_rejected),
                            skipped ? Line.Tone.NEUTRAL : Line.Tone.DANGER);
            (ok ? others : problems).add(line);
        }
        problems.addAll(others);
        return Line.grouped(problems);
    }

    @StringRes
    private static int label(@NonNull RosterCsv.Check check) {
        return switch (check) {
            case READY -> R.string.check_ready;
            case MALFORMED -> R.string.check_malformed;
            case INVALID_EMAIL -> R.string.check_invalid_email;
            case INVALID_ROLL_NO -> R.string.check_invalid_roll;
            case INVALID_COHORT -> R.string.check_invalid_cohort;
            case DUPLICATE_EMAIL -> R.string.check_duplicate_email;
            case DUPLICATE_ROLL_NO -> R.string.check_duplicate_roll;
            case ALREADY_ON_ROSTER -> R.string.check_already;
            case EMAIL_ON_ROSTER -> R.string.check_email_on_roster;
            case ROLL_TAKEN -> R.string.check_roll_taken;
        };
    }

    @StringRes
    private static int label(@NonNull Result result) {
        return switch (result) {
            case IMPORTED -> R.string.outcome_imported;
            case EMAIL_EXISTS -> R.string.add_email_exists;
            case ROLL_TAKEN -> R.string.check_roll_taken;
            case FAILED -> R.string.outcome_failed;
        };
    }

    private void onPrimary() {
        final RosterImportViewModel.State state = importViewModel.getState().getValue();
        if (state != null && state.phase == Phase.PREVIEW) {
            importViewModel.startImport();
        } else {
            leave();
        }
    }

    /** Back is ignored while rows are being written; otherwise the file is forgotten. */
    private void leave() {
        final RosterImportViewModel.State state = importViewModel.getState().getValue();
        if (state != null && state.phase == Phase.IMPORTING) {
            return;
        }
        if (state != null && state.phase == Phase.DONE) {
            rosterViewModel.refresh();
        }
        // Resetting moves the state to IDLE, which pops this screen in render().
        importViewModel.reset();
    }
}
