package com.mantra.spar

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
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
 * SPAR LOCATOR. Every SPAR in Croatia is a circle on OpenStreetMap, green when it is open now and
 * red when it is shut, with today's opening and closing hours above it. The pin key shows where the
 * phone is (v3, replacing the City list). Open and shut are Croatian time, recomputed every minute.
 */
class MainActivity : ComponentActivity() {

    private lateinit var source: Source
    private lateinit var map: SparMap

    private var loaded by mutableStateOf<Source.Loaded?>(null)
    private var now by mutableStateOf(LocalDateTime.now(Stores.ZONE))
    private var locating by mutableStateOf(false)
    private var face by mutableStateOf<Face>(Face.None)
    private var fetching = false
    private var lastTry = 0L

    /** The location permission, asked the first time the pin is pressed and never before. */
    private val ask = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) locate() else say("Location not allowed")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge to edge: the map reaches the glass; Overlay pads every control back inside.
        enableEdgeToEdge()
        source = Source(this)
        map = SparMap(this) { store -> face = Face.Shop(store) }

        // What is on the phone at once, so the first frame already has every store; spar.hr after.
        val local = source.local()
        loaded = local
        map.show(local.stores, now)
        map.fit(local.stores)
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

    /** spar.hr, off the main thread. A failure keeps what is shown and says so in the store panel. */
    private fun refresh() {
        if (fetching) return
        fetching = true
        lastTry = System.currentTimeMillis()
        lifecycleScope.launch {
            val fresh = withContext(Dispatchers.IO) { source.live() }
            fetching = false
            if (fresh != null) {
                loaded = fresh
                map.show(fresh.stores, now)
            }
        }
    }

    private fun stores(): List<Store> = loaded?.stores.orEmpty()

    private fun allowed(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** THE PIN KEY: where am I. The permission first, if it was never given. */
    private fun pin() {
        face = Face.None
        if (allowed()) locate()
        else ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    /**
     * WHERE AM I, without Google Play: the last place the phone knew is shown at once, then a fresh
     * fix replaces it (fused if the phone has it, else GPS, else the network; the compat call gives
     * up after 30 s on its own). The permission is checked here, in the same function as the calls
     * that need it, so lint sees it.
     */
    private fun locate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return
        val lm = getSystemService(LocationManager::class.java) ?: return
        val on = lm.getProviders(true)
        var last: Location? = null
        try {
            for (p in on) {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (last == null || l.time > last.time) last = l
            }
        } catch (e: SecurityException) {
            say("Location not allowed")
            return
        }
        last?.let { map.showMe(it.latitude, it.longitude) }
        val provider = listOf(FUSED, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { it in on }
        if (provider == null) {
            if (last == null) {
                say("Location is off")
                open(Settings.ACTION_LOCATION_SOURCE_SETTINGS, intent = true)
            }
            return
        }
        locating = true
        val hadLast = last != null
        try {
            // A real signal, not null: core 1.15 has two overloads and null matches both.
            LocationManagerCompat.getCurrentLocation(lm, provider, CancellationSignal(), ContextCompat.getMainExecutor(this)) { fix ->
                locating = false
                if (fix != null) map.showMe(fix.latitude, fix.longitude)
                else if (!hadLast) say("No location yet")
            }
        } catch (e: SecurityException) {
            locating = false
            say("Location not allowed")
        }
    }

    private fun say(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun origin(): String {
        val l = loaded ?: return ""
        val at = Instant.ofEpochMilli(l.at).atZone(Stores.ZONE).toLocalDateTime().format(STAMP)
        val said = when (l.origin) {
            Source.Origin.LIVE -> "${BuildConfig.SITE} $at"
            Source.Origin.SAVED -> "saved $at"
            Source.Origin.BUILT_IN -> "built in $at"
        }
        return if (l.origin != Source.Origin.LIVE && source.lastProblem != null) "$said · offline" else said
    }

    private fun open(url: String, intent: Boolean = false) {
        try {
            startActivity(if (intent) Intent(url) else Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
        BackHandler(enabled = face != Face.None) { face = Face.None }
        Box(Modifier.fillMaxSize()) {
            AndroidView(factory = { map.view }, modifier = Modifier.fillMaxSize())
            Overlay {
                TopLine("Croatia", Stores.openCount(all, now), all.size, now)
                KeyRow(
                    locating = locating,
                    onZoomOut = { map.zoomOut() },
                    onPin = ::pin,
                    onZoomIn = { map.zoomIn() },
                )
            }
            when (val f = face) {
                Face.None -> Unit
                is Face.Shop -> StorePanel(
                    f.store,
                    now,
                    origin = origin(),
                    version = "v${BuildConfig.VERSION_NAME}",
                    onRoute = { route(f.store) },
                    onVersion = { open(LATEST_BUILD) },
                    onClose = { face = Face.None },
                )
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
        /** LocationManager.FUSED_PROVIDER, written out: the constant is Android 12's, the app runs from 8. */
        const val FUSED = "fused"
        const val RETRY_MS = 10L * 60 * 1000
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M. HH:mm")
    }
}
