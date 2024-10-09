package org.linphone.environment

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.AnyThread
import com.google.gson.Gson
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import org.json.JSONException
import org.linphone.R
import org.linphone.models.DimensionsEnvironment
import org.linphone.models.EnvironmentOverride
import org.linphone.utils.Log

class DimensionsEnvironmentService(context: Context) {
    companion object {
        private val INSTANCE_REF: AtomicReference<WeakReference<DimensionsEnvironmentService>> = AtomicReference(
            WeakReference(null)
        )
        private const val STORE_NAME: String = "environment"
        private const val KEY_STATE: String = "environment-state"
        private const val KEY_DEV_MODE: String = "dev-mode"

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
    private var mResources = context.resources
    private var mPrefsLock: ReentrantLock = ReentrantLock()
    private var mCurrentEnvironment: AtomicReference<DimensionsEnvironment> = AtomicReference<DimensionsEnvironment>()
    private var isListInitialised: Boolean = false
    private var isDevModeEnabled: Boolean = false

    init {
        isDevModeEnabled = mPrefs.getBoolean(KEY_DEV_MODE, false)
    }

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
    fun getEnvironmentList(): List<DimensionsEnvironment> {
        if (!isListInitialised) {
            addEnvironmentOverrides()
        }

        isListInitialised = true

        return environments.filter { e -> !e.isHidden || isDevModeEnabled }
    }

    private fun addEnvironmentOverrides() {
        // Read any environment overrides for the current build variant:
        var overrides: Array<EnvironmentOverride>

        mResources.openRawResource(R.raw.environment_overrides)
            .bufferedReader().use {
                val jsonStr = it.readText()
                overrides = Gson().fromJson(jsonStr, Array<EnvironmentOverride>::class.java)
            }

        var defaultId: String? = null

        // For each override, take any non-null properties
        for (x in overrides) {
            val env = environments.firstOrNull { e -> e.id == x.id }
            if (env != null) {
                env.name = x.name ?: env.name
                env.defaultTenantId = x.defaultTenantId ?: env.defaultTenantId
                if (x.isDefault) defaultId = x.id
            }
        }

        // If necessary, override the default environment
        if (defaultId != null) {
            environments.forEach { x -> x.isDefault = x.id == defaultId }
        }
    }

    @AnyThread
    fun readEnvironment(): DimensionsEnvironment? {
        mPrefsLock.lock()
        try {
            val currentEnvironment = mPrefs.getString(KEY_STATE, null)

            if (currentEnvironment.isNullOrBlank()) {
                return getDefaultEnvironment()
            }

            try {
                return DimensionsEnvironment.jsonDeserialize(currentEnvironment)
            } catch (ex: JSONException) {
                Log.w("Failed to deserialize stored auth state - discarding")
                return null
            }
        } finally {
            mPrefsLock.unlock()
        }
    }

    fun getDefaultEnvironment(): DimensionsEnvironment? {
        // Get the first environment that matches the current UI culture.
        val localeCode = Locale.getDefault().toLanguageTag()
        val cultureMatch = environments.firstOrNull { x -> x.locales.contains(localeCode) }
        if (cultureMatch != null) {
            return cultureMatch
        }
        // If not found, return the overall default environment.
        return environments.firstOrNull { x -> x.isDefault }
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

    fun toggleDevMode() {
        isDevModeEnabled = !isDevModeEnabled
        mPrefsLock.lock()
        try {
            val editor = mPrefs.edit()
            editor.putBoolean(KEY_DEV_MODE, isDevModeEnabled)
            check(editor.commit()) { "Failed to write dev mode setting" }
        } finally {
            mPrefsLock.unlock()
        }
    }
}
