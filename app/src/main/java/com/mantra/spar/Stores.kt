package com.mantra.spar

import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * THE STORES AND THEIR HOURS. This file imports nothing from Android (android-app.md §1), so
 * Test 1 runs it on a desk against the real answer from spar.hr, and verify.py fails the build
 * the day an Android import appears here.
 *
 * The source is the list behind https://www.spar.hr/lokacije/ : every SPAR and INTERSPAR in
 * Croatia, each with its weekly hours, its date overrides (public holidays, the Sundays a shop
 * may open) and a temporary closure (a renovation). Read 1.11.2026; the shape is in
 * scripts/sample-store.json.
 *
 * THE SAME FOR dm (DM Finder, 2.11.2026): dm's own store service, the one behind dm.hr's store
 * finder, answers every dm in Croatia with its week, its holidays (extraClosingDates), its open
 * Sundays (extraOpeningDays) and a last day (closingDate). [parse] tells the two answers apart by
 * their shape: spar.hr sends a list, dm an object with "stores" in it.
 */

/** One stretch of opening, in minutes after midnight. [close] of 1440 is "until midnight". */
data class Span(val open: Int, val close: Int)

/** A shop shut for a while, both days included. [info] is spar.hr's word, e.g. "Preuređenje". */
data class Closure(val from: LocalDate, val to: LocalDate, val info: String)

data class Store(
    val id: String,
    val name: String,
    /** INTERSPAR, the hypermarket: drawn as the bigger circle. */
    val big: Boolean,
    val address: String,
    val city: String,
    val zip: String,
    val lat: Double,
    val lon: Double,
    val phone: String,
    /** week[0] is Monday ... week[6] is Sunday. An empty list is closed all day. */
    val week: List<List<Span>>,
    /** A date's own hours, replacing the week's. An empty list is closed all day (a holiday). */
    val special: Map<LocalDate, List<Span>>,
    val closed: Closure?,
)

object Stores {
    /** Open and closed are always Croatian time, wherever the phone thinks it is. */
    val ZONE: ZoneId = ZoneId.of("Europe/Zagreb")

    private val DAYS = listOf("ponedjeljak", "utorak", "srijeda", "četvrtak", "petak", "subota", "nedjelja")

    /** Fewer than this many stores in an answer means the answer is not the store list. */
    const val ENOUGH = 50

