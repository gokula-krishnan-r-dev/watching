package com.meritscreen.feature.child.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

/**
 * Responsive metrics for the child Home launcher (C05).
 *
 * Phone keeps the existing single-column Stitch layout. Tablets get an iPad-style
 * denser icon grid, larger touch targets, and — in landscape — a split pane so the
 * playground fills the wide viewport without stretching a phone column.
 */
@Immutable
data class ChildHomeLayout(
    val widthDp: Float,
    val heightDp: Float,
    val isLandscape: Boolean,
    val isTablet: Boolean,
    /** Two-pane chrome (time/search/quests | app grid) for wide landscape tablets. */
    val useSplitPane: Boolean,
    val contentHorizontalPadding: Dp,
    val sectionSpacing: Dp,
    val contentMaxWidth: Dp?,
    val appGridColumns: Int,
    val appIconSize: Dp,
    val appTileRowHeight: Dp,
    val dockMaxWidth: Dp?,
    val dockHorizontalPadding: Dp,
    val dockBottomPadding: Dp,
    val dockClearance: Dp,
    val dockIconSize: Dp,
    val dockGlyphSize: Dp,
    val profileAvatarSize: Dp,
    val parentLockSize: Dp,
    val titleTextSize: TextUnit,
    val appLabelTextSize: TextUnit,
    val appSubtitleTextSize: TextUnit,
    val badgeTextSize: TextUnit,
)

private const val TABLET_SHORTEST_DP = 600f
private const val EXPANDED_WIDTH_DP = 840f

@Composable
fun rememberChildHomeLayout(
    maxWidthDp: Float,
    maxHeightDp: Float,
): ChildHomeLayout {
    val configuration = LocalConfiguration.current
    val shortest = minOf(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    ).toFloat()
    return remember(maxWidthDp, maxHeightDp, shortest, configuration.orientation) {
        childHomeLayoutFor(
            widthDp = maxWidthDp,
            heightDp = maxHeightDp,
            shortestSideDp = shortest,
        )
    }
}

internal fun childHomeLayoutFor(
    widthDp: Float,
    heightDp: Float,
    shortestSideDp: Float = minOf(widthDp, heightDp),
): ChildHomeLayout {
    val isTablet = shortestSideDp >= TABLET_SHORTEST_DP
    val isLandscape = widthDp > heightDp
    val useSplitPane = isTablet && isLandscape && widthDp >= EXPANDED_WIDTH_DP

    val columns = when {
        widthDp >= 1000f -> 7
        widthDp >= EXPANDED_WIDTH_DP -> 6
        widthDp >= TABLET_SHORTEST_DP -> 5
        else -> 4
    }

    val iconSize = when {
        isTablet && widthDp >= EXPANDED_WIDTH_DP -> 78.dp
        isTablet -> 72.dp
        else -> 62.dp
    }
    // Icon + badge overhang + two text lines + gaps.
    val tileRowHeight = iconSize + 44.dp

    return ChildHomeLayout(
        widthDp = widthDp,
        heightDp = heightDp,
        isLandscape = isLandscape,
        isTablet = isTablet,
        useSplitPane = useSplitPane,
        contentHorizontalPadding = when {
            isTablet && !useSplitPane -> 28.dp
            isTablet -> 20.dp
            else -> 16.dp
        },
        sectionSpacing = if (isTablet) 20.dp else 16.dp,
        contentMaxWidth = when {
            useSplitPane -> null
            isTablet && !isLandscape -> 840.dp
            isTablet -> 960.dp
            else -> null
        },
        appGridColumns = columns,
        appIconSize = iconSize,
        appTileRowHeight = tileRowHeight,
        dockMaxWidth = when {
            isTablet && widthDp >= EXPANDED_WIDTH_DP -> 520.dp
            isTablet -> 440.dp
            else -> null
        },
        dockHorizontalPadding = if (isTablet) 24.dp else 16.dp,
        dockBottomPadding = if (isTablet) 16.dp else 12.dp,
        dockClearance = if (isTablet) 128.dp else 110.dp,
        dockIconSize = if (isTablet) 56.dp else 50.dp,
        dockGlyphSize = if (isTablet) 28.dp else 26.dp,
        profileAvatarSize = if (isTablet) 58.dp else 50.dp,
        parentLockSize = if (isTablet) 48.dp else 42.dp,
        titleTextSize = if (isTablet) 24.sp else 22.sp,
        appLabelTextSize = if (isTablet) 13.sp else 12.sp,
        appSubtitleTextSize = if (isTablet) 11.sp else 10.sp,
        badgeTextSize = if (isTablet) 9.5.sp else 8.5.sp,
    )
}
