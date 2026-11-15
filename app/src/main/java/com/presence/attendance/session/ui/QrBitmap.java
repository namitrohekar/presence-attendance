package com.presence.attendance.session.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import com.google.zxing.common.BitMatrix;
import com.presence.attendance.session.domain.QrMatrix;

/** Turns QR text into a drawable with hard-edged modules. */
final class QrBitmap {

    private QrBitmap() {}

    /**
     * A bitmap of one pixel per module, scaled by the view without filtering, so module edges stay
     * sharp at any size. Small enough to build on the main thread.
     */
    @NonNull
    static BitmapDrawable render(
            @NonNull Resources resources,
            @NonNull String text,
            @ColorInt int dark,
            @ColorInt int light) {
        final BitMatrix matrix = QrMatrix.encode(text);
        final int size = matrix.getWidth();
        final int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = matrix.get(x, y) ? dark : light;
            }
        }
        final Bitmap bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565);
        final BitmapDrawable drawable = new BitmapDrawable(resources, bitmap);
        drawable.setFilterBitmap(false);
        drawable.setAntiAlias(false);
        return drawable;
    }
}
