package pl.twojanazwa.aapsremote.sms.platform

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import pl.twojanazwa.aapsremote.sms.SentResultsRegistration
import pl.twojanazwa.aapsremote.sms.SmsSystemApi

/**
 * SMS-09 – SMS-13. SmsManager domyślnej karty SMS pobierany przy każdym wywołaniu.
 * Pola: wyłącznie niezmienny kontekst aplikacji; treść SMS jest tylko zmienną lokalną. Bez logowania.
 */
class AndroidSmsSystemApi
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SmsSystemApi {
        override fun divideMessage(text: String): List<String> = smsManager().divideMessage(text)

        override fun registerSentResults(onPartResult: (partIndex: Int, ok: Boolean) -> Unit): SentResultsRegistration {
            val action = "${context.packageName}.sms.SENT.${UUID.randomUUID()}"
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        if (intent.action != action) return
                        val index = intent.getIntExtra(EXTRA_PART_INDEX, -1)
                        if (index < 0) return
                        onPartResult(index, resultCode == Activity.RESULT_OK)
                    }
                }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            return Registration(context, receiver, action)
        }

        override fun sendParts(
            destination: String,
            parts: List<String>,
            registration: SentResultsRegistration,
        ) {
            val handle = registration as Registration
            val manager = smsManager()
            val intents = ArrayList<PendingIntent?>(parts.size)
            for (i in parts.indices) {
                val intent = Intent(handle.action).setPackage(context.packageName).putExtra(EXTRA_PART_INDEX, i)
                val pending =
                    PendingIntent.getBroadcast(
                        context,
                        i,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
                    )
                handle.track(pending)
                intents.add(pending)
            }
            if (parts.size == 1) {
                manager.sendTextMessage(destination, null, parts[0], intents[0], null)
            } else {
                manager.sendMultipartTextMessage(destination, null, ArrayList(parts), intents, null)
            }
        }

        override fun toString(): String = "AndroidSmsSystemApi"

        @Suppress("DEPRECATION")
        private fun smsManager(): SmsManager {
            val manager =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    SmsManager.getDefault()
                }
            return manager ?: throw IllegalStateException(NO_SMS_MANAGER)
        }

        /** Trzyma wyłącznie receiver, akcję i PendingIntent-y – nigdy treść (SMS-13). */
        private class Registration(
            private val context: Context,
            private val receiver: BroadcastReceiver,
            val action: String,
        ) : SentResultsRegistration {
            private val pendingIntents = CopyOnWriteArrayList<PendingIntent>()
            private val done = AtomicBoolean(false)

            fun track(pending: PendingIntent) {
                pendingIntents.add(pending)
            }

            override fun unregister() {
                if (!done.compareAndSet(false, true)) return
                try {
                    context.unregisterReceiver(receiver)
                } catch (e: IllegalArgumentException) {
                    // Receiver już wyrejestrowany – idempotencja.
                }
                pendingIntents.forEach { it.cancel() }
                pendingIntents.clear()
            }

            override fun toString(): String = "SentResultsRegistration"
        }
    }

private const val EXTRA_PART_INDEX = "partIndex"
private const val NO_SMS_MANAGER = "Brak SmsManager"
