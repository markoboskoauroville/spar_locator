package com.mantra.spar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * TEST 1, THE MECHANISM (four-tests.md). Stores.kt alone, on a desk, against two inputs:
 *
 *  - spar.hr's real answer of 1.11.2026, frozen in src/test/resources, so the numbers below are
 *    measured, not invented: on 1.11.2026 (All Saints) every store is shut; on Wednesday 4.11.2026
 *    at 20:45 140 are open and 4 are shut for renovation (the web version showed the same).
 *  - small hand-written answers for the edges: the hour boundaries, a closure over a holiday,
 *    a closing time of midnight, a page that is not the list at all.
 */
class StoresTest {

    private fun resource(name: String): String {
        // Gradle runs tests in app/; kotlinc on a desk runs them from the repository root.
        val here = listOf("src/test/resources/$name", "app/src/test/resources/$name").map(::File).first { it.exists() }
        return here.readText()
    }

    private val real: List<Store> by lazy { Stores.parse(resource("spar_2026-11-01.json")) }

    private fun at(text: String) = LocalDateTime.parse(text)

    // --- the real answer ---------------------------------------------------------------------

    @Test fun `the real answer has every store`() = assertEquals(144, real.size)

    @Test fun `every store is in Croatia`() = real.forEach {
        assertTrue("${it.name} ${it.lat}", it.lat in 42.3..46.6)
        assertTrue("${it.name} ${it.lon}", it.lon in 13.4..19.5)
    }

    @Test fun `every store has seven days`() = real.forEach { assertEquals(it.name, 7, it.week.size) }

    @Test fun `the hypermarkets are found`() = assertEquals(25, real.count { it.big })

    @Test fun `All Saints shuts every store`() = assertEquals(0, Stores.openCount(real, at("2026-11-01T12:00")))

    @Test fun `a Wednesday evening has 140 open`() = assertEquals(140, Stores.openCount(real, at("2026-11-04T20:45")))

    @Test fun `the four shut that evening are the renovations`() {
        val shut = real.filter { !Stores.isOpen(it, at("2026-11-04T20:45")) }
        assertEquals(4, shut.size)
        shut.forEach { assertNotNull(it.name, Stores.closedFor(it, LocalDate.parse("2026-11-04"))) }
    }

    @Test fun `a Wednesday at nine at night has everything shut`() =
        assertEquals(0, Stores.openCount(real, at("2026-11-04T23:30")))

    @Test fun `the renovation in Rijeka runs 26 October to 25 November`() {
        val rijeka = real.first { it.name == "SPAR supermarket Rijeka" }
        assertEquals(Closure(LocalDate.parse("2026-10-26"), LocalDate.parse("2026-11-25"), "Preuređenje"), rijeka.closed)
    }

    @Test fun `city names are made one`() {
        val names = real.map { it.city }.toSet()
        assertTrue(names.contains("Zagreb"))
        assertFalse(names.contains("ZAGREB"))
        assertTrue(names.contains("Velika Gorica"))
        assertTrue(names.contains("Grubišno Polje"))
    }

    @Test fun `Zagreb has 51 stores`() =
        assertEquals(51, Stores.cities(real).first { it.name == "Zagreb" }.stores)

    @Test fun `the cities are in Croatian order`() {
        val names = Stores.cities(real).map { it.name }
        assertEquals(63, names.size)
        // Č comes after C and before D; Đ after D; Š after S; the last letters last.
        assertTrue(names.indexOf("Čakovec") > names.indexOf("Buje"))
        assertTrue(names.indexOf("Čakovec") < names.indexOf("Daruvar"))
        assertTrue(names.indexOf("Đakovo") > names.indexOf("Dugopolje"))
        assertTrue(names.indexOf("Šibenik") > names.indexOf("Sv. Ivan Zelina"))
    }

    @Test fun `the bundled list is the store list`() {
        val bundled = listOf("src/spar/assets/spar_stores.json", "app/src/spar/assets/spar_stores.json")
            .map(::File).first { it.exists() }.readText()
        assertTrue(Stores.parse(bundled).size >= Stores.ENOUGH)
    }

    @Test fun `dm's bundled list is the store list`() {
        val bundled = listOf("src/dm/assets/dm_stores.json", "app/src/dm/assets/dm_stores.json")
            .map(::File).first { it.exists() }.readText()
        assertEquals(179, Stores.parse(bundled).size)
    }

    // --- the labels --------------------------------------------------------------------------

    @Test fun `closed all day is a dash`() = assertEquals("–", Stores.label(emptyList()))

    @Test fun `whole hours are two numbers`() = assertEquals("7–21", Stores.label(listOf(Span(420, 1260))))

