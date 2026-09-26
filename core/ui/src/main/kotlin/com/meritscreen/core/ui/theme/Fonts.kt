package com.meritscreen.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.meritscreen.core.ui.R

/**
 * Calm Horizon / Stitch typography families.
 *
 * Fonts are **bundled** under `res/font` as **latin subsets** (~340 KB total) so
 * parent and child UIs load instantly offline with no Google Fonts network
 * round-trip — critical for the child launcher path.
 *
 * - [MeritFontFamily] — Inter 400/500/600/700 (OFL, rsms/inter via fontsource)
 * - [MeritMonoFontFamily] — Roboto Mono 400/500/600 (Apache 2.0, via fontsource)
 *
 * Do not add runtime Google Fonts downloads or full-charset TTFs here.
 */
object MeritFonts {
    val Inter: FontFamily = FontFamily(
        Font(R.font.inter_regular, weight = FontWeight.Normal),
        Font(R.font.inter_medium, weight = FontWeight.Medium),
        Font(R.font.inter_semibold, weight = FontWeight.SemiBold),
        Font(R.font.inter_bold, weight = FontWeight.Bold),
    )

    val RobotoMono: FontFamily = FontFamily(
        Font(R.font.roboto_mono_regular, weight = FontWeight.Normal),
        Font(R.font.roboto_mono_medium, weight = FontWeight.Medium),
        Font(R.font.roboto_mono_semibold, weight = FontWeight.SemiBold),
    )
}

/** Primary UI typeface — Inter. Prefer this over [FontFamily.SansSerif]. */
val MeritFontFamily: FontFamily get() = MeritFonts.Inter

/** Tabular / security codes — Roboto Mono. */
val MeritMonoFontFamily: FontFamily get() = MeritFonts.RobotoMono

/**
 * Remembered families for Compose call sites that need a stable [FontFamily]
 * reference across recompositions (e.g. custom Text styles outside MaterialTheme).
 */
@Composable
fun rememberMeritFontFamily(): FontFamily = remember { MeritFonts.Inter }

@Composable
fun rememberMeritMonoFontFamily(): FontFamily = remember { MeritFonts.RobotoMono }
