# kotlinx.serialization keeps its generated serializers via annotations
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class org.pashri.bustimes.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class org.pashri.bustimes.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
