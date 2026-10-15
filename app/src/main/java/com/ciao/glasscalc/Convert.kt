package com.ciao.glasscalc

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A unit and its size relative to the category's base unit (for currencies: 1 / rate per USD). */
data class Un(val n: String, val f: Double)

object Units {
    val table: Map<String, List<Un>> = linkedMapOf(
        "Length" to listOf(Un("m", 1.0), Un("km", 1000.0), Un("cm", .01), Un("mm", .001),
            Un("mi", 1609.344), Un("yd", .9144), Un("ft", .3048), Un("in", .0254)),
        "Mass" to listOf(Un("kg", 1.0), Un("g", .001), Un("lb", .45359237), Un("oz", .028349523), Un("t", 1000.0)),
        "Temperature" to listOf(Un("°C", 1.0), Un("°F", 1.0), Un("K", 1.0)),
        "Volume" to listOf(Un("L", 1.0), Un("mL", .001), Un("gal", 3.785411784),
            Un("qt", .946352946), Un("cup", .2365882365), Un("fl oz", .0295735296)),
        "Speed" to listOf(Un("m/s", 1.0), Un("km/h", 1 / 3.6), Un("mph", .44704), Un("kn", .5144444)),
        "Area" to listOf(Un("m²", 1.0), Un("km²", 1e6), Un("ha", 1e4), Un("ft²", .09290304), Un("ac", 4046.8564224)),
        "Time" to listOf(Un("s", 1.0), Un("min", 60.0), Un("h", 3600.0), Un("day", 86400.0), Un("wk", 604800.0)),
        "Data" to listOf(Un("B", 1.0), Un("KB", 1e3), Un("MB", 1e6), Un("GB", 1e9), Un("TB", 1e12)),
    )

    fun convert(cat: String, v: Double, a: Un, b: Un): Double {
        if (cat != "Temperature") return v * a.f / b.f
        val c = when (a.n) { "°F" -> (v - 32) * 5 / 9; "K" -> v - 273.15; else -> v }
        return when (b.n) { "°F" -> c * 9 / 5 + 32; "K" -> c + 273.15; else -> c }
    }
}

object Fx {
    val codes = listOf("USD", "EUR", "GBP", "JPY", "CNY", "INR", "CAD", "AUD", "CHF", "AED",
        "SAR", "TRY", "BRL", "MXN", "ZAR", "NGN", "KES", "ETB")

    /** Rough offline estimates, only used when the live fetch fails. */
    val fallback = mapOf("USD" to 1.0, "EUR" to 0.92, "GBP" to 0.79, "JPY" to 150.0, "CNY" to 7.2,
        "INR" to 84.0, "CAD" to 1.36, "AUD" to 1.52, "CHF" to 0.88, "AED" to 3.67, "SAR" to 3.75,
        "TRY" to 34.0, "BRL" to 5.5, "MXN" to 19.0, "ZAR" to 18.0, "NGN" to 1500.0, "KES" to 129.0, "ETB" to 120.0)

    /** Returns rates per 1 USD and whether they are live. */
    suspend fun load(): Pair<Map<String, Double>, Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://open.er-api.com/v6/latest/USD").openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 6000
            val rates = JSONObject(c.inputStream.bufferedReader().readText()).getJSONObject("rates")
            codes.associateWith { rates.getDouble(it) } to true
        }.getOrElse { fallback to false }
    }
}

@Composable
fun GlassChip(text: String, selected: Boolean, backdrop: LayerBackdrop, ink: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                    lens(8f.dp.toPx(), 16f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(if (selected) Sakura.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.12f))
                },
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = ink, fontSize = 16.sp) }
}

@Composable
private fun UnitRow(units: List<Un>, sel: Int, backdrop: LayerBackdrop, ink: Color, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        units.forEachIndexed { i, u -> GlassChip(u.n, i == sel, backdrop, ink) { onPick(i) } }
    }
}

private val padRows = listOf(
    listOf("7", "8", "9", "⌫"),
    listOf("4", "5", "6", "AC"),
    listOf("1", "2", "3", "±"),
    listOf("0", ".", "⇅"),
)

@Composable
fun ConvertScreen(dark: Boolean, backdrop: LayerBackdrop, ink: Color, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    var cat by remember { mutableStateOf("Length") }
    var fi by remember { mutableIntStateOf(0) }
    var ti by remember { mutableIntStateOf(1) }
    var input by remember { mutableStateOf("1") }
    var fx by remember { mutableStateOf<Pair<Map<String, Double>, Boolean>?>(null) }
    LaunchedEffect(Unit) { fx = Fx.load() }

    val units = if (cat == "Currency") {
        val r = fx?.first ?: Fx.fallback
        Fx.codes.mapNotNull { c -> r[c]?.let { Un(c, 1.0 / it) } }
    } else Units.table.getValue(cat)
    val a = units[fi.coerceIn(units.indices)]
    val b = units[ti.coerceIn(units.indices)]
    val out = input.toDoubleOrNull()?.let { Calc.format(Units.convert(cat, it, a, b)) } ?: "—"

    fun press(k: String) {
        input = when (k) {
            "⌫" -> input.dropLast(1)
            "AC" -> ""
            "±" -> if (input.startsWith("-")) input.drop(1) else "-$input"
            "." -> if ('.' in input) input else if (input.isEmpty() || input == "-") "${input}0." else "$input."
            "⇅" -> { val t = fi; fi = ti; ti = t; input }
            else -> if (input == "0") k else input + k
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (Units.table.keys + "Currency").forEach { c ->
                GlassChip(c, c == cat, backdrop, ink) { cat = c; fi = 0; ti = 1 }
            }
        }
        Column(
            Modifier
                .weight(1.5f)
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
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(input.ifEmpty { "0" }, color = ink, fontSize = 44.sp, fontWeight = FontWeight.Light,
                maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            UnitRow(units, fi, backdrop, ink) { fi = it }
            Text(out, color = ink, fontSize = 44.sp, fontWeight = FontWeight.Light,
                maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            UnitRow(units, ti, backdrop, ink) { ti = it }
            if (cat == "Currency") {
                Text(
                    when { fx == null -> "Fetching live rates…"; fx!!.second -> "Live rates (open.er-api.com)"; else -> "Offline estimates, not live" },
                    color = ink.copy(alpha = 0.6f), fontSize = 12.sp,
                )
            }
        }
        Column(Modifier.weight(2.2f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            padRows.forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { k ->
                        GlassKey(k, dark, backdrop, ink, if (k == "⇅") Amber else null,
                            Modifier.weight(if (k == "0") 2f else 1f).fillMaxHeight()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            press(k)
                        }
                    }
                }
            }
        }
    }
}
