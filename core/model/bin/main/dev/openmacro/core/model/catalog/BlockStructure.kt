package dev.openmacro.core.model.catalog

import dev.openmacro.core.model.ActionSpec

/**
 * Static analysis of the flat action list: matching block markers, nesting depth for the editor
 * and structural errors. Computed once per run so the interpreter jumps in O(1).
 */
class BlockStructure private constructor(
    /** For If/ElseIf/Else: index of the next branch marker (ElseIf/Else/EndIf). */
    val nextBranch: IntArray,
    /** For If/ElseIf/Else: index of the closing EndIf. */
    val endIf: IntArray,
    /** For Loop: index of its EndLoop; for EndLoop: index of its Loop. */
    val loopPartner: IntArray,
    /** For Break/Continue: index of the enclosing Loop. */
    val enclosingLoop: IntArray,
    val depth: IntArray,
    val errors: List<String>,
) {
    val isValid: Boolean get() = errors.isEmpty()

    companion object {
        fun analyze(actions: List<ActionSpec>): BlockStructure {
            val n = actions.size
            val nextBranch = IntArray(n) { -1 }
            val endIf = IntArray(n) { -1 }
            val loopPartner = IntArray(n) { -1 }
            val enclosingLoop = IntArray(n) { -1 }
            val depth = IntArray(n)
            val errors = mutableListOf<String>()

            // Each open frame: (index of opening marker, branch markers seen so far, isLoop).
            data class Frame(val start: Int, val branches: MutableList<Int>, val loop: Boolean, var sawElse: Boolean = false)
            val stack = ArrayDeque<Frame>()

            actions.forEachIndexed { i, action ->
                val role = Catalog.action(action).block
                depth[i] = stack.size
                when (role) {
                    BlockRole.IF_START -> stack.addLast(Frame(i, mutableListOf(i), loop = false))
                    BlockRole.LOOP_START -> stack.addLast(Frame(i, mutableListOf(i), loop = true))
                    BlockRole.ELSE_IF, BlockRole.ELSE -> {
                        val frame = stack.lastOrNull()
                        if (frame == null || frame.loop) {
                            errors += "Step ${i + 1}: ${Catalog.action(action).title} outside an If block"
                        } else {
                            if (frame.sawElse) errors += "Step ${i + 1}: branch after Else"
                            if (role == BlockRole.ELSE) frame.sawElse = true
                            depth[i] = stack.size - 1
                            frame.branches += i
                        }
                    }
                    BlockRole.IF_END -> {
                        val frame = stack.lastOrNull()
                        if (frame == null || frame.loop) {
                            errors += "Step ${i + 1}: End If without a matching If"
                        } else {
                            stack.removeLast()
                            depth[i] = stack.size
                            val markers = frame.branches + i
                            for (k in 0 until markers.size - 1) {
                                nextBranch[markers[k]] = markers[k + 1]
                                endIf[markers[k]] = i
                            }
                        }
                    }
                    BlockRole.LOOP_END -> {
                        val frame = stack.lastOrNull()
                        if (frame == null || !frame.loop) {
                            errors += "Step ${i + 1}: End Loop without a matching Loop"
                        } else {
                            stack.removeLast()
                            depth[i] = stack.size
                            loopPartner[frame.start] = i
                            loopPartner[i] = frame.start
                        }
                    }
                    BlockRole.LOOP_JUMP -> {
                        val loop = stack.lastOrNull { it.loop }
                        if (loop == null) errors += "Step ${i + 1}: ${Catalog.action(action).title} outside a loop"
                        else enclosingLoop[i] = loop.start
                    }
                    BlockRole.NONE -> Unit
                }
            }
            stack.forEach { frame ->
                errors += "Step ${frame.start + 1}: ${Catalog.action(actions[frame.start]).title} is never closed"
            }
            return BlockStructure(nextBranch, endIf, loopPartner, enclosingLoop, depth, errors)
        }
    }
}
