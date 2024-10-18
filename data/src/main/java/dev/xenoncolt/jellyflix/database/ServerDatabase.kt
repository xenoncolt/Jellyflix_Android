package dev.xenoncolt.jellyflix.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.xenoncolt.jellyflix.models.FindroidEpisodeDto
import dev.xenoncolt.jellyflix.models.FindroidMediaStreamDto
import dev.xenoncolt.jellyflix.models.FindroidMovieDto
import dev.xenoncolt.jellyflix.models.FindroidSeasonDto
import dev.xenoncolt.jellyflix.models.FindroidShowDto
import dev.xenoncolt.jellyflix.models.FindroidSourceDto
import dev.xenoncolt.jellyflix.models.FindroidUserDataDto
import dev.xenoncolt.jellyflix.models.IntroDto
import dev.xenoncolt.jellyflix.models.Server
import dev.xenoncolt.jellyflix.models.ServerAddress
import dev.xenoncolt.jellyflix.models.TrickPlayManifestDto
import dev.xenoncolt.jellyflix.models.User

@Database(
    entities = [Server::class, ServerAddress::class, User::class, FindroidMovieDto::class, FindroidShowDto::class, FindroidSeasonDto::class, FindroidEpisodeDto::class, FindroidSourceDto::class, FindroidMediaStreamDto::class, TrickPlayManifestDto::class, IntroDto::class, FindroidUserDataDto::class],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
    ],
)
@TypeConverters(Converters::class)
abstract class ServerDatabase : RoomDatabase() {
    abstract fun getServerDatabaseDao(): ServerDatabaseDao
}
