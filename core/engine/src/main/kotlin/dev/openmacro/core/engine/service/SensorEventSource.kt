package dev.openmacro.core.engine.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dev.openmacro.core.model.TriggerEvent
import kotlin.math.sqrt
import kotlin.math.roundToInt

/**
 * Registers a single hardware sensor and translates its readings into trigger events, with
 * edge-detection and debouncing so a macro fires on a change, not on every reading.
 */
internal object SensorEventSource {

    fun open(context: Context, param: String, emit: (TriggerEvent) -> Unit): Subscription {
        val manager = context.getSystemService(SensorManager::class.java)
        return when {
            param == "shake" -> shake(manager, emit)
            param == "orientation" -> orientation(manager, emit)
            param == "proximity" -> proximity(manager, emit)
            param == "light" -> light(manager, emit)
            param.startsWith("steps:") -> steps(manager, param.removePrefix("steps:").toIntOrNull() ?: 100, emit)
            else -> Subscription {}
        }
    }

    private fun steps(manager: SensorManager, every: Int, emit: (TriggerEvent) -> Unit): Subscription {
        var baseline = -1f
        val listener = object : SimpleSensorListener() {
            override fun onSensorChanged(event: SensorEvent) {
                val total = event.values[0] // cumulative steps since boot
                if (baseline < 0) baseline = total
                while (total - baseline >= every) {
                    baseline += every
                    emit(TriggerEvent.StepsReached(every))
                }
            }
        }
        return register(manager, Sensor.TYPE_STEP_COUNTER, listener)
    }

    private fun register(manager: SensorManager, type: Int, listener: SensorEventListener): Subscription {
        val sensor = manager.getDefaultSensor(type) ?: return Subscription {}
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        return Subscription { manager.unregisterListener(listener) }
    }

    private fun shake(manager: SensorManager, emit: (TriggerEvent) -> Unit): Subscription {
        val threshold = SensorManager.GRAVITY_EARTH * 2.2f
        var lastShake = 0L
        val listener = object : SimpleSensorListener() {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val magnitude = sqrt(x * x + y * y + z * z)
                val now = System.currentTimeMillis()
                if (magnitude > threshold && now - lastShake > 1_000) {
                    lastShake = now
                    emit(TriggerEvent.ShakeDetected)
                }
            }
        }
        return register(manager, Sensor.TYPE_ACCELEROMETER, listener)
    }

    private fun orientation(manager: SensorManager, emit: (TriggerEvent) -> Unit): Subscription {
        var faceDown: Boolean? = null
        val listener = object : SimpleSensorListener() {
            override fun onSensorChanged(event: SensorEvent) {
                val z = event.values[2]
                val down = when {
                    z < -7f -> true
                    z > 7f -> false
                    else -> return // tilted; ignore until clearly face up/down
                }
                if (faceDown != down) {
                    faceDown = down
                    emit(TriggerEvent.FlipDetected(down))
                }
            }
        }
        return register(manager, Sensor.TYPE_ACCELEROMETER, listener)
    }

    private fun proximity(manager: SensorManager, emit: (TriggerEvent) -> Unit): Subscription {
        val sensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return Subscription {}
        val maxRange = sensor.maximumRange
        var near: Boolean? = null
        val listener = object : SimpleSensorListener() {
            override fun onSensorChanged(event: SensorEvent) {
                val isNear = event.values[0] < maxRange
                if (near != isNear) {
                    near = isNear
                    emit(TriggerEvent.ProximityChanged(isNear))
                }
            }
        }
        return register(manager, Sensor.TYPE_PROXIMITY, listener)
    }

    private fun light(manager: SensorManager, emit: (TriggerEvent) -> Unit): Subscription {
        var lastLux: Int? = null
        val listener = object : SimpleSensorListener() {
            override fun onSensorChanged(event: SensorEvent) {
                val lux = event.values[0].roundToInt()
                val previous = lastLux
                // Only report meaningful changes to avoid noise from tiny fluctuations.
                if (previous == null) {
                    lastLux = lux
                } else if (kotlin.math.abs(previous - lux) >= 3) {
                    lastLux = lux
                    emit(TriggerEvent.LightChanged(previous, lux))
                }
            }
        }
        return register(manager, Sensor.TYPE_LIGHT, listener)
    }

    private operator fun FloatArray.component1() = this[0]
    private operator fun FloatArray.component2() = this[1]
    private operator fun FloatArray.component3() = this[2]
}

private abstract class SimpleSensorListener : SensorEventListener {
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
