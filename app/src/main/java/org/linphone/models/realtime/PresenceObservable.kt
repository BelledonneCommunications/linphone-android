import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.ReplaySubject

class PresenceObservable(
    onSubscribe: () -> Unit,
    onFinalise: () -> Unit
) {
    val subject: ReplaySubject<PresenceEventData> = ReplaySubject.create(1)

    val data: Observable<PresenceEventData> = Observable.defer {
        onSubscribe()
        subject
    }.doFinally(onFinalise)
        .replay(1)
        .refCount()
}
