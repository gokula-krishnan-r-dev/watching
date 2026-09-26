package com.meritscreen.core.firebase.curriculum

import com.meritscreen.core.common.domain.NurseryCurriculum
import kotlinx.coroutines.flow.Flow

interface NurseryCurriculumRemoteClient {
    fun observeCurriculum(): Flow<NurseryCurriculum?>
    suspend fun fetchCurriculum(): NurseryCurriculum?
    suspend fun saveCurriculum(curriculum: NurseryCurriculum)
}
