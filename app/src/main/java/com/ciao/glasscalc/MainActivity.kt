package com.ciao.glasscalc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CalcApp() }
    }
}

val Amber = Color(0xFFFF9F5A)
val Sakura = Color(0xFFFF6B9D)

private val rows = listOf(
    listOf("AC", "±", "%", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf("⌫", "0", ".", "="),
)
private val sciRows = listOf(
    listOf("sin", "cos", "tan", "xʸ"),
    listOf("ln", "log", "√", "x!"),
    listOf("(", ")", "π", "DEG"),
)

private fun isValueEnd(c: Char) = c.isDigit() || c in ")πe!%."

private fun onKey(k: String, e0: String, deg: Boolean): String {
    val e = if (e0 == "Error") "" else e0
    return when (k) {
        "AC" -> ""
        "⌫" -> e.dropLast(1)
        "±" -> Calc.negateLast(e)
        "=" -> if (e.isEmpty()) "" else Calc.evaluate(e, deg)?.let { Calc.format(it) } ?: "Error"
        "sin", "cos", "tan", "ln", "log", "√" -> "$e$k("
        "xʸ" -> if (e.isNotEmpty() && isValueEnd(e.last())) "$e^" else e
        "x!" -> if (e.isNotEmpty() && isValueEnd(e.last())) "$e!" else e
        "π", "e", "(" -> e + k
        ")" -> if (e.isNotEmpty() && isValueEnd(e.last()) &&
            e.count { it == '(' } > e.count { it == ')' }) "$e)" else e
        "+", "×", "÷" ->
            if (e.isEmpty() || e.last() == '(') e else e.trimEnd('+', '−', '×', '÷', '^') + k
        "−" ->
            if (e.isEmpty() || e.last() == '(') e + k else e.trimEnd('+', '−', '×', '÷', '^') + k
        "%" -> if (e.isNotEmpty() && (e.last().isDigit() || e.last() in ")%π")) "$e%" else e
        "." -> {
            val n = e.takeLastWhile { it.isDigit() || it == '.' }
            if ('.' in n) e else if (n.isEmpty()) "${e}0." else "$e."
        }
        else -> e + k
    }
}

/** Decodes a picked image, downsampled so huge photos don't blow up memory. */
private fun decode(ctx: Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
    var s = 1
    while (bounds.outWidth / s > 2400 || bounds.outHeight / s > 2400) s *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = s }
    ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
}.getOrNull()

