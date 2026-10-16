package com.ciao.glasscalc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Plain flat background: black in dark mode, iOS-style light gray in light mode. */
@Composable
fun Scene(dark: Boolean) {
    Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF000000) else Color(0xFFF2F2F7)))
}
