-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class dev.dewy.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
