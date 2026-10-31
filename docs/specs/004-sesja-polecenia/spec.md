# Spec 004: Sesja polecenia

- **Status:** zatwierdzona
- **Prefiks wymagań:** `SESSION`
- **Pakiety:** `session`; `security` (tylko typ `AapsPin`, SESSION-02)
- **Powiązane:** [docs/plan.md](../../plan.md#przepływ-działania), [docs/plan.md](../../plan.md#parsowanie-odpowiedzi-i-obsługa-błędów), [docs/plan.md](../../plan.md#bezpieczeństwo); [Spec 001: Generator TOTP](../001-totp/spec.md) (`UsableTotpProvider`, `EpochClock`, `TotpSecret`); [Spec 002: Parser odpowiedzi SMS z AAPS](../002-parser-odpowiedzi/spec.md) (`AapsReplyParser`, `AapsReply`, sekcja Bezpieczeństwo); [Spec 003: Katalog poleceń](../003-katalog-polecen/spec.md) (`Command`, `smsText()`, `toExpectedCommand()`, `requiresCode`); [stan prac – ustalenia](../../stan-prac.md#ustalenia-z-użytkownikiem-decyzje); przyszłe specyfikacje `sms` (wysyłanie, odbiór, filtr numeru), `security` (Keystore, sekret), `ui` (ekran przebiegu, komunikaty), `data` (historia, ustawienia limitów)

## Cel

Aplikacja prowadzi jedno polecenie od wysłania SMS-a do wyniku: czeka na odpowiedź AAPS, dla poleceń z kodem (`CARBS`, `BOLUS`, zmiana profilu) sama odsyła kod `<TOTP><PIN>` dokładnie wtedy, gdy AAPS poprosił o kod dla wysłanej ilości (profilu i procentu), i kończy się jednym, jednoznacznie nazwanym wynikiem. Ta specyfikacja opisuje czystą logikę w Kotlinie (bez `android.*`): maszynę stanów jednego polecenia, limity czasu, regułę „jedno aktywne polecenie” i porty, których `session` wymaga od `sms`.

## Zakres

- Maszyna stanów jednego polecenia: wysyłanie polecenia → czekanie na odpowiedź (`BG`, `PUMP`, `PROFILE STATUS`, `PROFILE LIST`) albo na prośbę o kod (`CARBS`, `BOLUS`, zmiana profilu) → przygotowanie i wysłanie kodu → czekanie na potwierdzenie → wynik końcowy.
- Wyniki końcowe: zakończone (odpowiedź `BG`/`PUMP`/stan lub lista profili), potwierdzenie `CARBS` albo `BOLUS` przez AAPS, błąd, brak odpowiedzi (timeout), wynik nieznany („sprawdź w AAPS”), anulowane.
- Limity czasu jako parametry sesji z wartościami domyślnymi (3 min, 6 min).
- Warunki odesłania kodu: rodzaj wiadomości, stan sesji, ilość, wiek prośby o kod.
- Składanie odpowiedzi `<6 cyfr TOTP><PIN>` z `UsableTotpProvider` (Spec 001) i PIN-u AAPS trzymanego tylko w pamięci.
- Typ `AapsPin` (PIN w pamięci z czyszczeniem) – minimalny, potrzebny sesji.
- Odrzucanie wiadomości sprzed wysłania polecenia; wiadomości nieoczekiwane.
- Anulowanie przez użytkownika.
- Reguła „tylko jedno aktywne polecenie naraz” i jej egzekwowanie.
- Stan obserwowalny przez `ui` i wynik końcowy z danymi dla historii, bez PIN-u i kodu.
- Porty (interfejsy) wysyłania i odbioru SMS z kontraktem, który musi spełnić `sms`.

**Poza zakresem:**

- Implementacja portów: `SmsManager`, wybór karty SIM, `BroadcastReceiver`, filtr po numerze AAPS, składanie SMS-ów wieloczęściowych – spec `sms`.
- Odczyt i odszyfrowanie sekretu TOTP, Keystore – spec `security`. Sesja dostaje gotowy `TotpSecret`.
- Format PIN-u AAPS (długość, dozwolone znaki) i jego wpisywanie – `ui`/`security`. Sesja wymaga tylko niepustego PIN-u.
- Ekrany, teksty komunikatów i ich tłumaczenia – `ui`. Spec określa rodzaje wyników i wiążące znaczenie dwóch z nich (SESSION-12, SESSION-14).
- Zapis historii w Room, ustawienia limitów czasu w aplikacji – `data`/`ui` (później).
- Wstrzykiwanie przez Hilt – przy integracji z `ui`.
- Obsługa wiadomości, które przychodzą, gdy żadna sesja nie jest aktywna.
- Polecenia spoza katalogu ([Spec 003](../003-katalog-polecen/spec.md)). Reguły sesji dla `BOLUS` i profilu: [Spec 012](../012-bolus/spec.md) (BOL-10, BOL-11, BOL-19) i [Spec 013](../013-profil/spec.md) (PROF-11, PROF-12); ta specyfikacja opisuje wspólne fazy, limity i wyniki.

## Fakty o AAPS (źródło)

Źródło: kod AndroidAPS (repozytorium `nightscout/AndroidAPS`, pliki `SmsCommunicatorPlugin.kt`, `AuthRequest.kt`, stała `Constants.SMS_CONFIRM_TIMEOUT`), wersja użytkownika 3.4.2.6, oraz ustalenia z użytkownikiem ([stan prac](../../stan-prac.md#ustalenia-z-użytkownikiem-decyzje), [Spec 002](../002-parser-odpowiedzi/spec.md#otwarte-pytania)).

| Nr | Fakt | Skutek dla sesji |
| --- | --- | --- |
| A1 | Czas na odesłanie kodu: 5 min od utworzenia prośby o kod w AAPS. Po upływie AAPS ignoruje kod i nic nie odsyła. | Kod wysyłany tylko, gdy prośba jest dość świeża (SESSION-10). |
| A2 | Zły kod → SMS „Zly kod. Polecenie anulowano.”, prośba unieważniona. | Wynik „błąd: zły kod” (SESSION-13). |
| A3 | `CARBS` ponad limit AAPS: AAPS obcina ilość i prosi o kod z mniejszą ilością. | Parser zwraca „niezgodna ilość” → stop bez kodu (SESSION-11). Rozstrzyga otwarte pytanie ze stanu prac. |
| A4 | `CARBS` to polecenie pompy: po poprawnym kodzie AAPS czeka do 3 min na pustą kolejkę pompy, może odesłać komunikat o przekroczeniu czasu albo o niepowodzeniu. Potwierdzenie może przyjść kilka minut po kodzie. | Limit oczekiwania na potwierdzenie 6 min (SESSION-15); nieznane komunikaty po kodzie → wynik nieznany (SESSION-13). |
| A5 | Potwierdzenie `CARBS` AAPS wysyła do wszystkich dozwolonych numerów. Po `CARBS` z innego numeru tekst jest identyczny z naszym potwierdzeniem. | Potwierdzenie przyjmowane tylko w stanie „kod wysłany” i prezentowane jako potwierdzenie AAPS, nie jako dowód wykonania naszego polecenia (SESSION-12, SESSION-18). |
| A6 | AAPS trzyma jedną oczekującą prośbę o kod (wspólną dla wszystkich numerów, przypisaną do numeru nadawcy polecenia). Nadpisuje ją tylko polecenie z dowolnego dozwolonego numeru, na które AAPS tworzy nową prośbę o kod (w katalogu aplikacji: `CARBS`, `BOLUS`, `PROFILE <nr>`). Nie nadpisują jej ani nie usuwają: polecenia bez kodu (`BG`, `PUMP` bez argumentów, `PROFILE STATUS`, `PROFILE LIST`, `HELP`) ani odmowy AAPS (np. `Bledny format`, `Bolus zdalny niedostepny. Sprobuj ponownie pozniej.`, `W kolejce oczekuje inny bolus. Sprobuj ponownie pozniej.`). Usuwa ją SMS od jej nadawcy, który nie jest nazwą polecenia AAPS – AAPS traktuje go jako kod. Kod z innego numeru niż nadawca prośby AAPS ignoruje bez odpowiedzi. Źródło: z kodu AAPS 3.4.2.6 (nie z telefonu), `SmsCommunicatorPlugin.kt:246-252`, `283-363` ([raport](../013-profil/dane/aaps-kod-zrodlowy.md#d-nadpisywanie-oczekującej-prośby)). | Po anulowaniu przed kodem prośba w AAPS wygasa (A1) albo zostaje nadpisana przez kolejne polecenie z kodem; polecenie bez kodu z aplikacji jej nie usuwa. Nic nie trzeba odwoływać, bo aplikacja odsyła kod tylko w sesji po zgodnej prośbie (SESSION-08, SESSION-17). |
| A7 | Na nieznane polecenie, z numeru spoza listy i po upływie czasu na kod AAPS nic nie wysyła. | Brak odpowiedzi kończy się limitem czasu (SESSION-07, SESSION-15). |

## Pojęcia

- **Sesja** – przebieg jednego polecenia (`Command` ze Spec 003) od wysłania do wyniku końcowego.
- **Polecenie z kodem** – polecenie, którego wpis katalogu ma `requiresCode = true` (`CARBS`, `BOLUS`, zmiana profilu). Pozostałe to **polecenia bez kodu** (`BG`, `PUMP`, `PROFILE STATUS`, `PROFILE LIST`).
- **Wiadomość** – przychodzący SMS od numeru AAPS przekazany sesji przez port odbioru: treść i chwila odbioru (`receivedAtEpochMillis`).
- **Zegar** – `EpochClock` ze Spec 001; wszystkie chwile w tej specyfikacji to milisekundy epoki z tego zegara.
- **Chwila wysłania polecenia** (`commandSentAt`) – odczyt zegara w chwili, gdy port wysyłania zwrócił „wysłano” dla SMS-a z poleceniem.
- **Chwila wysłania kodu** (`codeSentAt`) – odczyt zegara w chwili, gdy port wysyłania zwrócił „wysłano” dla SMS-a z kodem.
- **Rozpoczęcie wysyłania kodu** – chwila wywołania portu wysyłania z SMS-em z kodem. Od niej kod uważa się za wysłany w sensie decyzji (anulowanie, wyniki „nieznany”), bo SMS może dotrzeć do AAPS niezależnie od dalszych zdarzeń.
- **Wynik parsera** – `AapsReplyParser.parse(treść, command.toExpectedCommand())` (Spec 002).
- **Wiadomość nieoczekiwana** – wiadomość pokazywana użytkownikowi, która nie zmienia fazy ani wyniku sesji (SESSION-18).
- **Czas wirtualny** – czas planisty `kotlinx-coroutines-test`; w kryteriach zegar jest powiązany z nim: `zegar = T0 + czas wirtualny`.
- **Termin limitu** – chwila w czasie korutyn (monotonicznym, w testach: czas wirtualny), w której upływa limit bieżącej fazy: limit odpowiedzi (SESSION-07), limit przygotowania kodu (SESSION-10) albo limit potwierdzenia (SESSION-15).
- **Pierwszeństwo limitu** – zdarzenie (wiadomość, wynik portu wysyłania, gotowy kod TOTP, anulowanie), którego obsługa przypada w czasie korutyn ≥ terminowi limitu bieżącej fazy, nie jest obsługiwane; sesja kończy się wynikiem tego limitu. Zdarzenie obsługiwane przed terminem (także o 1 ms wcześniej) jest obsługiwane normalnie. Przy równej chwili zawsze wygrywa limit, niezależnie od kolejności, w jakiej zdarzenia dotarły do sesji.

### Fazy sesji

| Faza | Znaczenie |
| --- | --- |
| `NOT_STARTED` | sesja utworzona, nic nie wysłano |
| `SENDING_COMMAND` | wywołano wysyłanie polecenia, brak wyniku portu |
| `AWAITING_REPLY` | polecenie bez kodu wysłane, czekanie na odpowiedź |
| `AWAITING_CODE_REQUEST` | polecenie z kodem wysłane, czekanie na prośbę o kod |
| `PREPARING_CODE` | prośba o kod przyjęta, czekanie na użyteczny kod TOTP; kod jeszcze nie wysłany |
| `SENDING_CODE` | wywołano wysyłanie kodu, brak wyniku portu |
| `AWAITING_CONFIRMATION` | kod wysłany, czekanie na potwierdzenie |
| `FINISHED` | wynik końcowy ustalony; sesja nic już nie wysyła ani nie przetwarza |

Fazy przed kodem: `NOT_STARTED`, `SENDING_COMMAND`, `AWAITING_REPLY`, `AWAITING_CODE_REQUEST`, `PREPARING_CODE`. Fazy po kodzie: `SENDING_CODE`, `AWAITING_CONFIRMATION`.

### Wyniki końcowe

| Wynik | Znaczenie (dla `ui`) | Pola |
| --- | --- | --- |
| `Completed` | odpowiedź na polecenie bez kodu | wynik parsera (`BgReport`, `PumpStatus`, stan profilu albo lista profili), treść |
| `CarbsConfirmed` | „AAPS potwierdził wprowadzenie X g” | ilość, treść |
| `BolusConfirmed` | „AAPS potwierdził podanie X j.” (BOL-11) | ilość w setnych jednostki, treść |
| `Failed` | błąd; kod na pewno nie został wysłany albo AAPS go odrzucił | powód, treść (jeśli dotyczy) |
| `TimedOut` | „AAPS nie odpowiada” | faza, w której upłynął limit |
| `Unknown` | „nieznany – sprawdź w AAPS”; nie błąd i nie sukces | powód, treść (jeśli dotyczy) |
| `Cancelled` | anulowane przez użytkownika przed wysłaniem kodu | – |

Powody `Failed`: `SEND_FAILED`, `UNRECOGNIZED_REPLY`, `AMOUNT_MISMATCH`, `UNEXPECTED_REPLY`, `WRONG_CODE`, `CODE_REQUEST_EXPIRED`, `CODE_PREPARATION_TIMEOUT`, `INTERNAL_ERROR`.
Powody `Unknown`: `CONFIRMATION_TIMEOUT`, `CANCELLED_AFTER_CODE`, `CODE_SEND_FAILED`, `UNRECOGNIZED_REPLY`, `AMOUNT_MISMATCH`, `UNEXPECTED_REPLY`, `INTERNAL_ERROR`.

## Wymagania

Wspólne dane kryteriów (o ile kryterium nie mówi inaczej): sekret TOTP z RFC 6238 (`12345678901234567890`), fikcyjny PIN `4321`, `T0 = 1111111051000`, zegar = `T0 + czas wirtualny`, port wysyłania zwraca „wysłano” po 0 ms, limity domyślne. Wiadomość „przychodzi w chwili v” = port odbioru emituje ją w czasie wirtualnym v z `receivedAtEpochMillis = T0 + v`. Treści wiadomości: pliki z [`../002-parser-odpowiedzi/dane/`](../002-parser-odpowiedzi/dane/README.md) (`carbs-prosba-o-kod.txt` = prośba o kod dla 1 g, `carbs-sukces.txt` = potwierdzenie 1 g, `zly-kod.txt`, `bg.txt`, `pump.txt`).

### SESSION-01: Limity czasu jako parametry sesji

Sesja ma dwa limity czasu podawane przy tworzeniu: limit odpowiedzi (domyślnie 180 000 ms = 3 min) i limit potwierdzenia (domyślnie 360 000 ms = 6 min). Każdy limit musi być dodatni; wartość ≤ 0 daje `IllegalArgumentException` przy tworzeniu parametrów. Stałe AAPS i marginesy z SESSION-10 nie są parametrami.

**Kryteria akceptacji:**

- **Zakładając** parametry utworzone bez argumentów, **gdy** odczytano limity, **wtedy** limit odpowiedzi = 180 000 ms, limit potwierdzenia = 360 000 ms.
- **Zakładając** limit odpowiedzi 30 000 ms, **gdy** utworzono parametry, **wtedy** odczytany limit odpowiedzi = 30 000 ms.
- **Zakładając** limit odpowiedzi 0 ms albo limit potwierdzenia −1 ms, **gdy** tworzone są parametry, **wtedy** rzucany jest `IllegalArgumentException`.

### SESSION-02: PIN AAPS w pamięci i dane do kodu

PIN AAPS jest przekazywany sesji jako `AapsPin`: obiekt w pamięci z metodą czyszczenia. Utworzenie `AapsPin` z tablicy znaków kopiuje znaki i zeruje tablicę wejściową (`'\u0000'`); pusta tablica daje `IllegalArgumentException`. Po wyczyszczeniu wszystkie znaki PIN-u są zerami, a próba odczytu daje `IllegalStateException`. Czyszczenie jest idempotentne.

Sesja polecenia z kodem wymaga danych do kodu: `AapsPin` i `TotpSecret`. Sesja polecenia bez kodu nie przyjmuje danych do kodu. Brak danych dla polecenia z kodem albo dane dla polecenia bez kodu dają `IllegalArgumentException` przy tworzeniu sesji; przed rzuceniem wyjątku przekazany PIN (jeśli jest) jest czyszczony.

**Kryteria akceptacji:**

- **Zakładając** tablicę `['4','3','2','1']`, **gdy** utworzono z niej `AapsPin`, **wtedy** tablica wejściowa zawiera same `'\u0000'`, a PIN ma długość 4.
- **Zakładając** pustą tablicę, **gdy** tworzony jest `AapsPin`, **wtedy** rzucany jest `IllegalArgumentException`.
- **Zakładając** `AapsPin` `4321`, **gdy** wywołano czyszczenie dwa razy, **wtedy** PIN jest oznaczony jako wyczyszczony, a odczyt znaków rzuca `IllegalStateException`.
- **Zakładając** polecenie `CARBS 20` i brak danych do kodu, **gdy** tworzona jest sesja, **wtedy** rzucany jest `IllegalArgumentException`.
- **Zakładając** polecenie `BG` i dane do kodu z PIN-em `4321`, **gdy** tworzona jest sesja, **wtedy** rzucany jest `IllegalArgumentException`, a PIN jest wyczyszczony.
- **Zakładając** polecenie `BG` albo `PUMP` bez danych do kodu, **gdy** tworzona jest sesja, **wtedy** sesja powstaje w fazie `NOT_STARTED`.

### SESSION-03: Wysłanie polecenia

Uruchomienie sesji: odczyt zegara jako chwila rozpoczęcia, faza `SENDING_COMMAND`, a następnie dokładnie jedno wywołanie portu wysyłania z treścią `command.smsText()`. Odbiór wiadomości jest aktywny (subskrypcja portu odbioru) najpóźniej w chwili wywołania portu wysyłania. Gdy port zwróci „wysłano”, sesja zapisuje chwilę wysłania polecenia i przechodzi do `AWAITING_REPLY` (polecenie bez kodu) albo `AWAITING_CODE_REQUEST` (polecenie z kodem). Rodzaj ścieżki wynika wyłącznie z `command.definition.requiresCode`. Sesję można uruchomić tylko raz; drugie uruchomienie rzuca `IllegalStateException`.

**Kryteria akceptacji:**

- **Zakładając** sesję `BG`, **gdy** ją uruchomiono, **wtedy** port wysyłania otrzymał dokładnie jeden SMS z treścią `BG`, a faza to `AWAITING_REPLY`.
- **Zakładając** sesję `PUMP`, **gdy** ją uruchomiono, **wtedy** port otrzymał dokładnie jeden SMS `PUMP`, faza `AWAITING_REPLY`.
- **Zakładając** sesję `CARBS 20`, **gdy** ją uruchomiono, **wtedy** port otrzymał dokładnie jeden SMS `CARBS 20`, faza `AWAITING_CODE_REQUEST`.
- **Zakładając** sesję `BG`, **gdy** port wysyłania jest wywoływany, **wtedy** port odbioru ma w tej chwili aktywnego subskrybenta sesji.
- **Zakładając** port wysyłania, który zwraca „wysłano” po 2000 ms czasu wirtualnego, **gdy** uruchomiono sesję `BG`, **wtedy** przez te 2000 ms faza to `SENDING_COMMAND`, a chwila wysłania polecenia = `T0 + 2000`.
- **Zakładając** sesję już uruchomioną, **gdy** uruchomiono ją ponownie, **wtedy** rzucany jest `IllegalStateException`, a port wysyłania nie dostaje drugiego SMS-a.

### SESSION-04: Błąd wysyłania polecenia

Gdy port wysyłania zwróci „błąd wysyłania” albo rzuci wyjątek inny niż `CancellationException` dla SMS-a z poleceniem, sesja natychmiast kończy się wynikiem `Failed(SEND_FAILED)` bez treści. Treść wyjątku nie jest zapisywana w wyniku ani stanie.

**Kryteria akceptacji:**

- **Zakładając** port wysyłania zwracający „błąd wysyłania”, **gdy** uruchomiono sesję `CARBS 20`, **wtedy** faza to `FINISHED`, wynik `Failed(SEND_FAILED)`, w tym samym czasie wirtualnym, w którym port zwrócił wynik, i żaden kolejny SMS nie jest wysyłany.
- **Zakładając** port wysyłania rzucający `IllegalStateException("x")`, **gdy** uruchomiono sesję `BG`, **wtedy** wynik to `Failed(SEND_FAILED)`, a `toString()` stanu i wyniku nie zawiera `x`.
- **Zakładając** błąd wysyłania i potem wiadomość z `bg.txt` w chwili 1000, **gdy** sesja `BG` się zakończyła, **wtedy** wynik pozostaje `Failed(SEND_FAILED)`.

### SESSION-05: Odbiór i filtrowanie wiadomości

Sesja przetwarza tylko wiadomości z portu odbioru (już przefiltrowane po numerze AAPS przez `sms`), w kolejności emisji. Wiadomość z `receivedAtEpochMillis` mniejszym niż chwila wysłania polecenia jest ignorowana (nie zmienia fazy ani wyniku, nie trafia do wiadomości nieoczekiwanych); równa – jest przetwarzana. Wiadomości odebrane w fazie `SENDING_COMMAND` są przetwarzane po wyniku portu według tej reguły. Każda przetwarzana wiadomość jest parsowana z kontekstem `command.toExpectedCommand()`. Zakończenie strumienia odbioru albo wyjątek w nim (inny niż `CancellationException`) nie kończy sesji – sesja czeka do upływu limitu czasu bieżącej fazy.

**Kryteria akceptacji:**

- **Zakładając** sesję `BG` z wysłaniem po 2000 ms, **gdy** podczas wysyłania (w chwili 1000) przychodzi wiadomość z `bg.txt` z `receivedAtEpochMillis = T0 + 1000`, **wtedy** jest ignorowana, a faza po wysłaniu to `AWAITING_REPLY`.
- **Zakładając** sesję `BG` w `AWAITING_REPLY` z chwilą wysłania `T0`, **gdy** przychodzi wiadomość z `bg.txt` z `receivedAtEpochMillis = T0 − 1`, **wtedy** jest ignorowana; z `receivedAtEpochMillis = T0` – sesja kończy się `Completed`.
- **Zakładając** sesję `CARBS 1` w `AWAITING_CODE_REQUEST`, **gdy** przychodzi `carbs-prosba-o-kod.txt` z `receivedAtEpochMillis = T0 − 1`, **wtedy** jest ignorowana i żaden kod nie jest wysyłany.
- **Zakładając** sesję `BG`, **gdy** strumień odbioru kończy się wyjątkiem w chwili 1000, **wtedy** faza pozostaje `AWAITING_REPLY` do chwili 180 000, a potem wynik to `TimedOut(AWAITING_REPLY)`.

### SESSION-06: Odpowiedź na BG, PUMP, PROFILE STATUS i PROFILE LIST

W fazie `AWAITING_REPLY`: wynik parsera `BgReport` (dla `BG`), `PumpStatus` (dla `PUMP`), stan profilu (dla `PROFILE STATUS`) albo lista profili (dla `PROFILE LIST`; PROF-06, PROF-07, PROF-11) kończy sesję wynikiem `Completed` z tym wynikiem parsera i treścią wiadomości. Każdy inny wynik parsera (`Unrecognized`, a także raport innego polecenia, np. lista profili w sesji `PROFILE STATUS`) kończy sesję wynikiem `Failed(UNRECOGNIZED_REPLY)` z treścią wiadomości.

**Kryteria akceptacji:**

- **Zakładając** sesję `BG` w `AWAITING_REPLY`, **gdy** przychodzi `bg.txt`, **wtedy** wynik to `Completed` z `BgReport` (glikemia 210) i treścią równą zawartości pliku.
- **Zakładając** sesję `PUMP` w `AWAITING_REPLY`, **gdy** przychodzi `pump.txt`, **wtedy** wynik to `Completed` z `PumpStatus` i treścią równą zawartości pliku.
- **Zakładając** sesję `BG` w `AWAITING_REPLY`, **gdy** przychodzi `Czesc, kupisz chleb?`, **wtedy** wynik to `Failed(UNRECOGNIZED_REPLY)` z treścią `Czesc, kupisz chleb?`.
- **Zakładając** sesję `BG` w `AWAITING_REPLY`, **gdy** przychodzi `carbs-sukces.txt` (potwierdzenie `CARBS` z innego numeru), **wtedy** wynik to `Failed(UNRECOGNIZED_REPLY)` z tą treścią.
- **Zakładając** sesję `PUMP` w `AWAITING_REPLY`, **gdy** przychodzi `bg.txt`, **wtedy** wynik to `Failed(UNRECOGNIZED_REPLY)`.
- **Zakładając** sesję `PROFILE LIST` (osobno `PROFILE STATUS`) w `AWAITING_REPLY`, **gdy** przychodzi `profile-list.txt` (`profile-status.txt`), **wtedy** wynik to `Completed` z listą profili (stanem profilu) i treścią pliku; **gdy** przychodzi plik drugiego polecenia, **wtedy** `Failed(UNRECOGNIZED_REPLY)` (PROF-11).

### SESSION-07: Limit oczekiwania na pierwszą odpowiedź

Limit odpowiedzi (SESSION-01) liczony jest w czasie korutyn od rozpoczęcia wysyłania polecenia (wejście w `SENDING_COMMAND`) i obejmuje fazy `SENDING_COMMAND`, `AWAITING_REPLY` i `AWAITING_CODE_REQUEST`. Gdy upłynie, a sesja jest w jednej z tych faz, kończy się wynikiem `TimedOut` z tą fazą. Limit przestaje obowiązywać po wejściu w `PREPARING_CODE`. Przy równej chwili obowiązuje pierwszeństwo limitu (Pojęcia). Brak automatycznego ponawiania (SESSION-20).

**Kryteria akceptacji:**

- **Zakładając** sesję `BG` bez odpowiedzi, **gdy** czas wirtualny = 179 999 ms, **wtedy** faza to `AWAITING_REPLY`; **gdy** czas = 180 000 ms, **wtedy** wynik to `TimedOut(AWAITING_REPLY)`.
- **Zakładając** sesję `BG`, **gdy** `bg.txt` przychodzi w chwili 179 999, **wtedy** wynik to `Completed`.
- **Zakładając** sesję `BG`, **gdy** `bg.txt` przychodzi w chwili 180 000, **wtedy** wynik to `TimedOut(AWAITING_REPLY)`.
- **Zakładając** sesję `CARBS 1`, **gdy** `carbs-prosba-o-kod.txt` przychodzi w chwili 180 000, **wtedy** wynik to `TimedOut(AWAITING_CODE_REQUEST)` i żaden SMS z kodem nie jest wysyłany.
- **Zakładając** port wysyłania zwracający „wysłano” po dokładnie 180 000 ms, **gdy** uruchomiono sesję `BG`, **wtedy** wynik to `TimedOut(SENDING_COMMAND)`.
- **Zakładając** sesję `CARBS 1` bez odpowiedzi, **gdy** czas = 180 000 ms, **wtedy** wynik to `TimedOut(AWAITING_CODE_REQUEST)` i żaden kod nie jest wysyłany.
- **Zakładając** limit odpowiedzi 30 000 ms i sesję `PUMP` bez odpowiedzi, **gdy** czas = 30 000 ms, **wtedy** wynik to `TimedOut(AWAITING_REPLY)`.
- **Zakładając** port wysyłania, który nigdy nie zwraca wyniku, **gdy** czas = 180 000 ms, **wtedy** wynik to `TimedOut(SENDING_COMMAND)`.
- **Zakładając** port wysyłania zwracający „wysłano” po 2000 ms i brak odpowiedzi, **gdy** czas = 180 000 ms (a nie 182 000 ms), **wtedy** wynik to `TimedOut(AWAITING_REPLY)`.
- **Zakładając** `T0 = 1111110932000`, sesję `CARBS 1`, prośbę o kod w chwili 179 000 (zegar 1111111111000) i port wysyłania kodu zwracający „wysłano” po 5000 ms, **gdy** czas = 180 000 ms, **wtedy** faza to `SENDING_CODE` (nie `TimedOut`), a w chwili 184 000 – `AWAITING_CONFIRMATION`.

### SESSION-08: Przyjęcie prośby o kod

Jedynym zdarzeniem, które prowadzi do wysłania kodu, jest wynik parsera będący prośbą o kod zgodną z poleceniem sesji (`CodeRequest` dla `CARBS`, prośba o kod bolusa dla `BOLUS`, prośba o kod profilu dla zmiany profilu; wspólny nadtyp, BOL-19) dla wiadomości przetwarzanej w fazie `AWAITING_CODE_REQUEST` (więc z `receivedAtEpochMillis` ≥ chwili wysłania polecenia, SESSION-05). Parser zwraca prośbę o kod tylko przy ilości (profilu i procencie) równej wysłanej (PARSER-06, BOL-08, PROF-09). Po przyjęciu sesja zapamiętuje chwilę odbioru prośby i przechodzi do `PREPARING_CODE`. W żadnej innej fazie prośba o kod nie prowadzi do wysłania kodu (SESSION-11, SESSION-13). Sesja wysyła kod najwyżej raz.

**Ograniczenie (brak identyfikatora prośby):** AAPS nie nadaje prośbie o kod identyfikatora, a jej treść niesie tylko ilość i godzinę (godziny parser nie porównuje z zegarem – PARSER-05). Sesja nie odróżni więc prośby dla bieżącego polecenia od starej prośby z tą samą ilością, która zostanie dostarczona ponownie albo z opóźnieniem i ma chwilę odbioru ≥ chwili wysłania polecenia (np. prośba z wcześniejszej, anulowanej sesji). Taka wiadomość w `AWAITING_CODE_REQUEST` zostanie przyjęta i sesja odeśle kod. Skutek jest ograniczony: AAPS trzyma tylko jedną oczekującą prośbę – dla najnowszego polecenia, na które utworzył prośbę o kod (A6) – i kod odnosi się do niej, a nie do prośby, której treść przyszła. Polecenie tej sesji zostało wysłane później niż stara prośba i ma tę samą ilość (inaczej parser zwróciłby „niezgodna ilość”), więc kod potwierdza bieżące, identyczne polecenie i nie powoduje ponownego wykonania starego. Gdy AAPS odmówi polecenia tej sesji przed prośbą o kod (np. odstęp lub kolejka bolusa, BOL-20), sesja kończy się bez kodu; odmowa nie nadpisuje w AAPS wcześniejszej prośby (A6), ale aplikacja jej nie potwierdza – kod na starą prośbę nie jest odsyłany, bo starą prośbę wykonałby AAPS z pominięciem kontroli (odstęp, kolejka), czyli z ominięciem limitów AAPS. Znane, świadome ograniczenie poza kontrolą aplikacji opisuje sekcja „Bezpieczeństwo”. Ryzyko ponownego dostarczenia już przekazanej wiadomości ogranicza kontrakt portu odbioru (K2, sekcja „Kontrakt portów”); wiadomości odebrane przed wysłaniem polecenia odrzuca SESSION-05.

**Kryteria akceptacji:**

- **Zakładając** sesję `CARBS 1` w `AWAITING_CODE_REQUEST`, **gdy** w chwili 60 000 przychodzi `carbs-prosba-o-kod.txt`, **wtedy** sesja przechodzi przez `PREPARING_CODE` i wysyła dokładnie jeden SMS z kodem (SESSION-09).
- **Zakładając** sesję `CARBS 20` w `AWAITING_CODE_REQUEST`, **gdy** przychodzi syntetyczna prośba `Aby wprowadzic 20g o 9:05 odeslij w SMS kod z aplikacji Authenticator dla: Jan, z doklejonym PIN-em`, **wtedy** sesja wysyła kod.
- **Zakładając** sesję `CARBS 1` w `AWAITING_CONFIRMATION`, **gdy** przychodzi drugi raz `carbs-prosba-o-kod.txt`, **wtedy** drugi SMS z kodem nie jest wysyłany (wynik wg SESSION-13).
- **Zakładając** sesję `BOLUS 0,10 j.`, która w `AWAITING_CODE_REQUEST` zakończyła się `Failed(AAPS_BOLUS_INTERVAL)` po `bolus-odmowa-odstep.txt`, **gdy** potem przychodzi `bolus-prosba-o-kod-0-10.txt`, **wtedy** wynik sesji się nie zmienia, a port wysyłania dostał tylko `BOLUS 0.1` (bez kodu).

### SESSION-09: Złożenie i wysłanie kodu

W fazie `PREPARING_CODE` sesja pobiera kod z `UsableTotpProvider.awaitUsableCode(secret)` (Spec 001, kod ma ≥ 5000 ms ważności), sprawdza wiek prośby (SESSION-10) i składa odpowiedź: 6 cyfr kodu, bezpośrednio po nich znaki PIN-u, bez spacji, separatorów ani końca linii. Zaraz po złożeniu odpowiedzi PIN jest czyszczony. Faza przechodzi do `SENDING_CODE` i wywoływany jest port wysyłania z odpowiedzią jako tablicą znaków; po powrocie z portu (dowolny wynik, wyjątek lub anulowanie) tablica jest zerowana. Gdy port zwróci „wysłano”, sesja zapisuje chwilę wysłania kodu i przechodzi do `AWAITING_CONFIRMATION`.

**Kryteria akceptacji:**

- **Zakładając** sesję `CARBS 1` (`T0 = 1111111051000`), **gdy** w chwili 60 000 (zegar 1111111111000) przychodzi `carbs-prosba-o-kod.txt`, **wtedy** w chwili 60 000 wysyłany jest SMS `0504714321`, a chwila wysłania kodu = 1111111111000.
- **Zakładając** tę sesję, **gdy** prośba przychodzi w chwili 58 000 (zegar 1111111109000, kodowi zostało 1000 ms), **wtedy** sesja czeka 1000 ms w `PREPARING_CODE` i w chwili 59 000 wysyła `0504714321`.
- **Zakładając** PIN `987654`, **gdy** prośba przychodzi w chwili 60 000, **wtedy** wysyłany SMS to `050471987654` (12 znaków).
- **Zakładając** port wysyłania kodu, który zapamiętuje przekazaną tablicę, **gdy** wywołano go, **wtedy** w chwili wywołania PIN jest już wyczyszczony, a po zakończeniu sesji zapamiętana tablica zawiera same `'\u0000'`.
- **Zakładając** wysłanie kodu, **gdy** port zwrócił „wysłano”, **wtedy** faza to `AWAITING_CONFIRMATION`.

### SESSION-10: Ważność prośby o kod i czas przygotowania kodu

AAPS przyjmuje kod przez 5 min od utworzenia prośby (A1), a sesja zna tylko chwilę odbioru prośby. Bezpośrednio przed rozpoczęciem wysyłania kodu (po otrzymaniu kodu TOTP) sesja liczy wiek prośby = zegar − chwila odbioru prośby. Kod jest wysyłany tylko, gdy 0 ≤ wiek ≤ 240 000 ms (4 min, margines 60 s na doręczenie prośby do aplikacji, doręczenie kodu do AAPS i czekanie na kod TOTP do 5000 ms). W przeciwnym razie sesja kończy się `Failed(CODE_REQUEST_EXPIRED)` z treścią prośby, bez wysyłania kodu. Faza `PREPARING_CODE` trwa najwyżej 10 000 ms czasu korutyn; po tym czasie bez rozpoczęcia wysyłania kodu sesja kończy się `Failed(CODE_PREPARATION_TIMEOUT)` bez wysyłania kodu. Kod TOTP gotowy dokładnie po 10 000 ms nie jest wysyłany (pierwszeństwo limitu, Pojęcia).

**Kryteria akceptacji** (limit odpowiedzi 600 000 ms, chwila wysłania polecenia 1111110811000, prośba `carbs-prosba-o-kod.txt` przetwarzana, gdy zegar = 1111111111000):

- **Zakładając** prośbę z `receivedAtEpochMillis = 1111110871000` (wiek 240 000 ms), **gdy** sesja ją przetworzy, **wtedy** wysyłany jest SMS `0504714321`.
- **Zakładając** prośbę z `receivedAtEpochMillis = 1111110870999` (wiek 240 001 ms), **gdy** sesja ją przetworzy, **wtedy** wynik to `Failed(CODE_REQUEST_EXPIRED)` i żaden SMS z kodem nie jest wysyłany.
- **Zakładając** prośbę z `receivedAtEpochMillis = 1111111111001` (wiek −1 ms, zegar cofnięty), **gdy** sesja ją przetworzy, **wtedy** wynik to `Failed(CODE_REQUEST_EXPIRED)` i żaden SMS z kodem nie jest wysyłany.
- **Zakładając** zegar zatrzymany na 1111111109000 (kod TOTP nigdy nie osiąga 5000 ms ważności) i prośbę z tą chwilą odbioru, **gdy** od wejścia w `PREPARING_CODE` minie 9 999 ms, **wtedy** faza to `PREPARING_CODE`; **gdy** minie 10 000 ms, **wtedy** wynik to `Failed(CODE_PREPARATION_TIMEOUT)` i żaden SMS z kodem nie jest wysyłany.

Kryteria równości limitu (`T0 = 1111111051000`, prośba `carbs-prosba-o-kod.txt` w chwili 60 000, podstawiony dostawca kodu TOTP, który po zadanym czasie zwraca kod `050471` z oknem 1111111110000–1111111140000):

- **Zakładając** dostawcę zwracającego kod po 9 999 ms, **gdy** kod jest gotowy w chwili 69 999, **wtedy** w chwili 69 999 wysyłany jest SMS `0504714321`.
- **Zakładając** dostawcę zwracającego kod po 10 000 ms, **gdy** kod jest gotowy w chwili 70 000, **wtedy** wynik to `Failed(CODE_PREPARATION_TIMEOUT)` i żaden SMS z kodem nie jest wysyłany.

### SESSION-11: Wiadomości przed wysłaniem kodu (polecenia z kodem)

Reguły dotyczą każdego polecenia z kodem (`CARBS`, `BOLUS`, zmiana profilu): prośbę o kod i niezgodność sesja rozpoznaje po wspólnych typach parsera (prośba o kod zgodna z poleceniem, niezgodność z poleceniem, potwierdzenie zgodne z poleceniem – PARSER-01), a rodzaj odpowiedzi musi odpowiadać poleceniu sesji (BOL-19); odpowiedź innego rodzaju jest traktowana jak `Unrecognized`. Dodatkowo sesja sama porównuje ilość (a dla zmiany profilu – nazwę i procent z treści prośby) z wysłaną, niezależnie od parsera. W fazach `AWAITING_CODE_REQUEST` i `PREPARING_CODE` wynik parsera decyduje tak (nazwy typów jak dla `CARBS`; dla `BOLUS` i profilu – odpowiedniki z BOL-10 i PROF-12):

| Wynik parsera | `AWAITING_CODE_REQUEST` | `PREPARING_CODE` |
| --- | --- | --- |
| prośba o kod (`CodeRequest`) | SESSION-08 | `Failed(UNEXPECTED_REPLY)` – druga prośba, kod nie jest wysyłany |
| niezgodność (`CarbsAmountMismatch`, niezgodna ilość bolusa, niezgodny profil; dowolny etap) | `Failed(AMOUNT_MISMATCH)` | `Failed(AMOUNT_MISMATCH)`, kod nie jest wysyłany |
| potwierdzenie (`CarbsDone`, wykonano bolus) | wiadomość nieoczekiwana (SESSION-18), faza bez zmian | wiadomość nieoczekiwana, przygotowanie kodu trwa dalej |
| `WrongCode` | `Failed(UNEXPECTED_REPLY)` | `Failed(UNEXPECTED_REPLY)`, kod nie jest wysyłany |
| `Unrecognized` | `Failed(UNRECOGNIZED_REPLY)` | `Failed(UNRECOGNIZED_REPLY)`, kod nie jest wysyłany |

Każdy wynik `Failed` niesie treść wiadomości. Wynik parsera niemożliwy w kontekście polecenia sesji (np. `BgReport`, `PumpStatus`, stan lub lista profili, prośba o kod innego rodzaju) jest traktowany jak `Unrecognized`. Zmiana profilu nie ma rozpoznawanego potwierdzenia (PROF-12): jedynie prośbę o kod, niezgodność, zły kod i `Unrecognized`.

**Kryteria akceptacji:**

- **Zakładając** sesję `CARBS 10` w `AWAITING_CODE_REQUEST`, **gdy** przychodzi `carbs-prosba-o-kod.txt` (1 g – np. obcięte przez limit AAPS), **wtedy** wynik to `Failed(AMOUNT_MISMATCH)` z treścią pliku i żaden SMS z kodem nie jest wysyłany.
- **Zakładając** sesję `CARBS 2` w `AWAITING_CODE_REQUEST`, **gdy** przychodzi `carbs-sukces.txt` (1 g), **wtedy** wynik to `Failed(AMOUNT_MISMATCH)`.
- **Zakładając** sesję `CARBS 1` w `AWAITING_CODE_REQUEST`, **gdy** przychodzi `carbs-sukces.txt`, **wtedy** faza pozostaje `AWAITING_CODE_REQUEST`, wiadomość jest na liście nieoczekiwanych, a gdy potem przychodzi `carbs-prosba-o-kod.txt`, sesja wysyła kod.
- **Zakładając** sesję `CARBS 1` w `AWAITING_CODE_REQUEST`, **gdy** przychodzi `zly-kod.txt`, **wtedy** wynik to `Failed(UNEXPECTED_REPLY)` z treścią pliku.
- **Zakładając** sesję `CARBS 1` w `AWAITING_CODE_REQUEST`, **gdy** przychodzi `Czesc, kupisz chleb?`, **wtedy** wynik to `Failed(UNRECOGNIZED_REPLY)` z tą treścią.
- **Zakładając** sesje `BOLUS` i zmiany profilu, **gdy** przychodzą wiadomości z tabeli, **wtedy** wyniki są takie jak w BOL-10 i PROF-12, a prośba o kod innego rodzaju niż polecenie sesji kończy sesję `Failed(UNRECOGNIZED_REPLY)` bez kodu (BOL-19).
- **Zakładając** sesję `CARBS 1`, która w chwili 58 000 przyjęła prośbę i czeka 1000 ms na kod TOTP, **gdy** w chwili 58 500 przychodzi `Czesc, kupisz chleb?`, **wtedy** wynik to `Failed(UNRECOGNIZED_REPLY)` i żaden SMS z kodem nie jest wysyłany (także po chwili 59 000).
- **Zakładając** tę sesję, **gdy** w chwili 58 500 przychodzi `carbs-sukces.txt`, **wtedy** wiadomość trafia do nieoczekiwanych, a w chwili 59 000 wysyłany jest `0504714321`.
- **Zakładając** tę sesję, **gdy** w chwili 58 500 przychodzi ponownie `carbs-prosba-o-kod.txt`, **wtedy** wynik to `Failed(UNEXPECTED_REPLY)` i żaden SMS z kodem nie jest wysyłany.

### SESSION-12: Potwierdzenie CARBS i BOLUS przez AAPS

W fazie `AWAITING_CONFIRMATION` wynik parsera `CarbsDone` (ilość równa wysłanej; dla `BOLUS` – wykonano bolus z ilością równą wysłanej) dla wiadomości z `receivedAtEpochMillis` ≥ chwili wysłania kodu kończy sesję wynikiem `CarbsConfirmed` (dla `BOLUS` – `BolusConfirmed`, BOL-11) z ilością i treścią. Ponieważ ten sam tekst przychodzi po `CARBS` (i analogicznie po `BOLUS`) z innego dozwolonego numeru (A5), wynik oznacza „AAPS potwierdził wprowadzenie X g” („AAPS potwierdził podanie X j.”), a nie „wykonano Twoje polecenie”; `ui` i `data` nie mogą go prezentować jako dowodu wykonania polecenia z tej aplikacji. Potwierdzenie z `receivedAtEpochMillis` < chwili wysłania kodu jest wiadomością nieoczekiwaną (SESSION-18).

**Kryteria akceptacji:**

- **Zakładając** sesję `CARBS 1` z kodem wysłanym w chwili 60 000, **gdy** w chwili 120 000 przychodzi `carbs-sukces.txt`, **wtedy** wynik to `CarbsConfirmed` z ilością 1 i treścią pliku.
- **Zakładając** port wysyłania kodu zwracający „wysłano” po 3000 ms (chwila wysłania kodu = zegar w chwili 63 000), **gdy** w chwili 62 000 przychodzi `carbs-sukces.txt`, **wtedy** po wyniku portu wiadomość trafia do nieoczekiwanych, a faza to `AWAITING_CONFIRMATION`.
- **Zakładając** sesję `CARBS 1` w `AWAITING_CODE_REQUEST`, **gdy** przychodzi `carbs-sukces.txt`, **wtedy** wynik nie jest `CarbsConfirmed` (SESSION-11).

### SESSION-13: Wiadomości po wysłaniu kodu

W fazie `AWAITING_CONFIRMATION` (oraz dla wiadomości odebranych w `SENDING_CODE`, przetwarzanych po wyniku „wysłano”) – dla każdego polecenia z kodem; zasady zgodności rodzaju odpowiedzi jak w SESSION-11, tabele dla `BOLUS` i zmiany profilu: BOL-11 i PROF-12:

| Wynik parsera | Wynik sesji |
| --- | --- |
| potwierdzenie (`CarbsDone`, wykonano bolus) | SESSION-12 |
| `WrongCode` | `Failed(WRONG_CODE)` z treścią (AAPS odrzucił kod; `ui` sugeruje sprawdzenie zegara telefonu) |
| `Unrecognized` (np. komunikat o przekroczeniu czasu kolejki pompy, A4) | `Unknown(UNRECOGNIZED_REPLY)` z treścią |
| niezgodność (`CarbsAmountMismatch`, niezgodna ilość bolusa, niezgodny profil; dowolny etap) | `Unknown(AMOUNT_MISMATCH)` z treścią |
| prośba o kod (`CodeRequest`, prośba o kod bolusa lub profilu) | `Unknown(UNEXPECTED_REPLY)` z treścią; kod nie jest wysyłany ponownie |

Wynik parsera niemożliwy w kontekście polecenia sesji jest traktowany jak `Unrecognized`. Dla zmiany profilu każda odpowiedź inna niż zły kod, niezgodność i prośba o kod jest `Unrecognized`, czyli `Unknown(UNRECOGNIZED_REPLY)` z treścią (PROF-12).

**Kryteria akceptacji:**

- **Zakładając** sesję `CARBS 1` w `AWAITING_CONFIRMATION`, **gdy** przychodzi `zly-kod.txt`, **wtedy** wynik to `Failed(WRONG_CODE)` z treścią pliku.
- **Zakładając** tę sesję, **gdy** przychodzi `Czesc, kupisz chleb?`, **wtedy** wynik to `Unknown(UNRECOGNIZED_REPLY)` z tą treścią.
- **Zakładając** sesję `CARBS 2` w `AWAITING_CONFIRMATION`, **gdy** przychodzi `carbs-sukces.txt` (1 g), **wtedy** wynik to `Unknown(AMOUNT_MISMATCH)`.
- **Zakładając** sesję `CARBS 1` w `AWAITING_CONFIRMATION`, **gdy** przychodzi `carbs-prosba-o-kod.txt`, **wtedy** wynik to `Unknown(UNEXPECTED_REPLY)`, a łączna liczba SMS-ów z kodem = 1.

### SESSION-14: Wynik nieznany

Wynik `Unknown` oznacza, że kod mógł dotrzeć do AAPS, ale sesja nie może stwierdzić, czy polecenie wykonano. `ui` prezentuje go jako „nieznany – sprawdź w AAPS”: ani jako błąd, ani jako sukces. Sesja kończy się wynikiem `Unknown` tylko w fazach po kodzie (lub przy ich opuszczaniu) – nigdy przed rozpoczęciem wysyłania kodu.

**Kryteria akceptacji:**

- **Zakładając** każdy scenariusz z kryteriów SESSION-03 – SESSION-11, w którym kod nie został przekazany do portu wysyłania, **gdy** sesja się zakończyła, **wtedy** wynik nie jest `Unknown`.
- **Zakładając** każdy scenariusz z kryteriów SESSION-13, SESSION-15, SESSION-16, SESSION-17 kończący się `Unknown`, **gdy** sprawdzono historię wywołań portu, **wtedy** port wysyłania otrzymał SMS z kodem przed zakończeniem sesji.

### SESSION-15: Limit oczekiwania na potwierdzenie

Limit potwierdzenia (SESSION-01) liczony jest w czasie korutyn od rozpoczęcia wysyłania kodu i obejmuje fazy `SENDING_CODE` i `AWAITING_CONFIRMATION`. Gdy upłynie, sesja kończy się `Unknown(CONFIRMATION_TIMEOUT)` bez treści. Przy równej chwili obowiązuje pierwszeństwo limitu (Pojęcia).

**Kryteria akceptacji:**

- **Zakładając** sesję `CARBS 1` z rozpoczęciem wysyłania kodu w chwili 60 000 i brak dalszych wiadomości, **gdy** czas = 419 999 ms, **wtedy** faza to `AWAITING_CONFIRMATION`; **gdy** czas = 420 000 ms, **wtedy** wynik to `Unknown(CONFIRMATION_TIMEOUT)`.
- **Zakładając** tę sesję, **gdy** `carbs-sukces.txt` przychodzi w chwili 419 999, **wtedy** wynik to `CarbsConfirmed`.
- **Zakładając** tę sesję, **gdy** `carbs-sukces.txt` przychodzi w chwili 420 000, **wtedy** wynik to `Unknown(CONFIRMATION_TIMEOUT)`, nie `CarbsConfirmed`.
- **Zakładając** port wysyłania kodu zwracający „wysłano” po dokładnie 360 000 ms (w chwili 420 000), **gdy** przyjęto prośbę o kod w chwili 60 000, **wtedy** wynik to `Unknown(CONFIRMATION_TIMEOUT)`.
- **Zakładając** limit potwierdzenia 60 000 ms, **gdy** czas = 120 000 ms, **wtedy** wynik to `Unknown(CONFIRMATION_TIMEOUT)`.
- **Zakładając** port wysyłania kodu, który nigdy nie zwraca wyniku, **gdy** czas = 420 000 ms, **wtedy** wynik to `Unknown(CONFIRMATION_TIMEOUT)`, a tablica z kodem jest wyzerowana.

### SESSION-16: Błąd wysyłania kodu

Gdy port wysyłania zwróci „błąd wysyłania” albo rzuci wyjątek inny niż `CancellationException` dla SMS-a z kodem, sesja kończy się `Unknown(CODE_SEND_FAILED)` bez treści. Wiadomości odebrane w `SENDING_CODE` nie są wtedy przetwarzane. Kod nie jest wysyłany ponownie.

**Kryteria akceptacji:**

- **Zakładając** sesję `CARBS 1` i port zwracający „błąd wysyłania” dla SMS-a z kodem, **gdy** przyjęto prośbę o kod, **wtedy** wynik to `Unknown(CODE_SEND_FAILED)`, łączna liczba SMS-ów = 2 (polecenie i jedna próba kodu), PIN wyczyszczony, tablica z kodem wyzerowana.
- **Zakładając** port rzucający `IllegalStateException("x")` dla SMS-a z kodem, **gdy** przyjęto prośbę o kod, **wtedy** wynik to `Unknown(CODE_SEND_FAILED)`, a `toString()` wyniku nie zawiera `x`.

### SESSION-17: Anulowanie przez użytkownika

Sesję można anulować w każdej fazie przed `FINISHED`; anulowanie w `FINISHED` nic nie zmienia. Skutek zależy od fazy w chwili przetworzenia anulowania:

- Fazy przed kodem (`NOT_STARTED`, `SENDING_COMMAND`, `AWAITING_REPLY`, `AWAITING_CODE_REQUEST`, `PREPARING_CODE`): wynik `Cancelled`, PIN czyszczony, żaden SMS nie jest już wysyłany (w szczególności kod), późniejsze wiadomości – także prośba o kod – nie są przetwarzane. Anulowanie w `NOT_STARTED` od razu kończy sesję (`FINISHED`, `Cancelled`, PIN wyczyszczony); pierwsze późniejsze uruchomienie zwraca ten wynik bez subskrypcji portu odbioru, bez limitu czasu i bez wywołania portu wysyłania. Uruchomienie zwraca wynik dopiero po zakończeniu anulowania: gdy wraca, PIN jest wyczyszczony, a stan `FINISHED` z wynikiem `Cancelled` jest opublikowany (także gdy uruchomienie nastąpi w trakcie kończenia sesji przez anulowanie). Przejście `NOT_STARTED` → `SENDING_COMMAND` i anulowanie w `NOT_STARTED` są atomowe względem siebie: albo anulowanie zostaje zapisane przed przejściem (wtedy zero SMS-ów), albo po nim (wtedy działa jak anulowanie w `SENDING_COMMAND`).
- Fazy po kodzie (`SENDING_CODE`, `AWAITING_CONFIRMATION`): czekanie się kończy, wynik `Unknown(CANCELLED_AFTER_CODE)`.

Anulowanie korutyny wykonującej sesję z zewnątrz (np. zamknięcie zakresu) daje ten sam wynik i stan co anulowanie przez użytkownika w tej samej fazie, a potem `CancellationException` jest przekazywany dalej.

**Kryteria akceptacji:**

- **Zakładając** sesję `CARBS 1` w `AWAITING_CODE_REQUEST`, **gdy** użytkownik anuluje w chwili 30 000, a w chwili 60 000 przychodzi `carbs-prosba-o-kod.txt`, **wtedy** wynik to `Cancelled`, PIN jest wyczyszczony, a port wysyłania otrzymał tylko SMS `CARBS 1`.
- **Zakładając** sesję `CARBS 1` czekającą w `PREPARING_CODE` (prośba w chwili 58 000), **gdy** użytkownik anuluje w chwili 58 500, **wtedy** wynik to `Cancelled` i żaden SMS z kodem nie jest wysyłany (także po chwili 59 000).
- **Zakładając** sesję `BG` w `AWAITING_REPLY`, **gdy** użytkownik anuluje, **wtedy** wynik to `Cancelled`.
- **Zakładając** sesję `CARBS 1` w `AWAITING_CONFIRMATION`, **gdy** użytkownik anuluje, **wtedy** wynik to `Unknown(CANCELLED_AFTER_CODE)`.
- **Zakładając** port wysyłania kodu, który nie zwraca wyniku, **gdy** użytkownik anuluje w `SENDING_CODE`, **wtedy** wynik to `Unknown(CANCELLED_AFTER_CODE)`, a tablica z kodem jest wyzerowana.
- **Zakładając** sesję `CARBS 1` w `NOT_STARTED`, **gdy** użytkownik anuluje, **wtedy** od razu (przed uruchomieniem) faza to `FINISHED` z wynikiem `Cancelled` i PIN jest wyczyszczony; **gdy** potem sesja jest uruchomiona, **wtedy** zwraca `Cancelled`, port wysyłania nie otrzymał żadnego SMS-a, a liczba subskrybentów portu odbioru przez cały test wynosi 0.
- **Zakładając** 1000 powtórzeń, w których `cancel()` i uruchomienie sesji `CARBS 1` startują równocześnie na `Dispatchers.Default`, **gdy** każda sesja się zakończyła, **wtedy** każdy wynik to `Cancelled`, port wysyłania otrzymał w każdym powtórzeniu 0 albo 1 SMS (tylko `CARBS 1`) i nigdy SMS-a z kodem, a w chwili powrotu z uruchomienia PIN jest wyczyszczony i stan ma fazę `FINISHED` z wynikiem `Cancelled`.
- **Zakładając** sesję `CARBS 1`, w której `cancel()` w `NOT_STARTED` zostało wstrzymane w teście po zmianie stanu cyklu życia, a przed zakończeniem sesji (np. przez zegar blokujący odczyt chwili zakończenia do sygnału testu), **gdy** w tym czasie uruchomiono sesję, **wtedy** uruchomienie nie zwraca wyniku przed zwolnieniem blokady, a po jej zwolnieniu zwraca `Cancelled` z PIN-em wyczyszczonym i opublikowaną fazą `FINISHED`.
- **Zakładając** sesję `CARBS 1` w `AWAITING_CODE_REQUEST`, **gdy** korutyna sesji zostaje anulowana z zewnątrz, **wtedy** faza to `FINISHED` z wynikiem `Cancelled`, PIN wyczyszczony, a wywołujący dostaje `CancellationException`.
- **Zakładając** sesję zakończoną wynikiem `Completed`, **gdy** użytkownik anuluje, **wtedy** wynik pozostaje `Completed`.

### SESSION-18: Wiadomości nieoczekiwane

Wiadomość nieoczekiwana (SESSION-11, SESSION-12) jest dopisywana na koniec listy wiadomości nieoczekiwanych w stanie sesji, w kolejności przetwarzania, i trafia do wyniku końcowego. Nie zmienia fazy ani wyniku, nie zatrzymuje ani nie przedłuża limitów czasu i nie wywołuje wysłania SMS-a.

**Kryteria akceptacji:**

- **Zakładając** sesję `CARBS 1` w `AWAITING_CODE_REQUEST`, **gdy** w chwilach 10 000 i 20 000 przychodzi `carbs-sukces.txt`, **wtedy** lista nieoczekiwanych ma 2 elementy z chwilami odbioru `T0 + 10 000` i `T0 + 20 000` w tej kolejności, a faza to `AWAITING_CODE_REQUEST`.
- **Zakładając** tę sesję bez dalszych wiadomości, **gdy** czas = 180 000 ms, **wtedy** wynik to `TimedOut(AWAITING_CODE_REQUEST)`, a wynik końcowy zawiera obie wiadomości nieoczekiwane.

### SESSION-19: Czyszczenie PIN-u i nieoczekiwane wyjątki

PIN jest czyszczony najpóźniej w chwili przejścia do `FINISHED`, w każdym wyniku końcowym, przy nieoczekiwanym wyjątku i przy anulowaniu z zewnątrz. Obserwator, który zobaczy fazę `FINISHED`, widzi PIN już wyczyszczony. Dodatkowo PIN jest czyszczony zaraz po złożeniu odpowiedzi z kodem (SESSION-09).

Wyjątek inny niż `CancellationException`, którego nie obsługują SESSION-04, SESSION-05 i SESSION-16 (np. z zegara albo dostawcy kodu TOTP), kończy sesję: w fazach przed kodem wynikiem `Failed(INTERNAL_ERROR)` bez wysyłania kodu, w fazach po kodzie – `Unknown(INTERNAL_ERROR)`. Uruchomienie sesji zwraca ten wynik zamiast rzucać wyjątek; komunikat wyjątku nie jest zapisywany.

Zakończenie sesji odczytuje zegar najwyżej raz (chwila zakończenia). Jeśli ten odczyt rzuci wyjątek, zegar nie jest odczytywany ponownie, wynik nie zmienia się (wyjątek zegara przy zakończeniu nie zamienia np. `CarbsConfirmed` na `INTERNAL_ERROR`), a chwila zakończenia = ostatni poprawny odczyt zegara w tej sesji. Gdy zegar nie zwrócił w sesji żadnej poprawnej wartości, chwile rozpoczęcia i zakończenia w wyniku są puste (`null`). W każdym z tych przypadków sesja przechodzi do `FINISHED`, czyści PIN, a uruchomienie zwraca wynik bez wyjątku.

**Kryteria akceptacji:**

- **Zakładając** sesję `CARBS 1`, **gdy** kończy się każdym z wyników: `CarbsConfirmed`, `Failed` (każdy powód z kryteriów SESSION-04, SESSION-10, SESSION-11, SESSION-13), `TimedOut`, `Unknown` (każdy powód), `Cancelled`, **wtedy** w chwili, gdy stan ma fazę `FINISHED`, PIN jest wyczyszczony.
- **Zakładając** sesję `CARBS 1` i dostawcę kodu TOTP, który rzuca `IllegalStateException("x")`, **gdy** przychodzi `carbs-prosba-o-kod.txt`, **wtedy** wynik to `Failed(INTERNAL_ERROR)`, PIN jest wyczyszczony, żaden SMS z kodem nie jest wysyłany, uruchomienie zwraca wynik bez wyjątku, a `toString()` wyniku nie zawiera `x`.
- **Zakładając** sesję `CARBS 1` i zegar, który do chwili 59 999 zwraca `T0 + czas wirtualny`, a od chwili 60 000 rzuca `IllegalStateException` przy każdym odczycie, **gdy** w chwili 60 000 przychodzi `carbs-prosba-o-kod.txt`, **wtedy** uruchomienie zwraca bez wyjątku wynik `Failed(INTERNAL_ERROR)`, faza to `FINISHED`, PIN jest wyczyszczony, żaden SMS z kodem nie jest wysyłany, a chwila zakończenia = `T0` (ostatni poprawny odczyt – chwila wysłania polecenia).
- **Zakładając** sesję `BG` i zegar, który rzuca `IllegalStateException` przy każdym odczycie, **gdy** uruchomiono sesję, **wtedy** uruchomienie zwraca bez wyjątku `Failed(INTERNAL_ERROR)`, faza to `FINISHED`, port wysyłania nie otrzymał żadnego SMS-a, a chwile rozpoczęcia i zakończenia są puste (`null`).
- **Zakładając** sesję `CARBS 1` z kodem wysłanym w chwili 60 000 i zegar, który od chwili 60 001 rzuca `IllegalStateException`, **gdy** w chwili 120 000 przychodzi `carbs-sukces.txt`, **wtedy** wynik to `CarbsConfirmed`, faza `FINISHED`, a chwila zakończenia = `T0 + 60 000` (ostatni poprawny odczyt – chwila wysłania kodu).

### SESSION-20: Brak ponawiania i koniec przetwarzania

Sesja wysyła najwyżej jeden SMS z poleceniem i najwyżej jeden SMS z kodem, niezależnie od wyników portu, limitów czasu i wiadomości. Po przejściu do `FINISHED` sesja kończy subskrypcję portu odbioru, nie przetwarza wiadomości i nie wywołuje portu wysyłania. Wynik końcowy się nie zmienia.

**Kryteria akceptacji:**

- **Zakładając** każdy scenariusz z kryteriów tej specyfikacji, **gdy** sesja się zakończyła, **wtedy** port wysyłania otrzymał co najwyżej 1 SMS z poleceniem i co najwyżej 1 SMS z kodem.
- **Zakładając** sesję zakończoną `TimedOut(AWAITING_CODE_REQUEST)`, **gdy** potem przychodzi `carbs-prosba-o-kod.txt`, **wtedy** żaden SMS nie jest wysyłany, a wynik pozostaje `TimedOut`.
- **Zakładając** sesję w `FINISHED`, **gdy** sprawdzono port odbioru, **wtedy** nie ma aktywnego subskrybenta sesji.

### SESSION-21: Stan i wynik sesji

Sesja udostępnia stan jako `StateFlow`: fazę, listę wiadomości nieoczekiwanych i – w `FINISHED` – wynik końcowy. Wynik końcowy niesie dane dla historii: polecenie, chwilę rozpoczęcia i chwilę zakończenia (puste tylko w przypadku z SESSION-19, gdy zegar nie zwrócił żadnej wartości), chwilę wysłania polecenia i kodu (jeśli były), rodzaj wyniku z polami z tabeli „Wyniki końcowe” i wiadomości nieoczekiwane. Wynik ani stan nie zawierają PIN-u, kodu TOTP ani odpowiedzi z kodem. Uruchomienie sesji zwraca ten sam wynik, który jest w stanie `FINISHED`.

**Kryteria akceptacji:**

- **Zakładając** sesję `BG` i port wysyłania czekający 1000 ms, **gdy** przychodzi `bg.txt` w chwili 5000, **wtedy** obserwowane fazy to kolejno `NOT_STARTED`, `SENDING_COMMAND`, `AWAITING_REPLY`, `FINISHED`.
- **Zakładając** sesję `CARBS 1` z portem wysyłania czekającym 1000 ms przy każdym SMS-ie, prośbą o kod w chwili 60 000 (zegar 1111111111000) i `carbs-sukces.txt` w chwili 120 000, **gdy** sesja się zakończyła, **wtedy** obserwowane fazy to kolejno `NOT_STARTED`, `SENDING_COMMAND`, `AWAITING_CODE_REQUEST`, `PREPARING_CODE`, `SENDING_CODE`, `AWAITING_CONFIRMATION`, `FINISHED`, a wynik ma: polecenie `CARBS 1`, rozpoczęcie `T0`, wysłanie polecenia `T0 + 1000`, wysłanie kodu `T0 + 61 000`, zakończenie `T0 + 120 000`, `CarbsConfirmed` z ilością 1.
- **Zakładając** wynik z poprzedniego kryterium, **gdy** przeszukano wszystkie pola tekstowe wyniku i stanu, **wtedy** nie zawierają `4321` ani `050471`.
- **Zakładając** sesję `BG` zakończoną `TimedOut`, **gdy** odczytano chwilę wysłania kodu, **wtedy** jest pusta (`null`).

### SESSION-22: Jedno aktywne polecenie

Koordynator sesji (jeden na aplikację) dopuszcza najwyżej jedną aktywną sesję. Sesja jest aktywna od udanego startu przez koordynatora do zwolnienia miejsca, które następuje po opublikowaniu fazy `FINISHED` i przed powrotem z uruchomienia sesji; start w tym oknie zwraca „zajęte”. Start najpierw atomowo rezerwuje miejsce na aktywną sesję, a dopiero potem tworzy sesję. Gdy miejsce jest zajęte (aktywna sesja albo trwający start), start zwraca „zajęte” bez sprawdzania danych wejściowych (także dla niepoprawnego zestawu polecenie/dane do kodu nie rzuca wyjątku), nie tworzy sesji, niczego nie wysyła i czyści przekazany PIN. Gdy utworzenie sesji po rezerwacji rzuci `IllegalArgumentException` (SESSION-02), rezerwacja jest zwalniana, PIN jest wyczyszczony, a wyjątek trafia do wywołującego. Z dowolnej liczby równoczesnych startów co najwyżej jeden się udaje. Koordynator udostępnia aktywną sesję jako `StateFlow` (pusta, gdy brak), żeby `ui` blokowało przyciski. `ui` uruchamia polecenia wyłącznie przez koordynatora.

**Kryteria akceptacji:**

- **Zakładając** brak aktywnej sesji, **gdy** wystartowano `BG`, **wtedy** wynik to „wystartowano”, a aktywna sesja jest niepusta.
- **Zakładając** aktywną sesję `CARBS 1` w `AWAITING_CODE_REQUEST`, **gdy** wystartowano `CARBS 2` z PIN-em `4321`, **wtedy** wynik to „zajęte”, port wysyłania nie otrzymał `CARBS 2`, a przekazany PIN jest wyczyszczony.
- **Zakładając** aktywną sesję `CARBS 1`, **gdy** wystartowano `BG` z danymi do kodu (PIN `4321`) albo `CARBS 2` bez danych do kodu, **wtedy** w obu przypadkach wynik to „zajęte” bez wyjątku, a przekazany PIN `4321` jest wyczyszczony.
- **Zakładając** brak aktywnej sesji, **gdy** wystartowano `BG` z danymi do kodu (PIN `4321`), **wtedy** rzucany jest `IllegalArgumentException`, PIN jest wyczyszczony, aktywna sesja jest pusta, a kolejny start `BG` bez danych do kodu zwraca „wystartowano”.
- **Zakładając** aktywną sesję, której uruchomienie zwróciło wynik, **gdy** odczytano aktywną sesję, **wtedy** jest pusta, a kolejny start `BG` się udaje.
- **Zakładając** sesję wstrzymaną po opublikowaniu `FINISHED`, a przed zwolnieniem miejsca, **gdy** wystartowano `BG`, **wtedy** wynik to „zajęte” i istnieje najwyżej jedna sesja bez `FINISHED`.
- **Zakładając** 100 równoczesnych startów `BG` na `Dispatchers.Default` przy braku aktywnej sesji, **gdy** wszystkie się zakończyły, **wtedy** dokładnie jeden zwrócił „wystartowano”.
- **Zakładając** aktywną sesję anulowaną z zewnątrz (zakres zamknięty), **gdy** odczytano aktywną sesję, **wtedy** jest pusta.
- **Zakładając** zakres koordynatora anulowany po starcie `CARBS 1` z PIN-em `4321`, zanim uruchomienie sesji się rozpoczęło, **gdy** odczytano stan sesji i aktywną sesję, **wtedy** faza to `FINISHED` z wynikiem `Cancelled`, PIN jest wyczyszczony, żaden SMS nie został wysłany, a aktywna sesja jest pusta.

### SESSION-23: Brak ujawniania danych

PIN, kod TOTP, odpowiedź z kodem, sekret, treść wiadomości AAPS i ilość węglowodanów nie trafiają do `toString()` typów z `session` i `AapsPin`, do komunikatów wyjątków ani do logów. Pakiet `session` nie wywołuje żadnego API logowania (`android.util.Log`, `println`, `System.out`/`err`, loggerów). Komunikaty wyjątków rzucanych przez `session` i `AapsPin` są stałymi tekstami.

**Kryteria akceptacji:**

- **Zakładając** `AapsPin` `4321`, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `4321`.
- **Zakładając** odpowiedź z kodem `0504714321` przekazaną do portu wysyłania, **gdy** wywołano na niej `toString()`, **wtedy** tekst nie zawiera `050471` ani `4321`.
- **Zakładając** wiadomość z treścią `bg.txt`, **gdy** wywołano `toString()` na wiadomości, stanie z nią na liście nieoczekiwanych i na wyniku `Completed`, **wtedy** żaden tekst nie zawiera `210` ani żadnej linii treści.
- **Zakładając** wynik `CarbsConfirmed` dla `CARBS 37` (syntetyczne potwierdzenie `Pomyslnie wprowadzono 37 g weglowodanow`), **gdy** wywołano `toString()` na wyniku i stanie, **wtedy** tekst nie zawiera `37`.
- **Zakładając** wyjątki z SESSION-01, SESSION-02, SESSION-03, **gdy** odczytano ich komunikaty, **wtedy** nie zawierają PIN-u, ilości ani treści wiadomości.

## Kontrakt portów (wymagania dla spec `sms`)

Sesja zakłada, że implementacje portów spełniają poniższe warunki. Nie są testowane w `session` (testy używają podstawionych portów, które je spełniają); spec `sms` musi je przejąć jako własne wymagania z testami.

| Nr | Warunek | Po co |
| --- | --- | --- |
| K1 | `receivedAtEpochMillis` jest stemplowane w chwili odbioru SMS-a przez aplikację tym samym `EpochClock`, którego używa sesja – nie znacznikiem centrum SMS ani nadawcy. | Porównanie z chwilą wysłania polecenia i kodu (SESSION-05, SESSION-12) oraz wiek prośby (SESSION-10). |
| K2 | Każda odebrana wiadomość jest dostarczana do strumienia najwyżej raz: brak ponownego dostarczania wiadomości już przekazanych (deduplikacja, np. po powtórzonym broadcaście albo ponownym uruchomieniu procesu), a ponowne dostarczenie nie może nadawać nowej chwili odbioru. | Ogranicza przyjęcie ponownie dostarczonej prośby o kod (SESSION-08, „Ograniczenie”). |
| K3 | Strumień odbioru jest gorący: nie powtarza nowemu kolektorowi wiadomości odebranych przed rozpoczęciem zbierania. | Wiadomości sprzed sesji nie trafiają do sesji. |
| K4 | Strumień zawiera wyłącznie wiadomości od skonfigurowanego numeru AAPS, z treścią sklejoną ze wszystkich części. | Filtr numeru nadawcy (AGENTS.md); parser dostaje całą treść (Spec 002). |
| K5 | Port wysyłania zawiesza się do wyniku wysłania i zwraca „wysłano” dopiero po potwierdzeniu wysłania przez system; anulowanie korutyny przerywa czekanie. | Chwile wysłania (Pojęcia), limity (SESSION-07, SESSION-15). |
| K6 | Port wysyłania nie przechowuje `CodeReply.chars` ani żadnej kopii (w tym `String` dla `SmsManager`) po powrocie z wywołania i nie loguje treści. | PIN tylko w pamięci (Bezpieczeństwo). |

## Przypadki błędów

| Sytuacja | Oczekiwane zachowanie | Wymaganie |
| --- | --- | --- |
| Limit czasu ≤ 0 | `IllegalArgumentException` | SESSION-01 |
| Brak PIN-u dla `CARBS`, PIN dla `BG`/`PUMP`, pusty PIN | `IllegalArgumentException`, PIN wyczyszczony | SESSION-02 |
| Port wysyłania: błąd lub wyjątek przy poleceniu | `Failed(SEND_FAILED)` od razu | SESSION-04 |
| Port wysyłania nie zwraca wyniku dla polecenia | `TimedOut(SENDING_COMMAND)` po limicie odpowiedzi | SESSION-07 |
| Wiadomość odebrana przed chwilą wysłania polecenia | ignorowana | SESSION-05 |
| Strumień odbioru kończy się lub rzuca wyjątek | sesja czeka do limitu czasu | SESSION-05 |
| Brak odpowiedzi AAPS („AAPS nie odpowiada”) | `TimedOut` z fazą, bez ponawiania | SESSION-07 |
| Nierozpoznana odpowiedź przed kodem (w tym potwierdzenie `CARBS` w sesji `BG`, prośba o kod innego polecenia) | `Failed(UNRECOGNIZED_REPLY)` z treścią, kod nie jest wysyłany | SESSION-06, SESSION-11 |
| Prośba o kod z inną ilością (np. obcięta przez limit AAPS) | `Failed(AMOUNT_MISMATCH)` z treścią, kod nie jest wysyłany | SESSION-11 |
| Potwierdzenie `CARBS` z tą samą ilością przed wysłaniem kodu | wiadomość nieoczekiwana, faza bez zmian | SESSION-11, SESSION-18 |
| „Zly kod” przed wysłaniem kodu | `Failed(UNEXPECTED_REPLY)` | SESSION-11 |
| Druga prośba o kod w trakcie przygotowania kodu | `Failed(UNEXPECTED_REPLY)`, kod nie jest wysyłany | SESSION-11 |
| Prośba o kod starsza niż 240 000 ms albo z chwilą odbioru w przyszłości | `Failed(CODE_REQUEST_EXPIRED)`, kod nie jest wysyłany | SESSION-10 |
| Kod TOTP niedostępny przez 10 000 ms (np. zatrzymany zegar) | `Failed(CODE_PREPARATION_TIMEOUT)` | SESSION-10 |
| Nieoczekiwany wyjątek (zegar, dostawca TOTP) | przed kodem `Failed(INTERNAL_ERROR)`, po kodzie `Unknown(INTERNAL_ERROR)`, PIN wyczyszczony | SESSION-19 |
| Zegar rzuca wyjątek przy zakończeniu sesji | zegar nie jest odczytywany ponownie; wynik bez zmian; chwila zakończenia = ostatni poprawny odczyt albo `null` | SESSION-19 |
| Zdarzenie w tej samej chwili co termin limitu | wygrywa limit (`TimedOut`, `CODE_PREPARATION_TIMEOUT`, `CONFIRMATION_TIMEOUT`) | SESSION-07, SESSION-10, SESSION-15 |
| Stara prośba o kod z tą samą ilością dostarczona po wysłaniu polecenia | przyjęta; kod potwierdza bieżące identyczne polecenie (ograniczenie); ponowne dostarczenie blokuje `sms` (K2) | SESSION-08 |
| AAPS odmawia polecenia przed prośbą o kod (np. odstęp bolusa) | sesja kończy się bez kodu (BOL-20); kod na starą oczekującą prośbę nie jest odsyłany | SESSION-08, BOL-20 |
| Port wysyłania: błąd lub wyjątek przy kodzie | `Unknown(CODE_SEND_FAILED)` | SESSION-16 |
| AAPS odrzuca kod („Zly kod”) | `Failed(WRONG_CODE)` z treścią | SESSION-13 |
| Nierozpoznana odpowiedź po kodzie (np. przekroczony czas kolejki pompy) | `Unknown(UNRECOGNIZED_REPLY)` z treścią | SESSION-13 |
| Niezgodna ilość po kodzie | `Unknown(AMOUNT_MISMATCH)` z treścią | SESSION-13 |
| Prośba o kod po wysłaniu kodu | `Unknown(UNEXPECTED_REPLY)`, kod nie jest wysyłany ponownie | SESSION-13 |
| Potwierdzenie odebrane przed chwilą wysłania kodu | wiadomość nieoczekiwana | SESSION-12 |
| Brak potwierdzenia w limicie | `Unknown(CONFIRMATION_TIMEOUT)` | SESSION-15 |
| Anulowanie przed kodem / po kodzie | `Cancelled` / `Unknown(CANCELLED_AFTER_CODE)` | SESSION-17 |
| Anulowanie przed uruchomieniem (także równoczesne z uruchomieniem) | `Cancelled`; przy anulowaniu przed uruchomieniem zero SMS-ów i brak subskrypcji | SESSION-17 |
| Start drugiego polecenia (także z niepoprawnymi danymi do kodu) | „zajęte” bez wyjątku, PIN wyczyszczony | SESSION-22 |
| Niepoprawne dane do kodu przy wolnym miejscu | `IllegalArgumentException`, rezerwacja zwolniona, PIN wyczyszczony | SESSION-22 |
| Wiadomości po zakończeniu sesji | nieprzetwarzane, nic nie jest wysyłane | SESSION-20 |

## Bezpieczeństwo

Zasady z [AGENTS.md](../../../AGENTS.md#bezpieczeństwo), które dotyczą tej funkcji:

- **Nigdy nie odsyłamy kodu, gdy odpowiedź AAPS nie zgadza się z poleceniem.** Kod powstaje wyłącznie po wyniku będącym prośbą o kod zgodną z poleceniem sesji (`CodeRequest`, prośba o kod bolusa albo profilu – dokładne dopasowanie całej treści i ilości (profilu i procentu) równej wysłanej – Spec 002, 012, 013) przetworzonym w fazie `AWAITING_CODE_REQUEST` (SESSION-08). Inny numer nadawcy odcina `sms` (kontrakt portu odbioru, SESSION-05); odpowiedź sprzed wysłania – SESSION-05; inna ilość – SESSION-11; nierozpoznana treść – SESSION-11; wiadomość niepasująca do fazy – SESSION-11, SESSION-13; stara prośba – SESSION-10; ponownie dostarczona prośba – kontrakt K2 i ograniczenie opisane w SESSION-08 (AAPS nie ma identyfikatora prośby, więc kod może potwierdzić tylko bieżące, identyczne polecenie; wyjątek: odmowa AAPS po starej prośbie – znane ograniczenie niżej). Każda wątpliwa wiadomość w czasie przygotowania kodu przerywa je bez wysłania (SESSION-11).
- **Potwierdzenie `CARBS` i `BOLUS` nie dowodzi wykonania naszego polecenia** (A5). Przyjmowane tylko po wysłaniu kodu (SESSION-12), prezentowane jako „AAPS potwierdził wprowadzenie X g” („podanie X j.”). Ryzyko szczątkowe: `CARBS` z tą samą ilością wysłane z innego dozwolonego numeru, którego potwierdzenie przyjdzie w czasie `AWAITING_CONFIRMATION`, zostanie przyjęte jako potwierdzenie dla tej sesji. Sesja nie potrafi tego odróżnić; dlatego treść wyniku mówi o potwierdzeniu AAPS, a nie o wykonaniu polecenia z aplikacji.
- **Brak automatycznego ponawiania i omijania limitów AAPS.** Najwyżej 1 SMS z poleceniem i 1 z kodem na sesję (SESSION-20); po niezgodnej ilości (A3) sesja kończy się błędem, bez korekty ilości i bez nowej próby. Wyniki „nieznany” istnieją po to, żeby użytkownik sprawdził AAPS przed ponowieniem ręcznym – zamiast fałszywego „błąd”, które zachęcałoby do powtórzenia `CARBS` (ryzyko podwójnego wprowadzenia).
- **Znane ograniczenie: stara oczekująca prośba w AAPS (poza kontrolą aplikacji).** Gdy AAPS odmówi polecenia (odstęp, kolejka), nie nadpisuje wcześniejszej prośby o kod (A6), więc ta może pozostać ważna w AAPS do 5 min od utworzenia (A1). Aplikacja nie odsyła na nią kodu (sesja kończy się bez kodu, BOL-20), ale kod wysłany ręcznie z telefonu w tym czasie potwierdziłby starą prośbę z pominięciem kontroli odstępu i kolejki. Aplikacja nie ma identyfikatora prośby, więc nie może tego wykluczyć; ryzyko świadomie akceptowane (zasada: bez omijania limitów AAPS dotyczy działań aplikacji). Przy zmianie kolejności SMS-ów (stara prośba przed odmową) kod może zostać wysłany na starą prośbę z tą samą ilością; odmowa po kodzie daje wtedy `Unknown(UNRECOGNIZED_REPLY)` z ostrzeżeniem o sprawdzeniu AAPS (BOL-20), a ponowne dostarczenie ogranicza K2.
- **PIN nigdy nie jest zapisywany.** `AapsPin` żyje tylko w pamięci, nie jest serializowany ani przekazywany poza sesję i port wysyłania (w odpowiedzi z kodem); jest czyszczony zaraz po złożeniu kodu i w każdym zakończeniu sesji (SESSION-02, SESSION-09, SESSION-19, SESSION-22). Odpowiedź z kodem jest tablicą znaków zerowaną po wysłaniu (SESSION-09). Ograniczenie: port `sms` musi utworzyć `String` dla `SmsManager`; tej kopii nie da się wyzerować – wymóg dla spec `sms`: nie przechowywać jej poza wywołaniem (K6).
- **Nie logujemy PIN-u, kodów, sekretu.** SESSION-23; wynik i stan nie zawierają PIN-u ani kodu (SESSION-21), więc mogą trafić do historii.
- **Dane w repo.** Testy używają sekretu z RFC 6238, fikcyjnych PIN-ów `4321` i `987654` oraz danych z `../002-parser-odpowiedzi/dane/`; brak numerów telefonu.

## Dane testowe

- TOTP: sekret i wektory z RFC 6238 ([Spec 001](../001-totp/spec.md#dane-testowe)). Chwile kryteriów dobrano tak, by kod był liczony w oknie wektora `1111111111` → `050471` (np. zegar 1111111109000 → czekanie 1000 ms → 1111111110000, to samo okno 30 s).
- Wiadomości AAPS: pliki z [`../002-parser-odpowiedzi/dane/`](../002-parser-odpowiedzi/dane/README.md) (prawdziwe SMS-y AAPS 3.4.2.6 z fikcyjnym imieniem `Jan`) i warianty syntetyczne opisane w kryteriach. Testy używają kopii w `app/src/test/resources/parser/aaps-3.4.2.6-pl/`.
- PIN: fikcyjne `4321`, `987654`. Brak numerów telefonu – port odbioru dostarcza wiadomości już po filtrze numeru.
- Brak nowych plików w `dane/`.

## Otwarte pytania

Brak. Decyzje użytkownika przyjęte przy tej specyfikacji:

- [x] Limit pierwszej odpowiedzi 3 min, potem „AAPS nie odpowiada” bez ponawiania (SESSION-07).
- [x] Limit potwierdzenia po kodzie 6 min, potem „nieznany – sprawdź w AAPS” (SESSION-15).
- [x] Potwierdzenie `CARBS` tylko w fazie po kodzie, jako „AAPS potwierdził wprowadzenie X g”; w innych fazach wiadomość nieoczekiwana (SESSION-12, SESSION-18).
- [x] Limity jako parametry sesji z wartościami domyślnymi; ustawienia w aplikacji później (SESSION-01).
- [x] Nierozpoznana, niezgodna ilość, zły kod, komunikat błędu → koniec sesji z treścią, bez kodu; po kodzie → „nieznany” poza złym kodem (SESSION-11, SESSION-13).
- [x] Anulowanie: przed kodem w każdej chwili; po kodzie tylko kończy czekanie z wynikiem „nieznany” (SESSION-17).
- [x] CARBS ponad limit AAPS: AAPS obcina ilość → niezgodna ilość → stop (A3, SESSION-11).
- [x] Czas na kod w AAPS: 5 min (A1, SESSION-10).

## Historia zmian

- **Planowane zmiany (Spec 013, 2026-10-30).** SESSION-11 i sekcja „Bezpieczeństwo”: dla zmiany profilu sesja porównuje w treści prośby o kod tylko procent z wysłanym, bez nazwy profilu (aplikacja zawsze wysyła profil 1 i nie zna jego nazwy; [PROF-08](../013-profil/spec.md#prof-08-prośba-o-kod-zmiany-profilu), [PROF-12](../013-profil/spec.md#prof-12-zmiana-profilu--przed-kodem-i-po-kodzie)). Pozostałe reguły (rodzaj odpowiedzi zgodny z poleceniem, nadawca, wiadomość sprzed wysłania, nierozpoznana treść – stop bez kodu) bez zmian. Treść SESSION-11 i „Bezpieczeństwa” zostanie przepisana w zadaniu 4 design 013 po wdrożeniu; do tego czasu obowiązuje Spec 013.

- **2026-10-31 – korekta A6 z kodu AAPS 3.4.2.6 (nie z telefonu).** A6: oczekującą prośbę o kod nadpisuje tylko polecenie, na które AAPS tworzy nową prośbę (`CARBS`, `BOLUS`, `PROFILE <nr>`); polecenia bez kodu (`BG`, `PUMP`, `PROFILE STATUS`, `PROFILE LIST`, `HELP`) i odmowy jej nie ruszają. SESSION-08 („Ograniczenie”): po odmowie AAPS przed kodem sesja kończy się bez kodu (bez wyjątku potwierdzającego starą prośbę); ryzyko starej oczekującej prośby opisane w „Bezpieczeństwie” jako znane ograniczenie; nowe kryterium. SESSION-17 i SESSION-22 bez zmian treści: koordynator nadal dopuszcza jedną sesję, ale powodem nie jest nadpisywanie prośby przez polecenia bez kodu. Źródło: [raport z kodu AAPS](../013-profil/dane/aaps-kod-zrodlowy.md).

- **2026-10-29 – scalenie zmian ze Spec 011 – 013 (po wdrożeniu).**
  - SESSION-06: `AWAITING_REPLY` przyjmuje też odpowiedź na `PROFILE STATUS` i `PROFILE LIST` (PROF-11).
  - SESSION-08, SESSION-11 – SESSION-13: reguły dla `CARBS` obejmują każde polecenie z kodem – prośba o kod i niezgodność rozpoznawane po wspólnych typach parsera, zgodność rodzaju odpowiedzi z poleceniem sesji (BOL-19) oraz drugie, niezależne od parsera porównanie ilości (i nazwy z procentem dla profilu) w sesji; tabele dla `BOLUS` (BOL-10, BOL-11) i zmiany profilu (PROF-12). Nowy wynik `BolusConfirmed` (jak `CarbsConfirmed`). Fazy, limity czasu i A1 – A7 bez zmian.
