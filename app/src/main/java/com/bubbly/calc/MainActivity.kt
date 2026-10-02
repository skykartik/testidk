package com.bubbly.calc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private lateinit var sfx: Sfx

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sfx = Sfx(applicationContext)
        setContent { BubblyApp(sfx) }
    }

    override fun onDestroy() {
        super.onDestroy()
        sfx.release()
    }
}

private val OPS = setOf("+", "\u2212", "\u00d7", "\u00f7")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubblyApp(sfx: Sfx) {
    val calc = remember { Calc() }
    var expr by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    var mem by remember { mutableStateOf(0.0) }
    var deg by remember { mutableStateOf(true) }
    var theme by remember { mutableStateOf(0) }
    var soundOn by remember { mutableStateOf(true) }
    var sciOpen by remember { mutableStateOf(false) }
    var catIdx by remember { mutableStateOf(-1) } // -1 = standard mode
    var fromIdx by remember { mutableStateOf(0) }
    var toIdx by remember { mutableStateOf(1) }
    var rates by remember { mutableStateOf(fallbackRates()) }
    var ratesLive by remember { mutableStateOf(false) }
    var hist by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var sheet by remember { mutableStateOf<String?>(null) } // null | "hist" | "mode" | "from" | "to"
    var errorFlash by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val th = THEMES[theme]
    val c0 = Color(th.a); val c1 = Color(th.b); val c2 = Color(th.c); val c3 = Color(th.d)

    LaunchedEffect(Unit) {
        // best-effort live currency refresh; the app is fully usable offline with the built-in rates either way.
        // Android throws NetworkOnMainThreadException for any network I/O on the main thread - LaunchedEffect
        // runs on the UI dispatcher by default, so the actual request has to be explicitly pushed onto an IO
        // dispatcher or this would silently fail every time on a real device (caught by the try/catch below,
        // so no crash - just a feature that quietly never works).
        try {
            val updated = withContext(Dispatchers.IO) {
                val conn = URL("https://open.er-api.com/v6/latest/USD").openConnection() as HttpURLConnection
                conn.connectTimeout = 4000; conn.readTimeout = 4000
                val text = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                val found = Regex("\"([A-Z]{3})\":([0-9.eE+-]+)").findAll(text)
                val m = rates.toMutableMap()
                for (match in found) m[match.groupValues[1]] = match.groupValues[2].toDoubleOrNull() ?: continue
                m
            }
            rates = updated
            ratesLive = true
        } catch (_: Exception) { /* offline is fine - keep the built-in fallback rates */ }
    }

    fun add(t: String) {
        if (done) {
            done = false
            val post = t in OPS || t == "%" || t == "!" || t.startsWith("^")
            expr = if (post) fmtNum(calc.ans) + t else t
            return
        }
        val last = if (expr.isNotEmpty()) expr.last().toString() else ""
        if (t in OPS && t != "\u2212") {
            if (expr.isEmpty() || last == "(") return
            if (last in OPS) { expr = expr.dropLast(1) + t; return }
        }
        expr += t
    }

    fun currentCat() = if (catIdx >= 0) CATS[catIdx] else null

    fun equalsPressed() {
        if (expr.isEmpty()) return
        try {
            val v = calc.eval(expr)
            val cat = currentCat()
            val resultStr: String
            if (cat != null) {
                val converted = convert(cat, fromIdx, toIdx, v, rates)
                resultStr = fmtNum(converted)
                hist = (listOf(expr + " " + cat.units[fromIdx] + " \u2192 " + cat.units[toIdx] to resultStr) + hist).take(40)
                expr = fmtNum(v); done = true
            } else {
                resultStr = fmtNum(v)
                hist = (listOf(expr to resultStr) + hist).take(40)
                calc.ans = v
                expr = resultStr; done = true
            }
            sfx.play("eq")
        } catch (_: Exception) {
            errorFlash = true
            sfx.play("err")
            scope.launch {
                kotlinx.coroutines.delay(700)
                errorFlash = false
            }
        }
    }

    fun fire(code: String) {
        when {
            code.length == 1 && code[0].isDigit() -> sfx.play("d${code}")
            code == "@AC" -> sfx.play("clr")
            code in OPS -> sfx.play("op")
            code != "@EQ" -> sfx.play("fn")
        }
        when (code) {
            "@AC" -> { expr = ""; done = false }
            "@PAR" -> {
                val o = expr.count { it == '(' }; val cl = expr.count { it == ')' }
                val last = if (expr.isNotEmpty()) expr.last() else ' '
                add(if (o > cl && (last.isDigit() || ")\u03c0e!%.".contains(last))) ")" else "(")
            }
            "@NEG" -> {
                if (done) { calc.ans = -calc.ans; expr = fmtNum(calc.ans) }
                else {
                    val m = Regex("(\u2212?[0-9.]+)$").find(expr)
                    if (m != null) {
                        val v = m.groupValues[1]
                        val repl = if (v.startsWith("\u2212")) v.substring(1) else "\u2212$v"
                        expr = expr.substring(0, m.range.first) + repl
                    } else expr = "\u2212$expr"
                }
            }
            "@EQ" -> { equalsPressed(); return }
            "@MC" -> mem = 0.0
            "@MR" -> add(fmtNum(mem))
            "@M+", "@M-" -> {
                val v = try { if (expr.isNotEmpty()) calc.eval(expr) else calc.ans } catch (_: Exception) { 0.0 }
                mem += if (code == "@M+") v else -v
            }
            else -> add(code)
        }
    }

    val previewText = remember(expr, done, catIdx, fromIdx, toIdx, rates) {
        if (done) "" else {
            val cat = currentCat()
            try {
                val v = calc.eval(if (expr.isNotEmpty()) expr else "0")
                if (cat != null) "= " + fmtNum(convert(cat, fromIdx, toIdx, v, rates)) + " " + cat.units[toIdx]
                else { val f = fmtNum(v); if (f != expr) "= $f" else "" }
            } catch (_: Exception) { "" }
        }
    }

    MaterialTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(c0.copy(alpha = .25f), c1.copy(alpha = .18f), c2.copy(alpha = .25f))))
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // top bar
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("\u2726 bubbly", color = c0, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.weight(1f))
                    Chip(if (soundOn) "Sound" else "Muted", c0, active = soundOn) { soundOn = !soundOn }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(c0, c1)))
                            .clickable { sfx.play("tick"); theme = (theme + 1) % THEMES.size }
                    )
                }

                Spacer(Modifier.height(8.dp))

                // mode + tool row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(if (catIdx < 0) "Standard \u25be" else CATS[catIdx].name + " \u25be", c0) {
                        sfx.play("tick"); sheet = "mode"
                    }
                    Spacer(Modifier.weight(1f))
                    if (catIdx < 0) {
                        Chip(if (deg) "DEG" else "RAD", c0) { deg = !deg; calc.deg = deg }
                        Spacer(Modifier.width(6.dp))
                        Chip("f(x)", c0, active = sciOpen) { sfx.play("tick"); sciOpen = !sciOpen }
                        Spacer(Modifier.width(6.dp))
                    }
                    Chip("Hist", c0) { sfx.play("tick"); sheet = "hist" }
                }

                Spacer(Modifier.height(10.dp))

                // display
                // a real keyframe shake, run once each time errorFlash flips true - the first draft called
                // System.nanoTime() directly inside the composable body, which doesn't animate at all (it just
                // reads one frozen value at whatever instant recomposition happened to occur)
                val shakeOffset = remember { Animatable(0f) }
                LaunchedEffect(errorFlash) {
                    if (errorFlash) {
                        shakeOffset.animateTo(0f, animationSpec = keyframes {
                            durationMillis = 400
                            0f at 0
                            (-14f) at 60
                            12f at 140
                            (-8f) at 220
                            5f at 300
                            0f at 400
                        })
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .offset(x = shakeOffset.value.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .55f), Color.White.copy(alpha = .2f))))
                        .padding(18.dp, 14.dp)
                ) {
                    if (mem != 0.0) Text("M  " + fmtNum(mem), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF241446).copy(alpha = .55f))
                    if (catIdx >= 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            UnitChip(CATS[catIdx].units[fromIdx]) { sheet = "from" }
                            Text("\u21c4", modifier = Modifier.clickable {
                                val t = fromIdx; fromIdx = toIdx; toIdx = t
                            }, fontSize = 18.sp)
                            UnitChip(CATS[catIdx].units[toIdx]) { sheet = "to" }
                        }
                        Text(if (!ratesLive && catIdx == 0) "offline rates" else "", fontSize = 10.sp, color = Color.Gray)
                    } else {
                        Text(previewText, fontSize = 14.sp, color = Color(0xFF241446).copy(alpha = .6f),
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    }
                    Text(
                        if (errorFlash) "Oops" else if (expr.isEmpty()) "0" else expr,
                        fontSize = 46.sp, fontWeight = FontWeight.Light, color = Color(0xFF241446),
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End, maxLines = 1
                    )
                }

                Spacer(Modifier.height(8.dp))

                // scientific row
                AnimatedVisibility(visible = sciOpen && catIdx < 0, enter = expandVertically(), exit = shrinkVertically()) {
                    val sci = listOf(
                        "sin" to "sin(", "cos" to "cos(", "tan" to "tan(", "ln" to "ln(", "log" to "log(", "\u221a" to "\u221a(",
                        "\u03c0" to "\u03c0", "e" to "e", "x\u00b2" to "^2", "x\u02b8" to "^", "n!" to "!", "Ans" to "Ans",
                        "MC" to "@MC", "MR" to "@MR", "M+" to "@M+", "M\u2212" to "@M-", "1/x" to "^(-1)", "|x|" to "abs("
                    )
                    FlowKeyGrid(columns = 6, items = sci, height = 34.dp, fontSize = 13.sp,
                        bg = c3.copy(alpha = .5f)) { code -> fire(code) }
                }

                Spacer(Modifier.height(8.dp))

                // keypad
                val pad = listOf(
                    Triple("AC", "@AC", "fn"), Triple("( )", "@PAR", "fn"), Triple("%", "%", "fn"), Triple("\u00f7", "\u00f7", "op"),
                    Triple("7", "7", ""), Triple("8", "8", ""), Triple("9", "9", ""), Triple("\u00d7", "\u00d7", "op"),
                    Triple("4", "4", ""), Triple("5", "5", ""), Triple("6", "6", ""), Triple("\u2212", "\u2212", "op"),
                    Triple("1", "1", ""), Triple("2", "2", ""), Triple("3", "3", ""), Triple("+", "+", "op"),
                    Triple("\u00b1", "@NEG", "fn"), Triple("0", "0", ""), Triple(".", ".", ""), Triple("=", "@EQ", "eq"),
                )
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pad.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (label, code, kind) ->
                                Key(label, kind, c0, c1, Modifier.weight(1f).fillMaxHeight()) { fire(code) }
                            }
                        }
                    }
                }
            }

            // ---- sheets ----
            if (sheet == "hist") {
                SheetScrim { sheet = null }
                HistorySheet(hist, c0) { res -> expr = res; done = false; sheet = null }
            }
            if (sheet == "mode") {
                SheetScrim { sheet = null }
                PickerSheet("Choose a mode", listOf("Standard") + CATS.map { it.name }, c0) { pickedIdx ->
                    catIdx = pickedIdx - 1
                    if (catIdx >= 0) { fromIdx = CATS[catIdx].a; toIdx = CATS[catIdx].b; sciOpen = false }
                    expr = ""; done = false
                    sheet = null
                }
            }
            if (sheet == "from" && catIdx >= 0) {
                SheetScrim { sheet = null }
                PickerSheet("Convert from", CATS[catIdx].units, c0) { idx -> fromIdx = idx; sheet = null }
            }
            if (sheet == "to" && catIdx >= 0) {
                SheetScrim { sheet = null }
                PickerSheet("Convert to", CATS[catIdx].units, c0) { idx -> toIdx = idx; sheet = null }
            }
        }
    }
}

