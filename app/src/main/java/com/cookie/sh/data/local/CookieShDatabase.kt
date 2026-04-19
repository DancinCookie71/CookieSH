package com.cookie.sh.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val command: String,
    val outputPreview: String,
    val usedRoot: Boolean,
    val executedAt: Long,
)

@Entity(tableName = "favorite_props")
data class FavoritePropEntity(
    @PrimaryKey val propName: String,
    val pinnedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "saved_logs")
data class SavedLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val filePath: String,
    val summary: String,
    val exportedAt: Long,
)

@Dao
interface CommandHistoryDao {
    @Query("SELECT * FROM command_history ORDER BY executedAt DESC LIMIT 25")
    fun observeHistory(): Flow<List<CommandHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CommandHistoryEntity)

    @Query("DELETE FROM command_history")
    suspend fun clear()
}

@Dao
interface FavoritePropDao {
    @Query("SELECT * FROM favorite_props ORDER BY pinnedAt DESC")
    fun observeFavorites(): Flow<List<FavoritePropEntity>>

    @Upsert
    suspend fun upsert(item: FavoritePropEntity)

    @Query("DELETE FROM favorite_props WHERE propName = :propName")
    suspend fun delete(propName: String)
}

@Dao
interface SavedLogDao {
    @Query("SELECT * FROM saved_logs ORDER BY exportedAt DESC LIMIT 25")
    fun observeSavedLogs(): Flow<List<SavedLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SavedLogEntity)
}

@Database(
    entities = [
        CommandHistoryEntity::class,
        FavoritePropEntity::class,
        SavedLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class CookieShDatabase : RoomDatabase() {
    abstract fun commandHistoryDao(): CommandHistoryDao
    abstract fun favoritePropDao(): FavoritePropDao
    abstract fun savedLogDao(): SavedLogDao
}
