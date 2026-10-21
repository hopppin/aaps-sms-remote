package pl.twojanazwa.aapsremote.ui.platform

import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import pl.twojanazwa.aapsremote.ui.settings.secret.SecretClipboard

/** Schowek systemowy (UI-24). Bez logowania; wyjątki są połykane. */
class AndroidSecretClipboard @Inject constructor(
    @ApplicationContext private val context: Context,
) : SecretClipboard {
    private val manager: ClipboardManager?
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    override fun currentText(): String? = try {
        manager?.primaryClip?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.text?.toString()
    } catch (e: RuntimeException) {
        null
    }

    override fun clear() {
        try {
            manager?.clearPrimaryClip()
        } catch (e: RuntimeException) {
            // celowo ignorowane: bez logowania
        }
    }
}
