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
