# Stan prac

Krótki obraz tego, gdzie jest projekt. Czytaj na początku sesji; aktualizuj na końcu (skill `/aktualizuj-stan`). Szczegóły: [plan](plan.md), [specyfikacje](specs/README.md), [zasady](../AGENTS.md).

_Ostatnia aktualizacja: 2026-10-21_

## Zrobione

| Obszar | Co | Gdzie |
| --- | --- | --- |
| Szkielet | Android (Kotlin, Compose, Hilt, minSdk 28, SDK 37), CI na PR, CodeRabbit, AGENTS.md | PR #1 |
| Proces | spec-driven: szablony `docs/specs/_szablon/`, zasady w `docs/specs/README.md` | PR #2 |
| Spec 001 TOTP | zatwierdzona, w całości zaimplementowana | PR #3; kod: PR #8, #9, #10, #11 |
| Pakiet `otp` | generator RFC 6238, okno ważności, `UsableTotpProvider` (≥ 5 s zapasu), dekoder Base32, parser `otpauth://` | `app/src/main/java/pl/twojanazwa/aapsremote/otp/` |
| Spec 002 parser | zatwierdzona, w całości zaimplementowana; prawdziwe SMS-y AAPS 3.4.2.6 jako dane testowe | PR #12; kod: PR #16, #18, #19; dane: `docs/specs/002-parser-odpowiedzi/dane/` |
| Pakiet `parser` | `AapsReplyParser`: BG, PUMP, CARBS (prośba o kod, niezgodna ilość, potwierdzenie, zły kod); normalizacja, redakcja `toString()`, macierz plik × kontekst | `app/src/main/java/pl/twojanazwa/aapsremote/parser/` |
| Spec 003 katalog | zatwierdzona, w całości zaimplementowana | PR #21; kod: PR #24, #25 |
| Pakiet `commands` | `CommandCatalog` (BG, PUMP, CARBS), `Command` (`Carbs` tylko przez `of`/`parse`, 1–50 g, dodatkowe potwierdzenie > 40 g), `smsText()`, `toExpectedCommand()`, redakcja ilości w `toString()` | `app/src/main/java/pl/twojanazwa/aapsremote/commands/` |
| Spec 004 sesja | zatwierdzona, w całości zaimplementowana; fakty z kodu AAPS (źródła A1–A7) | PR #27; kod: PR #37–#45 |
| Pakiet `session` | `CommandSession` (maszyna stanów BG/PUMP/CARBS, limity 3 min / 10 s / 6 min z pierwszeństwem limitu, kontrola wieku prośby o kod ≤ 240 s, anulowanie, wynik `Unknown` po kodzie, `StateFlow` i `SessionResult` dla historii), `SessionCoordinator` (jedno aktywne polecenie), porty `SmsSender`/`IncomingSmsSource` | `app/src/main/java/pl/twojanazwa/aapsremote/session/` |
| `security/AapsPin` | PIN AAPS tylko w pamięci, kopia z zerowaniem, czyszczenie idempotentne | `app/src/main/java/pl/twojanazwa/aapsremote/security/AapsPin.kt` |
| Spec 006 sekret TOTP | zatwierdzona, w całości zaimplementowana | PR #65; kod: PR #76–#85 (issues #66–#75) |
| Pakiet `security` (sekret TOTP) | `KeystoreTotpSecretStore` (`TotpSecretStore`/`TotpSecretProvider`: `save`/`load`/`delete`/`refreshState`, `StateFlow<SecretState>`), rekord Base64 `[0x01][IV 12][szyfrogram+tag]` (`SealedSecretFormat`, ścisłe Base64), `AesGcmSecretCipher` (AAD = wersja), `CryptoFailureClassifier` (blokada → `LOCKED`, KPI / brak klucza → `KEY_INVALIDATED`, tag → `CORRUPTED`, reszta → `UNAVAILABLE`), wyczyszczenie po trwałym błędzie, wycofanie po nieudanym `commit()`, Mutex + `Dispatchers.IO`, brak pól z danymi; `security.platform`: `AndroidKeystoreKeySource` (AES-256-GCM, `setUnlockedDeviceRequired`, StrongBox → TEE), `AndroidKeystoreEnvironment`, `SharedPreferencesSecretRecordStorage` (`totp_secret_store`/`sealed_secret`, `commit()`), `SecurityModule` | `app/src/main/java/pl/twojanazwa/aapsremote/security/` |
| Spec 005 SMS | zatwierdzona, w całości zaimplementowana; kontrakt K1–K6 ze spec 004 przejęty jako SMS-01–SMS-16 | PR #47; kod: PR #56–#63 |
| Pakiet `sms` | `AapsNumber` (ostatnie 9 cyfr), `SmsAssembler` (sklejanie części po nadawcy), `DeliveredSmsRegistry` (SHA-256, 100 wpisów FIFO), `IncomingSmsHub` (gorący strumień, chwila odbioru z `EpochClock`, jedna rejestracja receivera, brak uprawnienia = brak dostarczeń), `AapsSmsSender` (`Sent` po `RESULT_OK` wszystkich części, anulowanie bez wysyłania, wyjątki = `Failed`); `sms.platform`: receiver `SMS_RECEIVED` (`BROADCAST_SMS`), `AndroidSmsSystemApi` (domyślna karta SMS), `AndroidSmsPermissions`, `SmsModule`; `otp/ClockModule` (wspólny `EpochClock`); manifest: `SEND_SMS`, `RECEIVE_SMS` | `app/src/main/java/pl/twojanazwa/aapsremote/sms/` |
| Spec 007 dane | zatwierdzona, w całości zaimplementowana | PR #87; kod: PR #99–#109 (issues #88–#98) |
| Pakiet `data` | `AapsNumberSettings` (`AapsNumberProvider` + `AapsNumberSettingsStore`: stany `Loading`/`NotConfigured`/`Configured`/`LoadFailed`, zapis tylko numeru poprawnego wg `AapsNumber.parse` w postaci `destination`, usunięcie, `currentNumber()` z `StateFlow` bez I/O, Mutex + `@DataDispatcher`), historia: `HistoryCodec` (stabilne kody tekstowe, nieznane → `Unrecognized`), `HistoryRepository` (retencja 200 wpisów w jednej transakcji, `Flow<HistorySnapshot>` od najnowszego po `id`, `Unavailable` przy błędzie, czyszczenie całości), `SessionHistoryRecorder` (`track(session.state)`, jeden wpis na sesję, kolejność zakończenia – sprawiedliwy Mutex); wpis bez PIN-u, kodu, sekretu, numeru i treści wiadomości nieoczekiwanych (tylko ich liczba); `data.platform`: `SharedPreferencesSettingsStorage` (`settings`/`aaps_number`, `commit()`), Room 2.8.5 (`aaps_remote.db`, tabela `command_history`, schemat v1 w `app/schemas/`, bez destrukcyjnej migracji), `DataModule` (Hilt, m.in. wiązanie `sms.AapsNumberProvider`) | `app/src/main/java/pl/twojanazwa/aapsremote/data/` |

