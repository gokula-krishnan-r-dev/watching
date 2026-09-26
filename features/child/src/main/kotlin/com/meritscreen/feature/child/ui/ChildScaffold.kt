package com.meritscreen.feature.child.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.meritscreen.core.ui.theme.MeritSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    onParentLock: (() -> Unit)? = null,
    /** When false, the body Column does not scroll — use for Lazy grids (Home). */
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (onParentLock != null) {
                        IconButton(onClick = onParentLock) {
                            Icon(Icons.Outlined.Lock, contentDescription = "Parent settings")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val bodyModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = MeritSpacing.lg, vertical = MeritSpacing.md)
        Column(
            modifier = bodyModifier,
            content = content,
        )
    }
}

@Composable
fun ChildHelperText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(MeritSpacing.sm))
}
