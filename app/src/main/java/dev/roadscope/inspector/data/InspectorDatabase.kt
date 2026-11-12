package dev.roadscope.inspector.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Dao
interface TelemetryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(events: List<TelemetryEvent>)

    @Query("SELECT * FROM telemetry_events ORDER BY id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<TelemetryEvent>

    @Query("SELECT * FROM telemetry_events WHERE id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun pageAfter(afterId: Long, limit: Int): List<TelemetryEvent>

    @Query("SELECT COUNT(*) FROM telemetry_events")
    suspend fun count(): Long
}

@Database(entities = [TelemetryEvent::class], version = 1, exportSchema = true)
abstract class InspectorDatabase : RoomDatabase() {
    abstract fun telemetryDao(): TelemetryDao

    companion object {
        @Volatile private var instance: InspectorDatabase? = null

        fun get(context: Context): InspectorDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                InspectorDatabase::class.java,
                "roadscope.db",
            ).build().also { instance = it }
        }
    }
}
