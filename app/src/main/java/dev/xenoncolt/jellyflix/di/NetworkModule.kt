package dev.xenoncolt.jellyflix.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.xenoncolt.jellyflix.BuildConfig
import dev.xenoncolt.jellyflix.data.local.SettingsStore
import dev.xenoncolt.jellyflix.data.remote.GithubApi
import dev.xenoncolt.jellyflix.data.remote.JellyflixApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okio.Path.Companion.toOkioPath
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GithubClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true   // server may add fields; never crash a screen over it
        explicitNulls = false
        coerceInputValues = true   // null where we expect a value -> the default
        isLenient = true
    }

    /**
     * Adds the bearer token when we have one.
     *
     * The runBlocking looks alarming but is fine: OkHttp interceptors already
     * run off the main thread, and after the first call the token comes from an
     * in-memory cache without touching DataStore at all.
     */
    @Provides
    @Singleton
    fun provideAuthInterceptor(settings: SettingsStore): Interceptor = Interceptor { chain ->
        val token = settings.cachedTokenOrNull() ?: runBlocking { settings.currentToken() }
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        chain.proceed(request)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        authInterceptor: Interceptor,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            // Tier-2 torrent search takes ~13s on the server (measured ~18s in
            // practice), so the read timeout needs plenty of headroom over that.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // 10 MB HTTP cache; the server already sets sane cache headers.
            .cache(Cache(context.cacheDir.resolve("http"), 10L * 1024 * 1024))
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideMovHubsApi(retrofit: Retrofit): JellyflixApi = retrofit.create(JellyflixApi::class.java)

    /**
     * Separate Retrofit for GitHub — different host, and it must never carry
     * the movhubs bearer token, so it gets a bare client with no auth
     * interceptor on it.
     */
    @Provides
    @Singleton
    @GithubClient
    fun provideGithubRetrofit(json: Json): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideGithubApi(@GithubClient retrofit: Retrofit): GithubApi =
        retrofit.create(GithubApi::class.java)

    /**
     * One shared Coil loader for the whole app. Poster grids scroll past
     * hundreds of images, and a generous memory cache is the difference between
     * a smooth grid and re-decoding on every fling.
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
        }
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .apply { if (BuildConfig.DEBUG) logger(DebugLogger()) }
        .build()
}
