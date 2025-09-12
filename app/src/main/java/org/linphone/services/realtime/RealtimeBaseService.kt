package org.linphone.services.realtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import com.microsoft.signalr.TransportEnum
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.linphone.authentication.AuthStateManager
import org.linphone.environment.DimensionsEnvironmentService
import org.linphone.models.AuthenticatedUser
import org.linphone.models.DimensionsEnvironment
import org.linphone.models.realtime.EventSubscription
import org.linphone.models.realtime.RealtimeEventPresence
import org.linphone.models.realtime.RealtimeEventType
import org.linphone.models.realtime.SubscriptionData
import org.linphone.models.realtime.SubscriptionRequest
import org.linphone.models.realtime.SubscriptionState
import org.linphone.models.realtime.UnsubscriptionRequest
import org.linphone.utils.Log

// TODO - add errornotification service
open class RealtimeBaseService(private val context: Context, private val hubSuffix: String) : DefaultLifecycleObserver {

    private val environmentService = DimensionsEnvironmentService.getInstance(context)
    private val authStateManager = AuthStateManager.getInstance(context)
    private val destroy = PublishSubject.create<Unit>()

    private var hubConnection: HubConnection? = null

    /** Emits each time the environment changes, once the SignalR hub has been configured for that environment */
    private val environmentConfigured = BehaviorSubject.createDefault(false)

    private var currentEnvironmentSubscription: Disposable? =
        environmentService.currentEnvironmentObservable.subscribe { environment ->
            try {
                createHubConnection(environment)
            } catch (e: Exception) {
                Log.e(e, "RealtimeBaseService.currentEnvironmentSubscription")
            }
        }

    private var hubConnectionCompletable: Disposable? = null

    private val isReadySubject = BehaviorSubject.createDefault(false)

    /** Emits when the network connection changes */
    private val isOnlineSubject = BehaviorSubject.createDefault(false)

    /** Emits whenever the logged-in user changes */
    private val distinctUser = authStateManager.user
        .doOnNext { user -> Log.i("User: $user, ${user?.id}") }
        .filter { u -> u.id != null }
        .distinctUntilChanged { user -> user.id ?: "" }

    // This is the entry point for starting/stopping the SignalR connection.
    private val connectionObs = Observable.combineLatest(
        environmentConfigured.filter { isConfigured -> isConfigured },
        distinctUser,
        isOnlineSubject
    ) { _, user, isOnline -> Pair(user, isOnline) }
        .takeUntil(destroy)
        .subscribe { pair ->
            try {
                if (pair.first.hasValidId()) {
                    if (pair.second) ensureConnected()
                } else {
                    Log.d("RealtimeBaseService: no user - close connection.")
                    hubConnection?.stop()
                }
            } catch (e: Exception) {
                Log.e(e, "RealtimeBaseService.userSubscription")
            }
        }

