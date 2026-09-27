package com.meritscreen.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.meritscreen.core.database.child.ChildStickerDao
import com.meritscreen.core.database.child.ChildStickerUnlockEntity
import com.meritscreen.core.database.child.ChildExplorerProgressEntity
import com.meritscreen.core.database.child.ChildAppRuleEntity
import com.meritscreen.core.database.child.ChildPolicyDao
import com.meritscreen.core.database.child.ChildPolicyEntity
import com.meritscreen.core.database.child.ChildProfileCacheEntity
import com.meritscreen.core.database.child.PolicySyncStateDao
import com.meritscreen.core.database.child.PolicySyncStateEntity
import com.meritscreen.core.database.child.QuizAttemptDao
import com.meritscreen.core.database.child.QuizAttemptEntity
import com.meritscreen.core.database.child.QuizDao
import com.meritscreen.core.database.child.QuizItemEntity
import com.meritscreen.core.database.child.RecentQuestionEntity
import com.meritscreen.core.database.child.SessionStateDao
import com.meritscreen.core.database.child.SessionStateEntity
import com.meritscreen.core.database.child.SkillStateEntity
import com.meritscreen.core.database.child.UsageDao
import com.meritscreen.core.database.child.UsageDayEntity
import com.meritscreen.core.database.device.DeviceRuntimeStateDao
import com.meritscreen.core.database.device.DeviceRuntimeStateEntity
import com.meritscreen.core.database.device.InstalledAppDao
import com.meritscreen.core.database.device.InstalledAppEntity

@Database(
    entities = [
        SyncMetaEntity::class,
        ChildPolicyEntity::class,
        ChildAppRuleEntity::class,
        ChildProfileCacheEntity::class,
        SessionStateEntity::class,
        QuizItemEntity::class,
        SkillStateEntity::class,
        RecentQuestionEntity::class,
        InstalledAppEntity::class,
        DeviceRuntimeStateEntity::class,
        UsageDayEntity::class,
        QuizAttemptEntity::class,
        PolicySyncStateEntity::class,
        ChildStickerUnlockEntity::class,
        ChildExplorerProgressEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class MeritScreenDatabase : RoomDatabase() {
    abstract fun syncMetaDao(): SyncMetaDao
    abstract fun childPolicyDao(): ChildPolicyDao
    abstract fun sessionStateDao(): SessionStateDao
    abstract fun quizDao(): QuizDao
    abstract fun installedAppDao(): InstalledAppDao
    abstract fun deviceRuntimeStateDao(): DeviceRuntimeStateDao
    abstract fun usageDao(): UsageDao
    abstract fun quizAttemptDao(): QuizAttemptDao
    abstract fun policySyncStateDao(): PolicySyncStateDao
    abstract fun childStickerDao(): ChildStickerDao
}
