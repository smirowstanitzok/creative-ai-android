# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.creativeai.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.creativeai.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
