# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ===============================
# FIREBASE
# ===============================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Realtime Database serialization
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class com.amvarpvtltd.swiftNote.** {
    public <init>();
}

# Firebase Crashlytics
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# ===============================
# GSON
# ===============================
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ===============================
# ROOM DATABASE
# ===============================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface *
-keep class * extends androidx.room.RoomDatabase$Callback
-dontwarn androidx.room.paging.**

# Room generated implementations
-keep class com.amvarpvtltd.swiftNote.room.** { *; }
-keepclassmembers class com.amvarpvtltd.swiftNote.room.** { *; }

# ===============================
# APP DATA CLASSES & ENTITIES
# ===============================
# Note class (formerly named "dataclass") — keep both names for Firebase serialization compatibility
-keep class com.amvarpvtltd.swiftNote.Note { *; }
-keepclassmembers class com.amvarpvtltd.swiftNote.Note { *; }
-keep class com.amvarpvtltd.swiftNote.dataclass { *; }
-keepclassmembers class com.amvarpvtltd.swiftNote.dataclass { *; }
-keep class com.amvarpvtltd.swiftNote.room.NoteEntity { *; }
-keep class com.amvarpvtltd.swiftNote.room.PendingDeletionEntity { *; }
-keep class com.amvarpvtltd.swiftNote.reminders.ReminderEntity { *; }
-keep class com.amvarpvtltd.swiftNote.sync.SyncResult { *; }
-keep class com.amvarpvtltd.swiftNote.sync.SyncStats { *; }
-keep class com.amvarpvtltd.swiftNote.ai.DetectedReminder { *; }
# Phase 5: AI parser models accessed via reflection by Gemini/Groq JSON deserialization
-keep class com.amvarpvtltd.swiftNote.ai.DetectedRecurrence { *; }
-keep class com.amvarpvtltd.swiftNote.ai.ParsedReminderIntent { *; }
-keep class com.amvarpvtltd.swiftNote.ai.GeminiDetectedReminder { *; }

# ===============================
# RICH TEXT EDITOR (compose-rich-editor) & JSOUP
# ===============================
-keep class com.mohamedrejeb.richeditor.** { *; }
-dontwarn com.mohamedrejeb.richeditor.**
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ===============================
# GLANCE WIDGET (reflective instantiation by Glance/AppWidget framework)
# ===============================
-keep class com.amvarpvtltd.swiftNote.widget.** { *; }

# ===============================
# ML KIT
# ===============================
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.internal.mlkit_entity_extraction.** { *; }
-dontwarn com.google.android.gms.internal.mlkit_entity_extraction.**

# ML Kit Entity Extraction model classes
-keepclassmembers class * implements com.google.mlkit.common.model.DownloadConditions { *; }

# ===============================
# WORKMANAGER
# ===============================
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ===============================
# CAMERAX & BARCODE SCANNING
# ===============================
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
-keep class com.google.mlkit.vision.barcode.** { *; }
-dontwarn com.google.mlkit.vision.barcode.**

# ===============================
# ZXING (QR Code generation)
# ===============================
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ===============================
# BROADCAST RECEIVERS
# ===============================
-keep class com.amvarpvtltd.swiftNote.reminders.ReminderReceiver { *; }
-keep class com.amvarpvtltd.swiftNote.reminders.BootReceiver { *; }
-keep class com.amvarpvtltd.swiftNote.reminders.ExactAlarmPermissionReceiver { *; }
-keep class com.amvarpvtltd.swiftNote.notifications.NotificationActionReceiver { *; }

# ===============================
# KOTLIN & COROUTINES
# ===============================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ===============================
# NATIVE METHODS & JNI
# ===============================
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Preserve native method names for 16KB compatibility
-keep class java.lang.System {
    public static void loadLibrary(java.lang.String);
    public static void load(java.lang.String);
}

# ===============================
# ANNOTATIONS & REFLECTION
# ===============================
-keepattributes *Annotation*,Signature,Exception

# Keep classes that might be accessed via reflection
-keepclassmembers class * {
    @androidx.annotation.Keep <methods>;
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <init>(...);
}

# ===============================
# JETPACK COMPOSE
# ===============================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ===============================
# WORKMANAGER
# ===============================
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**
# Keep our SyncWorker — WorkManager instantiates it by class name via reflection
-keep class com.amvarpvtltd.swiftNote.utils.SyncWorker { *; }

# ===============================
# 16KB PAGE SIZE COMPATIBILITY RULES
# ===============================
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Strip verbose and debug logs in release builds (security: removes any residual sensitive data)
# Keep warn/error so Crashlytics can capture meaningful crash context
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Optimize string resources for memory efficiency
-adaptresourcefilenames **.properties,**.gif,**.jpg,**.png
-adaptresourcefilecontents **.properties,META-INF/MANIFEST.MF

# Ensure proper initialization order for 16KB compatibility
-keepclassmembers class * extends android.app.Application {
    public void onCreate();
}

# Optimize for better memory alignment
-allowaccessmodification
-repackageclasses ''

# Reduce method count and optimize for 16KB devices
-overloadaggressively