## W toku

Brak otwartych issues i PR-ów.

Stan PR-ów: `gh pr list`, stan issues: `gh issue list`.

## Następne kroki

1. `ui` (ekrany; przyciski blokowane przez `SessionCoordinator.activeSession`). Wymagania ze spec 005 dla `ui`: w trakcie polecenia aplikacja na pierwszym planie, ekran przebiegu trzyma ekran włączony; prośba o `SEND_SMS`/`RECEIVE_SMS` i komunikat przy odmowie (`SmsPermissions`). Pierwszy PR `ui` uruchamiający wysyłanie i odbiór opisuje ręczną weryfikację W1–W7 ze [spec 005](specs/005-sms/spec.md) na telefonie. Wymagania ze spec 006 dla `ui`: skan QR → `TotpSecretStore.save`, pytanie o zastąpienie istniejącego sekretu, komunikaty dla `Locked` („odblokuj telefon”), `Invalidated` („zeskanuj ponownie QR z AAPS”), `Unavailable`; sekret odczytywany (`TotpSecretProvider.load`) tuż przed startem polecenia z kodem i nietrzymany w ViewModelu dłużej niż do przekazania `CodeCredentials`. Pierwszy PR `ui` zapisujący sekret opisuje ręczną weryfikację W1–W10 ze [spec 006](specs/006-sekret-totp/spec.md) (m.in. W7: telefon bez blokady ekranu). Wymagania ze spec 007 dla `ui`: przyciski nieaktywne, gdy numer w stanie `Loading`; `SessionHistoryRecorder.track(session.state)` zaraz po `StartResult.Started`; blokada zmiany i usunięcia numeru AAPS, gdy `activeSession != null`; potwierdzenie przed wyczyszczeniem historii; ręczna weryfikacja W1–W7 ze [spec 007](specs/007-dane/spec.md) w pierwszym PR `ui` z ustawieniami i historią.
2. Ewentualnie później: przyczyna w `SendResult.Failed` (zmiana spec 004), usługa pierwszoplanowa na czas polecenia (osobna spec), `TotpSecret.clear()` po zakończeniu polecenia (zmiana spec 001 i 004, „Przyszłe zmiany” w spec 006), doprecyzowanie w design 006: `encrypt` przy pustym tekście jawnym → `UNAVAILABLE`, `CancellationException` w szkicach `KeyGenerationPolicy`/klasyfikatora; numer AAPS ustalany raz na polecenie (zmiana spec 004/005, „Przyszłe zmiany” w spec 007).

