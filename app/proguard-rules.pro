# Keep crash traces readable.
#
# The app records uncaught exceptions to a file that the user can read and
# share, which is the only way to diagnose a fault that appears against one
# operator's live data. R8 renames classes and strips line numbers by default,
# which would reduce those traces to "a.b.c(SourceFile:1)" and make the whole
# diagnostics feature pointless in the only build that ships.
-keepattributes SourceFile,LineNumberTable
-keepnames class org.pashri.bustimes.** { *; }

# kotlinx.serialization keeps its generated serializers via annotations
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class org.pashri.bustimes.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class org.pashri.bustimes.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
