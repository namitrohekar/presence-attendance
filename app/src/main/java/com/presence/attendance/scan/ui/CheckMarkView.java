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

/** A check mark that draws itself once, the app's single success animation. */
public final class CheckMarkView extends View {

    private static final long DRAW_MS = 400;
    // The check's three points, as fractions of the view's size.
    private static final float START_X = 0.20f;
    private static final float START_Y = 0.53f;
    private static final float ELBOW_X = 0.42f;
    private static final float ELBOW_Y = 0.74f;
    private static final float END_X = 0.82f;
    private static final float END_Y = 0.30f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        paint.setColor(context.getColor(R.color.success));
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
        full.lineTo(width * END_X, height * END_Y);
        measure.setPath(full, false);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
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
