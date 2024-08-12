package org.linphone.utils

import org.jetbrains.annotations.NonNls
import timber.log.Timber

class Log {
    companion object Log {

        fun v(@NonNls message: String?, vararg args: Any?) {
            Timber.v(message, *args)
        }

        fun v(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
            Timber.v(t, message, *args)
        }

        /** Log a verbose exception. */
        fun v(t: Throwable?) {
            Timber.v(t)
        }

        /** Log a debug message with optional format args. */
        fun d(@NonNls message: String?, vararg args: Any?) {
            Timber.d(message, *args)
        }

        /** Log a debug exception and a message with optional format args. */
        fun d(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
            Timber.d(t, message, *args)
        }

        /** Log a debug exception. */
        fun d(t: Throwable?) {
            Timber.d(t)
        }

        /** Log an info message with optional format args. */
        fun i(@NonNls message: String?, vararg args: Any?) {
            Timber.i(message, *args)
        }

        /** Log an info exception and a message with optional format args. */

        fun i(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
            Timber.i(t, message, *args)
        }

        /** Log an info exception. */
        fun i(t: Throwable?) {
            Timber.i(t)
        }

        /** Log a warning message with optional format args. */
        fun w(@NonNls message: String?, vararg args: Any?) {
            Timber.w(message, *args)
        }

        /** Log a warning exception and a message with optional format args. */
        fun w(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
            Timber.w(t, message, *args)
        }

        /** Log a warning exception. */
        fun w(t: Throwable?) {
            Timber.w(t)
        }

        /** Log an error message with optional format args. */
        fun e(@NonNls message: String?, vararg args: Any?) {
            Timber.e(message, *args)
        }

        /** Log an error exception and a message with optional format args. */
        fun e(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
            Timber.e(t, message, *args)
        }

        /** Log an error exception. */
        fun e(t: Throwable?) {
            Timber.e(t)
        }

        /** Log an assert message with optional format args. */
        fun wtf(@NonNls message: String?, vararg args: Any?) {
            Timber.wtf(message, *args)
        }

        /** Log an assert exception and a message with optional format args. */
        fun wtf(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
            Timber.wtf(t, message, *args)
        }

        /** Log an assert exception. */
        fun wtf(t: Throwable?) {
            Timber.wtf(t)
        }

        /** Log at `priority` a message with optional format args. */
        fun log(priority: Int, @NonNls message: String?, vararg args: Any?) {
            Timber.log(priority, message, *args)
        }

        /** Log at `priority` an exception and a message with optional format args. */
        fun log(
            priority: Int,
            t: Throwable?,
            @NonNls message: String?,
            vararg args: Any?
        ) {
            Timber.log(priority, t, message, *args)
        }

        /** Log at `priority` an exception. */
        fun log(priority: Int, t: Throwable?) {
            Timber.log(priority, t)
        }

        fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            throw AssertionError() // Missing for log method.
        }
    }
}
