package com.mantra.spar

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * SPAR LOCATOR. Choose the city you are in; every SPAR there is a circle on OpenStreetMap, green
 * when it is open now and red when it is shut, with today's opening and closing hours above it.
 * Open and shut are Croatian time, recomputed on every minute.
 */
class MainActivity : ComponentActivity() {

    private lateinit var source: Source
    private lateinit var map: SparMap

    private var loaded by mutableStateOf<Source.Loaded?>(null)
    private var now by mutableStateOf(LocalDateTime.now(Stores.ZONE))
    private var city by mutableStateOf<String?>(null)
    private var face by mutableStateOf<Face>(Face.None)
    private var fetching = false
    private var lastTry = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge to edge: the map reaches the glass; Overlay pads every control back inside.
        enableEdgeToEdge()
        source = Source(this)
        city = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_CITY, null)
        map = SparMap(this) { store -> face = Face.Shop(store) }

        // What is on the phone at once, so the first frame already has every store; spar.hr after.
        val local = source.local()
        loaded = local
        if (city != null && local.stores.none { it.city == city }) city = null
        map.show(local.stores, now)
        map.fit(shown())
        refresh()

        // THE CLOCK: on every minute, while the app is in front. The one while(true) in the app,
        // named in the workflow's G5: repeatOnLifecycle cancels it when the app leaves the front.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    tick()
                    val second = LocalDateTime.now(Stores.ZONE).second
                    delay((61 - second) * 1000L)
                }
            }
        }

        setContent { Screen() }
    }

    private fun tick() {
        now = LocalDateTime.now(Stores.ZONE)
        loaded?.let { map.show(it.stores, now) }
        val stale = loaded?.origin != Source.Origin.LIVE || System.currentTimeMillis() - (loaded?.at ?: 0) > Source.STALE_MS
        // Not every minute when spar.hr cannot be reached: the phone may be on a SIM.
        if (stale && System.currentTimeMillis() - lastTry > RETRY_MS) refresh()
    }

    /** spar.hr, off the main thread. A failure keeps what is shown and says so in the city panel. */
    private fun refresh() {
        if (fetching) return
        fetching = true
        lastTry = System.currentTimeMillis()
        lifecycleScope.launch {
            val fresh = withContext(Dispatchers.IO) { source.live() }
            fetching = false
            if (fresh != null) {
                loaded = fresh
                if (city != null && fresh.stores.none { it.city == city }) city = null
                map.show(fresh.stores, now)
            }
        }
    }

    private fun stores(): List<Store> = loaded?.stores.orEmpty()

    private fun shown(): List<Store> = city?.let { c -> stores().filter { it.city == c } } ?: stores()

    private fun choose(c: String?) {
        city = c
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_CITY, c).apply()
        face = Face.None
        map.fit(shown())
    }

    private fun origin(): String {
        val l = loaded ?: return ""
        val at = Instant.ofEpochMilli(l.at).atZone(Stores.ZONE).toLocalDateTime().format(STAMP)
        val said = when (l.origin) {
            Source.Origin.LIVE -> "spar.hr $at"
            Source.Origin.SAVED -> "saved $at"
            Source.Origin.BUILT_IN -> "built in $at"
        }
        return if (l.origin != Source.Origin.LIVE && source.lastProblem != null) "$said · offline" else said
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No app can open $url", Toast.LENGTH_LONG).show()
        }
    }

    private fun route(store: Store) {
        val name = Uri.encode("${store.name}, ${store.address}")
        open("geo:${store.lat},${store.lon}?q=${store.lat},${store.lon}($name)")
    }

    @androidx.compose.runtime.Composable
    private fun Screen() {
        val all = stores()
        val here = shown()
        BackHandler(enabled = face != Face.None) { face = Face.None }
        Box(Modifier.fillMaxSize()) {
            AndroidView(factory = { map.view }, modifier = Modifier.fillMaxSize())
            Overlay {
                TopLine(city ?: "Croatia", Stores.openCount(here, now), here.size, now)
                KeyRow(
                    cityOpen = face == Face.City,
                    onZoomOut = { map.zoomOut() },
                    onCity = { face = if (face == Face.City) Face.None else Face.City },
                    onZoomIn = { map.zoomIn() },
                )
            }
            when (val f = face) {
                Face.None -> Unit
                Face.City -> CityPanel(
                    rows = listOf(CityRow(null, all.size, Stores.openCount(all, now))) +
                        Stores.cities(all).map { c ->
                            CityRow(c.name, c.stores, Stores.openCount(all.filter { it.city == c.name }, now))
                        },
                    chosen = city,
                    origin = origin(),
                    version = "v${BuildConfig.VERSION_NAME}",
                    onPick = ::choose,
                    onVersion = { open(LATEST_BUILD) },
                    onClose = { face = Face.None },
                )
                is Face.Shop -> StorePanel(f.store, now, onRoute = { route(f.store) }, onClose = { face = Face.None })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        map.resume()
    }

    override fun onPause() {
        map.pause()
        super.onPause()
    }

    override fun onDestroy() {
        map.destroy()
        super.onDestroy()
    }

    private companion object {
        const val PREFS = "spar"
        const val KEY_CITY = "city"
        const val RETRY_MS = 10L * 60 * 1000
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M. HH:mm")
    }
}
