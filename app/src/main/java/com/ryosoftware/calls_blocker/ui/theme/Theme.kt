package com.ryosoftware.calls_blocker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import com.ryosoftware.calls_blocker.R

@Composable
fun CallsBlockerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = colorResource(R.color.primary),
            onPrimary = colorResource(R.color.on_primary),
            primaryContainer = colorResource(R.color.primary_container),
            onPrimaryContainer = colorResource(R.color.on_primary_container)
        )
        else -> lightColorScheme(
            primary = colorResource(R.color.primary),
            onPrimary = colorResource(R.color.on_primary),
            primaryContainer = colorResource(R.color.primary_container),
            onPrimaryContainer = colorResource(R.color.on_primary_container)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
