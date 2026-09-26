package com.meritscreen.app.rolegate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.meritscreen.core.common.session.DeviceRole
import com.meritscreen.core.ui.components.MeritPrimaryButton
import com.meritscreen.core.ui.theme.MeritSpacing
import kotlinx.serialization.Serializable

@Serializable
data object RoleSelectRoute

@Composable
fun RoleSelectScreen(
    onSelectRole: (DeviceRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MeritSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "MeritScreen",
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(MeritSpacing.sm))
        Text(
            text = "A calm home for kids, and a simple dashboard for parents.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(MeritSpacing.xl))
        MeritPrimaryButton(
            text = "I am a parent",
            onClick = { onSelectRole(DeviceRole.Parent) },
        )
        Spacer(Modifier.height(MeritSpacing.md))
        MeritPrimaryButton(
            text = "Set up this child device",
            onClick = { onSelectRole(DeviceRole.Child) },
        )
    }
}
