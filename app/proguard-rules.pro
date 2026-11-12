# Keep kotlinx-serialization models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.navidrome.tv.**$$serializer { *; }
-keepclassmembers class com.navidrome.tv.** { *** Companion; }
-keepclasseswithmembers class com.navidrome.tv.** { kotlinx.serialization.KSerializer serializer(...); }
