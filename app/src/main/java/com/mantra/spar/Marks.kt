package com.mantra.spar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint as Brush
import android.graphics.RectF
import android.graphics.Typeface

/**
 * A STORE ON THE MAP: a circle, green when it is open and red when it is shut, and above it the
 * two numbers of the day ("7–21"), on a pill of the same colour. Zoomed out the label is left off,
 * so a hundred labels do not cover Croatia.
 *
 * Only a handful of different pictures exist (open or shut, big or small, label or none, and the
 * few hours Croatian shops keep), so each is drawn once and kept.
 */
class Marks(private val density: Float) {

    /** Where the store's point is in the picture, as a fraction of its width and height. */
    data class Mark(val bitmap: Bitmap, val x: Float, val y: Float)

    private val kept = HashMap<String, Mark>()

    private fun dp(v: Float) = v * density

    fun mark(open: Boolean, big: Boolean, label: String?): Mark =
        kept.getOrPut("$open|$big|$label") { draw(open, big, label) }

    private fun draw(open: Boolean, big: Boolean, label: String?): Mark {
        val fill = if (open) GREEN else RED
        val radius = dp(if (big) 10f else 7.5f)
        val ring = dp(2f)

        val text = Brush(Brush.ANTI_ALIAS_FLAG).apply {
            color = GROUND
            textSize = dp(12f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val padX = dp(5f)
        val padY = dp(2.5f)
        val gap = dp(3f)
        val textW = if (label != null) text.measureText(label) else 0f
        val metrics = text.fontMetrics
        val textH = metrics.descent - metrics.ascent
        val pillW = if (label != null) textW + 2 * padX else 0f
        val pillH = if (label != null) textH + 2 * padY else 0f

        val circle = 2 * (radius + ring)
        val width = maxOf(pillW, circle).toInt() + 2
        val height = (pillH + (if (label != null) gap else 0f) + circle).toInt() + 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = width / 2f
        val cy = height - 1 - radius - ring

        if (label != null) {
            val pill = RectF(cx - pillW / 2, 1f, cx + pillW / 2, 1f + pillH)
            val body = Brush(Brush.ANTI_ALIAS_FLAG).apply { color = fill }
            val edge = Brush(Brush.ANTI_ALIAS_FLAG).apply {
                color = GROUND
                style = Brush.Style.STROKE
                strokeWidth = dp(1f)
            }
            canvas.drawRoundRect(pill, dp(5f), dp(5f), body)
            canvas.drawRoundRect(pill, dp(5f), dp(5f), edge)
            canvas.drawText(label, cx - textW / 2, pill.top + padY - metrics.ascent, text)
        }
        canvas.drawCircle(cx, cy, radius + ring, Brush(Brush.ANTI_ALIAS_FLAG).apply { color = GROUND })
        canvas.drawCircle(cx, cy, radius, Brush(Brush.ANTI_ALIAS_FLAG).apply { color = fill })
        return Mark(bitmap, cx / width, cy / height)
    }

    /** How far above and below its point a store's picture reaches, in pixels, for taps. */
    fun reachUp(label: Boolean): Float = dp(if (label) 36f else 14f)
    fun reachDown(): Float = dp(14f)
    fun reachSide(): Float = dp(28f)

    companion object {
        // The Trail colours (Palette.kt): green and red are the two states, the ground is the ink.
        const val GREEN = 0xFF34D399.toInt()
        const val RED = 0xFFEF4444.toInt()
        const val GROUND = 0xFF0B0D10.toInt()
    }
}
