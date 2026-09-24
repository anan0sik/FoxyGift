# ProGuard rules for FoxyGift POS

# Keep Room entities
-keep class com.foxygift.pos.data.db.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class **_HiltModules** { *; }
-keep class **_Factory** { *; }

# Keep NFC-related classes
-keep class android.nfc.** { *; }

# Keep security classes
-keep class androidx.security.crypto.** { *; }

# Keep Gson (if used for JSON serialization)
-keepattributes Signature
-keepattributes *Annotation*

# Keep application class
-keep class com.foxygift.pos.FoxyGiftApp { *; }

# Remove debug logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
