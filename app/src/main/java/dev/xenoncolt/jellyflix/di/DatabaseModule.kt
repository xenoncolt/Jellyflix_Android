package dev.xenoncolt.jellyflix.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.xenoncolt.jellyflix.data.local.AppDatabase
import dev.xenoncolt.jellyflix.data.local.DownloadDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "movhubs.db")
            // Torrent rows are reconstructible by re-adding a download, so a
            // destructive migration is an acceptable trade for schema churn
            // during development. Add real migrations before a public release.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideDownloadDao(database: AppDatabase): DownloadDao = database.downloadDao()
}
