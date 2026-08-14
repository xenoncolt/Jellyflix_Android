package dev.xenoncolt.jellyflix.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Where the user got to in a particular video.
 *
 * Keyed by **file path, not info hash**: a season pack is one torrent holding a
 * dozen episodes, and resuming the whole pack at episode 3's timestamp would be
 * nonsense. The info hash is carried alongside so every position for a download
 * can be dropped when the download is.
 */
@Entity(
    tableName = "playback_positions",
    indices = [Index("infoHash")],
)
data class PlaybackPositionEntity(
    @PrimaryKey val filePath: String,
    val infoHash: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /**
     * Treat the last few percent as finished, so a file whose credits were
     * skipped doesn't reopen two seconds from the end forever.
     */
    val isFinished: Boolean
        get() = durationMs > 0 && positionMs >= durationMs - FINISHED_TAIL_MS

    private companion object {
        const val FINISHED_TAIL_MS = 15_000L
    }
}

@Dao
interface PlaybackDao {

    @Query("SELECT * FROM playback_positions WHERE filePath = :filePath")
    suspend fun find(filePath: String): PlaybackPositionEntity?

    @Query("SELECT * FROM playback_positions WHERE infoHash = :infoHash")
    suspend fun forDownload(infoHash: String): List<PlaybackPositionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: PlaybackPositionEntity)

    @Query("DELETE FROM playback_positions WHERE infoHash = :infoHash")
    suspend fun deleteForDownload(infoHash: String)
}
