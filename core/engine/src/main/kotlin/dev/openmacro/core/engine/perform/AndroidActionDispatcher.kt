package dev.openmacro.core.engine.perform

import android.content.Context
import dev.openmacro.core.model.ActionSpec
import dev.openmacro.core.model.DeviceProbe

/** Routes an action spec to the first performer group that handles it. */
class AndroidActionDispatcher(app: Context, deviceProbe: DeviceProbe) {
    private val device = DeviceActions(app)
    private val apps = AppActions(app)
    private val data = DataActions(app)
    private val network = NetworkActions(app)
    private val accessibility = AccessibilityActions()
    private val system = SystemActions(app, deviceProbe)

    suspend fun perform(spec: ActionSpec, ctx: ActionContext): ActionOutcome {
        if (spec is ActionSpec.Unsupported) {
            return ActionOutcome.Failed("“${spec.originalType}” is no longer available in this version; edit the macro to remove it")
        }
        return route(spec, ctx)
    }

    private suspend fun route(spec: ActionSpec, ctx: ActionContext): ActionOutcome =
        device.perform(spec, ctx)
            ?: apps.perform(spec, ctx)
            ?: data.perform(spec, ctx)
            ?: network.perform(spec, ctx)
            ?: accessibility.perform(spec, ctx)
            ?: system.perform(spec, ctx)
            ?: ActionOutcome.Failed("Action not supported: ${spec::class.simpleName}")
}
