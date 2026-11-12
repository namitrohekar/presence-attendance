package com.presence.attendance.core.ui;

import android.app.Dialog;
import androidx.annotation.LayoutRes;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/** Base for the app's bottom sheets: always opens at full height, never rests half open. */
public abstract class SheetFragment extends BottomSheetDialogFragment {

    protected SheetFragment(@LayoutRes int contentLayoutId) {
        super(contentLayoutId);
    }

    @Override
    public void onStart() {
        super.onStart();
        final Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog) {
            final BottomSheetBehavior<?> behavior = ((BottomSheetDialog) dialog).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }
}
