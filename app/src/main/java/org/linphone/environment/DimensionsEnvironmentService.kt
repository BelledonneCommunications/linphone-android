package org.linphone.environment

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.AnyThread
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import org.json.JSONException
import org.linphone.models.DimensionsEnvironment

class DimensionsEnvironmentService(context: Context) {
    companion object {
        private val INSTANCE_REF: AtomicReference<WeakReference<DimensionsEnvironmentService>> = AtomicReference(
            WeakReference(null)
        )
        private const val TAG: String = "environment-service"
        private const val STORE_NAME: String = "environment"
        private const val KEY_STATE: String = "environment-state"

        @AnyThread
        fun getInstance(context: Context): DimensionsEnvironmentService {
            var service = INSTANCE_REF.get().get()
            if (service == null) {
                service = DimensionsEnvironmentService(context.applicationContext)
                INSTANCE_REF.set(WeakReference(service))
            }

            return service
        }
    }

    private var mPrefs: SharedPreferences = context.getSharedPreferences(
        STORE_NAME,
        Context.MODE_PRIVATE
    )
    private var mPrefsLock: ReentrantLock = ReentrantLock()
    private var mCurrentEnvironment: AtomicReference<DimensionsEnvironment> = AtomicReference<DimensionsEnvironment>()
    private var mEnvironmentList: List<DimensionsEnvironment>? = null

    @AnyThread
    fun getCurrentEnvironment(): DimensionsEnvironment? {
        if (mCurrentEnvironment.get() != null) {
            return mCurrentEnvironment.get()
        }

        val dimensionsEnvironment: DimensionsEnvironment? = readEnvironment()

        return if (mCurrentEnvironment.compareAndSet(null, dimensionsEnvironment)) {
            dimensionsEnvironment
        } else {
            mCurrentEnvironment.get()
        }
    }

    @AnyThread
    fun setCurrentEnvironment(dimensionsEnvironment: DimensionsEnvironment): DimensionsEnvironment {
        writeEnvironment(dimensionsEnvironment)
        mCurrentEnvironment.set(dimensionsEnvironment)
        return dimensionsEnvironment
    }

    @AnyThread
    fun getEnvironmentList(): List<DimensionsEnvironment>? {
        Log.d("getEnvironmentList", "Reading environments...")

        if (mEnvironmentList == null) {
            mEnvironmentList = buildEnvironmentList()
        }

        return mEnvironmentList
    }

