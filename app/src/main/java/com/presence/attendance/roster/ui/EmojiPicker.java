package com.presence.attendance.roster.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.presence.attendance.R;
import com.presence.attendance.roster.domain.StudentEmoji;

/**
 * Fills a chip group with the ten student emojis, at most one selected, and reads the choice. The
 * chips get new ids each time, so the choice is carried across a rotation by {@link #save} and
 * {@link #restore}.
 */
final class EmojiPicker {

    private static final String KEY_EMOJI = "emojiPicker.selected";

    private EmojiPicker() {}

    static void save(@NonNull ChipGroup group, @NonNull Bundle outState) {
        final StudentEmoji selected = selected(group);
        if (selected != null) {
            outState.putString(KEY_EMOJI, selected.name());
        }
    }

    /** The emoji saved by {@link #save}, or null. */
    @Nullable
    static StudentEmoji restore(@Nullable Bundle savedState) {
        return savedState == null ? null : StudentEmoji.fromName(savedState.getString(KEY_EMOJI));
    }

    /** Adds one chip per emoji, selecting {@code selected} if given. Call once per view. */
    static void fill(@NonNull ChipGroup group, @Nullable StudentEmoji selected) {
        final LayoutInflater inflater = LayoutInflater.from(group.getContext());
        for (StudentEmoji emoji : StudentEmoji.values()) {
            final Chip chip = (Chip) inflater.inflate(R.layout.item_emoji_chip, group, false);
            chip.setId(View.generateViewId());
            chip.setTag(emoji);
            chip.setText(emoji.glyph());
            chip.setContentDescription(group.getContext().getString(label(emoji)));
            chip.setChecked(emoji == selected);
            group.addView(chip);
        }
    }

    /** The emoji whose chip is selected, or null if none is. */
    @Nullable
    static StudentEmoji selected(@NonNull ChipGroup group) {
        final View chip = group.findViewById(group.getCheckedChipId());
        return chip != null ? (StudentEmoji) chip.getTag() : null;
    }

    @StringRes
    private static int label(@NonNull StudentEmoji emoji) {
        return switch (emoji) {
            case LION -> R.string.emoji_lion;
            case TIGER -> R.string.emoji_tiger;
            case ELEPHANT -> R.string.emoji_elephant;
            case PANDA -> R.string.emoji_panda;
            case FOX -> R.string.emoji_fox;
            case OWL -> R.string.emoji_owl;
            case DOLPHIN -> R.string.emoji_dolphin;
            case TURTLE -> R.string.emoji_turtle;
            case BUTTERFLY -> R.string.emoji_butterfly;
            case PEACOCK -> R.string.emoji_peacock;
        };
    }
}