    @Test fun `half hours keep their minutes`() = assertEquals("7:30–20:30", Stores.label(listOf(Span(450, 1230))))

    @Test fun `two stretches are both shown`() =
        assertEquals("8–12, 16–20", Stores.label(listOf(Span(480, 720), Span(960, 1200))))

    @Test fun `midnight is 24`() = assertEquals("24", Stores.time(1440))

    @Test fun `city names`() {
        assertEquals("Velika Gorica", Stores.cityName("VELIKA GORICA"))
        assertEquals("Grubišno Polje", Stores.cityName("Grubišno Polje "))
        assertEquals("Čakovec", Stores.cityName("ČAKOVEC"))
        assertEquals("Sv. Ivan Zelina", Stores.cityName("Sv.  Ivan Zelina"))
    }

    // --- hand-written answers ----------------------------------------------------------------

    /** One store: 7–21 on weekdays and Saturday, shut on Sunday, with what [extra] adds. */
    private fun one(extra: String = "", hours: String = WEEK): Store = Stores.parse(
        """[{"locationId":"1","name":"SPAR test","address":"Ulica 1","city":"ZAGREB","zipCode":"10000",
            "latitude":45.8,"longitude":15.97,"plantType":{"id":1,"name":"SPAR SUPERMARKET"},
            "shopHours":$hours $extra}]"""
    ).single()

    private fun t(h: Int, m: Int = 0) = """{"year":1970,"month":0,"dayOfMonth":1,"hourOfDay":$h,"minute":$m,"second":0}"""
    private fun d(y: Int, month0: Int, day: Int) =
        """{"year":$y,"month":$month0,"dayOfMonth":$day,"hourOfDay":0,"minute":0,"second":0}"""

    private fun day(name: String, from: String?, to: String?) =
        """{"openingHours":{"dayType":"$name","from1":$from,"from2":null,"to1":$to,"to2":null},"timeperiod":{"timeperiodType":"standard"}}"""

    private val WEEK = "[" + listOf("ponedjeljak", "utorak", "srijeda", "četvrtak", "petak", "subota")
        .joinToString(",") { day(it, t(7), t(21)) } + "," + day("nedjelja", null, null) + "]"

    @Test fun `open from the first minute`() = assertTrue(Stores.isOpen(one(), at("2026-11-04T07:00")))

    @Test fun `shut the minute before`() = assertFalse(Stores.isOpen(one(), at("2026-11-04T06:59")))

    @Test fun `open in the last minute`() = assertTrue(Stores.isOpen(one(), at("2026-11-04T20:59")))

    @Test fun `shut at the closing time`() = assertFalse(Stores.isOpen(one(), at("2026-11-04T21:00")))

    @Test fun `shut on Sunday`() = assertFalse(Stores.isOpen(one(), at("2026-11-08T12:00")))

    @Test fun `a Sunday with its own hours opens`() {
        // spar.hr counts months from 0: 10 is November.
        val s = one(""","specialShopHours":[{"openingHours":{"dayType":${d(2026, 10, 8)},"from1":${t(8)},"from2":null,"to1":${t(14)},"to2":null}}]""")
        assertEquals(listOf(Span(480, 840)), Stores.hoursOn(s, LocalDate.parse("2026-11-08")))
        assertTrue(Stores.isOpen(s, at("2026-11-08T10:00")))
        assertFalse(Stores.isOpen(s, at("2026-11-08T14:00")))
    }

    @Test fun `a holiday shuts a weekday`() {
        val s = one(""","specialShopHours":[{"openingHours":{"dayType":${d(2026, 11, 25)},"from1":null,"from2":null,"to1":null,"to2":null}}]""")
        assertEquals("–", Stores.label(Stores.hoursOn(s, LocalDate.parse("2026-12-25"))))
        assertFalse(Stores.isOpen(s, at("2026-12-25T12:00")))
        assertTrue(Stores.isOpen(s, at("2026-12-24T12:00")))
    }

    @Test fun `a closure beats a date's own hours`() {
        val s = one(
            ""","specialShopHours":[{"openingHours":{"dayType":${d(2026, 10, 8)},"from1":${t(8)},"from2":null,"to1":${t(14)},"to2":null}}],
               "temporarilyClosed":{"dateFrom":${d(2026, 10, 1)},"dateTo":${d(2026, 10, 10)},"info":"Preuređenje"}"""
        )
        assertFalse(Stores.isOpen(s, at("2026-11-08T10:00")))
        assertNotNull(Stores.closedFor(s, LocalDate.parse("2026-11-08")))
    }

