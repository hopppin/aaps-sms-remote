package pl.twojanazwa.aapsremote.ui.settings.secret

class FakeSecretClipboard(var text: String? = null) : SecretClipboard {
    var throwOnRead: Boolean = false
    var readCount: Int = 0
        private set
    var clearCount: Int = 0
        private set

    override fun currentText(): String? {
        readCount++
        if (throwOnRead) throw IllegalStateException("brak dostepu")
        return text
    }

    override fun clear() {
        clearCount++
        text = null
    }
}
