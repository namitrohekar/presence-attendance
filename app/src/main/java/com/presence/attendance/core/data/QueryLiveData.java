package com.presence.attendance.core.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.presence.attendance.core.Outcome;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A Firestore query as LiveData. The snapshot listener is attached only while something is
 * observing, so a live screen costs reads only while it is open. Documents the mapper rejects
 * (returns null for) are left out.
 */
public final class QueryLiveData<T> extends LiveData<Outcome<List<T>>> {

    private final Query query;
    private final Function<DocumentSnapshot, T> mapper;
    @Nullable private ListenerRegistration registration;

    public QueryLiveData(@NonNull Query query, @NonNull Function<DocumentSnapshot, T> mapper) {
        this.query = query;
        this.mapper = mapper;
    }

    @Override
    protected void onActive() {
        registration =
                query.addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) {
                        setValue(Outcome.failure(FirebaseErrors.toAppError(error)));
                        return;
                    }
                    final List<T> items = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        final T item = mapper.apply(document);
                        if (item != null) {
                            items.add(item);
                        }
                    }
                    setValue(Outcome.success(items));
                });
    }

    @Override
    protected void onInactive() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
