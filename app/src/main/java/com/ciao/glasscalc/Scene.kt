package com.ciao.glasscalc

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Flat, anime-style vector scenes. Night: moonlit city. Day: Fuji, torii and sakura. */
@Composable
fun Scene(dark: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        if (dark) night() else day()
        sakura(dark)
    }
}

private fun DrawScope.night() {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF0C1230))
    for (i in 0 until 46) {
        val x = ((i * 97) % 100) / 100f * w
        val y = ((i * 53) % 46) / 100f * h
        drawCircle(Color.White.copy(alpha = 0.35f + (i % 4) * 0.15f), 1.5f + (i % 3), Offset(x, y))
    }
    val moon = Color(0xFFF6F1D8)
    val c = Offset(w * 0.68f, h * 0.2f)
    drawCircle(moon.copy(alpha = 0.12f), w * 0.3f, c)
    drawCircle(moon.copy(alpha = 0.2f), w * 0.24f, c)
    drawCircle(moon, w * 0.19f, c)
    drawCircle(Color(0xFFE6DFB8), w * 0.035f, c + Offset(-w * 0.06f, -w * 0.04f))
    drawCircle(Color(0xFFE6DFB8), w * 0.022f, c + Offset(w * 0.07f, w * 0.03f))
    skyline(Color(0xFF1B2450), h * 0.5f, 3, false)
    skyline(Color(0xFF10173A), h * 0.62f, 11, true)
}

private fun DrawScope.skyline(color: Color, baseY: Float, seed: Int, windows: Boolean) {
    var x = 0f
    var i = seed
    while (x < size.width) {
        i = (i * 31 + 7) % 97
        val bw = size.width * (0.07f + (i % 5) * 0.02f)
        val bh = size.height * (0.06f + (i % 7) * 0.02f)
        drawRect(color, Offset(x, baseY - bh), Size(bw, size.height))
        if (windows) {
            val lit = Color(0xFFFFD88A).copy(alpha = 0.85f)
            var wy = baseY - bh + 16f
            var row = 0
            while (wy < baseY + size.height * 0.1f) {
                if ((i + row) % 3 != 0) drawRect(lit, Offset(x + bw * 0.22f, wy), Size(7f, 10f))
                if ((i + row * 2) % 4 != 0) drawRect(lit, Offset(x + bw * 0.6f, wy), Size(7f, 10f))
                wy += 30f
                row++
            }
        }
        x += bw
    }
}

private fun DrawScope.day() {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFFCFE6FA))
    drawCircle(Color(0xFFFFB3C1).copy(alpha = 0.45f), w * 0.26f, Offset(w * 0.5f, h * 0.2f))
    drawCircle(Color(0xFFFFB3C1), w * 0.19f, Offset(w * 0.5f, h * 0.2f))
    cloud(w * 0.16f, h * 0.12f, w * 0.05f)
    cloud(w * 0.82f, h * 0.32f, w * 0.045f)
    val fuji = Path().apply {
        moveTo(w * 0.02f, h * 0.56f)
        lineTo(w * 0.5f, h * 0.28f)
        lineTo(w * 0.98f, h * 0.56f)
        close()
    }
    drawPath(fuji, Color(0xFF8FA8D6))
    val cap = Path().apply {
        moveTo(w * 0.5f, h * 0.28f)
        lineTo(w * 0.61f, h * 0.348f)
        lineTo(w * 0.56f, h * 0.335f)
        lineTo(w * 0.5f, h * 0.365f)
        lineTo(w * 0.44f, h * 0.335f)
        lineTo(w * 0.39f, h * 0.348f)
        close()
    }
    drawPath(cap, Color.White)
    drawRect(Color(0xFFB9D6C0), Offset(0f, h * 0.56f), Size(w, h))
    drawCircle(Color(0xFFA5C8B0), w * 0.4f, Offset(w * 0.05f, h * 0.7f))
    drawCircle(Color(0xFFA5C8B0), w * 0.35f, Offset(w * 0.95f, h * 0.72f))
    torii(w * 0.24f, h * 0.64f, w * 0.2f)
}

private fun DrawScope.cloud(x: Float, y: Float, r: Float) {
    val c = Color.White.copy(alpha = 0.92f)
    drawCircle(c, r, Offset(x, y))
    drawCircle(c, r * 0.8f, Offset(x + r * 1.1f, y + r * 0.2f))
    drawCircle(c, r * 0.7f, Offset(x - r, y + r * 0.3f))
}

private fun DrawScope.torii(cx: Float, base: Float, s: Float) {
    val red = Color(0xFFE4574F)
    drawRect(red, Offset(cx - s * 0.38f, base - s), Size(s * 0.07f, s))
    drawRect(red, Offset(cx + s * 0.31f, base - s), Size(s * 0.07f, s))
    drawRect(red, Offset(cx - s * 0.5f, base - s * 1.06f), Size(s, s * 0.07f))
    drawRect(red, Offset(cx - s * 0.4f, base - s * 0.78f), Size(s * 0.8f, s * 0.05f))
}

private fun DrawScope.sakura(dark: Boolean) {
    val w = size.width
    val h = size.height
    val wood = if (dark) Color(0xFF4A2F3A) else Color(0xFF6B4A4A)
    val pink = Color(0xFFFFB7C9)
    drawLine(wood, Offset(w * 1.02f, h * 0.05f), Offset(w * 0.58f, h * 0.19f), w * 0.012f, StrokeCap.Round)
    drawLine(wood, Offset(w * 0.82f, h * 0.1f), Offset(w * 0.7f, h * 0.04f), w * 0.008f, StrokeCap.Round)
    for (i in 0..9) {
        val t = i / 9f
        val cx = w * (1f - 0.42f * t)
        val cy = h * (0.06f + 0.13f * t) + ((i % 3) - 1) * h * 0.012f
        drawCircle(pink, w * 0.022f, Offset(cx, cy))
        drawCircle(Color.White.copy(alpha = 0.7f), w * 0.01f, Offset(cx - w * 0.004f, cy - w * 0.004f))
    }
    for (i in 0 until 16) {
        val x = ((i * 61) % 100) / 100f * w
        val y = ((i * 37) % 70) / 100f * h
        drawCircle(pink.copy(alpha = 0.75f), 4f + (i % 3) * 2f, Offset(x, y))
    }
}
