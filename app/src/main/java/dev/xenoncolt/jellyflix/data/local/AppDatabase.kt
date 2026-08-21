package dev.xenoncolt.jellyflix.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import dev.xenoncolt.jellyflix.data.remote.dto.MediaType

class Converters {
    @TypeConverter fun mediaTypeToString(value: MediaType): String = value.wire

    @TypeConverter fun stringToMediaType(value: String): MediaType = MediaType.fromWire(value)

    @TypeConverter fun downloadStateToString(value: DownloadState): String = value.name

    @TypeConverter
    fun stringToDownloadState(value: String): DownloadState =
        runCatching { DownloadState.valueOf(value) }.getOrDefault(DownloadState.Queued)
}

@Database(
    entities = [DownloadEntity::class, PlaybackPositionEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun playbackDao(): PlaybackDao
}
