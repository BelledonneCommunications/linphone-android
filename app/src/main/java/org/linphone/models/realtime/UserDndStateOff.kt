package org.linphone.models.realtime

import org.linphone.models.realtime.interfaces.IUserDndState

class UserDndStateOff : IUserDndState {
    internal val type: String? = this::class.simpleName

    override fun clone(): IUserDndState {
        return UserDndStateOff()
    }

    override fun equals(other: Any?): Boolean {
        return other is UserDndStateOff &&
            other.type == type
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }
}
