package pl.twojanazwa.aapsremote.sms.platform

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Telephony
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.twojanazwa.aapsremote.sms.RawSmsPart
import pl.twojanazwa.aapsremote.sms.SmsBroadcastRegistrar
import javax.inject.Inject

/**
 * SMS-07, SMS-03. Dynamiczny receiver SMS_RECEIVED, eksportowany, z wymaganym uprawnieniem nadawcy
 * BROADCAST_SMS (blokuje fałszywe broadcasty innych aplikacji). Bez logowania; części w kolejności z intentu.
 */
class AndroidSmsBroadcastRegistrar
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SmsBroadcastRegistrar {
        private var receiver: BroadcastReceiver? = null

        override fun register(onBroadcast: (List<RawSmsPart>) -> Unit) {
            val newReceiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
                        val parts =
                            try {
                                Telephony.Sms.Intents
                                    .getMessagesFromIntent(intent)
                                    .orEmpty()
                                    .filterNotNull()
                                    .map { RawSmsPart(it.originatingAddress, it.messageBody, it.timestampMillis) }
                            } catch (e: RuntimeException) {
                                return
                            }
                        if (parts.isNotEmpty()) onBroadcast(parts)
                    }
                }
            ContextCompat.registerReceiver(
                context,
                newReceiver,
                IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
                Manifest.permission.BROADCAST_SMS,
                null,
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiver = newReceiver
        }

        override fun unregister() {
            val current = receiver ?: throw IllegalArgumentException("Receiver nie jest zarejestrowany")
            context.unregisterReceiver(current)
            receiver = null
        }
    }
