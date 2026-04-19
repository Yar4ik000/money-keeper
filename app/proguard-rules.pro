# ProGuard/R8 rules for release build. См. §1.12 плана — правила покрывают библиотеки,
# использующие рефлексию (Room, Hilt, kotlinx.serialization, SQLCipher, Argon2, Security).
# Без них release-сборка упадёт на первом DAO-запросе / разрешении Hilt / загрузке native lib.

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Room -------------------------------------------------------
-keep class androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# --- Hilt / Dagger ---------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper
-keepclasseswithmembers class * { @dagger.hilt.android.* <init>(...); }
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# --- kotlinx.serialization (BackupManifest, §2.12) --------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.moneykeeper.**$$serializer { *; }
-keepclassmembers class com.moneykeeper.** {
    *** Companion;
}
-keepclasseswithmembers class com.moneykeeper.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- SQLCipher (net.zetetic) -----------------------------------
-keep class net.zetetic.database.** { *; }

# --- Argon2 JNI binding ----------------------------------------
-keep class de.mkammerer.argon2.** { *; }

# --- AndroidX Security (EncryptedSharedPreferences) ------------
-keep class androidx.security.crypto.** { *; }

# --- Kotlin metadata (для Compose/Coroutines reflection) -------
-keep class kotlin.Metadata { *; }
