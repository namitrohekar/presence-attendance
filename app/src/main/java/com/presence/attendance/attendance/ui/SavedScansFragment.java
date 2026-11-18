package com.presence.attendance.attendance.ui;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.R;
import com.presence.attendance.attendance.domain.PendingMark;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.databinding.FragmentListBinding;
import java.util.ArrayList;
import java.util.List;

/** Student: scans made without a connection, and what became of each. */
public final class SavedScansFragment extends Fragment {

    private static final int WHEN_FLAGS =
            DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_TIME
                    | DateUtils.FORMAT_SHOW_WEEKDAY
                    | DateUtils.FORMAT_ABBREV_ALL;

    public SavedScansFragment() {
        super(R.layout.fragment_list);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentListBinding binding = FragmentListBinding.bind(view);
        WindowInsetsPadding.applyHorizontal(binding.getRoot());
        WindowInsetsPadding.applyBottom(binding.listContent);

        final SavedScansViewModel viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(c ->
                                        new SavedScansViewModel(c.pendingMarks(), c::requestSync)))
                        .get(SavedScansViewModel.class);
        viewModel.setStudent(SavedScansFragmentArgs.fromBundle(requireArguments()).getStudentId());

        binding.listToolbar.setTitle(R.string.saved_title);
        binding.listToolbar.setNavigationOnClickListener(
                v -> NavHostFragment.findNavController(this).popBackStack());
        final LineAdapter adapter = new LineAdapter(viewModel::dismiss);
        binding.listRecycler.setAdapter(adapter);
        binding.listProgress.setVisibility(View.GONE);
        binding.listMessageAction.setVisibility(View.GONE);
        binding.listMessageTitle.setText(R.string.saved_empty_title);
        binding.listMessageBody.setText(R.string.saved_empty_body);
        binding.listPrimary.setVisibility(View.GONE);
        binding.listSummary.setText(R.string.saved_dismiss_hint);

        viewModel
                .getMarks()
                .observe(
                        getViewLifecycleOwner(),
                        saved -> {
                            adapter.submitList(lines(saved));
                            final boolean empty = saved.isEmpty();
                            binding.listRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
                            binding.listMessage.setVisibility(empty ? View.VISIBLE : View.GONE);
                            // The hint applies only when a settled scan is on the list.
                            final boolean settled =
                                    saved.size() > SavedScansViewModel.waiting(saved);
                            binding.listFooter.setVisibility(settled ? View.VISIBLE : View.GONE);
                        });
    }

    @NonNull
    private List<Line> lines(@NonNull List<PendingMark> saved) {
        final List<Line> lines = new ArrayList<>();
        for (PendingMark mark : saved) {
            final String when =
                    DateUtils.formatDateTime(
                            requireContext(), mark.getScannedAtMillis(), WHEN_FLAGS);
            lines.add(Line.row(mark.getSessionId(), when, detail(mark), badge(mark), tone(mark)));
        }
        return Line.grouped(lines);
    }

    @NonNull
    private String detail(@NonNull PendingMark mark) {
        switch (mark.getState()) {
            case PENDING:
                return getString(R.string.saved_pending_detail);
            case SYNCED:
                return getString(R.string.saved_synced_detail);
            case EXPIRED:
                return getString(R.string.saved_expired_detail);
            default:
                final AppError reason = mark.getReason();
                return getString(AppErrorMessages.of(reason != null ? reason : AppError.UNKNOWN));
        }
    }

    @NonNull
    private String badge(@NonNull PendingMark mark) {
        switch (mark.getState()) {
            case PENDING:
                return getString(R.string.saved_pending);
            case SYNCED:
                return getString(R.string.saved_synced);
            case EXPIRED:
                return getString(R.string.saved_expired);
            default:
                return getString(R.string.saved_rejected);
        }
    }

    @NonNull
    private static Line.Tone tone(@NonNull PendingMark mark) {
        switch (mark.getState()) {
            case PENDING:
                return Line.Tone.NEUTRAL;
            case SYNCED:
                return Line.Tone.NEUTRAL;
            case EXPIRED:
                return Line.Tone.WARNING;
            default:
                return Line.Tone.DANGER;
        }
    }
}
