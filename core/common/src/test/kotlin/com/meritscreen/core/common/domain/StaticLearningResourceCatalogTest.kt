package com.meritscreen.core.common.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticLearningResourceCatalogTest {

    @Test
    fun `division sharing catalog entry has valid blog and videos`() {
        val resource = StaticLearningResourceCatalog.getFor("division-sharing", "math", "Fair Sharing Division")
        assertEquals("division-sharing", resource.conceptId)
        assertEquals("math", resource.topic)
        assertTrue(resource.summaryBlog.coreRule.isNotBlank())
        assertTrue(resource.summaryBlog.sections.isNotEmpty())
        assertTrue(resource.videos.isNotEmpty())
        for (video in resource.videos) {
            assertEquals(11, video.videoId.length)
            assertTrue(video.title.isNotBlank())
            assertTrue(video.channelName.isNotBlank())
        }
    }

    @Test
    fun `money change catalog entry has valid coin guide`() {
        val resource = StaticLearningResourceCatalog.getFor("money-change", "math")
        assertEquals("money-change", resource.conceptId)
        assertTrue(resource.summaryBlog.title.contains("Shopper"))
        assertTrue(resource.videos.any { it.channelName == "Khan Academy Kids" })
    }

    @Test
    fun `science plants photosynthesis has valid real videos`() {
        val resource = StaticLearningResourceCatalog.getFor("plants-photosynthesis", "science")
        assertEquals("science", resource.topic)
        assertTrue(resource.summaryBlog.coreRule.contains("Plants"))
        assertTrue(resource.videos.any { it.channelName == "SciShow Kids" })
    }

    @Test
    fun `solar system has 8 planets explanation`() {
        val resource = StaticLearningResourceCatalog.getFor("solar-system", "science")
        assertTrue(resource.summaryBlog.sections.any { it.content.contains("Sun") || it.heading.contains("Planets") })
        assertTrue(resource.videos.isNotEmpty())
    }

    @Test
    fun `unknown concept falls back intelligently to universal resource`() {
        val resource = StaticLearningResourceCatalog.getFor("geometry-3d-shapes", "geometry", "3D Shapes")
        assertEquals("geometry-3d-shapes", resource.conceptId)
        assertNotNull(resource.summaryBlog)
        assertTrue(resource.summaryBlog.coreRule.isNotBlank())
        assertTrue(resource.videos.isNotEmpty())
        assertFalse(resource.isAiGenerated)
    }
}
