package app.widapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.widapp.R

private fun variable(res: Int, vararg weights: Int) = FontFamily(
    weights.map { w ->
        Font(res, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

/** Unbounded — wide display face for screen titles. */
val Display = variable(R.font.unbounded, 400, 500, 600, 700)
/** Onest — humanist grotesque with excellent Cyrillic, for everything you read. */
val Body = variable(R.font.onest, 300, 400, 500, 600, 700)
/** JetBrains Mono — sizes, model ids, code. */
val Mono = variable(R.font.jetbrains_mono, 400, 500, 600)

private val base = Typography()

val WidTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.W600, fontSize = 54.sp, lineHeight = 58.sp, letterSpacing = (-0.03).em),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.W600, fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-0.03).em),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.W600, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.025).em),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.W600, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.W500, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.015).em),
    headlineSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.W600, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.W600, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.W600, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.W600, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.W600, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.W500, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.W500, fontSize = 11.sp, lineHeight = 16.sp),
)

/** Small technical caption: "4×2", model ids, version numbers. */
val MonoLabel = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W500, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.02.em)
val MonoBody = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W400, fontSize = 13.sp, lineHeight = 20.sp)
@Suppress("unused") private val keep = base
