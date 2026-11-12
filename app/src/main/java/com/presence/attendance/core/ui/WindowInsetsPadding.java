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

    /** Adds the window insets on every side to the padding the view was laid out with. */
    public static void apply(@NonNull View view) {
        apply(view, true, true, true);
    }

    /**
     * Left and right insets only. For the root of a screen whose content is a centred column: the
     * column is then centred inside the safe area instead of being pushed sideways.
     */
    public static void applyHorizontal(@NonNull View view) {
        apply(view, true, false, false);
    }

    /** Bottom inset only. For scrolling content below an app bar that clears the status bar. */
    public static void applyBottom(@NonNull View view) {
        apply(view, false, false, true);
    }

    private static void apply(@NonNull View view, boolean sides, boolean top, boolean bottom) {
        final int paddingLeft = view.getPaddingLeft();
        final int paddingTop = view.getPaddingTop();
        final int paddingRight = view.getPaddingRight();
        final int paddingBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(
                view,
                (v, windowInsets) -> {
                    final Insets insets = windowInsets.getInsets(INSET_TYPES);
                    v.setPadding(
                            paddingLeft + (sides ? insets.left : 0),
                            paddingTop + (top ? insets.top : 0),
                            paddingRight + (sides ? insets.right : 0),
                            paddingBottom + (bottom ? insets.bottom : 0));
                    return windowInsets;
                });
    }
}
