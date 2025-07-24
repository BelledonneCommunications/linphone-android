package org.linphone.services

import androidx.lifecycle.MutableLiveData
import java.util.concurrent.atomic.AtomicReference
import org.linphone.activities.voip.TransferState

class TransferService {

    val transferState = MutableLiveData<TransferState>(TransferState.NONE)

    companion object {
        private val instance: AtomicReference<TransferService> = AtomicReference<TransferService>()

        fun getInstance(): TransferService {
            var svc = instance.get()
            if (svc == null) {
                svc = TransferService()
                instance.set(svc)
            }
            return svc
        }
    }
}
