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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
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

/**
 * Google Sans Flex, the open-source (OFL) sibling of Google Sans, downloaded through Google Fonts.
 * It needs Google Play services; without it the default font is used.
 */
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)
private val flex = GoogleFont("Google Sans Flex")
private val GoogleSans = FontFamily(
    Font(googleFont = flex, fontProvider = fontProvider, weight = FontWeight.Light),
    Font(googleFont = flex, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = flex, fontProvider = fontProvider, weight = FontWeight.Medium),
)

private fun glassAlpha(dark: Boolean) = if (dark) 0.16f else 0.6f

// Same layout as the iOS Calculator.
private val rows = listOf(
    listOf("⌫", "AC", "%", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf("±", "0", ".", "="),
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
        "=" -> e
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalcApp() {
    var dark by remember { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }
    var sci by remember { mutableStateOf(false) }
    var deg by remember { mutableStateOf(true) }
    var expr by remember { mutableStateOf("") }
    var lastExpr by remember { mutableStateOf<String?>(null) }
    val history = remember { mutableStateListOf<Pair<String, String>>() }
    var showHistory by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf<Bitmap?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val haptic = LocalHapticFeedback.current
    val ink = if (dark) Color.White else Color(0xFF111111)

    val systemDark = isSystemInDarkTheme()
    LaunchedEffect(Unit) {
        dark = systemDark
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

    fun press(k: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (k == "DEG") { deg = !deg; return }
        if (k == "=") {
            val r = if (expr.isEmpty()) null else Calc.evaluate(expr, deg)?.let { Calc.format(it) }
            if (r != null) {
                history.add(0, expr to r)
                lastExpr = expr
                expr = r
            } else if (expr.isNotEmpty()) {
                expr = "Error"
                lastExpr = null
            }
            return
        }
        lastExpr = null
        expr = onKey(k, expr, deg)
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = GoogleSans)) {
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
                        Crossfade(dark, Modifier.fillMaxSize(), label = "bg") { Scene(it) }
                    }
                }

                Column(
                    Modifier.fillMaxSize().systemBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        if (tab == 0) {
                            GlassCircleButton("↺", dark, backdrop, ink) { showHistory = true }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                GlassCircleButton("←", dark, backdrop, ink) { tab = 0 }
                                Text("Unit Converter", color = ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (tab == 0) GlassCircleButton("⇄", dark, backdrop, ink) { tab = 1 }
                            Box {
                                GlassCircleButton("⋮", dark, backdrop, ink) { menu = true }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (dark) "Light theme" else "Dark theme", fontFamily = GoogleSans) },
                                        onClick = { menu = false; dark = !dark },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Choose background…", fontFamily = GoogleSans) },
                                        onClick = {
                                            menu = false
                                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                    )
                                    if (custom != null) DropdownMenuItem(
                                        text = { Text("Remove custom background", fontFamily = GoogleSans) },
                                        onClick = {
                                            menu = false
                                            File(ctx.filesDir, "bg.jpg").delete()
                                            custom = null
                                        },
                                    )
                                    if (tab == 0) DropdownMenuItem(
                                        text = { Text(if (sci) "Scientific keys  ✓" else "Scientific keys", fontFamily = GoogleSans) },
                                        onClick = { menu = false; sci = !sci },
                                    )
                                }
                            }
                        }
                    }

                    if (tab == 1) {
                        ConvertScreen(dark, backdrop, ink, Modifier.weight(1f).fillMaxWidth())
                    } else {
                        val live = if (lastExpr == null)
                            Calc.evaluate(expr, deg)?.let { Calc.format(it) }?.takeIf { it != expr }?.let { "= $it" }
                        else null
                        Column(
                            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.End,
                        ) {
                            Text(lastExpr ?: live ?: "", color = ink.copy(alpha = 0.55f), fontSize = 26.sp,
                                maxLines = 1, softWrap = false)
                            val shown = expr.ifEmpty { "0" }
                            val fs = when { shown.length > 12 -> 40.sp; shown.length > 9 -> 56.sp; else -> 80.sp }
                            Text(shown, color = ink, fontSize = fs, fontWeight = FontWeight.Light,
                                maxLines = 1, softWrap = false, textAlign = TextAlign.End)
                        }

                        // Generous, even gaps: normal keypad keeps fixed proportions (~67dp keys, 14dp gaps);
                        // with scientific rows it flexes to fit the screen.
                        val gap = if (sci) 10.dp else 14.dp
                        Column(
                            if (sci) Modifier.weight(3.9f).fillMaxWidth() else Modifier.fillMaxWidth().aspectRatio(0.793f),
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            (if (sci) sciRows + rows else rows).forEach { row ->
                                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                    row.forEach { k ->
                                        val label = if (k == "DEG" && !deg) "RAD" else k
                                        GlassKey(label, dark, backdrop, ink, null, Modifier.weight(1f).fillMaxHeight()) {
                                            press(k)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showHistory) ModalBottomSheet(onDismissRequest = { showHistory = false }) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp).padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        if (history.isEmpty()) Text("No history yet", fontFamily = GoogleSans)
                        history.forEach { (e, r) ->
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    expr = r; lastExpr = null; showHistory = false
                                }.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                Text(e, fontSize = 16.sp, fontFamily = GoogleSans)
                                Text(r, fontSize = 30.sp, fontFamily = GoogleSans)
                            }
                        }
                        if (history.isNotEmpty()) TextButton(onClick = { history.clear() }) {
                            Text("Clear history", fontFamily = GoogleSans)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A liquid-glass key. Round by default (like the iOS Calculator); with fill = true it stretches to
 * its cell and becomes a tall pill. Pressing it swells the glass and bends the lens harder.
 */
@Composable
fun GlassKey(
    label: String,
    dark: Boolean,
    backdrop: LayerBackdrop,
    ink: Color,
    tint: Color?,
    modifier: Modifier,
    fill: Boolean = false,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val bulge = animateFloatAsState(if (pressed) 1f else 0f, spring(0.5f, 380f), label = "bulge")
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            (if (fill) Modifier.fillMaxSize() else Modifier.aspectRatio(1f))
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
                            drawRect(Color.White.copy(alpha = glassAlpha(dark)))
                        }
                    },
                )
                .clickable(interactionSource = src, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val fs = when { label.length > 2 -> 18.sp; label.length > 1 -> 22.sp; else -> 32.sp }
            Text(label, color = ink, fontSize = fs, fontWeight = FontWeight.Normal)
        }
    }
}

/** Small round glass button for the top bar (history, converter, back, menu). */
@Composable
fun GlassCircleButton(text: String, dark: Boolean, backdrop: LayerBackdrop, ink: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(10f.dp.toPx(), 20f.dp.toPx())
                },
                onDrawSurface = { drawRect(Color.White.copy(alpha = glassAlpha(dark))) },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = ink, fontSize = if (text.length > 1) 15.sp else 22.sp) }
}
