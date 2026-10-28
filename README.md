# aaps-sms-remote

Aplikacja na Androida do wysyłania poleceń SMS do telefonu z AndroidAPS. Dla polecenia `CARBS <g>` po otrzymaniu prośby AAPS aplikacja odsyła kod potwierdzający, łączący kod TOTP z PIN-em AAPS wpisanym przy tym poleceniu. Polecenia `BG` i `PUMP` są wysyłane jednym dotknięciem, bez kodu potwierdzającego. Wersja MVP obsługuje polecenia `BG`, `PUMP` i `CARBS <g>`.

## Wymagania

- JDK 17
- Android SDK
- Android 9 (API 28) lub nowszy (`minSdk 28`)

## Budowanie i sprawdzanie

Uruchom w katalogu projektu:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Skrypt `scripts/sprawdz.sh` uruchamia testy jednostkowe i lint; opcja `--pelne` dodaje budowanie aplikacji:

```bash
bash scripts/sprawdz.sh
bash scripts/sprawdz.sh --pelne
```

## Dokumentacja

- [Zasady pracy](AGENTS.md)
- [Plan projektu](docs/plan.md)
- [Specyfikacje](docs/specs/README.md)
- [Stan prac](docs/stan-prac.md)
