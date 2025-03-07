package org.linphone.activities.main.presence.viewmodels

import androidx.databinding.ObservableField
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.authentication.AuthStateManager
import org.linphone.core.ConsolidatedPresence
import org.linphone.models.AuthenticatedUser
import org.linphone.models.UserInfo
import org.linphone.models.realtime.PresenceIconState
import org.linphone.models.realtime.PresenceProfile
import org.linphone.models.realtime.SetPresenceModel
import org.linphone.services.PresenceProfileService
import org.linphone.services.PresenceService
import org.linphone.utils.Log

class PresenceEditorViewModel : ViewModel() {
    private val presenceService = PresenceService.getInstance(coreContext.context)
    private val presenceProfileService = PresenceProfileService.getInstance(coreContext.context)

    val userImageUrl = ObservableField<String>()

    val user = ObservableField<UserInfo>()

    val presenceStatus = MutableLiveData<ConsolidatedPresence>()

    val presenceProfile = MutableLiveData<PresenceProfile>()

    val statusMessage = MutableLiveData<String>()

    private var presenceSubscription: Disposable? = null

    init {

        presenceSubscription = Observable.combineLatest(
            presenceService.currentUserPresence,
            presenceProfileService.presenceProfiles
        ) { currentUserPresence, presenceProfiles -> Pair(currentUserPresence, presenceProfiles) }
            .subscribe { pair ->
                try {
                    if (pair.first.isPresent()) {
                        val eventData = pair.first.get()
                        presenceStatus.postValue(
                            PresenceIconState.toConsolidatedPresence(
                                PresenceIconState.fromString(eventData.iconState)
                            )
                        )

                        val selectedPresence = pair.second.single { it.id == eventData.stateId }
                        presenceProfile.postValue(selectedPresence)
                        statusMessage.postValue(eventData.message ?: "")
                    } else {
                        presenceStatus.postValue(
                            PresenceIconState.toConsolidatedPresence(
                                null
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("presenceSubscription", e)
                }
            }
    }

    fun applyPresence() {
        try {
            val userId = AuthStateManager.getInstance(coreContext.context).getUser().id
            if (userId.isNullOrBlank() || userId == AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER) {
                Log.i("applyPresence called with no authed user")
                return
            }

            val selectedProfile = presenceProfile.value
            val selectedProfileMessage = statusMessage.value
            if (selectedProfile != null) {
                val currentPresence = presenceService.getCurrent(userId)
                val currentPresenceHasChanged = currentPresence == null || currentPresence.stateId != selectedProfile.id

                if (currentPresence == null || currentPresenceHasChanged || currentPresence.message != selectedProfileMessage) {
                    val message = if (currentPresenceHasChanged) selectedProfile.message else selectedProfileMessage

                    val selectedMessage = statusMessage.value
                    val setPresenceModel = SetPresenceModel(
                        selectedProfile.id ?: "",
                        message ?: "",
                        selectedProfile.dnd,
                        selectedProfile.forward,
                        selectedProfile.callRouting,
                        selectedProfile.acd,
                        selectedProfile.enablePersonalRoutingGroup,
                        selectedProfile.hideFromSelection
                    )

                    presenceService.setPresenceState(userId, setPresenceModel)
                }
            }
        } catch (e: Exception) {
            Log.e("applyPresence", e)
        }
    }
}
