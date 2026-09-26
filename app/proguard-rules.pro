-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-dontwarn org.bouncycastle.**

# Firebase App Check Play Integrity (store / pure release builds)
-keep class com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory {
    public static ** getInstance();
}
-keep class com.google.firebase.appcheck.playintegrity.** { *; }

# Firebase App Check debug provider (internal sideload tester builds)
-keep class com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory {
    public static ** getInstance();
}
-keep class com.google.firebase.appcheck.debug.** { *; }

# Firebase AI Logic / serialization used by background quiz pack generation
-keepclassmembers class * {
    @com.google.firebase.ai.** <methods>;
}
-dontwarn com.google.firebase.ai.**

