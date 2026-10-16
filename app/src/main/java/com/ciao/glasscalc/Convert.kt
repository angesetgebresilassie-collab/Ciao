package com.ciao.glasscalc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A unit: symbol, size relative to the category's base unit (currencies: 1 / rate per USD), full name. */
data class Un(val n: String, val f: Double, val full: String = "")

object Units {
    val table: Map<String, List<Un>> = linkedMapOf(
        "Length" to listOf(Un("m", 1.0, "Meter"), Un("km", 1000.0, "Kilometer"), Un("cm", .01, "Centimeter"),
            Un("mm", .001, "Millimeter"), Un("mi", 1609.344, "Mile"), Un("yd", .9144, "Yard"),
            Un("ft", .3048, "Foot"), Un("in", .0254, "Inch")),
        "Mass" to listOf(Un("kg", 1.0, "Kilogram"), Un("g", .001, "Gram"), Un("lb", .45359237, "Pound"),
            Un("oz", .028349523, "Ounce"), Un("t", 1000.0, "Tonne")),
        "Temperature" to listOf(Un("°C", 1.0, "Celsius"), Un("°F", 1.0, "Fahrenheit"), Un("K", 1.0, "Kelvin")),
        "Volume" to listOf(Un("L", 1.0, "Liter"), Un("mL", .001, "Milliliter"), Un("gal", 3.785411784, "US Gallon"),
            Un("qt", .946352946, "US Quart"), Un("cup", .2365882365, "Cup"), Un("fl oz", .0295735296, "US Fluid Ounce")),
        "Speed" to listOf(Un("m/s", 1.0, "Meter/sec"), Un("km/h", 1 / 3.6, "Kilometer/hour"),
            Un("mph", .44704, "Mile/hour"), Un("kn", .5144444, "Knot")),
        "Area" to listOf(Un("m²", 1.0, "Square Meter"), Un("km²", 1e6, "Square Kilometer"), Un("ha", 1e4, "Hectare"),
            Un("ft²", .09290304, "Square Foot"), Un("ac", 4046.8564224, "Acre")),
        "Time" to listOf(Un("s", 1.0, "Second"), Un("min", 60.0, "Minute"), Un("h", 3600.0, "Hour"),
            Un("day", 86400.0, "Day"), Un("wk", 604800.0, "Week")),
        "Data" to listOf(Un("B", 1.0, "Byte"), Un("KB", 1e3, "Kilobyte"), Un("MB", 1e6, "Megabyte"),
            Un("GB", 1e9, "Gigabyte"), Un("TB", 1e12, "Terabyte")),
    )

    /** Which three units each category starts with (indexes into its list). */
    val defaults = mapOf(
        "Length" to listOf(6, 2, 7), "Mass" to listOf(2, 0, 3), "Temperature" to listOf(0, 1, 2),
        "Volume" to listOf(0, 2, 1), "Speed" to listOf(1, 2, 0), "Area" to listOf(4, 0, 3),
        "Time" to listOf(2, 1, 0), "Data" to listOf(3, 2, 1), "Currency" to listOf(0, 1, 2),
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

private val padRows = listOf(
    listOf("7", "8", "9"),
    listOf("4", "5", "6"),
    listOf("1", "2", "3"),
    listOf("±", "0", "."),
)

@Composable
private fun ConvRow(
    unit: Un,
    units: List<Un>,
    value: String,
    active: Boolean,
    ink: Color,
    ff: FontFamily?,
    onSelect: () -> Unit,
    onUnit: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSelect)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Row(
                Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (unit.full.isEmpty()) unit.n else "${unit.full}  ${unit.n}", color = ink.copy(alpha = 0.9f), fontSize = 18.sp)
                Text("  ▾", color = ink.copy(alpha = 0.6f), fontSize = 14.sp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                units.forEachIndexed { i, u ->
                    DropdownMenuItem(
                        text = { Text(if (u.full.isEmpty()) u.n else "${u.full}  ${u.n}", fontFamily = ff) },
                        onClick = { open = false; onUnit(i) },
                    )
                }
            }
        }
        Text(
            value,
            color = ink.copy(alpha = if (active) 1f else 0.6f),
            fontSize = if (active) 40.sp else 28.sp,
            fontWeight = if (active) FontWeight.Light else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
    }
}

@Composable
fun ConvertScreen(dark: Boolean, backdrop: LayerBackdrop, ink: Color, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    val ff = LocalTextStyle.current.fontFamily
    var cat by remember { mutableStateOf("Length") }
    val idx = remember { mutableStateListOf(6, 2, 7) }
    var active by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("1") }
    var fx by remember { mutableStateOf<Pair<Map<String, Double>, Boolean>?>(null) }
    LaunchedEffect(Unit) { fx = Fx.load() }

    val units = if (cat == "Currency") {
        val r = fx?.first ?: Fx.fallback
        Fx.codes.mapNotNull { c -> r[c]?.let { Un(c, 1.0 / it) } }
    } else Units.table.getValue(cat)
    fun unit(i: Int) = units[idx[i].coerceIn(units.indices)]
    val src = unit(active)
    val values = List(3) { i ->
        if (i == active) input.ifEmpty { "0" }
        else input.toDoubleOrNull()?.let { Calc.format(Units.convert(cat, it, src, unit(i))) } ?: "—"
    }

    fun press(k: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        input = when (k) {
            "⌫" -> input.dropLast(1)
            "AC" -> ""
            "±" -> if (input.startsWith("-")) input.drop(1) else "-$input"
            "." -> if ('.' in input) input else if (input.isEmpty() || input == "-") "${input}0." else "$input."
            else -> if (input == "0") k else input + k
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (Units.table.keys + "Currency").forEach { c ->
                val sel = c == cat
                Column(
                    Modifier
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            cat = c
                            (Units.defaults[c] ?: listOf(0, 1, 2)).forEachIndexed { k, v -> idx[k] = v }
                            active = 0
                            input = "1"
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(c, color = ink.copy(alpha = if (sel) 1f else 0.5f), fontSize = 17.sp,
                        fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal)
                    Box(
                        Modifier.padding(top = 4.dp).height(2.dp).width(22.dp)
                            .background(if (sel) ink else Color.Transparent, CircleShape),
                    )
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.SpaceEvenly) {
            repeat(3) { i ->
                ConvRow(unit(i), units, values[i], i == active, ink, ff,
                    onSelect = {
                        if (i != active) {
                            input = values[i].takeIf { it != "—" } ?: ""
                            active = i
                        }
                    },
                    onUnit = { idx[i] = it })
            }
            if (cat == "Currency") {
                Text(
                    when { fx == null -> "Fetching live rates…"; fx!!.second -> "Live rates"; else -> "Offline estimates, not live" },
                    color = ink.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 3-column pad plus tall AC and backspace pills; fixed proportions so the gaps stay generous.
        Row(Modifier.fillMaxWidth().aspectRatio(1.03f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                padRows.forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { k ->
                            GlassKey(k, dark, backdrop, ink, null, Modifier.weight(1f).fillMaxHeight()) { press(k) }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GlassKey("AC", dark, backdrop, ink, null, Modifier.weight(1f).fillMaxWidth(), fill = true) { press("AC") }
                GlassKey("⌫", dark, backdrop, ink, null, Modifier.weight(1f).fillMaxWidth(), fill = true) { press("⌫") }
            }
        }
    }
}
