package dev.openmacro.core.model.catalog

import dev.openmacro.core.model.ActionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockStructureTest {

    @Test
    fun matchesIfElseEndIf() {
        val actions = listOf(
            ActionSpec.If(),
            ActionSpec.ShowToast("a"),
            ActionSpec.Else,
            ActionSpec.ShowToast("b"),
            ActionSpec.EndIf,
        )
        val s = BlockStructure.analyze(actions)
        assertTrue(s.isValid)
        assertEquals(2, s.nextBranch[0]) // if -> else
        assertEquals(4, s.endIf[0]) // if -> endif
        assertEquals(4, s.endIf[2]) // else -> endif
    }

    @Test
    fun matchesLoopAndJumps() {
        val actions = listOf(
            ActionSpec.Loop(),
            ActionSpec.BreakLoop,
            ActionSpec.EndLoop,
        )
        val s = BlockStructure.analyze(actions)
        assertTrue(s.isValid)
        assertEquals(2, s.loopPartner[0])
        assertEquals(0, s.loopPartner[2])
        assertEquals(0, s.enclosingLoop[1])
    }

    @Test
    fun reportsUnclosedBlock() {
        val s = BlockStructure.analyze(listOf(ActionSpec.If(), ActionSpec.ShowToast("x")))
        assertFalse(s.isValid)
    }

    @Test
    fun reportsStrayElse() {
        val s = BlockStructure.analyze(listOf(ActionSpec.Else))
        assertFalse(s.isValid)
    }

    @Test
    fun reportsBreakOutsideLoop() {
        val s = BlockStructure.analyze(listOf(ActionSpec.BreakLoop))
        assertFalse(s.isValid)
    }

    @Test
    fun nestedBlocksTrackDepth() {
        val actions = listOf(
            ActionSpec.Loop(),      // 0 depth 0
            ActionSpec.If(),        // 1 depth 1
            ActionSpec.ShowToast(), // 2 depth 2
            ActionSpec.EndIf,       // 3 depth 1
            ActionSpec.EndLoop,     // 4 depth 0
        )
        val s = BlockStructure.analyze(actions)
        assertTrue(s.isValid)
        assertEquals(listOf(0, 1, 2, 1, 0), s.depth.toList())
    }
}
