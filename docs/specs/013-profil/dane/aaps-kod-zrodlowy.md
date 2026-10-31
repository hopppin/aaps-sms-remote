# AAPS SMS Communicator: BOLUS i PROFILE wg kodu źródłowego

- **Wersja:** AndroidAPS, tag `3.4.2.6` (commit `598e2eb`), płytki klon w `C:/Users/user/projects/AndroidAPS` (tylko do odczytu; nic z niego nie uruchamiano).
- **Data analizy:** 2026-10-31.
- **Pliki:** `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/smsCommunicator/SmsCommunicatorPlugin.kt` (dalej `Plugin.kt`), `.../AuthRequest.kt`, teksty `plugins/main/src/main/res/values-pl-rPL/strings.xml` (dalej `pl-main`) i `core/ui/src/main/res/values-pl-rPL/strings.xml` (dalej `pl-ui`).
- Dane fikcyjne. Teksty niżej: wersja z polskimi znakami (źródło) i wersja bez diakrytyków (tak SMS wychodzi z AAPS: `sendSMS` robi `stripAccents`, `Plugin.kt:1253`, `1288-1293`; `ł` zamieniane na `l`).
- Uwaga: polskie zasoby leżą w katalogu `values-pl-rPL` (nie `values-pl`).

## Przepływ wspólny (AuthRequest, `AuthRequest.kt`)

- `with(...)` od razu wysyła prośbę o kod **tylko do nadawcy polecenia** (`AuthRequest.kt:39`, `smsCommunicator.sendSMS(Sms(requester.phoneNumber, requestText))`).
- Kod: `Plugin.kt:355-363`. Jeśli SMS od nadawcy oczekującej prośby nie jest nazwą polecenia, całość pierwszego tokenu trafia do `action(divided[0])`. SMS od innego numeru, który nie jest komendą, jest **ignorowany bez odpowiedzi** (`isCommand`, `Plugin.kt:246-252`).
- Zły kod: `AuthRequest.kt:51-56`, odpowiedź do nadawcy: `Zły kod. Polecenie anulowano.` / `Zly kod. Polecenie anulowano.` (`sms_wrong_code`, pl-main:31). Prośba jest wtedy zużyta (`processed = true`); `messageToConfirm` wyzerowane już w `Plugin.kt:358`.
- Zły kod jest sprawdzany **przed** czasem ważności.
- Czas ważności: `Constants.SMS_CONFIRM_TIMEOUT = 5L * 60 * 1000` (`core/data/.../Constants.kt:50`), warunek `AuthRequest.kt:57`. Po 5 min poprawny kod jest ignorowany bez żadnej odpowiedzi (tylko log, `AuthRequest.kt:76`). Odliczanie od utworzenia `AuthRequest`.
- Polecenia pompy (`pumpCommand = true`; BOLUS i PROFILE tak): po poprawnym kodzie AAPS czeka do 3 min, aż kolejka pompy będzie pusta (`AuthRequest.kt:59-70`). Jeśli nadal niepusta: `Przekroczono limit czasu oczekiwania na zakończenie poprzedniej komunikacji z pompą` / `Przekroczono limit czasu oczekiwania na zakonczenie poprzedniej komunikacji z pompa` (`sms_timeout_while_waiting`, pl-main:32), do nadawcy; polecenie nie jest wykonane.
- Treść „co wpisać” (`smscommunicator_code_from_authenticator_for`, pl-main:7): `z aplikacji Authenticator dla: %1$s, z doklejonym PIN-em` → bez diakrytyków `z aplikacji Authenticator dla: Jan, z doklejonym PIN-em`; `%1$s` = nazwa konta OTP.

## a) BOLUS

Rozgałęzienie `Plugin.kt:318-324`, kolejność kontroli (każda odmowa wychodzi tylko do nadawcy, przed prośbą o kod):

