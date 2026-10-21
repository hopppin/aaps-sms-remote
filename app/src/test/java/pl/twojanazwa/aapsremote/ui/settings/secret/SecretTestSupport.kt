package pl.twojanazwa.aapsremote.ui.settings.secret

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import pl.twojanazwa.aapsremote.security.SecretState
import pl.twojanazwa.aapsremote.ui.SessionDoubles
import pl.twojanazwa.aapsremote.ui.permissions.FakeCameraPermission

const val URI_RFC =
    "otpauth://totp/AAPS?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=AAPS&algorithm=SHA1&digits=6&period=30"

/** Zestaw zależności `TotpSecretViewModel` dla testów JVM. */
@OptIn(ExperimentalCoroutinesApi::class)
class SecretFixture(
    private val dispatcher: TestDispatcher,
    initial: SecretState = SecretState.UNKNOWN,
) {
    val store = FakeTotpSecretStore(initial)
    val session = SessionDoubles()
    val camera = FakeCameraPermission()
    val clipboard = FakeSecretClipboard()
    val viewModel = TotpSecretViewModel(store, session.coordinator, camera, clipboard)

    fun idle() = dispatcher.scheduler.advanceUntilIdle()
}
