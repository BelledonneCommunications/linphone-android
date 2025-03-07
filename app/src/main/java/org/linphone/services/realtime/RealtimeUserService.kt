package org.linphone.services.realtime

import android.content.Context
import java.util.concurrent.atomic.AtomicReference

class RealtimeUserService(context: Context) : RealtimeBaseService(context, "user") {

    companion object {
        private const val TAG: String = "RealtimeUserService"

        private val instance: AtomicReference<RealtimeUserService> =
            AtomicReference<RealtimeUserService>()

        fun getInstance(context: Context): RealtimeUserService {
            var svc = instance.get()
            if (svc == null) {
                svc = RealtimeUserService(context.applicationContext)
                instance.set(svc)
            }
            return svc
        }
    }
}
