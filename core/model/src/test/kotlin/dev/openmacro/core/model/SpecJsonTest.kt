package dev.openmacro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecJsonTest {

    @Test
    fun triggersRoundTrip() {
        val specs = listOf(
            TriggerSpec.BatteryLevel(15, LevelDirection.RISES_TO),
            TriggerSpec.PowerConnection(false),
            TriggerSpec.WifiState(true),
            TriggerSpec.BluetoothState(BluetoothEvent.DEVICE_CONNECTED, "Buds"),
            TriggerSpec.ScreenState(false),
            TriggerSpec.TimeOfDay(6, 45, setOf(1, 3, 5)),
        )
        specs.forEach { assertEquals(it, SpecJson.decodeTrigger(SpecJson.encode(it))) }
    }

    @Test
    fun actionsRoundTrip() {
        val specs = listOf(
            ActionSpec.Flashlight(SwitchMode.ON),
            ActionSpec.SetVolume(AudioStream.ALARM, 80),
            ActionSpec.SetRingerMode(RingerMode.SILENT),
            ActionSpec.ShowNotification("Hi", "there"),
            ActionSpec.ShowToast("toast", long = true),
            ActionSpec.Vibrate(VibrationPattern.HEARTBEAT),
            ActionSpec.Wait(5),
        )
        specs.forEach { assertEquals(it, SpecJson.decodeAction(SpecJson.encode(it))) }
    }

    @Test
    fun constraintListRoundTrip() {
        val specs = listOf(
            ConstraintSpec.TimeWindow(9, 0, 17, 30),
            ConstraintSpec.DeviceLock(false),
            ConstraintSpec.BatteryThreshold(Comparison.AT_MOST, 30),
        )
        assertEquals(specs, SpecJson.decodeConstraints(SpecJson.encodeConstraints(specs)))
    }

    @Test
    fun discriminatorIsStable() {
        val raw = SpecJson.encode(TriggerSpec.WifiState(true))
        assertTrue(raw, raw.contains("\"type\":\"wifi_state\""))
    }

    @Test
    fun unknownActionTypeBecomesAPlaceholderInsteadOfCrashing() {
        // Regression: a removed action type stored in the database used to crash the app at startup.
        val raw = """{"type":"immersive_mode","mode":"FULL"}"""
        val spec = SpecJson.decodeAction(raw)
        assertTrue(spec is ActionSpec.Unsupported)
        assertEquals("immersive_mode", (spec as ActionSpec.Unsupported).originalType)
        assertEquals(raw, spec.raw)
    }

    @Test
    fun unknownTriggerAndConstraintTypesAreTolerated() {
        assertTrue(SpecJson.decodeTrigger("""{"type":"gone"}""") is TriggerSpec.Unsupported)
        assertTrue(SpecJson.decodeConstraint("""{"type":"gone"}""") is ConstraintSpec.Unsupported)
    }

    @Test
    fun garbageDoesNotThrow() {
        assertTrue(SpecJson.decodeAction("not json at all") is ActionSpec.Unsupported)
        assertTrue(SpecJson.decodeAction("""{"type":"toast","long":"notabool"}""") is ActionSpec.Unsupported)
    }

    @Test
    fun oneBadConstraintDoesNotDiscardItsSiblings() {
        val raw = """[{"type":"device_lock","locked":true},{"type":"gone"},{"type":"screen","on":false}]"""
        val list = SpecJson.decodeConstraints(raw)
        assertEquals(3, list.size)
        assertEquals(ConstraintSpec.DeviceLock(true), list[0])
        assertTrue(list[1] is ConstraintSpec.Unsupported)
        assertEquals(ConstraintSpec.Screen(false), list[2])
    }

    @Test
    fun unsupportedPlaceholdersNeverMatchOrPass() {
        val trigger = dev.openmacro.core.model.catalog.Catalog.trigger(TriggerSpec.Unsupported("x"))
        assertFalse(trigger.matches(TriggerSpec.Unsupported("x"), TriggerEvent.Boot))
        // Looking them up must not throw, or the editor and engine would crash on load.
        dev.openmacro.core.model.catalog.Catalog.action(ActionSpec.Unsupported("x")).summarize(ActionSpec.Unsupported("x"))
        dev.openmacro.core.model.catalog.Catalog.constraint(ConstraintSpec.Unsupported("x"))
    }

    @Test
    fun unknownKeysAreIgnored() {
        val raw = """{"type":"screen_state","on":false,"futureField":42}"""
        assertEquals(TriggerSpec.ScreenState(false), SpecJson.decodeTrigger(raw))
    }
}
