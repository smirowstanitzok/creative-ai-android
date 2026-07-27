package com.creativeai.app.ui.theme

import androidx.compose.ui.graphics.Color

// Markenfarben – Violett/Indigo mit Teal-Akzent, bewusst modern und ruhig.
val LightColors = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF5B3BE0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7DEFF),
    onPrimaryContainer = Color(0xFF1C0066),
    secondary = Color(0xFF00A38A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB8F5E8),
    onSecondaryContainer = Color(0xFF00201A),
    tertiary = Color(0xFFB4471F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF3A0B00),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE5E0EC),
    onSurfaceVariant = Color(0xFF48454F),
    outline = Color(0xFF7A7686),
    outlineVariant = Color(0xFFCAC5D0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

val DarkColors = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFFCBBEFF),
    onPrimary = Color(0xFF2E1178),
    primaryContainer = Color(0xFF452A9A),
    onPrimaryContainer = Color(0xFFE7DEFF),
    secondary = Color(0xFF63D9C1),
    onSecondary = Color(0xFF00382E),
    secondaryContainer = Color(0xFF005143),
    onSecondaryContainer = Color(0xFFB8F5E8),
    tertiary = Color(0xFFFFB59B),
    onTertiary = Color(0xFF5F1600),
    tertiaryContainer = Color(0xFF872F0B),
    onTertiaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF48454F),
    onSurfaceVariant = Color(0xFFC9C5D0),
    outline = Color(0xFF938F9A),
    outlineVariant = Color(0xFF48454F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// Statusfarbe „läuft“ – bewusst außerhalb des Schemas, für den Running-Badge.
val RunningGreenLight = Color(0xFF1B8A4B)
val RunningGreenDark = Color(0xFF7FE0A0)
