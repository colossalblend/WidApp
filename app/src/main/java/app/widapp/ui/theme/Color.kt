package app.widapp.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// WidApp palette: warm paper + ink, one vermilion accent, olive neutrals for selection.
// Hand-tuned rather than generated so the app keeps a recognisable, print-like character.

object Ink {
    val Paper = Color(0xFFF4F1EA)
    val PaperDeep = Color(0xFFEAE5DA)
    val Ink = Color(0xFF1C1B18)
    val InkSoft = Color(0xFF5F5B52)
    val Vermilion = Color(0xFFD9481F)
    val VermilionLight = Color(0xFFFF8F6B)
    val Olive = Color(0xFF5C604A)
    val Night = Color(0xFF121110)
}

val LightBrand = lightColorScheme(
    primary = Color(0xFF1C1B18),
    onPrimary = Color(0xFFF4F1EA),
    primaryContainer = Color(0xFF2E2C28),
    onPrimaryContainer = Color(0xFFF4F1EA),
    inversePrimary = Color(0xFFECE7DD),
    secondary = Color(0xFF5C604A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E0C8),
    onSecondaryContainer = Color(0xFF1C1E10),
    tertiary = Color(0xFFD9481F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCE),
    onTertiaryContainer = Color(0xFF3B0A00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4F1EA),
    onBackground = Color(0xFF1C1B18),
    surface = Color(0xFFF4F1EA),
    onSurface = Color(0xFF1C1B18),
    surfaceVariant = Color(0xFFE4DFD3),
    onSurfaceVariant = Color(0xFF5F5B52),
    surfaceTint = Color(0xFF1C1B18),
    inverseSurface = Color(0xFF31302C),
    inverseOnSurface = Color(0xFFF4F1EA),
    outline = Color(0xFF8B867A),
    outlineVariant = Color(0xFFD3CDC0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBF9F4),
    surfaceDim = Color(0xFFDDD8CD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFEBE3),
    surfaceContainer = Color(0xFFEAE5DA),
    surfaceContainerHigh = Color(0xFFE4DFD3),
    surfaceContainerHighest = Color(0xFFDDD8CB),
)

val DarkBrand = darkColorScheme(
    primary = Color(0xFFEDE8DE),
    onPrimary = Color(0xFF1C1B18),
    primaryContainer = Color(0xFFD9D3C7),
    onPrimaryContainer = Color(0xFF1C1B18),
    inversePrimary = Color(0xFF1C1B18),
    secondary = Color(0xFFC5C4AC),
    onSecondary = Color(0xFF2E3120),
    secondaryContainer = Color(0xFF3A3D2B),
    onSecondaryContainer = Color(0xFFE1E0C8),
    tertiary = Color(0xFFFF8F6B),
    onTertiary = Color(0xFF3B0A00),
    tertiaryContainer = Color(0xFF7C2B10),
    onTertiaryContainer = Color(0xFFFFDBCE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121110),
    onBackground = Color(0xFFEDE8DE),
    surface = Color(0xFF121110),
    onSurface = Color(0xFFEDE8DE),
    surfaceVariant = Color(0xFF3B3934),
    onSurfaceVariant = Color(0xFFB8B2A6),
    surfaceTint = Color(0xFFEDE8DE),
    inverseSurface = Color(0xFFEDE8DE),
    inverseOnSurface = Color(0xFF31302C),
    outline = Color(0xFF878276),
    outlineVariant = Color(0xFF3D3B36),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF393733),
    surfaceDim = Color(0xFF121110),
    surfaceContainerLowest = Color(0xFF0C0C0B),
    surfaceContainerLow = Color(0xFF1A1917),
    surfaceContainer = Color(0xFF1F1E1B),
    surfaceContainerHigh = Color(0xFF2A2825),
    surfaceContainerHighest = Color(0xFF35332F),
)
