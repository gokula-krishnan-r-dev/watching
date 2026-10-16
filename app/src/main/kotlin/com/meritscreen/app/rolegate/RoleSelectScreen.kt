package com.meritscreen.app.rolegate

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
        Image(
            painter = painterResource(id = com.meritscreen.core.ui.R.drawable.ic_watching_logo),
            contentDescription = "Watching Logo",
            modifier = Modifier
                .size(84.dp)
                .padding(bottom = MeritSpacing.sm),
        )
        Text(
            text = "Watching",
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(MeritSpacing.sm))
        Text(
            text = "Turn screen time battles into curious minds.",
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
