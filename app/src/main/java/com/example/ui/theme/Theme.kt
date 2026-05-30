package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ActiveSunset,
    onPrimary = Color.White,
    secondary = CorrectMatchGreen,
    onSecondary = Color.White,
    tertiary = ActiveSunsetLight,
    onTertiary = Color.White,
    background = SlateDarkBackground,
    onBackground = TextPremiumOffWhite,
    surface = SlateCardSurface,
    onSurface = TextPremiumOffWhite,
    surfaceVariant = SlateInputActive,
    onSurfaceVariant = TextSubtitleGray,
    outline = InkPaperBorder,
    error = WrongRoseRed
)

// We want our app to follow the premium dark theme consistently as standard
private val LightColorScheme = DarkColorScheme

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for cohesive "言叶之庭" styling
    dynamicColor: Boolean = false, // Preserve our custom handcrafted branding branding colors
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
