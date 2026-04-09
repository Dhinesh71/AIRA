# Room and Retrofit rely on reflection in a few narrow cases during development.
-keepattributes Signature
-keep class androidx.room.** { *; }

