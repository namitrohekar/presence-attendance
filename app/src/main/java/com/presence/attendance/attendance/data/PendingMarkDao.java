package com.presence.attendance.attendance.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
interface PendingMarkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(@NonNull PendingMarkEntity mark);

    @Query("SELECT * FROM pending_marks WHERE studentId = :studentId ORDER BY scannedAtMillis DESC")
    @NonNull
    LiveData<List<PendingMarkEntity>> observe(@NonNull String studentId);

    @Query("SELECT * FROM pending_marks WHERE studentId = :studentId AND state = 'PENDING'")
    @NonNull
    List<PendingMarkEntity> pending(@NonNull String studentId);

    // The token in the condition: a newer scan of the same session is left alone. The code and
    // the coordinates are not needed once the scan is settled, so they are wiped.
    @Query(
            "UPDATE pending_marks SET state = :state, reason = :reason, token = '', lat = 0,"
                    + " lng = 0, accuracyM = 0 WHERE sessionId = :sessionId AND studentId ="
                    + " :studentId AND token = :token AND state = 'PENDING'")
    void resolve(
            @NonNull String sessionId,
            @NonNull String studentId,
            @NonNull String token,
            @NonNull String state,
            @Nullable String reason);

    @Query(
            "DELETE FROM pending_marks WHERE sessionId = :sessionId AND studentId = :studentId"
                    + " AND state != 'PENDING'")
    void dismiss(@NonNull String sessionId, @NonNull String studentId);
}
