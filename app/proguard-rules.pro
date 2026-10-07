# Default AGP/R8 rules already cover Compose, coroutines and Navigation.
# Add only what your own code needs.

# Keep line numbers for readable production stack traces, but hide the original
# file name so the mapping stays useful.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Kotlin coroutines: strip debug-only internals ----------------------------
-dontwarn kotlinx.coroutines.debug.**

# --- Debug logging ------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}