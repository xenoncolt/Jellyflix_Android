# --- libtorrent4j -----------------------------------------------------------
# The JNI layer looks these up by name from native code, so R8 must not touch
# their names or strip "unused" members.
-keep class org.libtorrent4j.** { *; }
-keep class com.frostwire.jlibtorrent.** { *; }
-dontwarn org.libtorrent4j.**

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

# --- Misc -------------------------------------------------------------------
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
