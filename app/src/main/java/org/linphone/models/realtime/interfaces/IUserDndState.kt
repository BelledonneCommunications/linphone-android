package org.linphone.models.realtime.interfaces

interface IUserDndState : IProvisioningState {
    fun clone(): IUserDndState
}
