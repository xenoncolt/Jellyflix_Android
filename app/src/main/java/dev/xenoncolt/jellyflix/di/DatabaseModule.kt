package dev.xenoncolt.jellyflix.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.xenoncolt.jellyflix.data.local.AppDatabase
import dev.xenoncolt.jellyflix.data.local.DownloadDao
import dev.xenoncolt.jellyflix.data.local.PlaybackDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Adds the playback position table.
     *
     * Written out rather than left to the destructive fallback: that drops every
     * table, and by 1.0.1 people have real download lists in there. Purely
     * additive, so nothing existing is touched.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playback_positions` (
                    `filePath` TEXT NOT NULL,
                    `infoHash` TEXT NOT NULL,
                    `positionMs` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`filePath`)
                )
                """.trimIndent()
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playback_positions_infoHash` " +
                    "ON `playback_positions` (`infoHash`)"
            )
        }
    }

    /**
     * Adds the episode column.
     *
     * Nullable and additive, so every existing row stays valid — an older
     * download simply has no episode, which is exactly what a season pack means.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `downloads` ADD COLUMN `episode` INTEGER")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "movhubs.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            // Still the last resort for a schema that predates the migrations:
            // torrent rows can be rebuilt by re-adding a download.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideDownloadDao(database: AppDatabase): DownloadDao = database.downloadDao()

    @Provides
    @Singleton
    fun providePlaybackDao(database: AppDatabase): PlaybackDao = database.playbackDao()
}
