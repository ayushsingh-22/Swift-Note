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

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep Gson classes
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep your data classes for serialization
-keep class dataclass { *; }

# Preserve native method names for 16KB compatibility
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep annotation classes
-keepattributes *Annotation*,Signature,Exception

# Preserve line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable

# ===============================
# 16KB PAGE SIZE COMPATIBILITY RULES
# ===============================

# Optimize for 16KB page size devices - reduce memory fragmentation
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Remove debug symbols that cause alignment issues
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Optimize native library loading for 16KB alignment
-keep class java.lang.System {
    public static void loadLibrary(java.lang.String);
    public static void load(java.lang.String);
}

# Keep classes that might be accessed via reflection to prevent runtime issues
-keepclassmembers class * {
    @androidx.annotation.Keep <methods>;
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <init>(...);
}

# Optimize memory usage for Compose (important for 16KB devices)
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Preserve essential JNI functions for proper alignment
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Optimize string resources for memory efficiency
-adaptresourcefilenames **.properties,**.gif,**.jpg,**.png
-adaptresourcefilecontents **.properties,META-INF/MANIFEST.MF

# Ensure proper initialization order for 16KB compatibility
-keepclassmembers class * extends android.app.Application {
    public void onCreate();
}

# Keep Crashlytics classes but optimize for 16KB
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# Optimize for better memory alignment
-allowaccessmodification
-repackageclasses ''

# Reduce method count and optimize for 16KB devices
-overloadaggressively
