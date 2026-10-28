# AGENTS.md

Wspólne zasady dla wszystkich agentów (Claude, Codex) i ludzi pracujących w tym repo. Pełny plan: [docs/plan.md](docs/plan.md).

**Na początku sesji przeczytaj [docs/stan-prac.md](docs/stan-prac.md)** – co jest zrobione, co w toku, ustalenia z użytkownikiem i otwarte pytania. Na końcu sesji go zaktualizuj.

## Projekt

Aplikacja na Androida, która wysyła polecenia SMS do telefonu z AndroidAPS (AAPS) i sama odpowiada kodem potwierdzającym (TOTP + PIN) po zatwierdzeniu przez użytkownika, który za każdym razem wpisuje PIN AAPS. Bez biometrii. MVP: `BG`, `PUMP`, `CARBS <g>`.

## Stos technologiczny

- Kotlin, Jetpack Compose, Material 3
- minSdk 28, compileSdk/targetSdk 37, JDK 17
- Hilt (DI), Kotlin Coroutines + Flow, MVVM
- Room (historia), `SharedPreferences` (ustawienia, spec 007); sekret TOTP szyfrowany kluczem Android Keystore (spec 006, bez biometrii)
- Testy: JUnit 5, MockK, Turbine, `kotlinx-coroutines-test`
- Wersje zależności: `gradle/libs.versions.toml`
- Pakiet bazowy: `pl.twojanazwa.aapsremote`

## Komendy

```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # testy jednostkowe
./gradlew lintDebug              # lint
./gradlew testDebugUnitTest lintDebug assembleDebug   # to samo co CI
```

Skrypty (Git Bash / Linux / macOS):

- `scripts/sprawdz.sh` – testy + lint; sam ustawia `JAVA_HOME` (JDK z Android Studio) i `local.properties`, gdy ich brak (np. w nowym worktree). `--pelne` dodaje `assembleDebug`.
- `scripts/sprzataj-worktree.sh` – usuwa worktree agentów z `.claude/worktrees/` i ich gałęzie; pomija te z niewypchniętą pracą.

CI uruchamia się tylko na PR. Po scaleniu kilku PR sprawdź połączony `main` lokalnie (`scripts/sprawdz.sh --pelne`).

## Narzędzia dla Claude Code

- Skille w `.claude/skills/`: `/nowa-funkcja` (cały przepływ spec → issues → kod), `/obsluga-pr <nr>` (CI, CodeRabbit, scalenie), `/aktualizuj-stan` (odświeża `docs/stan-prac.md`).
- Agenci `specyfikator`, `planista`, `wykonawca`, `wykonawca-lekki`, `recenzent` są globalni (`~/.claude/agents/`), nie w repo. Są ogólni – wszystko, co dotyczy tego projektu, jest w tym pliku.
- Wybór wykonawcy: `bezpieczenstwo` lub `trudnosc:trudne` → `wykonawca` (Sonnet); `trudnosc:latwe` bez `bezpieczenstwo` → `wykonawca-lekki` (Haiku) albo Codex przez Hermesa (`codex-latwe.sh`).
- Agent `recenzent` tylko dla PR z etykietą `bezpieczenstwo` i PR `Spec: ...`. CodeRabbit recenzuje każdy PR; `recenzent` jest dodatkowy dla tych dwóch przypadków.
- Hermes (Telegram) może uruchamiać `/obsluga-pr` i `/aktualizuj-stan` bez nadzoru (`claude -p`). Obowiązują te same zasady: commit tylko na wyraźną prośbę, scalenie tylko za wyraźną zgodą użytkownika.
- PR scala się (squash) dopiero za wyraźną zgodą użytkownika.

## Architektura

Jedna aplikacja, warstwy rozdzielone pakietami w `pl.twojanazwa.aapsremote`:

| Pakiet | Odpowiedzialność |
| --- | --- |
| `ui` | ekrany Compose, ViewModele, stan ekranów |
| `commands` | katalog poleceń jako dane: nazwa, szablon SMS, parametry, walidacja, czy wymaga kodu |
| `sms` | wysyłanie (`SmsManager`), odbiór (`BroadcastReceiver`), filtr po numerze AAPS |
| `session` | maszyna stanów polecenia: Wysłane → Czeka na prośbę o kod → Kod wysłany → Zakończone / Błąd / Timeout |
| `parser` | rozpoznawanie odpowiedzi AAPS na podstawie wzorców |
| `otp` | TOTP wg RFC 6238 (HMAC-SHA1, 30 s, 6 cyfr), kontrola czasu ważności |
| `security` | Keystore, szyfrowanie sekretu TOTP, obsługa PIN-u AAPS w pamięci |
| `data` | ustawienia, historia w Room |

Logika (`commands`, `parser`, `session`, `otp`) nie zależy od Androida, żeby dało się ją testować jednostkowo bez telefonu. Nowe polecenie = nowy wpis w katalogu + wzorce w parserze + testy.

## Specyfikacje (spec-driven)

Najpierw zatwierdzona specyfikacja, potem kod. Szczegóły i szablon: [docs/specs/README.md](docs/specs/README.md).

