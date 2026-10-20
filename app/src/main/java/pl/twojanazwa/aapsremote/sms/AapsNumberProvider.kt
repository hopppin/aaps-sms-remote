package pl.twojanazwa.aapsremote.sms

/**
 * Numer AAPS z ustawień; null = brak konfiguracji. Implementacja: spec `data`.
 * Wołane z onReceive (wątek główny) i z send(): nie może wykonywać blokującego I/O
 * (np. zwraca wartość z pamięci podręcznej StateFlow ustawień).
 */
fun interface AapsNumberProvider {
    fun currentNumber(): String?
}
