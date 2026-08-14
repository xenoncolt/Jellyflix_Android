# --- Native / JNI -----------------------------------------------------------
# Everything in this section exists because R8 is a static analyser and JNI is
# not static: native code resolves Java classes, fields and methods by name at
# runtime, so R8 sees them as unreachable and removes or renames them. The
# result is a release build that crashes where the debug build is fine, which is
# exactly what happened here — debug has minification off.

# Native method names are resolved from C by name. This is deliberately global
# rather than per-library: every dependency with a native layer needs it.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# --- libtorrent4j -----------------------------------------------------------
-keep class org.libtorrent4j.** { *; }
-keep interface org.libtorrent4j.** { *; }
-keep class com.frostwire.jlibtorrent.** { *; }
-dontwarn org.libtorrent4j.**

# --- Media3 / FFmpeg decoder ------------------------------------------------
# Media3 ships its own consumer rules, so it needs nothing here. The FFmpeg
# extension does need this one: DefaultRenderersFactory finds the renderer by
# reflecting on its class name, so R8 sees no reference to it and would drop the
# very thing that decodes DTS and TrueHD.
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer { *; }
-keep class androidx.media3.decoder.ffmpeg.FfmpegLibrary { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**

# --- Enums stored by name ---------------------------------------------------
# DownloadState is persisted into Room as its constant name and read back with
# valueOf. Obfuscating the constants would not crash — it would quietly reset
# every saved download to Queued, which is worse.
-keepclassmembers enum ** { *; }

# --- kotlinx.serialization --------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.xenoncolt.jellyflix.** {
    *** Companion;
}
-keepclasseswithmembers class dev.xenoncolt.jellyflix.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.xenoncolt.jellyflix.**$$serializer { *; }

# --- Retrofit / OkHttp ------------------------------------------------------
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# --- Diagnosability ---------------------------------------------------------
# Keeps file and line numbers in release stack traces so a crash report from a
# user is actually actionable; the class names stay obfuscated and are recovered
# with build/outputs/mapping/release/mapping.txt. Without this a release crash
# is an unreadable frame list, which is precisely the position this build was in.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Misc -------------------------------------------------------------------
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
