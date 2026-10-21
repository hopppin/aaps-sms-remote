package pl.twojanazwa.aapsremote.session.platform

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.twojanazwa.aapsremote.otp.EpochClock
import pl.twojanazwa.aapsremote.otp.UsableTotpProvider
import pl.twojanazwa.aapsremote.session.IncomingSmsSource
import pl.twojanazwa.aapsremote.session.SessionCoordinator
import pl.twojanazwa.aapsremote.session.SmsSender
import javax.inject.Singleton

/** Jeden koordynator na aplikację (SESSION-22). Zakres uruchamiania sesji – spec 009. */
@Module
@InstallIn(SingletonComponent::class)
object SessionModule {
    @Provides
    @Singleton
    fun usableTotpProvider(clock: EpochClock): UsableTotpProvider = UsableTotpProvider(clock)

    @Provides
    @Singleton
    fun sessionCoordinator(
        sender: SmsSender,
        incoming: IncomingSmsSource,
        clock: EpochClock,
        totp: UsableTotpProvider,
    ): SessionCoordinator = SessionCoordinator(sender, incoming, clock, totp)
}