    @Test fun `a closure includes both of its days and no more`() {
        val s = one(""","temporarilyClosed":{"dateFrom":${d(2026, 10, 2)},"dateTo":${d(2026, 10, 4)},"info":""}""")
        assertNull(Stores.closedFor(s, LocalDate.parse("2026-11-01")))
        assertFalse(Stores.isOpen(s, at("2026-11-02T12:00")))
        assertFalse(Stores.isOpen(s, at("2026-11-04T12:00")))
        assertTrue(Stores.isOpen(s, at("2026-11-05T12:00")))
        assertNull(Stores.closedFor(s, LocalDate.parse("2026-11-05")))
    }

    @Test fun `a closing time of midnight is the end of the day`() {
        val s = one(hours = "[" + day("srijeda", t(7), t(0)) + "]")
        assertEquals(listOf(Span(420, 1440)), s.week[2])
        assertTrue(Stores.isOpen(s, at("2026-11-04T23:59")))
        assertEquals("7–24", Stores.label(s.week[2]))
    }

    @Test fun `a day spar_hr does not list is shut`() {
        val s = one(hours = "[" + day("srijeda", t(7), t(21)) + "]")
        assertFalse(Stores.isOpen(s, at("2026-11-05T12:00")))
    }

    @Test fun `a store without a place is left out`() = assertEquals(
        0, Stores.parse("""[{"locationId":"9","name":"x","city":"Zagreb","latitude":0,"longitude":0}]""").size
    )

    @Test(expected = org.json.JSONException::class)
    fun `Cloudflare's page is not a store list`() {
        Stores.parse("<!DOCTYPE html><html><head><title>Just a moment...</title></head></html>")
    }

    @Test fun `the box around some stores`() {
        val box = Stores.bounds(real.filter { it.city == "Zagreb" })!!
        assertTrue(box[0] < box[2] && box[1] < box[3])
        assertTrue(box[0] > 45.6 && box[2] < 46.0)
        assertNull(Stores.bounds(emptyList()))
    }

    // --- dm (DM Finder, 2.11.2026): dm's store service, 61 stores of its real answer --------------

    private val dm: List<Store> by lazy { Stores.parse(resource("dm_2026-11-02.json")) }

    private fun dmStore(id: String) = dm.first { it.id == id }

    @Test fun `dm's answer is read`() {
        assertEquals(61, dm.size)
        dm.forEach {
            assertTrue("${it.name} ${it.lat}", it.lat in 42.3..46.6)
            assertTrue("${it.name} ${it.lon}", it.lon in 13.4..19.5)
            assertTrue(it.name, it.name.startsWith("dm "))
        }
    }

    @Test fun `a dm store's name, city and week`() {
        val k = dmStore("K05K")
        assertEquals("dm Supernova Karlovac", k.name)
        assertEquals("Karlovac", k.city)
        assertEquals("Prilaz V. Holjevca 12", k.address)
        assertEquals(listOf(Span(9 * 60, 21 * 60)), k.week[0])
        // Sunday is not in dm's list at all: shut.
        assertTrue(k.week[6].isEmpty())
        assertTrue(Stores.isOpen(k, at("2026-11-04T20:59")))
        assertFalse(Stores.isOpen(k, at("2026-11-04T21:00")))
        assertFalse(Stores.isOpen(k, at("2026-11-08T12:00")))
    }

    @Test fun `dm's holiday and open Sunday`() {
        val k = dmStore("K05K")
        // 18.11.2026, Remembrance Day, a Wednesday: shut.
        assertFalse(Stores.isOpen(k, at("2026-11-18T12:00")))
        // 29.11.2026, a Sunday dm opens.
        assertTrue(Stores.isOpen(k, at("2026-11-29T12:00")))
    }

    @Test fun `a dm store that closes for good`() {
        val s = dmStore("K08M")
        val date = s.closed!!.from
        assertEquals(Stores.FOREVER, s.closed!!.to)
        assertTrue(Stores.hoursOn(s, date).isEmpty())
        assertTrue(Stores.hoursOn(s, date.plusYears(1)).isEmpty())
    }

    @Test fun `dm keeps only Croatia`() {
        val two = """{"stores":[
            {"storeId":"A","countryCode":"SI","location":{"lat":46.0,"lon":14.5},"address":{"street":"x","city":"LJUBLJANA"},"openingHours":[]},
            {"storeId":"B","countryCode":"HR","location":{"lat":45.8,"lon":16.0},"address":{"street":"Ilica 1","city":"ZAGREB"},
             "openingHours":[{"weekDay":7,"timeRanges":[{"opening":"10:00","closing":"24:00"}]}]}]}"""
        val got = Stores.parse(two)
        assertEquals(listOf("B"), got.map { it.id })
        assertEquals("dm Ilica 1", got[0].name)
        assertEquals(listOf(Span(600, 1440)), got[0].week[6])
    }
}
