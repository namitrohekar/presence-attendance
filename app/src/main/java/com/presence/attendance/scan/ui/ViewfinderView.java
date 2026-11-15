package com.presence.attendance.scan.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.R;

/** Four corner marks around the middle of the camera preview. A hint, not a crop. */
public final class ViewfinderView extends View {

    /** The marked square's side as a fraction of the view's shorter side. */
    private static final float SIZE_FRACTION = 0.62f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float corner;

    public ViewfinderView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(context.getColor(R.color.scan_on_scrim));
        paint.setStrokeWidth(getResources().getDimension(R.dimen.viewfinder_stroke));
        corner = getResources().getDimension(R.dimen.viewfinder_corner);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        final float side = Math.min(getWidth(), getHeight()) * SIZE_FRACTION;
        final float left = (getWidth() - side) / 2f;
        final float top = (getHeight() - side) / 2f;
        final float right = left + side;
        final float bottom = top + side;
        drawCorner(canvas, left, top, 1, 1);
        drawCorner(canvas, right, top, -1, 1);
        drawCorner(canvas, left, bottom, 1, -1);
        drawCorner(canvas, right, bottom, -1, -1);
    }

    /** Two strokes meeting at (x, y), pointing inwards along dx and dy. */
    private void drawCorner(@NonNull Canvas canvas, float x, float y, int dx, int dy) {
        canvas.drawLine(x, y, x + dx * corner, y, paint);
        canvas.drawLine(x, y, x, y + dy * corner, paint);
    }
}
