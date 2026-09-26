package com.meritscreen.feature.child.data

import com.meritscreen.core.common.dispatchers.AppDispatchers
import com.meritscreen.core.common.domain.NurseryStaticCatalog
import com.meritscreen.core.common.logging.AppLogger
import com.meritscreen.core.firebase.curriculum.NurseryCurriculumRemoteClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NurseryCurriculumRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val appDispatchers = AppDispatchers(
        io = testDispatcher,
        default = testDispatcher,
        main = testDispatcher,
    )
    private val remoteClient: NurseryCurriculumRemoteClient = mockk(relaxed = true)
    private val logger: AppLogger = mockk(relaxed = true)

    private lateinit var repository: NurseryCurriculumRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { remoteClient.fetchCurriculum() } returns NurseryStaticCatalog.DEFAULT_CURRICULUM
        coEvery { remoteClient.observeCurriculum() } returns flowOf(NurseryStaticCatalog.DEFAULT_CURRICULUM)

        repository = NurseryCurriculumRepository(
            remoteClient = remoteClient,
            dispatchers = appDispatchers,
            logger = logger,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pickDiverseVideo provides variety across multiple topics`() = runTest(testDispatcher) {
        val pickedVideos = mutableListOf<String>()
        repeat(5) {
            val video = repository.pickDiverseVideo(preferredPlaylistId = "all")
            assertNotNull(video)
            pickedVideos.add(video!!.videoId)
        }

        // Must not have selected the exact same video 5 times in a row
        val distinctVideos = pickedVideos.distinct()
        assertTrue("Expected multiple distinct videos, got: $distinctVideos", distinctVideos.size >= 4)
    }

    @Test
    fun `pickDiverseVideo excludes error video on retry`() = runTest(testDispatcher) {
        val failedId = "ezmsrB59mj8"
        val next = repository.pickDiverseVideo(
            preferredPlaylistId = "all",
            currentVideoId = failedId,
            excludeVideoIds = setOf(failedId),
        )
        assertNotNull(next)
        assertNotEquals(failedId, next!!.videoId)
    }

    @Test
    fun `pickDiverseTeachItems selects items from available tracks`() = runTest(testDispatcher) {
        val items = repository.pickDiverseTeachItems(count = 3)
        assertEquals(3, items.size)
    }
}
