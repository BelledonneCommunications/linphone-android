package org.linphone.services

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.activities.main.contact.viewmodels.UserGroupViewModel
import org.linphone.authentication.AuthStateManager
import org.linphone.authentication.AuthorizationServiceManager
import org.linphone.environment.DimensionsEnvironmentService
import org.linphone.models.AuthenticatedUser
import org.linphone.models.UserInfo
import org.linphone.models.contact.ContactDirectoryModel
import org.linphone.models.contact.ContactGroupItem
import org.linphone.models.contact.ContactItemModel
import org.linphone.models.usergroup.GroupUserSummaryModel
import org.linphone.models.usergroup.UserGroupModel
import org.linphone.utils.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber

class DirectoriesService(val context: Context) : DefaultLifecycleObserver {
    private val apiClient = APIClientService()
    private val dimensionsEnvironment =
        DimensionsEnvironmentService.getInstance(context).getCurrentEnvironment()
    private val authStateManager = AuthStateManager.getInstance(context)
    private val destroy = PublishSubject.create<Unit>()

    private var contactDirectoriesSubscription: Disposable? = null
    val contactDirectoriesSubject = BehaviorSubject.create<List<ContactDirectoryModel>>()
    val contactDirectories = contactDirectoriesSubject.map { x -> x }

    private var allUsersSubscription: Disposable? = null
    private val allUsersSubject = BehaviorSubject.create<List<UserInfo>>()
    private val allUsers = allUsersSubject.map { x -> x }

    val dialSearchTextSubject = BehaviorSubject.createDefault("")
    val dialSearchText = dialSearchTextSubject
        .map { x -> x }