1. Zdalne polecenia wyłączone → `Zdalne komendy nie są dozwolone` / `Zdalne komendy nie sa dozwolone` (pl-main:59).
2. `commandQueue.bolusInQueue()` (jest bolus lub SMB w kolejce/w toku; `CommandQueueImplementation.kt:269-277`) → `W kolejce oczekuje inny bolus. Spróbuj ponownie później.` / `W kolejce oczekuje inny bolus. Sprobuj ponownie pozniej.` (pl-main:84).
3. Minimalny odstęp: tylko dla `BOLUS <x>` (dokładnie 2 tokeny; `BOLUS x MEAL` go **omija**) i gdy `dateUtil.now() - lastRemoteBolusTime < minDistance` → `Bolus zdalny niedostępny. Spróbuj ponownie później.` / `Bolus zdalny niedostepny. Sprobuj ponownie pozniej.` (pl-main:61). `lastRemoteBolusTime` ustawiane dopiero po **udanym** bolusie (`Plugin.kt:995`), zerowe po starcie aplikacji. `minDistance` = stałe 15 min (`Constants.remoteBolusMinDistance`, `Constants.kt:17`), a gdy skonfigurowano więcej niż jeden numer – ustawienie `SmsRemoteBolusDistance` (domyślnie 15, zakres 3–60 min; `IntKey.kt:68`; `Plugin.kt:278-281`).
4. Pętla zawieszona (tylko 2 tokeny) → `Pompa wstrzymana` / `Pompa wstrzymana` (`pumpsuspended`, pl-ui:119).
5. Inna liczba tokenów niż 2–3 → `Błędny format` / `Bledny format` (pl-main:97).

`processBOLUS` (`Plugin.kt:966-1041`):

- Ilość: `SafeParse.stringToDouble(divided[1])` przechodzi przez `constraintChecker.applyBolusConstraints` (`Plugin.kt:969`). Ograniczenie z `SafetyPlugin.kt:143-147`: ujemne → 0, `> maxBolus` (ustawienie) lub twardego limitu → **obcięcie do limitu** (bez odmowy). Dlatego `BOLUS 99` daje prośbę o kod na max (potwierdza B2).
- Trzy tokeny, trzeci inny niż `MEAL` (bez rozróżnienia wielkości liter) → `Błędny format`. Ilość po ograniczeniach `<= 0` (np. `BOLUS abc`, `BOLUS 0`) → `Błędny format` (`Plugin.kt:1040`).
- Prośba o kod (do nadawcy): `smscommunicator_bolus_reply_with_code`, pl-main:35 → `Aby dostarczyć bolus %1$.2fU wprowadź kod %2$s`; po podstawieniu `%2$s`: `Aby dostarczyc bolus 0,10U wprowadz kod z aplikacji Authenticator dla: Jan, z doklejonym PIN-em` (zgodne z naszym `bolus-prosba-o-kod-0-10.txt`). Wariant `MEAL` (pl-main:36): `Aby podać do posiłku bolus %1$.2fU odeślij %2$s` → `Aby podac do posilku bolus 1,20U odesli z aplikacji Authenticator dla: Jan, z doklejonym PIN-em`. Przecinek dziesiętny wynika z lokalizacji telefonu (`%.2f`).
- Po poprawnym kodzie: `commandQueue.bolus`, potem `readStatus`, potem (sukces) `Bolus %1$.2f U podany prawidłowo` / `Bolus 1,20 U podany prawidlowo` (pl-main:46, `Plugin.kt:989-994`) + `"\n" + pumpStatusProvider.shortStatus(true)` (linie stanu pompy). Ilość w potwierdzeniu to `result.bolusDelivered` (faktycznie podana), nie zlecona. Wariant MEAL: `Pomyślnie podano %1$.2f U bolusa posiłkowego` (pl-main:47) + dodatkowa linia o TT (`Cel … przez … minut`).
- **Potwierdzenie sukcesu idzie do WSZYSTKICH dozwolonych numerów** (`sendSMSToAllNumbers`, `Plugin.kt:1023`, `1246-1250`; pętla po `allowedNumbers`).
- Błąd bolusa: `Bolus nie powiódł się. Zweryfikuj prawdziwy stan przed wysłaniem kolejnej komendy!` / `Bolus nie powiodl sie. Zweryfikuj prawdziwy stan przed wyslaniem kolejnej komendy!` (pl-main:42) + `"\n" + shortStatus`, **tylko do nadawcy** (`sendSMS`, `Plugin.kt:1026-1028`). `lastRemoteBolusTime` się wtedy nie zmienia.
- Zły kod: wspólny, patrz wyżej (`Zly kod. Polecenie anulowano.`).

## b) PROFILE STATUS / PROFILE LIST

`processPROFILE`, `Plugin.kt:661-725`. Wejście: `PROFILE` wymaga zdalnych poleceń i 2–3 tokenów (`Plugin.kt:303-306`), inaczej `Zdalne komendy nie są dozwolone` lub `Błędny format`.

