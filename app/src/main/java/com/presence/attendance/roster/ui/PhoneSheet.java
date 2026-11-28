package com.presence.attendance.roster.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.PresenceApp;
import com.presence.attendance.R;
import com.presence.attendance.core.ui.AppErrorMessages;
import com.presence.attendance.core.ui.SheetFragment;
import com.presence.attendance.databinding.SheetConfirmBinding;

/**
 * HOD: confirm moving a student's account to a new phone. One phone per account; the old phone is
 * forgotten and the next one the student signs in on is bound.
 */
public final class PhoneSheet extends SheetFragment {

    static final String TAG = "phone_sheet";
    private static final String ARG_UID = "uid";
    private static final String ARG_NAME = "name";

    public PhoneSheet() {
        super(R.layout.sheet_confirm);
    }

    @NonNull
    static PhoneSheet newInstance(@NonNull String uid, @NonNull String name) {
        final Bundle args = new Bundle();
        args.putString(ARG_UID, uid);
        args.putString(ARG_NAME, name);
        final PhoneSheet sheet = new PhoneSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final SheetConfirmBinding binding = SheetConfirmBinding.bind(view);
        final String uid = requireArguments().getString(ARG_UID, "");
        binding.confirmTitle.setText(
                getString(R.string.phone_title, requireArguments().getString(ARG_NAME, "")));
        binding.confirmBody.setText(R.string.phone_body);
        binding.confirmYes.setText(R.string.phone_confirm);
        binding.confirmNo.setOnClickListener(v -> dismiss());
        binding.confirmYes.setOnClickListener(v -> {
            binding.confirmYes.setEnabled(false);
            binding.confirmStatus.setVisibility(View.GONE);
            ((PresenceApp) requireContext().getApplicationContext())
                    .getContainer()
                    .roster()
                    .releasePhone(
                            uid,
                            outcome -> {
                                // shortcut: no ViewModel; a rotation mid-write only loses the
                                // confirmation, the write itself still happens.
                                if (!isAdded()) {
                                    return;
                                }
                                if (outcome.isSuccess()) {
                                    dismiss();
                                } else {
                                    binding.confirmYes.setEnabled(true);
                                    binding.confirmStatus.setVisibility(View.VISIBLE);
                                    binding.confirmStatus.setText(
                                            AppErrorMessages.of(outcome.getError()));
                                }
                            });
        });
    }
}
