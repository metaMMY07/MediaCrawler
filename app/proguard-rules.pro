# R8 / ProGuard rules for dev.mediasearch (release build).
#
# Keep annotations and generic signatures: Compose, kotlinx-coroutines and Cronet all
# rely on them for reflection / service discovery.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# ---------------------------------------------------------------- Kotlin / coroutines
-dontwarn kotlin.**
-dontwarn kotlinx.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class **$WhenMappings {
    <fields>;
}
# ServiceLoader-based MainDispatcherFactory lookup.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# ---------------------------------------------------------------- Chromium Cronet
# Cronet resolves its engine + providers through reflection and META-INF/services.
-keep class org.chromium.net.** { *; }
-keepclassmembers class org.chromium.net.** { *; }
-keep class org.chromium.base.** { *; }
-dontwarn org.chromium.**
-keepnames class * implements org.chromium.net.CronetProvider
-keep class * implements org.chromium.net.CronetProvider { *; }
-keepnames @org.chromium.net.CronetProvider$CronetProviderType class *
# Do not strip the ServiceLoader descriptors Cronet ships.
-keepdirectories META-INF/services

# ---------------------------------------------------------------- Protobuf (Cronet transitive)
-dontwarn com.google.protobuf.**
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }

# ---------------------------------------------------------------- Misc transitive deps
-dontwarn javax.annotation.**
-dontwarn javax.lang.model.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn java.lang.invoke.**

# ---------------------------------------------------------------- JSON (unit tests / parsing)
-keep class org.json.** { *; }
-dontwarn org.json.**

# ---------------------------------------------------------------- AndroidX WebView
-keep class androidx.webkit.** { *; }
-dontwarn androidx.webkit.**

# Native methods must keep their names (JNI binds by symbol).
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enum valueOf/values used by reflective serialization paths.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
