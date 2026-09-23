# R8 rules for Hangry. Libraries (Room, WorkManager, kotlinx.serialization, OkHttp, Coil,
# Health Connect) ship their own keep rules; these cover what's specific to this app.

# Enum names are stored as text (recovery states, workout types, widget types, sexes...) and
# read back with valueOf() - keep every constant and its name intact.
-keepclassmembers enum com.kevan.hangry.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep class names readable in crash reports' stack traces (line numbers too).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
