# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.widapp.**$$serializer { *; }
-keepclassmembers class app.widapp.** {
    *** Companion;
}
# Glance action callbacks are instantiated reflectively
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
