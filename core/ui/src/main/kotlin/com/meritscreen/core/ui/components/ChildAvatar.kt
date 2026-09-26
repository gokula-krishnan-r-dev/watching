package com.meritscreen.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meritscreen.core.common.domain.AvatarPreset
import com.meritscreen.core.ui.theme.MeritColors

/**
 * Renders the parent-selected [AvatarPreset] emoji for a child profile.
 * Use everywhere a child face/identity mark appears on the parent surfaces.
 */
@Composable
fun ChildAvatar(
    avatar: AvatarPreset,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = CircleShape,
    background: Color = MeritColors.SecondaryContainer,
    contentDescription: String? = avatar.label.ifBlank { avatar.name },
    emojiSize: TextUnit = (size.value * 0.55f).sp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background)
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatar.emoji,
            fontSize = emojiSize,
        )
    }
}

/** Squircle variant used on child detail banners. */
@Composable
fun ChildAvatarTile(
    avatar: AvatarPreset,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    background: Color = MeritColors.SecondaryContainer,
    contentDescription: String? = avatar.label.ifBlank { avatar.name },
) {
    ChildAvatar(
        avatar = avatar,
        modifier = modifier,
        size = size,
        shape = RoundedCornerShape(16.dp),
        background = background,
        contentDescription = contentDescription,
        emojiSize = (size.value * 0.5f).sp,
    )
}
