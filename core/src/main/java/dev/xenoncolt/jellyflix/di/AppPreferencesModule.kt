package dev.xenoncolt.jellyflix.di

import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.xenoncolt.jellyflix.AppPreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppPreferencesModule {
    @Singleton
    @Provides
    fun provideAppPreferences(sp: SharedPreferences): AppPreferences {
        return AppPreferences(sp)
    }
}
