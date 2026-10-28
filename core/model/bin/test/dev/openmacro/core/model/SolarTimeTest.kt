package dev.openmacro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class SolarTimeTest {
    private val ist = ZoneId.of("Asia/Kolkata")

    // Bangalore. Reference values from Open-Meteo for 2026-10-28: sunrise 06:12, sunset 17:54 IST.
    private val lat = 12.97
    private val lng = 77.59

    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 10, 28, h, m, 0, 0, ist)

    @Test
    fun sunriseMatchesReferenceWithinFiveMinutes() {
        val next = SolarTime.next(at(0, 1), lat, lng, sunrise = true, offsetMinutes = 0)!!
        val diff = ChronoUnit.MINUTES.between(at(6, 12), next)
        assertTrue("sunrise off by $diff min (got $next)", kotlin.math.abs(diff) <= 5)
    }

    @Test
    fun sunsetMatchesReferenceWithinFiveMinutes() {
        val next = SolarTime.next(at(12, 0), lat, lng, sunrise = false, offsetMinutes = 0)!!
        val diff = ChronoUnit.MINUTES.between(at(17, 54), next)
        assertTrue("sunset off by $diff min (got $next)", kotlin.math.abs(diff) <= 5)
    }

    @Test
    fun offsetShiftsTheEvent() {
        val plain = SolarTime.next(at(0, 1), lat, lng, sunrise = true, offsetMinutes = 0)!!
        val shifted = SolarTime.next(at(0, 1), lat, lng, sunrise = true, offsetMinutes = 30)!!
        assertEquals(30, ChronoUnit.MINUTES.between(plain, shifted))
    }

    @Test
    fun afterTodaysSunriseRollsToTomorrow() {
        val next = SolarTime.next(at(10, 0), lat, lng, sunrise = true, offsetMinutes = 0)!!
        assertEquals(29, next.dayOfMonth)
    }

    @Test
    fun daytimeFlagFollowsTheSun() {
        assertTrue(SolarTime.isDaytime(at(12, 0), lat, lng))
        assertFalse(SolarTime.isDaytime(at(3, 0), lat, lng))
        assertFalse(SolarTime.isDaytime(at(22, 0), lat, lng))
    }

    @Test
    fun polarNightHasNoSunrise() {
        // Tromsø in mid-December: the sun does not rise for weeks.
        val tromso = ZonedDateTime.of(2026, 12, 21, 0, 0, 0, 0, ZoneId.of("Europe/Oslo"))
        val next = SolarTime.next(tromso, 69.65, 18.96, sunrise = true, offsetMinutes = 0)
        // The first sunrise is after the polar night ends, i.e. well into January.
        assertTrue(next == null || next.isAfter(tromso.plusDays(10)))
        assertNull(null) // keeps the import used if the assertion above is rewritten
    }
}
