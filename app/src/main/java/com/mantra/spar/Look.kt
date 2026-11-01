package com.mantra.spar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * THE VISUAL LANGUAGE (27.9.2026).
 *
 * Marko, with the route menu and the settings in two screenshots: *"everything is the same and user
 * is confused where to click. What is option? ... Compass on the map, off, press to change ... only
 * one word needs to be there ... make visual distinction and design cues. What are the toggle
 * buttons? What are the action buttons? ... minimal words, more with symbols."*
 *
 * So every control on every screen is one of these, and each kind looks like nothing else:
 *
 *   ACTION        solid amber, dark icon and verb. The only solid amber thing: amber means "do".
 *   ACTION, QUIET amber icon and amber word on the card, for the second things a panel does.
 *   TOGGLE        a switch, amber when on, and one noun. The switch is the state; no sentence.
 *   CHOICE        one bar split into parts; the chosen part raised and bright, never amber.
 *   FLIP          a choice of two, one bar; a tap anywhere turns it to the other.
 *   OPENS         a row with its icon and a chevron: it goes somewhere.
 *
 * Icons are drawn from design/icons/<name>.svg (design/make_icons.py), one line style.
 */
object Look {
    /** The raised face of a chosen part: one step up from the card, neutral, never amber. */
    val Raised = Color(0xFF2C323A)
    val Outline = Color(0xFF2E343B)
}

@Composable
fun Glyph(@DrawableRes icon: Int, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

@Composable
private fun Word(text: String, colour: Color, size: Int = 15, bold: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text,
        color = colour,
        fontSize = size.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** ACTION: solid amber, the icon and the verb. [quiet] for a panel's second things. */
@Composable
fun Action(
    verb: String,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    quiet: Boolean = false,
    danger: Boolean = false,
    trailing: String? = null,
) {
    val ink = when {
        !enabled -> Paint.Dim
        quiet && danger -> Paint.Red
        quiet -> Paint.Amber
        else -> Paint.Ground
    }
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (quiet) Color.Transparent else if (enabled) Paint.Amber else Look.Raised)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Glyph(icon, ink)
        // With something trailing, the verb takes the room so the trailing word sits at the far
        // edge (a shared weight left "1.1 GB" floating in the middle, 27.9.2026).
        Word(verb, ink, size = 16, bold = !quiet, modifier = if (trailing != null) Modifier.weight(1f) else Modifier)
        if (trailing != null) Word(trailing, if (quiet) Paint.Dim else ink.copy(alpha = 0.75f), size = 13)
    }
}

/** The switch itself: a track and a thumb. Amber track when on; an empty track when off. */
@Composable
fun SwitchMark(on: Boolean) {
    Box(
        Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (on) Paint.Amber else Color.Transparent)
            .border(1.8.dp, if (on) Paint.Amber else Paint.Dim, CircleShape)
            .padding(4.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(if (on) Paint.Ground else Paint.Dim))
    }
}

/** TOGGLE: an icon, one noun, the switch. The whole row flips it. */
@Composable
fun Toggle(word: String, @DrawableRes icon: Int?, on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onChange(!on) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Glyph(icon, if (on) Paint.Sand else Paint.Dim)
        Word(word, if (on) Paint.Sand else Paint.Dim, size = 16, modifier = Modifier.weight(1f))
        SwitchMark(on)
    }
}

/** One part of a [Choice]: a word, an icon, or both. */
data class Part(val word: String? = null, @DrawableRes val icon: Int? = null)

/**
 * CHOICE: one bar, its parts side by side, the chosen one raised and bright. [flip] makes a choice
 * of two into one control: a tap anywhere on it turns it to the other side.
 */
@Composable
fun Choice(parts: List<Part>, chosen: Int, onChoose: (Int) -> Unit, modifier: Modifier = Modifier, flip: Boolean = false) {
    Row(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Paint.Card)
            .border(1.dp, Look.Outline, RoundedCornerShape(12.dp))
            .then(if (flip) Modifier.clickable { onChoose(if (chosen == 0) 1 else 0) } else Modifier)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        parts.forEachIndexed { i, part ->
            val on = i == chosen
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) Look.Raised else Color.Transparent)
                    .then(if (flip) Modifier else Modifier.clickable { onChoose(i) }),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                part.icon?.let { Glyph(it, if (on) Paint.Sand else Paint.Dim, size = 20.dp) }
                if (part.icon != null && part.word != null) Spacer(Modifier.width(6.dp))
                part.word?.let { Word(it, if (on) Paint.Sand else Paint.Dim, size = 14, bold = on) }
            }
        }
    }
}

/** A choice with its name in front of it, the name small and dim: "options  [1|2|3|4|5]". */
@Composable
fun Labelled(name: String, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Word(name, Paint.Dim, size = 12, modifier = Modifier.width(64.dp))
        content()
    }
}

/** OPENS: an icon, a title, what it holds in dim under it, and the chevron. */
@Composable
fun Opens(title: String, @DrawableRes icon: Int?, under: String? = null, onClick: () -> Unit, open: Boolean? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Glyph(icon, Paint.Sand)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Word(title, Paint.Sand, size = 16)
            if (!under.isNullOrBlank()) Word(under, Paint.Dim, size = 12)
        }
        Glyph(if (open == true) R.drawable.ic_chevron_down else R.drawable.ic_chevron, Paint.Dim, size = 20.dp)
    }
}

/** A row that is one of a list's choices: its name, and a check when it is the chosen one. */
@Composable
fun Pick(title: String, @DrawableRes icon: Int?, chosen: Boolean, under: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Glyph(icon, if (chosen) Paint.Sand else Paint.Dim, size = 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Word(title, if (chosen) Paint.Sand else Paint.Dim, size = 15, bold = chosen)
            if (!under.isNullOrBlank()) Word(under, Paint.Dim, size = 12)
        }
        if (chosen) Glyph(R.drawable.ic_check, Paint.Sand, size = 20.dp)
    }
}

/** An icon alone that does one thing, with its short name under it: close, copy, text. */
@Composable
fun IconAction(@DrawableRes icon: Int, name: String?, onClick: () -> Unit, tint: Color = Paint.Amber) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(icon, tint, size = 24.dp)
        if (name != null) Word(name, tint, size = 10)
    }
}

/** A thin rule between rows of a card. */
@Composable
fun Hairline(inset: Dp = 52.dp) {
    Box(Modifier.fillMaxWidth().padding(start = inset).height(1.dp).background(Paint.Rule))
}
