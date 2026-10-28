package dev.openmacro.core.engine.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dev.openmacro.core.engine.EngineLog
import dev.openmacro.core.model.TriggerEvent

/**
 * Tracks one geofence area ("lat,lng,radius") using [LocationManager] updates (no Play Services),
 * emitting an enter/leave transition when the inside/outside state changes. Seeds the initial
 * state silently so a macro fires on a real crossing, not on registration.
 */
internal object LocationEventSource {

    @SuppressLint("MissingPermission") // guarded by the permission check below
    fun open(context: Context, areaKey: String, emit: (TriggerEvent) -> Unit): Subscription {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            EngineLog.w("Location permission not granted; geofence '$areaKey' inactive")
            return Subscription {}
        }
        val parts = areaKey.split(",")
        val lat = parts.getOrNull(0)?.toDoubleOrNull()
        val lng = parts.getOrNull(1)?.toDoubleOrNull()
        val radius = parts.getOrNull(2)?.toFloatOrNull()
        if (lat == null || lng == null || radius == null) return Subscription {}

        val manager = context.getSystemService(LocationManager::class.java)
        var inside: Boolean? = null

        fun evaluate(location: Location) {
            val distance = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, lat, lng, distance)
            val now = distance[0] <= radius
            val previous = inside
            inside = now
            if (previous != null && previous != now) emit(TriggerEvent.GeofenceTransition(areaKey, entering = now))
        }

        val listener = LocationListener { evaluate(it) }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        providers.forEach { provider ->
            // ~1 minute / 50 m balances battery against responsiveness for geofencing.
            manager.requestLocationUpdates(provider, 60_000L, 50f, listener)
            manager.getLastKnownLocation(provider)?.let(::evaluate)
        }
        if (providers.isEmpty()) EngineLog.w("No location providers enabled for geofence '$areaKey'")

        return Subscription { manager.removeUpdates(listener) }
    }
}
