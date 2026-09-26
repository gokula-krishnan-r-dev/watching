package com.meritscreen.core.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.autofill.AutofillManager
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.meritscreen.core.common.auth.OtpCodeExtractor
import com.meritscreen.core.common.config.AppConfig
import com.meritscreen.core.ui.theme.MeritColors
import com.meritscreen.core.ui.theme.MeritPinDigitStyle

/**
 * Segmented OTP field backed by a single autofill-aware [BasicTextField].
 *
 * Autofill uses [ContentType.SmsOtpCode] (Android's OTP hint — also used for email OTPs
 * from Gmail / Google Autofill). Clipboard paste/copy from the mail client is detected
 * only while this composable is resumed; no notification-listener permission is required.
 */
@Composable
fun NativeOtpInputField(
    value: String,
    onValueChange: (String) -> Unit,
    length: Int = AppConfig.EMAIL_OTP_LENGTH,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    autoFocus: Boolean = true,
    onComplete: ((String) -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val context = LocalContext.current

    LaunchedEffect(autoFocus, enabled) {
        if (autoFocus && enabled) {
            focusRequester.requestFocus()
            keyboardController?.show()
            requestOtpAutofill(context, view)
        }
    }

    OtpClipboardAutofillEffect(
        enabled = enabled && value.length < length,
        length = length,
        currentValue = value,
        onCodeDetected = { code ->
            onValueChange(code)
            if (code.length == length) {
                onComplete?.invoke(code)
            }
        },
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                focusRequester.requestFocus()
                keyboardController?.show()
                requestOtpAutofill(context, view)
            },
        contentAlignment = Alignment.Center,
    ) {
        OtpInputView(
            otp = value,
            length = length,
            isError = isError,
        )

        // Full-size transparent field so Autofill / IME / paste target the real input
        // (a 1dp field is often skipped by Autofill services).
        BasicTextField(
            value = value,
            onValueChange = { newValue ->
                val filtered = newValue.filter { it.isDigit() }.take(length)
                if (filtered != value) {
                    onValueChange(filtered)
                    if (filtered.length == length) {
                        onComplete?.invoke(filtered)
                    }
                }
            },
            enabled = enabled,
            singleLine = true,
            cursorBrush = SolidColor(MeritColors.Primary.copy(alpha = 0f)),
            textStyle = TextStyle(
                color = MeritColors.OnSurface.copy(alpha = 0f),
                fontSize = 1.sp,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { keyboardController?.hide() },
            ),
            modifier = Modifier
                .matchParentSize()
                .alpha(0.01f)
                .focusRequester(focusRequester)
                .semantics {
                    contentType = ContentType.SmsOtpCode
                },
        )
    }
}

@Composable
private fun OtpClipboardAutofillEffect(
    enabled: Boolean,
    length: Int,
    currentValue: String,
    onCodeDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestValue by rememberUpdatedState(currentValue)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnCodeDetected by rememberUpdatedState(onCodeDetected)
    var lastApplied by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner, length) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
            ?: return@DisposableEffect onDispose { }

        fun tryConsumeClipboard() {
            if (!latestEnabled) return
            val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return
            if (clip.itemCount <= 0) return
            val raw = runCatching { clip.getItemAt(0).coerceToText(context)?.toString() }
                .getOrNull()
            val code = OtpCodeExtractor.extract(raw, length) ?: return
            if (code == latestValue) return
            // Allow the same code again after clear/resend (empty field).
            if (code == lastApplied && latestValue.isNotEmpty()) return
            lastApplied = code
            latestOnCodeDetected(code)
        }

        val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
            tryConsumeClipboard()
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                tryConsumeClipboard()
            }
        }

        clipboard.addPrimaryClipChangedListener(clipListener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        tryConsumeClipboard()

        onDispose {
            clipboard.removePrimaryClipChangedListener(clipListener)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }
}

private fun requestOtpAutofill(context: Context, view: View) {
    val autofill = context.getSystemService(AutofillManager::class.java) ?: return
    if (!autofill.isEnabled) return
    runCatching { autofill.requestAutofill(view) }
}

@Composable
fun OtpInputView(
    otp: String,
    length: Int = AppConfig.EMAIL_OTP_LENGTH,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val activeIndex = otp.length.coerceAtMost(length - 1)
        val isComplete = otp.length >= length

        for (i in 0 until length) {
            val isFilled = i < otp.length
            val isActive = i == activeIndex && !isComplete
            val char = if (isFilled) otp[i] else null

            OtpDigitCell(
                char = char,
                isActive = isActive,
                isError = isError,
            )
        }
    }
}

@Composable
private fun OtpDigitCell(
    char: Char?,
    isActive: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
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

    val shape = RoundedCornerShape(16.dp)

    val backgroundColor = when {
        isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        isActive -> MaterialTheme.colorScheme.primaryFixed.copy(alpha = 0.35f)
        char != null -> MaterialTheme.colorScheme.surfaceContainerLowest
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        else -> null
    }

    val cellModifier = modifier
        .width(48.dp)
        .height(56.dp)
        .let {
            if (borderColor != null) it.border(1.5.dp, borderColor, shape) else it
        }

    Surface(
        modifier = cellModifier,
        shape = shape,
        color = backgroundColor,
        shadowElevation = if (char != null && !isActive && !isError) 1.dp else 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                char != null -> {
                    Text(
                        text = char.toString(),
                        style = MeritPinDigitStyle,
                        color = MeritColors.OnSurface,
                    )
                }
                isActive -> {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(28.dp)
                            .alpha(cursorAlpha)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
        }
    }
}
