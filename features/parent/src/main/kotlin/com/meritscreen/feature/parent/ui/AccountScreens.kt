package com.meritscreen.feature.parent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import com.meritscreen.core.common.domain.AgeBand
import com.meritscreen.core.common.domain.AvatarPreset
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.meritscreen.core.ui.components.ChildAvatar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.firebase.family.FamilyChildProfile
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.LoadingState
import com.meritscreen.core.ui.components.MeritPrimaryButton
import com.meritscreen.core.ui.components.MeritSecondaryButton
import com.meritscreen.core.ui.components.PinInputField
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing

@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onAddChild: () -> Unit,
    onResetPin: () -> Unit,
    onDeleteFamily: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        UiState.Loading -> LoadingState()
        UiState.Empty -> Unit
        is UiState.Error -> ErrorState(message = current.error.userMessage, onRetry = viewModel::refresh)
        is UiState.Success -> {
            AccountContent(
                data = current.data,
                onBack = onBack,
                onAddChild = onAddChild,
                onResetPin = onResetPin,
                onDeleteFamily = onDeleteFamily,
                onSignOut = onSignOut,
            )
        }
    }
}

@Composable
fun AccountContent(
    data: AccountUi,
    onBack: () -> Unit,
    onAddChild: () -> Unit,
    onResetPin: () -> Unit,
    onDeleteFamily: () -> Unit,
    onSignOut: () -> Unit,
) {
    var showSignOutConfirm by remember { mutableStateOf(false) }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = {
                Text(
                    text = "Sign out of Parent App?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.OnSurface,
                )
            },
            text = {
                Text(
                    text = "Your children's devices will stay safely supervised and paired. Scheduled bedtime locks, time limits, and AI learning rules will continue protecting them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeritColors.OnSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MeritColors.Primary),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Sign out", color = MeritColors.OnPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSignOutConfirm = false },
                ) {
                    Text("Cancel", color = MeritColors.OnSurfaceVariant)
                }
            },
            containerColor = MeritColors.SurfaceContainerLowest,
            shape = RoundedCornerShape(16.dp),
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MeritColors.SurfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Top Header Bar
            AccountHeader(onBack = onBack)

            // 2. Parent Profile Hero Card
            ParentProfileCard(data = data)

            // 3. App Appearance & Theme — debug builds only (empty in release)
            AccountDebugThemeSection()

            // 4. Family Household & Child Slots Card
            FamilyCapacityCard(
                data = data,
                onAddChild = onAddChild,
            )

            // 4. Security & Access Settings Card
            SecuritySettingsCard(
                onResetPin = onResetPin,
            )

            // 5. Session Supervision & Sign Out Card
            SessionSupervisionCard(
                onSignOutClick = { showSignOutConfirm = true },
            )

            // 6. Danger Zone (Delete Family) Card
            DangerZoneCard(
                onDeleteFamily = onDeleteFamily,
            )

            // 7. System & Version Footer
            AccountFooter()

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// SUBCOMPONENTS
// ---------------------------------------------------------------------------

@Composable
private fun AccountHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MeritColors.SurfaceContainer),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MeritColors.OnSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column {
                Text(
                    text = "Account & Family",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    color = MeritColors.OnSurface,
                )
                Text(
                    text = "Preferences, supervision & security",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MeritColors.OnSurfaceVariant,
                )
            }
        }

        // Active Status Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9999.dp))
                .background(MeritColors.PrimaryFixed.copy(alpha = 0.6f))
                .padding(horizontal = 9.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MeritColors.Primary),
                )
                Text(
                    text = "Family Active",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                    ),
                    color = MeritColors.OnPrimaryFixedVariant,
                )
            }
        }
    }
}