@Composable
fun CalcApp() {
    var dark by remember { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }
    var sci by remember { mutableStateOf(false) }
    var deg by remember { mutableStateOf(true) }
    var expr by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf<Bitmap?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val haptic = LocalHapticFeedback.current
    val ink = if (dark) Color.White else Color(0xFF1B2140)

    LaunchedEffect(Unit) {
        custom = withContext(Dispatchers.IO) {
            File(ctx.filesDir, "bg.jpg").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                decode(ctx, uri)?.also { b ->
                    File(ctx.filesDir, "bg.jpg").outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                }
            }
            if (bmp != null) custom = bmp
        }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Box(Modifier.fillMaxSize()) {
            // Whatever is drawn here is what every glass piece samples and refracts.
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                val bmp = custom
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (dark) 0.3f else 0.04f)))
                } else {
                    Crossfade(dark, Modifier.fillMaxSize(), label = "scene") { Scene(it) }
                }
            }

            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassChip("Calc", tab == 0, backdrop, ink) { tab = 0 }
                        GlassChip("Convert", tab == 1, backdrop, ink) { tab = 1 }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ThemeToggle(dark, backdrop, ink) { dark = !dark }
                        Box {
                            GlassChip("⋮", false, backdrop, ink) { menu = true }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Choose background…") },
                                    onClick = {
                                        menu = false
                                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                )
                                if (custom != null) DropdownMenuItem(
                                    text = { Text("Use built-in anime scene") },
                                    onClick = {
                                        menu = false
                                        File(ctx.filesDir, "bg.jpg").delete()
                                        custom = null
                                    },
                                )
                                if (tab == 0) DropdownMenuItem(
                                    text = { Text(if (sci) "Scientific keys  ✓" else "Scientific keys") },
                                    onClick = { menu = false; sci = !sci },
                                )
                            }
                        }
                    }
                }

                if (tab == 1) {
                    ConvertScreen(dark, backdrop, ink, Modifier.weight(1f).fillMaxWidth())
                } else {
                    val res = Calc.evaluate(expr, deg)?.let { Calc.format(it) }
                    val preview = if (res != null && res != expr) "= $res" else ""
                    Box(
                        Modifier
                            .weight(0.9f)
                            .fillMaxWidth()
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedCornerShape(36.dp) },
                                effects = {
                                    vibrancy()
                                    blur(10f.dp.toPx())
                                    lens(12f.dp.toPx(), 24f.dp.toPx())
                                },
                                onDrawSurface = { drawRect(Color.White.copy(alpha = if (dark) 0.08f else 0.22f)) },
                            )
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(preview, color = ink.copy(alpha = 0.65f), fontSize = 22.sp, maxLines = 1)
                            val shown = expr.ifEmpty { "0" }
                            val fs = when { shown.length > 16 -> 26.sp; shown.length > 12 -> 32.sp; shown.length > 8 -> 44.sp; else -> 62.sp }
                            Text(shown, color = ink, fontSize = fs, fontWeight = FontWeight.Light,
                                maxLines = 1, textAlign = TextAlign.End)
                        }
                    }

                    Column(
                        Modifier.weight(if (sci) 3.6f else 2.8f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (if (sci) sciRows + rows else rows).forEach { row ->
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                row.forEach { k ->
                                    val tint = when (k) {
                                        "÷", "×", "−", "+" -> Amber
                                        "=" -> Sakura
                                        else -> null
                                    }
                                    val label = if (k == "DEG" && !deg) "RAD" else k
                                    GlassKey(label, dark, backdrop, ink, tint, Modifier.weight(1f).fillMaxHeight()) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (k == "DEG") deg = !deg else expr = onKey(k, expr, deg)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A round key, like the iPhone PIN pad. Pressing it swells the glass and strengthens the lens,
 * so the background visibly bends around your finger.
 */
@Composable
fun GlassKey(
    label: String,
    dark: Boolean,
    backdrop: LayerBackdrop,
    ink: Color,
    tint: Color?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val bulge = animateFloatAsState(if (pressed) 1f else 0f, spring(0.5f, 380f), label = "bulge")
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .aspectRatio(1f)
                .graphicsLayer {
                    val s = 1f + 0.1f * bulge.value
                    scaleX = s
                    scaleY = s
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(2f.dp.toPx())
                        lens(
                            12f.dp.toPx() + 8f.dp.toPx() * bulge.value,
                            24f.dp.toPx() + 24f.dp.toPx() * bulge.value,
                        )
                    },
                    onDrawSurface = {
                        if (tint != null) {
                            drawRect(tint, blendMode = BlendMode.Hue)
                            drawRect(tint.copy(alpha = 0.55f))
                        } else {
                            drawRect(Color.White.copy(alpha = if (dark) 0.08f else 0.24f))
                        }
                    },
                )
                .clickable(interactionSource = src, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val fs = when { label.length > 2 -> 18.sp; label.length > 1 -> 22.sp; else -> 32.sp }
            Text(label, color = ink, fontSize = fs, fontWeight = FontWeight.Light)
        }
    }
}

/** A glass pill whose thumb is a second lens sliding over the first, like a drop of liquid. */
@Composable
fun ThemeToggle(dark: Boolean, backdrop: LayerBackdrop, ink: Color, onToggle: () -> Unit) {
    val x by animateDpAsState(if (dark) 48.dp else 0.dp, spring(0.55f, 300f), label = "thumb")
    Box(
        Modifier
            .width(96.dp)
            .height(48.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    blur(6f.dp.toPx())
                    lens(8f.dp.toPx(), 16f.dp.toPx())
                },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.1f)) },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            ),
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically) {
            Text("☀", color = ink, fontSize = 20.sp)
            Text("☾", color = ink, fontSize = 20.sp)
        }
        Box(
            Modifier
                .offset(x = x)
                .size(48.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        lens(14f.dp.toPx(), 28f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 0.22f)) },
                ),
        )
    }
}
