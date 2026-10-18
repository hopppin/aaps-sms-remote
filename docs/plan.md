# Plan aplikacji AAPS SMS Remote

Wersja z 17.10.2026

## Cel i zakres

Aplikacja na Androida wysyła gotowe polecenia SMS do telefonu z AndroidAPS i sama odpowiada kodem potwierdzającym (TOTP + PIN). Użytkownik zatwierdza wysłanie biometrią (zapasowo: ręcznie wpisanym PIN-em AAPS) i nie musi przepisywać kodów z Authenticatora.

**Polecenia w wersji 1 (MVP):**

| Polecenie | SMS wysyłany | Wymaga potwierdzenia kodem | Parametr |
| --- | --- | --- | --- |
| Glikemia | `BG` | nie | brak |
| Stan pompy | `PUMP` | nie | brak |
| Węglowodany | `CARBS 20` | tak | ilość w gramach (np. 1–100) |

**Poza zakresem MVP:** bolus, zmiana celu, LOOP STOP/RESUME, profile. Struktura aplikacji ma pozwalać dodać je później jako kolejne wpisy w katalogu poleceń.

**Czego aplikacja nie robi:** nie omija limitów AAPS, nie ponawia poleceń automatycznie, nie wysyła nic bez zgody użytkownika.

## Wybór technologii

Wybór: natywnie w **Kotlinie z Jetpack Compose**. SMS, Keystore i biometria to API Androida, więc bez Fluttera odpada warstwa kanałów platformowych i pakietów pośrednich.

| Obszar | Rozwiązanie |
| --- | --- |
| Język / UI | Kotlin, Jetpack Compose, Material 3 |
| Min. wersja Androida | API 28 (Android 9), cel: najnowsze API |
| Wysyłanie SMS | `SmsManager` (z wyborem karty SIM przez `SubscriptionManager`) |
| Odbiór SMS | `BroadcastReceiver` na `SMS_RECEIVED` |
| TOTP | własna implementacja RFC 6238 (HMAC-SHA1, 30 s, 6 cyfr) albo biblioteka `kotlin-onetimepassword` |
| Skan kodu QR | CameraX + ML Kit Barcode Scanning |
| Bezpieczne dane | Android Keystore + `EncryptedSharedPreferences` / DataStore z szyfrowaniem |
| Biometria | `androidx.biometric` (`BiometricPrompt`) |
| Architektura | MVVM, Kotlin Coroutines + Flow, Hilt (DI) |
| Historia poleceń | Room |
| Testy | JUnit, Turbine, Compose UI Test |

Dystrybucja: plik APK instalowany ręcznie (sideload). Google Play mocno ogranicza uprawnienia SMS, a do użytku własnego sklep nie jest potrzebny.

## Przepływ działania

Dla poleceń bez potwierdzenia (BG, PUMP) użytkownik dotyka ekranu tylko raz: wybiera polecenie. Dla CARBS wybiera polecenie, wpisuje ilość i zatwierdza podsumowanie biometrią lub ręcznie wpisanym PIN-em AAPS. Resztę robi aplikacja.

**Polecenie bez potwierdzenia (BG, PUMP):**

1. Użytkownik wybiera polecenie z listy.
2. Aplikacja wysyła SMS `BG` lub `PUMP` na numer AAPS.
3. Aplikacja czeka na odpowiedź z tego numeru (limit np. 3 min) i pokazuje ją na ekranie.

**Polecenie z potwierdzeniem (CARBS):**

1. Użytkownik wybiera CARBS i wpisuje ilość, np. 20 g.
2. Ekran podsumowania: „Wysłać CARBS 20 g do AAPS?” + przycisk Zatwierdź.
3. Zatwierdzenie biometrią. To odblokowuje klucz w Keystore, a z nim zapisany PIN AAPS. Zapas: użytkownik wpisuje PIN AAPS ręcznie przy każdym poleceniu (aplikacja nie ma własnego PIN-u, bo nie da się nim odblokować klucza Keystore).
4. Aplikacja wysyła SMS `CARBS 20`.
5. AAPS odsyła prośbę o kod. Aplikacja sprawdza, czy wiadomość przyszła z numeru AAPS i czy dotyczy dokładnie 20 g.
    - zgadza się → krok 6
    - nie zgadza się albo brak odpowiedzi → stop, komunikat dla użytkownika, nic nie jest odsyłane
6. Aplikacja liczy bieżący kod TOTP, dokleja PIN AAPS i odsyła odpowiedź.
7. AAPS potwierdza wykonanie. Aplikacja pokazuje wynik i zapisuje wpis w historii.

Jeśli kod TOTP jest bliski wygaśnięcia (np. mniej niż 5 s ważności), aplikacja czeka na następny, żeby uniknąć odrzucenia.

