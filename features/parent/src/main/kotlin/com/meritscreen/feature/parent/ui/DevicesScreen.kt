package com.meritscreen.feature.parent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.domain.DeviceSummary
import com.meritscreen.core.common.error.AppError
import com.meritscreen.core.common.error.AppErrorMapper
import com.meritscreen.core.common.session.ParentSessionRepository
import com.meritscreen.core.common.ui.UiState
import com.meritscreen.core.firebase.family.ParentControlStore
import com.meritscreen.core.ui.components.ErrorState
import com.meritscreen.core.ui.components.LoadingState
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritMonoFontFamily
import com.meritscreen.core.ui.theme.MeritSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class DevicesUi(
    val childId: String,
    val childName: String,
    val devices: List<DeviceCardUi>,
    val activeDeviceCount: Int,
    val nowMs: Long,
)

@Immutable
data class DeviceCardUi(
    val device: DeviceSummary,
    val connectionStatus: DeviceConnectionStatus,
    val connectionLabel: String,
    val lastSeenLabel: String,
    val launcherLabel: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DevicesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val parentSessionRepository: ParentSessionRepository,
    private val parentControlStore: ParentControlStore,
) : ViewModel() {

    private val childId = savedStateHandle.toRoute<DevicesRoute>().childId

    private val clock = MutableStateFlow(System.currentTimeMillis())
    private val _busyDeviceId = MutableStateFlow<String?>(null)
    val busyDeviceId: StateFlow<String?> = _busyDeviceId.asStateFlow()
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    val uiState: StateFlow<UiState<DevicesUi>> = parentSessionRepository.session
        .flatMapLatest { session ->
            val familyId = session?.familyId
            when {
                familyId.isNullOrBlank() -> flowOf(UiState.Error(AppError.Auth()))
                childId.isBlank() -> flowOf(UiState.Error(AppError.NotFound("We couldn't find that child.")))
                else -> combine(
                    parentControlStore.observeDevices(familyId, childId),
                    clock,
                    flow {
                        val profile = runCatching {
                            parentControlStore.listChildren(familyId)
                                .firstOrNull { it.childId == childId }
                        }.getOrNull()
                        emit(profile?.displayName?.ifBlank { null } ?: "Child")
                    },
                ) { devices, nowMs, childName ->
                    val cards = devices
                        .sortedWith(
                            compareByDescending<DeviceSummary> { !it.revoked }
                                .thenByDescending { it.lastSeenAtEpochMs ?: 0L },
                        )
                        .map { device -> toCard(device, nowMs) }
                    UiState.Success(
                        DevicesUi(
                            childId = childId,
                            childName = childName,
                            devices = cards,
                            activeDeviceCount = cards.count { !it.device.revoked },
                            nowMs = nowMs,
                        ),
                    ) as UiState<DevicesUi>
                }.catch { emit(UiState.Error(AppErrorMapper.from(it))) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                clock.value = System.currentTimeMillis()
            }
        }
    }

    fun refresh() {
        clock.value = System.currentTimeMillis()
        _actionError.value = null
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun disconnectDevice(deviceId: String) = setRevoked(deviceId, revoked = true)

    fun restoreDevice(deviceId: String) = setRevoked(deviceId, revoked = false)

    private fun setRevoked(deviceId: String, revoked: Boolean) {
        viewModelScope.launch {
            val familyId = parentSessionRepository.current()?.familyId ?: return@launch
            _busyDeviceId.value = deviceId
            _actionError.value = null
            runCatching {
                parentControlStore.setDeviceRevoked(familyId, childId, deviceId, revoked)
            }.onFailure {
                _actionError.value = AppErrorMapper.from(it).userMessage
            }
            _busyDeviceId.value = null
            clock.value = System.currentTimeMillis()
        }
    }

    private companion object {
        fun toCard(device: DeviceSummary, nowMs: Long): DeviceCardUi {
            val status = resolveConnection(device, nowMs)
            return DeviceCardUi(
                device = device,
                connectionStatus = status,
                connectionLabel = when (status) {
                    DeviceConnectionStatus.Connected -> "Online"
                    DeviceConnectionStatus.Disconnected -> "Offline"
                    DeviceConnectionStatus.Paused -> "Paused"
                    DeviceConnectionStatus.Unpaired -> "Disconnected"
                },
                lastSeenLabel = "Last seen ${formatRelativeTime(device.lastSeenAtEpochMs)}",
                launcherLabel = when (device.launcherDefault) {
                    true -> "Merit Launcher active"
                    false -> "Home app not set"
                    null -> "Launcher unknown"
                },
            )
        }

        fun resolveConnection(device: DeviceSummary, nowMs: Long): DeviceConnectionStatus {
            if (device.revoked) return DeviceConnectionStatus.Unpaired
            val lastSeen = device.lastSeenAtEpochMs
            val onlineMs = AppConfig.DEVICE_ONLINE_THRESHOLD_MINUTES * 60_000L
            val connected = lastSeen != null && nowMs - lastSeen <= onlineMs
            return if (connected) DeviceConnectionStatus.Connected else DeviceConnectionStatus.Disconnected
        }
    }
}

@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    onPairDevice: () -> Unit = {},
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busyDeviceId by viewModel.busyDeviceId.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    when (val current = state) {
        UiState.Loading -> LoadingState(message = "Loading devices")
        UiState.Empty -> Unit
        is UiState.Error -> ErrorState(message = current.error.userMessage, onRetry = viewModel::refresh)
        is UiState.Success -> DevicesContent(
            data = current.data,
            busyDeviceId = busyDeviceId,
            actionError = actionError,
            onBack = onBack,
            onPairDevice = onPairDevice,
            onRefresh = viewModel::refresh,
            onDisconnect = viewModel::disconnectDevice,
            onRestore = viewModel::restoreDevice,
            onClearError = viewModel::clearActionError,
        )
    }
}

