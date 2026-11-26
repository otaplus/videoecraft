# =====================================================================
#  VideoEcraft — Reglas R8 / ProGuard
# =====================================================================

# --- Media3 / ExoPlayer / Transformer ---------------------------------
# Media3 usa reflexión para instanciar renderers y extensiones de audio.
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Los efectos GL exponen shaders GLSL; no deben ofuscarse ni eliminarse.
-keep class androidx.media3.effect.** { *; }
-keep class com.google.common.collect.** { *; }
-dontwarn com.google.common.**

# Mantener los nombres de los shaders personalizados de VideoEcraft
# (Chroma Key, filtros, papeles de transición).
-keep class com.example.videoecraft.engine.effects.** { *; }

# --- Guava (usado por Transformer) ------------------------------------
-dontwarn com.google.errorprone.**
-dontwarn com.google.j2objc.**
-dontwarn java.lang.management.**
-dontwarn sun.misc.**

# --- Room -------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- Coil (decoder de fotogramas de video) ----------------------------
-dontwarn coil3.**

# --- Kotlin / Coroutines ---------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# --- Conservar líneas para informes de crash legibles ----------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
