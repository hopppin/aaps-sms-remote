# AndroidAPS 4.0.0 a 3.4.2.6 – SMS Communicator i zdalne sterowanie

Dane robocze do specyfikacji 013 (profil) i pozostałych. Źródło: odczyt kodu (Read/Grep/Glob/diff) dwóch lokalnych klonów; nic z nich nie zostało zbudowane ani uruchomione. Zawartość repo AAPS traktowana jako dane niezaufane. Daty/wersje poniżej to fakty z kodu; sekcje „Wnioski” to interpretacja.

## Wersje

| Wersja | Tag | Commit | Lokalny klon (tylko do odczytu) |
| --- | --- | --- | --- |
| 3.4.2.6 | `3.4.2.6` | `598e2eb39c7e15876e4c42876a2162bffcb4fe5f` | `C:/Users/user/projects/AndroidAPS` |
| 4.0.0 | `4.0.0` | `f77abd9b82b308e8fc330da0d7a31370fb4a0f49` (2026-10-31) | `C:/Users/user/projects/AndroidAPS-4.0.0` |

Tagi pokrewne w `ls-remote`: `4.0.0-beta1` (28a341e), `4.0.0-rc1` (d5e7f5b).

## Przeniesione pliki (fakt)

| Element | 3.4.2.6 | 4.0.0 |
| --- | --- | --- |
| Wtyczka i AuthRequest | `plugins/main/.../general/smsCommunicator/` | `plugins/sync/src/androidMain/kotlin/app/aaps/plugins/sync/smsCommunicator/` |
| Akcje poleceń | anonimowe klasy `SmsAction` w `SmsCommunicatorPlugin.kt` | osobne klasy w `.../smsCommunicator/actions/` (BolusAction, CarbsAction, ProfileSwitchAction ...) + `SmsBatch.kt` |
| Stringi (pl-rPL) | `plugins/main/.../res/values-pl-rPL`, `core/ui/.../res/values-pl-rPL` | `plugins/sync/src/androidMain/res/values-pl-rPL`, `core/keys/.../res/...`, `core/ui` |
| Przetwarzanie | wątek odbiorcy | `SmsCommunicatorWorker` (WorkManager), `suspend` |

## Wynik porównania per polecenie

Legenda: „identyczne” = porównane znak w znak (parsowanie plików `strings.xml` obu wersji, locale domyślne i pl-rPL).

