package com.meritscreen.core.database.child

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildPolicyDao {
    @Query("SELECT * FROM child_policy WHERE childId = :childId LIMIT 1")
    fun observePolicy(childId: String): Flow<ChildPolicyEntity?>

    @Query("SELECT * FROM child_policy WHERE childId = :childId LIMIT 1")
    suspend fun getPolicy(childId: String): ChildPolicyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPolicy(entity: ChildPolicyEntity)

    @Query("SELECT * FROM child_app_rule WHERE childId = :childId ORDER BY displayName ASC")
    fun observeAppRules(childId: String): Flow<List<ChildAppRuleEntity>>

    @Query("SELECT * FROM child_app_rule WHERE childId = :childId")
    suspend fun listAppRules(childId: String): List<ChildAppRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppRules(rules: List<ChildAppRuleEntity>)

    @Query("DELETE FROM child_app_rule WHERE childId = :childId")
    suspend fun clearAppRules(childId: String)

    @Transaction
    suspend fun replaceAppRules(childId: String, rules: List<ChildAppRuleEntity>) {
        clearAppRules(childId)
        if (rules.isNotEmpty()) upsertAppRules(rules)
    }

    @Query("DELETE FROM child_policy WHERE childId = :childId")
    suspend fun clearPolicy(childId: String)

    @Query("DELETE FROM child_profile_cache WHERE childId = :childId")
    suspend fun clearProfile(childId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(entity: ChildProfileCacheEntity)

    @Query("SELECT * FROM child_profile_cache WHERE childId = :childId LIMIT 1")
    fun observeProfile(childId: String): Flow<ChildProfileCacheEntity?>

    @Query("SELECT * FROM child_profile_cache WHERE childId = :childId LIMIT 1")
    suspend fun getProfile(childId: String): ChildProfileCacheEntity?
}

@Dao
interface SessionStateDao {
    @Query("SELECT * FROM session_state WHERE id = 1 LIMIT 1")
    fun observe(): Flow<SessionStateEntity?>

    @Query("SELECT * FROM session_state WHERE id = 1 LIMIT 1")
    suspend fun get(): SessionStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SessionStateEntity)

    @Query("DELETE FROM session_state")
    suspend fun clear()
}

@Dao
interface QuizDao {
    @Query("SELECT COUNT(*) FROM quiz_item")
    suspend fun countItems(): Int

    @Query("SELECT COUNT(*) FROM quiz_item WHERE ageBand = :ageBand AND language = :language")
    suspend fun countForAgeBand(ageBand: String, language: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM quiz_item
        WHERE ageBand = :ageBand AND language = :language AND source = 'ai'
        """,
    )
    suspend fun countAiForAgeBand(ageBand: String, language: String): Int

    @Query("SELECT id FROM quiz_item WHERE ageBand = :ageBand")
    suspend fun idsForAgeBand(ageBand: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<QuizItemEntity>)

    @Query(
        """
        SELECT * FROM quiz_item
        WHERE ageBand = :ageBand AND language = :language
        ORDER BY difficulty ASC, id ASC
        """,
    )
    suspend fun listForAgeBand(ageBand: String, language: String): List<QuizItemEntity>

    @Query("SELECT * FROM quiz_item WHERE id = :id LIMIT 1")
    suspend fun getItem(id: String): QuizItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkill(entity: SkillStateEntity)

    @Query("SELECT * FROM skill_state WHERE childId = :childId")
    suspend fun listSkills(childId: String): List<SkillStateEntity>

    @Query("SELECT * FROM skill_state WHERE childId = :childId AND topic = :topic LIMIT 1")
    suspend fun getTopicSkill(childId: String, topic: String): SkillStateEntity?

    @Query("SELECT * FROM skill_state WHERE childId = :childId AND dirty = 1")
    suspend fun listDirtySkills(childId: String): List<SkillStateEntity>

    @Query("UPDATE skill_state SET dirty = 0, syncedAtEpochMs = :syncedAtEpochMs WHERE childId = :childId")
    suspend fun markSkillsSynced(childId: String, syncedAtEpochMs: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(entity: RecentQuestionEntity)

    @Query(
        """
        SELECT questionId FROM recent_question
        WHERE childId = :childId AND answeredAtEpochMs >= :afterEpochMs
        ORDER BY answeredAtEpochMs DESC
        """,
    )
    suspend fun recentQuestionIds(childId: String, afterEpochMs: Long): List<String>

    @Query(
        """
        SELECT DISTINCT q.prompt FROM recent_question r
        INNER JOIN quiz_item q ON q.id = r.questionId
        WHERE r.childId = :childId AND r.answeredAtEpochMs >= :afterEpochMs
        """,
    )
    suspend fun recentQuestionPrompts(childId: String, afterEpochMs: Long): List<String>

    @Query("DELETE FROM recent_question WHERE childId = :childId AND answeredAtEpochMs < :beforeEpochMs")
    suspend fun deleteRecentBefore(childId: String, beforeEpochMs: Long)

    @Query("DELETE FROM skill_state WHERE childId = :childId")
    suspend fun clearSkills(childId: String)

    @Query("DELETE FROM recent_question WHERE childId = :childId")
    suspend fun clearRecent(childId: String)
}
