package com.mantra.spar

import org.oscim.core.Tile
import org.oscim.tiling.source.HttpEngine
import org.oscim.tiling.source.UrlTileSource
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * FETCHING A TILE WITH THE STACK THAT IS KNOWN TO WORK. Copied from Mantra ARKOD's TileHttp.kt
 * (17.9.2026), with the cadastre and Google taken out: VTM's own client cannot do https and its
 * OkHttp engine did not draw a tile either, so tiles go through java.net.HttpURLConnection.
 *
 * OpenStreetMap's tile usage policy: the app names itself ([Source.USER_AGENT]) and never
 * pre-fetches.
 */
class TileHttp(private val source: UrlTileSource) : HttpEngine {

    private var connection: HttpURLConnection? = null
    private var stream: InputStream? = null

    override fun sendRequest(tile: Tile?) {
        if (tile == null) throw java.io.IOException("no tile asked for")
        val open = (URL(source.getTileUrl(tile)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", Source.USER_AGENT)
            instanceFollowRedirects = true
        }
        connection = open
        val code = open.responseCode
        if (code != HttpURLConnection.HTTP_OK) throw java.io.IOException("tile answered $code")
        stream = open.inputStream
    }

    override fun read(): InputStream = stream ?: throw java.io.IOException("nothing was fetched")

    override fun close() {
        runCatching { stream?.close() }
        runCatching { connection?.disconnect() }
        stream = null
        connection = null
    }

    override fun setCache(os: OutputStream?) = Unit

    override fun requestCompleted(success: Boolean): Boolean {
        close()
        return success
    }

    class Factory : HttpEngine.Factory {
        override fun create(tileSource: UrlTileSource): HttpEngine = TileHttp(tileSource)
    }
}
