package org.linphone.models.callhistory

enum class CallDirections(val value: Int) {
    Unknown(0),
    Internal(1),
    Incoming(2),
    Outgoing(3),
    Both(4);

    companion object {
        // Helper function to get the enum constant by its value
        fun fromValue(value: Int): CallDirections {
            return values().find { it.value == value } ?: Unknown
        }
    }
}
