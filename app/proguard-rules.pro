# Readable stack traces if something goes wrong.
-dontobfuscate

# kotlinx.serialization (the library ships rules too; these are a safety net).
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.ivlens.app.**$$serializer { *; }
-keepclassmembers class com.ivlens.app.** { *** Companion; }
-keepclasseswithmembers class com.ivlens.app.** { kotlinx.serialization.KSerializer serializer(...); }
