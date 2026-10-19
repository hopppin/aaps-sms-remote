# Stan prac

Krótki obraz tego, gdzie jest projekt. Czytaj na początku sesji; aktualizuj na końcu (skill `/aktualizuj-stan`). Szczegóły: [plan](plan.md), [specyfikacje](specs/README.md), [zasady](../AGENTS.md).

_Ostatnia aktualizacja: 2026-10-19_

## Zrobione

| Obszar | Co | Gdzie |
| --- | --- | --- |
| Szkielet | Android (Kotlin, Compose, Hilt, minSdk 28, SDK 37), CI na PR, CodeRabbit, AGENTS.md | PR #1 |
| Proces | spec-driven: szablony `docs/specs/_szablon/`, zasady w `docs/specs/README.md` | PR #2 |
| Spec 001 TOTP | zatwierdzona, w całości zaimplementowana | PR #3; kod: PR #8, #9, #10, #11 |
| Pakiet `otp` | generator RFC 6238, okno ważności, `UsableTotpProvider` (≥ 5 s zapasu), dekoder Base32, parser `otpauth://` | `app/src/main/java/pl/twojanazwa/aapsremote/otp/` |
| Spec 002 parser | zatwierdzona; prawdziwe SMS-y AAPS 3.4.2.6 jako dane testowe; zaimplementowane: typy wyniku, normalizacja, BG, PUMP (#13) | PR #12, #16; dane: `docs/specs/002-parser-odpowiedzi/dane/` |

## W toku

| Issue | Temat | Zależy od |
| --- | --- | --- |
| #14 | parser: CARBS – prośba o kod, niezgodna ilość, zły kod | – (#13 scalone w PR #16) |
| #15 | parser: CARBS – potwierdzenie, macierz kontekstów | #14 |

Stan PR-ów: `gh pr list`, stan issues: `gh issue list`.

## Następne kroki

1. Dokończyć parser (#14 → #15, po kolei).
2. Spec `commands` (katalog poleceń BG, PUMP, CARBS, walidacja ilości) – nie zależy od niczego.
3. Spec `session` (maszyna stanów polecenia, timeouty) – po parserze i `commands`. Musi uwzględnić ustalenia poniżej.
4. Potem: `sms` (SmsManager, odbiór, filtr numeru), `security` (Keystore dla sekretu TOTP), `ui`, `data`.

## Ustalenia z użytkownikiem (decyzje)

- **Bez biometrii.** PIN AAPS wpisywany przy każdym poleceniu z potwierdzeniem, nigdy nie zapisywany (plan, AGENTS.md).
- **Sekret TOTP:** każdy niepusty sekret jest przyjmowany; nieznane parametry `otpauth://` są ignorowane. QR z AAPS: `otpauth://totp/AAPS?secret=…&issuer=AAPS&algorithm=SHA1&digits=6&period=30`.
- **SMS z AAPS 3.4.2.6, po polsku, bez znaków diakrytycznych.** Każda odpowiedź to jeden SMS. Odpowiedź na kod: `<6 cyfr><PIN>` bez spacji.
- **Brak odpowiedzi AAPS:** na nieznane polecenie, z numeru spoza listy dozwolonych i po upływie czasu na kod AAPS nic nie wysyła.
- **Ważne dla `session`:** po CARBS z innego dozwolonego numeru przychodzi tekst identyczny z naszym potwierdzeniem, więc samo potwierdzenie nie dowodzi, że wykonano **nasze** polecenie. Minimum: przyjmować je tylko w stanie „kod wysłany”, a w każdym innym stanie – stop jako wiadomość nieoczekiwana. Spec `session` musi dodatkowo rozstrzygnąć, jak pokazać wynik, którego nie da się jednoznacznie przypisać (np. „AAPS potwierdził wprowadzenie 20 g – sprawdź w AAPS”), zamiast bezwarunkowo oznaczać CARBS jako wykonane.

## Otwarte (do ustalenia)

- Czas na odesłanie kodu w AAPS – w SMS Communicator nie ma ustawienia; ustalić przy spec `session` (dokumentacja / kod AAPS).
- Odpowiedź AAPS na CARBS powyżej limitu – nieznana; oba możliwe warianty parser obsługuje bezpiecznie.
- Własny limit CARBS w aplikacji (otwarte pytanie z planu).
- Etap 2 planu: porównanie kodu z aplikacji z Authenticatorem na żywo – jeszcze nie zrobione.