@Composable
fun DevicesContent(
    data: DevicesUi,
    busyDeviceId: String? = null,
    actionError: String? = null,
    onBack: () -> Unit = {},
    onPairDevice: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onDisconnect: (deviceId: String) -> Unit = {},
    onRestore: (deviceId: String) -> Unit = {},
    onClearError: () -> Unit = {},
) {
    var pendingDisconnectId by remember { mutableStateOf<String?>(null) }
    val pendingDevice = data.devices.firstOrNull { it.device.deviceId == pendingDisconnectId }

    if (pendingDevice != null) {
        AlertDialog(
            onDismissRequest = {
                if (busyDeviceId == null) pendingDisconnectId = null
            },
            title = { Text("Disconnect this device?") },
            text = {
                Text(
                    "“${pendingDevice.device.model?.ifBlank { null } ?: "Android device"}” will sign out " +
                        "on its next check-in (within about ${AppConfig.DEVICE_ONLINE_THRESHOLD_MINUTES} minutes, " +
                        "sooner if Watching is open). Use this for a lost or handed-down phone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDisconnect(pendingDevice.device.deviceId)
                        pendingDisconnectId = null
                    },
                    enabled = busyDeviceId == null,
                ) {
                    Text("Disconnect", color = MeritColors.Error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDisconnectId = null },
                    enabled = busyDeviceId == null,
                ) {
                    Text("Cancel")
                }
            },
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
                .padding(horizontal = MeritSpacing.lg, vertical = MeritSpacing.xs),
        ) {
            DevicesHeader(onBack = onBack, onRefresh = onRefresh)

            Spacer(Modifier.height(MeritSpacing.md))

            DevicesIntroCard()

            Spacer(Modifier.height(MeritSpacing.md))

            AnimatedVisibility(visible = actionError != null, enter = fadeIn(), exit = fadeOut()) {
                if (actionError != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MeritColors.ErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onClearError),
                    ) {
                        Text(
                            text = actionError,
                            color = MeritColors.OnErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(MeritSpacing.md),
                        )
                    }
                    Spacer(Modifier.height(MeritSpacing.md))
                }
            }

            // Pair / add new device
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MeritColors.Primary,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable(onClick = onPairDevice),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = MeritColors.OnPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (data.devices.isEmpty()) "Pair first device" else "Pair new device",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MeritColors.OnPrimary,
                    )
                }
            }

            Spacer(Modifier.height(MeritSpacing.md))

            if (data.devices.isEmpty()) {
                EmptyDevicesCard(onPairDevice = onPairDevice)
            } else {
                data.devices.forEach { card ->
                    DeviceInfoCard(
                        card = card,
                        childId = data.childId,
                        busy = busyDeviceId == card.device.deviceId,
                        onDisconnect = { pendingDisconnectId = card.device.deviceId },
                        onRestore = { onRestore(card.device.deviceId) },
                        onReconnectPair = onPairDevice,
                        onRefresh = onRefresh,
                    )
                    Spacer(Modifier.height(MeritSpacing.md))
                }
            }

            Spacer(Modifier.height(MeritSpacing.xl))
        }
    }
}

@Composable
private fun DevicesHeader(onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MeritColors.OnSurface,
                )
            }
            Column {
                Text(
                    text = "Devices",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                )
                Text(
                    text = "Manage paired phones & tablets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh status",
                tint = MeritColors.Primary,
            )
        }
    }
}