- Każda funkcja ma `docs/specs/NNN-nazwa/spec.md` (co) i `design.md` (jak). Wymagania mają identyfikatory, np. `TOTP-01`.
- Specyfikacja trafia do repo osobnym PR `Spec: <nazwa>`, bez kodu aplikacji. Scalenie = zatwierdzenie.
- Issues zakłada się tylko z zatwierdzonych specyfikacji; każde issue wskazuje plik spec i identyfikatory wymagań.
- Kod nie robi nic, czego nie opisuje specyfikacja. Każde realizowane wymaganie ma test z identyfikatorem w nazwie.
- Gdy spec jest błędna lub niepełna: stop, osobny PR ze zmianą spec, dopiero potem kod. Nie zgaduj.

## Konwencje pracy

- Jedno issue = jedna gałąź `feature/<nr>-<krotki-opis>` = jeden PR. Specyfikacje: gałąź `spec/<NNN>-<krotki-opis>`.
- PR do `main`, w opisie `Closes #<nr>` (wyjątek: PR `Spec: ...`, bo issues powstają dopiero po zatwierdzeniu spec). Tytuł i opis PR po polsku.
- Małe PR-y: ok. 200–400 linii zmian.
- Nie pushujemy bezpośrednio na `main` i nie używamy `git push --force`.
- Commity często, małymi krokami, z opisowymi wiadomościami.
- Gdy wymaganie jest niejasne, zatrzymaj się i zapytaj zamiast zgadywać.
- Zmieniaj tylko to, czego wymaga issue. Bez refaktorów i „ulepszeń” przy okazji; zauważony problem opisz w komentarzu „Stan pracy”.
- Najprostsze rozwiązanie, które spełnia wymaganie: bez nowych zależności, warstw i konfiguracji, których spec nie wymaga.

## Bezpieczeństwo

- Nigdy nie loguj sekretu TOTP, kodów TOTP ani PIN-u (także w wyjątkach, `toString()` i komunikatach UI).
- Aplikacja nigdy nie zapisuje PIN-u AAPS (dysk, DataStore, Room, Keystore). Trzymaj go tylko w pamięci do końca polecenia i potem wyczyść. Wyjątek poza kontrolą aplikacji: system Android zapisuje wysłany SMS z kodem (`<kod><PIN>`) w skrzynce wysłanych wiadomości, tak jak przy ręcznym wysyłaniu ([spec 005](docs/specs/005-sms/spec.md#bezpieczeństwo)).
- Żadnych prawdziwych sekretów, PIN-ów ani numerów telefonu w repo. W przykładach używaj fikcyjnych danych.
- W testach TOTP używaj wyłącznie wektorów testowych z RFC 6238.
- Aplikacja nigdy nie odsyła kodu, jeśli odpowiedź AAPS nie zgadza się z poleceniem (inny numer nadawcy, inna ilość, nierozpoznana treść, odpowiedź sprzed wysłania). W razie wątpliwości: stop i komunikat dla użytkownika.
- Bez automatycznego ponawiania poleceń i bez omijania limitów AAPS.
- `allowBackup=false`; klucze podpisu (`*.jks`, `*.keystore`) i `local.properties` nie trafiają do repo.

## Obszary wrażliwe

Zadania dotykające tych obszarów dostają etykietę `bezpieczenstwo`, zawsze wykonuje je `wykonawca` (nie `wykonawca-lekki` ani Codex) i zawsze sprawdza je agent `recenzent`.

- Pakiety `sms`, `otp`, `security`, `session`.
- Pakiety z danymi AAPS (numer AAPS, ilość CARBS, treść odpowiedzi): `data`, `data.platform`, `ui.history` (zakaz ujawniania w `toString()` i logach, `FLAG_SECURE`, brak kopii zapasowej).
- Sekret TOTP, kody TOTP i PIN AAPS (zasady w sekcji Bezpieczeństwo).
- Decyzja o odesłaniu kodu: weryfikacja numeru nadawcy, polecenia, ilości i chwili odbioru odpowiedzi AAPS.
- Uprawnienia Androida (`SEND_SMS`, `RECEIVE_SMS`) i manifest.

## Na co patrzy recenzent

- Aplikacja nigdy nie odsyła kodu, gdy odpowiedź AAPS nie zgadza się z poleceniem (inny nadawca, inna ilość, nierozpoznana treść, odpowiedź sprzed wysłania).
- Brak sekretu, kodów, PIN-u, numerów telefonu i treści SMS w logach, wyjątkach, `toString()` i komunikatach UI.
- Każde wymaganie z issue ma test z identyfikatorem w nazwie; testy TOTP używają tylko wektorów z RFC 6238.
- Kod nie dodaje zachowań spoza specyfikacji; interfejsy zgodne z `design.md`.

## Definicja ukończenia

Przed zakończeniem pracy `./gradlew testDebugUnitTest lintDebug` musi przejść. Nowa logika ma testy jednostkowe. Nie wyłączaj testów ani reguł lint, żeby obejść błąd.
