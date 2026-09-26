package com.meritscreen.feature.child.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildHomeLayoutTest {

    @Test
    fun phonePortrait_usesFourColumns() {
        val layout = childHomeLayoutFor(widthDp = 360f, heightDp = 800f, shortestSideDp = 360f)
        assertFalse(layout.isTablet)
        assertFalse(layout.useSplitPane)
        assertEquals(4, layout.appGridColumns)
        assertEquals(62f, layout.appIconSize.value)
    }

    @Test
    fun tabletPortrait_widensGridAndIcons() {
        val layout = childHomeLayoutFor(widthDp = 800f, heightDp = 1200f, shortestSideDp = 800f)
        assertTrue(layout.isTablet)
        assertFalse(layout.useSplitPane)
        assertEquals(5, layout.appGridColumns)
        assertTrue(layout.appIconSize.value >= 72f)
        assertEquals(840f, layout.contentMaxWidth?.value)
    }

    @Test
    fun tabletLandscape_usesSplitPaneAndDockCap() {
        val layout = childHomeLayoutFor(widthDp = 1180f, heightDp = 820f, shortestSideDp = 820f)
        assertTrue(layout.isTablet)
        assertTrue(layout.useSplitPane)
        assertEquals(7, layout.appGridColumns)
        assertTrue(layout.dockMaxWidth != null)
    }
}
