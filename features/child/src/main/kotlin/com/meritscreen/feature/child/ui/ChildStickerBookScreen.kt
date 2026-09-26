package com.meritscreen.feature.child.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.common.domain.StickerStage
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.LoadingState
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing

@Composable
fun ChildStickerBookScreen(
    onBack: () -> Unit,
    viewModel: ChildStickerBookViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        UiState.Loading -> LoadingState(message = "Opening your sticker book")
        UiState.Empty -> Unit
        is UiState.Error -> ErrorState(
            message = current.error.userMessage,
            onRetry = viewModel::refresh,
        )
        is UiState.Success -> ChildStickerBookContent(
            data = current.data,
            onBack = onBack,
        )
    }
}

@Composable
private fun ChildStickerBookContent(
    data: ChildStickerBookUi,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeritColors.SurfaceContainerLow)
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MeritColors.OnSurface,
                )
            }
            Text(
                text = "Sticker Book",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MeritColors.OnSurface,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MeritColors.PrimaryContainer,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Explorer Level ${data.explorerLevel}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MeritColors.OnPrimaryContainer,
                    )
                    Text(
                        text = "${data.ownedCount} of ${data.catalogTotal} stickers • ${data.xp} XP",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MeritColors.OnPrimaryContainer,
                    )
                    if (data.xpToNext != null) {
                        Text(
                            text = "${data.xpToNext} XP to next level",
                            style = MaterialTheme.typography.labelMedium,
                            color = MeritColors.OnPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.md))

            StickerStage.entries.forEach { stage ->
                val stickers = data.stages[stage].orEmpty()
                if (stickers.isEmpty()) return@forEach
                Text(
                    text = "${stage.displayLabel} · Levels ${stage.minExplorerLevel}–${stage.maxExplorerLevel}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MeritColors.OnSurface,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                stickers.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { item ->
                            StickerTile(
                                item = item,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.xl))
        }
    }
}

@Composable
private fun StickerTile(
    item: StickerBookItem,
    modifier: Modifier = Modifier,
) {
    val owned = item.owned
    Surface(
        modifier = modifier.alpha(if (owned) 1f else 0.55f),
        shape = RoundedCornerShape(16.dp),
        color = MeritColors.SurfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        if (owned) MeritColors.SecondaryContainer else MeritColors.SurfaceContainerHigh,
                        RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (owned) item.definition.emoji else "❔",
                    fontSize = 28.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (owned) item.definition.title else "Locked",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MeritColors.OnSurface,
                textAlign = TextAlign.Center,
            )
            if (!owned && item.definition.hint.isNotBlank()) {
                Text(
                    text = item.definition.hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MeritColors.OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
