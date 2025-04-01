package org.linphone.models.callhistory

enum class CallTypes(val callTypeValue: Int) {
    Unknown(0),
    Internal(1),
    External(2);

    companion object {
        // Helper function to get the enum constant by its value
        fun fromValue(value: Int): CallTypes {
            return values().find { it.callTypeValue == value } ?: Unknown
        }
    }
}
