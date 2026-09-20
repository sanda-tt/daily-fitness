package com.example.gym.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = AppPink,
    onPrimary = Color.White,
    primaryContainer = AppPinkSoft,
    onPrimaryContainer = AppPinkDeep,
    background = AppBgPink,
    onBackground = AppTextDark,
    surface = AppCardWhite,
    onSurface = AppTextDark,
    surfaceVariant = Color(0xFFFDF6F8),
    onSurfaceVariant = AppTextGray,
    outline = AppDivider
)

@Composable
fun GymTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // 应用固定使用浅色品牌风格
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}
