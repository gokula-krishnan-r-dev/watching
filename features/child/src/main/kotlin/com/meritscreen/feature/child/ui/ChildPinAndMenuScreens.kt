package com.meritscreen.feature.child.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.common.device.DefaultHomeChecker
import com.meritscreen.core.common.domain.SessionPhase
import com.meritscreen.core.common.result.Outcome
import com.meritscreen.core.security.pairing.ChildPairingStore
import com.meritscreen.core.ui.components.MeritPrimaryButton
import com.meritscreen.core.ui.components.MeritSecondaryButton
import com.meritscreen.core.ui.components.NumericKeypad
import com.meritscreen.core.ui.components.PinInputField
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritSpacing
import com.meritscreen.feature.child.data.ChildDeviceLifecycleCoordinator
import com.meritscreen.feature.child.data.ChildPolicyRepository
import com.meritscreen.feature.child.data.ChildPolicySyncCoordinator
import com.meritscreen.feature.child.domain.ChildSessionController
import com.meritscreen.feature.child.domain.UnpairChildDeviceUseCase
import com.meritscreen.feature.child.domain.VerifyParentPinUseCase
import com.meritscreen.feature.launcher.HomeRoleManager
import com.meritscreen.feature.launcher.LockTaskGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChildPinViewModel @Inject constructor(
    private val verifyParentPin: VerifyParentPinUseCase,
    private val sessionController: ChildSessionController,
) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun submit(pin: String, endFailLockOnUnlock: Boolean) {
        if (_busy.value || _unlocked.value) return
        viewModelScope.launch {
            _busy.value = true
            when (val result = verifyParentPin(pin)) {
                is Outcome.Success -> {
                    _error.value = null
                    if (endFailLockOnUnlock ||
                        sessionController.phase() == SessionPhase.Shielded
                    ) {
                        sessionController.endFailLock()
                    }
                    _unlocked.value = true
                }
                is Outcome.Failure -> _error.value = result.error.userMessage
            }
            _busy.value = false
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** Tiny, dependency-free relative-time label for the parent-menu sync hint. */
internal fun formatSyncAge(epochMs: Long): String {
    val minutes = (System.currentTimeMillis() - epochMs) / 60_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} hr ago"
        else -> "${minutes / (24 * 60)} d ago"
    }
}

