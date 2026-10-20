package pl.twojanazwa.aapsremote.sms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SmsAssemblerTest {
    private val a = "+447700900123"
    private val b = "+447700900999"

    @Test
    fun `SMS-03 dwie czesci jednego nadawcy skladaja sie w jedna wiadomosc`() {
        val result = SmsAssembler.assemble(
            listOf(
                RawSmsPart(a, "Aby wprowadzic 20g o 9:05 odeslij w SMS kod ", 1L),
                RawSmsPart(a, "z aplikacji Authenticator dla: Jan, z doklejonym PIN-em", 1L),
            ),
        )
        assertEquals(1, result.size)
        assertEquals(
            "Aby wprowadzic 20g o 9:05 odeslij w SMS kod z aplikacji Authenticator dla: Jan, z doklejonym PIN-em",
            result[0].body,
        )
    }

    @Test
    fun `SMS-03 czesci przeplatane od dwoch nadawcow daja dwie wiadomosci w kolejnosci pierwszego wystapienia`() {
        val result = SmsAssembler.assemble(
            listOf(RawSmsPart(a, "A1", 1L), RawSmsPart(b, "B1", 2L), RawSmsPart(a, "A2", 1L)),
        )
        assertEquals(listOf(a, b), result.map { it.sender })
        assertEquals(listOf("A1A2", "B1"), result.map { it.body })
    }

    @Test
    fun `SMS-03 czesc z trescia null daje wiadomosc z pusta trescia`() {
        val result = SmsAssembler.assemble(listOf(RawSmsPart(a, null, 1L)))
        assertEquals(1, result.size)
        assertEquals("", result[0].body)
    }

    @Test
    fun `SMS-03 nadawca null tworzy osobna grupe`() {
        val result = SmsAssembler.assemble(listOf(RawSmsPart(null, "x", 1L), RawSmsPart(a, "y", 1L)))
        assertEquals(listOf<String?>(null, a), result.map { it.sender })
    }

    @Test
    fun `SMS-03 dwa wywolania assemble nie lacza czesci miedzy broadcastami`() {
        val first = SmsAssembler.assemble(listOf(RawSmsPart(a, "czesc1", 1L)))
        val second = SmsAssembler.assemble(listOf(RawSmsPart(a, "czesc2", 1L)))
        assertEquals(listOf("czesc1"), first.map { it.body })
        assertEquals(listOf("czesc2"), second.map { it.body })
    }

    @Test
    fun `SMS-03 znacznik wiadomosci to znacznik pierwszej czesci`() {
        val result = SmsAssembler.assemble(
            listOf(RawSmsPart(a, "x", 1111111000000L), RawSmsPart(a, "y", 1111111001000L)),
        )
        assertEquals(1111111000000L, result[0].serviceCenterTimestampMillis)
    }
}
