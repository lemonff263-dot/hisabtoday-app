# Proguard and R8 optimization rules for Hishab App

# Keep JavascriptInterface annotations and methods so JavaScript bridge is not stripped
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep MainActivity AppBridge methods
-keep class com.example.MainActivity$AppBridge { *; }
-keepclassmembers class com.example.MainActivity$AppBridge {
    public *;
}

# Keep WebKit and FileProvider
-keep class androidx.webkit.** { *; }
-keep class androidx.core.content.FileProvider { *; }
