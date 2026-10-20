package pl.twojanazwa.aapsremote.otp

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Jeden zegar dla sesji, UsableTotpProvider i IncomingSmsHub (SMS-05, K1). */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    @Singleton
    fun clock(): EpochClock = EpochClock { System.currentTimeMillis() }
}
