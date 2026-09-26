package com.meritscreen.feature.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.core.ui.components.MeritPrimaryButton
import com.meritscreen.core.ui.components.MeritSecondaryButton
import com.meritscreen.core.ui.theme.MeritSpacing

/**
 * C02 — Launcher setup. Asks the child device to grant MeritScreen the Android Home role
 * (or Home-app settings / chooser fallback) so restricted apps have nowhere to "escape" to.
 * Skippable: quiz/cooldown gating does not depend on being Home, but allowlist + fail-lock
 * are strongest when MeritScreen is Home.
 *
 * RoleManager intents **must** use the Activity Result API (`startActivityForResult`);
 * plain `startActivity` is a silent no-op on many API 29+ images including emulators.
 */
@Composable
fun LauncherSetupScreen(
    onDone: () -> Unit,
    viewModel: LauncherSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val homeSetupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onSetupResult(result.resultCode)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun launchHomeSetup() {
        viewModel.clearMessage()
        val intent = viewModel.setupIntent()
        try {
            // RoleManager.createRequestRoleIntent requires for-result; Settings / chooser
            // also work through the same launcher.
            homeSetupLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            val fallback = HomeRoleManager.homeSettingsIntent()
                ?: HomeRoleManager.legacyHomeChooserIntent(context)
            try {
                // Settings path from an Activity context.
                if (context is Activity) {
                    homeSetupLauncher.launch(fallback)
                } else {
                    context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } catch (_: ActivityNotFoundException) {
                viewModel.onSetupFailed()
            }
        } catch (_: SecurityException) {
            viewModel.onSetupFailed("Android blocked the Home-app request. Open Settings and set MeritScreen as Home manually.")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .displayCutoutPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(MeritSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (state.isDefaultHome) Icons.Filled.CheckCircle else Icons.Filled.Home,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(MeritSpacing.md))
        Text(
            text = if (state.isDefaultHome) "MeritScreen is your Home app" else "Make MeritScreen your Home app",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(MeritSpacing.sm))
        Text(
            text = if (state.isDefaultHome) {
                "Nice — tapping the Home/Recents button now brings the child back here instead of the phone's regular Home screen."
            } else {
                "When MeritScreen is Home, pressing the phone's Home button always returns here — so approved apps and screen-time stay front and center."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(MeritSpacing.lg))
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(MeritSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Not device-enforced: this uses Android's standard Home-app choice, " +
                        "the same setting used to switch launchers. Nothing is hidden or forced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.userMessage?.let { message ->
            Spacer(Modifier.height(MeritSpacing.md))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(MeritSpacing.lg))
        if (!state.isDefaultHome) {
            MeritPrimaryButton(
                text = "Set as Home app",
                onClick = { launchHomeSetup() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MeritSpacing.sm))
        }
        MeritSecondaryButton(
            text = if (state.isDefaultHome) "Done" else "I'll do this later",
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
