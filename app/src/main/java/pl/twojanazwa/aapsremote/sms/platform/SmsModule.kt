package pl.twojanazwa.aapsremote.sms.platform

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import pl.twojanazwa.aapsremote.session.IncomingSmsSource
import pl.twojanazwa.aapsremote.session.SmsSender
import pl.twojanazwa.aapsremote.sms.AapsSmsSender
import pl.twojanazwa.aapsremote.sms.DeliveredSmsRegistry
import pl.twojanazwa.aapsremote.sms.IncomingSmsHub
import pl.twojanazwa.aapsremote.sms.SmsBroadcastRegistrar
import pl.twojanazwa.aapsremote.sms.SmsPermissions
import pl.twojanazwa.aapsremote.sms.SmsSystemApi

/**
 * Wiązania spec 005. AapsNumberProvider celowo bez wiązania (spec `data`) – jest wiązany
 * w [pl.twojanazwa.aapsremote.data.platform.DataModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SmsModule {
    @Binds
    abstract fun smsSender(impl: AapsSmsSender): SmsSender

    @Binds
    abstract fun incomingSmsSource(impl: IncomingSmsHub): IncomingSmsSource

    @Binds
    abstract fun smsPermissions(impl: AndroidSmsPermissions): SmsPermissions

    @Binds
    abstract fun smsBroadcastRegistrar(impl: AndroidSmsBroadcastRegistrar): SmsBroadcastRegistrar

    @Binds
    abstract fun smsSystemApi(impl: AndroidSmsSystemApi): SmsSystemApi

    companion object {
        @Provides
        @Singleton
        fun deliveredSmsRegistry(): DeliveredSmsRegistry = DeliveredSmsRegistry()
    }
}
