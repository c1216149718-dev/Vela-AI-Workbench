# DeepSeek Widget ProGuard Rules

# Keep kotlinx.serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes used for API responses
-keep class com.deepseek.widget.api.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
