package pl.twojanazwa.aapsremote.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pl.twojanazwa.aapsremote.commands.CommandId
import pl.twojanazwa.aapsremote.parser.AapsReply

class ReplyMatchTest {

    private val codeRequests = mapOf<AapsReply.MatchingCodeRequest, CommandId>(
        AapsReply.CodeRequest(1, "x") to CommandId.CARBS,
        AapsReply.BolusCodeRequest(10, "x") to CommandId.BOLUS,
        AapsReply.ProfileCodeRequest("x") to CommandId.PROFILE_SWITCH,
    )
    private val dones = mapOf<AapsReply.MatchingDone, CommandId>(
        AapsReply.CarbsDone(1, "x") to CommandId.CARBS,
        AapsReply.BolusDone(10, "x") to CommandId.BOLUS,
    )

    @Test
    fun `BOL-19 prosba o kod jest zgodna tylko z wlasnym poleceniem`() {
        for ((reply, owner) in codeRequests) {
            for (id in CommandId.entries) {
                assertEquals(id == owner, ReplyMatch.isCodeRequestFor(id, reply), "$id / $reply")
            }
        }
    }

    @Test
    fun `PROF-12 polecenia profilu nie sa zgodne z prosba o kod bolusa ani CARBS i zadnym potwierdzeniem`() {
        val profile = listOf(CommandId.PROFILE_STATUS, CommandId.PROFILE_LIST, CommandId.PROFILE_SWITCH)
        for (id in profile) {
            for (reply in codeRequests.keys.filter { it !is AapsReply.ProfileCodeRequest }) assertEquals(false, ReplyMatch.isCodeRequestFor(id, reply))
            for (reply in dones.keys) assertEquals(false, ReplyMatch.isDoneFor(id, reply))
        }
    }

    /**
     * PROF-19. Przypiecie obecnego zachowania: ProfileSwitchDone nie jest zgodne z PROFILE_SWITCH.
     * Zmiana na true wymaga zmiany CommandSession.confirmed (zadanie #266), nie samego ReplyMatch.
     */
    @Test
    fun `PROF-19 ProfileSwitchDone nie jest zgodne z PROFILE_SWITCH (zmiana na true wymaga zadania 266)`() {
        assertEquals(false, ReplyMatch.isDoneFor(CommandId.PROFILE_SWITCH, AapsReply.ProfileSwitchDone("x")))
    }

    @Test
    fun `BOL-19 potwierdzenie jest zgodne tylko z wlasnym poleceniem`() {
        for ((reply, owner) in dones) {
            for (id in CommandId.entries) {
                assertEquals(id == owner, ReplyMatch.isDoneFor(id, reply), "$id / $reply")
            }
        }
    }
}