## Architektura i moduły

Całość to jedna aplikacja z wyraźnie rozdzielonymi warstwami. Logikę potwierdzania da się wtedy testować bez telefonu i bez prawdziwych SMS-ów.

| Moduł | Odpowiedzialność |
| --- | --- |
| `ui` | ekrany Compose, ViewModele, stan ekranów |
| `commands` | katalog poleceń: nazwa, szablon SMS, parametry, walidacja, czy wymaga kodu |
| `sms` | wysyłanie przez `SmsManager`, odbiór przez `BroadcastReceiver`, filtr po numerze AAPS |
| `session` | maszyna stanów jednego polecenia: Wysłane → Czeka na prośbę o kod → Kod wysłany → Zakończone / Błąd / Timeout |
| `parser` | rozpoznawanie odpowiedzi AAPS (prośba o kod, sukces, błąd, odrzucenie) na podstawie wzorców |
| `otp` | generowanie TOTP z sekretu, kontrola czasu ważności kodu |
| `security` | Keystore, szyfrowanie sekretu i PIN-u, `BiometricPrompt`, blokada aplikacji |
| `data` | ustawienia (numer AAPS, karta SIM), historia poleceń w Room |

Katalog poleceń jest danymi, a nie kodem rozsianym po ekranach. Dodanie bolusa czy zmiany celu w przyszłości to nowy wpis w katalogu, nowe wzorce w parserze i testy.

## Bezpieczeństwo

Sekret TOTP i PIN AAPS są szyfrowane kluczem z Android Keystore, który odblokowuje się tylko po biometrii lub PIN-ie urządzenia. Kradzież pliku z danymi aplikacji nic wtedy nie daje.

- **Sekret TOTP:** wczytany raz ze zeskanowanego QR (`otpauth://…`), zapisany zaszyfrowany. Nigdy nie jest pokazywany ani logowany.
- **PIN AAPS:** żeby potwierdzać samą biometrią, PIN musi być zapisany. Szyfruje go klucz z `setUserAuthenticationRequired(true)`, więc bez biometrii nie da się go odczytać.
- **Tryb zapasowy:** zamiast biometrii użytkownik wpisuje PIN AAPS ręcznie przy każdym poleceniu; zapisany PIN nie jest wtedy odczytywany. Sekret TOTP odblokowuje w tym trybie blokada ekranu telefonu. Aplikacja nie ma własnego PIN-u.
- **Unieważnienie klucza:** po dodaniu nowego odcisku palca Keystore unieważnia klucz i trzeba ponownie podać PIN. To zamierzone zachowanie.
- **Brak kopii:** `allowBackup=false`, żeby sekret nie trafiał do kopii zapasowej Google.
- **Ekran:** `FLAG_SECURE` na ekranach z danymi, czyli brak zrzutów i podglądu w ostatnich aplikacjach.
- **Świadome ryzyko:** sekret TOTP i aplikacja na jednym telefonie to w praktyce jeden czynnik. Ochroną jest blokada telefonu, biometria i to, że AAPS przyjmuje SMS tylko z dozwolonego numeru.

## Parsowanie odpowiedzi i obsługa błędów

Aplikacja odsyła kod tylko wtedy, gdy odpowiedź AAPS przyszła z właściwego numeru i dotyczy dokładnie wysłanego polecenia. Każda wątpliwość kończy się zatrzymaniem, a nie zgadywaniem.

**Zasady parsera:**

- wzorce oparte na prawdziwych SMS-ach z Twojej wersji i języka AAPS, zebranych w etapie 1
- dla CARBS sprawdzenie, że liczba gramów w prośbie o kod = liczba wysłana
- obsługa wiadomości dzielonych na kilka części (długie SMS)
- ignorowanie SMS-ów z innych numerów i odpowiedzi, które przyszły przed wysłaniem polecenia
- tylko jedno aktywne polecenie naraz, kolejne przycisk blokuje do zakończenia

| Sytuacja | Reakcja aplikacji |
| --- | --- |
| Brak odpowiedzi w limicie czasu | komunikat „AAPS nie odpowiada”, bez ponawiania |
| Prośba o kod z inną ilością | stop, pokazanie treści SMS, nic nie jest odsyłane |
| Nierozpoznana odpowiedź | pokazanie surowej treści SMS użytkownikowi |
| AAPS odrzuca kod | komunikat + sprawdzenie zegara telefonu (TOTP wymaga dokładnego czasu) |
| SMS nie wysłał się (brak zasięgu, SIM) | błąd z `SmsManager` pokazany od razu |
| Odrzucenie przez limity AAPS | pokazanie komunikatu AAPS bez prób obejścia |

