# Spec 008: UI – ustawienia, sekret TOTP i uprawnienia

- **Status:** zatwierdzona
- **Prefiks wymagań:** `UI`
- **Pakiety:** `ui` (`ui.navigation`, `ui.settings`, `ui.settings.number`, `ui.settings.secret`, `ui.permissions`, `ui.commands`, `ui.history`, `ui.platform`), `session.platform` (moduł Hilt koordynatora sesji)
- **Powiązane:** [docs/plan.md](../../plan.md#ekrany-aplikacji) (ekrany „Konfiguracja”, „Historia i ustawienia”); [Spec 001: Generator TOTP](../001-totp/spec.md) (`OtpauthParser`, `TotpSecret`, TOTP-06–TOTP-09); [Spec 004: Sesja polecenia](../004-sesja-polecenia/spec.md) (`SessionCoordinator`, SESSION-22); [Spec 005: SMS](../005-sms/spec.md) (`SmsPermissions`, SMS-14, SMS-15, W6); [Spec 006: Sekret TOTP](../006-sekret-totp/spec.md) (`TotpSecretStore`, `SecretState`, `SaveResult`, `DeleteResult`, W1–W10); [Spec 007: Dane](../007-dane/spec.md) (`AapsNumberSettingsStore`, `AapsNumberState`, `SaveNumberResult`, `ClearNumberResult`, sekcja Bezpieczeństwo, W1–W7); [stan prac](../../stan-prac.md); następne specyfikacje `ui`: 009 (ekran Polecenia i przebieg polecenia), 010 (ekran Historia)

## Cel

Użytkownik musi raz skonfigurować aplikację: wpisać numer telefonu z AAPS, zapisać sekret TOTP z kodu QR pokazanego przez AAPS i przyznać uprawnienia SMS. Ta specyfikacja opisuje szkielet nawigacji aplikacji (dolny pasek z trzema zakładkami) oraz ekran Ustawienia z tymi trzema sekcjami, a także moduł Hilt dający jeden `SessionCoordinator`, potrzebny do blokowania zmian ustawień w trakcie polecenia.

## Zakres

- Navigation Compose: dolny pasek z zakładkami Polecenia / Historia / Ustawienia, start na zakładce Polecenia.
- Zakładki Polecenia i Historia jako zaślepki z krótkim tekstem (zastąpią je spec 009 i 010).
- Ekran Ustawienia, sekcja „Numer telefonu AAPS”: stan numeru, ustawienie/zmiana z walidacją, usunięcie z potwierdzeniem, komunikaty wyników.
- Ekran Ustawienia, sekcja „Sekret TOTP”: stan sekretu, skan kodu QR aparatem (CameraX + ML Kit, tylko format QR, offline), ręczne wklejenie URI `otpauth://`, pytanie o zastąpienie istniejącego sekretu, usunięcie z potwierdzeniem, komunikaty wyników, uprawnienie `CAMERA`.
- Ekran Ustawienia, sekcja „Uprawnienia SMS”: stan `SEND_SMS`/`RECEIVE_SMS`, prośba o uprawnienia, komunikat przy odmowie, przejście do ustawień aplikacji przy trwałej odmowie, odświeżenie po powrocie do aplikacji.
- Czyszczenie schowka systemowego po wklejeniu URI, gdy zawiera dokładnie wklejony tekst.
- Blokada zmiany i usunięcia numeru oraz sekretu, gdy `SessionCoordinator.activeSession.value != null`.
- Moduł Hilt `session.platform.SessionModule`: singleton `SessionCoordinator` i `UsableTotpProvider`.
- Nowe zależności Gradle, wpisy w manifeście (`CAMERA`), teksty UI po polsku w `strings.xml`.

**Poza zakresem:**

- Ekran Polecenia i przebieg polecenia: przyciski BG/PUMP/CARBS, PIN AAPS, CARBS z potwierdzeniem, start sesji, `SessionHistoryRecorder.track`, odczyt sekretu `TotpSecretProvider.load` przed poleceniem z kodem i komunikaty jego wyników (`Absent`, `Locked`, `Invalidated`, `Unavailable`), ekran włączony w trakcie polecenia, zakres korutyn uruchamiania sesji, ręczna weryfikacja W1–W7 ze Spec 005 – spec 009.
- Ekran Historia: lista wpisów, potwierdzenie przed wyczyszczeniem historii, weryfikacja W3–W5 ze Spec 007 i część W6 dotycząca historii – spec 010.
- Osobny kreator pierwszego uruchomienia i „test połączenia `BG`” z planu – aplikacja startuje na zakładce Polecenia; konfiguracja odbywa się w zakładce Ustawienia (decyzja użytkownika). Test `BG` to zwykłe polecenie z 009.
- Ustawienie limitów czasu sesji (plan: „limit oczekiwania”) – limity są stałe (Spec 004).
- Wybór karty SIM (Spec 005: tylko domyślna karta SMS).
- Testy Robolectric i testy Compose UI; kompozycje i warstwa kamery są sprawdzane ręcznie (sekcja „Weryfikacja na urządzeniu”).

## Założenia i zależności

- **Numer AAPS:** jedyną regułą poprawności jest `sms.AapsNumber.parse`, stosowana przez `AapsNumberSettingsStore.save` (DATA-03); `ui` jej nie powtarza, tylko pokazuje wynik `Invalid`.
- **Sekret TOTP:** jedynym parserem URI jest `otp.OtpauthParser` (TOTP-06–TOTP-09), jedynym miejscem zapisu `TotpSecretStore.save` (SECRET-04); `ui` nie dekoduje Base32 i nie interpretuje parametrów URI.
- **Stan sekretu** `SecretState` zaczyna jako `UNKNOWN` (SECRET-12); `ui` wywołuje `refreshState()`, żeby poznać stan bez odszyfrowania.
- **Sesja:** `SessionCoordinator` jest jeden na aplikację (SESSION-22); `activeSession.value != null` oznacza trwające polecenie. W 008 żaden ekran nie uruchamia sesji – robi to 009; blokady z tej spec działają od razu na wspólnym singletonie.
- **Testy (decyzja użytkownika):** tylko JVM – ViewModele, logika stanu ekranów, testy plików (manifest, `strings.xml`, źródła); JUnit 5, MockK, Turbine, `kotlinx-coroutines-test`. Bez Robolectric i bez testów Compose UI. Kompozycje, nawigacja, kamera, okna uprawnień i Hilt – weryfikacja ręczna W1–W10 i `assembleDebug`.
- **Wymagania dla spec 009** (wynikają z tej specyfikacji, nie są tu realizowane):
  - przed startem polecenia 009 używa tego samego stanu uprawnień SMS (`SmsPermissions` + klasyfikacja odmowy z UI-17, UI-18) i tych samych tekstów komunikatów;
  - 009 uruchamia sesje wyłącznie przez singleton `SessionCoordinator` z UI-19;
  - komunikaty wyników `TotpSecretProvider.load` („odblokuj telefon”, „zeskanuj ponownie QR z AAPS”, „spróbuj ponownie”, „zeskanuj QR z AAPS”) definiuje 009; sekret jest odczytywany tuż przed startem polecenia z kodem i nietrzymany w ViewModelu dłużej niż do przekazania `CodeCredentials` (Spec 006, SECRET-14).

## Pojęcia

- **Zakładka** – jedno z trzech miejsc docelowych nawigacji: Polecenia, Historia, Ustawienia.
- **Polecenie w toku** – `SessionCoordinator.activeSession.value != null`.
- **Operacja ustawień** – wywołanie `AapsNumberSettingsStore.save`/`clear` albo `TotpSecretStore.save`/`delete` przez ViewModel.
- **Wejście sekretu** – tekst URI odczytany przez skaner QR albo wpisany/wklejony w pole tekstowe.
- **Pole wklejania** – pole tekstowe na URI `otpauth://` w oknie „Wklej URI”.
- **Trwała odmowa** – po prośbie o uprawnienie wynik „odmowa”, a `shouldShowRequestPermissionRationale` = `false` dla każdego brakującego uprawnienia (system nie pokaże więcej okna).
- **Komunikat** – jednorazowa informacja dla użytkownika (np. pasek Snackbar), w stanie ViewModelu jako wartość wyliczeniowa, kasowana po pokazaniu.

## Wymagania

Wspólne dane kryteriów (o ile kryterium nie mówi inaczej): numery fikcyjne `+44 7700 900123` (`destination` = `+447700900123`) i `+44 7700 900999` (`+447700900999`) z zakresu Ofcom „drama numbers”; sekret RFC 6238 `12345678901234567890` (ASCII) zapisany w URI jako `URI_RFC` = `otpauth://totp/AAPS?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=AAPS&algorithm=SHA1&digits=6&period=30`; atrapy magazynów (`AapsNumberSettingsStore`, `TotpSecretStore`), `SmsPermissions` i `CameraPermission`; `SessionCoordinator` z atrapą `activeSession` (MockK); dyspozytor główny zastąpiony `StandardTestDispatcher`. „Brak wywołania magazynu” = atrapa nie zarejestrowała wywołania danej metody.

### Nawigacja

### UI-01: Trzy zakładki i start

Aplikacja ma jedno `Activity` (`MainActivity`) z ekranem zbudowanym z Navigation Compose i dolnym paskiem nawigacji (Material 3 `NavigationBar`) z trzema zakładkami w kolejności: Polecenia, Historia, Ustawienia. Każda zakładka ma ikonę i etykietę z `strings.xml`. Ekranem startowym jest Polecenia.

**Kryteria akceptacji:**

- **Zakładając** wyliczenie miejsc docelowych, **gdy** odczytano jego wartości, **wtedy** są dokładnie trzy, w kolejności `COMMANDS`, `HISTORY`, `SETTINGS`, z różnymi trasami, a miejsce startowe = `COMMANDS`.
- **Zakładając** każde miejsce docelowe, **gdy** odczytano jego etykietę i ikonę, **wtedy** obie są niezerowymi identyfikatorami zasobów, a etykiety są różne.
- **Zakładając** zimny start aplikacji (sprawdzenie na urządzeniu, W1), **gdy** ekran się pojawił, **wtedy** widać zaślepkę Polecenia, a w dolnym pasku zaznaczona jest zakładka Polecenia.

### UI-02: Przełączanie zakładek i przycisk Wstecz

Dotknięcie zakładki pokazuje jej ekran i zaznacza ją w pasku. Na stosie nawigacji jest najwyżej jeden wpis każdej zakładki: ponowne dotknięcie zaznaczonej zakładki nie dodaje wpisu. Przycisk Wstecz na zakładce Historia albo Ustawienia wraca do zakładki Polecenia; na zakładce Polecenia zamyka aplikację (`Activity` kończy się). Obrót ekranu nie zmienia zaznaczonej zakładki.

**Kryteria akceptacji:**

- **Zakładając** zakładkę Polecenia (sprawdzenie na urządzeniu, W1), **gdy** dotknięto kolejno Ustawienia, Historia, Ustawienia, Ustawienia, a potem Wstecz, **wtedy** widać zakładkę Polecenia; kolejne Wstecz zamyka aplikację.
- **Zakładając** zakładkę Ustawienia (W1), **gdy** obrócono ekran, **wtedy** nadal widać zakładkę Ustawienia, zaznaczoną w pasku.

### UI-03: Zaślepki Polecenia i Historia

Zakładki Polecenia i Historia pokazują wyłącznie nagłówek z nazwą zakładki i jedno zdanie z `strings.xml`: Polecenia – że polecenia pojawią się w kolejnej wersji, a do tego czasu można skonfigurować aplikację w zakładce Ustawienia; Historia – że historia poleceń pojawi się w kolejnej wersji. Zaślepki nie mają przycisków ani ViewModeli.

**Kryteria akceptacji:**

- **Zakładając** `strings.xml`, **gdy** test JVM go przeczytał, **wtedy** zawiera niepuste teksty o kluczach z design dla obu zaślepek.
- **Zakładając** aplikację na telefonie (W1), **gdy** wybrano zakładkę Polecenia, a potem Historia, **wtedy** każda pokazuje swój nagłówek i zdanie, bez przycisków.

### Numer telefonu AAPS

### UI-04: Stan numeru w sekcji

Sekcja „Numer telefonu AAPS” pokazuje stan z `AapsNumberSettingsStore.state` i udostępnia akcje według tabeli. Akcje są dodatkowo niedostępne, gdy trwa operacja ustawień tej sekcji (UI-16) albo jest polecenie w toku (UI-15).

| Stan numeru | Treść | Dostępne akcje |
| --- | --- | --- |
| `Loading` | „Wczytywanie…” | brak |
| `NotConfigured` | „Nie ustawiono numeru AAPS” | „Ustaw numer” |
| `Configured(number)` | `number.destination` | „Zmień”, „Usuń” |
| `LoadFailed` | „Nie udało się wczytać numeru. Ustaw go ponownie.” | „Ustaw numer” |

**Kryteria akceptacji:**

- **Zakładając** magazyn w stanie `Loading`, **gdy** utworzono ViewModel, **wtedy** stan ekranu = wyświetlanie `Loading`, a akcje „Ustaw/Zmień” i „Usuń” są niedostępne.
- **Zakładając** magazyn, którego stan zmienia się kolejno na `NotConfigured`, `Configured(+447700900123)`, `LoadFailed` (Turbine), **gdy** ViewModel zbiera stan, **wtedy** stan ekranu pokazuje kolejno: brak numeru z akcją „Ustaw numer”; numer `+447700900123` z akcjami „Zmień” i „Usuń”; błąd wczytania z akcją „Ustaw numer”.

### UI-05: Ustawienie i zmiana numeru

„Ustaw numer”/„Zmień” otwiera edycję: pole tekstowe (klawiatura telefoniczna) wypełnione `destination` przy stanie `Configured`, puste w pozostałych stanach, z przyciskami „Zapisz” i „Anuluj”. „Zapisz” wywołuje `AapsNumberSettingsStore.save(tekst pola)` dokładnie raz. Wynik:

| Wynik | Reakcja |
| --- | --- |
| `Saved` | edycja zamknięta, pole wyczyszczone, komunikat „Zapisano numer AAPS” |
| `Invalid` | edycja otwarta, tekst pola bez zmian, pod polem błąd „Nieprawidłowy numer. Podaj co najmniej 9 cyfr, opcjonalnie z „+” na początku.” |
| `Failed` | edycja otwarta, tekst pola bez zmian, komunikat „Nie udało się zapisać numeru. Spróbuj ponownie.” |

Zmiana tekstu pola usuwa błąd spod pola. „Anuluj” zamyka edycję i czyści pole bez wywołania magazynu. Walidację wykonuje wyłącznie magazyn (`AapsNumber.parse`); ViewModel nie wywołuje `AapsNumber.parse` i nie blokuje „Zapisz” dla pustego tekstu.

**Kryteria akceptacji:**

- **Zakładając** stan `Configured(+447700900999)`, **gdy** dotknięto „Zmień”, **wtedy** edycja jest otwarta, a tekst pola = `+447700900999`.
- **Zakładając** stan `NotConfigured` i edycję z tekstem `+44 7700 900123`, magazyn zwracający `Saved`, **gdy** dotknięto „Zapisz”, **wtedy** magazyn dostał dokładnie jedno wywołanie `save("+44 7700 900123")`, edycja jest zamknięta, tekst pola = `""`, komunikat = zapisano.
- **Zakładając** edycję z tekstem `70090012` i magazyn zwracający `Invalid`, **gdy** dotknięto „Zapisz”, **wtedy** edycja jest otwarta, tekst pola = `70090012`, błąd pola = nieprawidłowy numer; **gdy** potem zmieniono tekst na `700900123`, **wtedy** błąd pola = brak.
- **Zakładając** edycję z tekstem `""` i magazyn zwracający `Invalid`, **gdy** dotknięto „Zapisz”, **wtedy** magazyn dostał `save("")`, a błąd pola = nieprawidłowy numer.
- **Zakładając** edycję z tekstem `+44 7700 900123` i magazyn zwracający `Failed`, **gdy** dotknięto „Zapisz”, **wtedy** edycja jest otwarta, tekst pola bez zmian, komunikat = błąd zapisu.
- **Zakładając** edycję z tekstem `+44 7700 900123`, **gdy** dotknięto „Anuluj”, **wtedy** edycja jest zamknięta, tekst pola = `""`, a magazyn nie dostał wywołania.

### UI-06: Usunięcie numeru z potwierdzeniem

„Usuń” pokazuje okno potwierdzenia „Usunąć numer AAPS? Bez numeru aplikacja nie wyśle poleceń.” z przyciskami „Usuń” i „Anuluj”. Dopiero potwierdzenie wywołuje `AapsNumberSettingsStore.clear()` dokładnie raz. Wynik `Cleared` → komunikat „Usunięto numer AAPS”; `Failed` → komunikat „Nie udało się usunąć numeru. Spróbuj ponownie.” Anulowanie zamyka okno bez wywołania magazynu.

**Kryteria akceptacji:**

- **Zakładając** stan `Configured(+447700900123)`, **gdy** dotknięto „Usuń”, **wtedy** okno potwierdzenia jest otwarte, a magazyn nie dostał wywołania `clear`.
- **Zakładając** otwarte okno potwierdzenia i magazyn zwracający `Cleared`, **gdy** potwierdzono, **wtedy** okno jest zamknięte, magazyn dostał dokładnie jedno `clear()`, komunikat = usunięto.
- **Zakładając** otwarte okno i magazyn zwracający `Failed`, **gdy** potwierdzono, **wtedy** komunikat = błąd usunięcia.
- **Zakładając** otwarte okno, **gdy** anulowano, **wtedy** okno jest zamknięte, a magazyn nie dostał wywołania `clear`.

### Sekret TOTP

### UI-07: Stan sekretu w sekcji

Przy utworzeniu ViewModel sekcji „Sekret TOTP” wywołuje `TotpSecretStore.refreshState()` dokładnie raz i dalej pokazuje stan z `TotpSecretStore.state` według tabeli. Akcje są dodatkowo niedostępne, gdy trwa operacja ustawień tej sekcji (UI-16) albo jest polecenie w toku (UI-15).

| `SecretState` | Treść | Dostępne akcje |
| --- | --- | --- |
| `UNKNOWN` | „Sprawdzanie…” | „Skanuj QR”, „Wklej URI”, „Usuń sekret” |
| `ABSENT` | „Brak sekretu TOTP. Zeskanuj kod QR z AAPS.” | „Skanuj QR”, „Wklej URI” |
| `PRESENT` | „Sekret TOTP zapisany” | „Skanuj QR”, „Wklej URI”, „Usuń sekret” |
| `INVALIDATED` | „Sekret TOTP przestał działać (np. po zmianie blokady ekranu). Zeskanuj ponownie kod QR z AAPS.” | „Skanuj QR”, „Wklej URI”, „Usuń sekret” |

`UNKNOWN` nie blokuje akcji, bo `refreshState()` przy błędzie odczytu zostawia stan bez zmian (SECRET-12) i stan `UNKNOWN` może trwać do końca procesu; przy `UNKNOWN` zapis jest poprzedzony pytaniem o zastąpienie (UI-08).

**Kryteria akceptacji:**

- **Zakładając** atrapę magazynu w stanie `UNKNOWN`, **gdy** utworzono ViewModel i wykonano zadania dyspozytora, **wtedy** `refreshState()` wywołano dokładnie raz, a `save`, `load` i `delete` – ani razu.
- **Zakładając** magazyn, którego stan zmienia się kolejno na `ABSENT`, `PRESENT`, `INVALIDATED` (Turbine), **gdy** ViewModel zbiera stan, **wtedy** stan ekranu ma kolejno wyświetlanie i akcje z tabeli.
- **Zakładając** dowolny stan sekretu, **gdy** ViewModel działał przez cały test, **wtedy** `load()` nie został wywołany (odczyt sekretu należy do 009).

### UI-08: Pytanie o zastąpienie przed skanem albo wklejeniem

Gdy stan sekretu to `PRESENT` albo `UNKNOWN`, dotknięcie „Skanuj QR” albo „Wklej URI” najpierw pokazuje okno „Zastąpić zapisany sekret TOTP? Kody z poprzedniego sekretu przestaną działać w aplikacji.” z przyciskami „Zastąp” i „Anuluj”. „Zastąp” kontynuuje wybraną akcję (UI-09/UI-10 albo UI-11); „Anuluj” zamyka okno bez otwierania skanera i pola wklejania. Przy `ABSENT` i `INVALIDATED` akcja rusza od razu, bez pytania. Pytanie jest zadawane przed odczytem wejścia sekretu, więc ViewModel nie trzyma sekretu w czasie, gdy okno jest otwarte.

**Kryteria akceptacji:**

- **Zakładając** stan `PRESENT`, **gdy** dotknięto „Wklej URI”, **wtedy** okno zastąpienia jest otwarte, a pole wklejania zamknięte; **gdy** dotknięto „Zastąp”, **wtedy** okno jest zamknięte, a pole wklejania otwarte.
- **Zakładając** stan `UNKNOWN` i przyznane `CAMERA`, **gdy** dotknięto „Skanuj QR”, **wtedy** okno zastąpienia jest otwarte, a skaner zamknięty; **gdy** dotknięto „Anuluj”, **wtedy** okno jest zamknięte, skaner zamknięty, magazyn nie dostał `save`.
- **Zakładając** stan `ABSENT` (oraz osobno `INVALIDATED`) i przyznane `CAMERA`, **gdy** dotknięto „Skanuj QR”, **wtedy** skaner jest otwarty bez okna zastąpienia.

### UI-09: Skan kodu QR aparatem

Skaner zajmuje cały obszar treści zakładki Ustawienia (bez osobnej trasy nawigacji) i pokazuje podgląd aparatu (CameraX) z analizą klatek przez ML Kit Barcode Scanning z modelem w paczce aplikacji (działa bez sieci), ograniczoną do formatu QR. Pierwsza niepusta wartość tekstowa (`rawValue`) rozpoznanego kodu QR jest przekazywana do ViewModelu jako wejście sekretu; ViewModel zamyka skaner przy pierwszej przyjętej wartości i ignoruje kolejne, dopóki skaner nie zostanie otwarty ponownie. Skaner ma przycisk „Anuluj”; przycisk Wstecz też go zamyka. Gdy aparatu nie da się uruchomić (brak aparatu, błąd CameraX), skaner się zamyka z komunikatem „Nie udało się uruchomić aparatu. Możesz wkleić URI z AAPS.” Klatki obrazu nie są zapisywane na dysku.

**Kryteria akceptacji:**

- **Zakładając** otwarty skaner i magazyn zwracający `Saved`, **gdy** ViewModel dostał dwa razy z rzędu wejście `URI_RFC`, **wtedy** magazyn dostał dokładnie jedno `save`, a skaner jest zamknięty.
- **Zakładając** zamknięty skaner, **gdy** ViewModel dostał wejście ze skanera `URI_RFC`, **wtedy** magazyn nie dostał `save`.
- **Zakładając** otwarty skaner, **gdy** zgłoszono błąd aparatu, **wtedy** skaner jest zamknięty, a komunikat = aparat niedostępny.
- **Zakładając** otwarty skaner, **gdy** dotknięto „Anuluj”, **wtedy** skaner jest zamknięty, a magazyn nie dostał `save`.
- **Zakładając** telefon w trybie samolotowym i kod QR z `URI_RFC` na ekranie komputera (sprawdzenie na urządzeniu, W3), **gdy** otwarto skaner i skierowano aparat na kod, **wtedy** skaner się zamyka, a stan sekretu = „Sekret TOTP zapisany”; kod kreskowy EAN-13 nie zamyka skanera.

### UI-10: Uprawnienie CAMERA

Przed otwarciem skanera ViewModel sprawdza `CAMERA` (`CameraPermission.isGranted()`). Przyznane → skaner się otwiera. Brak → ekran prosi system o `CAMERA`; po wyniku ViewModel ponownie sprawdza uprawnienie: przyznane → skaner się otwiera; brak → skaner zamknięty i w sekcji tekst „Bez dostępu do aparatu nie zeskanujesz kodu QR. Możesz wkleić URI z AAPS.”; przy trwałej odmowie dodatkowo przycisk „Otwórz ustawienia aplikacji”, otwierający systemowy ekran informacji o aplikacji. Tekst odmowy znika, gdy po powrocie do aplikacji (`ON_RESUME`) uprawnienie jest przyznane. „Wklej URI” działa niezależnie od `CAMERA`.

**Kryteria akceptacji:**

- **Zakładając** stan `ABSENT` i brak `CAMERA`, **gdy** dotknięto „Skanuj QR”, **wtedy** stan ekranu zawiera żądanie prośby o `CAMERA`, a skaner jest zamknięty; **gdy** ekran zgłosił wysłanie prośby, **wtedy** żądanie znika.
- **Zakładając** żądanie prośby o `CAMERA`, **gdy** wynik = przyznane (sprawdzenie zwraca `true`), **wtedy** skaner jest otwarty, odmowa = brak.
- **Zakładając** prośbę o `CAMERA`, **gdy** wynik = odmowa i `shouldShowRequestPermissionRationale` = `true`, **wtedy** skaner zamknięty, odmowa = zwykła (bez przycisku ustawień); **gdy** wynik = odmowa i `shouldShowRequestPermissionRationale` = `false`, **wtedy** odmowa = trwała (z przyciskiem ustawień).
- **Zakładając** odmowę trwałą, **gdy** zgłoszono `ON_RESUME`, a `CAMERA` jest przyznane, **wtedy** odmowa = brak.
- **Zakładając** brak `CAMERA` i odmowę trwałą, **gdy** dotknięto „Wklej URI” przy stanie `ABSENT`, **wtedy** pole wklejania jest otwarte.
- **Zakładając** telefon (W4), **gdy** odmówiono `CAMERA` dwa razy (albo „Nie pytaj ponownie”), **wtedy** widać przycisk „Otwórz ustawienia aplikacji”, który otwiera ekran informacji o aplikacji; po przyznaniu tam uprawnienia i powrocie tekst odmowy znika, a „Skanuj QR” otwiera skaner.

### UI-11: Wklejenie URI

„Wklej URI” otwiera okno z polem wklejania (jedna linia, znaki maskowane, klawiatura typu hasło – bez podpowiedzi i bez uczenia się słów) oraz przyciskami „Zapisz” i „Anuluj”. Tekst pola jest trzymany wyłącznie w stanie ViewModelu w pamięci (nie w `SavedStateHandle`, nie w `rememberSaveable`). „Zapisz” przekazuje tekst pola jako wejście sekretu (UI-12) i od razu czyści pole oraz zamyka okno, niezależnie od wyniku. „Anuluj” czyści pole i zamyka okno bez wywołania magazynu. „Zapisz” jest niedostępne, gdy pole jest puste po usunięciu znaków białych z obu końców.

**Kryteria akceptacji:**

- **Zakładając** otwarte pole wklejania z tekstem `URI_RFC` i magazyn zwracający `Saved`, **gdy** dotknięto „Zapisz”, **wtedy** magazyn dostał dokładnie jedno `save`, tekst pola = `""`, okno zamknięte.
- **Zakładając** otwarte pole z tekstem `otpauth://hotp/AAPS?secret=GEZDGNBV`, **gdy** dotknięto „Zapisz”, **wtedy** tekst pola = `""`, okno zamknięte, magazyn nie dostał `save`, komunikat = to nie jest kod TOTP z AAPS.
- **Zakładając** otwarte pole z tekstem `URI_RFC`, **gdy** dotknięto „Anuluj”, **wtedy** tekst pola = `""`, okno zamknięte, magazyn nie dostał `save`.
- **Zakładając** otwarte pole z tekstem `"   "`, **gdy** odczytano stan, **wtedy** „Zapisz” jest niedostępne.
- **Zakładając** konstruktor `TotpSecretViewModel`, **gdy** test sprawdził go refleksją, **wtedy** nie ma parametru typu `SavedStateHandle`; **zakładając** źródła `ui/settings/secret/`, **gdy** test JVM je przeczytał, **wtedy** nie zawierają `rememberSaveable` ani `SavedStateHandle`.
- **Zakładając** telefon z klawiaturą Gboard (W5), **gdy** wklejono URI w pole, **wtedy** znaki są maskowane, a pasek podpowiedzi klawiatury nie pokazuje fragmentów URI.

### UI-12: Odczyt i zapis sekretu

Wejście sekretu (ze skanera albo z pola wklejania) trafia do `OtpauthParser.parse`. `Success(secret)` → `TotpSecretStore.save(secret)` dokładnie raz; `Failure(error)` → komunikat według tabeli, bez wywołania magazynu. Wynik zapisu → komunikat według tabeli.

| Wynik | Komunikat |
| --- | --- |
| `Failure(NOT_OTPAUTH_URI \| MALFORMED_URI \| UNSUPPORTED_TYPE)` | „To nie jest kod QR TOTP z AAPS.” |
| `Failure(UNSUPPORTED_ALGORITHM \| UNSUPPORTED_DIGITS \| UNSUPPORTED_PERIOD)` | „Nieobsługiwane parametry TOTP. Wymagane: SHA1, 6 cyfr, 30 s.” |
| `Failure(MISSING_SECRET \| DUPLICATE_PARAMETER \| EMPTY_SECRET \| INVALID_BASE32)` | „Kod nie zawiera poprawnego sekretu TOTP.” |
| `SaveResult.Saved` | „Zapisano sekret TOTP” |
| `SaveResult.Locked` | „Odblokuj telefon i spróbuj ponownie.” |
| `SaveResult.Failed(KEY_GENERATION)` | „Nie udało się utworzyć klucza szyfrowania. Spróbuj ponownie.” |
| `SaveResult.Failed(CRYPTO \| STORAGE)` | „Nie udało się zapisać sekretu. Spróbuj ponownie.” |

Stan sekretu po zapisie pochodzi wyłącznie z `TotpSecretStore.state` (np. `INVALIDATED` po wyczyszczeniu w trakcie zapisu, SECRET-08, pokazuje treść „zeskanuj ponownie” z UI-07). Komunikaty nie zawierają żadnego fragmentu wejścia ani sekretu.

**Kryteria akceptacji:**

- **Zakładając** stan `ABSENT` i magazyn zwracający `Saved`, **gdy** przekazano wejście `URI_RFC`, **wtedy** magazyn dostał dokładnie jedno `save`, a z przekazanego `TotpSecret` `TotpGenerator` dla chwili 59000 ms daje kod `287082` (RFC 6238); komunikat = zapisano sekret.
- **Zakładając** każdą z 10 wartości `OtpauthError` (wejście dobrane tak, by parser zwrócił tę wartość), **gdy** przekazano wejście, **wtedy** magazyn nie dostał `save`, a komunikat odpowiada wierszowi tabeli.
- **Zakładając** magazyn zwracający kolejno `Locked`, `Failed(KEY_GENERATION)`, `Failed(CRYPTO)`, `Failed(STORAGE)`, **gdy** czterokrotnie przekazano `URI_RFC`, **wtedy** komunikaty odpowiadają wierszom tabeli.
- **Zakładając** komunikat dowolnego wyniku, **gdy** porównano go z wejściem, **wtedy** wartość komunikatu (wyliczenie) nie przenosi danych – jest jedną ze stałych.

### UI-13: Usunięcie sekretu z potwierdzeniem

„Usuń sekret” pokazuje okno „Usunąć sekret TOTP? Polecenia wymagające kodu (CARBS) nie będą działać do ponownego zeskanowania QR.” z przyciskami „Usuń” i „Anuluj”. Dopiero potwierdzenie wywołuje `TotpSecretStore.delete()` dokładnie raz. `Deleted` → komunikat „Usunięto sekret TOTP”; `Failed` → „Nie udało się usunąć sekretu. Spróbuj ponownie.” Anulowanie zamyka okno bez wywołania magazynu.

**Kryteria akceptacji:**

- **Zakładając** stan `PRESENT`, **gdy** dotknięto „Usuń sekret”, **wtedy** okno jest otwarte, a magazyn nie dostał `delete`; **gdy** potwierdzono przy magazynie zwracającym `Deleted`, **wtedy** magazyn dostał dokładnie jedno `delete()`, okno zamknięte, komunikat = usunięto sekret.
- **Zakładając** otwarte okno i magazyn zwracający `Failed`, **gdy** potwierdzono, **wtedy** komunikat = błąd usunięcia.
- **Zakładając** otwarte okno, **gdy** anulowano, **wtedy** okno zamknięte, magazyn nie dostał `delete`.

### UI-14: Sekret nie zostaje w ViewModelu

`TotpSecretViewModel` nie ma pól typu `TotpSecret`, `OtpauthResult`, `LoadResult`, `ByteArray` ani `CharArray`. Wejście ze skanera istnieje tylko jako parametr i zmienna lokalna wywołania. Jedynym miejscem tekstu wejścia w stanie jest tekst pola wklejania, czyszczony: przy „Zapisz” (przed wywołaniem parsera), przy „Anuluj”, gdy aplikacja przechodzi w tło albo ekran Ustawień jest zatrzymywany (`ON_STOP`, także przy obrocie ekranu i zmianie zakładki), oraz gdy zaczyna się polecenie (UI-15). Przy `ON_STOP` zamykane są też skaner i okno wklejania. Ograniczenie: `String` z wartością QR (ML Kit) i tekst pola nie dają się wyzerować w JVM; zostają w pamięci do odśmiecenia (jak SMS-13).

**Kryteria akceptacji:**

- **Zakładając** klasę `TotpSecretViewModel` i klasę jej stanu ekranu, **gdy** sprawdzono pola refleksją, **wtedy** żadne nie ma typu z listy.
- **Zakładając** atrapę magazynu zapamiętującą przekazany `TotpSecret`, **gdy** przekazano wejście `URI_RFC` ze skanera i zapis się zakończył, **wtedy** żadne pole ViewModelu ani jego stanu ekranu (refleksja, jeden poziom w głąb) nie jest referencją do tego `TotpSecret`, a `toString()` stanu nie zawiera `GEZDGNBV`.
- **Zakładając** otwarte pole wklejania z tekstem `URI_RFC` i magazyn, którego `save` zawiesza się do zwolnienia przez test, **gdy** dotknięto „Zapisz”, **wtedy** przed zwolnieniem zapisu tekst pola = `""`.
- **Zakładając** otwarte pole z tekstem `URI_RFC` i otwarty skaner (osobno), **gdy** zgłoszono `ON_STOP`, **wtedy** tekst pola = `""`, okno wklejania i skaner zamknięte, magazyn nie dostał `save`.

### Blokada w trakcie polecenia

### UI-15: Blokada zmian w trakcie polecenia

Gdy polecenie jest w toku, w sekcjach numeru i sekretu są niedostępne: „Ustaw numer”/„Zmień”, „Zapisz” edycji numeru, „Usuń” numeru, „Skanuj QR”, „Wklej URI”, „Usuń sekret”; nad sekcjami jest tekst „Trwa polecenie. Numer i sekret możesz zmienić po jego zakończeniu.” Gdy polecenie się zaczyna: okna potwierdzeń (usunięcie numeru, zastąpienie i usunięcie sekretu) są zamykane, skaner i okno wklejania zamykane, a tekst pola wklejania czyszczony; edycja numeru zostaje otwarta z tekstem, ale bez „Zapisz”. Każda akcja zmieniająca (zapis i usunięcie numeru, przyjęcie wejścia sekretu, usunięcie sekretu) sprawdza `activeSession.value` w chwili wywołania: polecenie w toku → komunikat „Trwa polecenie – spróbuj po jego zakończeniu.” i brak wywołania magazynu oraz parsera. Operacja już przekazana do magazynu przed startem polecenia kończy się normalnie (nie jest anulowana). Po zakończeniu polecenia akcje wracają według UI-04 i UI-07. Uprawnienia SMS i `CAMERA` nie są blokowane.

**Kryteria akceptacji:**

- **Zakładając** stan numeru `Configured(+447700900123)`, stan sekretu `PRESENT` i `activeSession` = sesja, **gdy** odczytano stany ekranów, **wtedy** obie sekcje mają akcje niedostępne i znacznik „polecenie w toku”.
- **Zakładając** otwarte okno usunięcia numeru, otwarte okno usunięcia sekretu i (osobno) otwarte pole wklejania z tekstem `URI_RFC`, **gdy** `activeSession` zmienił się z `null` na sesję, **wtedy** okna są zamknięte, pole wklejania zamknięte z tekstem `""`.
- **Zakładając** edycję numeru z tekstem `+44 7700 900123` i `activeSession` ustawione na sesję bez wykonania zadań dyspozytora (ViewModel nie zebrał jeszcze zmiany – wyścig), **gdy** wywołano „Zapisz”, **wtedy** magazyn nie dostał `save`, a komunikat = polecenie w toku; tak samo dla potwierdzenia usunięcia numeru (`clear`), wejścia ze skanera i potwierdzenia usunięcia sekretu (`save`/`delete`).
- **Zakładając** magazyn numeru, którego `save` zawiesza się do zwolnienia przez test, **gdy** po wywołaniu „Zapisz” `activeSession` zmienił się na sesję, a potem test zwolnił zapis z wynikiem `Saved`, **wtedy** komunikat = zapisano (operacja nie została anulowana).
- **Zakładając** `activeSession` = sesja, **gdy** zmienił się na `null`, **wtedy** akcje są dostępne według UI-04 i UI-07.
- **Zakładając** `activeSession` = sesja, **gdy** odczytano stan sekcji uprawnień SMS, **wtedy** „Zezwól” jest dostępne, gdy brakuje uprawnień.

### UI-16: Jedna operacja ustawień naraz w sekcji

W każdej z sekcji (numer, sekret) w danej chwili trwa najwyżej jedna operacja ustawień. Od jej wywołania do wyniku akcje zmieniające sekcji są niedostępne, a ich wywołanie (np. podwójne dotknięcie) jest ignorowane bez wywołania magazynu.

**Kryteria akceptacji:**

- **Zakładając** magazyn numeru, którego `save` zawiesza się do zwolnienia przez test, **gdy** dotknięto „Zapisz” dwa razy, a potem „Usuń” i potwierdzono, **wtedy** do zwolnienia magazyn dostał dokładnie jedno wywołanie (`save`), a stan ekranu ma znacznik trwającej operacji.
- **Zakładając** magazyn sekretu, którego `save` zawiesza się do zwolnienia, **gdy** przekazano wejście `URI_RFC` z pola wklejania, a potem otwarto skaner i przekazano wejście ze skanera, **wtedy** do zwolnienia magazyn dostał dokładnie jedno `save`.

### Uprawnienia SMS

### UI-17: Stan uprawnień SMS

Sekcja „Uprawnienia SMS” pokazuje stan dwóch uprawnień z `SmsPermissions` (`canSend()`, `canReceive()`): oba przyznane → „Wysyłanie i odbiór SMS dozwolone”; inaczej lista brakujących („wysyłanie SMS”, „odbiór SMS”) i przycisk „Zezwól”. Stan jest odczytywany przy utworzeniu ViewModelu, po każdym wyniku prośby o uprawnienia i przy każdym `ON_RESUME` ekranu (powrót do aplikacji, np. z ustawień systemu); ViewModel nie zapamiętuje stanu uprawnień poza ostatnim odczytem.

**Kryteria akceptacji:**

- **Zakładając** `SmsPermissions` zwracające `canSend` = `true`, `canReceive` = `false`, **gdy** utworzono ViewModel, **wtedy** stan ekranu: brakuje „odbiór SMS”, „Zezwól” dostępne.
- **Zakładając** oba przyznane, **gdy** utworzono ViewModel, **wtedy** stan: wszystko dozwolone, „Zezwól” niedostępne.
- **Zakładając** stan z brakiem obu uprawnień, **gdy** atrapa zaczęła zwracać `true` dla obu i zgłoszono `ON_RESUME`, **wtedy** stan: wszystko dozwolone.
- **Zakładając** utworzony ViewModel, **gdy** zgłoszono 3 razy `ON_RESUME`, **wtedy** `canSend()` i `canReceive()` wywołano po 4 razy (utworzenie + 3).

### UI-18: Prośba o uprawnienia SMS i odmowa

„Zezwól” prosi system jednym oknem o brakujące uprawnienia z `SmsPermissions.REQUIRED`, w tej kolejności (przyznanych nie ma na liście). Po wyniku: oba przyznane → bez komunikatu odmowy; inaczej tekst „Bez uprawnień SMS aplikacja nie wyśle polecenia ani nie odbierze odpowiedzi AAPS.”; przy trwałej odmowie (żadne brakujące uprawnienie nie ma `shouldShowRequestPermissionRationale` = `true`) dodatkowo przycisk „Otwórz ustawienia aplikacji” (systemowy ekran informacji o aplikacji). „Zezwól” zostaje dostępne także przy trwałej odmowie (system może zgłosić odmowę bez pokazania okna także w innych sytuacjach, np. po zamknięciu okna przyciskiem Wstecz).

**Kryteria akceptacji:**

- **Zakładając** brak obu uprawnień, **gdy** odczytano listę do prośby, **wtedy** to dokładnie `android.permission.SEND_SMS`, `android.permission.RECEIVE_SMS`; **zakładając** brak tylko `RECEIVE_SMS`, **wtedy** lista = `android.permission.RECEIVE_SMS`.
- **Zakładając** prośbę, po której `SmsPermissions` zwraca oba `true`, **gdy** zgłoszono wynik, **wtedy** odmowa = brak.
- **Zakładając** prośbę, po której brakuje `RECEIVE_SMS`, a `shouldShowRequestPermissionRationale(RECEIVE_SMS)` = `true`, **gdy** zgłoszono wynik, **wtedy** odmowa = zwykła (bez przycisku ustawień), „Zezwól” dostępne.
- **Zakładając** prośbę, po której brakuje obu, a `shouldShowRequestPermissionRationale` = `false` dla obu, **gdy** zgłoszono wynik, **wtedy** odmowa = trwała (z przyciskiem ustawień), „Zezwól” dostępne.
- **Zakładając** prośbę, po której brakuje obu, rationale `SEND_SMS` = `false`, `RECEIVE_SMS` = `true`, **gdy** zgłoszono wynik, **wtedy** odmowa = zwykła.
- **Zakładając** odmowę trwałą, **gdy** po `ON_RESUME` oba uprawnienia są przyznane, **wtedy** odmowa = brak.
- **Zakładając** telefon z aplikacją zainstalowaną z pliku APK (W7, także W6 ze Spec 005), **gdy** dotknięto „Zezwól”, odmówiono, a potem odmówiono trwale, **wtedy** widać tekst odmowy i przycisk ustawień; po przyznaniu w ustawieniach i powrocie sekcja pokazuje „Wysyłanie i odbiór SMS dozwolone”. Zanotować, czy Android pokazał okno uprawnień, czy wymagał włączenia ustawień z ograniczeniami.

### Moduł sesji i reguły wspólne

### UI-19: Jeden koordynator sesji w aplikacji (Hilt)

Moduł Hilt `session.platform.SessionModule` (`SingletonComponent`) dostarcza `UsableTotpProvider` (z `EpochClock` z `ClockModule`) i `SessionCoordinator` (z `SmsSender`, `IncomingSmsSource`, `EpochClock`, `UsableTotpProvider` z istniejących wiązań), oba jako `@Singleton`. W grafie Hilt jest dokładnie jedno wiązanie każdego z tych typów. Pakiety `session` i `otp` (logika) nie zmieniają się.

**Kryteria akceptacji:**

- **Zakładając** klasę `SessionModule`, **gdy** test JVM sprawdził refleksją metody dostarczające, **wtedy** metoda dla `SessionCoordinator` i metoda dla `UsableTotpProvider` mają adnotacje `@Provides` i `@Singleton`.
- **Zakładając** atrapy `SmsSender`, `IncomingSmsSource` i zegar, **gdy** test wywołał metody modułu, **wtedy** powstał koordynator z `activeSession.value == null`, który uruchamia polecenie `BG` (`StartResult.Started`), a jego `send` trafia do atrapy `SmsSender`.
- **Zakładając** zbudowaną aplikację (`assembleDebug`), **gdy** Hilt wygenerował graf, **wtedy** `SessionCoordinator` i `UsableTotpProvider` mają po jednym wiązaniu, w `session.platform.SessionModule`, a `AapsNumberViewModel` i `TotpSecretViewModel` wstrzykują ten sam `SessionCoordinator` (`SmsPermissionsViewModel` nie zależy od sesji).

### UI-20: Teksty po polsku w zasobach

Wszystkie teksty widoczne w UI z tej specyfikacji (etykiety zakładek, zaślepki, treści sekcji, przyciski, okna, komunikaty) są w `app/src/main/res/values/strings.xml`, po polsku, pod kluczami z tabeli tekstów w design; treści podane w tej spec są wiążące (drobne zmiany redakcyjne w recenzji PR nie wymagają zmiany spec). ViewModele nie zwracają tekstów, tylko wartości wyliczeniowe; odwzorowanie wartość → zasób tekstowy jest jedną funkcją na typ komunikatu. Kod Compose nie zawiera literałów tekstowych widocznych dla użytkownika.

**Kryteria akceptacji:**

- **Zakładając** każde wyliczenie komunikatów (`NumberMessage`, `SecretMessage`), **gdy** test JVM odwzorował każdą wartość na zasób, **wtedy** każdy identyfikator jest niezerowy, a identyfikatory w obrębie typu są różne.
- **Zakładając** `strings.xml`, **gdy** test JVM go przeczytał, **wtedy** zawiera niepusty tekst dla każdego klucza z tabeli tekstów w design.
- **Zakładając** ekrany na telefonie (W1–W7), **gdy** przejrzano wszystkie stany z tej specyfikacji, **wtedy** każdy tekst jest po polsku.

### UI-21: Brak ujawniania danych

Pakiety `ui` i `session.platform` nie wywołują API logowania (`android.util.Log`, `println`, `System.out`/`err`, loggerów). `toString()` stanów ekranów i typów wyświetlania numeru nie zawiera numeru ani tekstu pola wklejania (stały tekst `***` w miejscu wartości). Wejście sekretu, sekret i numer nie trafiają do komunikatów ani wyjątków.

**Kryteria akceptacji:**

- **Zakładając** źródła `ui/` i `session/platform/`, **gdy** test JVM je przeczytał, **wtedy** nie zawierają wywołań API logowania (wzorce jak w `DataNoLoggingTest`).
- **Zakładając** stan ekranu numeru z wyświetlaniem `Configured` dla `+447700900123` i edycją z tekstem `+44 7700 900999`, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `900123`, `900999` ani `7700`.
- **Zakładając** stan ekranu sekretu z tekstem pola `URI_RFC`, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera `otpauth`, `GEZDGNBV` ani `secret=`.

### UI-22: Manifest i zależności

`AndroidManifest.xml` deklaruje `uses-permission` `android.permission.CAMERA` oraz `uses-feature` `android.hardware.camera.any` z `android:required="false"` (instalacja na telefonie bez aparatu; zostaje wklejenie URI). Wymagania SMS-15 pozostają spełnione (bez nowych `<receiver>`/`<service>` w manifeście aplikacji, `allowBackup="false"`). Aplikacja nie ma uprawnień sieciowych: manifest aplikacji (z przestrzenią nazw `xmlns:tools`) zawiera `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />` i to samo dla `android.permission.ACCESS_NETWORK_STATE`, więc uprawnienia dołączane przez zależności ML Kit (`com.google.android.datatransport`) nie trafiają do połączonego manifestu; skan QR działa offline (UI-09), a telemetria ML Kit nie jest wysyłana. Nowe biblioteki i wersje – tabela w design; nie są dodawane biblioteki ikon (ikony zakładek to zasoby wektorowe w `res/drawable`).

**Kryteria akceptacji:**

- **Zakładając** `app/src/main/AndroidManifest.xml`, **gdy** test JVM go przeczytał, **wtedy** zawiera `uses-permission` `android.permission.CAMERA`, `uses-feature` `android.hardware.camera.any` z `required` = `false`, `allowBackup` = `false`, i nie zawiera `<receiver>` ani `<service>`.
- **Zakładając** `app/src/main/AndroidManifest.xml`, **gdy** test JVM go przeczytał, **wtedy** zawiera `uses-permission` `android.permission.INTERNET` i `android.permission.ACCESS_NETWORK_STATE`, każde z atrybutem `tools:node` = `remove` (przestrzeń nazw `http://schemas.android.com/tools`), i nie zawiera żadnego z nich bez tego atrybutu.
- **Zakładając** `gradle/libs.versions.toml`, **gdy** test JVM go przeczytał, **wtedy** artefakt ML Kit to `com.google.mlkit:barcode-scanning` (model w paczce), a nie `com.google.android.gms:play-services-mlkit-barcode-scanning` (model pobierany przez Usługi Google Play).
- **Zakładając** zbudowany APK debug (W8), **gdy** wypisano uprawnienia (`aapt2 dump permissions` albo „Merged Manifest” w Android Studio), **wtedy** lista zawiera `SEND_SMS`, `RECEIVE_SMS` i `CAMERA`, a nie zawiera `INTERNET` ani `ACCESS_NETWORK_STATE`; skan QR w trybie samolotowym działa (W3).

### UI-23: Ochrona ekranu z sekretem

Gdy otwarty jest skaner albo okno wklejania, okno `Activity` ma `WindowManager.LayoutParams.FLAG_SECURE` (brak zrzutów ekranu i podglądu w ostatnich aplikacjach); po zamknięciu skanera i okna flaga jest zdejmowana.

**Kryteria akceptacji:**

- **Zakładając** stan ekranu sekretu, **gdy** otwarto skaner, potem go zamknięto, potem otwarto okno wklejania i je zamknięto, **wtedy** znacznik „ekran chroniony” w stanie ma kolejno wartości `true`, `false`, `true`, `false`.
- **Zakładając** otwarty skaner na telefonie (W9), **gdy** wykonano zrzut ekranu i otwarto listę ostatnich aplikacji, **wtedy** zrzut się nie wykonuje, a podgląd aplikacji jest pusty; po zamknięciu skanera zrzut działa.

### UI-24: Czyszczenie schowka po wklejeniu URI

Przy zamknięciu okna wklejania – „Zapisz”, „Anuluj” albo `ON_STOP` z otwartym oknem – ViewModel bierze tekst pola sprzed jego wyczyszczenia i, gdy jest niepusty, porównuje go z bieżącym tekstem schowka systemowego (port `SecretClipboard`). Dokładna zgodność (`==`, bez przycinania znaków białych) → schowek jest czyszczony (`ClipboardManager.clearPrimaryClip()`, API 28+). Inny tekst, pusty schowek, brak dostępu do schowka albo pusty tekst pola → schowek bez zmian. Czyszczenie przy „Zapisz” odbywa się niezależnie od wyniku parsowania i zapisu. Porównanie jest jedynym użyciem treści schowka: nie trafia ona do stanu ViewModelu, logów, komunikatów ani wyjątków; wyjątki portu są połykane.

**Kryteria akceptacji:**

- **Zakładając** okno wklejania z tekstem `URI_RFC` i schowek z `URI_RFC`, **gdy** dotknięto „Zapisz” (magazyn zwraca `Saved`), **wtedy** schowek wyczyszczono dokładnie raz; tak samo przy „Anuluj” i przy `ON_STOP` z otwartym oknem.
- **Zakładając** okno z tekstem `URI_RFC` i schowek z tekstem `otpauth://totp/INNE?secret=GEZDGNBV`, **gdy** dotknięto „Zapisz”, **wtedy** schowek nie był czyszczony.
- **Zakładając** okno z tekstem `URI_RFC` i schowek z `URI_RFC` poprzedzonym spacją, **gdy** dotknięto „Anuluj”, **wtedy** schowek nie był czyszczony.
- **Zakładając** okno z tekstem `otpauth://hotp/AAPS?secret=GEZDGNBV` i schowek z tym samym tekstem, **gdy** dotknięto „Zapisz” (parser zwraca `Failure`), **wtedy** schowek wyczyszczono.
- **Zakładając** schowek zwracający `null` (brak dostępu) albo port rzucający wyjątek przy odczycie, **gdy** dotknięto „Zapisz” z tekstem `URI_RFC`, **wtedy** schowek nie był czyszczony, bez wyjątku na zewnątrz.
- **Zakładając** okno wklejania zamknięte (skaner otwarty, osobno: nic nie otwarte) i schowek z `URI_RFC`, **gdy** zgłoszono `ON_STOP`, **wtedy** schowek nie był czytany ani czyszczony.
- **Zakładając** pole z pustym tekstem, **gdy** dotknięto „Anuluj”, **wtedy** schowek nie był czytany.
- **Zakładając** telefon (W10), **gdy** skopiowano `URI_RFC`, wklejono w pole i dotknięto „Zapisz”, **wtedy** schowek systemowy jest pusty; po skopiowaniu innego tekstu i „Anuluj” z pustym polem schowek zawiera ten tekst. Zanotować zachowanie przy `ON_STOP` (API 29+: brak dostępu do schowka bez fokusu) i powiadomienie systemu o odczycie schowka (API 31+).

## Przypadki błędów

| Sytuacja | Oczekiwane zachowanie | Wymaganie |
| --- | --- | --- |
| Numer nieprawidłowy (np. `70090012`, pusty) | błąd pod polem, edycja otwarta, tekst zachowany | UI-05 |
| Nieudane `save()` / `clear()` numeru (wynik `SaveNumberResult.Failed` / `ClearNumberResult.Failed` z kontraktu `AapsNumberSettingsStore`) | komunikat „Nie udało się zapisać/usunąć numeru”, stan bez zmian (DATA-03/DATA-04) | UI-05, UI-06 |
| Numer nie wczytał się (`LoadFailed`) | tekst błędu wczytania, „Ustaw numer” | UI-04 |
| Kod QR / URI nie jest `otpauth://totp` | komunikat „To nie jest kod QR TOTP z AAPS”, brak zapisu | UI-12 |
| URI z innym algorytmem / cyframi / okresem | komunikat o nieobsługiwanych parametrach, brak zapisu | UI-12 |
| URI bez sekretu / z błędnym Base32 | komunikat o braku poprawnego sekretu, brak zapisu | UI-12 |
| Zapis sekretu przy zablokowanym telefonie | „Odblokuj telefon i spróbuj ponownie”, nic nie usunięte (SECRET-09) | UI-12 |
| Błąd generacji klucza / szyfrowania / zapisu | komunikat błędu zapisu, stan z magazynu | UI-12 |
| Sekret unieważniony (`INVALIDATED`) | treść „zeskanuj ponownie kod QR z AAPS”, zapis bez pytania o zastąpienie | UI-07, UI-08 |
| Usunięcie sekretu nieudane | komunikat „Nie udało się usunąć sekretu” | UI-13 |
| Odmowa `CAMERA` | tekst odmowy, „Wklej URI” działa; trwała odmowa → przycisk ustawień | UI-10 |
| Brak aparatu / błąd CameraX | skaner zamknięty, komunikat o aparacie, „Wklej URI” działa | UI-09, UI-22 |
| Kilka rozpoznań QR z rzędu | jeden zapis, skaner zamknięty po pierwszym | UI-09 |
| Odmowa `SEND_SMS`/`RECEIVE_SMS` | tekst odmowy; trwała odmowa → przycisk ustawień; odświeżenie po powrocie | UI-17, UI-18 |
| Polecenie zaczyna się w trakcie edycji / okna | okna zamknięte, pole wklejania wyczyszczone, akcje niedostępne | UI-15 |
| Akcja zmieniająca w chwili startu polecenia (wyścig) | komunikat „Trwa polecenie”, brak wywołania magazynu | UI-15 |
| Podwójne dotknięcie „Zapisz” | jedno wywołanie magazynu | UI-16 |
| Aplikacja w tle z otwartym polem wklejania / skanerem | pole wyczyszczone, okno i skaner zamknięte | UI-14 |
| Schowek zawiera inny tekst niż wklejony albo brak dostępu do schowka | schowek bez zmian, bez wyjątku | UI-24 |

## Bezpieczeństwo

Zasady z [AGENTS.md](../../../AGENTS.md#bezpieczeństwo) i sposób ich spełnienia:

- **Nie logujemy sekretu, kodów ani PIN-u.** `ui` i `session.platform` nie wywołują API logowania (UI-21). Wejście sekretu nie trafia do komunikatów, wyjątków ani `toString()` (UI-12, UI-21). PIN AAPS nie występuje w tej specyfikacji (009).
- **Sekret nie zostaje w pamięci UI dłużej niż do zapisu.** ViewModel nie ma pól z sekretem; wejście ze skanera jest zmienną lokalną; tekst pola wklejania jest czyszczony przy zapisie (przed parserem), anulowaniu, przejściu w tło i starcie polecenia; nie trafia do `SavedStateHandle` ani `rememberSaveable`, więc nie jest zapisywany przez system w stanie instancji (UI-11, UI-14). Pytanie o zastąpienie jest zadawane przed odczytem wejścia (UI-08). Pole jest maskowane, z klawiaturą typu hasło, żeby słownik klawiatury nie uczył się fragmentów URI (UI-11). Ograniczenie: obiekty `String` (wartość QR, tekst pola) nie dają się wyzerować w JVM.
- **Kopie poza kontrolą aplikacji.** Aplikacja czyta schowek tylko po to, by porównać go z wklejonym tekstem przy zamknięciu okna wklejania, i czyści go wyłącznie przy dokładnej zgodności (UI-24); treść schowka nie jest zapisywana ani logowana. Gdy system nie da dostępu do schowka (np. aplikacja bez fokusu przy `ON_STOP`, API 29+), URI zostaje w schowku – poza kontrolą aplikacji. Kod QR na ekranie AAPS jest poza aplikacją. Ochrona przed zrzutem ekranu skanera/okna wklejania – `FLAG_SECURE` na czas otwartego skanera i okna wklejania (UI-23).
- **Skan offline, minimum danych.** Model ML Kit jest w paczce aplikacji (UI-22), analiza klatek odbywa się na telefonie, klatki nie są zapisywane (UI-09). Biblioteki ML Kit dołączają moduł telemetrii Google (`com.google.android.datatransport`) z uprawnieniami `INTERNET` i `ACCESS_NETWORK_STATE` w połączonym manifeście; aplikacja usuwa oba uprawnienia (`tools:node="remove"`, UI-22), więc nie ma dostępu do sieci, a telemetria nie jest wysyłana.
- **Zmiana numeru w trakcie polecenia** (Spec 007, sekcja Bezpieczeństwo): zmiana i usunięcie numeru są niedostępne i sprawdzane w chwili wywołania, gdy polecenie jest w toku (UI-15). Pozostałe okno wyścigu: zapis numeru przekazany do magazynu tuż przed startem polecenia kończy się już w trakcie polecenia. Skutek jest bezpieczny: `sms` czyta numer przy każdym wysłaniu i odbiorze (SMS-04, SMS-09), więc odpowiedzi ze starego numeru są odrzucane przez filtr (brak odpowiedzi → limit czasu sesji), a kod jest odsyłany tylko po prośbie o kod z bieżącego numeru. Pełne rozwiązanie (numer ustalany raz na polecenie) – „Przyszłe zmiany” w Spec 007.
- **Blokada zmiany sekretu w trakcie polecenia** (UI-15) nie jest wymagana dla poprawności – sesja dostaje własną kopię sekretu w `CodeCredentials` przed startem (Spec 006), więc zapis ani usunięcie sekretu w magazynie nie wpływa na trwające polecenie. Powody blokady: jedna, prosta reguła „w trakcie polecenia numer i sekret są tylko do odczytu”; brak wątpliwości użytkownika, którym sekretem zostało potwierdzone polecenie; brak uruchamiania aparatu i okien uprawnień, gdy aplikacja ma pozostać na pierwszym planie z ekranem przebiegu (Spec 005, założenie MVP).
- **Minimum uprawnień:** dochodzi tylko `CAMERA` (UI-22); bez `READ_SMS`, bez dostępu do pamięci i galerii. `allowBackup=false` bez zmian.
- **Brak prawdziwych danych:** numery z zakresu fikcyjnego Ofcom, sekret wyłącznie z RFC 6238.

## Dane testowe

- Numery fikcyjne: `+44 7700 900123`, `+44 7700 900999` (zakres Ofcom „drama numbers”, jak w Spec 005 i 007).
- Sekret RFC 6238 (`12345678901234567890`, Base32 `GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ`), oczekiwany kod `287082` dla chwili 59000 ms (Spec 001).
- `URI_RFC` (sekcja Wymagania) i URI błędne, po jednym na każdą wartość `OtpauthError` (np. `https://example.com`, `otpauth://hotp/AAPS?secret=GEZDGNBV`, `otpauth://totp/AAPS?secret=GEZDGNBV&algorithm=SHA256`, `otpauth://totp/AAPS?issuer=AAPS`) – wartości z istniejących testów `OtpauthParserTest`.
- Do W3: kod QR z `URI_RFC` wygenerowany na komputerze dowolnym generatorem offline i pokazany na ekranie (nigdy prawdziwy sekret z AAPS w repo ani w opisie PR). Do W3 dodatkowo dowolny kod EAN-13 (np. z opakowania).
- Brak plików w `dane/`.

## Weryfikacja na urządzeniu

Kompozycje, nawigacja, kamera, okna uprawnień i wiązania Hilt nie mają testów automatycznych (decyzja użytkownika: tylko JVM). Wyniki (telefon, wersja Androida, wynik każdego punktu) opisuje PR zadania, które wprowadza dany ekran (design, „Podział na zadania”). Wynik niezgodny ze spec = zmiana spec, nie obejście w kodzie.

| Nr | Co sprawdzić | Wymagania |
| --- | --- | --- |
| W1 | Zimny start: zakładka Polecenia zaznaczona, zaślepka Polecenia. Przełączanie zakładek, ponowne dotknięcie zaznaczonej, Wstecz z Historii i Ustawień → Polecenia, Wstecz z Polecenia zamyka aplikację; obrót ekranu zachowuje zakładkę; zaślepki Historia i Polecenia; teksty po polsku | UI-01, UI-02, UI-03, UI-20 |
| W2 | Numer: „Ustaw numer” z `+44 7700 900123` → wyświetla `+447700900123`; `70090012` → błąd pod polem; „Zmień” wypełnia pole; „Usuń” z potwierdzeniem i anulowaniem; zabicie procesu (`adb shell am kill`) i ponowny start → ten sam numer | UI-04, UI-05, UI-06 |
| W3 | Skan QR (tryb samolotowy): kod z `URI_RFC` → skaner zamyka się, „Sekret TOTP zapisany”; drugi skan przy zapisanym sekrecie → pytanie o zastąpienie przed otwarciem skanera; kod EAN-13 nie zamyka skanera; „Anuluj” i Wstecz zamykają skaner; obrót ekranu / przejście w tło zamyka skaner | UI-08, UI-09, UI-14 |
| W4 | `CAMERA`: pierwsza prośba, odmowa → tekst odmowy, „Wklej URI” działa; trwała odmowa → „Otwórz ustawienia aplikacji” otwiera ekran informacji o aplikacji; przyznanie tam i powrót → tekst znika, skaner działa. Telefon/emulator bez aparatu albo aparat zajęty przez inną aplikację → komunikat o aparacie | UI-09, UI-10 |
| W5 | Wklejenie URI: znaki maskowane, Gboard bez podpowiedzi z fragmentami URI; „Zapisz” z `URI_RFC` → zapisano, pole puste przy ponownym otwarciu; błędny URI → komunikat; „Anuluj” → pole puste; przejście do innej aplikacji i powrót → okno zamknięte | UI-11, UI-12, UI-14 |
| W6 | Usunięcie sekretu z potwierdzeniem i anulowaniem → „Brak sekretu TOTP”; po ponownym starcie procesu stan sekretu bez zmian | UI-07, UI-13 |
| W7 | Uprawnienia SMS na aplikacji instalowanej z APK: prośba, odmowa, trwała odmowa, przycisk ustawień, powrót i odświeżenie; zanotować, czy Android wymagał „ustawień z ograniczeniami” (to samo co W6 w Spec 005) | UI-17, UI-18 |
| W8 | Uprawnienia w zbudowanym APK debug (`aapt2 dump permissions` albo „Merged Manifest”): są `SEND_SMS`, `RECEIVE_SMS`, `CAMERA`; brak `INTERNET` i `ACCESS_NETWORK_STATE` (połączony manifest bez uprawnień sieciowych); w ustawieniach systemu „Informacje o aplikacji” nie pokazują dostępu do sieci | UI-22 |
| W9 | `FLAG_SECURE` przy otwartym skanerze i oknie wklejania: zrzut ekranu zablokowany, podgląd w ostatnich aplikacjach pusty; po zamknięciu zrzut działa | UI-23 |
| W10 | Schowek: skopiowanie `URI_RFC`, wklejenie i „Zapisz” → schowek pusty; skopiowanie innego tekstu, otwarcie i „Anuluj” z pustym polem → schowek bez zmian; przejście w tło z otwartym oknem po wklejeniu `URI_RFC` → na API 28 schowek pusty (wynik wymagany); na API 29+ schowek pusty albo bez zmian, gdy system nie dał dostępu bez fokusu (oba wyniki zaliczają, zanotować który) – nigdy inna treść niż przed testem; klip z dwoma elementami (np. z aplikacji udostępniającej wiele tekstów), z których pierwszy to wklejony URI → schowek bez zmian; zanotować powiadomienie systemu o odczycie schowka (API 31+) | UI-24 |

Blokady w trakcie polecenia (UI-15) nie da się sprawdzić na telefonie bez ekranu Polecenia – ich sprawdzenie ręczne opisuje pierwszy PR 009, który uruchamia polecenie.

**Punkty weryfikacji z wcześniejszych specyfikacji** (podział zatwierdzony przez użytkownika – P3):

- **Spec 006 (W1–W10)** – opisuje PR zadania 9 z design (pierwszy zapis sekretu – z wklejonego URI); PR zadania 10 (skaner) powtarza W5–W7 dla zapisu ze skanera. W 008 nie ma odczytu sekretu (`load`), więc PR opisuje: W1 w części zapisu i parametrów `KeyInfo`, W5, W6, W7, W8. Punkty wymagające odczytu – W1 w części odczytu i zgodności kodu z Authenticatorem, W2, W3, W4, W9, W10 – opisuje pierwszy PR 009 z poleceniem wymagającym kodu.
- **Spec 007 (W1–W7)** – PR zadania 8 z design (sekcja numeru) opisuje punkty dotyczące numeru: W1 bez części „polecenie `BG` wychodzi na ten numer”, W2, W6 w części numeru (`bmgr backupnow` przy zapisanym numerze). Do 009: W1 w części polecenia `BG` i W7 (przyciski poleceń przy wczytywaniu). Do 010: W3, W4, W5 i W6 w części historii.
- **Spec 005 (W1–W7)** – 009 (pierwszy PR uruchamiający wysyłanie i odbiór); wyjątek: W6 (przyznanie uprawnień SMS aplikacji z APK) sprawdza W7 tej specyfikacji.

## Otwarte pytania

- [x] **P1. Uprawnienia sieciowe z ML Kit.** `com.google.mlkit:barcode-scanning:17.3.0` zależy od `com.google.mlkit:common`, a ta od `com.google.android.datatransport:transport-backend-cct` i `transport-runtime`, których manifesty dodają do połączonego manifestu `INTERNET` i `ACCESS_NETWORK_STATE` (telemetria ML Kit). Rozstrzygnięte: oba uprawnienia są usuwane z połączonego manifestu przez `tools:node="remove"` w manifeście aplikacji – skan offline, aplikacja bez dostępu do sieci (UI-22, W8).
- [x] **P2. `FLAG_SECURE` przy skanerze i oknie wklejania.** Rozstrzygnięte: tak – `FLAG_SECURE` tylko na czas otwartego skanera i okna wklejania (UI-23, W9).
- [x] **P3. Podział ręcznej weryfikacji z wcześniejszych spec.** Rozstrzygnięte: zgoda na podział z sekcji „Punkty weryfikacji z wcześniejszych specyfikacji” – Spec 006: W1 (zapis, `KeyInfo`) i W5–W8 w 008, reszta w 009; Spec 007: W1 bez `BG`, W2 i W6 (numer) w 008, W1 (`BG`) i W7 w 009, W3–W5 i W6 (historia) w 010; Spec 005: W6 w 008, reszta w 009.
- [x] Nawigacja (dolny pasek, start na Poleceniach), testy tylko JVM, CameraX + ML Kit z modelem w paczce i zapasowe wklejenie URI, podział `ui` na 008/009/010 – decyzje użytkownika (sekcje Zakres i Założenia).

## Przyszłe zmiany (nie blokują)

- **Podpowiedź o blokadzie ekranu**, jeśli W7 ze Spec 006 pokaże, że generacja klucza z `setUnlockedDeviceRequired(true)` nie udaje się bez blokady ekranu: komunikat dla `Failed(KEY_GENERATION)` „Ustaw blokadę ekranu i spróbuj ponownie” (zmiana UI-12).
- **Podpowiedź o ustawieniach z ograniczeniami**, jeśli W7 tej spec (W6 Spec 005) pokaże, że Android blokuje prośbę o uprawnienia SMS dla aplikacji z APK: dodatkowy tekst przy trwałej odmowie (zmiana UI-18).
- **Utrwalenie stanu `INVALIDATED`** po restarcie procesu (Spec 006, „Przyszłe zmiany”), jeśli użytkownicy nie zrozumieją, czemu po restarcie widzą „Brak sekretu”.
