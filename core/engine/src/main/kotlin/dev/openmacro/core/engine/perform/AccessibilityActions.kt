package dev.openmacro.core.engine.perform

import dev.openmacro.core.engine.accessibility.OpenMacroAccessibilityService
import dev.openmacro.core.model.ActionSpec as A

/** UI-automation actions, delegated to the accessibility service when the user has enabled it. */
internal class AccessibilityActions {

    suspend fun perform(spec: A, ctx: ActionContext): ActionOutcome? {
        val handled = spec is A.PressButton || spec is A.TapScreen || spec is A.TapElement ||
            spec is A.Swipe || spec is A.InputText || spec is A.ReadScreen
        if (!handled) return null

        val service = OpenMacroAccessibilityService.instance
            ?: return ActionOutcome.Failed("Enable the OpenMacro accessibility service to use UI automation")

        return when (spec) {
            is A.PressButton -> service.pressButton(spec.button).toOutcome("Button press rejected")
            is A.TapScreen -> service.tap(spec.x, spec.y, spec.longPress).toOutcome("Tap failed")
            is A.TapElement -> {
                val text = ctx.render(spec.text)
                if (text.isBlank()) ActionOutcome.Failed("No element text") else service.tapElement(text, spec.longPress).toOutcome("No matching element on screen")
            }
            is A.Swipe -> service.swipe(spec.startX, spec.startY, spec.endX, spec.endY, spec.durationMs).toOutcome("Swipe failed")
            is A.InputText -> service.inputText(ctx.render(spec.text)).toOutcome("No editable field focused")
            is A.ReadScreen -> {
                ctx.setVariableValue(spec.target, service.readScreenText())
                ActionOutcome.Success
            }
            else -> null
        }
    }

    private fun Boolean.toOutcome(failure: String): ActionOutcome = if (this) ActionOutcome.Success else ActionOutcome.Failed(failure)
}
