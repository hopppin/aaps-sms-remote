# Spec 005: SMS (wysyłanie i odbiór)

- **Status:** szkic
- **Prefiks wymagań:** `SMS`
- **Pakiety:** `sms`; `otp` (tylko wiązanie Hilt dla `EpochClock`, jeśli jeszcze nie istnieje – design, zadanie 6)
- **Powiązane:** [docs/plan.md](../../plan.md#wybór-technologii), [docs/plan.md](../../plan.md#parsowanie-odpowiedzi-i-obsługa-błędów); [Spec 004: Sesja polecenia](../004-sesja-polecenia/spec.md) (porty `SmsSender`, `IncomingSmsSource`, sekcja [Kontrakt portów](../004-sesja-polecenia/spec.md#kontrakt-portów-wymagania-dla-spec-sms) K1–K6); [Spec 001: Generator TOTP](../001-totp/spec.md) (`EpochClock`); [stan prac – ustalenia](../../stan-prac.md#ustalenia-z-użytkownikiem-decyzje); przyszłe specyfikacje `data` (przechowywanie numeru AAPS), `ui` (prośba o uprawnienia, komunikaty)

## Cel

Sesja polecenia (Spec 004) wysyła i odbiera SMS-y wyłącznie przez dwa porty: `SmsSender` i `IncomingSmsSource`. Ta specyfikacja opisuje ich implementację na Androidzie: wysyłanie przez `SmsManager` z domyślnej karty SMS, odbiór przez dynamicznie rejestrowany `BroadcastReceiver`, filtr numeru AAPS, sklejanie części, deduplikację i sprawdzanie uprawnień – tak, by spełnić cały kontrakt K1–K6 ze Spec 004.

## Zakres

- Implementacja portu `SmsSender` (wysyłanie polecenia i odpowiedzi z kodem) na bazie `SmsManager` z domyślnej karty SMS systemu, z wynikiem z `PendingIntent` wysłania.
- Implementacja portu `IncomingSmsSource` na bazie `BroadcastReceiver` na `SMS_RECEIVED`, rejestrowanego dynamicznie tylko na czas zbierania strumienia.
- Numer AAPS: walidacja numeru z ustawień, adres docelowy wysyłania i porównanie z nadawcą (ostatnie 9 cyfr).
- Sklejanie części wiadomości z jednego broadcastu, deduplikacja, znacznik chwili odbioru.
- Sprawdzanie uprawnień `SEND_SMS` i `RECEIVE_SMS` (interfejs dla `ui`) i ich wpis w manifeście.
- Minimalny port dostawcy numeru AAPS (`AapsNumberProvider`); jego implementacja należy do spec `data`.
- Wiązania Hilt portów z implementacjami.
- Podział na logikę czystą (testowalną bez Androida) i cienką warstwę Androida.

**Poza zakresem:**

- **Wybór karty SIM.** Wysyłanie zawsze z domyślnej karty SMS systemu, bez ustawienia w aplikacji; odbiór z dowolnej karty. To świadome odstępstwo od [docs/plan.md](../../plan.md#wybór-technologii) (wiersz „Wysyłanie SMS: `SmsManager` z wyborem karty SIM przez `SubscriptionManager`”, ekran konfiguracji „wybór karty SIM”, `data` „karta SIM”) – decyzja użytkownika dla MVP. Plan zostanie poprawiony osobno (propozycja w opisie PR); wybór karty może wrócić jako osobna specyfikacja.
- Usługa pierwszoplanowa, receiver w manifeście i odbiór przy zabitym procesie. Zabity proces = sesja przepada (otwarte pytanie P2 dotyczy procesu w stanie „cached”).
- Ekrany, prośba o uprawnienia, komunikaty i ich teksty – spec `ui`.
- Przechowywanie i edycja numeru AAPS – spec `data`.
- Rozszerzenie `SendResult.Failed` o przyczynę błędu (np. brak zasięgu) – ewentualna zmiana Spec 004 (sekcja „Przyszłe zmiany”). Interfejsy z `session/SmsPorts.kt` nie są zmieniane.
- `READ_SMS`, bycie domyślną aplikacją SMS, odczyt skrzynki SMS, raporty doręczenia (`deliveryIntent`).
- Obsługa wiadomości, gdy żadna sesja nie zbiera strumienia (wtedy receiver nie jest zarejestrowany).
- SMS-y binarne (`DATA_SMS_RECEIVED`), MMS, RCS.

## Pojęcia

- **Numer z ustawień** – tekst numeru AAPS zwrócony przez `AapsNumberProvider` (może być `null` – brak konfiguracji).
- **Cyfry** – wyłącznie znaki ASCII `0`–`9`.
- **Końcówka** – ostatnie 9 cyfr numeru.
- **Poprawny numer AAPS** – numer z ustawień spełniający SMS-01; ma adres docelowy i końcówkę.
- **Broadcast** – jedno wywołanie `onReceive` receivera `SMS_RECEIVED` z intentem, z którego `Telephony.Sms.Intents.getMessagesFromIntent` zwraca listę części (`SmsMessage`) w kolejności z intentu.
- **Część** – jeden `SmsMessage`: nadawca (`originatingAddress`), treść (`messageBody`), znacznik centrum SMS (`timestampMillis`).
- **Wiadomość złożona** – treść sklejona z części jednego nadawcy z jednego broadcastu (SMS-03).
- **Kolektor** – aktywne zbieranie `IncomingSmsSource.messages()` (w aplikacji: sesja polecenia).
- **Zegar** – `EpochClock` ze Spec 001, ten sam obiekt, którego używa sesja.
- **Warstwa systemowa** – cienka warstwa Androida nad `SmsManager` i receiverem wyniku wysłania (`SmsSystemApi` w design); w kryteriach logiki czystej zastępowana atrapą.
- **Wynik części** – kod wyniku z `PendingIntent` wysłania dla jednej części SMS-a; `RESULT_OK` = `android.app.Activity.RESULT_OK`.

## Wymagania

Wspólne dane kryteriów (o ile kryterium nie mówi inaczej): numer z ustawień `+44 7700 900123` (fikcyjny, zakres Ofcom „drama numbers”), uprawnienia `SEND_SMS` i `RECEIVE_SMS` przyznane, zegar zwraca `T0 = 1111111051000`, znacznik centrum SMS części `1111111000000`. Kryteria dla logiki czystej używają atrap warstwy Androida (rejestrator receivera, warstwa systemowa wysyłania, uprawnienia – design).

### SMS-01: Numer AAPS z ustawień

Numer z ustawień jest poprawny, gdy po usunięciu znaków białych (`Char.isWhitespace`), `-`, `(` i `)` składa się z opcjonalnego `+` na początku i samych cyfr, a cyfr jest ≥ 9. Adres docelowy wysyłania = numer po tym usunięciu (z `+`, jeśli był). Końcówka = ostatnie 9 cyfr adresu docelowego. Numer `null`, pusty, z innym znakiem (litera, `.`, `/`, `+` nie na początku, cyfra spoza ASCII) albo z < 9 cyframi jest nieprawidłową konfiguracją: nie ma adresu docelowego ani końcówki.

**Kryteria akceptacji:**

- **Zakładając** numer `+44 7700 900123`, **gdy** jest sprawdzany, **wtedy** jest poprawny, adres docelowy = `+447700900123`, końcówka = `700900123`.
- **Zakładając** numer `+44-7700-900-123`, **gdy** jest sprawdzany, **wtedy** adres docelowy = `+447700900123`.
- **Zakładając** numer `+44 (0)7700 900123`, **gdy** jest sprawdzany, **wtedy** adres docelowy = `+4407700900123`, końcówka = `700900123`.
- **Zakładając** numer `07700900123`, **gdy** jest sprawdzany, **wtedy** adres docelowy = `07700900123`, końcówka = `700900123`.
- **Zakładając** numer `700900123` (dokładnie 9 cyfr), **gdy** jest sprawdzany, **wtedy** jest poprawny.
- **Zakładając** numer `70090012` (8 cyfr), `null`, `""`, `"   "`, `+44 7700 9OO123` (litera `O`), `+44.7700.900123`, `44+7700900123`, **gdy** jest sprawdzany, **wtedy** w każdym przypadku jest nieprawidłową konfiguracją.

### SMS-02: Porównanie nadawcy z numerem AAPS (K4)

Nadawca pasuje do poprawnego numeru AAPS wtedy i tylko wtedy, gdy nie jest `null`, nie zawiera żadnej litery (`Char.isLetter`), po usunięciu wszystkich znaków poza cyframi ma ≥ 9 cyfr, a jego ostatnie 9 cyfr = końcówka numeru AAPS. Nadawca alfanumeryczny nigdy nie pasuje. Przy nieprawidłowej konfiguracji (SMS-01) żaden nadawca nie pasuje.

**Kryteria akceptacji:**

- **Zakładając** numer AAPS `+44 7700 900123`, **gdy** nadawca to `+447700900123`, `07700900123`, `00447700900123`, `700900123` albo `+44 7700 900123`, **wtedy** w każdym przypadku pasuje.
- **Zakładając** ten numer, **gdy** nadawca to `+447700900124`, `+447700900321` albo `70090012`, **wtedy** nie pasuje.
- **Zakładając** ten numer, **gdy** nadawca to `AAPS`, `Info700900123`, `""` albo `null`, **wtedy** nie pasuje.
- **Zakładając** ten numer, **gdy** nadawca to `+17700900123` (inny numer kierunkowy, ta sama końcówka), **wtedy** pasuje (znane ograniczenie porównania po 9 cyfrach – sekcja Bezpieczeństwo).
- **Zakładając** numer z ustawień `70090012` (nieprawidłowy), **gdy** nadawca to `70090012`, **wtedy** nie pasuje.

### SMS-03: Sklejanie części (K4)

Części jednego broadcastu są grupowane po nadawcy (równość tekstu `originatingAddress`, `null` to osobna grupa). Każda grupa daje jedną wiadomość złożoną: treść = treści części tej grupy sklejone bez separatorów w kolejności z intentu (treść `null` = pusty tekst), znacznik centrum SMS = znacznik pierwszej części grupy. Wiadomości złożone są uporządkowane według pierwszego wystąpienia nadawcy w intencie. Części z różnych broadcastów nie są łączone (założenie: system dostarcza wszystkie części jednej wiadomości w jednym broadcaście – do weryfikacji W6).

**Kryteria akceptacji:**

- **Zakładając** broadcast z dwiema częściami od `+447700900123`: `Aby wprowadzic 20g o 9:05 odeslij w SMS kod ` i `z aplikacji Authenticator dla: Jan, z doklejonym PIN-em`, **gdy** jest składany, **wtedy** powstaje jedna wiadomość z treścią `Aby wprowadzic 20g o 9:05 odeslij w SMS kod z aplikacji Authenticator dla: Jan, z doklejonym PIN-em`.
- **Zakładając** broadcast z częściami w kolejności: A1 od `+447700900123`, B1 od `+447700900999`, A2 od `+447700900123`, **gdy** jest składany, **wtedy** powstają dwie wiadomości w kolejności: A1+A2 (od `+447700900123`), B1 (od `+447700900999`).
- **Zakładając** broadcast z jedną częścią z treścią `null`, **gdy** jest składany, **wtedy** powstaje jedna wiadomość z pustą treścią.
- **Zakładając** części tej samej wiadomości od `+447700900123` w dwóch kolejnych broadcastach, **gdy** są przetwarzane, **wtedy** powstają dwie osobne wiadomości.
- **Zakładając** broadcast z dwiema częściami ze znacznikami `1111111000000` i `1111111001000`, **gdy** jest składany, **wtedy** znacznik wiadomości złożonej = `1111111000000`.

### SMS-04: Filtr numeru AAPS w strumieniu (K4)

Strumień `IncomingSmsSource.messages()` zawiera wyłącznie wiadomości złożone (SMS-03), których nadawca pasuje do numeru AAPS (SMS-02), z całą sklejoną treścią jako `IncomingSms.body`. Numer z ustawień jest odczytywany z `AapsNumberProvider` raz na broadcast; przy nieprawidłowej konfiguracji broadcast nie dostarcza nic.

**Kryteria akceptacji:**

- **Zakładając** aktywnego kolektora, **gdy** przychodzi broadcast z dwiema częściami od `07700900123` (kryterium 1 z SMS-03), **wtedy** kolektor dostaje dokładnie jedną wiadomość z całą sklejoną treścią.
- **Zakładając** aktywnego kolektora, **gdy** przychodzi broadcast z `bg.txt` od `+447700900999`, **wtedy** kolektor nie dostaje nic.
- **Zakładając** aktywnego kolektora, **gdy** przychodzi broadcast z częściami od `+447700900999` (treść `X`) i od `+447700900123` (treść `bg.txt`), **wtedy** kolektor dostaje dokładnie jedną wiadomość z treścią `bg.txt`.
- **Zakładając** numer z ustawień `null` albo `70090012`, **gdy** przychodzi broadcast z `bg.txt` od `70090012`, **wtedy** kolektor nie dostaje nic.
- **Zakładając** numer z ustawień zmieniony z `+44 7700 900123` na `+44 7700 900999` między dwoma broadcastami od `+447700900123`, **gdy** przychodzą oba, **wtedy** kolektor dostaje tylko pierwszy.

### SMS-05: Chwila odbioru z zegara sesji (K1)

`IncomingSms.receivedAtEpochMillis` = odczyt zegara (ten sam `EpochClock` co sesja) wykonany raz na broadcast, na początku jego obsługi w `onReceive`, przed składaniem i filtrem. Wszystkie wiadomości z jednego broadcastu mają tę samą chwilę odbioru. Znacznik centrum SMS (`SmsMessage.timestampMillis`) nie jest używany jako chwila odbioru. Gdy odczyt zegara rzuci wyjątek, broadcast nie dostarcza nic, a wyjątek nie wychodzi z obsługi broadcastu.

**Kryteria akceptacji:**

- **Zakładając** zegar zwracający `T0` i część ze znacznikiem centrum SMS `1111111000000`, **gdy** przychodzi broadcast z `bg.txt` od numeru AAPS, **wtedy** `receivedAtEpochMillis` = `T0`.
- **Zakładając** zegar zwracający kolejno `T0`, `T0 + 5000`, **gdy** przychodzą dwa broadcasty od numeru AAPS, **wtedy** chwile odbioru to kolejno `T0` i `T0 + 5000`, a zegar odczytano dokładnie 2 razy.
- **Zakładając** broadcast z dwiema wiadomościami złożonymi pasującymi do numeru AAPS (np. nadawcy `+447700900123` i `07700900123`), **gdy** jest obsługiwany, **wtedy** obie mają tę samą chwilę odbioru, a zegar odczytano raz.
- **Zakładając** zegar rzucający `IllegalStateException`, **gdy** przychodzi broadcast z `bg.txt` od numeru AAPS, **wtedy** kolektor nie dostaje nic, obsługa broadcastu kończy się bez wyjątku, a następny broadcast (zegar już działa) jest dostarczany.

### SMS-06: Dostarczenie najwyżej raz (K2)

Każda wiadomość złożona od numeru AAPS jest dostarczana do strumienia najwyżej raz w czasie życia procesu. Wiadomość o tym samym nadawcy (tekst `originatingAddress`), tej samej treści i tym samym znaczniku centrum SMS co jedna ze 100 ostatnio zapamiętanych jest duplikatem: nie jest dostarczana i nie dostaje nowej chwili odbioru. Pamiętane są wiadomości od numeru AAPS po filtrze (SMS-04), także gdy w chwili obsługi nie było kolektora; po przekroczeniu 100 wpisów usuwany jest najstarszy. Pamięć deduplikacji jest wspólna dla wszystkich kolektorów i kolejnych sesji. Po ponownym uruchomieniu procesu pamięć jest pusta, a receiver nie istnieje do pierwszego kolektora; system nie powtarza nowemu receiverowi broadcastów sprzed jego rejestracji (`SMS_RECEIVED` nie jest broadcastem trwałym), więc wiadomości dostarczone przed restartem nie wracają.

**Kryteria akceptacji:**

- **Zakładając** aktywnego kolektora, **gdy** ten sam broadcast z `carbs-prosba-o-kod.txt` od numeru AAPS przychodzi dwa razy (zegar `T0`, potem `T0 + 1000`), **wtedy** kolektor dostaje dokładnie jedną wiadomość z chwilą odbioru `T0`.
- **Zakładając** dwa broadcasty z tą samą treścią od numeru AAPS ze znacznikami centrum SMS `1111111000000` i `1111111060000`, **gdy** przychodzą, **wtedy** kolektor dostaje obie.
- **Zakładając** wiadomość dostarczoną w sesji 1 (kolektor zakończony), **gdy** w sesji 2 (nowy kolektor) przychodzi ten sam broadcast, **wtedy** kolektor sesji 2 nie dostaje nic.
- **Zakładając** 100 różnych wiadomości od numeru AAPS dostarczonych po wiadomości W, **gdy** W przychodzi ponownie, **wtedy** jest dostarczana (101. wpis usunął W); **zakładając** 99 różnych wiadomości po W, **gdy** W przychodzi ponownie, **wtedy** nie jest dostarczana.
- **Zakładając** wiadomość od `+447700900999` (nie AAPS), **gdy** przychodzi 150 razy z różną treścią, a potem przychodzi wiadomość W od numeru AAPS dostarczona wcześniej, **wtedy** W nie jest dostarczana (wiadomości spoza numeru AAPS nie zajmują pamięci).

### SMS-07: Gorący strumień i czas rejestracji receivera (K3)

`messages()` zwraca strumień gorący: kolektor dostaje tylko wiadomości z broadcastów obsłużonych po rozpoczęciu jego zbierania, w kolejności obsługi. Rozpoczęcie zbierania rejestruje kolektora synchronicznie, przed pierwszym zawieszeniem: kolektor uruchomiony z `CoroutineStart.UNDISPATCHED` jest zarejestrowany, zanim `launch` zwróci. Receiver `SMS_RECEIVED` jest zarejestrowany w systemie dokładnie wtedy, gdy jest ≥ 1 kolektor (i uprawnienie, SMS-08): rejestracja przy pierwszym kolektorze, wyrejestrowanie przy zakończeniu ostatniego (anulowanie albo błąd kolektora). Przy kilku kolektorach jest jedna rejestracja, a każdy kolektor dostaje każdą dostarczaną wiadomość dokładnie raz. Wiadomość dla wolnego kolektora jest buforowana bez limitu i bez blokowania obsługi broadcastu. Strumień nie kończy się sam (tylko anulowaniem kolektora) i nie rzuca wyjątków.

**Kryteria akceptacji:**

- **Zakładając** broadcast z `bg.txt` od numeru AAPS obsłużony przed rozpoczęciem zbierania, **gdy** kolektor zaczyna zbierać, **wtedy** nie dostaje tej wiadomości.
- **Zakładając** kolektor uruchomiony z `CoroutineStart.UNDISPATCHED`, **gdy** `launch` zwrócił, **wtedy** liczba rejestracji receivera = 1, a broadcast obsłużony zaraz potem jest dostarczany temu kolektorowi.
- **Zakładając** dwóch kolektorów, **gdy** przychodzą broadcasty z `bg.txt` i `pump.txt`, **wtedy** każdy kolektor dostaje dokładnie te dwie wiadomości w tej kolejności, a liczba rejestracji = 1.
- **Zakładając** dwóch kolektorów, **gdy** pierwszy zostaje anulowany, **wtedy** receiver jest nadal zarejestrowany; **gdy** drugi zostaje anulowany, **wtedy** liczba wyrejestrowań = 1 i receiver nie jest zarejestrowany.
- **Zakładając** wyrejestrowany receiver po zakończeniu sesji, **gdy** nowy kolektor zaczyna zbierać, **wtedy** liczba rejestracji = 2.
- **Zakładając** kolektora, który nie pobiera elementów, **gdy** przychodzi 200 broadcastów z różnymi treściami od numeru AAPS, **wtedy** obsługa każdego broadcastu kończy się bez zawieszenia, a po wznowieniu kolektor dostaje 200 wiadomości w kolejności.
- **Zakładając** kolektora bez wiadomości, **gdy** mija 600 000 ms czasu wirtualnego, **wtedy** strumień się nie zakończył i nie rzucił wyjątku.

### SMS-08: Brak uprawnienia RECEIVE_SMS i błąd rejestracji

Gdy w chwili dołączenia kolektora, przy niezarejestrowanym receiverze, `RECEIVE_SMS` nie jest przyznane, receiver nie jest rejestrowany; strumień nic nie dostarcza, nie kończy się i nie rzuca. Tak samo, gdy rejestracja receivera rzuci wyjątek (wyjątek nie wychodzi do kolektora). W obu przypadkach kolejny kolektor dołączający przy niezarejestrowanym receiverze ponawia sprawdzenie uprawnienia i rejestrację. Wyjątek przy wyrejestrowaniu nie wychodzi do kolektora.

**Kryteria akceptacji:**

- **Zakładając** brak `RECEIVE_SMS`, **gdy** kolektor zbiera przez 600 000 ms czasu wirtualnego, **wtedy** liczba rejestracji = 0, kolektor nie dostał nic, strumień nie zakończył się i nie rzucił wyjątku.
- **Zakładając** rejestrator rzucający `SecurityException` przy rejestracji, **gdy** kolektor zaczyna zbierać, **wtedy** kolektor nie dostaje wyjątku ani wiadomości.
- **Zakładając** brak `RECEIVE_SMS` przy pierwszym kolektorze, a potem przyznane uprawnienie, **gdy** ten kolektor się kończy i zaczyna nowy, **wtedy** liczba rejestracji = 1 i nowy kolektor dostaje wiadomości.
- **Zakładając** rejestrator rzucający `IllegalArgumentException` przy wyrejestrowaniu, **gdy** jedyny kolektor zostaje anulowany, **wtedy** kończy się zwykłym anulowaniem, bez innego wyjątku.

### SMS-09: Wysłanie treści na numer AAPS z domyślnej karty

`send(message)` wysyła na adres docelowy poprawnego numeru AAPS (SMS-01, numer odczytany przy każdym wywołaniu) dokładnie jedną wiadomość: dla `CommandText` – tekst `text` bez zmian, dla `CodeReply` – tekst złożony ze znaków `chars` w tej samej kolejności (bez spacji i końca linii). Wysyłanie używa `SmsManager` domyślnej karty SMS systemu (API ≥ 31: `Context.getSystemService(SmsManager::class.java)`, API 28–30: `SmsManager.getDefault()`). Treść jest dzielona przez `SmsManager.divideMessage`; 1 część → `sendTextMessage`, > 1 część → `sendMultipartTextMessage`, z `PendingIntent` wysłania dla każdej części i bez raportu doręczenia. Port nie modyfikuje tablicy `chars`.

**Kryteria akceptacji:**

- **Zakładając** `CommandText("CARBS 20")`, **gdy** wywołano `send`, **wtedy** warstwa systemowa otrzymała dokładnie jedno zlecenie: adres `+447700900123`, tekst `CARBS 20`.
- **Zakładając** `CodeReply` z tablicą `0504714321`, **gdy** wywołano `send`, **wtedy** warstwa systemowa otrzymała tekst `0504714321`, a po powrocie tablica nadal zawiera `0504714321` (zeruje ją sesja, nie port).
- **Zakładając** numer z ustawień zmieniony z `+44 7700 900123` na `07700 900999` między dwoma wywołaniami `send`, **gdy** wywołano je oba, **wtedy** adresy to kolejno `+447700900123` i `07700900999`.
- **Zakładając** telefon z API 28 i z API 31+ (sprawdzenie na urządzeniu, W1), **gdy** wysłano `BG`, **wtedy** SMS wychodzi z domyślnej karty SMS systemu.

### SMS-10: Wynik wysłania (K5)

`send` zawiesza się do wyniku wysłania od systemu i zwraca `SendResult.Sent` wtedy i tylko wtedy, gdy wynik każdej części to `RESULT_OK`. Pierwszy wynik części inny niż `RESULT_OK` (każdy kod błędu `SmsManager.RESULT_ERROR_*` i każdy inny kod) od razu kończy wysyłanie wynikiem `SendResult.Failed`. Powtórzony wynik tej samej części i wynik po ustaleniu wyniku są ignorowane. Brak wyniku od systemu oznacza zawieszenie bez własnego limitu czasu (limit to sesja – SESSION-07, SESSION-15).

**Kryteria akceptacji:**

- **Zakładając** wysyłanie 1 części, **gdy** jej wynik to `RESULT_OK`, **wtedy** `send` zwraca `Sent`.
- **Zakładając** wysyłanie 3 części, **gdy** przyszły wyniki `RESULT_OK` dla części 0 i 2, **wtedy** `send` nadal czeka; **gdy** przyszedł `RESULT_OK` dla części 1, **wtedy** zwraca `Sent`.
- **Zakładając** wysyłanie 3 części, **gdy** wynik części 1 to `RESULT_ERROR_NO_SERVICE`, **wtedy** `send` zwraca `Failed` bez czekania na części 0 i 2.
- **Zakładając** wysyłanie 2 części, **gdy** przyszły dwa razy `RESULT_OK` dla części 0, **wtedy** `send` nadal czeka.
- **Zakładając** wysyłanie 1 części, **gdy** wynik to `RESULT_ERROR_GENERIC_FAILURE`, `RESULT_ERROR_RADIO_OFF`, `RESULT_ERROR_NULL_PDU` albo kod `12345`, **wtedy** w każdym przypadku `send` zwraca `Failed`.
- **Zakładając** warstwę systemową, która nigdy nie zgłasza wyniku, **gdy** mija 600 000 ms czasu wirtualnego, **wtedy** `send` nadal jest zawieszony.

### SMS-11: Anulowanie wysyłania (K5)

Anulowanie korutyny wywołującej `send` przerywa czekanie na wynik: `send` kończy się `CancellationException` (nie `Failed`), receiver wyniku wysłania jest wyrejestrowany, a późniejszy wynik od systemu jest ignorowany. Anulowanie nie cofa SMS-a przekazanego już do systemu.

**Kryteria akceptacji:**

- **Zakładając** `send` czekający na wynik, **gdy** korutyna zostaje anulowana, **wtedy** wywołujący dostaje `CancellationException`, a rejestracja wyników wysłania została wyrejestrowana.
- **Zakładając** anulowany `send`, **gdy** potem przychodzi wynik `RESULT_OK`, **wtedy** nic się nie dzieje (brak wyjątku, brak wznowienia).

### SMS-12: Błędy wysyłania bez wyjątków

`send` nigdy nie rzuca wyjątku innego niż `CancellationException`. Bez wysyłania i z wynikiem `Failed` kończy się, gdy: `SEND_SMS` nie jest przyznane (sprawdzane przy każdym wywołaniu, przed odczytem numeru), numer z ustawień jest nieprawidłową konfiguracją (SMS-01) albo `AapsNumberProvider` rzuci wyjątek. Wynikiem `Failed` kończy się też, gdy sprawdzenie uprawnienia albo warstwa systemowa (w tym `SmsManager`: brak usługi SMS, `SecurityException`, `IllegalArgumentException`) rzuci wyjątek inny niż `CancellationException`.

**Kryteria akceptacji:**

- **Zakładając** brak `SEND_SMS`, **gdy** wywołano `send(CommandText("BG"))`, **wtedy** wynik to `Failed`, warstwa systemowa nie została wywołana, a numer nie został odczytany.
- **Zakładając** numer z ustawień `null`, `70090012` albo `+44 7700 9OO123`, **gdy** wywołano `send(CommandText("BG"))`, **wtedy** wynik to `Failed`, a warstwa systemowa nie została wywołana.
- **Zakładając** warstwę systemową rzucającą `SecurityException("x")`, **gdy** wywołano `send`, **wtedy** wynik to `Failed` bez wyjątku.
- **Zakładając** dostawcę numeru rzucającego `IllegalStateException`, **gdy** wywołano `send`, **wtedy** wynik to `Failed`, a warstwa systemowa nie została wywołana.

### SMS-13: Odpowiedź z kodem nie jest przechowywana (K6)

Port wysyłania tworzy tekst odpowiedzi z kodem (`String` z `CodeReply.chars`) tylko jako zmienną lokalną wywołania i przekazuje go wyłącznie do `SmsManager` (przez warstwę systemową). Po powrocie z `send` (dowolny wynik, wyjątek, anulowanie) żaden obiekt pakietu `sms` nie trzyma referencji do tablicy `chars`, tekstu ani jego części: wszystkie pola klas wysyłania są niezmienne i żadne nie może przechować treści (w praktyce: zależności – dostawca numeru, uprawnienia, warstwa systemowa, `Context`). Treść nie trafia do logów, wyjątków ani `toString()`. Ograniczenie: `String` w JVM nie da się wyzerować; zostaje w pamięci do odśmiecenia, a kopie poza aplikacją (system, `SmsManager`, skrzynka SMS – otwarte pytanie P1) są poza jej kontrolą.

**Kryteria akceptacji:**

- **Zakładając** klasy implementujące wysyłanie (port `AapsSmsSender` i warstwa systemowa `AndroidSmsSystemApi` – obiekty żyjące dłużej niż wywołanie), **gdy** sprawdzono ich pola refleksją, **wtedy** żadne nie jest typu `String`, `CharArray`, `CharSequence`, kolekcji ani tablicy, a wszystkie są `final`.
- **Zakładając** `CodeReply` z tablicą `0504714321` i warstwę systemową rzucającą wyjątek, **gdy** wywołano `send`, **wtedy** wynik to `Failed`, a `toString()` portu i warstwy systemowej nie zawiera `050471` ani `4321`.

### SMS-14: Sprawdzanie uprawnień

Pakiet `sms` udostępnia `SmsPermissions` z dwoma sprawdzeniami: wysyłanie (`android.permission.SEND_SMS`) i odbiór (`android.permission.RECEIVE_SMS`), każde zwraca stan w chwili wywołania (`checkSelfPermission == PERMISSION_GRANTED`), bez zapamiętywania. Lista wymaganych uprawnień dla `ui` zawiera dokładnie te dwa, w tej kolejności. `SmsPermissions` nie prosi o uprawnienia (to `ui`).

**Kryteria akceptacji:**

- **Zakładając** listę wymaganych uprawnień, **gdy** ją odczytano, **wtedy** to dokładnie `android.permission.SEND_SMS`, `android.permission.RECEIVE_SMS`.
- **Zakładając** telefon z `SEND_SMS` przyznanym i `RECEIVE_SMS` odrzuconym (sprawdzenie na urządzeniu), **gdy** odczytano stan, **wtedy** wysyłanie = przyznane, odbiór = nieprzyznane; **gdy** użytkownik przyznał `RECEIVE_SMS` w ustawieniach, **wtedy** kolejne odczytanie daje odbiór = przyznane.

### SMS-15: Manifest

`AndroidManifest.xml` aplikacji deklaruje `uses-permission` dla `android.permission.SEND_SMS` i `android.permission.RECEIVE_SMS`, nie deklaruje `READ_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`, `BROADCAST_SMS` ani `WRITE_SMS`, nie ma elementu `<receiver>` ani `<service>`, a `android:allowBackup="false"` pozostaje bez zmian.

**Kryteria akceptacji:**

- **Zakładając** plik `app/src/main/AndroidManifest.xml`, **gdy** test jednostkowy go przeczytał, **wtedy** zbiór `uses-permission` zawiera `SEND_SMS` i `RECEIVE_SMS`, nie zawiera żadnego z wykluczonych, nie ma `<receiver>` ani `<service>`, a `allowBackup` = `false`.

### SMS-16: Brak ujawniania danych

Treść SMS-ów (przychodzących i wysyłanych), kody, PIN i numery telefonu (z ustawień i nadawców) nie trafiają do `toString()` typów z `sms`, do komunikatów wyjątków ani do logów. Pakiet `sms` nie wywołuje żadnego API logowania (`android.util.Log`, `println`, `System.out`/`err`, loggerów). Komunikaty wyjątków rzucanych przez `sms` są stałymi tekstami.

**Kryteria akceptacji:**

- **Zakładając** numer AAPS `+44 7700 900123`, **gdy** wywołano `toString()` na obiekcie poprawnego numeru, **wtedy** tekst nie zawiera `7700`, `900123` ani `700900123`.
- **Zakładając** część i wiadomość złożoną od `+447700900123` z treścią `bg.txt`, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `7700`, `210` ani żadnej linii treści.
- **Zakładając** pamięć deduplikacji z wpisem dla `bg.txt`, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `7700`, `210` ani skrótu wpisu.

## Kontrakt K1–K6 ze Spec 004 – śledzenie

| K | Warunek (Spec 004) | Wymagania |
| --- | --- | --- |
| K1 | `receivedAtEpochMillis` stemplowane w chwili odbioru tym samym `EpochClock` co sesja, nie znacznikiem centrum SMS | SMS-05 (wspólny obiekt zegara: design, wiązanie Hilt) |
| K2 | dostarczenie najwyżej raz, bez nowej chwili odbioru przy ponownym dostarczeniu (także po restarcie procesu) | SMS-06 |
| K3 | strumień gorący, bez powtórek dla nowego kolektora | SMS-07 (także SMS-08: brak uprawnienia nie łamie strumienia) |
| K4 | wyłącznie wiadomości od numeru AAPS, z treścią sklejoną ze wszystkich części | SMS-01, SMS-02, SMS-03, SMS-04 |
| K5 | `send` zawiesza się do wyniku od systemu, „wysłano” po potwierdzeniu, anulowanie przerywa czekanie | SMS-10, SMS-11 (także SMS-12: błędy jako `Failed`, bez wyjątków) |
| K6 | brak przechowywania `CodeReply.chars` i kopii (`String`) po powrocie, brak logowania treści | SMS-13, SMS-16 |

## Przypadki błędów

| Sytuacja | Oczekiwane zachowanie | Wymaganie |
| --- | --- | --- |
| Numer z ustawień `null`, pusty, z niedozwolonym znakiem, < 9 cyfr | nieprawidłowa konfiguracja: `send` = `Failed` bez wysyłania; odbiór nic nie dostarcza | SMS-01, SMS-04, SMS-12 |
| Nadawca alfanumeryczny, `null`, < 9 cyfr, inna końcówka | wiadomość odrzucona | SMS-02, SMS-04 |
| Nadawca z inną kierunkową i tą samą końcówką | przyjęty (znane ograniczenie) | SMS-02 |
| Części od różnych nadawców w jednym broadcaście | osobne wiadomości wg nadawcy | SMS-03 |
| Treść części `null` | pusty tekst | SMS-03 |
| Ten sam SMS dostarczony ponownie | nie jest dostarczany drugi raz | SMS-06 |
| Zegar rzuca przy obsłudze broadcastu | broadcast pominięty, bez wyjątku | SMS-05 |
| Brak `RECEIVE_SMS`, błąd rejestracji receivera | strumień nic nie dostarcza, nie kończy się, nie rzuca | SMS-08 |
| Wolny kolektor | buforowanie bez limitu, obsługa broadcastu się nie blokuje | SMS-07 |
| Brak `SEND_SMS` | `Failed` bez wysyłania i bez odczytu numeru | SMS-12 |
| Wyjątek `SmsManager` / brak usługi SMS / błąd dostawcy numeru | `Failed`, bez wyjątku | SMS-12 |
| Wynik części inny niż `RESULT_OK` | `Failed` od razu | SMS-10 |
| Brak wyniku od systemu | zawieszenie do anulowania przez sesję | SMS-10, SMS-11 |
| Anulowanie w trakcie wysyłania | `CancellationException`, receiver wyniku wyrejestrowany | SMS-11 |
| Proces zabity w trakcie sesji | sesja przepada, receiver nie istnieje (poza zakresem) | – |

## Bezpieczeństwo

Zasady z [AGENTS.md](../../../AGENTS.md#bezpieczeństwo), które dotyczą tej funkcji:

- **Nie odsyłamy kodu, gdy odpowiedź nie pochodzi od AAPS.** Inny numer nadawcy odcina SMS-02/SMS-04; nadawca alfanumeryczny nigdy nie pasuje; nieprawidłowa konfiguracja numeru blokuje odbiór i wysyłanie. Ponowne dostarczenie tej samej wiadomości blokuje SMS-06 (K2); odpowiedź sprzed wysłania polecenia odrzuca sesja dzięki chwili odbioru z tego samego zegara (SMS-05, K1). Receiver `SMS_RECEIVED` przyjmuje broadcasty tylko od nadawcy z uprawnieniem `android.permission.BROADCAST_SMS` (design), więc inna aplikacja nie podsunie fałszywego SMS-a.
- **Znane ograniczenie porównania po 9 cyfrach** (decyzja użytkownika): numer z inną kierunkową i tą samą końcówką (np. `+17700900123` wobec `+447700900123`) pasuje. Ryzyko jest małe wobec ryzyka, które już istnieje: AAPS sam przyjmuje polecenia tylko z dozwolonych numerów, a podszycie się pod numer nadawcy SMS jest możliwe niezależnie od sposobu porównania. Ochroną kodu pozostaje dokładne dopasowanie treści i ilości (Spec 002, Spec 004).
- **PIN nigdy nie jest zapisywany przez aplikację.** Odpowiedź z kodem istnieje w `sms` tylko jako zmienna lokalna wywołania (SMS-13); port nie ma pól z danymi, nie modyfikuje tablicy sesji, nie loguje. Ograniczenie JVM: kopii `String` nie da się wyzerować. Kopia w systemie Androida (skrzynka „wysłane”) – otwarte pytanie P1.
- **Nie logujemy treści, kodów, PIN-u ani numerów** (SMS-16).
- **Brak automatycznego ponawiania.** Jedno wywołanie `send` = jedno zlecenie wysłania; port nie ponawia po błędzie ani po braku wyniku (SMS-10, SMS-12). Ponawianie wykluczone także w sesji (SESSION-20).
- **Minimum uprawnień:** tylko `SEND_SMS` i `RECEIVE_SMS`, bez `READ_SMS` i bez roli domyślnej aplikacji SMS (SMS-15). `allowBackup=false` bez zmian.
- **Dane w repo:** wyłącznie fikcyjne numery (poniżej), dane z `../002-parser-odpowiedzi/dane/`, fikcyjny PIN `4321` i kod z wektora RFC 6238 `050471`.

## Dane testowe

- Numery telefonu: wyłącznie z zakresu zarezerwowanego przez brytyjski urząd Ofcom dla fikcji („drama numbers”: `07700 900000`–`07700 900999`, w formacie międzynarodowym `+44 7700 900000`–`+44 7700 900999`): `+44 7700 900123` (numer AAPS), `+447700900999` i `+447700900124`, `+447700900321` (inni nadawcy). Wyjątki: `+17700900123` – numer nieprzydzielany w planie numeracji Ameryki Północnej (centrala zaczynająca się od `0`), użyty tylko do kryterium „inna kierunkowa, ta sama końcówka”; `70090012`, `700900123` – fragmenty numeru fikcyjnego.
- Treści: pliki z [`../002-parser-odpowiedzi/dane/`](../002-parser-odpowiedzi/dane/README.md) (`bg.txt`, `pump.txt`, `carbs-prosba-o-kod.txt`) i syntetyczna prośba o kod `Aby wprowadzic 20g o 9:05 odeslij w SMS kod z aplikacji Authenticator dla: Jan, z doklejonym PIN-em` (Spec 004, SESSION-08) podzielona na dwie części.
- Odpowiedź z kodem: `0504714321` (kod `050471` z wektora RFC 6238 dla `1111111111`, fikcyjny PIN `4321` – Spec 004).
- Znaczniki: `T0 = 1111111051000`, znacznik centrum SMS `1111111000000` (fikcyjne).
- Brak nowych plików w `dane/`.

## Weryfikacja na urządzeniu

Fakty o zachowaniu Androida przyjęte w tej specyfikacji na podstawie dokumentacji, do potwierdzenia na telefonie (etap 2 planu) w ramach zadań 5 i 6 z [design.md](design.md#podział-na-zadania). Wynik niezgodny z założeniem = zmiana spec, nie obejście w kodzie.

| Nr | Do sprawdzenia | Wymaganie |
| --- | --- | --- |
| W1 | SMS wychodzi z domyślnej karty SMS na API 28–30 (`SmsManager.getDefault()`) i API ≥ 31 (`getSystemService`) | SMS-09 |
| W2 | Receiver wyniku wysłania (`RECEIVER_NOT_EXPORTED`, `PendingIntent` z `FLAG_IMMUTABLE`) dostaje kod wyniku każdej części | SMS-10 |
| W3 | Dynamiczny receiver `SMS_RECEIVED` (eksportowany, wymagane uprawnienie nadawcy `BROADCAST_SMS`) dostaje SMS-y na API 28 i na najnowszym dostępnym API | SMS-07 |
| W4 | Długa odpowiedź (> 160 znaków GSM) przychodzi w jednym broadcaście z częściami w kolejności | SMS-03 |
| W5 | Zachowanie przy telefonie dual SIM bez ustawionej domyślnej karty SMS („pytaj za każdym razem”) – wynik trafia jako `Failed` albo zawieszenie (bez zmiany wymagań) | SMS-09, SMS-10 |
| W6 | Przyznanie `SEND_SMS`/`RECEIVE_SMS` aplikacji instalowanej z pliku APK (ograniczone uprawnienia na nowszych Androidach) – informacja dla spec `ui` | SMS-14 |

## Otwarte pytania

- [ ] **P1. Kopia odpowiedzi z kodem w skrzynce SMS.** Według dokumentacji Androida (zmiany SMS w Androidzie 4.4) system sam zapisuje do dostawcy SMS (folder „wysłane” domyślnej aplikacji SMS) każdą wiadomość wysłaną przez `SmsManager` aplikacji, która nie jest domyślną aplikacją SMS. Odpowiedź `<TOTP><PIN>` z PIN-em trafiłaby więc na dysk poza kontrolą aplikacji – tak samo jak przy ręcznym wysłaniu kodu z aplikacji SMS. Wysyłanie bez zapisu (`sendTextMessageWithoutPersisting`) wymaga uprawnień systemowych, a usunięcie wpisu – roli domyślnej aplikacji SMS (poza zakresem). Czy akceptujesz to ryzyko szczątkowe (wpis w sekcji Bezpieczeństwo i doprecyzowanie AGENTS.md: „aplikacja nie zapisuje PIN-u; kopię wysłanego SMS-a przechowuje system”), czy szukamy innego rozwiązania? Do potwierdzenia na urządzeniu.
- [ ] **P2. Odbiór i limity przy wygaszonym ekranie na Androidzie 14+.** Decyzja „działa przy wygaszonym ekranie, dopóki żyje proces” zakłada, że proces pracuje. Według dokumentacji zmian Androida 14 system może odkładać broadcasty do receiverów rejestrowanych dynamicznie, gdy aplikacja jest w stanie „cached” (np. po wygaszeniu ekranu bez usługi pierwszoplanowej), a procesy w tym stanie są zamrażane. Wtedy prośba o kod i odpowiedzi mogłyby dotrzeć z opóźnieniem (chwila odbioru = chwila `onReceive`, późniejsza niż faktyczna), a liczniki sesji mogłyby stać. Czy akceptujesz w MVP wymóg „ekran włączony z aplikacją na wierzchu w trakcie polecenia” (komunikat w `ui`), czy usługa pierwszoplanowa ma wejść do MVP (osobna spec)? Do sprawdzenia na telefonie (nie zgaduję, czy `SMS_RECEIVED` jest z tego wyjęty).
- [ ] **P3. Testy cienkiej warstwy Androida.** W projekcie nie ma Robolectric ani testów instrumentalnych; logika czysta ma testy JUnit 5, a klasy z `android.*` (receiver, `SmsManager`, `checkSelfPermission`) nie dadzą się uruchomić w testach jednostkowych bez dodatkowej biblioteki. Propozycja: warstwa Androida bez testów automatycznych, sprawdzana listą W1–W6 na telefonie, opisaną w PR; test manifestu (SMS-15) jako zwykły test JVM. Alternatywy: Robolectric (wymaga JUnit 4 przez silnik vintage) albo testy `androidTest` na emulatorze (CI ich nie uruchamia). Którą opcję wybierasz?

## Przyszłe zmiany (nie blokują)

- `SendResult.Failed` bez przyczyny: `ui` pokaże ogólny „SMS nie wysłał się”. Plan wspomina „błąd z `SmsManager` pokazany od razu” – przyczyna (brak zasięgu, radio wyłączone, brak uprawnienia, brak numeru) wymagałaby zmiany Spec 004 (`SendResult.Failed(reason)`) i mapowania kodów w tej spec. Proponowane jako osobny PR `Spec: zmiana sesja polecenia`, gdy powstanie spec `ui`.
