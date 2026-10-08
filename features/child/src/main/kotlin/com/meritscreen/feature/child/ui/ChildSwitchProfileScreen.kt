package com.meritscreen.feature.child.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.result.Outcome
import com.meritscreen.core.security.pairing.ChildPairingCredential
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.core.ui.components.MeritSecondaryButton
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import com.meritscreen.feature.child.domain.SwitchActiveChildUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SwitchProfileUi(
    val profiles: List<ChildPairingCredential> = emptyList(),
    val activeChildId: String? = null,
    val busyChildId: String? = null,
    val error: String? = null,
    val switched: Boolean = false,
)

@HiltViewModel
class ChildSwitchProfileViewModel @Inject constructor(
    private val pairingStore: ChildPairingStore,
    private val switchActiveChild: SwitchActiveChildUseCase,
) : ViewModel() {

    private val _ui = MutableStateFlow(SwitchProfileUi())
    val ui: StateFlow<SwitchProfileUi> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val profiles = pairingStore.listProfiles()
            _ui.value = SwitchProfileUi(
                profiles = profiles,
                activeChildId = pairingStore.get()?.childId,
            )
        }
    }

    fun select(childId: String) {
        val current = _ui.value
        if (current.busyChildId != null) return
        if (childId == current.activeChildId) {
            _ui.value = current.copy(switched = true)
            return
        }
        viewModelScope.launch {
            _ui.value = current.copy(busyChildId = childId, error = null)
            when (val result = switchActiveChild(childId)) {
                is Outcome.Success -> {
                    _ui.value = SwitchProfileUi(
                        profiles = pairingStore.listProfiles(),
                        activeChildId = result.value.childId,
                        switched = true,
                    )
                }
                is Outcome.Failure -> {
                    _ui.value = current.copy(
                        busyChildId = null,
                        error = result.error.userMessage,
                        profiles = pairingStore.listProfiles(),
                        activeChildId = pairingStore.get()?.childId,
                    )
                }
            }
        }
    }
}

@Composable
fun ChildSwitchProfileScreen(
    onSwitched: () -> Unit,
    onBack: () -> Unit,
    onAddAnotherChild: () -> Unit,
    viewModel: ChildSwitchProfileViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.switched) {
        if (ui.switched) onSwitched()
    }

    ChildScreenScaffold(title = "Switch child", onBack = onBack) {
        Text(
            text = "Who is using this device?",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MeritColors.OnSurface,
        )
        Spacer(Modifier.height(MeritSpacing.xs))
        ChildHelperText(
            "Timers and app rules switch with the profile. Up to ${AppConfig.MAX_CHILDREN_PER_PARENT} children can share this device.",
        )
        Spacer(Modifier.height(MeritSpacing.md))

        if (ui.profiles.isEmpty()) {
            ChildHelperText("No child profiles are paired on this device yet.")
        } else {
            ui.profiles.forEach { profile ->
                val isActive = profile.childId == ui.activeChildId
                val isBusy = profile.childId == ui.busyChildId
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isActive) {
                        MeritColors.Primary.copy(alpha = 0.10f)
                    } else {
                        MeritColors.SurfaceContainerLowest
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isActive) MeritColors.Primary.copy(alpha = 0.35f)
                        else MeritColors.OutlineVariant.copy(alpha = 0.35f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable(enabled = ui.busyChildId == null) {
                            viewModel.select(profile.childId)
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(MeritSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MeritColors.SecondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MeritColors.OnSecondaryContainer,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.displayName.ifBlank { "Child" },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MeritColors.OnSurface,
                            )
                            Text(
                                text = if (isActive) "Active now" else "Tap to switch",
                                style = MaterialTheme.typography.bodySmall,
                                color = MeritColors.OnSurfaceVariant,
                            )
                        }
                        when {
                            isBusy -> CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                            isActive -> Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active",
                                tint = MeritColors.Primary,
                            )
                        }
                    }
                }
            }
        }

        ui.error?.let { message ->
            Spacer(Modifier.height(MeritSpacing.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(MeritSpacing.lg))
        if (ui.profiles.size < AppConfig.MAX_CHILDREN_PER_PARENT) {
            MeritSecondaryButton(
                text = "Pair another child",
                onClick = onAddAnotherChild,
                enabled = ui.busyChildId == null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MeritSpacing.xs))
            ChildHelperText(
                "Ask a parent to open Device Handshake for the other child, then enter the 6-digit code on this device.",
            )
        }
    }
}
