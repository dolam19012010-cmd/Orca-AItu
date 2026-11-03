# ProGuard rules for OrcaAuto

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Google Generative AI & Networking
-keep class com.google.ai.client.generativeai.** { *; }
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Data models
-keep class com.orca.orcaauto.data.local.** { *; }
-keep class com.orca.orcaauto.data.remote.** { *; }