## Ekrany aplikacji

Pięć ekranów wystarczy na MVP.

| Ekran | Zawartość |
| --- | --- |
| Konfiguracja (pierwsze uruchomienie) | numer telefonu AAPS, wybór karty SIM, skan QR sekretu TOTP, podanie PIN-u AAPS, włączenie biometrii, test połączenia `BG` |
| Polecenia (główny) | duże przyciski BG, PUMP, CARBS; ostatnia znana glikemia i czas odpowiedzi |
| CARBS | pole ilości (klawiatura numeryczna, szybkie wartości 10/20/30 g), ekran podsumowania, przycisk Zatwierdź |
| Przebieg polecenia | kroki na żywo: wysłano → AAPS prosi o kod → kod wysłany → wykonano; treść SMS z AAPS |
| Historia i ustawienia | lista poleceń z wynikiem i czasem; zmiana numeru, SIM, ponowny skan QR, limit oczekiwania |

## Etapy realizacji

Sześć etapów, każdy kończy się czymś, co da się sprawdzić na telefonie. Najryzykowniejsze elementy, czyli SMS i TOTP, są sprawdzane najwcześniej.

**Etap 1. Rozpoznanie**

- [ ] Zebrać prawdziwe SMS-y z AAPS dla BG, PUMP i CARBS (polecenie, prośba o kod, sukces, błąd)
- [ ] Zapisać wersję AAPS i język komunikatów
- [ ] Sprawdzić ustawienia SMS Communicatora: dozwolone numery, czas na potwierdzenie

**Etap 2. Prototyp techniczny**

- [ ] Projekt w Android Studio (Kotlin, Compose, Hilt)
- [ ] Wysłanie SMS z wybranej karty SIM
- [ ] Odbiór SMS z filtrem po numerze
- [ ] Generator TOTP i porównanie kodów z Authenticatorem w tej samej sekundzie

**Etap 3. Konfiguracja i bezpieczeństwo**

- [ ] Ekran konfiguracji ze skanem QR
- [ ] Szyfrowanie sekretu i PIN-u w Keystore
- [ ] `BiometricPrompt` + tryb zapasowy z ręcznym PIN-em

**Etap 4. Polecenia i maszyna stanów**

- [ ] Katalog poleceń (BG, PUMP, CARBS)
- [ ] Parser odpowiedzi z testami jednostkowymi na zebranych SMS-ach
- [ ] Sesja polecenia z timeoutami i weryfikacją ilości

**Etap 5. Interfejs i historia**

- [ ] Ekran główny, CARBS z podsumowaniem, ekran przebiegu
- [ ] Historia w Room, ustawienia

**Etap 6. Testy na żywo i wydanie**

- [ ] Testy z pompą wirtualną w AAPS
- [ ] Testy na prawdziwej konfiguracji (najpierw BG i PUMP, potem CARBS)
- [ ] Podpisany APK i instrukcja instalacji

## Testy i wdrożenie

Zanim aplikacja wyśle prawdziwe CARBS, przechodzi trzy poziomy testów. Każdy kolejny zaczyna się dopiero, gdy poprzedni jest zielony.

1. **Testy jednostkowe:** TOTP zgodny z wektorami RFC 6238, parser na zebranych SMS-ach, maszyna stanów z symulowanymi odpowiedziami, timeoutami i błędną ilością.
2. **Pompa wirtualna:** AAPS na drugim telefonie z pompą wirtualną (Virtual Pump), pełny przepływ BG, PUMP i CARBS bez wpływu na prawdziwą terapię.
3. **Konfiguracja docelowa:** najpierw tylko BG i PUMP przez kilka dni, potem CARBS z małymi wartościami i ręczną kontrolą w AAPS.

Wdrożenie: podpisany APK z własnym kluczem, instalacja z pliku, aktualizacje tym samym kluczem. Przed każdą aktualizacją AAPS warto sprawdzić, czy treść odpowiedzi SMS się nie zmieniła.

## Otwarte kwestie

- [ ] Jaka wersja AAPS i w jakim języku są komunikaty SMS?
- [ ] Przykładowe SMS-y: prośba o kod dla CARBS, sukces, błąd (do etapu 1)
- [ ] Jak przenieść sekret TOTP: ponowne wygenerowanie QR w AAPS czy eksport z obecnego Authenticatora?
- [ ] Domyślne potwierdzenie: biometria z zapisanym PIN-em czy ręczny PIN za każdym razem?
- [ ] Telefon nadawcy: jedna karta SIM czy dual SIM, i czy jego numer jest na liście dozwolonych w AAPS?
- [ ] Maksymalna ilość CARBS do wpisania w aplikacji (dodatkowy limit poza AAPS)?