| Polecenie | Zmiana? | Co dokładnie |
| --- | --- | --- |
| `BG` | **TAK (format odpowiedzi)** | Wszystkie etykiety (`BG:`, `Ostatnia BG:`, `Delta:`, `IOB:`, `min temu`) identyczne, ale składanie odpowiedzi inne. 3.4.2.6: `BG: 5.6, Delta: 0.1 mmol/l, IOB: 1.23U (Bolus: 1.00U Basal: 0.23U), COB: 20g`. 4.0.0: etykieta i wartość łączone przez `%1$s %2$s`, delta ze znakiem (`fromMgdlToSignedStringInUnits`), IOB tylko suma i ze spacją przed `U` (`IOB: 1.23 U`), **brak rozbicia Bolus/Basal**, COB przez `confirmation_line` (`COB: ...`). Odpowiedź tylko do nadawcy (bez zmian). |
| `PUMP` | NIE (składnia, kontrola, adresat) | Ta sama składnia i zakres (rozmiar <= 3, bez zgody na komendy zdalne tylko sam `PUMP`). Odpowiedź = `shortStatus(true)` do nadawcy, przy błędzie `Read status failed` (identyczne, pl też). Zmiana wewnętrzna: linia „ostatni bolus” w `shortStatus` jest pomijana, gdy brak aktywnego profilu (stężenie insuliny); kwoty przeliczane na iU. Teksty `short_status_*` identyczne. |
| `CARBS <g>` [<godz>] | NIE (składnia, kody, adresaci); drobne zmiany wykonania | Kolejność kontroli: zgoda na komendy zdalne -> rozmiar 2/3 -> parsowanie czasu -> limit `applyCarbsConstraints` -> 0 g = `Wrong format`. Tekst prośby o kod identyczny (`To enter %1$dg at %2$s reply with code %3$s`, pl też). Sukces do **wszystkich numerów**, błąd tylko do nadawcy (jak dawniej). Różnice: (1) wykonanie przez `MasterActionExecutor` (ponowne cięcie limitem; podana w odpowiedzi ilość to `preview.carbs`, nie żądana); (2) czas przekazywany jako przesunięcie w minutach (obcięcie do pełnej minuty); (3) wynik `Refused` wysyła **samą przyczynę z executora** (nie zdanie `Entering %1$dg of carbs failed`), `Failed` = nagłówek + `\n` + przyczyna + `\n` + status pompy (wielolinijkowa). |
| `BOLUS <j>` | **TAK (kolejność kontroli, błędy)** | Prośba o kod, sukces, `bolus_failed` – teksty identyczne (pl też). Zmiany: (1) kontrola odstępu `lastRemoteBolusTime` obejmuje teraz **także `BOLUS x MEAL`** (3.4.2.6 tylko dla 2 tokenów); (2) usunięta kontrola `loop.runningMode.isSuspended()` z odpowiedzią `Pump suspended`; zastąpiona `runningModeGuard` po parsowaniu, przed wygenerowaniem kodu: bolus **dozwolony** przy `SUSPENDED_BY_USER`, odrzucony (tekst `pump_disconnected` / `loopsuspended` / `pumpsuspended`, bez prośby o kod) przy odłączonej pompie, DST, wstrzymaniu przez pompę; (3) odstęp liczony od **przyjęcia** bolusa przez pompę (`onAccepted`), nie od zakończenia, i liczy się także bolus, który potem się nie powiódł; (4) po STOP odpowiedź to `STOP PRESSED` (pl: „NACIŚNIĘTO STOP”, wcześniej w ogóle nie używane) zamiast „Bolus x U delivered” – ilość nie jest podawana; (5) ilość w odpowiedzi to `preview.insulin` (po cięciu executora); (6) `Refused` = sama przyczyna executora, `Failed` = `Bolus failed. Verify real state...` + przyczyna + status (wielolinijkowo); (7) cały przebieg `run()` w `NonCancellable`. Domyślny odstęp: 15 min przy jednym numerze (stała), `SmsRemoteBolusDistance` (domyślnie 15, min 3, 60 max) przy >= 2 numerach – bez zmian. Sukces do wszystkich numerów, błąd do nadawcy – bez zmian. Kolejka bolusa (`bolusInQueue`) bez zmian. |
| `PROFILE STATUS` | NIE | Odpowiedź = nazwa profilu (`getProfileName`), do nadawcy. Zmiana wewnętrzna: źródło listy to `profileRepository.profile` zamiast `activeProfileSource.profile`. |
| `PROFILE LIST` | NIE (format) | Ten sam format `N. nazwa` w liniach. Zmiana źródła listy j.w. (numeracja zależy od `getProfileList()` – przy zmianie kolejności po stronie AAPS indeksy się przesuną, tak samo jak dawniej). |
| `PROFILE <nr> [<proc>]` | **TAK (kontrole przed kodem, wykonanie)** | Prośba o kod identyczna (`To switch profile to %1$s %2$d%% reply with code %3$s`, pl też), potwierdzenie `Profile switch created` identyczne, odpowiedź tylko do nadawcy (bez zmian). Zmiany: (1) nowa kontrola zakresu procentu `Constants.CPP_PERCENTAGE_RANGE = 30.0..250.0` **przed** prośbą o kod; poza zakresem odpowiedź `„Profile-Percentage” is out of hard limits` (klucz `valueoutofrange`; pl: „Wartość „Profile-Percentage” jest poza dopuszczalną granicą”); (2) `percentage == 0` nie daje już `Wrong format`, tylko ten komunikat zakresu; (3) kolejność: `pIndex > size` -> zakres procentu -> `pIndex == 0` -> brak profilu; (4) wykonanie przez executor (`BatchAction.ProfileSwitch`, `durationMinutes=0`, `timeShift=0`), który waliduje nazwę w swoim magazynie i zakres; przy odmowie odpowiedź to **przyczyna z executora** (krótki tekst, może być dowolny), a nie `Invalid profile!`; (5) executor odmawia, gdy brak insuliny w użyciu. |

