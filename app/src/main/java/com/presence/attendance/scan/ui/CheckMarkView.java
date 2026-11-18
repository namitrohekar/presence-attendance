package com.presence.attendance.scan.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.R;

/**
 * The register's ink tick on a paper disc, drawing itself once: the app's single success animation,
 * and the same mark as the app icon.
 */
public final class CheckMarkView extends View {

    private static final long DRAW_MS = 400;
    // The tick, as fractions of the view's size: a short down-stroke, then a long up-stroke
    // that lifts slightly, as a pen does. The same shape as the app icon.
    private static final float START_X = 0.30f;
    private static final float START_Y = 0.52f;
    private static final float ELBOW_X = 0.44f;
    private static final float ELBOW_Y = 0.65f;
    private static final float CONTROL_X = 0.54f;
    private static final float CONTROL_Y = 0.50f;
    private static final float END_X = 0.71f;
    private static final float END_Y = 0.36f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path full = new Path();
    private final Path drawn = new Path();
    private final PathMeasure measure = new PathMeasure();
    private float fraction = 1f;
    @Nullable private ValueAnimator animator;

    public CheckMarkView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(context.getColor(R.color.accent));
        disc.setColor(context.getColor(R.color.tick_disc));
        paint.setStrokeWidth(getResources().getDimension(R.dimen.check_stroke));
    }

    /** Draws the check from nothing. With animations turned off it simply appears. */
    public void play() {
        if (animator != null) {
            animator.cancel();
        }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            fraction = 1f;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DRAW_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            fraction = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        full.reset();
        full.moveTo(width * START_X, height * START_Y);
        full.lineTo(width * ELBOW_X, height * ELBOW_Y);
        full.quadTo(width * CONTROL_X, height * CONTROL_Y, width * END_X, height * END_Y);
        measure.setPath(full, false);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        final float radius = Math.min(getWidth(), getHeight()) / 2f;
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, disc);
        drawn.reset();
        measure.getSegment(0, measure.getLength() * fraction, drawn, true);
        canvas.drawPath(drawn, paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }
}
