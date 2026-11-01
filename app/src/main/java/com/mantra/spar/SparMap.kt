package com.mantra.spar

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import org.oscim.android.MapView
import org.oscim.android.canvas.AndroidBitmap
import org.oscim.core.BoundingBox
import org.oscim.core.GeoPoint
import org.oscim.core.MapPosition
import org.oscim.core.MercatorProjection
import org.oscim.core.Tile
import org.oscim.layers.marker.ItemizedLayer
import org.oscim.layers.marker.MarkerInterface
import org.oscim.layers.marker.MarkerItem
import org.oscim.layers.marker.MarkerSymbol
import org.oscim.layers.tile.bitmap.BitmapTileLayer
import org.oscim.map.Map
import org.oscim.renderer.MapRenderer
import org.oscim.tiling.source.bitmap.BitmapTileSource
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * THE MAP: OpenStreetMap's tiles drawn by VTM (as in Mantra ARKOD), and over them one circle per
 * store with the day's hours above it. Every VTM call here was read from the 0.25.0 jar with javap
 * before it was written (1.11.2026).
 *
 * Lessons taken from Mantra ARKOD's VtmCanvas.kt, each of which cost it a build:
 *  - taps are read by Android's own GestureDetector beside the map: a layer listening for VTM's tap
 *    gesture was never called on the emulator;
 *  - the first layer is put on before the view has a size, so nothing is fetched until a pan: the
 *    view's first real size asks for the tiles again ([load]);
 *  - only what this class added is ever removed from map.layers(): VTM keeps its own layers there.
 */
class SparMap(context: Context, private val onTap: (Store) -> Unit) {

    val view: MapView = MapView(context)
    private val map: Map get() = view.map()
    private val marks = Marks(context.resources.displayMetrics.density)
    private var storeLayer: ItemizedLayer? = null

    private var stores: List<Store> = emptyList()
    private var now: LocalDateTime = LocalDateTime.now(Stores.ZONE)
    private var labels = false

    /** A fly-to asked for before the view had a size, done at its first layout. */
    private var pendingFit: List<Store>? = null

    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            storeAt(e.x, e.y)?.let(onTap)
            return false
        }
    })

    init {
        // The ground behind the tiles before they arrive: the app's own dark, not VTM's light grey.
        MapRenderer.setBackgroundColor(Marks.GROUND)
        map.setMapPosition(MapPosition(44.6, 16.4, (1 shl 7).toDouble()))
        // A store map is read north up: no turning, no tilting, so a circle is where it looks.
        map.eventLayer.enableRotation(false)
        map.eventLayer.enableTilt(false)

        val osm = BitmapTileSource.builder()
            .url("https://tile.openstreetmap.org")
            .tilePath("/{Z}/{X}/{Y}.png")
            .httpFactory(TileHttp.Factory())
            .zoomMin(1)
            .zoomMax(19)
            .build()
        map.layers().add(BitmapTileLayer(map, osm))

        @Suppress("ClickableViewAccessibility")
        view.setOnTouchListener { _, event ->
            taps.onTouchEvent(event)
            false
        }
        view.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val w = right - left
            val h = bottom - top
            if (w > 0 && h > 0 && (w != oldRight - oldLeft || h != oldBottom - oldTop)) view.post {
                load()
                pendingFit?.let { pendingFit = null; fit(it) }
            }
        }
        // Zoomed in far enough, the hours appear above the circles; zoomed out, only the circles.
        map.events.bind(Map.UpdateListener { _, position ->
            val want = position.zoomLevel >= LABEL_ZOOM
            if (want != labels) {
                labels = want
                view.post { draw() }
            }
        })
    }

    /** Ask for the tiles of what is on the map now (Mantra ARKOD, the black map of v1). */
    private fun load() {
        map.clearMap()
        map.updateMap(true)
    }

    /** The stores and the time to colour them by. Redraws only the circles, never the tiles. */
    fun show(stores: List<Store>, now: LocalDateTime) {
        this.stores = stores
        this.now = now
        draw()
    }

    private fun draw() {
        val date = now.toLocalDate()
        // Shut stores first, so an open one is drawn on top where two touch.
        val items = stores
            .map { it to Stores.isOpen(it, now) }
            .sortedBy { it.second }
            .map { (store, open) ->
                val label = if (labels) Stores.label(Stores.hoursOn(store, date)) else null
                val mark = marks.mark(open, store.big, label)
                MarkerItem(store.id, store.name, "", GeoPoint(store.lat, store.lon)).apply {
                    marker = MarkerSymbol(AndroidBitmap(mark.bitmap), mark.x, mark.y, true)
                }
            }
        storeLayer?.let { map.layers().remove(it) }
        val fallback = marks.mark(open = false, big = false, label = null)
        val layer = ItemizedLayer(
            map,
            items.toMutableList<MarkerInterface>(),
            MarkerSymbol(AndroidBitmap(fallback.bitmap), fallback.x, fallback.y, true),
            null,
        )
        map.layers().add(layer)
        storeLayer = layer
        map.updateMap(true)
    }

    /**
     * The store under a finger: the nearest whose circle or label covers the point. Distances are
     * worked in VTM's own projection (x and y from 0 to 1 across the world), times the world's
     * size in pixels at this scale; the map never turns (rotation is off), so screen and map agree.
     */
    private fun storeAt(x: Float, y: Float): Store? {
        val position = map.mapPosition
        val world = Tile.SIZE * position.scale
        val cx = view.width / 2f
        val cy = view.height / 2f
        val up = marks.reachUp(labels)
        val down = marks.reachDown()
        val side = marks.reachSide()
        return stores
            .map { s ->
                val sx = cx + ((MercatorProjection.longitudeToX(s.lon) - position.x) * world).toFloat()
                val sy = cy + ((MercatorProjection.latitudeToY(s.lat) - position.y) * world).toFloat()
                s to (x - sx to y - sy)
            }
            .filter { (_, d) -> abs(d.first) <= side && d.second >= -up && d.second <= down }
            .minByOrNull { (_, d) -> d.first * d.first + d.second * d.second }
            ?.first
    }

    /** Fly to some stores: the box around them, with room, or close in on a single one. */
    fun fit(some: List<Store>) {
        if (view.width == 0 || view.height == 0) {
            pendingFit = some
            return
        }
        val box = Stores.bounds(some) ?: return
        if (some.size == 1 || (box[2] - box[0] < 0.004 && box[3] - box[1] < 0.004)) {
            map.animator().animateTo(600, MapPosition((box[0] + box[2]) / 2, (box[1] + box[3]) / 2, (1 shl 15).toDouble()))
            return
        }
        // Margins for the line of numbers on top and the key row below.
        val padLat = (box[2] - box[0]) * 0.25
        val padLon = (box[3] - box[1]) * 0.15
        map.animator().animateTo(600, BoundingBox(box[0] - padLat, box[1] - padLon, box[2] + padLat, box[3] + padLon))
    }

    fun zoomIn() = map.animator().animateZoom(200, 2.0, 0f, 0f)

    fun zoomOut() = map.animator().animateZoom(200, 0.5, 0f, 0f)

    fun resume() = view.onResume()

    fun pause() = view.onPause()

    fun destroy() {
        runCatching { view.onDestroy() }
    }

    companion object {
        /** From this zoom (about a city's width on a phone) the hours are written above the circles. */
        const val LABEL_ZOOM = 12
    }
}
