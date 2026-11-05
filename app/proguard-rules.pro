# Lens035 R8 rules. Libraries (Media3, CameraX, ML Kit, kotlinx.serialization) ship their
# own consumer rules; only app-specific keeps live here.

# kotlinx.serialization: keep generated serializers of app/core model classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.lens035.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.lens035.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
