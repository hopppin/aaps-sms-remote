package pl.twojanazwa.aapsremote.commands

/** Pozycja listy profili wybrana przez użytkownika. toString() bez danych (PROF-05). */
data class ProfileChoice(val number: Int, val name: String) {
    override fun toString(): String = "ProfileChoice(***)"
}
