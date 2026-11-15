package com.presence.attendance.scan.ui;

import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.R;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.databinding.FragmentResultBinding;

/**
 * The outcome of a scan: marked, or not marked with the specific reason and a way to try again. The
 * result is in words, with a haptic and a drawn check as reinforcement, never colour alone.
 */
public final class ResultFragment extends Fragment {

    public ResultFragment() {
        super(R.layout.fragment_result);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentResultBinding binding = FragmentResultBinding.bind(view);
        WindowInsetsPadding.apply(binding.getRoot());
        final AppError rejection =
                rejection(ResultFragmentArgs.fromBundle(requireArguments()).getRejection());
        final NavController navController = NavHostFragment.findNavController(this);
        final boolean firstShow = savedInstanceState == null;

        if (rejection == null) {
            binding.resultCheck.setVisibility(View.VISIBLE);
            binding.resultTitle.setText(R.string.result_marked_title);
            binding.resultBody.setText(R.string.result_marked_body);
            binding.resultPrimary.setText(R.string.action_done);
            binding.resultPrimary.setOnClickListener(v -> navController.popBackStack());
            if (firstShow) {
                binding.resultCheck.play();
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            }
            return;
        }
        binding.resultTitle.setText(R.string.result_rejected_title);
        binding.resultBody.setText(AppErrorMessages.of(rejection));
        binding.resultPrimary.setText(R.string.action_try_scan_again);
        binding.resultPrimary.setOnClickListener(
                v -> navController.navigate(ResultFragmentDirections.actionResultToScan()));
        binding.resultSecondary.setVisibility(View.VISIBLE);
        binding.resultSecondary.setOnClickListener(v -> navController.popBackStack());
        if (firstShow) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT);
        }
    }

    /** The error named in the argument; null when the mark succeeded. Unknown names fail closed. */
    @Nullable
    private static AppError rejection(@NonNull String name) {
        if (name.isEmpty()) {
            return null;
        }
        for (AppError error : AppError.values()) {
            if (error.name().equals(name)) {
                return error;
            }
        }
        return AppError.UNKNOWN;
    }
}