@HiltViewModel
class ChildParentMenuViewModel @Inject constructor(
    private val pairingStore: ChildPairingStore,
    private val policyRepository: ChildPolicyRepository,
    private val syncCoordinator: ChildPolicySyncCoordinator,
    private val unpairChildDevice: UnpairChildDeviceUseCase,
    private val sessionController: ChildSessionController,
    private val lifecycleCoordinator: ChildDeviceLifecycleCoordinator,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _rulesSummary = MutableStateFlow("")
    val rulesSummary: StateFlow<String> = _rulesSummary.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _unpaired = MutableStateFlow(false)
    val unpaired: StateFlow<Boolean> = _unpaired.asStateFlow()
    private val _failLockActive = MutableStateFlow(false)
    val failLockActive: StateFlow<Boolean> = _failLockActive.asStateFlow()
    private val _failLockEnded = MutableStateFlow(false)
    val failLockEnded: StateFlow<Boolean> = _failLockEnded.asStateFlow()
    private val _isDefaultLauncher = MutableStateFlow(false)
    val isDefaultLauncher: StateFlow<Boolean> = _isDefaultLauncher.asStateFlow()
    private val _launcherMessage = MutableStateFlow<String?>(null)
    val launcherMessage: StateFlow<String?> = _launcherMessage.asStateFlow()

    init {
        viewModelScope.launch { refreshSummary() }
        viewModelScope.launch {
            sessionController.snapshot.collect { snap ->
                _failLockActive.value = snap.phase == SessionPhase.Shielded
            }
        }
        refreshLauncherStatus()
    }

    fun refreshLauncherStatus() {
        _isDefaultLauncher.value = DefaultHomeChecker.isDefaultHome(context)
    }

    fun refreshSummary() {
        viewModelScope.launch {
            val childId = pairingStore.get()?.childId ?: return@launch
            val rules = policyRepository.listAppRules(childId)
            val allowed = rules.count { it.allowed }
            val syncHint = syncCoordinator.lastSyncState()?.lastSuccessAtEpochMs?.let { formatSyncAge(it) }
            _rulesSummary.value = "$allowed allowed apps on this device" +
                (syncHint?.let { " · synced $it" } ?: " · not synced yet")
            _failLockActive.value = sessionController.phase() == SessionPhase.Shielded
            refreshLauncherStatus()
        }
    }

    fun refreshRules() {
        viewModelScope.launch {
            _busy.value = true
            syncCoordinator.refreshNow()
            refreshSummary()
            _busy.value = false
        }
    }

    fun endFailLockNow() {
        viewModelScope.launch {
            _busy.value = true
            sessionController.endFailLock()
            _failLockActive.value = false
            _failLockEnded.value = true
            _busy.value = false
        }
    }

    fun removeSetLauncherDefault(activity: Activity?) {
        activity?.let { LockTaskGuard.release(it) }
        runCatching {
            context.packageManager.clearPackagePreferredActivities(context.packageName)
        }
        val homeSettingsIntent = HomeRoleManager.homeSettingsIntent()?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = if (homeSettingsIntent != null && HomeRoleManager.canResolve(context, homeSettingsIntent)) {
            runCatching { context.startActivity(homeSettingsIntent) }.isSuccess
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val manageDefaults = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (manageDefaults.resolveActivity(context.packageManager) != null) {
                runCatching { context.startActivity(manageDefaults) }.isSuccess
            } else {
                runCatching { context.startActivity(HomeRoleManager.legacyHomeChooserIntent(context)) }.isSuccess
            }
        } else {
            runCatching { context.startActivity(HomeRoleManager.legacyHomeChooserIntent(context)) }.isSuccess
        }

        _launcherMessage.value = if (launched) {
            "Default launcher cleared. Please select your phone's standard home app."
        } else {
            "Default launcher preferences reset."
        }
        refreshLauncherStatus()
    }

    fun unpair(activity: Activity? = null) {
        viewModelScope.launch {
            _busy.value = true
            activity?.let { LockTaskGuard.release(it) }
            runCatching {
                context.packageManager.clearPackagePreferredActivities(context.packageName)
            }
            lifecycleCoordinator.stop()
            lifecycleCoordinator.markForceSignedOut()
            runCatching { unpairChildDevice() }
            _unpaired.value = true
            _busy.value = false
        }
    }

    fun unpairAndUninstall(activity: Activity?) {
        viewModelScope.launch {
            _busy.value = true
            activity?.let { LockTaskGuard.release(it) }
            runCatching {
                context.packageManager.clearPackagePreferredActivities(context.packageName)
            }
            lifecycleCoordinator.stop()
            lifecycleCoordinator.markForceSignedOut()
            runCatching { unpairChildDevice() }

            val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${context.packageName}")
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(uninstallIntent) }

            _unpaired.value = true
            _busy.value = false
        }
    }
}

