package com.presence.attendance.roster.ui;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.R;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.Line;
import com.presence.attendance.core.ui.LineAdapter;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.databinding.FragmentListBinding;
import com.presence.attendance.roster.domain.IdentityAlert;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Faculty: rejected identity attempts, newest first, updating live while open. */
public final class AlertsFragment extends Fragment {

    public AlertsFragment() {
        super(R.layout.fragment_list);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentListBinding binding = FragmentListBinding.bind(view);
        WindowInsetsPadding.applyHorizontal(binding.getRoot());
        WindowInsetsPadding.applyBottom(binding.listContent);

        final AlertsViewModel viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(
                                        c -> new AlertsViewModel(c.auth(), c.alerts(), c.roster())))
                        .get(AlertsViewModel.class);

        binding.listToolbar.setTitle(R.string.alerts_title);
        binding.listToolbar.setNavigationOnClickListener(
                v -> NavHostFragment.findNavController(this).popBackStack());
        final LineAdapter adapter = new LineAdapter(null);
        binding.listRecycler.setAdapter(adapter);
        binding.listMessageAction.setText(R.string.action_try_again);
        binding.listMessageAction.setOnClickListener(v -> viewModel.retry());
        viewModel
                .getState()
                .observe(getViewLifecycleOwner(), state -> render(binding, adapter, state));
    }

    private void render(
            @NonNull FragmentListBinding binding,
            @NonNull LineAdapter adapter,
            @NonNull AlertsViewModel.State state) {
        adapter.submitList(lines(state.alerts, state.emailByRollNo));
        binding.listProgress.setVisibility(
                visibleIf(state.status == AlertsViewModel.Status.LOADING));
        binding.listRecycler.setVisibility(
                visibleIf(state.status == AlertsViewModel.Status.CONTENT));
        final boolean empty = state.status == AlertsViewModel.Status.EMPTY;
        final boolean error = state.status == AlertsViewModel.Status.ERROR;
        binding.listMessage.setVisibility(visibleIf(empty || error));
        // An empty list needs no action; a failure offers a retry.
        binding.listMessageAction.setVisibility(visibleIf(error));
        if (empty) {
            binding.listMessageTitle.setText(R.string.alerts_empty_title);
            binding.listMessageBody.setText(R.string.alerts_empty_body);
        } else if (error) {
            final AppError cause = state.error != null ? state.error : AppError.UNKNOWN;
            binding.listMessageTitle.setText(R.string.alerts_error_title);
            binding.listMessageBody.setText(AppErrorMessages.of(cause));
        }
    }

    @NonNull
    private List<Line> lines(
            @NonNull List<IdentityAlert> alerts, @NonNull Map<String, String> emailByRollNo) {
        final List<Line> lines = new ArrayList<>();
        for (IdentityAlert alert : alerts) {
            lines.add(
                    Line.row(
                            alert.getId(),
                            getString(title(alert.getType())),
                            detail(alert, emailByRollNo)));
        }
        return Line.grouped(lines);
    }

    /** Who, what they typed, whose number that is if it is someone else's, and when. */
    @NonNull
    private String detail(
            @NonNull IdentityAlert alert, @NonNull Map<String, String> emailByRollNo) {
        final CharSequence when =
                DateUtils.getRelativeTimeSpanString(
                        alert.getCreatedAtMillis(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS);
        final String entered = alert.getEnteredRollNo();
        if (entered == null) {
            return getString(R.string.alert_detail, alert.getEmail(), when);
        }
        final String owner = emailByRollNo.get(entered);
        final boolean belongsToAnother =
                alert.getType() == IdentityAlert.Type.ROLL_MISMATCH
                        && owner != null
                        && !owner.equals(alert.getEmail());
        return belongsToAnother
                ? getString(R.string.alert_detail_belongs, alert.getEmail(), entered, owner, when)
                : getString(R.string.alert_detail_entered, alert.getEmail(), entered, when);
    }

    @StringRes
    private static int title(@NonNull IdentityAlert.Type type) {
        return switch (type) {
            case NON_VJTI_EMAIL -> R.string.alert_non_vjti;
            case NOT_ON_REGISTER -> R.string.alert_not_on_register;
            case ROLL_MISMATCH -> R.string.alert_roll_mismatch;
            case DUPLICATE_CLAIM -> R.string.alert_duplicate_claim;
        };
    }

    private static int visibleIf(boolean visible) {
        return visible ? View.VISIBLE : View.GONE;
    }
}
