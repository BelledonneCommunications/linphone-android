package org.linphone.models.realtime.interfaces

interface IUserForwardState : IProvisioningState {
    fun clone(): IUserForwardState
}