## Ustalenia z użytkownikiem (decyzje)

- **Bez biometrii.** PIN AAPS wpisywany przy każdym poleceniu z potwierdzeniem, nigdy niezapisywany (plan, AGENTS.md).
- **CARBS w aplikacji:** liczba całkowita 1–50 g; powyżej 40 g dodatkowe pytanie „Na pewno X g?” przed PIN-em; szybkie wartości 10/20/30 g; SMS zawsze `CARBS <g>` bez godziny. BG i PUMP wysyłane jednym dotknięciem, bez potwierdzenia (spec 003).
- **Sekret TOTP:** każdy niepusty sekret jest przyjmowany; nieznane parametry `otpauth://` są ignorowane. QR z AAPS: `otpauth://totp/AAPS?secret=…&issuer=AAPS&algorithm=SHA1&digits=6&period=30`.
- **SMS (spec 005):** tylko domyślna karta SMS systemu (bez wyboru SIM w ustawieniach); nadawca = numer AAPS, gdy zgadza się ostatnie 9 cyfr; odbiór tylko w trakcie zbierania strumienia (receiver dynamiczny, bez usługi pierwszoplanowej); brak uprawnień = `Failed` / brak dostarczeń, bez wyjątków. Ryzyko zaakceptowane: Android zapisuje wysłany SMS `<kod><PIN>` w skrzynce wysłanych wiadomości. Warstwa `sms.platform` bez testów automatycznych – weryfikacja ręczna W1–W7.
- **Sekret TOTP (spec 006):** klucz AES-256-GCM w Android Keystore, bez biometrii i bez uwierzytelniania przy użyciu, `setUnlockedDeviceRequired(true)`, StrongBox gdy jest (inaczej TEE); jeden sekret, ponowny skan nadpisuje; utrata/unieważnienie klucza = usunięcie i ponowny skan QR, zablokowany telefon i niepewne błędy niczego nie kasują; szyfrogram w zwykłym `SharedPreferences` (bez wycofanej `security-crypto`). Warstwa `security.platform` bez testów automatycznych – weryfikacja ręczna W1–W10.
- **Dane (spec 007):** numer AAPS w zwykłym prywatnym `SharedPreferences` (bez szyfrowania, wykluczony z kopii zapasowej – decyzja użytkownika, AGENTS.md doprecyzowany); historia: ostatnie 200 wpisów, zapisywane polecenie, ilość CARBS, rodzaj wyniku z powodem, czasy, treść odpowiedzi AAPS i liczba wiadomości nieoczekiwanych; użytkownik czyści tylko całą historię naraz. Warstwa `data.platform` bez Robolectric – test schematu JVM i weryfikacja ręczna W1–W7.
- **SMS z AAPS 3.4.2.6, po polsku, bez znaków diakrytycznych.** Każda odpowiedź to jeden SMS. Odpowiedź na kod: `<6 cyfr><PIN>` bez spacji.
- **Brak odpowiedzi AAPS:** na nieznane polecenie, z numeru spoza listy dozwolonych i po upływie czasu na kod AAPS nic nie wysyła.
- **Sesja (spec 004):** pierwsza odpowiedź AAPS do 3 min (potem „AAPS nie odpowiada”); po kodzie czekanie na potwierdzenie do 6 min, potem „nieznany – sprawdź w AAPS”. Potwierdzenie CARBS przyjmowane tylko po wysłaniu naszego kodu, z tą samą ilością, pokazywane jako „AAPS potwierdził wprowadzenie X g” (AAPS wysyła je do wszystkich dozwolonych numerów); w innym momencie – wiadomość nieoczekiwana (bez zmiany fazy). Przed kodem każda inna wątpliwa odpowiedź kończy polecenie bez odsyłania kodu; po kodzie – „nieznany”, z wyjątkiem „zły kod” (błąd). Limity i anulowanie obejmują też fazy wysyłania (polecenia i kodu). Anulowanie przed rozpoczęciem wysyłania kodu zawsze możliwe; potem tylko kończy czekanie („nieznany”). Szczegóły: tabela przejść w [design 004](specs/004-sesja-polecenia/design.md).
- **Fakty z kodu AAPS** (spec 004, źródła A1–A7): czas na kod 5 min, po nim cisza; CARBS ponad limit AAPS obcina i prosi o kod z mniejszą ilością (parser: niezgodna ilość → stop); po kodzie AAPS czeka do 3 min na kolejkę pompy; każde nowe polecenie nadpisuje oczekującą prośbę o kod.

## Otwarte (do ustalenia)

- Etap 2 planu: porównanie kodu z aplikacji z Authenticatorem na żywo – jeszcze niezrobione.
