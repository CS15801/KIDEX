package com.sitecouncil.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "analyses")
data class AnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "total_score") val totalScore: Int,
    @ColumnInfo(name = "screenshot_path") val screenshotPath: String,
    @ColumnInfo(name = "result_json") val resultJson: String,
)

/** Schlanke Zeile für die Verlaufsliste, ohne das große Ergebnis-JSON. */
data class HistoryRow(
    val id: Long,
    val url: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "total_score") val totalScore: Int,
)

@Dao
interface AnalysisDao {
    @Insert
    suspend fun insert(entity: AnalysisEntity): Long

    @Query("SELECT id, url, created_at, total_score FROM analyses ORDER BY created_at DESC LIMIT 50")
    fun observeHistory(): Flow<List<HistoryRow>>

    @Query("SELECT * FROM analyses WHERE id = :id")
    suspend fun getById(id: Long): AnalysisEntity?

    @Query("DELETE FROM analyses WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(entities = [AnalysisEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun analysisDao(): AnalysisDao
}