    private val networkMonitor = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            isOnlineSubject.onNext(true)
            Log.i("Network connection available")
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            isOnlineSubject.onNext(false)
            Log.w("Network connection lost!")
        }
    }

    val ready = isReadySubject.map { x -> x }

    private var reconnectContext: ReconnectContext? = null

    // Events
    val callHistoryEvent: MutableLiveData<Any> by lazy { MutableLiveData<Any>() }
    val presenceEvent: MutableLiveData<RealtimeEventPresence> by lazy { MutableLiveData<RealtimeEventPresence>() }

    init {
        Log.i("RealtimeBaseService: Monitor network...")

        val networkRequest: NetworkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerNetworkCallback(networkRequest, networkMonitor)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)

        Log.i("RealtimeBaseService DESTROYED")
        destroy.onNext(Unit)
        destroy.onComplete()

        currentEnvironmentSubscription?.dispose()
        hubConnectionCompletable?.dispose()
    }

    private fun createHubConnection(environment: DimensionsEnvironment) {
        Log.d("RealtimeBaseService.createHubConnection for ${environment.name}")

        val connection = HubConnectionBuilder.create(
            "${environment.realtimeApiUri}/$hubSuffix"
        )
            .shouldSkipNegotiate(true)
            .withTransport(TransportEnum.WEBSOCKETS)
            .withAccessTokenProvider(
                Single.defer {
                    Single.just(
                        authStateManager.current.accessToken ?: throw Exception(
                            "Auth token is missing or expired"
                        )
                    )
                }
            )
            .build()

        connection.on(RealtimeEventType.ConnectEvent.eventName, { message: Any ->
            try {
                Log.d("RealtimeBaseService.${RealtimeEventType.ConnectEvent.eventName}: $message")

                runBlocking {
                    delay(1)
                    invokePendingHubRequests()
                }

                isReadySubject.onNext(true)
                onConnected()
            } catch (e: Exception) {
                Log.e(e, "RealtimeBaseService." + RealtimeEventType.ConnectEvent.eventName)
            }
        }, Any::class.java)

        connection.on(RealtimeEventType.SubscribeResponse.eventName, { message: Any ->
            Log.d("RealtimeBaseService.${RealtimeEventType.SubscribeResponse.eventName}: $message")
        }, Any::class.java)

        connection.on(RealtimeEventType.UnSubscribeResponse.eventName, { message: Any ->
            Log.d(
                "RealtimeBaseService.${RealtimeEventType.UnSubscribeResponse.eventName}: $message"
            )
        }, Any::class.java)

        connection.onClosed {
            try {
                Log.w("RealtimeBaseService.connection closed")
                onDisconnected()
                // TODO Add warning notification
                CoroutineScope(Dispatchers.IO).launch {
                    tryReconnect()
                }
            } catch (e: Exception) {
                Log.w("RealtimeBaseService.onClosed ${e.message}")
            }
        }

        connection.on(
            RealtimeEventType.CallHistoryEvent.eventName,
            { data: Any ->
                Log.d("RealtimeBaseService.callHistoryEvent: $data")
                callHistoryEvent.postValue(data)
            },
            Any::class.java
        )

        connection.on(
            RealtimeEventType.PresenceEvent.eventName,
            { data: RealtimeEventPresence ->
                Log.d(
                    "RealtimeBaseService.presenceEvent: ${data.data.stateName} ${data.data.availability}"
                )
                presenceEvent.postValue(data)
            },
            RealtimeEventPresence::class.java
        )

        hubConnection = connection

        environmentConfigured.onNext(true)
    }

    private fun onConnected() {
        try {
            Log.d("RealtimeBaseService.OnConnected")
            // TODO Remove error notification
        } catch (e: Exception) {
            Log.e(e, "RealtimeBaseService.OnConnected")
        }
    }

    private fun onDisconnected() {
        try {
            Log.d("RealtimeService.OnDisconnected")
            // TODO Add error notification

            isReadySubject.onNext(false)

            this.subscriptions.forEach { s ->
                try {
                    if (s.value.subscriptionState == SubscriptionState.Subscribed) {
                        s.value.subscriptionState = SubscriptionState.FlaggedForSubscription
                    }
                } catch (e: Exception) {
                    Log.e(e, "RealtimeBaseService.onDisconnected")
                }
            }
        } catch (e: Exception) {
            Log.e(e, "RealtimeBaseService.OnConnected")
        }
    }

    private fun connect() {
        Log.d("RealtimeBaseService.connect")

        try {
            if (hubConnection != null) {
                hubConnectionCompletable?.dispose()
                hubConnectionCompletable = hubConnection!!.start()
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .subscribe(
                        {
                            Log.i("SignalR connected.")
                            reconnectContext = null
                        },
                        { e ->
                            Log.w("SignalR connect error: $e")
                            CoroutineScope(Dispatchers.IO).launch {
                                tryReconnect()
                            }
                        }
                    )
            } else {
                Log.w("RealtimeBaseService: hubconnection is not yet configured")
            }
        } catch (e: Exception) {
            Log.w(e, "RealtimeBaseService: failed to start connection.")
        }
    }

    private fun invoke(methodName: String, args: Any): Completable {
        val connection = hubConnection
        if (connection != null) {
            return connection.invoke("${methodName}Request", args)
        } else {
            throw Exception("realtime invoke called with no hubconnection")
        }
    }

    private suspend fun tryReconnect() {
        if (reconnectContext == null) reconnectContext = ReconnectContext(0.0)

        val delayMs = nextRetryDelayInMilliseconds(reconnectContext!!)
        reconnectContext!!.previousRetryCount++

        delay(delayMs)

        ensureConnected()
    }

    private fun nextRetryDelayInMilliseconds(reconnectContext: ReconnectContext): Long {
        val seconds = reconnectContext.previousRetryCount.pow(1.5).coerceAtMost(30.0)
        val delay = Math.round(seconds * 1000) + 1000
        Log.d("RealtimeBaseService.nextRetryDelayInMilliseconds in $delay ms.")
        return delay
    }

    private fun ensureConnected() {
        val connection = hubConnection
        if (connection != null && connection.connectionState != HubConnectionState.CONNECTED) {
            if (connection.connectionState != HubConnectionState.DISCONNECTED) {
                Log.d("RealtimeBaseService.ensureConnected: stopping connection")
                connection.stop()
            }

            val user = authStateManager.getUser()
            if (user != null && user.hasValidId()) {
                Log.d("RealtimeBaseService.ensureConnected: attempt connection...")
                connect()
            }
        }
    }

    private val subscriptions = mutableMapOf<String, EventSubscription>()

    private suspend fun invokePendingHubRequests() = runBlocking {
        Log.d("RealtimeBaseService.invokePendingHubRequests Un/subscribe all pending.")
        Log.d(
            "RealtimeBaseService.invokePendingHubRequests State: ${hubConnection?.connectionState}"
        )
        Log.d("RealtimeBaseService.invokePendingHubRequests Subscriptions: $subscriptions")

        // When SignalR connects, invoke any pending tasks to subscribe to/unsubscribe from events.
        if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
            val deferredCollection = subscriptions.map { (key, subscription) ->
                when (subscription.subscriptionState) {
                    SubscriptionState.FlaggedForSubscription -> {
                        async { subscribe(key, subscription) }
                    }
                    SubscriptionState.FlaggedForUnsubscription -> {
                        async { unsubscribe(key, subscription) }
                    }
                    else -> null
                }
            }.filterNotNull()

            deferredCollection.awaitAll()
        }
    }

    /** Creates a flow that watches for changes to the current user and maintains
     * a realtime event subscription filtered to that user. */
    fun subscribeForCurrentUser(eventType: RealtimeEventType): Observable<Any> {
        return authStateManager.user
            .startWithItem(AuthenticatedUser(AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER))
            .map { it.id.toString() }
            .buffer(2, 1)
            .map { (prevUserId, currentUserId) ->
                Log.d("RealtimeBaseService: User changed from $prevUserId to $currentUserId")
                runBlocking {
                    if (prevUserId != AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER && prevUserId != currentUserId) {
                        removeSubscription(eventType, prevUserId)
                    }
                    if (currentUserId != AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER && currentUserId != prevUserId) {
                        addSubscription(eventType, currentUserId)
                    }
                }
            }
    }

    fun addSubscription(
        eventType: RealtimeEventType,
        id: String,
        additionalData: Map<String, Any>? = null
    ) {
        val key = subscriptionKey(eventType, id)

        var subscription = subscriptions[key]

        // Only need to act if it doesn't exist (unsubscribed) or it's currently pending unsubscribe.
        if (subscription == null ||
            subscription.subscriptionState == SubscriptionState.FlaggedForUnsubscription ||
            subscription.subscriptionState == SubscriptionState.Unsubscribing
        ) {
            val data = SubscriptionData(eventType.eventName, id).apply {
                // additionalData?.forEach { (k, v) -> this.additionalData[k] = v } //TODO add property spreading for tileEvent
            }

            val request = SubscriptionRequest(data)
            subscription = EventSubscription(request, SubscriptionState.FlaggedForSubscription)
            subscriptions[key] = subscription

            subscribe(key, subscription)
        }
    }

    private fun subscribe(key: String, subscription: EventSubscription) {
        try {
            if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
                updateState(key, SubscriptionState.Subscribing)

                Log.d("Subscribing $key")

                invoke("subscribe", subscription.requestInfo)

                updateState(key, SubscriptionState.Subscribed)

                Log.d("Subscription $key subscribed.")
            }
        } catch (e: Exception) {
            if (subscription.subscriptionState == SubscriptionState.Subscribing) {
                updateState(key, SubscriptionState.FlaggedForSubscription)
            }
        }
    }

    suspend fun removeSubscription(
        eventType: RealtimeEventType,
        selector: String,
        delayMilliseconds: Long? = null
    ) {
        removeSubscriptionInternal(eventType, selector, delayMilliseconds)
    }

    suspend fun removeSubscription(
        eventType: RealtimeEventType,
        selector: (EventSubscription) -> Boolean,
        delayMilliseconds: Long? = null
    ) {
        val subscriptionIds = subscriptions
            .filter { (_, sub) -> selector(sub) }
            .map { (_, sub) -> sub.requestInfo.event.id }

        subscriptionIds.forEach { id ->
            removeSubscriptionInternal(eventType, id, delayMilliseconds)
        }
    }

    private suspend fun removeSubscriptionInternal(
        eventType: RealtimeEventType,
        id: String,
        delayMilliseconds: Long? = null
    ) {
        val key = subscriptionKey(eventType, id)
        Log.d("removeSubscription $key ${subscriptions[key]}")

        // Only need to act if a matching subscription entry exists and is subscribed/subscribing
        if (isState(
                key,
                SubscriptionState.FlaggedForSubscription,
                SubscriptionState.Subscribing,
                SubscriptionState.Subscribed
            )
        ) {
            val data = SubscriptionData(eventType.eventName, id)
            val request = UnsubscriptionRequest(data)
            val subscription =
                EventSubscription(request, SubscriptionState.FlaggedForUnsubscription)
            subscriptions[key] = subscription

            Log.d("Queued unsubscribe $key")

            // If desired, delay the unsubscribe by a few seconds to prevent unnecessary un/resubscribe
            // in the event we rapidly remove then re-add a subscription
            // (which is likely when switching user groups on the contacts page for example).
            delayMilliseconds?.let {
                delay(it)
            }

            // Check we still need to unsubscribe
            if (isState(key, SubscriptionState.FlaggedForUnsubscription)) {
                unsubscribe(key, subscription)
            } else {
                Log.d("Unsubscribe for $key no longer relevant ${subscription.subscriptionState}")
            }
        }
    }

    private fun unsubscribe(key: String, subscription: EventSubscription) {
        try {
            if (hubConnection?.connectionState == HubConnectionState.CONNECTED) {
                updateState(key, SubscriptionState.Unsubscribing)

                Log.d("Unsubscribing $key...")

                invoke("unSubscribe", subscription.requestInfo)

                subscriptions.remove(key)

                Log.d("Unsubscribed $key.")
            }
        } catch (e: Exception) {
            if (isState(key, SubscriptionState.Unsubscribing)) {
                updateState(key, SubscriptionState.FlaggedForUnsubscription)
            }
        }
    }

    private fun subscriptionKey(eventType: RealtimeEventType, id: String): String {
        return "$eventType:$id"
    }

    private fun updateState(key: String, state: SubscriptionState) {
        val subscription = subscriptions[key]
            ?: throw IllegalArgumentException(
                "Subscription $key not found attempting to update to $state"
            )

        subscriptions[key] = subscription.copy(subscriptionState = state)
    }

    private fun isState(key: String, vararg states: SubscriptionState): Boolean {
        val subscription = subscriptions[key]
        return subscription != null && states.contains(subscription.subscriptionState)
    }
}

data class ReconnectContext(var previousRetryCount: Double)
