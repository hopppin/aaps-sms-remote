package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge

/** Atrapa portu IncomingSmsSource: gorący strumień bez powtórek. */
class FakeIncomingSmsSource : IncomingSmsSource {
    private val shared = MutableSharedFlow<IncomingSms>(replay = 0, extraBufferCapacity = 64)

    /** Jeśli ustawione, strumień kończy się tym wyjątkiem po subskrypcji. */
    var failure: Throwable? = null

    /** Z [failure]: wyjątek po tylu ms czasu korutyn od subskrypcji (0 = od razu). */
    var failAfterMillis: Long = 0

    /** Jeśli true, strumień kończy się normalnie (bez wyjątku) tuż po subskrypcji. */
    var completeImmediately: Boolean = false

    val subscriptionCount get() = shared.subscriptionCount

    suspend fun emit(sms: IncomingSms) = shared.emit(sms)

    override fun messages(): Flow<IncomingSms> {
        if (completeImmediately) return flow { }
        val error = failure ?: return shared
        return merge(shared, flow {
            if (failAfterMillis > 0) delay(failAfterMillis)
            throw error
        })
    }
}
