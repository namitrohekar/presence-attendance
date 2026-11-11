package com.presence.attendance.core.ui;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Edge-to-edge helper: keeps a screen's content clear of system bars, cutouts and the keyboard. */
public final class WindowInsetsPadding {

    private static final int INSET_TYPES =
            WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime();

    private WindowInsetsPadding() {}

    /** Adds the window insets to the padding the view was laid out with. */
    public static void apply(@NonNull View view) {
        final int left = view.getPaddingLeft();
        final int top = view.getPaddingTop();
        final int right = view.getPaddingRight();
        final int bottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(
                view,
                (v, windowInsets) -> {
                    final Insets insets = windowInsets.getInsets(INSET_TYPES);
                    v.setPadding(
                            left + insets.left,
                            top + insets.top,
                            right + insets.right,
                            bottom + insets.bottom);
                    return windowInsets;
                });
    }
}
