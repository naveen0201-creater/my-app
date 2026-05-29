package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val SleekColorScheme = darkColorScheme(
  primary = SleekPrimary,
  onPrimary = SleekOnPrimary,
  primaryContainer = SleekPrimaryContainer,
  onPrimaryContainer = SleekOnPrimaryContainer,
  secondary = SleekSecondary,
  onSecondary = SleekOnSecondary,
  secondaryContainer = SleekSecondaryContainer,
  onSecondaryContainer = SleekOnSecondaryContainer,
  background = SleekBackground,
  onBackground = SleekOnBackground,
  surface = SleekSurface,
  onSurface = SleekOnSurface,
  surfaceVariant = SleekSurfaceVariant,
  onSurfaceVariant = SleekOnSurfaceVariant,
  outline = SleekOutline,
  error = SleekError,
  onError = SleekOnError,
  errorContainer = SleekErrorContainer,
  onErrorContainer = SleekOnErrorContainer
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme by default for Sleek Interface premium styling
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve customized Sleek color palette design
  content: @Composable () -> Unit,
) {
  val colorScheme = SleekColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
