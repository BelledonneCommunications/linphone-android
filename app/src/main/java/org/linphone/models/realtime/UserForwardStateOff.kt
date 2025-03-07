package org.linphone.models.realtime

import org.linphone.models.realtime.interfaces.IUserForwardState

class UserForwardStateOff : IUserForwardState {
    internal val type: String? = this::class.simpleName

    override fun clone(): IUserForwardState {
        return UserForwardStateOff()
    }

    override fun equals(other: Any?): Boolean {
        return other is UserForwardStateOff &&
            other.type == type
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }
}