@Composable
private fun ParentProfileCard(data: AccountUi) {
    val initial = (data.displayName.takeIf { it.isNotBlank() } ?: data.email.takeIf { it.isNotBlank() } ?: "P")
        .firstOrNull()?.uppercaseChar()?.toString() ?: "P"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Large Avatar with Verification Badge
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MeritColors.PrimaryFixed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MeritColors.OnPrimaryFixed,
                            ),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MeritColors.SurfaceContainerLowest)
                            .padding(2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MeritColors.Primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MeritColors.OnPrimary,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }

                // Name & Email
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = data.displayName.takeUnless { it.equals("there", ignoreCase = true) }
                            ?: data.email.ifBlank { "Parent Account" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                        ),
                        color = MeritColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (data.email.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MailOutline,
                                contentDescription = null,
                                tint = MeritColors.OnSurfaceVariant,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = data.email,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MeritColors.OnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MeritColors.OutlineVariant.copy(alpha = 0.25f))

            // Role & Security Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Role Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MeritColors.Primary.copy(alpha = 0.08f))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MeritColors.Primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "Family Organizer",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                            ),
                            color = MeritColors.Primary,
                        )
                    }
                }

                // PIN Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MeritColors.SecondaryContainer.copy(alpha = 0.5f))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = MeritColors.OnSecondaryContainer,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "PIN Secured",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                            ),
                            color = MeritColors.OnSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FamilyCapacityCard(
    data: AccountUi,
    onAddChild: () -> Unit,
) {
    val maxChildren = AppConfig.MAX_CHILDREN_PER_PARENT
    val currentCount = data.childCount
    val fraction = (currentCount.toFloat() / maxChildren.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MeritColors.PrimaryFixed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = MeritColors.OnPrimaryFixed,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column {
                        Text(
                            text = data.familyName.ifBlank { "My Family" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            ),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = "Household Supervision Hub",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9999.dp))
                        .background(MeritColors.SurfaceContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "$currentCount / $maxChildren Slots",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        ),
                        color = MeritColors.Primary,
                    )
                }
            }

            // Capacity Meter
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(9999.dp)),
                    color = MeritColors.Primary,
                    trackColor = MeritColors.Primary.copy(alpha = 0.12f),
                    strokeCap = StrokeCap.Round,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (currentCount == 1) "1 child registered" else "$currentCount children registered",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MeritColors.OnSurfaceVariant,
                    )
                    Text(
                        text = "${maxChildren - currentCount} slots remaining",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }

            // Children Previews (if any)
            if (data.children.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    data.children.forEach { child ->
                        ChildSummaryChip(child = child)
                    }
                }
            }

            // Add Child Action Button
            if (currentCount < maxChildren) {
                Button(
                    onClick = onAddChild,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MeritColors.Primary),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint = MeritColors.OnPrimary,
                            modifier = Modifier.size(17.dp),
                        )
                        Text(
                            text = "Add another child",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            ),
                            color = MeritColors.OnPrimary,
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MeritColors.SurfaceContainer)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Maximum child capacity reached ($maxChildren/$maxChildren)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChildSummaryChip(child: FamilyChildProfile) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MeritColors.SurfaceContainerLow,
        border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                ChildAvatar(
                    avatar = child.avatar,
                    size = 22.dp,
                    background = MeritColors.PrimaryFixed,
                    contentDescription = "${child.displayName} avatar",
                )
            }
            Column {
                Text(
                    text = child.displayName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MeritColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = child.ageBand.displayLabel,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    color = MeritColors.OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SecuritySettingsCard(
    onResetPin: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "SECURITY & CONTROLS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontSize = 10.sp,
                ),
                color = MeritColors.Primary,
            )

            // Reset Parent PIN Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onResetPin)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MeritColors.PrimaryFixed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = MeritColors.OnPrimaryFixed,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Reset Parent PIN",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            ),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = "Change 4-digit code securing child devices",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MeritColors.OnSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            HorizontalDivider(color = MeritColors.OutlineVariant.copy(alpha = 0.2f))

            // Tamper Supervision Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MeritColors.TertiaryFixed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MeritColors.OnTertiaryFixed,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Device Supervision Mode",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            ),
                            color = MeritColors.OnSurface,
                        )
                        Text(
                            text = "Tamper resistance & lockout enforcement active",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9999.dp))
                        .background(MeritColors.Tertiary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "Enforced",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                        ),
                        color = MeritColors.Tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionSupervisionCard(
    onSignOutClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
        border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "SESSION & SUPERVISION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontSize = 10.sp,
                ),
                color = MeritColors.Primary,
            )

            // Explanatory Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MeritColors.PrimaryContainer.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MeritColors.Primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Signing out will only disconnect this parent app. Scheduled bedtime locks, screen allowances, and AI learning rules will continue protecting your children's devices seamlessly.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        ),
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }

            // Sign Out Button
            OutlinedButton(
                onClick = onSignOutClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MeritColors.OutlineVariant),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MeritColors.OnSurface,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Sign out",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        ),
                        color = MeritColors.OnSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun DangerZoneCard(
    onDeleteFamily: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MeritColors.ErrorContainer.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, MeritColors.Error.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MeritColors.Error.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MeritColors.Error,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column {
                    Text(
                        text = "Delete Family Account",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        ),
                        color = MeritColors.Error,
                    )
                    Text(
                        text = "Permanently erase all children, paired devices, and history.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }

            OutlinedButton(
                onClick = onDeleteFamily,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MeritColors.Error.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MeritColors.Error,
                ),
            ) {
                Text(
                    text = "Delete family…",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    ),
                    color = MeritColors.Error,
                )
            }
        }
    }
}

