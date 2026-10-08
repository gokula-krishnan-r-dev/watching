package com.meritscreen.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.meritscreen.core.database.child.ChildStickerDao
import com.meritscreen.core.database.child.ChildPolicyDao
import com.meritscreen.core.database.child.PolicySyncStateDao
import com.meritscreen.core.database.child.QuizAttemptDao
import com.meritscreen.core.database.child.QuizDao
import com.meritscreen.core.database.child.SessionStateDao
import com.meritscreen.core.database.child.UsageDao
import com.meritscreen.core.database.device.DeviceRuntimeStateDao
import com.meritscreen.core.database.device.InstalledAppDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE session_state ADD COLUMN quizGraceUntilElapsedMs INTEGER")
        }
    }
    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recent_question_childId_answeredAtEpochMs` " +
                    "ON `recent_question` (`childId`, `answeredAtEpochMs`)",
            )
        }
    }

    /**
     * Session state becomes per-child so a shared tablet can switch profiles without
     * leaking timers / fail-lock across kids. Legacy single-row (`id = 1`) is dropped;
     * the active child re-hydrates Idle on first open after upgrade.
     */
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `session_state`")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `session_state` (
                    `childId` TEXT NOT NULL,
                    `phase` TEXT NOT NULL,
                    `activePackage` TEXT,
                    `activeAppId` TEXT,
                    `blockStartedElapsedMs` INTEGER,
                    `blockDurationMinutes` INTEGER NOT NULL,
                    `minutesAccruedInBlock` REAL NOT NULL,
                    `deviceShieldedUntilElapsedMs` INTEGER,
                    `cooldownMinutes` INTEGER NOT NULL,
                    `dayKey` TEXT NOT NULL,
                    `minutesUsedToday` INTEGER NOT NULL,
                    `lastTickElapsedMs` INTEGER,
                    `quizLockEndsAtElapsedMs` INTEGER,
                    `quizLockQuestionId` TEXT,
                    `quizGraceUntilElapsedMs` INTEGER,
                    PRIMARY KEY(`childId`)
                )
                """.trimIndent(),
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeritScreenDatabase {
        return Room.databaseBuilder(
            context,
            MeritScreenDatabase::class.java,
            "meritscreen.db",
        )
            .addMigrations(MIGRATION_9_10)
            .addMigrations(MIGRATION_10_11)
            .addMigrations(MIGRATION_11_12)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideSyncMetaDao(database: MeritScreenDatabase): SyncMetaDao = database.syncMetaDao()

    @Provides
    fun provideChildPolicyDao(database: MeritScreenDatabase): ChildPolicyDao = database.childPolicyDao()

    @Provides
    fun provideSessionStateDao(database: MeritScreenDatabase): SessionStateDao = database.sessionStateDao()

    @Provides
    fun provideQuizDao(database: MeritScreenDatabase): QuizDao = database.quizDao()

    @Provides
    fun provideInstalledAppDao(database: MeritScreenDatabase): InstalledAppDao =
        database.installedAppDao()

    @Provides
    fun provideDeviceRuntimeStateDao(database: MeritScreenDatabase): DeviceRuntimeStateDao =
        database.deviceRuntimeStateDao()

    @Provides
    fun provideUsageDao(database: MeritScreenDatabase): UsageDao = database.usageDao()

    @Provides
    fun provideQuizAttemptDao(database: MeritScreenDatabase): QuizAttemptDao = database.quizAttemptDao()

    @Provides
    fun providePolicySyncStateDao(database: MeritScreenDatabase): PolicySyncStateDao =
        database.policySyncStateDao()

    @Provides
    fun provideChildStickerDao(database: MeritScreenDatabase): ChildStickerDao =
        database.childStickerDao()
}
