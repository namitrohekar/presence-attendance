package com.presence.attendance.attendance.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import com.presence.attendance.R;
import com.presence.attendance.attendance.domain.MonthGrid;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A month as the register sees it: weekday initials, then one square per day with the day's mark
 * under its number, and a legend that names every mark in words. Tapping a day of the month picks
 * it. TalkBack reads the summary set as the content description; the list below has the detail.
 */
public final class MonthCalendarView extends View {

    private final float density;
    private final float rowHeight;
    private final float headerHeight;
    private final float legendGap;
    private final float legendRow;
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numberToday = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint weekday = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legend = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selected = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint amber = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint red = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF box = new RectF();
    private final String[] legendLabels;
    private final MonthGrid.Mark[] legendMarks = {
        MonthGrid.Mark.ATTENDED,
        MonthGrid.Mark.PARTLY,
        MonthGrid.Mark.MISSED,
        MonthGrid.Mark.REVOKED
    };
    private List<MonthGrid.Day> days = new ArrayList<>();
    @Nullable private LocalDate picked;
    @Nullable private LocalDate today;
    @Nullable private Consumer<LocalDate> onDay;

    public MonthCalendarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        rowHeight = 48 * density;
        headerHeight = 28 * density;
        legendGap = 12 * density;
        legendRow = Math.max(20 * density, sp(18));
        final Typeface regular = ResourcesCompat.getFont(context, R.font.inter_regular);
        final Typeface semibold = ResourcesCompat.getFont(context, R.font.inter_semibold);
        setText(number, regular, sp(15), R.color.text);
        setText(numberToday, semibold, sp(15), R.color.accent);
        setText(weekday, regular, sp(12), R.color.text_secondary);
        setText(legend, regular, sp(12), R.color.text_secondary);
        legend.setTextAlign(Paint.Align.LEFT);
        selected.setColor(context.getColor(R.color.fill));
        setStroke(ink, R.color.accent);
        setStroke(amber, R.color.warning);
        setStroke(red, R.color.danger);
        dot.setColor(context.getColor(R.color.accent));
        legendLabels =
                new String[] {
                    context.getString(R.string.calendar_legend_attended),
                    context.getString(R.string.calendar_legend_partly),
                    context.getString(R.string.calendar_legend_missed),
                    context.getString(R.string.calendar_legend_revoked)
                };
    }

    /** Text sizes follow the user's font size, including Android 14's non-linear scaling. */
    private float sp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }

    private void setText(@NonNull Paint paint, @Nullable Typeface typeface, float size, int color) {
        paint.setTypeface(typeface);
        paint.setTextSize(size);
        paint.setColor(getContext().getColor(color));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFontFeatureSettings("tnum");
    }

    private void setStroke(@NonNull Paint paint, int color) {
        paint.setColor(getContext().getColor(color));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.75f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setDays(
            @NonNull List<MonthGrid.Day> days,
            @Nullable LocalDate picked,
            @NonNull LocalDate today) {
        final boolean resized = days.size() != this.days.size();
        this.days = new ArrayList<>(days);
        this.picked = picked;
        this.today = today;
        if (resized) {
            requestLayout();
        }
        invalidate();
    }

    public void setOnDayClickListener(@Nullable Consumer<LocalDate> onDay) {
        this.onDay = onDay;
        setClickable(onDay != null);
    }

    private int weeks() {
        return days.size() / 7;
    }

    /** The legend's items, laid out left to right, wrapping onto a new line when needed. */
    private int legendLines(float width) {
        int lines = 1;
        float x = 0;
        for (String label : legendLabels) {
            final float item = legendItemWidth(label);
            if (x > 0 && x + item > width) {
                lines++;
                x = 0;
            }
            x += item;
        }
        return lines;
    }

    private float legendItemWidth(@NonNull String label) {
        return 16 * density + 6 * density + legend.measureText(label) + 16 * density;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        final int width = MeasureSpec.getSize(widthSpec);
        final float inner = width - getPaddingLeft() - getPaddingRight();
        final float height =
                headerHeight + weeks() * rowHeight + legendGap + legendLines(inner) * legendRow;
        setMeasuredDimension(width, (int) Math.ceil(height) + getPaddingTop() + getPaddingBottom());
    }

    private float cellWidth() {
        return (getWidth() - getPaddingLeft() - getPaddingRight()) / 7f;
    }

    private float columnLeft(int column) {
        final boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        final int visual = rtl ? 6 - column : column;
        return getPaddingLeft() + visual * cellWidth();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (days.isEmpty()) {
            return;
        }
        final float cell = cellWidth();
        final float top = getPaddingTop();
        final Locale locale = getResources().getConfiguration().getLocales().get(0);
        for (int column = 0; column < 7; column++) {
            final String name =
                    days.get(column).date.getDayOfWeek().getDisplayName(TextStyle.NARROW, locale);
            canvas.drawText(
                    name, columnLeft(column) + cell / 2, top + headerHeight * 0.62f, weekday);
        }
        for (int i = 0; i < days.size(); i++) {
            final MonthGrid.Day day = days.get(i);
            final float left = columnLeft(i % 7);
            final float rowTop = top + headerHeight + (i / 7) * rowHeight;
            final float cx = left + cell / 2;
            if (day.inMonth && day.date.equals(picked)) {
                box.set(
                        left + 2 * density,
                        rowTop + 2 * density,
                        left + cell - 2 * density,
                        rowTop + rowHeight - 2 * density);
                canvas.drawRoundRect(box, 10 * density, 10 * density, selected);
            }
            final Paint text = day.date.equals(today) ? numberToday : number;
            text.setAlpha(day.inMonth ? 255 : 90);
            canvas.drawText(
                    String.valueOf(day.date.getDayOfMonth()), cx, rowTop + rowHeight * 0.45f, text);
            text.setAlpha(255);
            if (day.inMonth) {
                drawMark(canvas, day.mark, cx, rowTop + rowHeight * 0.74f);
            }
        }
        drawLegend(canvas, top + headerHeight + weeks() * rowHeight + legendGap);
    }

    /** One mark, centred on (cx, cy), about 12 dp across. */
    private void drawMark(
            @NonNull Canvas canvas, @NonNull MonthGrid.Mark mark, float cx, float cy) {
        final float u = 6 * density;
        path.reset();
        switch (mark) {
            case ATTENDED:
            case PARTLY:
                path.moveTo(cx - u * 0.85f, cy + u * 0.05f);
                path.lineTo(cx - u * 0.25f, cy + u * 0.6f);
                path.quadTo(cx + u * 0.2f, cy - u * 0.15f, cx + u * 0.9f, cy - u * 0.65f);
                canvas.drawPath(path, mark == MonthGrid.Mark.ATTENDED ? ink : amber);
                break;
            case MISSED:
                canvas.drawCircle(cx, cy, u * 0.55f, amber);
                break;
            case REVOKED:
                path.moveTo(cx - u * 0.6f, cy + u * 0.6f);
                path.lineTo(cx + u * 0.6f, cy - u * 0.6f);
                canvas.drawPath(path, red);
                break;
            case RUNNING:
                canvas.drawCircle(cx, cy, u * 0.35f, dot);
                break;
            default:
                break;
        }
    }

    private void drawLegend(@NonNull Canvas canvas, float top) {
        final float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float x = 0;
        float y = top;
        final Paint.FontMetrics metrics = legend.getFontMetrics();
        for (int i = 0; i < legendLabels.length; i++) {
            final float item = legendItemWidth(legendLabels[i]);
            if (x > 0 && x + item > width) {
                x = 0;
                y += legendRow;
            }
            final float centre = y + legendRow / 2;
            drawMark(canvas, legendMarks[i], getPaddingLeft() + x + 8 * density, centre);
            canvas.drawText(
                    legendLabels[i],
                    getPaddingLeft() + x + 22 * density,
                    centre - (metrics.ascent + metrics.descent) / 2,
                    legend);
            x += item;
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (onDay == null || days.isEmpty()) {
            return super.onTouchEvent(event);
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return super.onTouchEvent(event);
        }
        final float y = event.getY() - getPaddingTop() - headerHeight;
        final int row = (int) Math.floor(y / rowHeight);
        int column = (int) Math.floor((event.getX() - getPaddingLeft()) / cellWidth());
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            column = 6 - column;
        }
        if (y >= 0 && row < weeks() && column >= 0 && column < 7) {
            final MonthGrid.Day day = days.get(row * 7 + column);
            if (day.inMonth) {
                performClick();
                onDay.accept(day.date);
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
