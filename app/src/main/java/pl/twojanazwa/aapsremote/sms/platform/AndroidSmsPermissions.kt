package pl.twojanazwa.aapsremote.sms.platform

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.twojanazwa.aapsremote.sms.SmsPermissions
import javax.inject.Inject

/** SMS-14. Stan uprawnień odczytywany przy każdym wywołaniu; bez zapamiętywania i bez próśb o uprawnienia. */
class AndroidSmsPermissions
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SmsPermissions {
        override fun canSend(): Boolean = granted(SmsPermissions.SEND_SMS)

        override fun canReceive(): Boolean = granted(SmsPermissions.RECEIVE_SMS)

        private fun granted(permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
