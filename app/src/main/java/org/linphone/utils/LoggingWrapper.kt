package org.linphone.utils


import android.os.Build
import android.util.Log

/**
 * This Logger class serves as a wrapper that clients can use to further customize how their logs are
 * collected and optionally plug-in logging frameworks such as Firebase, etc.
 */
interface LoggingWrapper {

    val loggerTag: String
        get() {
            val tag = javaClass.simpleName

            // Before API 24 there was a limit on the tag length for logcat.
            return if (tag.length <= MAX_TAG_LENGTH || Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tag
            } else {
                tag.substring(0, MAX_TAG_LENGTH)
            }
        }
}

class DefaultLoggingWrapper : LoggingWrapper

private const val MAX_TAG_LENGTH = 23


fun LoggingWrapper.logWarn(message: String, thr: Throwable? = null) {
    val logMessage = if (thr != null) {
        message + ". E.: ${thr.message}"
    } else {
        message
    }
    Log.w(this.loggerTag, logMessage)
}

fun LoggingWrapper.logError(message: String, thr: Throwable? = null) {
    val logMessage = if (thr != null) {
        message + ". E.: ${thr.message}"
    } else {
        message
    }
    Log.e(this.loggerTag, logMessage)
}
