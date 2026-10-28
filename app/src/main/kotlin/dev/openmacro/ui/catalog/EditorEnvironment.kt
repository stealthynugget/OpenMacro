package dev.openmacro.ui.catalog

import androidx.compose.runtime.Immutable
import dev.openmacro.core.model.VarRef

/** A pick-able installed app. */
@Immutable
data class AppEntry(val packageName: String, val label: String)

/** A pick-able macro. */
@Immutable
data class MacroEntry(val id: Long, val name: String)

/** Data and callbacks the generic field editors need, supplied by the editor screen. */
@Immutable
data class EditorEnvironment(
    val variables: List<VarRef>,
    val macros: List<MacroEntry>,
    val apps: () -> List<AppEntry>,
    val includeNetwork: Boolean,
    /** Launches a system picker for a sound or image; the chosen content URI comes back in [onPicked]. */
    val pickUri: (kind: PickKind, onPicked: (String) -> Unit) -> Unit,
    /** Resolves the device's current location, reporting progress and the outcome via [onStatus]. */
    val requestLocation: (onStatus: (LocationStatus) -> Unit) -> Unit,
)

enum class PickKind { SOUND, IMAGE }

/** Progress of a "use current location" lookup, shown inline in the location field. */
sealed interface LocationStatus {
    data object Searching : LocationStatus
    data class Found(val latitude: Double, val longitude: Double) : LocationStatus
    data class Failed(val message: String, val offerSettings: Boolean) : LocationStatus
}
