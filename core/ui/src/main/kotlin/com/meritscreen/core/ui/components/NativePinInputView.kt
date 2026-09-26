package com.meritscreen.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritPinDigitStyle

/**
 * Standard Android native numeric soft keyboard powered PIN input view.
 * Renders high-legibility tactile slots while delegating all key events to the
 * system IME keyboard rather than custom on-screen grids.
 */
@Composable
fun NativePinInputField(
    pin: String,
    onPinChange: (String) -> Unit,
    length: Int = 4,
    modifier: Modifier = Modifier,
    isMasked: Boolean = true,
    isError: Boolean = false,
    enabled: Boolean = true,
    autoFocus: Boolean = true,
    onComplete: ((String) -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "cursor_transition")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor_alpha",
    )

    BasicTextField(
        value = pin,
        onValueChange = { newValue ->
            val filtered = newValue.filter { it.isDigit() }.take(length)
            onPinChange(filtered)
            if (filtered.length == length) {
                onComplete?.invoke(filtered)
            }
        },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isMasked) KeyboardType.NumberPassword else KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = { keyboardController?.hide() },
        ),
        // Keep the real editable text invisible — only PinSlot draws digits.
        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
        cursorBrush = SolidColor(Color.Transparent),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                focusRequester.requestFocus()
                keyboardController?.show()
            },
        decorationBox = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val activeIndex = pin.length.coerceAtMost(length - 1)
                val isComplete = pin.length >= length

                for (i in 0 until length) {
                    val isFilled = i < pin.length
                    val isActive = i == activeIndex && !isComplete
                    val char = if (isFilled) pin[i] else null

                    PinSlot(
                        char = char,
                        isMasked = isMasked,
                        isActive = isActive,
                        isError = isError,
                        cursorAlpha = cursorAlpha,
                    )
                }
            }
        },
    )
}

@Composable
private fun PinSlot(
    char: Char?,
    isMasked: Boolean,
    isActive: Boolean,
    isError: Boolean,
    cursorAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)

    // Opaque fills only — translucent Surface + elevation was painting a white
    // rectangle behind each digit and looked misaligned.
    val backgroundColor = when {
        isError -> MeritColors.ErrorContainer
        isActive || char != null -> MeritColors.PrimaryFixed
        else -> MeritColors.SurfaceContainer
    }

    val borderColor = when {
        isError -> MeritColors.Error
        isActive -> MeritColors.Primary
        char != null -> MeritColors.Primary.copy(alpha = 0.35f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .width(56.dp)
            .height(64.dp)
            .clip(shape)
            .background(backgroundColor, shape)
            .border(1.5.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            char != null -> {
                if (isMasked) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(MeritColors.Primary),
                    )
                } else {
                    Text(
                        text = char.toString(),
                        // No letterSpacing on a single glyph — it shifted digits off-center.
                        style = MeritPinDigitStyle.copy(letterSpacing = 0.sp),
                        color = MeritColors.OnPrimaryFixed,
                    )
                }
            }
            isActive -> {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(28.dp)
                        .alpha(cursorAlpha)
                        .clip(CircleShape)
                        .background(MeritColors.Primary),
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MeritColors.SurfaceContainerHighest),
                )
            }
        }
    }
}
