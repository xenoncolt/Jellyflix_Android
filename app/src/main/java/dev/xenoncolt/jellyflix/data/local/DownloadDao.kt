package dev.xenoncolt.jellyflix.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE infoHash = :infoHash LIMIT 1")
    suspend fun find(infoHash: String): DownloadEntity?

    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadEntity>

    /** Used by the detail screen to show "already downloading / downloaded". */
    @Query("SELECT * FROM downloads WHERE mediaId = :mediaId AND mediaType = :mediaType")
    fun observeForMedia(
        mediaId: Int,
        mediaType: dev.xenoncolt.jellyflix.data.remote.dto.MediaType,
    ): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query(
        """
        UPDATE downloads
        SET progress = :progress,
            downloadedBytes = :downloadedBytes,
            state = :state
        WHERE infoHash = :infoHash
        """
    )
    suspend fun updateProgress(
        infoHash: String,
        progress: Float,
        downloadedBytes: Long,
        state: DownloadState,
    )

    @Query("UPDATE downloads SET state = :state WHERE infoHash = :infoHash")
    suspend fun updateState(infoHash: String, state: DownloadState)

    @Query(
        """
        UPDATE downloads
        SET state = :state, errorMessage = :message
        WHERE infoHash = :infoHash
        """
    )
    suspend fun markFailed(
        infoHash: String,
        message: String,
        state: DownloadState = DownloadState.Failed,
    )

    @Query(
        """
        UPDATE downloads
        SET state = :state, progress = 1.0, completedAt = :completedAt
        WHERE infoHash = :infoHash
        """
    )
    suspend fun markCompleted(
        infoHash: String,
        completedAt: Long = System.currentTimeMillis(),
        state: DownloadState = DownloadState.Completed,
    )

    @Query("UPDATE downloads SET totalBytes = :totalBytes WHERE infoHash = :infoHash")
    suspend fun updateTotalBytes(infoHash: String, totalBytes: Long)

    /** Clears a previous failure so a successful retry does not keep the old error. */
    @Query("UPDATE downloads SET errorMessage = NULL WHERE infoHash = :infoHash")
    suspend fun clearError(infoHash: String)

    @Query("DELETE FROM downloads WHERE infoHash = :infoHash")
    suspend fun delete(infoHash: String)

}
