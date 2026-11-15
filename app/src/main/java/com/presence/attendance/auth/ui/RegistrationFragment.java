package com.presence.attendance.auth.ui;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import com.presence.attendance.R;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.ViewModels;
import com.presence.attendance.core.ui.WindowInsetsPadding;
import com.presence.attendance.databinding.FragmentRegistrationBinding;

/** Shown once to a signed-in student who has not linked their roll number yet. */
public final class RegistrationFragment extends Fragment {

    public RegistrationFragment() {
        super(R.layout.fragment_registration);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final FragmentRegistrationBinding binding = FragmentRegistrationBinding.bind(view);
        WindowInsetsPadding.apply(binding.getRoot());

        final RegistrationViewModel viewModel =
                new ViewModelProvider(
                                this,
                                ViewModels.factory(
                                        c -> new RegistrationViewModel(c.auth(), c.online())))
                        .get(RegistrationViewModel.class);

        final Runnable submit = () -> viewModel.submit(binding.registerRoll.getText().toString());
        binding.registerSubmit.setOnClickListener(v -> submit.run());
        binding.registerRoll.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit.run();
            }
            // Not consumed, so the keyboard still closes.
            return false;
        });
        binding.registerSignOut.setOnClickListener(v -> viewModel.signOut());
        viewModel.getState().observe(getViewLifecycleOwner(), state -> render(binding, state));
    }

    private void render(
            @NonNull FragmentRegistrationBinding binding,
            @NonNull RegistrationViewModel.State state) {
        if (state.step == RegistrationViewModel.Step.REGISTERED) {
            leave(RegistrationFragmentDirections.actionRegistrationToHome());
            return;
        }
        if (state.step == RegistrationViewModel.Step.SIGNED_OUT) {
            GoogleIdTokenRequester.clearState(requireContext());
            leave(RegistrationFragmentDirections.actionRegistrationToSignIn());
            return;
        }
        binding.registerEmail.setText(
                getString(R.string.register_signed_in_as, state.email != null ? state.email : ""));
        binding.registerRollError.setVisibility(state.rollNoInvalid ? View.VISIBLE : View.GONE);
        if (!state.online) {
            binding.registerStatus.setText(R.string.register_offline);
        } else if (state.error != null) {
            binding.registerStatus.setText(AppErrorMessages.of(state.error));
        }
        final boolean showStatus = !state.online || state.error != null;
        binding.registerStatus.setVisibility(showStatus ? View.VISIBLE : View.GONE);
        binding.registerSubmit.setEnabled(state.online && !state.busy);
        binding.registerSubmit.setText(
                state.busy ? R.string.register_submitting : R.string.register_submit);
        binding.registerSignOut.setEnabled(!state.busy);
    }

    private void leave(@NonNull NavDirections directions) {
        final NavController navController = NavHostFragment.findNavController(this);
        final NavDestination current = navController.getCurrentDestination();
        if (current != null && current.getId() == R.id.registrationFragment) {
            navController.navigate(directions);
        }
    }
}