- Brak aktywnego źródła profili (`store == null`): `Nie skonfigurowano` / `Nie skonfigurowano` (`notconfigured`, pl-ui:121).
- `STATUS` (bez wielkości liter): odpowiedź to samo `profileFunction.getProfileName()` (`Plugin.kt:672`), czyli nazwa dostosowana (`originalCustomizedName`) aktywnego przełączenia profilu, np. `LocalProfile1 (115%)`; gdy brak: tekst `no_profile_set`. Bez kodu, tylko do nadawcy. Zgodne z `profile-status.txt`.
- `LIST`: linie `N. nazwa` rozdzielone **`\n`** (`Plugin.kt:676-682`), numeracja od 1, bez nagłówka i bez „Wybierz profil”; bez kodu; do nadawcy. Rozstrzyga P2/P1 spec 013: separatorem jest LF.
- `LIST` przy pustej liście: `Nieprawidłowy profil!` / `Nieprawidlowy profil!` (`invalid_profile`, pl-ui:64), nie „Błędny format”.

## c) PROFILE <nr> [<procent>]

`Plugin.kt:684-723`:

- `PROFILE 1` i `PROFILE 1 100` są **równoważne**: `percentage` startuje od 100 (`Plugin.kt:686`) i jest nadpisywane tylko gdy jest trzeci token. Prośba o kod ma tę samą treść w obu przypadkach, więc `PROFILE 1 100` jest akceptowane i AAPS prosi o kod (rozwiązuje O1/W2 ze spec 013).
- Walidacja (kolejno, każda → `Błędny format` / `Bledny format`): `pIndex > list.size`; `percentage == 0`; `pIndex == 0`. Nieliczbowy token daje 0 (`SafeParse`), więc też `Bledny format`. **Brak górnego i dolnego zakresu** procentu poza zerem: `PROFILE 1 5000` i `PROFILE 1 -5` przechodzą walidację tego pliku (zakres ewentualnie ograniczany niżej, w `buildProfileSwitch`, tu nie badano). Ujemny numer profilu (`PROFILE -1`) nie jest odrzucany tą walidacją i dalej wywołuje `list[pIndex - 1]` (ryzyko wyjątku) – aplikacja nie powinna go nigdy wysyłać.
- `store.getSpecificProfile(...) == null` → `Nie załadowano profilu z NS` / `Nie zaladowano profilu z NS` (`noprofile`, pl-ui:97).
- Prośba o kod (do nadawcy): `Aby przełączyć profil na %1$s %2$d%% odpowiedz kodem %3$s` (pl-main:63) → `Aby przelaczyc profil na LocalProfile1 100% odpowiedz kodem z aplikacji Authenticator dla: Jan, z doklejonym PIN-em`. Nazwa jest z listy profili, procent to liczba całkowita z `%d` (bez przecinka, bez spacji przed `%`). Zgodne z `profile-prosba-o-kod.txt`.
- Po poprawnym kodzie: `profileFunction.createProfileSwitch(..., durationInMinutes = 0, percentage, timeShift 0, source SMS)` (`Plugin.kt:701-712`). Sukces: `Zmiana profilu wykonana` / `Zmiana profilu wykonana` (`sms_profile_switch_created`, pl-main:107), **tylko do nadawcy** (`sendSMS`, `Plugin.kt:715`; w przeciwieństwie do BOLUS nie używa `sendSMSToAllNumbers`). Porażka: `Nieprawidłowy profil!` / `Nieprawidlowy profil!` (pl-ui:64), też tylko do nadawcy. Odpowiedź przychodzi bez linii stanu pompy.
- Zły kod: wspólny `Zly kod. Polecenie anulowano.`
- **Prośba o kod dla profilu idzie tylko do nadawcy polecenia** (`AuthRequest.with`, `AuthRequest.kt:39`).
- Zmiana profilu wywołana przez INNY numer: kod SMS Communicatora nie rozsyła jej do pozostałych numerów (potwierdzenie `Zmiana profilu wykonana` tylko do nadawcy). Osobna ścieżka: `CommandSetProfile.kt:52-57` wysyła do wszystkich numerów `Profil bazowy w pompie uaktualniony` / `Profil bazowy w pompie uaktualniony` (`profile_set_ok`, pl-ui:6), ale tylko gdy przełączenie pochodzi z NS (`hasNsId`), nie jest AAPSCLIENT i nie wyłączono `doNotSendSmsOnProfileChange`. Czy zmiana z SMS wywołuje tę ścieżkę – **wywnioskowane, nie sprawdzone do końca** (zależy od `hasNsId` po stronie kolejki). W praktyce nasz numer może więc dostać `Profil bazowy w pompie uaktualniony` bez własnego polecenia; trzeba to traktować jako nieznaną wiadomość (nie jako potwierdzenie).

## d) Nadpisywanie oczekującej prośby

Stan to jedno pole `messageToConfirm` (`Plugin.kt:136`), bez identyfikatora prośby.

