package org.linphone.middleware

import android.content.Context
import io.sentry.Attachment
import io.sentry.EventProcessor
import io.sentry.Hint
import io.sentry.SentryEvent
import org.linphone.services.DiagnosticsService
import org.linphone.utils.Log

class SentryEventProcessor(private val context: Context) : EventProcessor {

    override fun process(event: SentryEvent, hint: Hint): SentryEvent? {
        try {
            val zipFile = DiagnosticsService.getLogsZip(context)
            hint.clearAttachments()
            hint.addAttachment(Attachment(zipFile.path))
        } catch (ex: Exception) {
            Log.e(ex, "Failed to append log files to Sentry event.")
        }

        return super.process(event, hint)
    }
}