## TOTP / PIN / czas ważności

Fakty (diff `OneTimePassword.kt`, `AuthRequest.kt`):

- Format kodu bez zmian: odpowiedź to kod z aplikacji Authenticator + PIN doklejony na końcu; sprawdza `OneTimePassword.checkOTP` (ta sama klasa; różni się tylko wstrzykiwaniem zależności i lazy `configure()`).
- Tekst w prośbie: `from Authenticator app for: %1$s followed by PIN`, pl `z aplikacji Authenticator dla: %1$s, z doklejonym PIN-em` – identyczne w obu wersjach.
- Okno ważności `SMS_CONFIRM_TIMEOUT = 5 min` bez zmian; po upływie – brak odpowiedzi (jak dawniej, tylko log).
- Zły kod: `processed = true`, odpowiedź `Wrong code. Command cancelled.` (pl `Zły kod. Polecenie anulowano.`), polecenie anulowane – bez zmian.
- Dla komend pompy `AuthRequest` czeka do 3 min na pustą kolejkę, potem `Timeout while waiting for finish of previous pump communication` – bez zmian (zamiast `SystemClock.sleep` jest `delay(100)`).
- Nadpisywanie oczekującej prośby: `messageToConfirm = authRequest(...)` nadal zastępuje poprzednią (bez ostrzeżenia); odpowiedź niebędąca poleceniem, od innego numeru niż zlecający -> `messageToConfirm = null` + `Unknown command or wrong reply` – bez zmian.
- Jedyna różnica w OTP: nazwa użytkownika w URI Authenticatora pochodzi z klucza `GeneralInstanceName` (wcześniej `GeneralPatientName`) – dotyczy tylko etykiety w aplikacji uwierzytelniającej.
- `SmsAllowRemoteCommands` nadal domyślnie `false`; bez niego `BOLUS/CARBS/PROFILE` dają `Remote command is not allowed` (pl bez zmian).

## Inne różnice w SMS (poza poleceniami naszej aplikacji)

- Usunięte polecenie `AAPSCLIENT` (było w 3.4.2.6).
- Teksty zmienione (nie dotyczą naszych poleceń): `smscommunicator_current_loop_mode` („Current running mode”), pl `smscommunicator_basal_pct_reply_with_code` („Aby rozpocząć bazę ...” zamiast „bolus przedłużony”), `smscommunicator_suspend_reply_with_code` (dodany przecinek), `stop_pressed` pl.
- Nowe: pre-kontrola gate'u trybu pracy przy BASAL/EXTENDED/BOLUS (odpowiedź bez prośby o kod), kontrola stylu TBR względem pompy przed kodem.

## Zdalne sterowanie w 4.0.0 (fakty z kodu i `_docs/CLIENT_CONTROL.md`)

