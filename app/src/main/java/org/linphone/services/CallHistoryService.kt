package org.linphone.services

import ReportResult
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Base64
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx3.rxSingle
import kotlinx.coroutines.withContext
import org.linphone.authentication.AuthStateManager
import org.linphone.models.AuthenticatedUser
import org.linphone.models.callhistory.CallHistoryCache
import org.linphone.models.callhistory.CallHistoryItem
import org.linphone.models.callhistory.CallHistoryItemViewModel
import org.linphone.models.callhistory.ReportRequest
import org.linphone.models.callhistory.ReportStates
import org.linphone.models.realtime.RealtimeEventType
import org.linphone.services.realtime.RealtimeUserService
import org.linphone.utils.CallHistoryDatabaseHelper
import org.linphone.utils.DateUtils
import org.linphone.utils.Log
import org.linphone.utils.Optional
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class CallHistoryService(val context: Context) : DefaultLifecycleObserver {
    private val authStateManager = AuthStateManager.getInstance(context)
    private val realtimeUserService = RealtimeUserService.getInstance(context)

    private val destroy = PublishSubject.create<Unit>()

    private val callHistorySubject = BehaviorSubject.createDefault<List<CallHistoryItem>>(listOf())
    val history: Observable<List<CallHistoryItem>> = callHistorySubject.hide()

    /** Each time a value is emitted, a new report request will be submitted.  */
    private val newReportRequest: PublishSubject<Unit> = PublishSubject.create()

    /** Each time a value is emitted and new attempt will be made to query the current report result. */
    private val queryStatusSubject = BehaviorSubject.createDefault(0)
    private val queryStatus = queryStatusSubject.map { x -> x }
        .replay(1)
        .autoConnect()

    private var statusQueryAttempts: Int = 0

    private val missedCallTimestampSubject = BehaviorSubject.create<ZonedDateTime>()
    private val missedCallTimestamp: Observable<ZonedDateTime> = missedCallTimestampSubject.hide()

    private val authSubscription: Disposable = authStateManager.user
        .filter { u -> u.id != null && u.id != AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER }
        .distinctUntilChanged { user -> user.id ?: "" }
        .takeUntil(destroy)
        .subscribe {
            CoroutineScope(Dispatchers.IO).launch {
                getMissedCallTimestamp()
            }
        }

    private val userId: Observable<String> = authStateManager.user
        .filter { u -> u.id != null && u.id != AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER }
        .distinctUntilChanged { user -> user.id ?: "" }
        .map { user ->
            user.id.toString()
        }
        .takeUntil(destroy)

    private val historyRequest: Observable<ReportRequest> = Observable.merge(
        userId,
        newReportRequest
    )
        .doOnNext {
            statusQueryAttempts = 0
        }
        .switchMap {
            val arr = callHistorySubject.value ?: emptyList()
            val maxStartTime = arr.maxOfOrNull {
                it.startTime.toInstant().toEpochMilli()
            } ?: 0L

            val fromDate = if (maxStartTime > 0) {
                val dateFormat = SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    Locale.getDefault()
                )
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                dateFormat.format(Date(maxStartTime))
            } else {
                null
            }

            rxSingle {
                try {
                    withContext(Dispatchers.IO) {
                        APIClientService(context)
                            .getUCGatewayService()
                            .postReportRequest(
                                mapOf(
                                    "fromDate" to fromDate,
                                    "timeZoneId" to "UTC"
                                )
                            )
                    }
                } catch (throwable: Throwable) {
                    Log.e("historyRequest", throwable)
                    ReportRequest(ReportStates.Failed.value, "") // Provide a fallback ReportResult
                }
            }.toObservable()
        }
        .share()
        .replay(1)
        .autoConnect()

    private val startCache = authStateManager.user
        .filter { u -> u.id != null && u.id != AuthenticatedUser.UNINTIALIZED_AUTHENTICATEDUSER }
        .distinctUntilChanged { user -> user.id ?: "" }
        .map { user ->
            getCachedCallHistory(user.id ?: "")
        }
        .takeUntil(destroy)

    private val reportQuery: Observable<ReportResult> = Observable.combineLatest(
        queryStatus,
        historyRequest
    ) { _, request ->
        request
    }
        .switchMap { request ->
            backoffQuery(request)
        }
        .doOnNext { r ->
            statusQueryAttempts++
            if (!ReportStates.isDone(r.status)) queueNextQuery(r.status)
        }
        .share()
        .replay(1)
        .autoConnect()

    val appendHistoryObservable = Observable.merge(
        startCache,
        reportQuery
            .filter { r ->
                ReportStates.isDone(r.status)
            }
            .map
            {
                    r ->
                r.data ?: listOf()
            }
            .doOnNext {
                    data ->
                Log.d("History report returned item count ${data.size}")
            }
    )
        .subscribe { data ->
            appendToHistory(data)
        }

    val missedCallCount: Observable<Int> = Observable.combineLatest(
        history,
        missedCallTimestamp,
        { history, timestamp ->
            try {
                history.filter {
                    it.missedCall && it.startTime > timestamp
                }.size
            } catch (e: Exception) {
                Log.e("missedCallCount", e)
                0
            }
        }
    )

    val formattedHistory: Observable<List<CallHistoryItemViewModel>> = Observable.combineLatest(
        history,
        DateUtils.todaysDate,
        { history, date ->
            transformData(history, date)
        }
    )
        .replay(1)
        .autoConnect()

    val historyMessage: Observable<String> = Observable.combineLatest(
        reportQuery,
        callHistorySubject
    ) { query, history ->
        when {
            !ReportStates.isDone(query.status) && history.isEmpty() -> "Loading call history..."
            history.isEmpty() -> "No calls"
            else -> ""
        }
    }

    val selectedCallSessionIdSubject: BehaviorSubject<String> = BehaviorSubject.createDefault("")

    val selectedCallSessionId = selectedCallSessionIdSubject.map { x -> x }

    val currentCallHistoryItemView: Observable<Optional<CallHistoryItemViewModel>> = Observable.combineLatest(
        selectedCallSessionId,
        formattedHistory
    ) { selectedCallId: String?, callHistory: List<CallHistoryItemViewModel> ->
        Optional.ofNullable(
            callHistory.find {
                it.callId == selectedCallId
            }
        )
    }
        .share()
        .replay(1)
        .autoConnect()

    companion object {
        private const val TAG: String = "CallHistoryService"
        private const val CACHEVERSION = 1

        private val instance: AtomicReference<CallHistoryService> =
            AtomicReference<CallHistoryService>()

        fun getInstance(context: Context): CallHistoryService {
            var svc = instance.get()
            if (svc == null) {
                svc = CallHistoryService(context.applicationContext)
                instance.set(svc)
            }
            return svc
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)

        destroy.onNext(Unit)
        destroy.onComplete()

        authSubscription.dispose()

        runBlocking {
            authStateManager.getUser().id?.let {
                realtimeUserService.removeSubscription(
                    RealtimeEventType.CallHistoryEvent,
                    it,
                    null
                )
            }
        }
    }

    init {
        Log.d("Created CallHistoryService")

        realtimeUserService.hubConnection?.on(RealtimeEventType.CallHistoryEvent.eventName, { event ->
            try {
                Log.d(RealtimeEventType.CallHistoryEvent.eventName, event)

                CoroutineScope(Dispatchers.IO).launch {
                    getMissedCallTimestamp()

                    newReportRequest.onNext(Unit)
                }
            } catch (e: Exception) {
                Log.e(RealtimeEventType.CallHistoryEvent.eventName, e)
            }
        }, Any::class.java)

        runBlocking {
            authStateManager.getUser().id?.let {
                realtimeUserService.addSubscription(
                    RealtimeEventType.CallHistoryEvent,
                    it,
                    null
                )
            }
        }

        newReportRequest.onNext(Unit)
    }

    private fun queueNextQuery(status: Int) {
        queryStatusSubject.onNext(status)
    }

    private fun backoffQuery(request: ReportRequest): Observable<ReportResult> {
        if (statusQueryAttempts > 10) {
            return Observable.just(
                ReportResult(
                    LocalDateTime.now(ZoneOffset.UTC).toString(),
                    request.requestId,
                    "",
                    ReportStates.Timeout.value,
                    "",
                    "",
                    listOf()
                )
            )
        }

        val delayMs = statusQueryAttempts * 500L

        return Observable.timer(delayMs, TimeUnit.MILLISECONDS)
            .flatMap {
                rxSingle {
                    val response = APIClientService(context).getUCGatewayService().getReportResult(
                        request.requestId
                    )

                    if (response.isSuccessful && response.body() != null) {
                        response.body()!!
                    } else {
                        ReportResult(
                            LocalDateTime.now(ZoneOffset.UTC).toString(),
                            request.requestId,
                            "",
                            ReportStates.Failed.value,
                            "",
                            "",
                            listOf()
                        )
                    }
                }.toObservable()
            }
            .onErrorReturn {
                Log.e("backoffQuery", it.message ?: "Unknown error")
                ReportResult(
                    Date().toString(),
                    request.requestId,
                    "",
                    ReportStates.Failed.value,
                    "",
                    "",
                    listOf()
                )
            }
    }

    private fun appendToHistory(items: List<CallHistoryItem>) {
        try {
            val arr = callHistorySubject.value?.toMutableList()

            if (arr != null && items.isNotEmpty()) {
                // Insert any new items at the beginning
                arr.addAll(
                    0,
                    items
                )

                // Take the first 200, removing any duplicates
                val newArr: List<CallHistoryItem> = arr.distinctBy { it.connectionId }
                    .take(200)

                callHistorySubject.onNext(newArr)

                cacheCallHistory(newArr, authStateManager.getUser().id!!)
            }
        } catch (e: Exception) {
            Log.e("appendToHistory", e)
        }
    }

    private fun cacheCallHistory(data: Any, currentUserId: String) {
        try {
            // Compress the data to reduce storage size.
            val compressedData = compressString(Gson().toJson(data))

            // Add metadata to the history so we can choose whether to use it later
            val cacheObj = CallHistoryCache(
                userId = currentUserId,
                version = CACHEVERSION,
                data = compressedData
            )

            // Store the cache object in SQLite
            val dbHelper = CallHistoryDatabaseHelper(context)
            val db = dbHelper.writableDatabase

            val contentValues = ContentValues().apply {
                put("userId", cacheObj.userId)
                put("version", cacheObj.version)
                put("data", cacheObj.data)
            }

            db.replace("CallHistoryCache", null, contentValues)
            db.close()
        } catch (e: Exception) {
            // May not have access - this is OK
            Log.e("CallHistoryService", "Failed to cache call history.", e)
        }
    }

    private fun getCachedCallHistory(currentUserId: String): List<CallHistoryItem> {
        val dbHelper = CallHistoryDatabaseHelper(context)
        val db = dbHelper.readableDatabase

        return try {
            val cursor: Cursor = db.query(
                "CallHistoryCache",
                arrayOf("userId", "version", "data"),
                null,
                null,
                null,
                null,
                null
            )

            var cacheObj: CallHistoryCache? = null
            if (cursor.moveToFirst()) {
                val userId = cursor.getString(cursor.getColumnIndexOrThrow("userId"))
                val version = cursor.getInt(cursor.getColumnIndexOrThrow("version"))
                val data = cursor.getString(cursor.getColumnIndexOrThrow("data"))
                cacheObj = CallHistoryCache(userId, version, data)
            }
            cursor.close()

            if (cacheObj == null) {
                Log.d("CallHistoryService", "No cached history found.")
                return listOf()
            }

            val decompressedData = decompressString(cacheObj.data)
            val data = Gson().fromJson<List<CallHistoryItem>>(
                decompressedData,
                object : TypeToken<List<CallHistoryItem>>() {}.type
            )

            Log.d("CallHistoryService", "Got ${data.size} results from history cache.")

            if (cacheObj.userId != currentUserId) {
                Log.d(
                    "CallHistoryService",
                    "Cached history is for a different user. Current user: $currentUserId"
                )
                return listOf()
            }

            if (cacheObj.version < CACHEVERSION) {
                Log.w(
                    "CallHistoryService",
                    "Cached history version (${cacheObj.version}) does not match current version ($CACHEVERSION) - discarding."
                )
                return listOf()
            }

            return data ?: listOf()
        } catch (e: Exception) {
            Log.w("CallHistoryService", "Failed to load call history cache.", e)
            listOf()
        } finally {
            db.close()
        }
    }

    private suspend fun getMissedCallTimestamp() {
        val response = APIClientService(context).getUCGatewayService().doGetMissedCallDate()

        if (response.code() < 200 || response.code() > 299) {
            throw Exception("Error fetching user info: " + response.message())
        }

        val formattedDateTimeString = response.body()!!.missedCallTimestamp
        val missedCallTimestamp: ZonedDateTime? = ZonedDateTime.parse(formattedDateTimeString)

        missedCallTimestampSubject.onNext(
            missedCallTimestamp!!
        )
    }

    fun updateMissedCallTimestamp() {
        val now = ZonedDateTime.now(ZoneId.of("UTC"))

        val formattedDateTime = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        missedCallTimestampSubject.onNext(now)

        APIClientService(context)
            .getUCGatewayService()
            .doSetMissedCallDate(formattedDateTime)
            .enqueue(object : Callback<Void> {
                override fun onFailure(call: Call<Void>, t: Throwable) {
                    Log.e("Failed to update MissedCallTimestamp", t)
                }

                override fun onResponse(
                    call: Call<Void>,
                    response: Response<Void>
                ) {
                    if (response.isSuccessful) {
                        Log.i("Successfully updated MissedCallTimestamp")
                    } else {
                        Log.e("Failed to update MissedCallTimestamp::${response.code()}")
                    }
                }
            })
    }

    private fun transformData(
        callHistoryData: List<CallHistoryItem>,
        localDateTime: LocalDateTime
    ): List<CallHistoryItemViewModel> {
        return callHistoryData.map { call ->
            CallHistoryItemViewModel(call, localDateTime)
        }
    }

    private fun compressString(data: String): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        GZIPOutputStream(byteArrayOutputStream).use { gzipOutputStream ->
            gzipOutputStream.write(data.toByteArray(Charsets.UTF_8))
        }
        return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT)
    }

    private fun decompressString(compressedData: String): String {
        val compressedByteArray = Base64.decode(compressedData, Base64.DEFAULT)
        val byteArrayInputStream = ByteArrayInputStream(compressedByteArray)
        GZIPInputStream(byteArrayInputStream).use { gzipInputStream ->
            return gzipInputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }
}
