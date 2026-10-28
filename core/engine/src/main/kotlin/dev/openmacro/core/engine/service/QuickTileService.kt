package dev.openmacro.core.engine.service

import android.service.quicksettings.TileService
import dev.openmacro.core.engine.engine
import dev.openmacro.core.model.TriggerEvent

/** A Quick Settings tile that fires the "Quick Settings tile" trigger when tapped. */
class QuickTileService : TileService() {
    override fun onClick() {
        super.onClick()
        engine.submit(TriggerEvent.QuickTileTapped)
    }
}
