package org.linphone.services

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.i18n.phonenumbers.PhoneNumberUtil
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import org.linphone.authentication.AuthStateManager
import org.linphone.models.UserInfo

class PhoneFormatterService(val context: Context) : DefaultLifecycleObserver {
    private val authStateManager = AuthStateManager.getInstance(context)
    private val destroy = PublishSubject.create<Unit>()
    private var userSubscription: Disposable? = null

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)

        destroy.onNext(Unit)
        destroy.onComplete()

        userSubscription?.dispose()
    }

    companion object {
        private const val TAG: String = "PhoneFormatterService"

        private val instance: AtomicReference<PhoneFormatterService> =
            AtomicReference<PhoneFormatterService>()

        fun getInstance(context: Context): PhoneFormatterService {
            var svc = instance.get()
            if (svc == null) {
                svc = PhoneFormatterService(context.applicationContext)
                instance.set(svc)
            }
            return svc
        }
    }

    var currentUser: UserInfo? = null

    init {
        userSubscription = UserService.getInstance(context).user
            .subscribe { u -> currentUser = u }
    }

    fun getSearchNumber(input: String): String {
        // TODO #25806 - Remove tolldigit if any
        return input
    }

    private fun getPbxCountryCode(): String {
        return currentUser?.pbxCountryCode ?: Locale.getDefault().country
    }

    fun formatPhoneNumber(phoneNumber: String): String {
        val phoneUtil = PhoneNumberUtil.getInstance()
        val numberProto = phoneUtil.parse(phoneNumber, getPbxCountryCode())
        return phoneUtil.format(numberProto, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
            .replace("/\\D/g", "")
    }
}
