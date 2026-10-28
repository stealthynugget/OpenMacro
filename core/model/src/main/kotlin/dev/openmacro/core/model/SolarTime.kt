package dev.openmacro.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise/sunset times using the standard sunrise equation (Almanac algorithm). Pure math, no
 * network — given a date and coordinates it returns the event time, accurate to about a minute.
 */
object SolarTime {

    private const val ZENITH = 90.833 // official sunrise/sunset, accounting for refraction
    private const val D2R = Math.PI / 180.0
    private const val R2D = 180.0 / Math.PI

    /** The first sunrise/sunset strictly after [now] for [lat]/[lng], shifted by [offsetMinutes]. */
    fun next(now: ZonedDateTime, lat: Double, lng: Double, sunrise: Boolean, offsetMinutes: Int): ZonedDateTime? {
        for (addDays in 0..366L) {
            val date = now.toLocalDate().plusDays(addDays)
            val event = eventTime(date, lat, lng, sunrise)?.plusMinutes(offsetMinutes.toLong())?.withZoneSameInstant(now.zone)
            if (event != null && event.isAfter(now)) return event
        }
        return null // polar day/night for the whole period
    }

    /** True if the sun is currently up at [lat]/[lng] (between today's sunrise and sunset). */
    fun isDaytime(now: ZonedDateTime, lat: Double, lng: Double): Boolean {
        val date = now.toLocalDate()
        val sunrise = eventTime(date, lat, lng, sunrise = true)?.withZoneSameInstant(now.zone)
        val sunset = eventTime(date, lat, lng, sunrise = false)?.withZoneSameInstant(now.zone)
        return when {
            sunrise == null || sunset == null -> false // polar day/night: treat as not-day for safety
            else -> now.isAfter(sunrise) && now.isBefore(sunset)
        }
    }

    /** UTC-based [ZonedDateTime] of the event on [date], or null if the sun doesn't rise/set that day. */
    private fun eventTime(date: LocalDate, lat: Double, lng: Double, sunrise: Boolean): ZonedDateTime? {
        val n = date.dayOfYear
        val lngHour = lng / 15.0
        val t = n + ((if (sunrise) 6.0 else 18.0) - lngHour) / 24.0

        val m = (0.9856 * t) - 3.289
        var l = m + (1.916 * sin(m * D2R)) + (0.020 * sin(2 * m * D2R)) + 282.634
        l = norm(l, 360.0)

        var ra = atan(0.91764 * tan(l * D2R)) * R2D
        ra = norm(ra, 360.0)
        // Put RA in the same quadrant as L.
        val lQuadrant = floor(l / 90.0) * 90.0
        val raQuadrant = floor(ra / 90.0) * 90.0
        ra = (ra + (lQuadrant - raQuadrant)) / 15.0

        val sinDec = 0.39782 * sin(l * D2R)
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(ZENITH * D2R) - (sinDec * sin(lat * D2R))) / (cosDec * cos(lat * D2R))
        if (cosH > 1 || cosH < -1) return null // never rises / never sets on this date

        val h = (if (sunrise) 360.0 - acos(cosH) * R2D else acos(cosH) * R2D) / 15.0
        val localMeanT = h + ra - (0.06571 * t) - 6.622
        val ut = norm(localMeanT - lngHour, 24.0)

        val seconds = (ut * 3600.0).toLong()
        return date.atStartOfDay(java.time.ZoneOffset.UTC).let { Instant.from(it).plusSeconds(seconds).atZone(java.time.ZoneOffset.UTC) }
    }

    private fun norm(value: Double, range: Double): Double {
        var v = value % range
        if (v < 0) v += range
        return v
    }
}
