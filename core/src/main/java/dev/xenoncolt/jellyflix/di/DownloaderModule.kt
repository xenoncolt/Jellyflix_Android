package dev.xenoncolt.jellyflix.di

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.xenoncolt.jellyflix.AppPreferences
import dev.xenoncolt.jellyflix.database.ServerDatabaseDao
import dev.xenoncolt.jellyflix.repository.JellyfinRepository
import dev.xenoncolt.jellyflix.utils.Downloader
import dev.xenoncolt.jellyflix.utils.DownloaderImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloaderModule {
    @Singleton
    @Provides
    fun provideDownloader(
        application: Application,
        serverDatabase: ServerDatabaseDao,
        jellyfinRepository: JellyfinRepository,
        appPreferences: AppPreferences,
    ): Downloader {
        return DownloaderImpl(application, serverDatabase, jellyfinRepository, appPreferences)
    }
}
