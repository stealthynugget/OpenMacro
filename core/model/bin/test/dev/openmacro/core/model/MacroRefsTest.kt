package dev.openmacro.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MacroRefsTest {

    private val map: (MacroRef) -> MacroRef = { ref -> if (ref.id == 10L) MacroRef(99, ref.name) else ref }

    private fun macro(vararg actions: ActionSpec, triggers: List<TriggerSpec> = emptyList(), constraints: List<ConstraintSpec> = emptyList()) =
        Macro(
            triggers = triggers.map { TriggerItem(it) },
            actions = actions.map { ActionItem(it) },
            constraints = constraints.map { ConstraintItem(it) },
        )

    @Test
    fun actionRefsAreRemapped() {
        val out = MacroRefs.remap(
            macro(
                ActionSpec.RunMacro(MacroRef(10, "a")),
                ActionSpec.EnableMacro(MacroRef(10, "a")),
                ActionSpec.CancelMacro(MacroRef(10, "a")),
            ),
            map,
        )
        out.actions.forEach { item ->
            val ref = when (val s = item.spec) {
                is ActionSpec.RunMacro -> s.macro
                is ActionSpec.EnableMacro -> s.macro
                is ActionSpec.CancelMacro -> s.macro
                else -> error("unexpected")
            }
            assertEquals(99L, ref.id)
        }
    }

    @Test
    fun triggerAndConstraintRefsAreRemapped() {
        val out = MacroRefs.remap(
            macro(
                triggers = listOf(TriggerSpec.MacroFinished(MacroRef(10, "a")), TriggerSpec.MacroEnabledChanged(MacroRef(10, "a"))),
                constraints = listOf(ConstraintSpec.MacroEnabled(MacroRef(10, "a")), ConstraintSpec.MacroRunning(MacroRef(10, "a"))),
            ),
            map,
        )
        assertEquals(99L, (out.triggers[0].spec as TriggerSpec.MacroFinished).macro.id)
        assertEquals(99L, (out.triggers[1].spec as TriggerSpec.MacroEnabledChanged).macro.id)
        assertEquals(99L, (out.constraints[0].spec as ConstraintSpec.MacroEnabled).macro.id)
        assertEquals(99L, (out.constraints[1].spec as ConstraintSpec.MacroRunning).macro.id)
    }

    @Test
    fun nestedConditionsAreRemapped() {
        val nested = ConstraintSpec.Logic(LogicOp.AND, listOf(ConstraintSpec.MacroEnabled(MacroRef(10, "a"))))
        val out = MacroRefs.remap(macro(ActionSpec.If(conditions = listOf(nested))), map)
        val logic = (out.actions[0].spec as ActionSpec.If).conditions[0] as ConstraintSpec.Logic
        assertEquals(99L, (logic.constraints[0] as ConstraintSpec.MacroEnabled).macro.id)
    }

    @Test
    fun perActionConstraintsAreRemapped() {
        val item = ActionItem(ActionSpec.Wait(), constraints = listOf(ConstraintSpec.MacroEnabled(MacroRef(10, "a"))))
        val out = MacroRefs.remap(Macro(actions = listOf(item)), map)
        assertEquals(99L, (out.actions[0].constraints[0] as ConstraintSpec.MacroEnabled).macro.id)
    }

    @Test
    fun unmappedRefsAndOtherSpecsAreUntouched() {
        val original = macro(ActionSpec.RunMacro(MacroRef(5, "other")), ActionSpec.Wait(2))
        assertEquals(original, MacroRefs.remap(original, map))
    }
}
