package com.mantra.spar

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * WHERE THE STORE LIST COMES FROM, in the order it is believed:
 *
 *   1  spar.hr, asked from the phone. The list behind https://www.spar.hr/lokacije/ answers a plain
 *      request (measured 1.11.2026: 200, 617 kB, with this app's own name as the user agent).
 *   2  the last answer that was the store list, kept in the app's files.
 *   3  the list built into the APK (assets/spar_stores.json, read 1.11.2026), so the first start
 *      without a signal still has every store.
 *
 * spar.hr sits behind Cloudflare. If one day it answers with its "Just a moment" page instead, the
 * page does not parse as a store list and the kept list stays on the screen: an answer is only
 * kept when it holds at least [Stores.ENOUGH] stores.
 */
class Source(private val context: Context) {

    enum class Origin { LIVE, SAVED, BUILT_IN }

    data class Loaded(val stores: List<Store>, val origin: Origin, val at: Long)

    private val saved = File(context.filesDir, ASSET)

    /** What is on the phone already: the kept answer, else the built-in one. Never the network. */
    fun local(): Loaded {
        if (saved.exists()) {
            runCatching { Stores.parse(saved.readText()) }.getOrNull()
                ?.takeIf { it.size >= Stores.ENOUGH }
                ?.let { return Loaded(it, Origin.SAVED, saved.lastModified()) }
        }
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        return Loaded(Stores.parse(text), Origin.BUILT_IN, BUILT_IN_AT)
    }

    /**
     * spar.hr now, or null with the reason kept in [lastProblem]. Blocking: call it off the main
     * thread.
     */
    fun live(): Loaded? {
        val text = try {
            fetch()
        } catch (e: java.io.IOException) {
            lastProblem = e.message ?: e.javaClass.simpleName
            return null
        }
        val stores = runCatching { Stores.parse(text) }.getOrNull()
        if (stores == null || stores.size < Stores.ENOUGH) {
            lastProblem = "${BuildConfig.SITE} did not send the store list"
            return null
        }
        saved.writeText(text)
        lastProblem = null
        return Loaded(stores, Origin.LIVE, System.currentTimeMillis())
    }

    @Volatile
    var lastProblem: String? = null
        private set

    private fun fetch(): String {
        val open = (URL(URL_STORES).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = open.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw java.io.IOException("${BuildConfig.SITE} answered $code")
            return open.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            open.disconnect()
        }
    }

    companion object {
        // Each shop's own, from its flavour in app/build.gradle.kts (spar: spar.hr's list; dm: the
        // store service behind dm.hr's store finder, Croatia only).
        const val URL_STORES = BuildConfig.STORES_URL
        const val ASSET = BuildConfig.STORES_FILE

        /** When the built-in list was read (spar 1.11.2026 17:43, dm 2.11.2026), in ms. */
        const val BUILT_IN_AT = BuildConfig.BUILT_IN_AT

        val USER_AGENT = "${BuildConfig.AGENT}/${BuildConfig.VERSION_NAME} (+https://github.com/markoboskoauroville/spar_locator)"

        /** A list older than this is asked for again when the app comes back to the front. */
        const val STALE_MS = 3L * 60 * 60 * 1000
    }
}
