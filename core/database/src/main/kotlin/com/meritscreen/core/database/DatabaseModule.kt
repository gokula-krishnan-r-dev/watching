package com.meritscreen.core.database

import android.content.Context
import androidx.room.Room
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
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeritScreenDatabase {
        return Room.databaseBuilder(
            context,
            MeritScreenDatabase::class.java,
            "meritscreen.db",
        )
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
