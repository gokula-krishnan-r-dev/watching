package com.meritscreen.feature.child.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.common.domain.ExplorerLevelCalculator
import com.meritscreen.core.common.domain.StickerCatalog
import com.meritscreen.core.common.domain.StickerDefinition
import com.meritscreen.core.common.domain.StickerStage
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.feature.child.data.StickerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class StickerBookItem(
    val definition: StickerDefinition,
    val owned: Boolean,
)

@Immutable
data class ChildStickerBookUi(
    val explorerLevel: Int,
    val xp: Int,
    val xpToNext: Int?,
    val ownedCount: Int,
    val catalogTotal: Int,
    val stages: Map<StickerStage, List<StickerBookItem>>,
)

@HiltViewModel
class ChildStickerBookViewModel @Inject constructor(
    private val pairingStore: ChildPairingStore,
    private val stickerRepository: StickerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ChildStickerBookUi>>(UiState.Loading)
    val uiState: StateFlow<UiState<ChildStickerBookUi>> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val childId = pairingStore.get()?.childId
            if (childId.isNullOrBlank()) {
                _uiState.value = UiState.Error(AppError.Auth("This device is not paired."))
                return@launch
            }
            runCatching {
                val progress = stickerRepository.getProgress(childId)
                val ownedIds = stickerRepository.listUnlocks(childId).map { it.stickerId }.toSet()
                val stages = StickerStage.entries.associateWith { stage ->
                    StickerCatalog.forStage(stage).map { def ->
                        StickerBookItem(definition = def, owned = def.stickerId in ownedIds)
                    }
                }
                ChildStickerBookUi(
                    explorerLevel = progress.explorerLevel,
                    xp = progress.xp,
                    xpToNext = ExplorerLevelCalculator.xpToNextLevel(progress.xp),
                    ownedCount = ownedIds.size,
                    catalogTotal = StickerCatalog.all.size,
                    stages = stages,
                )
            }.fold(
                onSuccess = { _uiState.value = UiState.Success(it) },
                onFailure = {
                    _uiState.value = UiState.Error(AppError.Unknown(it.message ?: "Couldn't open stickers."))
                },
            )
        }
    }
}
