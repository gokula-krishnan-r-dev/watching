package com.meritscreen.feature.onboarding.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object ValueTourRoute

data class TourSlideData(
    val stepLabel: String,
    val title: String,
    val subtitle: String,
    /** Extra product line — kept short so the page stays non-scrollable. */
    val highlight: String,
    val mockType: TourMockType,
    val pills: List<Pair<ImageVector, String>>,
)

enum class TourMockType {
    QUIZ_PREVIEW,
    ALLOWLIST_PREVIEW,
    BEDTIME_PREVIEW,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ValueTourScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit = onContinue,
    onBack: () -> Unit = onSkip,
    modifier: Modifier = Modifier,
) {
    val slides = listOf(
        TourSlideData(
            stepLabel = "Step 1 of 3",
            title = "Learn first. Then play.",
            subtitle = "Kids answer short AI-powered questions on the phone. Pass the quiz, unlock screen time. Fail, and entertainment stays locked until they try again.",
            highlight = "Watching turns the mobile into a daily trainer — adaptive quizzes teach math, reading, and logic while screen time becomes the reward.",
            mockType = TourMockType.QUIZ_PREVIEW,
            pills = listOf(
                Icons.Default.Psychology to "AI training quizzes",
                Icons.Default.Smartphone to "Phone as the classroom",
                Icons.Default.Bolt to "Minutes for correct answers",
            ),
        ),
        TourSlideData(
            stepLabel = "Step 2 of 3",
            title = "Only apps you approve",
            subtitle = "Watching becomes the child’s home screen. Games and video stay behind the quiz gate. Emergency apps always stay open.",
            highlight = "No app-store loopholes or hidden browsers — parents set the allowlist once, and the device enforces it offline.",
            mockType = TourMockType.ALLOWLIST_PREVIEW,
            pills = listOf(
                Icons.Default.Shield to "Launcher lock",
                Icons.Default.LockClock to "Remote pause",
                Icons.Default.VerifiedUser to "Parent PIN",
            ),
        ),
        TourSlideData(
            stepLabel = "Step 3 of 3",
            title = "Healthy habits, calm nights",
            subtitle = "Set daily ceilings, cooldowns after a failed quiz, and bedtime so screens wind down without arguments.",
            highlight = "Parents steer from their phone. Kids keep learning on theirs — curiosity first, then the apps they love.",
            mockType = TourMockType.BEDTIME_PREVIEW,
            pills = listOf(
                Icons.Default.Bedtime to "Bedtime lock",
                Icons.Default.School to "Ages 3–12",
                Icons.Default.Tune to "Your rules, their pace",
            ),
        ),
    )

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeritColors.Surface)
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .padding(horizontal = MeritSpacing.margin),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MeritColors.SecondaryContainer.copy(alpha = 0.6f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = slides[pagerState.currentPage].stepLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnSecondaryContainer,
                    )
                }
            }

            TextButton(onClick = onSkip) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurfaceVariant,
                )
            }
        }

        // Non-scrollable body — compact so every slide fits one viewport.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            val slide = slides[page]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                when (slide.mockType) {
                    TourMockType.QUIZ_PREVIEW -> QuizPreviewCard()
                    TourMockType.ALLOWLIST_PREVIEW -> AllowlistPreviewCard()
                    TourMockType.BEDTIME_PREVIEW -> BedtimePreviewCard()
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = slide.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = slide.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeritColors.OnSurfaceVariant,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = slide.highlight,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MeritColors.Primary,
                        lineHeight = 18.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        slide.pills.forEach { (icon, text) ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MeritColors.SurfaceContainerHigh,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MeritColors.Primary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MeritColors.OnSurface,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(slides.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        label = "dot_width",
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(if (isSelected) MeritColors.Primary else MeritColors.SurfaceContainerHighest),
                    )
                }
            }

            Button(
                onClick = {
                    if (pagerState.currentPage < slides.size - 1) {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onContinue()
                    }
                },
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeritColors.PrimaryContainer,
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (pagerState.currentPage < slides.size - 1) "Continue" else "Get Started",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                        color = Color.White,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuizPreviewCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MeritColors.SurfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(MeritColors.PrimaryFixed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null,
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "AI Quiz • Grade 3 Math",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.Outline,
                        )
                        Text(
                            text = "Earn unlock minutes",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.OnSurface,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MeritColors.TertiaryFixed,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MeritColors.OnTertiaryFixedVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "+15 min",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.OnTertiaryFixedVariant,
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MeritColors.SurfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Question 2 of 3",
                        style = MaterialTheme.typography.labelSmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Maya shares 24 blueberries equally among 4 friends. How many does each get?",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MeritColors.SurfaceContainerLowest,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "4 berries",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MeritColors.OnSurface,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MeritColors.PrimaryContainer,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "6 berries",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AllowlistPreviewCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MeritColors.SurfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Approved apps only — quiz unlocks playtime",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MeritColors.OnSurfaceVariant,
            )
            listOf(
                "Duo ABC" to ("Learning" to true),
                "Khan Kids" to ("Math & reading" to true),
                "YouTube Kids" to ("Needs quiz pass" to true),
                "Roblox" to ("Blocked" to false),
            ).forEach { (name, info) ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MeritColors.SurfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MeritColors.OnSurface,
                            )
                            Text(
                                text = info.first,
                                style = MaterialTheme.typography.labelSmall,
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (info.second) MeritColors.PrimaryFixed else MeritColors.ErrorContainer,
                        ) {
                            Text(
                                text = if (info.second) "Allowed" else "Blocked",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (info.second) MeritColors.OnPrimaryFixedVariant else MeritColors.OnErrorContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BedtimePreviewCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MeritColors.SurfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(MeritColors.InverseSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = MeritColors.InverseOnSurface,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        text = "Bedtime wind-down",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurface,
                    )
                }
                Text(
                    text = "8:30 PM",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.Primary,
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MeritColors.SurfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.LockClock,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Column {
                        Text(
                            text = "Learning all day, rest at night",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = "Quizzes earn minutes until quiet hours — then the device soft-locks for sleep.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MeritColors.OnSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