@Composable
fun SheetScrim(onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .25f)).clickable(onClick = onDismiss))
}

@Composable
fun Chip(label: String, accent: Color, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) accent.copy(alpha = .5f) else Color.White.copy(alpha = .55f))
            .clickable(onClick = onClick)
            .padding(10.dp, 6.dp)
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF241446)) }
}

@Composable
fun UnitChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = .6f))
            .clickable(onClick = onClick)
            .padding(10.dp, 4.dp)
    ) { Text("$label \u25be", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF241446)) }
}

@Composable
fun Key(label: String, kind: String, c0: Color, c1: Color, modifier: Modifier, onClick: () -> Unit) {
    // collectIsPressedAsState() is the standard, correct way to get real press-down/release state out of an
    // interactionSource shared with clickable() - an earlier draft of this used a hand-rolled stub that never
    // actually updated, which would have compiled fine but left every key permanently un-springy. Caught on
    // a careful re-read rather than left for a failed build.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "keyscale"
    )
    val bg = when (kind) {
        "op" -> Brush.linearGradient(listOf(c0, c1))
        "eq" -> Brush.linearGradient(listOf(Color(0xFFC6FF5E), Color(0xFF7BE07B)))
        "fn" -> Brush.linearGradient(listOf(Color.White.copy(alpha = .7f), Color.White.copy(alpha = .3f)))
        else -> Brush.linearGradient(listOf(Color.White.copy(alpha = .85f), Color.White.copy(alpha = .4f)))
    }
    val textColor = when (kind) { "op" -> Color.White; "eq" -> Color(0xFF23431A); else -> Color(0xFF241446) }
    Box(
        modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = if (kind == "op" || kind == "eq") 22.sp else 19.sp, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

@Composable
fun FlowKeyGrid(columns: Int, items: List<Pair<String, String>>, height: androidx.compose.ui.unit.Dp,
                 fontSize: androidx.compose.ui.unit.TextUnit, bg: Color, onClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, code) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(height)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .clickable { onClick(code) },
                        contentAlignment = Alignment.Center
                    ) { Text(label, fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = Color(0xFF241446)) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun BoxScope.HistorySheet(hist: List<Pair<String, String>>, accent: Color, onPick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .align(Alignment.BottomCenter)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xFFF4EEF6))
            .padding(16.dp)
    ) {
        Text("History", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = accent)
        Spacer(Modifier.height(8.dp))
        if (hist.isEmpty()) {
            Text("Your calculations will show up here.", color = Color.Gray, modifier = Modifier.padding(top = 40.dp))
        } else {
            LazyColumn {
                items(hist) { (expr, res) ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(res) }
                            .padding(10.dp, 8.dp)
                    ) {
                        Text(expr, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                        Text("= $res", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.PickerSheet(title: String, options: List<String>, accent: Color, onPick: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.6f)
            .align(Alignment.BottomCenter)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xFFF4EEF6))
            .padding(16.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accent)
        Spacer(Modifier.height(10.dp))
        LazyColumn {
            items(options.size) { idx ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(idx) }
                        .padding(12.dp)
                ) { Text(options[idx], fontSize = 16.sp) }
            }
        }
    }
}


