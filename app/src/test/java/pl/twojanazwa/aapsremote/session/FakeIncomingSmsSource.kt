package pl.twojanazwa.aapsremote.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge

/** Atrapa portu IncomingSmsSource: gorący strumień bez powtórek. */
class FakeIncomingSmsSource : IncomingSmsSource {
    private val shared = MutableSharedFlow<IncomingSms>(replay = 0, extraBufferCapacity = 64)

    /** Jeśli ustawione, strumień kończy się tym wyjątkiem po subskrypcji. */
    var failure: Throwable? = null

    val subscriptionCount get() = shared.subscriptionCount

    suspend fun emit(sms: IncomingSms) = shared.emit(sms)

    override fun messages(): Flow<IncomingSms> {
        val error = failure ?: return shared
        return merge(shared, flow { throw error })
    }
}
