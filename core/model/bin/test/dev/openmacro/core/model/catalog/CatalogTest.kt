package dev.openmacro.core.model.catalog

import dev.openmacro.core.model.ActionSpec
import dev.openmacro.core.model.AudioStream
import dev.openmacro.core.model.BluetoothEvent
import dev.openmacro.core.model.Comparison
import dev.openmacro.core.model.ConstraintSpec
import dev.openmacro.core.model.DeviceProbe
import dev.openmacro.core.model.EvalContext
import dev.openmacro.core.model.LevelDirection
import dev.openmacro.core.model.RingerMode
import dev.openmacro.core.model.SpecJson
import dev.openmacro.core.model.TriggerEvent
import dev.openmacro.core.model.TriggerSpec
import dev.openmacro.core.model.VarRef
import dev.openmacro.core.model.Variable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CatalogTest {

    @Test
    fun everyCatalogItemHasADefinitionAndRoundTrips() {
        // Each trigger/constraint/action default instance must serialize and look up in the registry.
        Catalog.triggers.forEach { def ->
            val spec = def.create()
            assertEquals(spec, SpecJson.decodeTrigger(SpecJson.encode(spec)))
            assertEquals(def, Catalog.trigger(spec))
        }
        Catalog.constraints.forEach { def ->
            val spec = def.create()
            assertEquals(spec, SpecJson.decodeConstraint(SpecJson.encode(spec)))
            assertEquals(def, Catalog.constraint(spec))
        }
        Catalog.actions.forEach { def ->
            val spec = def.create()
            assertEquals(spec, SpecJson.decodeAction(SpecJson.encode(spec)))
            assertEquals(def, Catalog.action(spec))
        }
    }

    @Test
    fun serialNamesAreUnique() {
        val names = (Catalog.triggers.map { it.create() }.map { SpecJson.encode(it) } +
            Catalog.actions.map { SpecJson.encode(it.create()) })
        assertTrue(names.isNotEmpty())
    }

    @Test
    fun batteryMatcherCrossesThreshold() {
        val spec = TriggerSpec.BatteryLevel(20, LevelDirection.FALLS_TO)
        assertTrue(Catalog.matches(spec, TriggerEvent.BatteryLevelChanged(25, 18)))
        assertFalse(Catalog.matches(spec, TriggerEvent.BatteryLevelChanged(18, 15)))
    }

    @Test
    fun bluetoothDeviceFilterMatches() {
        val spec = TriggerSpec.BluetoothState(BluetoothEvent.DEVICE_CONNECTED, "buds")
        assertTrue(Catalog.matches(spec, TriggerEvent.BluetoothDeviceChanged(true, "Pixel Buds")))
        assertFalse(Catalog.matches(spec, TriggerEvent.BluetoothDeviceChanged(true, "Car")))
    }

    @Test
    fun intervalMatcherAlignsToEpoch() {
        val spec = TriggerSpec.RegularInterval(15)
        assertTrue(Catalog.matches(spec, tick(epochMinute = 30)))
        assertFalse(Catalog.matches(spec, tick(epochMinute = 31)))
    }

    @Test
    fun timeOfDayNextOccurrenceRollsToNextDay() {
        val zone = ZoneId.of("Europe/London")
        val now = ZonedDateTime.of(2026, 10, 27, 9, 0, 0, 0, zone)
        val next = Catalog.trigger(TriggerSpec.TimeOfDay(9, 0)).nextOccurrence(TriggerSpec.TimeOfDay(9, 0), now)
        assertEquals(ZonedDateTime.of(2026, 10, 28, 9, 0, 0, 0, zone), next)
    }

    @Test
    fun constraintsEvaluate() {
        val ctx = fakeContext(battery = 40, time = LocalTime.of(23, 0))
        assertTrue(Catalog.evaluate(ConstraintSpec.BatteryThreshold(Comparison.AT_MOST, 50), ctx))
        assertFalse(Catalog.evaluate(ConstraintSpec.BatteryThreshold(Comparison.AT_LEAST, 50), ctx))
        assertTrue(Catalog.evaluate(ConstraintSpec.TimeWindow(22, 0, 7, 0), ctx))
    }

    @Test
    fun logicConstraintCombines() {
        val ctx = fakeContext(battery = 40)
        val logic = ConstraintSpec.Logic(
            dev.openmacro.core.model.LogicOp.OR,
            listOf(ConstraintSpec.BatteryThreshold(Comparison.AT_LEAST, 90), ConstraintSpec.BatteryThreshold(Comparison.AT_MOST, 50)),
        )
        assertTrue(Catalog.evaluate(logic, ctx))
    }

    @Test
    fun variableCompareIsNumericWhenPossible() {
        val ctx = fakeContext(variables = mapOf("n" to "10"))
        val spec = ConstraintSpec.VariableCompare(VarRef(dev.openmacro.core.model.VarScope.GLOBAL, "n"), dev.openmacro.core.model.CompareOp.GREATER, "5")
        assertTrue(Catalog.evaluate(spec, ctx))
    }

    private fun tick(epochMinute: Long) = TriggerEvent.TimeTick(0, 0, 1, epochMinute)

    private fun fakeContext(
        battery: Int = 50,
        time: LocalTime = LocalTime.NOON,
        variables: Map<String, String> = emptyMap(),
    ): EvalContext = object : EvalContext {
        override val device: DeviceProbe = fakeDevice(battery)
        override val currentMacroId = 1L
        override fun now(): ZonedDateTime = ZonedDateTime.of(2026, 10, 27, time.hour, time.minute, 0, 0, ZoneId.of("UTC"))
        override fun variable(ref: VarRef): Variable? = variables[ref.name]?.let { Variable(ref.name, dev.openmacro.core.model.VarType.STRING, it) }
        override fun render(text: String): String = text
        override fun isMacroEnabled(id: Long) = true
        override fun isMacroRunning(id: Long) = false
        override fun lastRunAt(id: Long): Long? = null
    }

    private fun fakeDevice(battery: Int): DeviceProbe = object : DeviceProbe {
        override fun batteryPercent() = battery
        override fun batteryTemperatureC() = 25
        override fun isPowerConnected() = false
        override fun isDeviceLocked() = false
        override fun isScreenOn() = true
        override fun isHeadphonesConnected() = false
        override fun isWifiEnabled() = true
        override fun isWifiConnected() = true
        override fun isBluetoothEnabled() = false
        override fun isAirplaneMode() = false
        override fun isAutoRotate() = true
        override fun isBatterySaver() = false
        override fun isDarkTheme() = true
        override fun ringerMode() = RingerMode.NORMAL
        override fun volumePercent(stream: AudioStream) = 50
        override fun isMusicActive() = false
        override fun callState() = dev.openmacro.core.model.CallState.IDLE
        override fun isRoaming() = false
        override fun isVpnActive() = false
        override fun isLocationEnabled() = true
        override fun isTorchOn() = false
        override fun isNfcEnabled() = false
        override fun isPortrait() = true
        override fun uptimeMillis() = 600_000L
        override fun screenText() = ""
        override fun activeNotifications() = emptyList<dev.openmacro.core.model.NotificationInfo>()
        override fun chargingSource() = dev.openmacro.core.model.ChargingSource.UNPLUGGED
        override fun foregroundApp(): String? = null
        override fun isDeviceIdle() = false
        override fun brightnessPercent() = 50
        override fun isAutoBrightness() = false
    }
}
