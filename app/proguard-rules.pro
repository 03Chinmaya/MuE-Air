# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class *_Impl { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }

# Hilt / Dagger
-keep class com.mue.app.Hilt_* { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class dagger.hilt.** { *; }
-keep class com.google.dagger.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject *;
    @dagger.Provides *;
}

# Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.mue.**$$serializer { *; }
-keepclassmembers class com.mue.** { *** Companion; }
-keep class com.mue.core.model.** { *; }
-keep class com.mue.core.auth.** { *; }
-keep class com.mue.core.updates.** { *; }

# Signal Protocol
-keep class org.signal.libsignal.** { *; }
-dontwarn org.signal.libsignal.**
-keepclasseswithmembernames class * {
    native <methods>;
}

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Socket.io
-keep class io.socket.** { *; }
-dontwarn io.socket.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# UnifiedPush transitive dependency; WebPush path is unused (push disabled for v1.0).
-dontwarn com.google.crypto.tink.apps.webpush.WebPushHybridDecrypt$Builder
-dontwarn com.google.crypto.tink.apps.webpush.WebPushHybridDecrypt

