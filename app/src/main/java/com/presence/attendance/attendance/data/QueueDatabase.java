package com.presence.attendance.attendance.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/** The app's only local database: scans waiting to be sent. */
@Database(
        entities = {PendingMarkEntity.class},
        version = 1,
        exportSchema = false)
public abstract class QueueDatabase extends RoomDatabase {
    public static final String NAME = "queue.db";

    abstract PendingMarkDao pendingMarks();
}
