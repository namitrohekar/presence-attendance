package com.presence.attendance.attendance.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.presence.attendance.R;
import java.util.ArrayList;
import java.util.List;

/**
 * A student's row in the register: one cell per class held, oldest first, wrapping onto more lines.
 * Attended classes carry the ink tick, missed ones stay empty, a revoked one is struck through.
 * Decorative: the figure and caption above it say the same in words.
 */
public final class RegisterStripView extends View {

    public enum Mark {
        ATTENDED,
        MISSED,
        REVOKED
    }

    private final float cell;
    private final float gap;
    private final float radius;
    private final Paint empty = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint struck = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint struckFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path path = new Path();
    private List<Mark> marks = new ArrayList<>();

    public RegisterStripView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        final float density = getResources().getDisplayMetrics().density;
        cell = 20 * density;
        gap = 4 * density;
        radius = 4 * density;
        // Paper boxes with a hairline, like the squares of a printed register.
        empty.setColor(context.getColor(R.color.surface));
        rule.setColor(context.getColor(R.color.separator));
        rule.setStyle(Paint.Style.STROKE);
        rule.setStrokeWidth(density);
        tick.setColor(context.getColor(R.color.accent));
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeWidth(2 * density);
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setStrokeJoin(Paint.Join.ROUND);
        struck.set(tick);
        struck.setColor(context.getColor(R.color.danger));
        struckFill.setColor(context.getColor(R.color.badge_danger));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setMarks(@NonNull List<Mark> marks) {
        this.marks = new ArrayList<>(marks);
        requestLayout();
        invalidate();
    }

    private int perRow(int width) {
        return Math.max(
                1, (int) ((width - getPaddingLeft() - getPaddingRight() + gap) / (cell + gap)));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        final int width = MeasureSpec.getSize(widthSpec);
        final int rows = marks.isEmpty() ? 0 : (marks.size() + perRow(width) - 1) / perRow(width);
        final float height = rows == 0 ? 0 : rows * cell + (rows - 1) * gap;
        setMeasuredDimension(width, (int) Math.ceil(height) + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        final int perRow = perRow(getWidth());
        final boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (int i = 0; i < marks.size(); i++) {
            final int column = i % perRow;
            final float left =
                    rtl
                            ? getWidth() - getPaddingRight() - (column + 1) * cell - column * gap
                            : getPaddingLeft() + column * (cell + gap);
            final float top = getPaddingTop() + (i / perRow) * (cell + gap);
            box.set(left, top, left + cell, top + cell);
            final Mark mark = marks.get(i);
            canvas.drawRoundRect(box, radius, radius, mark == Mark.REVOKED ? struckFill : empty);
            box.inset(rule.getStrokeWidth() / 2, rule.getStrokeWidth() / 2);
            canvas.drawRoundRect(box, radius, radius, rule);
            path.reset();
            if (mark == Mark.ATTENDED) {
                // The app's tick, scaled into the cell.
                path.moveTo(left + cell * 0.27f, top + cell * 0.53f);
                path.lineTo(left + cell * 0.43f, top + cell * 0.68f);
                path.quadTo(
                        left + cell * 0.55f, top + cell * 0.48f,
                        left + cell * 0.75f, top + cell * 0.32f);
                canvas.drawPath(path, tick);
            } else if (mark == Mark.REVOKED) {
                path.moveTo(left + cell * 0.28f, top + cell * 0.72f);
                path.lineTo(left + cell * 0.72f, top + cell * 0.28f);
                canvas.drawPath(path, struck);
            }
        }
    }
}
