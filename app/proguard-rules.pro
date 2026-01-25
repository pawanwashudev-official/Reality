# Reality App ProGuard Rules
# Optimized for maximum shrinking while preserving essential functionality

# ========== GENERAL ANDROID ==========
# Keep Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.accessibilityservice.AccessibilityService
-keep public class * extends android.app.admin.DeviceAdminReceiver

# Keep Parcelables
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ========== KOTLIN ==========
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ========== ROOM DATABASE ==========
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * {
    <fields>;
}
-keep @androidx.room.Dao class *
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.**

# ========== REALITY APP SPECIFICS ==========

# Keep all data classes (used for SharedPreferences serialization)
-keep class com.neubofy.reality.Constants$* { *; }
-keepclassmembers class com.neubofy.reality.Constants$* { *; }

# Keep data classes used with Gson
-keepclassmembers class com.neubofy.reality.data.db.* { *; }
-keep class com.neubofy.reality.data.db.* { *; }
-keep class com.neubofy.reality.blockers.RealityBlocker$* { *; }
-keep class com.neubofy.reality.data.CustomReminder { *; }
-keep class com.neubofy.reality.data.model.** { *; }
-keep class com.neubofy.reality.google.GoogleTasksManager$TaskStats { *; }

# Keep Accessibility Service
-keep class com.neubofy.reality.services.AppBlockerService { *; }
-keep class com.neubofy.reality.services.GeneralFeaturesService { *; }
-keep class com.neubofy.reality.services.AlarmService { *; }

# Keep Device Admin Receiver
-keep class com.neubofy.reality.receivers.AdminLockReceiver { *; }
-keep class com.neubofy.reality.receivers.BootReceiver { *; }
-keep class com.neubofy.reality.receivers.ReminderReceiver { *; }
-keep class com.neubofy.reality.utils.AlarmScheduler { *; }
-keep class com.neubofy.reality.services.AlarmService { *; }

# Keep WorkManager Workers
-keep class com.neubofy.reality.workers.* { *; }

# Keep Widget Provider
-keep class com.neubofy.reality.widget.FocusWidgetProvider { *; }

# ========== GSON (for JSON serialization) ==========
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ========== MATERIAL DESIGN ==========
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ========== MPANDROIDCHART ==========
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ========== LOTTIE ==========
-dontwarn com.airbnb.lottie.**
-keep class com.airbnb.lottie.** { *; }

# ========== VIEW BINDING ==========
-keep class **.databinding.* { *; }
-keepclassmembers class **.databinding.* {
    public static *** inflate(...);
    public static *** bind(...);
}

# ========== PRESERVE LINE NUMBERS FOR CRASH REPORTS ==========
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ========== REMOVE LOGGING IN RELEASE ==========
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ========== OPTIMIZATIONS ==========
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Remove unused resources (handled by R8)
# Shrink enums to ints where possible
-optimizations class/unboxing/enum

# ========== WARNINGS ==========
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn afu.org.checkerframework.**
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.naming.**
-dontwarn javax.net.ssl.**
-dontwarn org.apache.commons.logging.**
-dontwarn javax.security.**
-dontwarn java.awt.**
-dontwarn org.apache.**
# ========== GOOGLE API CLIENT ==========
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.** { *; }
-keep class com.google.api.services.tasks.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.services.calendar.** { *; }
-keep class com.google.api.services.docs.** { *; }

# CRITICAL: Keep GenericData subclasses (used for ALL Google API JSON parsing)
-keep class * extends com.google.api.client.json.GenericJson { *; }
-keepclassmembers class * extends com.google.api.client.json.GenericJson { *; }
-keep class * extends com.google.api.client.util.GenericData { *; }
-keepclassmembers class * extends com.google.api.client.util.GenericData { *; }

# CRITICAL: Keep HTTP Client - used for authentication
-keep class com.google.http.client.** { *; }
-keepclassmembers class com.google.http.client.** { *; }

# Prevent stripping of Generic Types needed for JSON parsing
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

-dontwarn com.google.api.client.**
-dontwarn io.grpc.**
-dontwarn sun.misc.**
-dontwarn java.lang.management.**
-dontwarn org.w3c.dom.**
-dontwarn org.xml.sax.**
-dontwarn javax.xml.**
-dontwarn javax.activation.**
-dontwarn javax.annotation.**
-dontwarn com.google.common.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn org.slf4j.**
-dontwarn java.beans.**
-dontwarn javax.security.**

# ========== iText PDF ==========
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**
-keep class com.neubofy.reality.utils.PdfGenerator { *; }