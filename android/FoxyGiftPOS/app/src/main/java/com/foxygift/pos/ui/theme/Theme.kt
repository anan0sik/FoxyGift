package com.foxygift.pos.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val FoxyDarkColorScheme = darkColorScheme(
    primary             = FoxyAmber500,
    onPrimary           = FoxyOnPrimary,
    primaryContainer    = FoxyAmber900,
    onPrimaryContainer  = FoxyAmber100,
    secondary           = FoxyAmber300,
    onSecondary         = Graphite900,
    secondaryContainer  = Graphite800,
    onSecondaryContainer= FoxyAmber200,
    tertiary            = FoxyPurple,
    background          = Graphite950,
    onBackground        = Graphite100,
    surface             = Graphite900,
    onSurface           = Graphite100,
    surfaceVariant      = Graphite800,
    onSurfaceVariant    = Graphite400,
    outline             = Graphite700,
    error               = FoxyError,
    onError             = Color.White,
    errorContainer      = Color(0xFF7F1D1D),
    onErrorContainer    = Color(0xFFFEE2E2),
)

val FoxyLightColorScheme = lightColorScheme(
    primary             = FoxyAmber600,
    onPrimary           = Color.White,
    primaryContainer    = FoxyAmber100,
    onPrimaryContainer  = FoxyAmber900,
    secondary           = Graphite700,
    onSecondary         = Color.White,
    background          = Graphite50,
    onBackground        = Graphite900,
    surface             = Color.White,
    onSurface           = Graphite900,
    surfaceVariant      = Graphite100,
    onSurfaceVariant    = Graphite600,
    outline             = Graphite300,
    error               = FoxyError,
)
