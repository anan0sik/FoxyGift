package com.foxygift.pos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/** Current active language — mutable so Settings can hot-swap it without restart. */
val LocalAppLanguage = compositionLocalOf { AppLanguage.EN }

@Composable
fun FoxyGiftTheme(
    darkTheme:   Boolean     = true,          // POS terminals default to dark mode
    appLanguage: AppLanguage = AppLanguage.EN,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) FoxyDarkColorScheme else FoxyLightColorScheme
    val strings     = appLanguage.strings()

    CompositionLocalProvider(
        LocalStrings     provides strings,
        LocalAppLanguage provides appLanguage,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = FoxyTypography,
            content     = content,
        )
    }
}
