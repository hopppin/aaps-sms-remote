# AGENTS.md

Wspólne zasady dla wszystkich agentów (Claude, Codex) i ludzi pracujących w tym repo. Pełny plan: [docs/plan.md](docs/plan.md).

## Projekt

Aplikacja na Androida, która wysyła polecenia SMS do telefonu z AndroidAPS (AAPS) i sama odpowiada kodem potwierdzającym (TOTP + PIN) po zatwierdzeniu biometrią. MVP: `BG`, `PUMP`, `CARBS <g>`.

## Stos technologiczny

- Kotlin, Jetpack Compose, Material 3
- minSdk 28, compileSdk/targetSdk 37, JDK 17
- Hilt (DI), Kotlin Coroutines + Flow, MVVM
- Room (historia), Android Keystore + szyfrowane DataStore/SharedPreferences, `androidx.biometric`
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
| `security` | Keystore, szyfrowanie sekretu i PIN-u, `BiometricPrompt` |
| `data` | ustawienia, historia w Room |

Logika (`commands`, `parser`, `session`, `otp`) nie zależy od Androida, żeby dało się ją testować jednostkowo bez telefonu. Nowe polecenie = nowy wpis w katalogu + wzorce w parserze + testy.

## Konwencje pracy

- Jedno issue = jedna gałąź `feature/<nr>-<krotki-opis>` = jeden PR.
- PR do `main`, w opisie `Closes #<nr>`. Tytuł i opis PR po polsku.
- Małe PR-y: ok. 200–400 linii zmian.
- Nie pushujemy bezpośrednio na `main` i nie używamy `git push --force`.
- Commity często, małymi krokami, z opisowymi wiadomościami.
- Gdy wymaganie jest niejasne, zatrzymaj się i zapytaj zamiast zgadywać.

## Bezpieczeństwo

- Nigdy nie loguj sekretu TOTP, kodów TOTP ani PIN-u (także w wyjątkach, `toString()` i komunikatach UI).
- Żadnych prawdziwych sekretów, PIN-ów ani numerów telefonu w repo. W przykładach używaj fikcyjnych danych.
- W testach TOTP używaj wyłącznie wektorów testowych z RFC 6238.
- Aplikacja nigdy nie odsyła kodu, jeśli odpowiedź AAPS nie zgadza się z poleceniem (inny numer nadawcy, inna ilość, nierozpoznana treść, odpowiedź sprzed wysłania). W razie wątpliwości: stop i komunikat dla użytkownika.
- Bez automatycznego ponawiania poleceń i bez omijania limitów AAPS.
- `allowBackup=false`; klucze podpisu (`*.jks`, `*.keystore`) i `local.properties` nie trafiają do repo.

## Definicja ukończenia

Przed zakończeniem pracy `./gradlew testDebugUnitTest lintDebug` musi przejść. Nowa logika ma testy jednostkowe. Nie wyłączaj testów ani reguł lint, żeby obejść błąd.
