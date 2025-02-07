package org.linphone.services

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Function3
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.atomic.AtomicReference
import org.linphone.R
import org.linphone.activities.main.contact.viewmodels.UserGroupViewModel
import org.linphone.authentication.AuthStateManager
import org.linphone.authentication.AuthorizationServiceManager
import org.linphone.core.Friend
import org.linphone.environment.DimensionsEnvironmentService
import org.linphone.models.AuthenticatedUser
import org.linphone.models.contact.ContactItemModel
import org.linphone.models.search.UserDataModel
import org.linphone.models.usergroup.GroupUserSummaryModel
import org.linphone.models.usergroup.UserGroupModel
import org.linphone.utils.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber

class UserGroupService(val context: Context) : DefaultLifecycleObserver {
    private val apiClient = APIClientService()
    private val dimensionsEnvironment =
        DimensionsEnvironmentService.getInstance(context).getCurrentEnvironment()
    private val authStateManager = AuthStateManager.getInstance(context)
    private val destroy = PublishSubject.create<Unit>()

    private val tenantUserGroupsSubject = BehaviorSubject.create<List<UserGroupViewModel>>()
    private val personalUserGroupsSubject = BehaviorSubject.create<List<UserGroupViewModel>>()
    val localContactsSubject = BehaviorSubject.create<UserGroupViewModel>()

    var favouritesGroup: UserGroupViewModel? = null

    private var userSubscription: Disposable? = null
    private var contactDirectoriesSubscription: Disposable? = null

    val userGroups: Observable<List<UserGroupViewModel>> = Observable.combineLatest(
        tenantUserGroupsSubject,
        personalUserGroupsSubject,
        localContactsSubject,
        Function3 {
                tenantUserGroups, personalUserGroups, localContactsSubject ->
            return@Function3 mergeUserGroups(
                tenantUserGroups,
                personalUserGroups,
                localContactsSubject
            )
        }
    )

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)

        destroy.onNext(Unit)
        destroy.onComplete()

        userSubscription?.dispose()
        contactDirectoriesSubscription?.dispose()
    }

    init {
        Log.d("Created UserGroupService")

        contactDirectoriesSubscription = DirectoriesService.getInstance(context).contactDirectories
            .takeUntil(destroy)
            .subscribe {
                val user = authStateManager.getUser()
                try {
                    Log.d("ContactDirectory user: " + user.name)
                    if ((user.id == null || user.id == AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER) && tenantUserGroupsSubject.value != null) {
                        tenantUserGroupsSubject.onNext(
                            listOf()
                        )

                        personalUserGroupsSubject.onNext(
                            listOf()
                        )
                    } else {
                        fetchUserGroups()
                    }
                } catch (ex: Exception) {
                    Log.e(ex)
                }
            }
    }

    companion object {
        private const val TAG: String = "UserGroupService"

        private val instance: AtomicReference<UserGroupService> =
            AtomicReference<UserGroupService>()

        fun getInstance(context: Context): UserGroupService {
            var svc = instance.get()
            if (svc == null) {
                svc = UserGroupService(context.applicationContext)
                instance.set(svc)
            }
            return svc
        }
    }

    fun fetchUserGroups() {
        fetchTenantUserGroups()
        fetchPersonalUserGroups()
    }

    private fun fetchTenantUserGroups() {
        Log.d("Fetch tenant user groups...")

        apiClient.getUCGatewayService(
            dimensionsEnvironment!!.gatewayApiUri,
            AuthorizationServiceManager.getInstance(context).authorizationServiceInstance,
            AuthStateManager.getInstance(context)
        ).doGetTenantUserGroups()
            .enqueue(object : Callback<List<UserGroupModel>> {
                override fun onFailure(call: Call<List<UserGroupModel>>, t: Throwable) {
                    Log.e("Failed to fetch tenant user groups", t)
                }

                override fun onResponse(
                    call: Call<List<UserGroupModel>>,
                    response: Response<List<UserGroupModel>>
                ) {
                    Timber.d("Got tenant user groups from API")

                    val userGroupViewModels = arrayListOf<UserGroupViewModel>()
                    response.body()?.let {
                        for (userGroupModel in it) {
                            userGroupViewModels.add(UserGroupViewModel(userGroupModel))
                        }
                    }
                    tenantUserGroupsSubject.onNext(userGroupViewModels)
                }
            })
    }

    private fun fetchPersonalUserGroups() {
        Log.d("Fetch personal user groups...")

        apiClient.getUCGatewayService(
            dimensionsEnvironment!!.gatewayApiUri,
            AuthorizationServiceManager.getInstance(context).authorizationServiceInstance,
            AuthStateManager.getInstance(context)
        ).doGetPersonalUserGroups()
            .enqueue(object : Callback<List<UserGroupModel>> {
                override fun onFailure(call: Call<List<UserGroupModel>>, t: Throwable) {
                    Log.e("Failed to fetch personal user groups", t)
                }

                override fun onResponse(
                    call: Call<List<UserGroupModel>>,
                    response: Response<List<UserGroupModel>>
                ) {
                    Timber.d("Got personal user groups from API")

                    val userGroupViewModels = arrayListOf<UserGroupViewModel>()
                    response.body()?.let {
                        for (userGroupModel in it) {
                            userGroupViewModels.add(UserGroupViewModel(userGroupModel))
                        }
                    }
                    personalUserGroupsSubject.onNext(userGroupViewModels)
                }
            })
    }

    private fun setIsFavorite(friend: Friend, isFavorite: Boolean) {
        val user = friend.userData as? GroupUserSummaryModel
        if (user != null) {
            user.isInFavourites = isFavorite
        }

        val contact = friend.userData as? ContactItemModel
        if (contact != null) {
            contact.isInFavourites = isFavorite
        }

        val searchItem = friend.userData as? UserDataModel
        if (searchItem != null) {
            searchItem.isInFavourites = isFavorite
        }
    }

    private fun mergeUserGroups(
        tenantUserGroups: List<UserGroupViewModel>,
        personalUserGroups: List<UserGroupViewModel>,
        localContactsUserGroup: UserGroupViewModel
    ): List<UserGroupViewModel> {
        val favorites = personalUserGroups.firstOrNull { x ->
            x.name == context.resources.getString(
                R.string.contacts_favoritesGroup
            )
        }
        if (favorites != null) {
            favorites.friends.forEach { f -> setIsFavorite(f, true) }

            tenantUserGroups.forEach { group ->
                group.friends.forEach { f ->
                    setIsFavorite(f, favorites.friends.any { fu -> fu.refKey == f.refKey })
                }
            }
        }

        favouritesGroup = favorites

        // Put favourites at the top, then order the rest alpha ascending with contacts at the end
        val sortedUserGroups = (personalUserGroups + tenantUserGroups).sortedWith(
            compareBy<UserGroupViewModel> {
                !it.isFavorites
            }.thenBy { it.name }
        )

        return sortedUserGroups + localContactsUserGroup
    }
}
