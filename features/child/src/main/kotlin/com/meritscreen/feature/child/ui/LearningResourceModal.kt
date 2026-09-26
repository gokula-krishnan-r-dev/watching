package com.meritscreen.feature.child.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meritscreen.core.common.domain.LearningResource
import com.meritscreen.core.common.domain.LearningResourceVideo
import com.meritscreen.core.ui.theme.MeritColors

/**
 * Full-screen learning resource hub: concept guide + curated video lessons.
 */
@Composable
fun LearningResourceModal(
    resource: LearningResource,
    tts: QuizTtsNarrator,
    onDismiss: () -> Unit,
    onPlayVideo: (LearningResourceVideo) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MeritColors.Surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                ResourceModalHeader(
                    resource = resource,
                    onDismiss = onDismiss,
                    onSpeak = {
                        val blog = resource.summaryBlog
                        val speech = "${blog.title}. Core rule: ${blog.coreRule}. " +
                            blog.sections.joinToString(" ") { "${it.heading}: ${it.content}" }
                        tts.speak(speech)
                    },
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .background(MeritColors.SurfaceContainerHigh, RoundedCornerShape(12.dp))
                        .padding(3.dp),
                ) {
                    TabPill(
                        selected = selectedTab == 0,
                        icon = Icons.Default.MenuBook,
                        label = "Guide",
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 0 },
                    )
                    TabPill(
                        selected = selectedTab == 1,
                        icon = Icons.Default.SmartDisplay,
                        label = "Videos (${resource.videos.size})",
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTab = 1 },
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (selectedTab == 0) {
                        BlogContentSection(resource = resource)
                    } else {
                        VideosContentSection(
                            videos = resource.videos,
                            onPlayVideo = onPlayVideo,
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MeritColors.SurfaceContainerLowest,
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (selectedTab == 0 && resource.videos.isNotEmpty()) {
                                "Videos are on the Videos tab"
                            } else {
                                "Ready to try the quiz again?"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeritColors.Primary),
                        ) {
                            Text(
                                text = "Back to quiz",
                                fontWeight = FontWeight.Bold,
                                color = MeritColors.OnPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourceModalHeader(
    resource: LearningResource,
    onDismiss: () -> Unit,
    onSpeak: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MeritColors.SurfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MeritColors.PrimaryFixed.copy(alpha = 0.55f),
                ) {
                    Text(
                        text = resource.topic.uppercase(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.OnPrimaryFixedVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MeritColors.SurfaceContainerHighest,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (resource.isAiGenerated) {
                                Icons.Default.AutoAwesome
                            } else {
                                Icons.Default.School
                            },
                            contentDescription = null,
                            tint = MeritColors.OnSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = if (resource.isAiGenerated) "AI guide" else "Guide",
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onSpeak, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Read aloud",
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MeritColors.OnSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = resource.conceptTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MeritColors.OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TabPill(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = if (selected) MeritColors.Primary else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MeritColors.OnPrimary else MeritColors.OnSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MeritColors.OnPrimary else MeritColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BlogContentSection(resource: LearningResource) {
    val blog = resource.summaryBlog

    ResourceCard(
        icon = Icons.Default.MenuBook,
        iconTint = MeritColors.Primary,
        iconBg = MeritColors.PrimaryFixed.copy(alpha = 0.45f),
        title = blog.title,
        subtitle = "${blog.readingTimeMinutes} min read",
        subtitleIcon = Icons.Default.Timer,
    )

    ResourceCard(
        icon = Icons.Default.Psychology,
        iconTint = MeritColors.OnSecondaryContainer,
        iconBg = MeritColors.SecondaryContainer.copy(alpha = 0.7f),
        title = "Core rule",
        body = blog.coreRule,
    )

    blog.sections.forEach { section ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MeritColors.SurfaceContainerLowest,
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = section.heading,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MeritColors.Primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = section.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeritColors.OnSurface,
                    lineHeight = 20.sp,
                )
                if (section.bulletPoints.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    section.bulletPoints.forEach { point ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.dp)
                                    .size(5.dp)
                                    .background(MeritColors.Primary, CircleShape),
                            )
                            Text(
                                text = point,
                                style = MaterialTheme.typography.bodySmall,
                                color = MeritColors.OnSurfaceVariant,
                                lineHeight = 18.sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }

    ResourceCard(
        icon = Icons.Default.Lightbulb,
        iconTint = MeritColors.Tertiary,
        iconBg = MeritColors.TertiaryFixed.copy(alpha = 0.4f),
        title = "Memory tip",
        body = blog.quickMemoryTip,
    )

    blog.funFact?.let { fact ->
        ResourceCard(
            icon = Icons.Default.Spa,
            iconTint = MeritColors.Secondary,
            iconBg = MeritColors.SurfaceContainerHigh,
            title = "Did you know?",
            body = fact,
        )
    }
}

@Composable
private fun ResourceCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    body: String? = null,
    subtitle: String? = null,
    subtitleIcon: ImageVector? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MeritColors.SurfaceContainerLowest,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MeritColors.OnSurface,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (subtitleIcon != null) {
                            Icon(
                                imageVector = subtitleIcon,
                                contentDescription = null,
                                tint = MeritColors.OnSurfaceVariant,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }
                if (body != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeritColors.OnSurface,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideosContentSection(
    videos: List<LearningResourceVideo>,
    onPlayVideo: (LearningResourceVideo) -> Unit,
) {
    if (videos.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MeritColors.SurfaceContainerLowest,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.SmartDisplay,
                    contentDescription = null,
                    tint = MeritColors.OnSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No videos for this topic yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MeritColors.OnSurface,
                )
                Text(
                    text = "Use the Guide tab for a quick lesson.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }
        }
        return
    }

    Text(
        text = "Tap a video to play fullscreen",
        style = MaterialTheme.typography.bodySmall,
        color = MeritColors.OnSurfaceVariant,
    )

    videos.forEach { video ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onPlayVideo(video) },
            shape = RoundedCornerShape(14.dp),
            color = MeritColors.SurfaceContainerLowest,
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MeritColors.Primary, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MeritColors.OnPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MeritColors.OnSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (video.durationLabel.isNotBlank()) {
                        Text(
                            text = video.durationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
