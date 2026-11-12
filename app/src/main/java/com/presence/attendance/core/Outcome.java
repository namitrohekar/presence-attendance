package com.presence.attendance.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** The result of an operation: a value, or the {@link AppError} that prevented it. Immutable. */
public final class Outcome<T> {

    @Nullable private final T value;
    @Nullable private final AppError error;

    private Outcome(@Nullable T value, @Nullable AppError error) {
        this.value = value;
        this.error = error;
    }

    @NonNull
    public static <T> Outcome<T> success(@NonNull T value) {
        return new Outcome<>(value, null);
    }

    @NonNull
    public static <T> Outcome<T> failure(@NonNull AppError error) {
        return new Outcome<>(null, error);
    }

    public boolean isSuccess() {
        return error == null;
    }

    /**
     * @throws IllegalStateException if this outcome is a failure
     */
    @NonNull
    public T getValue() {
        if (value == null) {
            throw new IllegalStateException("No value: outcome failed with " + error);
        }
        return value;
    }

    /** The error, or null if this outcome is a success. */
    @Nullable
    public AppError getError() {
        return error;
    }
}
