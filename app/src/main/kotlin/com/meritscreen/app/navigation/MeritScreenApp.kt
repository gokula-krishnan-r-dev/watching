package com.meritscreen.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meritscreen.app.AppViewModel
import com.meritscreen.core.ui.components.LoadingState

@Composable
fun MeritScreenApp(
    viewModel: AppViewModel = hiltViewModel(),
) {
    val role by viewModel.role.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize()) {
        when (val current = role) {
            null -> LoadingState(message = "Starting Watching")
            else -> RoleGate(
                role = current,
                googleIdTokenClient = viewModel.googleIdTokenClient,
                onSelectRole = viewModel::selectRole,
                onSignOutParent = viewModel::signOutParent,
                onResetRole = viewModel::resetRole,
            )
        }
    }
}
