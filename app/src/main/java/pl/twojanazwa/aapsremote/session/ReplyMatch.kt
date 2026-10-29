package pl.twojanazwa.aapsremote.session

import pl.twojanazwa.aapsremote.commands.CommandId
import pl.twojanazwa.aapsremote.parser.AapsReply

/** BOL-19. Czysta funkcja zgodności rodzaju odpowiedzi AAPS z poleceniem sesji. */
internal object ReplyMatch {
    fun isCodeRequestFor(id: CommandId, reply: AapsReply.MatchingCodeRequest): Boolean = when (id) {
        CommandId.CARBS -> reply is AapsReply.CodeRequest
        CommandId.BOLUS -> reply is AapsReply.BolusCodeRequest
        CommandId.BG, CommandId.PUMP -> false
        // Spec 013, zad. 3 zmieni PROFILE_SWITCH; do tego czasu polecenia profilu nie odsyłają kodu.
        CommandId.PROFILE_STATUS, CommandId.PROFILE_LIST, CommandId.PROFILE_SWITCH -> false
    }

    fun isDoneFor(id: CommandId, reply: AapsReply.MatchingDone): Boolean = when (id) {
        CommandId.CARBS -> reply is AapsReply.CarbsDone
        CommandId.BOLUS -> reply is AapsReply.BolusDone
        CommandId.BG, CommandId.PUMP -> false
        CommandId.PROFILE_STATUS, CommandId.PROFILE_LIST, CommandId.PROFILE_SWITCH -> false
    }
}