@Composable
private fun AccountFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "Watching Parent • v1.0.0",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MeritColors.OnSurfaceVariant.copy(alpha = 0.7f),
        )
        Text(
            text = "AI-Driven Adaptive Learning & Screen Balance",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MeritColors.OnSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

// ---------------------------------------------------------------------------
// OTHER SCREENS IN ACCOUNT FLOW (POLISHED WITH SAME THEME)
// ---------------------------------------------------------------------------

@Composable
fun AddChildAccountScreen(
    onBack: () -> Unit,
    onCreated: (childId: String, childName: String, ageBand: AgeBand, avatar: AvatarPreset) -> Unit,
    viewModel: AddChildAccountViewModel = hiltViewModel(),
) {
    val uiModel by viewModel.uiModel.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val ageBand by viewModel.ageBand.collectAsStateWithLifecycle()
    val avatar by viewModel.avatar.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val curriculumFocusIds by viewModel.curriculumFocusIds.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val created by viewModel.createdChildId.collectAsStateWithLifecycle()
    val createdName by viewModel.createdChildName.collectAsStateWithLifecycle()
    val createdAgeBand by viewModel.createdAgeBand.collectAsStateWithLifecycle()
    val createdAvatar by viewModel.createdAvatar.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    LaunchedEffect(created, createdName, createdAgeBand, createdAvatar) {
        val id = created ?: return@LaunchedEffect
        val childName = createdName ?: return@LaunchedEffect
        val band = createdAgeBand ?: return@LaunchedEffect
        val childAvatar = createdAvatar ?: return@LaunchedEffect
        onCreated(id, childName, band, childAvatar)
    }
    com.meritscreen.feature.onboarding.ui.AddChildContent(
        children = uiModel.pendingChildren,
        canAddMore = uiModel.canAddMore,
        name = name,
        ageBand = ageBand,
        avatar = avatar,
        language = language,
        curriculumFocusIds = curriculumFocusIds,
        formError = error,
        isSubmitting = saving,
        stepLabel = "Step 1 of 5",
        progressFraction = 1f / 3f,
        onNameChanged = viewModel::onNameChanged,
        onAgeBandSelected = viewModel::onAgeBand,
        onAvatarSelected = viewModel::onAvatar,
        onLanguageSelected = viewModel::onLanguageSelected,
        onCurriculumFocusToggled = viewModel::onCurriculumFocusToggled,
        onAddChild = viewModel::addChildToList,
        onRemoveChild = viewModel::removeChild,
        onSaveAndContinue = viewModel::saveAndContinue,
        onBack = onBack,
    )
}

@Composable
fun ResetPinScreen(
    onBack: () -> Unit,
    viewModel: ResetPinViewModel = hiltViewModel(),
) {
    val pin by viewModel.pin.collectAsStateWithLifecycle()
    val confirm by viewModel.confirm.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MeritColors.SurfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MeritColors.SurfaceContainer),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MeritColors.OnSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        text = "Reset Parent PIN",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MeritColors.OnSurface,
                    )
                    Text(
                        text = "Manage your 4-digit security code",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }

            // PIN Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
                border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Explanatory note
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MeritColors.PrimaryContainer.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "This PIN unlocks settings and parental controls on child devices. Updates will sync automatically when devices connect online.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "New 4-Digit PIN",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurface,
                        )
                        PinInputField(
                            value = pin,
                            onValueChange = viewModel::onPin,
                            maxLength = AppConfig.PARENT_PIN_MAX_LENGTH,
                            isError = error != null,
                            contentDescriptionLabel = "New PIN",
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Confirm PIN",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurface,
                        )
                        PinInputField(
                            value = confirm,
                            onValueChange = viewModel::onConfirm,
                            maxLength = AppConfig.PARENT_PIN_MAX_LENGTH,
                            isError = error != null,
                            contentDescriptionLabel = "Confirm PIN",
                        )
                    }

                    if (error != null) {
                        Text(
                            text = error.orEmpty(),
                            color = MeritColors.Error,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        )
                    }

                    if (saved) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "PIN updated successfully",
                                color = MeritColors.Primary,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    MeritPrimaryButton(
                        text = "Save PIN",
                        onClick = viewModel::save,
                        loading = saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun DeleteFamilyScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: DeleteFamilyViewModel = hiltViewModel(),
) {
    val confirm by viewModel.confirmText.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) {
        if (deleted) onDeleted()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MeritColors.SurfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MeritColors.SurfaceContainer),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MeritColors.OnSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        text = "Delete Family",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        ),
                        color = MeritColors.Error,
                    )
                    Text(
                        text = "Irreversible account deletion",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            }

            // Warning Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MeritColors.SurfaceContainerLowest),
                border = BorderStroke(1.dp, MeritColors.Error.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MeritColors.ErrorContainer.copy(alpha = 0.25f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MeritColors.Error,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "This permanently deletes your family, children, devices, and history. Child phones will need to be paired again.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                ),
                                color = MeritColors.OnErrorContainer,
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "To confirm, please type DELETE below:",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurface,
                        )
                        OutlinedTextField(
                            value = confirm,
                            onValueChange = viewModel::onConfirmText,
                            label = { Text("Confirmation") },
                            placeholder = { Text("DELETE") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeritColors.Error,
                                cursorColor = MeritColors.Error,
                            ),
                        )
                    }

                    if (error != null) {
                        Text(
                            text = error.orEmpty(),
                            color = MeritColors.Error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Button(
                        onClick = viewModel::delete,
                        enabled = confirm.trim() == "DELETE" && !saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MeritColors.Error,
                            disabledContainerColor = MeritColors.Error.copy(alpha = 0.3f),
                        ),
                    ) {
                        Text(
                            text = if (saving) "Deleting..." else "Delete family forever",
                            fontWeight = FontWeight.Bold,
                            color = MeritColors.OnError,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsSettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    ParentScreenScaffold(title = "Notifications", onBack = onBack) {
        ParentHelperText(
            "These notification preferences apply to this parent device.",
        )
        Spacer(Modifier.height(MeritSpacing.lg))
        SwitchRow("Daily summary", prefs.dailySummary, viewModel::setDaily)
        SwitchRow("Time up alerts", prefs.timeUp, viewModel::setTimeUp)
        SwitchRow("Quiz failed 3 times", prefs.quizFailedRepeatedly, viewModel::setQuizFailed)
    }
}