@Composable
private fun DevicesIntroCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MeritColors.SurfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(MeritSpacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MeritColors.PrimaryFixed),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PhonelinkSetup,
                    contentDescription = null,
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Device kill switch",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Disconnect signs the device out on its next heartbeat. Pair again any time with a fresh QR or code. Storage is not collected — only model, OS, battery, and sync status from the child heartbeat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeritColors.OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyDevicesCard(onPairDevice: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MeritColors.SurfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MeritSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Default.TabletAndroid,
                contentDescription = null,
                tint = MeritColors.Primary,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = "No device paired yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MeritColors.OnSurface,
            )
            Text(
                text = "Open Watching on the child’s phone and scan the parent QR, or enter the pairing code.",
                style = MaterialTheme.typography.bodySmall,
                color = MeritColors.OnSurfaceVariant,
            )
            Spacer(Modifier.height(MeritSpacing.xs))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MeritColors.PrimaryFixed,
                modifier = Modifier.clickable(onClick = onPairDevice),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MeritColors.Primary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Start pairing",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MeritColors.OnPrimaryFixedVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(
    card: DeviceCardUi,
    childId: String,
    busy: Boolean,
    onDisconnect: () -> Unit,
    onRestore: () -> Unit,
    onReconnectPair: () -> Unit,
    onRefresh: () -> Unit,
) {
    val device = card.device
    val statusColor = when (card.connectionStatus) {
        DeviceConnectionStatus.Connected -> MeritColors.Tertiary
        DeviceConnectionStatus.Disconnected -> MeritColors.Secondary
        DeviceConnectionStatus.Paused -> MeritColors.Secondary
        DeviceConnectionStatus.Unpaired -> MeritColors.Error
    }
    val statusIcon = when (card.connectionStatus) {
        DeviceConnectionStatus.Connected -> Icons.Default.Wifi
        DeviceConnectionStatus.Disconnected -> Icons.Default.WifiOff
        DeviceConnectionStatus.Paused -> Icons.Default.Sync
        DeviceConnectionStatus.Unpaired -> Icons.Default.LinkOff
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MeritColors.SurfaceContainerLowest,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(MeritSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MeritSpacing.sm),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MeritColors.SecondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.TabletAndroid,
                            contentDescription = null,
                            tint = MeritColors.OnSecondaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.model?.ifBlank { null } ?: "Android device",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MeritColors.OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = card.lastSeenLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MeritColors.OnSurfaceVariant,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = statusColor.copy(alpha = 0.14f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = card.connectionLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = statusColor,
                        )
                    }
                }
            }

            Spacer(Modifier.height(MeritSpacing.md))

            // Metric grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DeviceMetricTile(
                        icon = Icons.Default.Info,
                        label = "OS",
                        value = device.osVersion?.ifBlank { null } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    DeviceMetricTile(
                        icon = Icons.Default.BatteryFull,
                        label = "Battery",
                        value = device.batteryPercent?.let { "$it%" } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DeviceMetricTile(
                        icon = Icons.Default.CheckCircle,
                        label = "App version",
                        value = device.appVersion?.ifBlank { null } ?: "Pending sync",
                        modifier = Modifier.weight(1f),
                    )
                    DeviceMetricTile(
                        icon = Icons.Default.Home,
                        label = "Launcher",
                        value = when (device.launcherDefault) {
                            true -> "Active"
                            false -> "Not set"
                            null -> "Unknown"
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                DeviceMetricTile(
                    icon = Icons.Default.Sync,
                    label = "Platform",
                    value = device.platform.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(MeritSpacing.sm))

            // IDs
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MeritColors.SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IdRow(label = "Device Identifier", value = device.deviceId)
                    IdRow(label = "Child Profile Identifier", value = childId)
                }
            }

            Spacer(Modifier.height(MeritSpacing.md))

            if (busy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MeritColors.Primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Updating device…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MeritColors.OnSurfaceVariant,
                    )
                }
            } else if (device.revoked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DeviceActionButton(
                        text = "Restore access",
                        icon = Icons.Default.Refresh,
                        filled = true,
                        onClick = onRestore,
                        modifier = Modifier.weight(1f),
                    )
                    DeviceActionButton(
                        text = "Pair again",
                        icon = Icons.Default.QrCode2,
                        filled = false,
                        onClick = onReconnectPair,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (card.connectionStatus == DeviceConnectionStatus.Disconnected) {
                        DeviceActionButton(
                            text = "Refresh sync status",
                            icon = Icons.Default.Sync,
                            filled = false,
                            onClick = onRefresh,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DeviceActionButton(
                            text = "Re-pair device",
                            icon = Icons.Default.QrCode2,
                            filled = false,
                            onClick = onReconnectPair,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    DeviceActionButton(
                        text = "Disconnect device",
                        icon = Icons.Default.LinkOff,
                        filled = true,
                        destructive = true,
                        onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceMetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MeritColors.SurfaceContainerLow,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MeritColors.Primary,
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MeritColors.OnSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MeritColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun IdRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MeritColors.OnSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = MeritMonoFontFamily,
                fontWeight = FontWeight.Medium,
            ),
            color = MeritColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun DeviceActionButton(
    text: String,
    icon: ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val bg = when {
        destructive && filled -> MeritColors.Error
        filled -> MeritColors.Primary
        else -> MeritColors.SurfaceContainer
    }
    val fg = when {
        destructive && filled -> MeritColors.OnError
        filled -> MeritColors.OnPrimary
        destructive -> MeritColors.Error
        else -> MeritColors.OnSurface
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = fg,
            )
        }
    }
}

internal fun formatRelativeTime(epochMs: Long?): String {
    if (epochMs == null) return "never"
    val deltaMs = (System.currentTimeMillis() - epochMs).coerceAtLeast(0)
    val minutes = deltaMs / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} hr ago"
        else -> "${minutes / (60 * 24)} d ago"
    }
}
