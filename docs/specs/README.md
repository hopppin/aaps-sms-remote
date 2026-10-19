# Specyfikacje

Projekt jest rozwijany metodą spec-driven: najpierw zatwierdzona specyfikacja, potem kod. Kod nie robi nic, czego nie opisuje specyfikacja.

## Struktura

Każda funkcja ma własny katalog `NNN-krotka-nazwa/` (numeracja rosnąca, trzy cyfry):

| Plik | Zawartość |
| --- | --- |
| `spec.md` | **co** ma działać: wymagania z identyfikatorami, kryteria akceptacji, przypadki błędów, poza zakresem |
| `design.md` | **jak** to zrobić: interfejsy, stany, decyzje i ich powody |
| `dane/` (opcjonalnie) | dane testowe: wektory z RFC, przykładowe SMS-y z AAPS (bez prawdziwych numerów i sekretów) |

Nową specyfikację zaczynasz od kopii katalogu [`_szablon/`](_szablon/).

## Identyfikatory wymagań

- Format: `<PREFIKS>-<NN>`, np. `TOTP-01`, `CARBS-03`. Prefiks jest stały dla specyfikacji i podany w jej nagłówku.
- Identyfikatorów nie zmienia się i nie używa ponownie. Usunięte wymaganie zostaje w spec z dopiskiem **(wycofane)**.
- Testy w nazwie zawierają identyfikator wymagania, np. `` `TOTP-02 kod bliski wygasniecia czeka na nastepny` ``.

## Statusy

| Status | Znaczenie |
| --- | --- |
| `szkic` | w trakcie pisania, nie wolno z niej planować zadań |
| `zatwierdzona` | scalona do `main` po recenzji; można zakładać issues i pisać kod |
| `zastąpiona` | nieaktualna, w nagłówku link do następczyni |

## Przepływ

1. **Spec:** agent `specyfikator` (albo człowiek) pisze `spec.md` i `design.md`. PR z tytułem `Spec: <nazwa>`, bez kodu aplikacji. Status w PR: `zatwierdzona`; scalenie PR oznacza zatwierdzenie.
2. **Plan:** agent `planista` zakłada issues tylko z zatwierdzonych specyfikacji. Każde issue wskazuje plik spec i identyfikatory wymagań.
3. **Kod:** agent `wykonawca` realizuje jedno issue = jeden PR. Każde wymaganie z issue ma test.
4. **Recenzja:** CodeRabbit i agent `recenzent` sprawdzają zgodność kodu ze specyfikacją.

## Zmiana wymagań

Specyfikacja wyprzedza kod. Jeśli w trakcie implementacji okaże się, że spec jest błędna lub niepełna:

1. Zatrzymaj implementację tej części.
2. Otwórz osobny PR `Spec: zmiana <nazwa>` z poprawką i opisem powodu.
3. Kod dostosuj dopiero po scaleniu zmiany.

Drobne poprawki redakcyjne (literówki, formatowanie) nie wymagają osobnego PR.

## Indeks

| Nr | Specyfikacja | Prefiks | Status |
| --- | --- | --- | --- |
| – | brak | – | – |
