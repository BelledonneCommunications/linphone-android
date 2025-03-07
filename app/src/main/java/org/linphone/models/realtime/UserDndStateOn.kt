package org.linphone.models.realtime

import org.linphone.models.realtime.interfaces.IUserDndState

class UserDndStateOn() : IUserDndState {
    internal val type: String? = this::class.simpleName

    override fun clone(): IUserDndState {
        return UserDndStateOn()
    }

    override fun equals(other: Any?): Boolean {
        return other is UserDndStateOn &&
            other.type == type
    }

    override fun hashCode(): Int {
        return type.hashCode()
    }
}