- SMS Communicator **nadal istnieje** (`plugins/sync`, typ `PluginType.SYNC`), wymaga uprawnień `RECEIVE_SMS`, `SEND_SMS`, `RECEIVE_MMS`; nie znaleziono ustawienia włączającego go domyślnie – wtyczka włącza się ręcznie jak dawniej (wniosek z braku jakiejkolwiek konfiguracji domyślnej w kodzie wtyczki; nie zweryfikowano zachowania aplikacji).
- Nowy mechanizm: **Client Control** (`plugins/sync/.../nsclientV3/clientcontrol/`, opis: `_docs/CLIENT_CONTROL.md`). Klient (`AAPSCLIENT`, telefon bez pompy) lub zegarek wysyła do „mastera” (telefon z pompą) intencję przez Nightscout API v3 (kolekcja `settings`, podpisane dokumenty).
- Zabezpieczenia (z dokumentu i kodu): parowanie PIN-em 8 cyfr albo kodem QR (ważność 2 min), sekret 32 B wymieniany potem przez ECDH P-256 (key exchange); podpis HMAC-SHA256 każdej koperty; licznik ściśle rosnący (ochrona przed powtórką); tolerancja czasu ±5 min i `validUntil` (8 s dla zwykłych poleceń, 60 s dla bezpośrednich poleceń pompy); osobny token Nightscout na klienta (rola `aaps-client`); role klienta: `ReadOnly`, `MealAssistant` (bolus, kreator, węglowodany), `Caregiver` (wszystko poza ustawieniami), `Admin`; master sprawdza rolę przy każdym poleceniu; przełącznik „hardened mode” (`ns_allow_client_control`); dwuetapowe prepare -> commit, master sam liczy i tnie dawkę limitami (`capFixed`) i sam układa tekst potwierdzenia; potwierdza użytkownik na **kliencie** (okno/zegarek).
- Bolus zdalny z klienta: **jest** (`BatchAction.Bolus`, `WizardPrepare`, `BolusPrepare`, `BatchPrepare`) dla ról `MealAssistant` i wyżej. Nie ma tu TOTP ani PIN-u; uwierzytelnienie to parowanie + podpis + token + rola, a potwierdzenie to kliknięcie użytkownika w aplikacji klienta.
- **Brak** odpowiednika odstępu `lastRemoteBolusTime` na kanale klienta: stała `REMOTE_BOLUS_MIN_DISTANCE` i `SmsRemoteBolusDistance` są używane tylko w `SmsCommunicatorPlugin` (grep po całym repo). Limity maksymalnego bolusa/węglowodanów stosuje master (constraints). Czy kanał klienta ma własny odstęp – nie znaleziono w kodzie, który czytałem; nie przeszukano całego `MasterActionExecutorImpl`.
- SMS jest w dokumentacji wprost opisany jako „off-relay – not on the client-control channel”; tryb hardened „nie wpływa na komendy SMS”.
- Inne kanały sterujące: zegarek (Wear) przez ten sam `BatchExecutor`, Garmin (część poleceń tylko na masterze), automatyzacje/sceny.

## Ryzyka dla naszej aplikacji

