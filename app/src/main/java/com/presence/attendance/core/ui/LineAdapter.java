package com.presence.attendance.core.ui;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.presence.attendance.R;
import com.presence.attendance.databinding.ItemHeaderBinding;
import com.presence.attendance.databinding.ItemLineBinding;
import java.util.function.Consumer;

/** Renders {@link Line}s as inset-grouped sections. Used by every list screen. */
public final class LineAdapter extends ListAdapter<Line, RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;

    private static final DiffUtil.ItemCallback<Line> DIFF =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull Line a, @NonNull Line b) {
                    return a.header == b.header && a.key.equals(b.key);
                }

                @Override
                public boolean areContentsTheSame(@NonNull Line a, @NonNull Line b) {
                    return a.equals(b);
                }
            };

    @Nullable private final Consumer<String> onRowClick;

    /**
     * @param onRowClick receives the key of a tapped row, or null if rows are not tappable
     */
    public LineAdapter(@Nullable Consumer<String> onRowClick) {
        super(DIFF);
        this.onRowClick = onRowClick;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).header ? TYPE_HEADER : TYPE_ROW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(ItemHeaderBinding.inflate(inflater, parent, false));
        }
        return new RowHolder(ItemLineBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final Line line = getItem(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).binding.headerTitle.setText(line.title);
        } else {
            bindRow(((RowHolder) holder).binding, line);
        }
    }

    private void bindRow(@NonNull ItemLineBinding binding, @NonNull Line line) {
        binding.lineTitle.setText(line.title);
        binding.lineDetail.setText(line.detail);
        binding.lineDetail.setVisibility(line.detail == null ? View.GONE : View.VISIBLE);
        binding.lineBadge.setText(line.badge);
        binding.lineBadge.setVisibility(line.badge == null ? View.GONE : View.VISIBLE);
        binding.lineBadge.setBackgroundTintList(
                ColorStateList.valueOf(binding.getRoot().getContext().getColor(fill(line.tone))));
        binding.lineRoot.setBackgroundResource(background(line.place));
        final boolean endsSection =
                line.place == Line.Place.LAST || line.place == Line.Place.SINGLE;
        binding.lineSeparator.setVisibility(endsSection ? View.GONE : View.VISIBLE);
        if (onRowClick != null) {
            binding.lineRoot.setOnClickListener(v -> onRowClick.accept(line.key));
        }
    }

    @ColorRes
    private static int fill(@NonNull Line.Tone tone) {
        return switch (tone) {
            case NEUTRAL -> R.color.fill;
            case SUCCESS -> R.color.badge_success;
            case WARNING -> R.color.badge_warning;
            case DANGER -> R.color.badge_danger;
        };
    }

    @DrawableRes
    private static int background(@NonNull Line.Place place) {
        return switch (place) {
            case SINGLE -> R.drawable.bg_row_single;
            case FIRST -> R.drawable.bg_row_first;
            case MIDDLE -> R.drawable.bg_row_middle;
            case LAST -> R.drawable.bg_row_last;
        };
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemHeaderBinding binding;

        HeaderHolder(@NonNull ItemHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        final ItemLineBinding binding;

        RowHolder(@NonNull ItemLineBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
