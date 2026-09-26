package com.meritscreen.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.meritscreen.core.ui.theme.MeritSpacing

/**
 * A numeric PIN entry field rendered as a row of masked digit slots, reused anywhere the
 * product asks for a 4-digit Parent PIN (setup, verification, and the on-device
 * child PIN gate). Never logs or echoes the raw value.
 */
@Composable
fun PinInputField(
    value: String,
    onValueChange: (String) -> Unit,
    maxLength: Int,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    masked: Boolean = true,
    contentDescriptionLabel: String = "PIN",
) {
    val digitsOnly = value.filter(Char::isDigit).take(maxLength)
    BasicTextField(
        value = TextFieldValue(digitsOnly, selection = TextRange(digitsOnly.length)),
        onValueChange = { new ->
            val digits = new.text.filter(Char::isDigit).take(maxLength)
            if (digits != value) onValueChange(digits)
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (masked) KeyboardType.NumberPassword else KeyboardType.Number,
        ),
        visualTransformation = if (masked) PasswordVisualTransformation(mask = ' ') else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = modifier.semantics { contentDescription = contentDescriptionLabel },
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(MeritSpacing.xs)) {
                repeat(maxLength) { index ->
                    PinDigitSlot(
                        symbol = digitsOnly.getOrNull(index)?.toString(),
                        masked = masked,
                        isError = isError,
                    )
                }
            }
        },
    )
}

@Composable
private fun PinDigitSlot(symbol: String?, masked: Boolean, isError: Boolean) {
    val filled = symbol != null
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        filled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .testTag("pin_digit_slot")
            .border(width = 1.5.dp, color = borderColor, shape = RoundedCornerShape(MeritSpacing.xs))
            .background(
                color = if (filled) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(MeritSpacing.xs),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (filled) {
            Text(
                text = if (masked) "•" else symbol.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}
