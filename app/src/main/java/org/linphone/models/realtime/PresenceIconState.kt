package org.linphone.models.realtime

import org.linphone.core.ConsolidatedPresence

enum class PresenceIconState(val stateName: String) {
    Unknown("unknown"),
    Available("available"),
    AvailableACD("available_acd"),
    BusyACDCall("busy_acdcall"),
    BusyNonACDCall("busy_nonacdcall"),
    BusyCall("busy_call"),
    DND("dnd"),
    Unavailable("unavailable"),
    UnavailableACD("unavailable_acd"),
    WrapUpACD("wrapup_acd");

    companion object {
        fun toColour(value: PresenceIconState): String {
            return when (value) {
                Available -> "green"
                AvailableACD -> "green"
                BusyACDCall -> "red"
                BusyNonACDCall -> "red"
                BusyCall -> "red"
                DND -> "red"
                Unavailable -> "yellow"
                UnavailableACD -> "yellow"
                WrapUpACD -> "yellow"
                else -> "grey"
            }
        }

        fun toInt(value: PresenceIconState?): Int {
            return when (value) {
                Available -> 0
                AvailableACD -> 0
                BusyACDCall -> 1
                BusyNonACDCall -> 1
                BusyCall -> 1
                DND -> 1
                Unavailable -> 2
                UnavailableACD -> 2
                WrapUpACD -> 2
                else -> 4
            }
        }

        fun toConsolidatedPresence(value: PresenceIconState?): ConsolidatedPresence {
            return when (value) {
                Available -> ConsolidatedPresence.fromInt(0)
                AvailableACD -> ConsolidatedPresence.fromInt(0)
                BusyACDCall -> ConsolidatedPresence.fromInt(2)
                BusyNonACDCall -> ConsolidatedPresence.fromInt(2)
                BusyCall -> ConsolidatedPresence.fromInt(2)
                DND -> ConsolidatedPresence.fromInt(2)
                Unavailable -> ConsolidatedPresence.fromInt(1)
                UnavailableACD -> ConsolidatedPresence.fromInt(1)
                WrapUpACD -> ConsolidatedPresence.fromInt(1)
                else -> ConsolidatedPresence.fromInt(4)
            }
        }

        fun fromString(stateName: String): PresenceIconState {
            return values().firstOrNull { it.stateName == stateName } ?: Unknown
        }
    }
}