    /**
     * spar.hr's answer, read. Throws when the text is not the store list at all (Cloudflare's
     * "Just a moment" page is HTML), so a caller keeps what it had instead of an empty map.
     */
    fun parse(text: String): List<Store> {
        if (text.trimStart().startsWith("{")) return parseDm(JSONObject(text))
        val array = JSONArray(text)
        val out = ArrayList<Store>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            store(o)?.let { out.add(it) }
        }
        return out
    }

    private fun store(o: JSONObject): Store? {
        val lat = o.optDouble("latitude", Double.NaN)
        val lon = o.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lon.isNaN() || (lat == 0.0 && lon == 0.0)) return null

        val byDay = HashMap<String, List<Span>>()
        o.optJSONArray("shopHours")?.let { hours ->
            for (i in 0 until hours.length()) {
                val oh = hours.optJSONObject(i)?.optJSONObject("openingHours") ?: continue
                val day = oh.opt("dayType") as? String ?: continue
                byDay[day] = spans(oh)
            }
        }
        val special = HashMap<LocalDate, List<Span>>()
        o.optJSONArray("specialShopHours")?.let { hours ->
            for (i in 0 until hours.length()) {
                val oh = hours.optJSONObject(i)?.optJSONObject("openingHours") ?: continue
                val date = date(oh.optJSONObject("dayType")) ?: continue
                special[date] = spans(oh)
            }
        }
        val closed = o.optJSONObject("temporarilyClosed")?.let { tc ->
            val from = date(tc.optJSONObject("dateFrom"))
            val to = date(tc.optJSONObject("dateTo"))
            if (from != null && to != null) Closure(from, to, tc.optString("info", "").trim()) else null
        }
        val plant = o.optJSONObject("plantType")?.optString("name", "").orEmpty()
        return Store(
            id = o.optString("locationId", ""),
            name = clean(o.optString("name", "")),
            big = plant.uppercase(Locale.ROOT).contains("INTERSPAR"),
            address = clean(o.optString("address", "")),
            city = cityName(o.optString("city", "")),
            zip = clean(o.optString("zipCode", "")),
            lat = lat,
            lon = lon,
            phone = clean(o.optString("telephone", "")),
            week = DAYS.map { byDay[it].orEmpty() },
            special = special,
            closed = closed,
        )
    }

    // --- dm ------------------------------------------------------------------------------------

    /** The last day of a store that closes for good: the closure reaches to the end of time. */
    val FOREVER: LocalDate = LocalDate.of(9999, 12, 31)

    private fun parseDm(root: JSONObject): List<Store> {
        val array = root.getJSONArray("stores")
        val out = ArrayList<Store>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            if (o.optString("countryCode", "HR") != "HR") continue
            dmStore(o)?.let { out.add(it) }
        }
        return out
    }

    private fun dmStore(o: JSONObject): Store? {
        val at = o.optJSONObject("location") ?: return null
        val lat = at.optDouble("lat", Double.NaN)
        val lon = at.optDouble("lon", Double.NaN)
        if (lat.isNaN() || lon.isNaN() || (lat == 0.0 && lon == 0.0)) return null
        val a = o.optJSONObject("address") ?: JSONObject()

        // weekDay 1 is Monday ... 7 Sunday; a day that is not listed is a day it does not open.
        val week = MutableList<List<Span>>(7) { emptyList() }
        o.optJSONArray("openingHours")?.let { days ->
            for (i in 0 until days.length()) {
                val d = days.optJSONObject(i) ?: continue
                val n = d.optInt("weekDay", 0)
                if (n in 1..7) week[n - 1] = dmSpans(d.optJSONArray("timeRanges"))
            }
        }
        val special = HashMap<LocalDate, List<Span>>()
        o.optJSONArray("extraOpeningDays")?.let { days ->
            for (i in 0 until days.length()) {
                val d = days.optJSONObject(i) ?: continue
                val date = isoDate(d.optString("date", "")) ?: continue
                special[date] = dmSpans(d.optJSONArray("timeRanges"))
            }
        }
        // Closed dates last: a holiday wins over anything else said about the same day.
        o.optJSONArray("extraClosingDates")?.let { days ->
            for (i in 0 until days.length()) {
                val date = isoDate(days.optJSONObject(i)?.optString("date", "").orEmpty()) ?: continue
                special[date] = emptyList()
            }
        }
        // closingDate is read as the first day shut: wrongly "shut" on a last day costs less than
        // wrongly "open" on a first shut one, which sends somebody to a dark door.
        val closing = isoDate(o.optString("closingDate", ""))
        val opening = isoDate(o.optString("openingDate", ""))
        val closed = when {
            closing != null -> Closure(closing, FOREVER, "closing for good")
            opening != null && opening.isAfter(LocalDate.now(ZONE)) ->
                Closure(LocalDate.of(2000, 1, 1), opening.minusDays(1), "opens ${opening.dayOfMonth}.${opening.monthValue}.")
            else -> null
        }

        val street = clean(a.optString("street", ""))
        val place = clean(a.optString("streetAdditional", ""))
        return Store(
            id = o.optString("storeId", ""),
            name = "dm " + (if (place.isNotEmpty()) cityName(place) else street),
            big = false,
            address = street,
            city = cityName(a.optString("city", "")),
            zip = clean(a.optString("zip", "")),
            lat = lat,
            lon = lon,
            phone = clean(o.optString("phone", "")),
            week = week,
            special = special,
            closed = closed,
        )
    }

    private fun isoDate(s: String): LocalDate? =
        if (s.isBlank()) null else runCatching { LocalDate.parse(s.trim().take(10)) }.getOrNull()

    /** "09:00"–"21:00" into minutes; "24:00", "00:00" or a time before the opening is midnight. */
    private fun dmSpans(ranges: JSONArray?): List<Span> {
        if (ranges == null) return emptyList()
        val out = ArrayList<Span>(1)
        for (i in 0 until ranges.length()) {
            val r = ranges.optJSONObject(i) ?: continue
            val open = hhmm(r.optString("opening", "")) ?: continue
            var close = hhmm(r.optString("closing", "")) ?: continue
            if (close <= open) close = 24 * 60
            out.add(Span(open, close))
        }
        return out
    }

    private fun hhmm(s: String): Int? {
        val parts = s.trim().split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return (h * 60 + m).coerceIn(0, 24 * 60)
    }

    /** spar.hr's dates count months from 0 (java.util.Calendar): month 10 is November. */
    private fun date(t: JSONObject?): LocalDate? {
        if (t == null) return null
        return runCatching { LocalDate.of(t.getInt("year"), t.getInt("month") + 1, t.getInt("dayOfMonth")) }.getOrNull()
    }

    private fun minutes(t: JSONObject?): Int? {
        if (t == null) return null
        return t.optInt("hourOfDay", 0) * 60 + t.optInt("minute", 0)
    }

    private fun spans(oh: JSONObject): List<Span> {
        val out = ArrayList<Span>(2)
        for ((f, t) in listOf("from1" to "to1", "from2" to "to2")) {
            val open = minutes(oh.optJSONObject(f)) ?: continue
            var close = minutes(oh.optJSONObject(t)) ?: continue
            // A closing time of 0:00, or one before the opening, is the end of the day.
            if (close <= open) close = 24 * 60
            out.add(Span(open, close))
        }
        return out
    }

    private fun clean(s: String) = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    /** "VELIKA GORICA" and "Velika Gorica " are one city: "Velika Gorica". */
    fun cityName(raw: String): String =
        clean(raw).lowercase(CROATIAN).split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase(CROATIAN) }
        }

    private val CROATIAN: Locale = Locale.forLanguageTag("hr")

    // --- the hours ---------------------------------------------------------------------------

    /** The hours a store keeps on one date: a closure first, then the date's own, then the week. */
    fun hoursOn(store: Store, date: LocalDate): List<Span> {
        store.closed?.let { if (!date.isBefore(it.from) && !date.isAfter(it.to)) return emptyList() }
        store.special[date]?.let { return it }
        return store.week[date.dayOfWeek.value - 1]
    }

    fun isOpen(store: Store, at: LocalDateTime): Boolean {
        val m = at.hour * 60 + at.minute
        return hoursOn(store, at.toLocalDate()).any { m >= it.open && m < it.close }
    }

    /** True when the store is shut for a closure on that date (a renovation), not for the day. */
    fun closedFor(store: Store, date: LocalDate): Closure? =
        store.closed?.takeIf { !date.isBefore(it.from) && !date.isAfter(it.to) }

    /** "7", "7:30", "24" for midnight. */
    fun time(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "$h" else "$h:${m.toString().padStart(2, '0')}"
    }

    /** The two numbers above a store: "7–21". A dash when it does not open that day. */
    fun label(spans: List<Span>): String =
        if (spans.isEmpty()) "–" else spans.joinToString(", ") { "${time(it.open)}–${time(it.close)}" }

    // --- the cities --------------------------------------------------------------------------

    data class City(val name: String, val stores: Int)

    /** Every city once, with how many stores it has, in Croatian alphabetical order (Č after C). */
    fun cities(stores: List<Store>): List<City> {
        val collator = Collator.getInstance(CROATIAN)
        return stores.groupingBy { it.city }.eachCount()
            .map { City(it.key, it.value) }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    fun openCount(stores: List<Store>, at: LocalDateTime): Int = stores.count { isOpen(it, at) }

    /** The box around some stores, as south, west, north, east. Null for none. */
    fun bounds(stores: List<Store>): DoubleArray? {
        if (stores.isEmpty()) return null
        return doubleArrayOf(
            stores.minOf { it.lat }, stores.minOf { it.lon },
            stores.maxOf { it.lat }, stores.maxOf { it.lon },
        )
    }
}
