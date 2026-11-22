package com.sakuguard.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class SakuExtendedColors(
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val textSecondary: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
)

val LocalSakuColors = staticCompositionLocalOf {
    SakuExtendedColors(
        income = SakuIncomeLight,
        incomeContainer = SakuIncomeContainerLight,
        expense = SakuExpenseLight,
        expenseContainer = SakuExpenseContainerLight,
        warning = SakuWarningLight,
        warningContainer = SakuWarningContainerLight,
        textSecondary = SakuTextSecondaryLight,
        heroStart = SakuHeroStartLight,
        heroEnd = SakuHeroEndLight,
        onHero = SakuOnHeroLight,
    )
}

private val LightColorScheme = lightColorScheme(
    primary = SakuPrimaryLight,
    onPrimary = SakuOnPrimaryLight,
    primaryContainer = SakuPrimaryContainerLight,
    onPrimaryContainer = SakuOnPrimaryContainerLight,
    secondary = SakuSecondaryLight,
    onSecondary = SakuOnSecondaryLight,
    secondaryContainer = SakuSecondaryContainerLight,
    onSecondaryContainer = SakuOnSecondaryContainerLight,
    background = SakuBackgroundLight,
    onBackground = SakuTextPrimaryLight,
    surface = SakuSurfaceLight,
    onSurface = SakuTextPrimaryLight,
    surfaceVariant = SakuSurfaceVariantLight,
    onSurfaceVariant = SakuTextSecondaryLight,
    outline = SakuOutlineLight,
    error = SakuErrorLight,
    onError = SakuOnErrorLight,
    errorContainer = SakuErrorContainerLight,
    onErrorContainer = SakuOnErrorContainerLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = SakuPrimaryDark,
    onPrimary = SakuOnPrimaryDark,
    primaryContainer = SakuPrimaryContainerDark,
    onPrimaryContainer = SakuOnPrimaryContainerDark,
    secondary = SakuSecondaryDark,
    onSecondary = SakuOnSecondaryDark,
    secondaryContainer = SakuSecondaryContainerDark,
    onSecondaryContainer = SakuOnSecondaryContainerDark,
    background = SakuBackgroundDark,
    onBackground = SakuTextPrimaryDark,
    surface = SakuSurfaceDark,
    onSurface = SakuTextPrimaryDark,
    surfaceVariant = SakuSurfaceVariantDark,
    onSurfaceVariant = SakuTextSecondaryDark,
    outline = SakuOutlineDark,
    error = SakuErrorDark,
    onError = SakuOnErrorDark,
    errorContainer = SakuErrorContainerDark,
    onErrorContainer = SakuOnErrorContainerDark,
)

@Composable
fun SakuGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        SakuExtendedColors(
            income = SakuIncomeDark,
            incomeContainer = SakuIncomeContainerDark,
            expense = SakuExpenseDark,
            expenseContainer = SakuExpenseContainerDark,
            warning = SakuWarningDark,
            warningContainer = SakuWarningContainerDark,
            textSecondary = SakuTextSecondaryDark,
            heroStart = SakuHeroStartDark,
            heroEnd = SakuHeroEndDark,
            onHero = SakuOnHeroDark,
        )
    } else {
        SakuExtendedColors(
            income = SakuIncomeLight,
            incomeContainer = SakuIncomeContainerLight,
            expense = SakuExpenseLight,
            expenseContainer = SakuExpenseContainerLight,
            warning = SakuWarningLight,
            warningContainer = SakuWarningContainerLight,
            textSecondary = SakuTextSecondaryLight,
            heroStart = SakuHeroStartLight,
            heroEnd = SakuHeroEndLight,
            onHero = SakuOnHeroLight,
        )
    }

    CompositionLocalProvider(LocalSakuColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SakuTypography,
            shapes = SakuShapes,
            content = content,
        )
    }
}
