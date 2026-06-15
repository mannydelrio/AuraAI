/*
 * Aura — Portal color palette (Meta Horizon design guidelines).
 * No pure white (#FFFFFF) or pure black (#000000).
 */
package com.aura.assistant.ui.theme

import androidx.compose.ui.graphics.Color

// Meta Horizon primary blue — used for buttons and primary actions
val MetaBlue = Color(0xFF0866FF)
val MetaBlueLight = Color(0xFFD4E3FF)
val MetaBlueDarkCont = Color(0xFF004CB0)
val OnMetaBlue = Color(0xFFF0F0F0)
val OnMetaBlueLight = Color(0xFF001A41)
val OnMetaBlueDarkC = Color(0xFFD4E3FF)

// Backgrounds — Portal dark surfaces
val BackgroundDark = Color(0xFF1A1A1A)
val SurfaceDark = Color(0xFF2B2B2B)
val ContentOnDark = Color(0xFFDADADA)
val NeutralGreyDark = Color(0xFFBEC6DC)

// Light fallbacks (Portal is dark-first, but keep a valid light scheme)
val BackgroundLight = Color(0xFFF0F0F0)
val SurfaceLight = Color(0xFFE6E6E6)
val ContentOnLight = Color(0xFF1A1A1A)
val NeutralGrey = Color(0xFF565F71)
