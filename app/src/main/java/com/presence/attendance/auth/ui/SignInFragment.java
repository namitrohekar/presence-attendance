package com.presence.attendance.auth.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.presence.attendance.BuildConfig;
import com.presence.attendance.R;
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
        binding.signInContinue.setOnClickListener(v -> onContinueClicked(binding));
    }

    private void onContinueClicked(@NonNull FragmentSignInBinding binding) {
        // A build without google-services.json has no OAuth client to sign in with.
        if (!BuildConfig.FIREBASE_CONFIGURED) {
            binding.signInError.setText(R.string.sign_in_error_not_configured);
            binding.signInError.setVisibility(View.VISIBLE);
        }
    }
}
