# Keep as baseline for now. Add production rules as needed.
-keep class kotlinx.serialization.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**