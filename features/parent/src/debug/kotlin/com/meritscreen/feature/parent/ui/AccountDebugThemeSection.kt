package com.meritscreen.feature.parent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.ui.theme.AppThemeManager
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritThemeCatalog
import com.meritscreen.core.ui.theme.MeritThemeId
import com.meritscreen.core.ui.theme.ThemeMode

/**
 * Debug-only Account & Family theme picker. Compiled into debug APKs only —
 * release builds use the empty stub in `src/release`.
 */
@Composable
fun AccountDebugThemeSection() {
    val themeConfig by AppThemeManager.themeConfig.collectAsStateWithLifecycle()
    val activeThemeId = themeConfig.themeId
    val activeMode = themeConfig.mode

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MeritColors.PrimaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = null,
                        tint = MeritColors.OnPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Appearance & Theme",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnSurface,
                    )
                    Text(
                        text = "Debug only · choose color pattern and display mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(
                color = MeritColors.SurfaceContainer,
                thickness = 1.dp,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DISPLAY MODE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                    color = MeritColors.OnSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MeritColors.SurfaceContainer)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val modes = listOf(
                        Triple(ThemeMode.SYSTEM, "Auto", Icons.Filled.BrightnessAuto),
                        Triple(ThemeMode.LIGHT, "Light", Icons.Filled.LightMode),
                        Triple(ThemeMode.DARK, "Dark", Icons.Filled.DarkMode),
                    )
                    modes.forEach { (mode, label, icon) ->
                        val isSelected = activeMode == mode
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MeritColors.Primary else Color.Transparent,
                                )
                                .clickable { AppThemeManager.setMode(mode) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) MeritColors.OnPrimary else MeritColors.OnSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = if (isSelected) MeritColors.OnPrimary else MeritColors.OnSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "COLOR PATTERNS (${MeritThemeCatalog.allThemes.size} PRESETS)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Text(
                        text = activeThemeId.displayName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.Primary,
                    )
                }

                MeritThemeCatalog.allThemes.forEach { theme ->
                    val isSelected = activeThemeId == theme.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { AppThemeManager.setTheme(theme.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MeritColors.SurfaceContainerHigh
                        } else {
                            MeritColors.SurfaceContainerLow
                        },
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                MeritColors.Primary
                            } else {
                                MeritColors.OutlineVariant.copy(alpha = 0.35f)
                            },
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                listOf(
                                    theme.previewPrimary,
                                    theme.previewAccent,
                                    theme.previewSurface,
                                    theme.previewBackground,
                                ).forEach { swatchColor ->
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(swatchColor)
                                            .border(
                                                0.75.dp,
                                                MeritColors.OutlineVariant.copy(alpha = 0.5f),
                                                CircleShape,
                                            ),
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = theme.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.SemiBold
                                            },
                                        ),
                                        color = MeritColors.OnSurface,
                                    )
                                    if (theme.id == MeritThemeId.CLASSIC) {
                                        Text(
                                            text = "Default",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = MeritColors.Primary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MeritColors.PrimaryContainer.copy(alpha = 0.4f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = theme.description,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MeritColors.OnSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Icon(
                                imageVector = if (isSelected) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.RadioButtonUnchecked
                                },
                                contentDescription = if (isSelected) "Selected" else "Select",
                                tint = if (isSelected) MeritColors.Primary else MeritColors.OutlineVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MeritColors.SurfaceContainerHigh,
                border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.4f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "LIVE PREVIEW",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(containerColor = MeritColors.Primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "Primary",
                                color = MeritColors.OnPrimary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MeritColors.PrimaryContainer,
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            Text(
                                text = "Container Tonal",
                                style = MaterialTheme.typography.labelMedium,
                                color = MeritColors.OnPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MeritColors.SurfaceContainerLowest,
                            border = BorderStroke(1.dp, MeritColors.Outline),
                        ) {
                            Text(
                                text = "Outline",
                                style = MaterialTheme.typography.labelMedium,
                                color = MeritColors.OnSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
