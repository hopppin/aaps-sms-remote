package pl.twojanazwa.aapsremote.parser

/**
 * Wynik parsowania. Każdy wariant (poza WrongCode) ma [text] = treść wejściowa bez zmian.
 * data class z jawnie nadpisanym toString() – equals/hashCode generowane, toString redagowane.
 */
sealed interface AapsReply {
    /** Prośba o kod zgodna z poleceniem – jedyna droga do kodu (SESSION-08). */
    sealed interface MatchingCodeRequest : AapsReply

    /** Wiadomość w formie prośby/potwierdzenia, ale z inną wartością niż wysłana. */
    sealed interface CommandMismatch : AapsReply

    /** Potwierdzenie wykonania zgodne z poleceniem. */
    sealed interface MatchingDone : AapsReply

    /** BOL-07. Istnieje tylko, gdy centiUnits == ExpectedCommand.Bolus.centiUnits. */
    data class BolusCodeRequest(val centiUnits: Int, val text: String) : MatchingCodeRequest {
        override fun toString(): String = "AapsReply.BolusCodeRequest(***)"
    }

    /** BOL-08 */
    data class BolusAmountMismatch(
        val expectedCentiUnits: Int,
        val receivedCentiUnits: Int,
        val stage: CarbsStage,
        val text: String,
    ) : CommandMismatch {
        override fun toString(): String = "AapsReply.BolusAmountMismatch(stage=$stage, ***)"
    }

    /** BOL-09 */
    data class BolusDone(val centiUnits: Int, val text: String) : MatchingDone {
        override fun toString(): String = "AapsReply.BolusDone(***)"
    }

    /** PARSER-03 */
    data class BgReport(val glucoseMgdl: Int, val text: String) : AapsReply {
        override fun toString(): String = "AapsReply.BgReport(***)"
    }

    /** PARSER-04 */
    data class PumpStatus(val text: String) : AapsReply {
        override fun toString(): String = "AapsReply.PumpStatus(***)"
    }

    /** PARSER-05. Istnieje tylko, gdy grams == ExpectedCommand.Carbs.grams. */
    data class CodeRequest(val grams: Int, val text: String) : MatchingCodeRequest {
        override fun toString(): String = "AapsReply.CodeRequest(***)"
    }

    /** PARSER-06 */
    data class CarbsAmountMismatch(
        val expectedGrams: Int,
        val receivedGrams: Int,
        val stage: CarbsStage,
        val text: String,
    ) : CommandMismatch {
        override fun toString(): String = "AapsReply.CarbsAmountMismatch(stage=$stage, ***)"
    }

    /** PARSER-07 */
    data class CarbsDone(val grams: Int, val text: String) : MatchingDone {
        override fun toString(): String = "AapsReply.CarbsDone(***)"
    }

    /** PARSER-08 */
    data object WrongCode : AapsReply

    /** PARSER-09 */
    data class Unrecognized(val text: String) : AapsReply {
        override fun toString(): String = "AapsReply.Unrecognized(***)"
    }
}

enum class CarbsStage { CODE_REQUEST, CONFIRMATION }
