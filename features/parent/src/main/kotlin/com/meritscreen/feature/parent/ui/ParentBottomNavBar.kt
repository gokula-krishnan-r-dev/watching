package com.meritscreen.feature.parent.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meritscreen.core.ui.theme.MeritColors

enum class ParentNavTab {
    HOME,
    CHILDREN,
    REPORTS,
    SETTINGS,
}

/**
 * Modern, sleek, compact bottom navigation bar for the Parent Portal.
 * Replaces the oversized M3 navigation bar with a refined 58dp height,
 * crisp icons, smooth animated pill indicators, and children count badges.
 */
@Composable
fun ParentBottomNavBar(
    selectedTab: ParentNavTab,
    onOpenHome: () -> Unit,
    onOpenChildren: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    childrenCount: Int = 0,
) {
    val dividerColor = MeritColors.OutlineVariant.copy(alpha = 0.25f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            },
        color = MeritColors.SurfaceContainerLowest,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(58.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ParentBottomNavItem(
                label = "Home",
                selected = selectedTab == ParentNavTab.HOME,
                selectedIcon = Icons.Rounded.Home,
                unselectedIcon = Icons.Outlined.Home,
                onClick = onOpenHome,
                modifier = Modifier.weight(1f),
            )
            ParentBottomNavItem(
                label = "Children",
                selected = selectedTab == ParentNavTab.CHILDREN,
                selectedIcon = Icons.Rounded.Face,
                unselectedIcon = Icons.Outlined.Face,
                badgeCount = childrenCount,
                onClick = onOpenChildren,
                modifier = Modifier.weight(1f),
            )
            ParentBottomNavItem(
                label = "Reports",
                selected = selectedTab == ParentNavTab.REPORTS,
                selectedIcon = Icons.Rounded.Leaderboard,
                unselectedIcon = Icons.Outlined.Leaderboard,
                onClick = onOpenReports,
                modifier = Modifier.weight(1f),
            )
            ParentBottomNavItem(
                label = "Settings",
                selected = selectedTab == ParentNavTab.SETTINGS,
                selectedIcon = Icons.Rounded.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ParentBottomNavItem(
    label: String,
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val iconColor by animateColorAsState(
        targetValue = if (selected) MeritColors.Primary else MeritColors.OnSurfaceVariant.copy(alpha = 0.70f),
        animationSpec = tween(200),
        label = "nav_icon_color",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MeritColors.Primary else MeritColors.OnSurfaceVariant.copy(alpha = 0.70f),
        animationSpec = tween(200),
        label = "nav_text_color",
    )
    val pillBackground by animateColorAsState(
        targetValue = if (selected) MeritColors.Primary.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(200),
        label = "nav_pill_bg",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(pillBackground)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )

            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-3).dp)
                        .clip(CircleShape)
                        .background(MeritColors.Primary)
                        .size(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 8.5.sp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                lineHeight = 13.sp,
            ),
            color = textColor,
        )
    }
}