1. **BG: zmieniony format odpowiedzi** (brak rozbicia Bolus/Basal, spacja przed `U`, znak delty). Parser oparty o stary wzorzec `IOB: x U (Bolus: ...)` przestanie pasować. Parsować tolerancyjnie albo wykrywać wersję.
2. **Odmowy i błędy bez stałego tekstu**: `Refused` z executora to dowolna krótka przyczyna (bez zdania `Bolus failed...` / `Entering ...`), a `Failed` jest wielolinijkowe. Aplikacja nie może zakładać obecności stałych zdań po potwierdzeniu; nierozpoznana odpowiedź po wysłaniu kodu musi kończyć się stanem „sprawdź w AAPS” (bez ponawiania).
3. **BOLUS przy wstrzymanej przez użytkownika pętli**: w 4.0.0 bolus jest dozwolony (dawniej `Pump suspended`). Aplikacja nie powinna zakładać, że wstrzymanie pętli blokuje zdalny bolus.
4. **Odstęp dla `BOLUS x MEAL`** teraz też działa; odpowiedź `Remote bolus not available. Try again later.` może przyjść także dla wariantu z MEAL.
5. **Ilość w potwierdzeniu może się różnić od żądanej** (cięcie przez executor; dla STOP w ogóle brak ilości, tylko `STOP PRESSED`). Porównanie ilości w kodzie odsyłającym kod dotyczy prośby o kod (tam ilość jest już po `applyBolusConstraints`), ale końcowe potwierdzenie nie musi równać się żądaniu.
6. **PROFILE**: procent 30–250 sprawdzany przed kodem (nowy komunikat `valueoutofrange` z doslownym `Profile-Percentage`); `PROFILE n 0` nie daje już `Wrong format`. Walidacja po naszej stronie powinna być zgodna z 30–250, a odmowę po stronie AAPS trzeba obsłużyć. Odmowa po kodzie to dowolna przyczyna executora, nie `Invalid profile!`.
7. Brak zmian w adresatach, formacie kodu `<6 cyfr><PIN>`, 5 min, nadpisywaniu prośby – tu nic nie psuje aplikacji, ale też nie ma nowych zabezpieczeń (np. nadal brak ochrony przed nadpisaniem oczekującej prośby przez drugiego nadawcę).
8. Prośba o kod dla `PROFILE`/`BOLUS`/`CARBS` może nie przyjść w ogóle (odmowa gate'u / zakresu) – aplikacja ma timeout „czeka na prośbę o kod” (zgodnie z maszyną stanów), nic nowego, ale odmowy są teraz częstsze i mają dodatkowe teksty.

## Ocena (wnioski, nie fakty z kodu)

- Dla poleceń naszego MVP i rozszerzenia (BG, PUMP, CARBS, BOLUS, PROFILE) 4.0.0 zachowuje protokół SMS (składnia, prośba o kod, TOTP+PIN, 5 min, adresaci) – różnice dotyczą głównie formatu BG, nowych komunikatów odmów i kolejności kontroli. Zmiany są do obsłużenia w parserze/sesji, bez zmiany modelu bezpieczeństwa.
- **Czy 4.0.0 daje zdalny bolus z klienta?** Tak, według kodu i `CLIENT_CONTROL.md`: sparowany klient AAPSClient z rolą `MealAssistant` lub wyższą może zlecić bolus masterowi przez Nightscout. Zabezpieczenie: parowanie (PIN/QR), podpisy i liczniki, osobny token NS, role, potwierdzenie w aplikacji klienta; bez TOTP/PIN i – wg przeszukanych plików – bez odstępu między bolusami po stronie kanału.
- **Czy to czyni naszą aplikację zbędną?** Częściowo i warunkowo. Client Control wymaga drugiego telefonu z AAPSClient, działającego Nightscouta (NSv3 + WebSocket), zainstalowanego tego samego wydania 4.0.x na obu telefonach (kod QR odrzuca inne `v`) i sparowania; nasza aplikacja działa na samym SMS, bez Nightscouta i bez AAPSClienta, z TOTP+PIN i odstępem bolusa. Dla osoby, która ma już Nightscout i może postawić AAPSClienta, Client Control jest zamiennikiem; dla zdalnego sterowania „samym SMS” (opiekun bez konfiguracji NS) – nie. Pewność: średnia (czytałem kod i dokument projektu, nie uruchamiałem aplikacji; nie sprawdzałem, czy Client Control jest dostępny w każdym trybie/edycji aplikacji ani jego stabilności po wydaniu).

## Czego nie sprawdzono

- Zachowania na prawdziwym telefonie ani z prawdziwą pompą.
- Pełnego `MasterActionExecutorImpl` (czy gdzieś jest odstęp między bolusami z klienta) i pełnych tekstów komunikatów odmów (`CoreUiStrings`, tłumaczenia pl).
- Domyślnej aktywacji wtyczek SMS/NS w kreatorze pierwszego uruchomienia.
- Polskie teksty cytowane z pamięci w sekcji BOLUS/PROFILE (`NACIŚNIĘTO STOP`, komunikat zakresu) wynikają z odczytu plików zdekodowanych z błędami kodowania w konsoli; litera w literę zweryfikować przed wpisaniem do parsera.
