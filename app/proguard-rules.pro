# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.decentespresso.dcamp.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.decentespresso.dcamp.**$$serializer { *; }
-keep class com.decentespresso.dcamp.api.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
