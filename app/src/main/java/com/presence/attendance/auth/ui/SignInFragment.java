package com.presence.attendance.auth.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.BuildConfig;
import com.presence.attendance.R;
import com.presence.attendance.core.AppError;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.databinding.FragmentSignInBinding;

public final class SignInFragment extends Fragment {

    public SignInFragment() {
        super(R.layout.fragment_sign_in);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // The binding is scoped to this view, so nothing outlives onDestroyView.
        final FragmentSignInBinding binding = FragmentSignInBinding.bind(view);
        WindowInsetsPadding.apply(binding.getRoot());

        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isEmpty()) {
            // No ViewModel here: its repository needs Firebase, which cannot start in a build
            // without google-services.json or without the Google provider enabled.
            binding.signInContinue.setOnClickListener(
                    v -> showError(binding, AppError.NOT_CONFIGURED));
            return;
        }

        final SignInViewModel viewModel =
                new ViewModelProvider(this, ViewModels.factory(c -> new SignInViewModel(c.auth())))
                        .get(SignInViewModel.class);
        binding.signInContinue.setOnClickListener(v -> {
            viewModel.onSignInStarted();
            // The ViewModel, not this fragment, receives the result, so a rotation
            // while Google's sheet is open does not lose it.
            GoogleIdTokenRequester.request(requireActivity(), viewModel::onGoogleIdToken);
        });
        viewModel.getState().observe(getViewLifecycleOwner(), state -> render(binding, state));
    }

    private void render(
            @NonNull FragmentSignInBinding binding, @NonNull SignInViewModel.State state) {
        final boolean signingIn = state.status == SignInViewModel.Status.SIGNING_IN;
        binding.signInContinue.setEnabled(!signingIn);
        binding.signInProgress.setVisibility(signingIn ? View.VISIBLE : View.GONE);
        if (state.error != null) {
            showError(binding, state.error);
            if (state.error == AppError.NOT_VJTI_ACCOUNT) {
                // The rejected account was signed out; let the next attempt pick another one.
                GoogleIdTokenRequester.clearState(requireContext());
            }
        } else {
            binding.signInError.setVisibility(View.GONE);
        }
        if (state.status == SignInViewModel.Status.SIGNED_IN) {
            leave(SignInFragmentDirections.actionSignInToHome());
        } else if (state.status == SignInViewModel.Status.NEEDS_REGISTRATION) {
            leave(SignInFragmentDirections.actionSignInToRegistration());
        }
    }

    private static void showError(@NonNull FragmentSignInBinding binding, @NonNull AppError error) {
        binding.signInError.setText(AppErrorMessages.of(error));
        binding.signInError.setVisibility(View.VISIBLE);
    }

    private void leave(@NonNull NavDirections directions) {
        final NavController navController = NavHostFragment.findNavController(this);
        final NavDestination current = navController.getCurrentDestination();
        if (current != null && current.getId() == R.id.signInFragment) {
            navController.navigate(directions);
        }
    }
}
