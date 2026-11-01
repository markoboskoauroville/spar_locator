package com.mantra.spar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * THE SCREEN, in the Trail language (MANTRA_MANIFEST/modules/language-trail.md): the map is the
 * screen; one line of numbers on top; one key row of icons on a firm bar at the bottom; panels
 * float over a veil, swallow their own touches, and close with the ✕ at the end of their title row.
 * Every control is one of Look.kt's kinds.
 */

/** Which face is over the map. */
sealed interface Face {
    data object None : Face
    data object City : Face
    data class Shop(val store: Store) : Face
}

/** The repository's latest build: the version row's hidden link (version-visible.md §3). */
const val LATEST_BUILD = "https://github.com/markoboskoauroville/spar_locator/releases/latest"

/** The firm bar under the key row: ground at 86 % (Trail v92: thin bars lose their icons). */
private val Firm = Color(0xDB0B0D10)

private val Mono = FontFamily.Monospace

@Composable
private fun Line(
    text: String,
    colour: Color,
    modifier: Modifier = Modifier,
    size: Int = 14,
    bold: Boolean = false,
    lines: Int = 1,
) {
    Text(
        text,
        color = colour,
        fontSize = size.sp,
        fontFamily = Mono,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** A touch that lands here goes nowhere else (Trail v89: a press on a panel opened the map below). */
private fun Modifier.swallow(): Modifier = this.pointerInput(Unit) { detectTapGestures { } }

/** Everything drawn over the map, kept clear of the bars and the camera hole (system-bars.md). */
@Composable
fun Overlay(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        content = content,
    )
}

/** THE LINE OF NUMBERS: where, how many are open of how many, and the time in Zagreb. */
@Composable
fun BoxScope.TopLine(place: String, open: Int, total: Int, now: LocalDateTime) {
    Text(
        "$place  $open/$total open  ${now.format(CLOCK)}",
        style = TextStyle(
            color = Paint.Sand,
            fontSize = 14.sp,
            fontFamily = Mono,
            fontWeight = FontWeight.SemiBold,
            shadow = Shadow(Color.Black, blurRadius = 4f),
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .clip(RoundedCornerShape(8.dp))
            .background(Paint.Bar)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** THE KEY ROW: icons only, one per key, lit keys amber. */
@Composable
fun BoxScope.KeyRow(cityOpen: Boolean, onZoomOut: () -> Unit, onCity: () -> Unit, onZoomIn: () -> Unit) {
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Firm)
            .swallow()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Key(R.drawable.ic_minus, lit = false, onZoomOut)
        Key(R.drawable.ic_pin, lit = cityOpen, onCity)
        Key(R.drawable.ic_plus, lit = false, onZoomIn)
    }
}

@Composable
private fun Key(@DrawableRes icon: Int, lit: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(icon, if (lit) Paint.AmberBright else Paint.Sand, size = 26.dp)
    }
}

/**
 * A PANEL: over a veil, rounded 16, its title row (icon, title, ✕ at the end), then its content.
 * The only place the ✕ is drawn, so every panel closes the same way (verify.py checks).
 */
@Composable
fun Panel(
    title: String,
    @DrawableRes icon: Int?,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Paint.Veil).swallow()) {
        Overlay {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Paint.Card)
                    .swallow()
                    .padding(vertical = 8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (icon != null) Glyph(icon, Paint.Sand)
                    Line(title, Paint.Sand, size = 17, bold = true, lines = 2, modifier = Modifier.weight(1f))
                    IconAction(R.drawable.ic_close, null, onClose, tint = Paint.Sand)
                }
                content()
            }
        }
    }
}

/** One city in the list, with how many of its stores are open now. */
data class CityRow(val name: String?, val stores: Int, val open: Int)

/** THE CITY: pick where you are. "All of Croatia" first, then every city A to Ž. */
@Composable
fun CityPanel(
    rows: List<CityRow>,
    chosen: String?,
    origin: String,
    version: String,
    onPick: (String?) -> Unit,
    onVersion: () -> Unit,
    onClose: () -> Unit,
) {
    Panel("City", R.drawable.ic_pin, onClose) {
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(rows, key = { it.name ?: "" }) { row ->
                Pick(
                    title = row.name ?: "All of Croatia",
                    icon = if (row.name == null) R.drawable.ic_globe else null,
                    chosen = row.name == chosen,
                    under = "${row.stores} · ${row.open} open",
                    onClick = { onPick(row.name) },
                )
                Hairline(inset = 24.dp)
            }
        }
        // Where the list came from, and the version: small, dim, the version a hidden link.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Line(origin, Paint.Dim, size = 12, modifier = Modifier.weight(1f))
            Line(version, Paint.Dim, size = 12, modifier = Modifier.clickable(onClick = onVersion).padding(8.dp))
        }
    }
}

/** A STORE: where it is, this week's hours with today bright, and the way there. */
@Composable
fun StorePanel(store: Store, now: LocalDateTime, onRoute: () -> Unit, onClose: () -> Unit) {
    val today = now.toLocalDate()
    Panel(store.name, null, onClose) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Line("${store.address}, ${store.zip} ${store.city}", Paint.Dim, size = 13, lines = 2)
                    if (store.phone.isNotBlank()) Line(store.phone, Paint.Dim, size = 13)
                }
            }
            Stores.closedFor(store, today)?.let { c ->
                val why = if (c.info.isNotBlank()) " · ${c.info}" else ""
                Line("Closed$why · until ${c.to.format(DAY)}", Paint.Red, size = 13, bold = true)
            }
            Spacer(Modifier.size(6.dp))
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            for (i in 0 until 7) {
                val date = monday.plusDays(i.toLong())
                val isToday = date == today
                val ink = if (isToday) Paint.Sand else Paint.Dim
                Row(Modifier.fillMaxWidth().heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                    Line(DAY_NAMES[i], ink, bold = isToday, modifier = Modifier.width(44.dp))
                    Line(date.format(DAY), ink, bold = isToday, modifier = Modifier.width(72.dp))
                    Line(Stores.label(Stores.hoursOn(store, date)), ink, bold = isToday)
                }
            }
        }
        Action("Route", R.drawable.ic_route, onRoute, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M.")
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
