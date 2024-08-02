package org.linphone.environment

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.AnyThread
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import org.json.JSONException
import org.linphone.models.DimensionsEnvironment

class DimensionsEnvironmentService(context: Context) {
    private val mContext = context

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
        val fileContent = this::class.java.getResource("/res/raw/environments.json")?.readText()

        val dimensionsEnvironmentListType = object : TypeToken<List<DimensionsEnvironment>>() {}.type

        return Gson().fromJson(fileContent, dimensionsEnvironmentListType)
    }

    @AnyThread
    fun getEnvironmentById(id: String): DimensionsEnvironment? {
        val environments = getEnvironmentList() ?: return null

        return environments.firstOrNull { x ->
            x.id == id
        }
    }

    @AnyThread
    private fun readEnvironment(): DimensionsEnvironment? {
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