@Composable
fun ChildPinScreen(
    endFailLockOnUnlock: Boolean,
    onUnlockedToMenu: () -> Unit,
    onUnlockedEndFailLock: () -> Unit,
    onBack: () -> Unit,
    viewModel: ChildPinViewModel = hiltViewModel(),
) {
    var pin by remember { mutableStateOf("") }
    val error by viewModel.error.collectAsStateWithLifecycle()
    val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(unlocked) {
        if (unlocked) {
            if (endFailLockOnUnlock) {
                onUnlockedEndFailLock()
            } else {
                onUnlockedToMenu()
            }
        }
    }

    LaunchedEffect(error) {
        if (error != null) {
            pin = ""
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    -18f at 50
                    18f at 100
                    -14f at 150
                    14f at 200
                    -8f at 250
                    8f at 300
                    -4f at 350
                    0f at 400
                },
            )
        }
    }

    Scaffold(
        containerColor = MeritColors.SurfaceContainerLow,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MeritColors.SurfaceContainerLowest)
                        .border(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MeritColors.OnSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MeritColors.Primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "PARENT AUTH",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 10.sp,
                        ),
                        color = MeritColors.Primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Security Lock Badge
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MeritColors.Primary.copy(alpha = 0.12f))
                    .border(1.dp, MeritColors.Primary.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MeritColors.Primary,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Parent PIN",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                ),
                color = MeritColors.OnSurface,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (endFailLockOnUnlock) {
                    "Enter your 4-digit Parent PIN to end the resting window and unlock apps."
                } else {
                    "Enter your 4-digit Parent PIN to open settings on this device."
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                color = MeritColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Modern Animated PIN Indicator Dots
            Row(
                modifier = Modifier
                    .offset { IntOffset(shakeOffset.value.toInt(), 0) },
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(AppConfig.PARENT_PIN_MAX_LENGTH) { index ->
                    val filled = index < pin.length
                    val isError = error != null
                    val dotScale by animateFloatAsState(
                        targetValue = if (filled) 1.15f else 1.0f,
                        label = "pin_dot_scale_$index",
                    )
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .scale(dotScale)
                            .clip(CircleShape)
                            .then(
                                if (filled) {
                                    Modifier.background(
                                        if (isError) MaterialTheme.colorScheme.error else MeritColors.Primary,
                                    )
                                } else {
                                    Modifier
                                        .border(
                                            width = 1.5.dp,
                                            color = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                            else MeritColors.OutlineVariant.copy(alpha = 0.7f),
                                            shape = CircleShape,
                                        )
                                        .background(MeritColors.SurfaceContainerLowest)
                                }
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Error Feedback Banner
            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier.padding(horizontal = 32.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // In-App Modern Numeric Keypad
            NumericKeypad(
                onDigitClick = { digit ->
                    if (pin.length < AppConfig.PARENT_PIN_MAX_LENGTH && !busy) {
                        val newPin = pin + digit
                        pin = newPin
                        if (newPin.length == AppConfig.PARENT_PIN_MAX_LENGTH) {
                            viewModel.submit(newPin, endFailLockOnUnlock)
                        }
                    }
                },
                onBackspaceClick = {
                    if (pin.isNotEmpty() && !busy) {
                        pin = pin.dropLast(1)
                    }
                },
                enabled = !busy,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button
            MeritPrimaryButton(
                text = if (endFailLockOnUnlock) "Unlock & end rest" else "Unlock",
                onClick = { viewModel.submit(pin, endFailLockOnUnlock) },
                loading = busy,
                enabled = pin.length == AppConfig.PARENT_PIN_MAX_LENGTH && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(48.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ChildParentMenuScreen(
    onBack: () -> Unit,
    onUnpaired: () -> Unit,
    onFailLockEnded: () -> Unit = onBack,
    onSwitchChild: () -> Unit = {},
    viewModel: ChildParentMenuViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    val summary by viewModel.rulesSummary.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val unpaired by viewModel.unpaired.collectAsStateWithLifecycle()
    val failLockActive by viewModel.failLockActive.collectAsStateWithLifecycle()
    val failLockEnded by viewModel.failLockEnded.collectAsStateWithLifecycle()
    val isDefaultLauncher by viewModel.isDefaultLauncher.collectAsStateWithLifecycle()
    val launcherMessage by viewModel.launcherMessage.collectAsStateWithLifecycle()

    var showUnpairConfirmDialog by remember { mutableStateOf(false) }
    var showUninstallConfirmDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLauncherStatus()
                viewModel.refreshSummary()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(unpaired) {
        if (unpaired) onUnpaired()
    }
    LaunchedEffect(failLockEnded) {
        if (failLockEnded) onFailLockEnded()
    }

    if (showUnpairConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUnpairConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(
                    text = "Unpair this device?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Text(
                    text = "This will disconnect this phone from your parent account, clear offline restrictions, and release the child kiosk lock. You can re-pair later from the parent app.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnpairConfirmDialog = false
                        viewModel.unpair(activity)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Unpair Device")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpairConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showUninstallConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUninstallConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(
                    text = "Uninstall MeritScreen?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Text(
                    text = "This will automatically unpair this phone, clear launcher defaults, wipe local learning and usage data, and trigger Android to completely uninstall the app.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUninstallConfirmDialog = false
                        viewModel.unpairAndUninstall(activity)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Unpair & Uninstall")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    ChildScreenScaffold(title = "Parent menu", onBack = onBack) {
        // 1. Sync & Rule Summary
        Text(summary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(MeritSpacing.xs))
        ChildHelperText(
            "Refresh downloads the latest allowlist and time limits from your parent account when online.",
        )
        Spacer(Modifier.height(MeritSpacing.sm))
        MeritPrimaryButton(
            text = "Refresh rules",
            onClick = viewModel::refreshRules,
            loading = busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(MeritSpacing.md))
        MeritSecondaryButton(
            text = "Switch child profile",
            onClick = onSwitchChild,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(MeritSpacing.xs))
        ChildHelperText(
            "Use Parent PIN (already entered) to pick another child on this shared device. Timers and rules switch with the profile.",
        )
        Spacer(Modifier.height(MeritSpacing.lg))

        // 2. Resting Window Active (if applicable)
        if (failLockActive) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Resting Window Active",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    ChildHelperText(
                        "A resting window is active. End it to let approved apps open again without waiting for the timer.",
                    )
                    MeritPrimaryButton(
                        text = "End rest window now",
                        onClick = viewModel::endFailLockNow,
                        loading = busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(MeritSpacing.lg))
        }

        // 3. Default Launcher / Home App Management
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MeritColors.SurfaceContainerLow,
            border = BorderStroke(1.dp, MeritColors.OutlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MeritColors.Primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = MeritColors.Primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = "Home Launcher",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MeritColors.OnSurface,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = if (isDefaultLauncher) MeritColors.Primary.copy(alpha = 0.12f)
                                else MeritColors.SecondaryContainer.copy(alpha = 0.7f),
                        border = BorderStroke(
                            1.dp,
                            if (isDefaultLauncher) MeritColors.Primary.copy(alpha = 0.3f)
                            else MeritColors.Secondary.copy(alpha = 0.3f)
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isDefaultLauncher) MeritColors.Tertiary else MeritColors.OnSurfaceVariant.copy(alpha = 0.4f)),
                            )
                            Text(
                                text = if (isDefaultLauncher) "Merit Launcher" else "Stock Launcher",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                ),
                                color = if (isDefaultLauncher) MeritColors.Primary else MeritColors.OnSurfaceVariant,
                            )
                        }
                    }
                }

                Text(
                    text = "When MeritScreen is set as your default launcher, home navigation is locked. Switch back to your phone's standard launcher to restore normal home navigation or before uninstalling.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = MeritColors.OnSurfaceVariant,
                )

                if (launcherMessage != null) {
                    Text(
                        text = launcherMessage.orEmpty(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MeritColors.Tertiary,
                            fontSize = 11.sp,
                        ),
                    )
                }

                MeritSecondaryButton(
                    text = if (isDefaultLauncher) "Remove Default Launcher" else "Change Default Home App",
                    onClick = { viewModel.removeSetLauncherDefault(activity) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(MeritSpacing.lg))

        // 4. Device Teardown: Unpair & Uninstall Section
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = "Device Teardown & Uninstall",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Text(
                    text = "Unpairing clears this phone's link from your parent account. Uninstalling automatically unpairs the phone, releases launcher locks, and prompts Android to remove the app.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = MeritColors.OnSurfaceVariant,
                )

                MeritSecondaryButton(
                    text = "Unpair this device",
                    onClick = { showUnpairConfirmDialog = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = { showUninstallConfirmDialog = true },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Uninstall MeritScreen",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onError,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(MeritSpacing.xl))
    }
}

@Composable
fun ChildNotAllowedScreen(onBack: () -> Unit) {
    ChildScreenScaffold(title = "Ask a parent", onBack = onBack) {
        ChildHelperText("That app isn’t on your allowed list yet.")
        MeritPrimaryButton(text = "OK", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}