- **Nadpisuje** je każde polecenie, które samo tworzy nową prośbę o kod (BOLUS, CARBS, PROFILE <nr>, BASAL, TARGET, LOOP, ...): `messageToConfirm = authRequestProvider.get().with(...)`. Stara prośba przestaje istnieć. Uwaga: `AuthRequest.confirmCode` nie jest porównywany z odebranym kodem; liczy się tylko ważność TOTP+PIN (`AuthRequest.kt:43-44`), więc kod wykona zawsze **najnowszą** oczekującą akcję danego numeru.
- **Nie nadpisują** i nie zerują jej polecenia bez kodu: `BG`, `PROFILE STATUS`, `PROFILE LIST`, `PUMP` (bez argumentów), `HELP`, `AAPSCLIENT`, oraz odmowy typu `Błędny format` / `Bolus zdalny niedostępny` / `W kolejce oczekuje inny bolus` (te gałęzie tylko wysyłają SMS, `Plugin.kt:285-353`). Czyli założenie A6 „każde nowe polecenie nadpisuje” jest **zbyt szerokie**: `PROFILE STATUS` pozostawia oczekującą prośbę nienaruszoną. Konsekwencja bezpieczna w naszej aplikacji (jedna sesja naraz), ale A6/R6 warto doprecyzować.
- Wyjątek zerujący: SMS od nadawcy prośby, którego pierwszy token nie jest komendą, jest traktowany jako kod (`Plugin.kt:356-359`); wtedy `messageToConfirm = null` niezależnie od wyniku. Gałąź `else { messageToConfirm = null; "Nieznane polecenie lub błędna odpowiedź" }` (`Plugin.kt:360-363`) jest w praktyce nieosiągalna (patrz `isCommand`: dla obcego numeru nie-komendy wejście `when` jest w ogóle pomijane).
- Prośba jest związana z numerem nadawcy (`requester.phoneNumber`): kod z innego numeru jest ignorowany bez odpowiedzi.

## e) Czas ważności

5 minut (`SMS_CONFIRM_TIMEOUT`, `Constants.kt:50`), liczone od utworzenia prośby w AAPS. Po upływie poprawny kod jest po cichu odrzucany (brak SMS-a), zły kod nadal daje `Zly kod...`. Dodatkowo oczekiwanie do 3 min na kolejkę pompy zaczyna się dopiero po poprawnym kodzie. Zgodne z naszym założeniem 5 min.

## Tabela skrócona (źródło: kod, chyba że zaznaczono)

| Pytanie | Odpowiedź | Pewność |
| --- | --- | --- |
| a) minimalny odstęp | `BOLUS x` (bez MEAL) odrzucone `Bolus zdalny niedostepny. Sprobuj ponownie pozniej.`, gdy od ostatniego UDANEGO zdalnego bolusa < 15 min (ustawialne 3–60 przy >1 numerze) | kod |
| a) ponad max bolus | brak odmowy; ilość obcięta do limitu, prośba o kod na limit | kod |
| a) kolejka | `W kolejce oczekuje inny bolus. Sprobuj ponownie pozniej.` przed prośbą o kod; po kodzie czekanie do 3 min, potem `Przekroczono limit czasu oczekiwania na zakonczenie poprzedniej komunikacji z pompa` | kod |
| a) sukces / błąd | `Bolus 1,20 U podany prawidlowo` + linie stanu (do wszystkich numerów); błąd `Bolus nie powiodl sie. Zweryfikuj prawdziwy stan przed wyslaniem kolejnej komendy!` + stan (tylko nadawca) | kod |
| b) STATUS / LIST | STATUS = `getProfileName()` (np. `LocalProfile1 (115%)`); LIST = `N. nazwa` rozdzielone LF; pusta lista = `Nieprawidlowy profil!` | kod |
| c) `PROFILE 1 100` | równoważne `PROFILE 1`, prośba o kod identyczna | kod |
| c) błędy | numer > liczba profili, 0, nieliczbowy; procent 0 → `Bledny format`; górnego zakresu procentu brak | kod |
| c) sukces / adresaci | `Zmiana profilu wykonana` tylko do nadawcy; prośba o kod tylko do nadawcy | kod |
| c) inny numer | brak rozesłania przez SMS Communicator; możliwa osobna wiadomość `Profil bazowy w pompie uaktualniony` do wszystkich (tylko gdy zmiana z NS) | kod / wywnioskowane |
| d) nadpisanie | nadpisują polecenia tworzące prośbę; `PROFILE STATUS`/`BG` nie | kod |
| e) ważność | 5 min | kod |
