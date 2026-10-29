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

    @Test
    fun `BOL-19 potwierdzenie jest zgodne tylko z wlasnym poleceniem`() {
        for ((reply, owner) in dones) {
            for (id in CommandId.entries) {
                assertEquals(id == owner, ReplyMatch.isDoneFor(id, reply), "$id / $reply")
            }
        }
    }
}
