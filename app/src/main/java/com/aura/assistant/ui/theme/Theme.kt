/*
 * Aura — Material 3 dark-first theme for Portal. Dynamic color disabled per guidelines.
 */
package com.aura.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
    darkColorScheme(
        primary = MetaBlue,
        onPrimary = OnMetaBlue,
        primaryContainer = MetaBlueDarkCont,
        onPrimaryContainer = OnMetaBlueDarkC,
        secondary = NeutralGreyDark,
        onSecondary = OnMetaBlue,
        background = BackgroundDark,
        surface = SurfaceDark,
        onBackground = ContentOnDark,
        onSurface = ContentOnDark,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = MetaBlue,
        onPrimary = OnMetaBlue,
        primaryContainer = MetaBlueLight,
        onPrimaryContainer = OnMetaBlueLight,
        secondary = NeutralGrey,
        onSecondary = OnMetaBlue,
        background = BackgroundLight,
        surface = SurfaceLight,
        onBackground = ContentOnLight,
        onSurface = ContentOnLight,
    )

@Composable
fun AuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
  // Portal is dark-first; default to dark even on light system unless explicitly overridden.
  val colorScheme = if (darkTheme) DarkColorScheme else DarkColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
