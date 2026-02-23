# General Rules
-ignorewarnings
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-keep class **.R$* { *; }
-keep class **.R { *; }

# Android Components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View

# Kotlin
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler {
    <init>();
}

# Room Database
-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * implements androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers class * {
    @androidx.room.PrimaryKey *;
    @androidx.room.ColumnInfo *;
    @androidx.room.Ignore *;
    @androidx.room.Relation *;
    @androidx.room.ForeignKey *;
    @androidx.room.Embedded *;
}

# Gson
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }
-dontwarn com.google.gson.**
# Keep generic types
-keepattributes Signature
# Keep models used by Gson (Google APIs and internal)
-keep class com.google.api.services.** { *; }
-keep class com.neubofy.reality.data.** { *; }

# Health Connect
-keep class androidx.health.connect.** { *; }

# Azhon AppUpdate
-keep class com.azhon.** { *; }
-keep class io.github.azhon.** { *; }

# Reality App Specifics
# Keep Nightly Protocol classes specifically mentioned by user as risky
-keep class com.neubofy.reality.data.NightlyProtocolExecutor { *; }
-keep class com.neubofy.reality.data.nightly.** { *; }
-keep class com.neubofy.reality.data.repository.** { *; }
-keep class com.neubofy.reality.utils.ThemeManager { *; } 
-keep class com.neubofy.reality.utils.UpdateManager { *; }
-keep class com.neubofy.reality.utils.SavedPreferencesLoader { *; }
-keep class com.neubofy.reality.blockers.** { *; }

# Google APIs (Drive, Docs, Tasks, Auth)
-keep class com.google.api.services.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.auth.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.auth.**

# Third Party Libraries
-keep class io.noties.markwon.** { *; }
-keep class coil.** { *; }
-keep class org.jsoup.** { *; }
-keep class com.google.zxing.** { *; }

# ML Kit (QR Scanning)
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# iText PDF (Aggressive Rules)
-keep class com.itextpdf.** { *; }
-keep class com.itextpdf.io.** { *; }
-keep class com.itextpdf.kernel.** { *; }
-keep class com.itextpdf.layout.** { *; }
-keep class com.itextpdf.forms.** { *; }
-keep class com.itextpdf.svg.** { *; }
-keep class com.itextpdf.barcodes.** { *; }
-keep class com.itextpdf.styledxmlparser.** { *; }
# Keep BouncyCastle if pulled transitively
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
# Keep Common Logging if used
-keep class org.apache.commons.logging.** { *; }

# Suppress benign warnings
-dontwarn org.ietf.jgss.**
-dontwarn org.jspecify.annotations.**
-dontwarn org.slf4j.**
-dontwarn pl.droidsonroids.gif.**
-dontwarn javax.annotation.**
-dontwarn javax.naming.**
-dontwarn org.apache.commons.logging.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn com.fasterxml.jackson.**