    val searchResults: Observable<UserGroupViewModel>
        get() = dialSearchText
            .debounce(500, TimeUnit.MILLISECONDS)
            .map { formatSearchText(it) }
            .switchMap { text ->
                if (text.length >= 3) {
                    Log.i("searchResults($text)")
                    search(PhoneFormatterService.getInstance(context).getSearchNumber(text))
                } else {
                    Observable.just(UserGroupViewModel.empty())
                }
            }
            .share()
            .onErrorResumeNext { e: Throwable ->
                Log.e(e, "Error searching directories.")
                Observable.just(UserGroupViewModel.empty())
            }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)

        destroy.onNext(Unit)
        destroy.onComplete()

        // contactDirectoriesSubscription?.dispose()
        // allUsersSubscription?.dispose()
    }

    init {
        Log.d("Created DirectoriesService")

        contactDirectoriesSubscription = authStateManager.user
            .distinctUntilChanged { user -> user.id ?: "" }
            .takeUntil(destroy)
            .subscribe { user ->
                try {
                    Log.d("Directory user: " + user.name)
                    if ((user.id == null || user.id == AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER) && contactDirectoriesSubject.value != null) {
                        contactDirectoriesSubject.onNext(
                            listOf()
                        )
                    } else {
                        fetchContactDirectories()
                    }
                } catch (ex: Exception) {
                    Log.e(ex)
                }
            }

        allUsersSubscription = authStateManager.user
            .distinctUntilChanged { user -> user.id ?: "" }
            .takeUntil(destroy)
            .subscribe { user ->
                try {
                    Log.d("Directories user: " + user.name)
                    if ((user.id == null || user.id == AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER) && allUsersSubject.value != null) {
                        allUsersSubject.onNext(
                            listOf()
                        )
                    } else {
                        fetchAllUsers()
                    }
                } catch (ex: Exception) {
                    Log.e(ex)
                }
            }
    }

    companion object {
        private const val TAG: String = "DirectoriesService"

        private val instance: AtomicReference<DirectoriesService> =
            AtomicReference<DirectoriesService>()

        fun getInstance(context: Context): DirectoriesService {
            var svc = instance.get()
            if (svc == null) {
                svc = DirectoriesService(context.applicationContext)
                instance.set(svc)
            }
            return svc
        }
    }

    private fun fetchContactDirectories() {
        Log.d("Fetch contact directories...")

        apiClient.getUCGatewayService(
            dimensionsEnvironment!!.gatewayApiUri,
            AuthorizationServiceManager.getInstance(context).authorizationServiceInstance,
            AuthStateManager.getInstance(context)
        ).doGetContactDirectories()
            .enqueue(object : Callback<List<ContactDirectoryModel>> {
                override fun onFailure(call: Call<List<ContactDirectoryModel>>, t: Throwable) {
                    Log.e("Failed to fetch contact directories", t)
                }

                override fun onResponse(
                    call: Call<List<ContactDirectoryModel>>,
                    response: Response<List<ContactDirectoryModel>>
                ) {
                    Timber.d("Got contact directories from API")
                    response.body()?.let { contactDirectoriesSubject.onNext(it) }
                }
            })
    }

    private fun fetchAllUsers() {
        Log.d("Fetch all users...")

        apiClient.getUCGatewayService(
            dimensionsEnvironment!!.gatewayApiUri,
            AuthorizationServiceManager.getInstance(context).authorizationServiceInstance,
            AuthStateManager.getInstance(context)
        ).doGetAllUsers()
            .enqueue(object : Callback<List<UserInfo>> {
                override fun onFailure(call: Call<List<UserInfo>>, t: Throwable) {
                    Log.e("Failed to fetch all users", t)
                }

                override fun onResponse(
                    call: Call<List<UserInfo>>,
                    response: Response<List<UserInfo>>
                ) {
                    Timber.d("Got all users from API")
                    response.body()?.let { allUsersSubject.onNext(it) }
                }
            })
    }

    private fun search(searchText: String): Observable<UserGroupViewModel> {
        return Observable.zip(
            searchAllDirectories(searchText),
            searchAllUsers(searchText),
            BiFunction {
                    contacts, directories ->
                return@BiFunction mergeSearchItems(
                    contacts,
                    directories
                )
            }
        )
    }

    private fun mergeSearchItems(
        contacts: List<ContactItemModel>,
        users: List<GroupUserSummaryModel>
    ): UserGroupViewModel {
        val favourites = UserGroupService.getInstance(context).favouritesGroup

        val userGroupModel = UserGroupModel()
        userGroupModel.id = UserGroupViewModel.SEARCH_RESULTS_GROUP_NAME
        userGroupModel.name = context.resources.getString(R.string.contacts_searchResultsGroup)
        userGroupModel.contacts = contacts
        userGroupModel.users = users

        if (favourites != null) {
            contacts.forEach { c ->
                c.isInFavourites = favourites.friends.any { fu -> fu.refKey == c.id }
            }

            users.forEach { d ->
                d.isInFavourites = favourites.friends.any { fu -> fu.refKey == d.id }
            }
        }

        return UserGroupViewModel(userGroupModel)
    }

    private fun searchAllDirectories(searchText: String): Observable<List<ContactItemModel>> {
        return contactDirectories.map { params ->
            val contactItemLists = params.map { param ->
                // TODO: this needs reworking as its not parallel
                searchDirectory(param.id, searchText)
            }
            contactItemLists.flatten()
        }
            .onErrorResumeNext { e: Throwable ->
                Log.e(e, "Error searching directories.")
                Observable.just(listOf())
            }
    }

    private fun searchDirectory(id: String, searchText: String): List<ContactItemModel> {
        val response = apiClient.getUCGatewayService(
            dimensionsEnvironment!!.gatewayApiUri,
            AuthorizationServiceManager.getInstance(context).authorizationServiceInstance,
            AuthStateManager.getInstance(context)
        )
            .searchDirectory(id, searchText).execute()

        return if (response.isSuccessful && response.body() != null) {
            response.body()!!
        } else {
            listOf()
        }
    }

    private fun searchAllUsers(searchText: String): Observable<List<GroupUserSummaryModel>> {
        Log.i("searchAllUsers::$searchText")
        // return Observable.just(listOf())
        val lowerSearchText = searchText.lowercase()

        val currentUserId = AuthStateManager.getInstance(context).getUser().id

        return allUsers.map { allUsers ->
            allUsers
                .filter { user ->
                    user.id != currentUserId && user.displayName.lowercase().indexOf(
                        lowerSearchText
                    ) > -1
                }
                .map { user -> toGroupUserSummaryModel(user) }
        }
    }

    private fun toGroupUserSummaryModel(user: UserInfo): GroupUserSummaryModel {
        return GroupUserSummaryModel(
            user.id,
            user.displayName,
            user.email,
            user.presenceId,
            user.profileImageUrl
        )
    }

    private fun formatSearchText(searchText: String): String {
        if (searchText.isNotBlank() && isValidPhoneNumber(searchText)) {
            val pattern = "/[()*#+\\- ]/gi".toRegex()
            return searchText.replace(pattern, "")
        }
        return searchText
    }

    private fun isValidPhoneNumber(searchText: String): Boolean {
        val pattern = "^[-+*#() 0-9]+\$".toRegex()
        return pattern.matches(searchText)
    }

    fun removeUserFromFavourites(id: String) {
        Log.d("removeUserFromFavourites::$id")

        val userGroupService = UserGroupService.getInstance(context)
        val favorites = userGroupService.favouritesGroup
        if (favorites != null) {
            apiClient.getUCGatewayService(
                dimensionsEnvironment!!.gatewayApiUri,
                AuthorizationServiceManager.getInstance(context).authorizationServiceInstance,
                AuthStateManager.getInstance(context)
            ).doRemoveUserFromDirectory(favorites.id, id)
                .enqueue(object : Callback<Void> {
                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        Log.e("Failed to remove user $id from favourites", t)
                        Toast.makeText(
                            coreContext.context,
                            coreContext.context.getString(
                                R.string.contacts_failedToRemoveUserFromFavorites
                            ),
                            Toast.LENGTH_LONG
                        ).show()

                        userGroupService.fetchUserGroups()
                    }

                    override fun onResponse(
                        call: Call<Void>,
                        response: Response<Void>
                    ) {
                        if (response.isSuccessful) {
                            Log.i("Removed user $id from favourites")

                            Toast.makeText(
                                coreContext.context,
                                coreContext.context.getString(
                                    R.string.contacts_successfullyRemovedUserFromFavorites
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Log.e("Failed to remove user $id from favourites:: ${response.code()}")
                            Toast.makeText(
                                coreContext.context,
                                coreContext.context.getString(
                                    R.string.contacts_failedToRemoveUserFromFavorites
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        userGroupService.fetchUserGroups()
                    }
                })
        }
    }

    fun addUserToFavourites(id: String) {
        Log.d("addUserToFavourites::$id")

        val userGroupService = UserGroupService.getInstance(context)
        val favorites = userGroupService.favouritesGroup

        if (favorites != null) {
            apiClient.getUCGatewayService(
                dimensionsEnvironment!!.gatewayApiUri,
                AuthorizationServiceManager.getInstance(context).authorizationServiceInstance,
                AuthStateManager.getInstance(context)
            ).doAddUserToUserDirectory(favorites.id, arrayOf(id))
                .enqueue(object : Callback<Void> {
                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        Log.e("Failed to add user $id to favourites", t)

                        Toast.makeText(
                            coreContext.context,
                            coreContext.context.getString(
                                R.string.contacts_failedToAddUserToFavorites
                            ),
                            Toast.LENGTH_LONG
                        ).show()

                        userGroupService.fetchUserGroups()
                    }

                    override fun onResponse(
                        call: Call<Void>,
                        response: Response<Void>
                    ) {
                        if (response.isSuccessful) {
                            Log.i("Added user $id to favourites")

                            Toast.makeText(
                                coreContext.context,
                                coreContext.context.getString(
                                    R.string.contacts_successfullyAddedUserToFavorites
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Log.e("Failed to add user $id to favourites::${response.code()}")

                            Toast.makeText(
                                coreContext.context,
                                coreContext.context.getString(
                                    R.string.contacts_failedToAddUserToFavorites
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        userGroupService.fetchUserGroups()
                    }
                })
        }
    }

    fun removeContactFromFavourites(id: String) {
        Log.d("removeContactFromFavourites::$id")

        val userGroupService = UserGroupService.getInstance(context)
        val favorites = userGroupService.favouritesGroup

        if (favorites != null) {
            apiClient.getUCGatewayService(
                dimensionsEnvironment!!.gatewayApiUri,
                AuthorizationServiceManager.getInstance(context).authorizationServiceInstance,
                AuthStateManager.getInstance(context)
            ).doRemoveContactFromDirectory(favorites.id, id)
                .enqueue(object : Callback<Void> {
                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        Log.e("Failed to remove contact $id from favourites", t)

                        Toast.makeText(
                            coreContext.context,
                            coreContext.context.getString(
                                R.string.contacts_failedToRemoveContactFromFavorites
                            ),
                            Toast.LENGTH_LONG
                        ).show()

                        userGroupService.fetchUserGroups()
                    }

                    override fun onResponse(
                        call: Call<Void>,
                        response: Response<Void>
                    ) {
                        if (response.isSuccessful) {
                            Log.i("Removed contact $id from favourites")

                            Toast.makeText(
                                coreContext.context,
                                coreContext.context.getString(
                                    R.string.contacts_successfullyRemovedContactFromFavorites
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Log.e(
                                "Failed to remove contact $id from favourites::${response.code()}"
                            )

                            Toast.makeText(
                                coreContext.context,
                                coreContext.context.getString(
                                    R.string.contacts_failedToRemoveContactFromFavorites
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        userGroupService.fetchUserGroups()
                    }
                })
        }
    }

    fun addContactToFavourites(contact: ContactItemModel) {
        Log.d("addContactToFavourites::${contact.id}")

        val userGroupService = UserGroupService.getInstance(context)
        val favorites = userGroupService.favouritesGroup

        if (favorites != null) {
            val contactGroupItem = ContactGroupItem(contact.directoryId, contact.id)

            apiClient.getUCGatewayService(
                dimensionsEnvironment!!.gatewayApiUri,
                AuthorizationServiceManager.getInstance(context).authorizationServiceInstance,
                AuthStateManager.getInstance(context)
            ).doAddContactToDirectory(favorites.id, contactGroupItem)
                .enqueue(object : Callback<Void> {
                    override fun onFailure(call: Call<Void>, t: Throwable) {
                        Log.e("Failed to add contact to favourites", t)

                        Toast.makeText(
                            coreContext.context,
                            coreContext.context.getString(
                                R.string.contacts_failedToAddContactToFavorites
                            ),
                            Toast.LENGTH_LONG
                        ).show()

                        userGroupService.fetchUserGroups()
                    }

                    override fun onResponse(
                        call: Call<Void>,
                        response: Response<Void>
                    ) {
                        if (response.isSuccessful) {
                            Log.i("Added contacts to favourites")
                            Toast.makeText(
                                coreContext.context,
                                coreContext.context.getString(
                                    R.string.contacts_successfullyAddedContactToFavorites
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Log.e("Failed to add contact to favourites::${response.code()}")

                            Toast.makeText(
                                coreContext.context,
                                coreContext.context.getString(
                                    R.string.contacts_failedToAddContactToFavorites
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        userGroupService.fetchUserGroups()
                    }
                })
        }
    }
}
