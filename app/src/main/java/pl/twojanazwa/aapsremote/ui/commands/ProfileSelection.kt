package pl.twojanazwa.aapsremote.ui.commands

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.twojanazwa.aapsremote.commands.ProfileChoice
import javax.inject.Inject
import javax.inject.Singleton

/** Wybór pozycji z wyniku `PROFILE LIST` do formularza zmiany profilu (PROF-14). Tylko w pamięci. */
@Singleton
class ProfileSelection @Inject constructor() {
    private val current = MutableStateFlow<ProfileChoice?>(null)

    val choice: StateFlow<ProfileChoice?> = current.asStateFlow()

    fun select(choice: ProfileChoice) {
        current.value = choice
    }

    fun clear() {
        current.value = null
    }

    override fun toString(): String = "ProfileSelection(***)"
}