    @AnyThread
    fun buildEnvironmentList(): List<DimensionsEnvironment> {
        val environments = listOf(
            DimensionsEnvironment(
                id = "NA",
                name = "North America",
                isDefault = false,
                isHidden = false,
                identityServerUri = "https://login.xarios.cloud",
                gatewayApiUri = "https://ucgateway.xarios.cloud",
                realtimeApiUri = "https://realtime.xarios.cloud",
                documentationUri = "https://docs.xarios.cloud/mobile",
                diagnosticsBlobConnectionString = "@~#DIAGBLOB_NA#~@",
                resourcesBlobUrl = "https://resource.xarios.cloud"
            ),
            DimensionsEnvironment(
                id = "AU",
                name = "Australia",
                isDefault = false,
                isHidden = false,
                identityServerUri = "https://login.au.xarios.cloud",
                gatewayApiUri = "https://ucgateway.au.xarios.cloud",
                realtimeApiUri = "https://realtime.au.xarios.cloud",
                documentationUri = "https://docs.au.xarios.cloud/mobile",
                diagnosticsBlobConnectionString = "@~#DIAGBLOB_AU#~@",
                resourcesBlobUrl = "https://resource.au.xarios.cloud"
            ),
            DimensionsEnvironment(
                id = "EU",
                name = "Europe",
                isDefault = false,
                isHidden = false,
                identityServerUri = "https://login.eu.xarios.cloud",
                gatewayApiUri = "https://ucgateway.eu.xarios.cloud",
                realtimeApiUri = "https://realtime.eu.xarios.cloud",
                documentationUri = "https://docs.eu.xarios.cloud/mobile",
                diagnosticsBlobConnectionString = "@~#DIAGBLOB_EU#~@",
                resourcesBlobUrl = "https://resource.eu.xarios.cloud"
            ),
            DimensionsEnvironment(
                id = "UK",
                name = "United Kingdom",
                isDefault = true,
                isHidden = false,
                identityServerUri = "https://login.uk.xarios.cloud",
                gatewayApiUri = "https://ucgateway.uk.xarios.cloud",
                realtimeApiUri = "https://realtime.uk.xarios.cloud",
                documentationUri = "https://docs.uk.xarios.cloud/mobile",
                diagnosticsBlobConnectionString = "@~#DIAGBLOB_UK#~@",
                resourcesBlobUrl = "https://resource.uk.xarios.cloud"
            ),
            DimensionsEnvironment(
                id = "Stg",
                name = "Staging",
                isDefault = false,
                isHidden = false, // FIXME - this should be true
                identityServerUri = "https://login.stage-env.dev",
                gatewayApiUri = "https://ucgateway.stage-env.dev",
                realtimeApiUri = "https://realtime.stage-env.dev",
                documentationUri = "https://docs.stage-env.dev/mobile",
                diagnosticsBlobConnectionString = "@~#DIAGBLOB_Stg#~@",
                resourcesBlobUrl = "https://resource.stage-env.dev"
            ),
            DimensionsEnvironment(
                id = "QA",
                name = "QA",
                isDefault = false,
                isHidden = true,
                identityServerUri = "https://login.xarios.dev",
                gatewayApiUri = "https://ucgateway.xarios.dev",
                realtimeApiUri = "https://realtime.xarios.dev",
                documentationUri = "https://docs.xarios.dev/mobile",
                diagnosticsBlobConnectionString = "@~#DIAGBLOB_QA#~@",
                resourcesBlobUrl = "https://resource.xarios.dev"
            ),
            DimensionsEnvironment(
                id = "RemoteDev",
                name = "Remote Development",
                isDefault = false,
                isHidden = true,
                identityServerUri = "https://grapefruit-idp.cowling.dev",
                gatewayApiUri = "https://grapefruit-ucgateway.cowling.dev",
                realtimeApiUri = "https://grapefruit-realtime.cowling.dev",
                documentationUri = "https://docs.xarios.dev/mobile/",
                diagnosticsBlobConnectionString = "@~#DIAGBLOB_RemoteDev#~@",
                resourcesBlobUrl = "https://resource.xarios.dev"
            )
        )

        return environments
    }

    @AnyThread
    fun getEnvironmentById(id: String): DimensionsEnvironment? {
        val environments = getEnvironmentList() ?: return null

        return environments.firstOrNull { x ->
            x.id == id
        }
    }

    @AnyThread
    fun readEnvironment(): DimensionsEnvironment? {
        mPrefsLock.lock()
        try {
            val currentEnvironment = mPrefs.getString(KEY_STATE, null)

            if (currentEnvironment.isNullOrBlank()) return null

            try {
                return DimensionsEnvironment.jsonDeserialize(currentEnvironment)
            } catch (ex: JSONException) {
                Log.w(TAG, "Failed to deserialize stored auth state - discarding")
                return null
            }
        } finally {
            mPrefsLock.unlock()
        }
    }

    @AnyThread
    private fun writeEnvironment(newEnvironment: DimensionsEnvironment?) {
        mPrefsLock.lock()
        try {
            val editor = mPrefs.edit()
            if (newEnvironment == null) {
                editor.remove(KEY_STATE)
            } else {
                editor.putString(KEY_STATE, DimensionsEnvironment.jsonSerialize(newEnvironment))
            }

            check(editor.commit()) { "Failed to write state to shared prefs" }
        } finally {
            mPrefsLock.unlock()
        }
    }
}
