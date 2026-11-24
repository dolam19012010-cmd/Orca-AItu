# ProGuard rules for OrcaAuto

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Gson & Networking
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Data models
-keep class com.orca.orcaauto.data.local.** { *; }
-keep class com.orca.orcaauto.data.remote.** { *; }
-keep class com.orca.orcaauto.data.preferences.** { *; }
-keep class com.orca.orcaauto.data.backup.** { *; }
-keep class com.orca.orcaauto.domain.** { *; }

# Preserve source file and line numbers for CrashActivity stack trace
-keepattributes SourceFile,LineNumberTable
