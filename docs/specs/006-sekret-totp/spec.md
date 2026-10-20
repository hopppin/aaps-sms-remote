# Spec 006: Przechowywanie sekretu TOTP (Keystore)

- **Status:** zatwierdzona
- **Prefiks wymagań:** `SECRET`
- **Pakiety:** `security` (logika czysta), `security.platform` (cienka warstwa Androida)
- **Powiązane:** [docs/plan.md](../../plan.md#bezpieczeństwo); [Spec 001: Generator TOTP](../001-totp/spec.md) (`TotpSecret`, `OtpauthParser`, TOTP-09); [Spec 004: Sesja polecenia](../004-sesja-polecenia/spec.md) (`CodeCredentials`, SESSION-02); [Spec 005: SMS](../005-sms/spec.md) (wzorzec podziału logika czysta / `platform`, decyzja P3); [stan prac – ustalenia](../../stan-prac.md#ustalenia-z-użytkownikiem-decyzje); przyszłe specyfikacje `ui` (skan QR, komunikaty, złożenie `CodeCredentials`)

## Cel

Sekret TOTP z kodu QR AAPS jest w aplikacji jedynym czynnikiem „co masz” przy potwierdzaniu poleceń. Ta specyfikacja opisuje, jak go zapisać zaszyfrowanego kluczem z Android Keystore, odczytać tylko na czas polecenia, zastąpić nowym, usunąć i jak reagować na utratę klucza – tak, by kradzież pliku z danymi aplikacji nic nie dawała, a przejściowa blokada telefonu nigdy nie kasowała sekretu.

## Zakres

- Klucz AES-256-GCM w Android Keystore: parametry generacji, StrongBox z przejściem na TEE, usuwanie.
- Format zapisanego szyfrogramu (wersja, IV, szyfrogram z tagiem) i jego walidacja.
- Operacje magazynu sekretu: zapis (także zamiana starego sekretu), odczyt na żądanie, jawne usunięcie, stan obserwowalny bez odszyfrowania.
- Klasyfikacja błędów kryptograficznych: telefon zablokowany (przejściowy), klucz utracony lub unieważniony, uszkodzony szyfrogram, inny błąd – i reakcja na każdy.
- Cykl życia odszyfrowanych bajtów w pamięci, brak ujawniania danych, serializacja operacji, wątek I/O.
- Magazyn klucz-wartość na `SharedPreferences` i sprawdzenie, że plik nie trafia do kopii zapasowej ani transferu urządzenia.
- Wiązania Hilt.
- Podział na logikę czystą (testowalną w JVM) i cienką warstwę Androida.

**Poza zakresem:**

- UI skanowania QR, ekran ustawień, pytanie „zastąpić istniejący sekret?”, treści komunikatów – spec `ui`.
- Złożenie `CodeCredentials` i blokowanie przycisków poleceń wymagających kodu – `ui` na podstawie wyników tej spec.
- Pakiet `data` (numer AAPS, historia).
- Biometria, uwierzytelnienie użytkownika przy użyciu klucza (`setUserAuthenticationRequired`), własny PIN aplikacji.
- Wiele sekretów / profili (jeden telefon z AAPS = jeden sekret).
- Kopia zapasowa, eksport i import sekretu, przenoszenie na inny telefon.
- Ochrona przed rootem, wykrywanie modyfikacji systemu, atestacja klucza.
- Zerowanie wewnętrznej tablicy `TotpSecret` po zakończeniu polecenia (wymaga zmiany Spec 001 i Spec 004 – sekcja „Przyszłe zmiany”).
- Zmiana `CodeCredentials`, `TotpSecret`, `OtpauthParser`, `AapsPin` – bez zmian.

## Założenia i zależności

- **Wejście zapisu:** `TotpSecret` z `OtpauthResult.Success` (Spec 001). Magazyn czyta bajty wyłącznie przez `TotpSecret.copyBytes()` i zeruje tę kopię.
- **Wyjście odczytu:** `TotpSecret` utworzony przez `TotpSecret.fromBytes`; `ui` (ViewModel) składa z niego i z `AapsPin` obiekt `CodeCredentials` (Spec 004) i przekazuje do `SessionCoordinator`. Sekret odszyfrowany przed startem sesji zostaje w pamięci sesji do jej końca, także gdy ekran zostanie potem zablokowany.
- **Wymaganie dla spec `ui`:** sekret jest odczytywany tuż przed startem polecenia wymagającego kodu (po wpisaniu PIN-u i zatwierdzeniu), a ViewModel nie przechowuje `TotpSecret` w polu dłużej niż do przekazania `CodeCredentials` koordynatorowi (SECRET-14).
- **Aplikacja nie działa w trybie Direct Boot** (brak `directBootAware`), więc magazyn jest używany dopiero po pierwszym odblokowaniu telefonu od uruchomienia.
- **Testy:** logika czysta w testach JVM (JUnit 5), w tym prawdziwe AES-GCM z JCE z kluczem programowym. Cienka warstwa Androida bez Robolectric i `androidTest` (jak decyzja P3 ze Spec 005), sprawdzana ręcznie według listy W1–W10 (sekcja „Weryfikacja na urządzeniu”), gdy powstanie `ui`.

## Pojęcia

- **Klucz** – klucz AES w Android Keystore pod stałym aliasem (SECRET-01); nieeksportowalny, dostępny w aplikacji tylko jako uchwyt.
- **Rekord** – tekst zapisany w magazynie klucz-wartość: Base64 z wersji formatu, IV i szyfrogramu z tagiem (SECRET-03). Najwyżej jeden.
- **Magazyn klucz-wartość** – port z trzema operacjami: odczyt rekordu, zapis rekordu, usunięcie rekordu; implementacja na `SharedPreferences` (SECRET-16).
- **Szyfrator** – logika AES-GCM nad kluczem (SECRET-03, SECRET-07); w testach JVM działa z kluczem programowym JCE.
- **Środowisko Keystore** – port platformy: czy telefon jest teraz zablokowany (`KeyguardManager.isDeviceLocked`) i rozpoznanie typów wyjątków Androida (`KeyPermanentlyInvalidatedException`, `UserNotAuthenticatedException`).
- **Sklasyfikowany błąd** – jedna z wartości: `LOCKED` (telefon zablokowany), `KEY_INVALIDATED` (klucz brak albo trwale unieważniony), `CORRUPTED` (szyfrogram nie przechodzi weryfikacji tagu), `KEY_GENERATION_FAILED`, `UNAVAILABLE` (każdy inny błąd).
- **Wyczyszczenie** – usunięcie rekordu, a potem klucza, po trwałym błędzie (SECRET-08); nie mylić z jawnym usunięciem (SECRET-11).
- **Stan sekretu** – `UNKNOWN` (jeszcze nie sprawdzono), `ABSENT`, `PRESENT`, `INVALIDATED` (wyczyszczono po trwałym błędzie, do zapisu nowego albo jawnego usunięcia).
- **Sekret S1** – 20 bajtów ASCII `12345678901234567890` (ziarno SHA-1 z RFC 6238, dodatek B). **Sekret S2** – 20 bajtów `0x00`…`0x13` (fikcyjne).
- **Klucz testowy K** – programowy klucz AES-256 JCE z bajtów `0x00`…`0x1F` (tylko testy JVM, fikcyjny).

## Wymagania

Wspólne dane kryteriów (o ile kryterium nie mówi inaczej): telefon odblokowany (`isDeviceLocked() == false`), magazyn klucz-wartość pusty, brak klucza. Kryteria logiki czystej używają atrap: magazynu klucz-wartość w pamięci (z możliwością wymuszenia `false` albo wyjątku), źródła klucza z kluczem K, środowiska Keystore z przełączaną blokadą i rozpoznawaniem wyjątków testowych, a dla magazynu sekretu – atrapy szyfratora zwracającej zadane wyniki (design).

### SECRET-01: Klucz w Android Keystore

Klucz jest generowany w aplikacji w dostawcy `AndroidKeyStore` pod aliasem `pl.twojanazwa.aapsremote.totp-secret-key` z parametrami: algorytm AES, rozmiar 256 bitów, cele `ENCRYPT` i `DECRYPT`, tryb blokowy GCM, dopełnienie `NONE`, `setRandomizedEncryptionRequired(true)`, `setUserAuthenticationRequired(false)`, `setUnlockedDeviceRequired(true)`. Klucz nie jest importowany ani eksportowany; aplikacja nie odczytuje jego bajtów. Klucz jest generowany tylko wtedy, gdy zapis (SECRET-04) nie znajdzie istniejącego klucza; odczyt (SECRET-06) nigdy nie generuje klucza.

**Kryteria akceptacji:**

- **Zakładając** brak klucza, **gdy** zapisano S1, **wtedy** źródło klucza wygenerowało klucz dokładnie raz.
- **Zakładając** istniejący klucz, **gdy** zapisano S2, **wtedy** źródło klucza nie wygenerowało nowego klucza.
- **Zakładając** brak klucza i istniejący rekord, **gdy** wywołano odczyt, **wtedy** źródło klucza nie wygenerowało klucza.
- **Zakładając** telefon z Androidem 9 (API 28) i z najnowszym dostępnym API (sprawdzenie na urządzeniu, W1), **gdy** zapisano i odczytano sekret, **wtedy** odczytany sekret daje ten sam kod TOTP co zapisany, a w `KeyInfo` klucza: rozmiar 256, `isInsideSecureHardware() == true` (API 28–30) albo `securityLevel` ≠ `SECURITY_LEVEL_SOFTWARE` (API ≥ 31), `isUserAuthenticationRequired() == false`.

### SECRET-02: StrongBox, gdy jest, bez cichego obniżania wymagań

Generacja najpierw próbuje klucza z `setIsStrongBoxBacked(true)`. Gdy ta próba rzuci wyjątek, w którego łańcuchu przyczyn jest `StrongBoxUnavailableException`, wykonywana jest dokładnie jedna druga próba z tymi samymi parametrami bez `setIsStrongBoxBacked` (klucz w TEE). Każdy inny wyjątek pierwszej próby i każdy wyjątek drugiej próby kończy generację błędem `KEY_GENERATION_FAILED`, bez trzeciej próby i bez rezygnacji z któregokolwiek parametru z SECRET-01 (w szczególności z `setUnlockedDeviceRequired(true)`).

**Kryteria akceptacji:**

- **Zakładając** próbę ze StrongBox zakończoną sukcesem, **gdy** generowany jest klucz, **wtedy** wykonano 1 próbę (ze StrongBox).
- **Zakładając** próbę ze StrongBox rzucającą wyjątek rozpoznany jako „StrongBox niedostępny” (także jako przyczynę `ProviderException`), **gdy** generowany jest klucz, **wtedy** wykonano 2 próby: ze StrongBox, potem bez, i zwrócono klucz z drugiej.
- **Zakładając** próbę ze StrongBox rzucającą `ProviderException` bez przyczyny „StrongBox niedostępny”, **gdy** generowany jest klucz, **wtedy** wykonano 1 próbę, a zapis kończy się `Failed(KEY_GENERATION)`.
- **Zakładając** obie próby rzucające wyjątek (pierwsza „StrongBox niedostępny”), **gdy** generowany jest klucz, **wtedy** wykonano 2 próby, a zapis kończy się `Failed(KEY_GENERATION)`.
- **Zakładając** telefon ze StrongBox i telefon bez StrongBox (sprawdzenie na urządzeniu, W5), **gdy** zapisano sekret, **wtedy** zapis się udał, a `KeyInfo` (API ≥ 31) pokazuje odpowiednio `SECURITY_LEVEL_STRONGBOX` i `SECURITY_LEVEL_TRUSTED_ENVIRONMENT`.

### SECRET-03: Format rekordu i szyfrowanie AES-GCM

Rekord = Base64 (alfabet standardowy RFC 4648, z dopełnieniem `=`, bez znaków końca linii) z bajtów: `[wersja = 0x01][IV: 12 bajtów][szyfrogram z tagiem: n + 16 bajtów]`, gdzie n ≥ 1 to długość sekretu. Szyfrowanie: `AES/GCM/NoPadding`, IV generuje szyfr (aplikacja go nie podaje), tag 128 bitów, dane dodatkowe uwierzytelniane (AAD) = jeden bajt wersji. Zaszyfrowanie kończy się błędem `UNAVAILABLE` (bez zapisu), gdy IV z szyfru nie ma 12 bajtów albo szyfrogram nie ma n + 16 bajtów. Rekord jest **nieprawidłowy**, gdy: nie jest poprawnym Base64 (dekoder ścisły), po dekodowaniu ma < 30 bajtów, albo pierwszy bajt ≠ `0x01`. Odszyfrowanie używa IV z rekordu, tagu 128 bitów i AAD = bajt wersji z rekordu.

**Kryteria akceptacji:**

- **Zakładając** szyfrator z kluczem K, **gdy** zaszyfrowano S1 i zakodowano rekord, **wtedy** rekord po dekodowaniu Base64 ma 1 + 12 + 36 = 49 bajtów, pierwszy bajt to `0x01`, a tekst rekordu ma 68 znaków i pasuje do `^[A-Za-z0-9+/]+={0,2}$`.
- **Zakładając** rekord z kryterium wyżej, **gdy** go zdekodowano i odszyfrowano kluczem K, **wtedy** wynikiem są bajty S1.
- **Zakładając** dwa zaszyfrowania S1 tym samym kluczem K, **gdy** porównano rekordy, **wtedy** IV są różne i rekordy są różne.
- **Zakładając** rekord S1 z jednym bitem zmienionym w szyfrogramie, w tagu albo w IV, **gdy** go odszyfrowano kluczem K, **wtedy** wynik to błąd `CORRUPTED`.
- **Zakładając** bajty `[0x01][12 × 0x00][17 × 0xAA]` (30 bajtów) zakodowane Base64, **gdy** rekord zdekodowano, **wtedy** jest prawidłowy (IV 12 bajtów, szyfrogram 17 bajtów).
- **Zakładając** rekordy: `""`, `"@@@@"`, Base64 z 29 bajtów `[0x01][12 × 0x00][16 × 0xAA]`, Base64 z 30 bajtów `[0x02][12 × 0x00][17 × 0xAA]`, Base64 z 30 bajtów `[0x00][…]`, **gdy** je zdekodowano, **wtedy** każdy jest nieprawidłowy.
- **Zakładając** rekord S1 z kluczem K, w którym bajt wersji zmieniono na `0x02`, **gdy** go zdekodowano, **wtedy** jest nieprawidłowy (nie dochodzi do odszyfrowania).

### SECRET-04: Zapis sekretu

`save(secret)` zapisuje sekret tak: kopia bajtów z `secret.copyBytes()` → zaszyfrowanie (z kluczem istniejącym albo nowo wygenerowanym, SECRET-01) → rekord (SECRET-03) → zapis rekordu w magazynie klucz-wartość. Wynik `Saved` wtedy i tylko wtedy, gdy zapis rekordu zwrócił `true`; stan sekretu = `PRESENT`. Kopia bajtów jest wyzerowana przed powrotem z `save` przy każdym wyniku, wyjątku i anulowaniu. `save` nie zmienia przekazanego `TotpSecret`. Wyniki błędów: zaszyfrowanie `LOCKED` → `Locked`; `KEY_GENERATION_FAILED` → `Failed(KEY_GENERATION)`; `KEY_INVALIDATED` → postępowanie z SECRET-08; inny błąd zaszyfrowania → `Failed(CRYPTO)`; zapis rekordu `false` albo wyjątek → `Failed(STORAGE)`.

**Kryteria akceptacji:**

- **Zakładając** pusty magazyn, **gdy** wywołano `save(S1)`, **wtedy** wynik to `Saved`, magazyn zawiera dokładnie jeden rekord, stan = `PRESENT`, a następny odczyt zwraca sekret, z którego `TotpGenerator` dla chwili 59000 ms daje kod `287082` (RFC 6238, Spec 001).
- **Zakładając** szyfrator, który zapamiętuje referencję do przekazanej tablicy bajtów, **gdy** `save(S1)` zwrócił wynik, **wtedy** ta tablica zawiera same zera; tak samo przy wyniku `Failed(CRYPTO)`, `Failed(STORAGE)`, `Locked` i przy anulowaniu korutyny w trakcie zapisu rekordu.
- **Zakładając** `TotpSecret` S1, **gdy** wywołano `save`, **wtedy** po powrocie kod liczony z tego obiektu jest taki sam jak przed wywołaniem.
- **Zakładając** szyfrator zwracający `LOCKED`, **gdy** wywołano `save(S1)`, **wtedy** wynik to `Locked`, magazyn klucz-wartość nie był zapisywany, klucz nie był usuwany, stan bez zmian.
- **Zakładając** źródło klucza rzucające przy generacji, **gdy** wywołano `save(S1)`, **wtedy** wynik to `Failed(KEY_GENERATION)`, magazyn nie był zapisywany.
- **Zakładając** szyfrator zwracający `UNAVAILABLE`, **gdy** wywołano `save(S1)`, **wtedy** wynik to `Failed(CRYPTO)`, magazyn nie był zapisywany, klucz nie był usuwany.
- **Zakładając** magazyn klucz-wartość, którego zapis zwraca `false` albo rzuca `IllegalStateException` (inny niż `CancellationException` – ten jest przepuszczany, SECRET-17), **gdy** wywołano `save(S1)`, **wtedy** wynik to `Failed(STORAGE)`, klucz nie był usuwany, stan odpowiada zawartości magazynu (SECRET-05).

### SECRET-05: Zamiana sekretu atomowo, bez rotacji klucza

Zapis przy istniejącym rekordzie zastępuje go jednym zapisem nowego rekordu (jedna wartość pod jednym kluczem, `commit()`): po `Saved` magazyn zawiera tylko nowy rekord. Przy wynikach `Locked` i `Failed(KEY_GENERATION|CRYPTO)` magazyn klucz-wartość nie jest zapisywany, więc stary rekord pozostaje bez zmian (wyjątek: wyczyszczenie po `KEY_INVALIDATED`, SECRET-08). Przy `Failed(STORAGE)`: `SharedPreferences.commit()` zwracające `false` mogło już zmienić wartość w pamięci procesu, dlatego magazyn odczytuje rekord przed zapisem (po ewentualnym wyczyszczeniu z SECRET-08) i po nieudanym zapisie wykonuje jedną próbę wycofania – zapis poprzedniego rekordu, gdy był prawidłowy (SECRET-03), albo usunięcie, gdy go nie było lub był nieprawidłowy; gdy odczyt przed zapisem rzucił, wycofania nie ma. Gwarancja po `Failed(STORAGE)`, gdy poprzedni rekord był prawidłowy albo go nie było: magazyn zawiera poprzedni albo nowy rekord (albo jest pusty, gdy poprzedniego nie było), nigdy rekordu nieprawidłowego; odczyt nie zwraca `Invalidated`. Wyjątek: zapis po wyczyszczeniu z powodu `KEY_INVALIDATED` (SECRET-08) – stary rekord już nie istnieje, więc po `Failed(STORAGE)` magazyn jest pusty albo zawiera nowy rekord, stan = `INVALIDATED` albo `PRESENT`, a nowo wygenerowany klucz zostaje (użyje go następny zapis). Stan sekretu po wycofaniu jest wyliczany jak w `refreshState()` (SECRET-12: pusty magazyn przy stanie `INVALIDATED` zostawia `INVALIDATED`). Zamiana używa istniejącego klucza (bez generowania nowego i bez usuwania starego). Pytanie użytkownika o zgodę na zastąpienie należy do `ui`.

**Kryteria akceptacji:**

- **Zakładając** zapisany S1, **gdy** wywołano `save(S2)`, **wtedy** wynik to `Saved`, magazyn zawiera dokładnie jeden rekord, odczyt zwraca S2, a źródło klucza nie generowało ani nie usuwało klucza.
- **Zakładając** zapisany S1 i magazyn, którego zapis zwraca `false` (bez zmiany wartości), **gdy** wywołano `save(S2)`, **wtedy** wynik to `Failed(STORAGE)`, a rekord jest identyczny (tekstowo) z rekordem sprzed wywołania i odczyt zwraca S1.
- **Zakładając** zapisany S1 i magazyn, którego pierwszy zapis po `save` zmienia wartość, ale zwraca `false` (jak `commit()` przy błędzie dysku), a kolejne zapisy działają, **gdy** wywołano `save(S2)`, **wtedy** wynik to `Failed(STORAGE)`, wykonano dokładnie jedną próbę wycofania, rekord jest identyczny z rekordem sprzed wywołania i odczyt zwraca S1.
- **Zakładając** pusty magazyn, którego zapis zmienia wartość, ale zwraca `false`, **gdy** wywołano `save(S1)`, **wtedy** wynik to `Failed(STORAGE)`, magazyn jest pusty (wycofanie przez usunięcie), stan = `ABSENT`.
- **Zakładając** zapisany S1 i magazyn, którego każdy zapis zmienia wartość, ale zwraca `false`, **gdy** wywołano `save(S2)`, **wtedy** wynik to `Failed(STORAGE)`, wykonano 2 zapisy (nowy rekord, wycofanie), a `load()` zwraca S1 albo S2 (nie `Invalidated`), stan = `PRESENT`.
- **Zakładając** rekord nieprawidłowy `"@@@@"` i magazyn, którego zapis zmienia wartość, ale zwraca `false`, **gdy** wywołano `save(S1)`, **wtedy** wynik to `Failed(STORAGE)`, wycofanie wykonało usunięcie (nie zapis `"@@@@"`), a magazyn jest pusty.
- **Zakładając** zapisany S1 i magazyn rzucający przy odczycie przed zapisem, a zapis zwraca `false`, **gdy** wywołano `save(S2)`, **wtedy** wynik to `Failed(STORAGE)` i nie było próby wycofania (1 zapis).
- **Zakładając** stan `INVALIDATED`, pusty magazyn i zapis zmieniający wartość, ale zwracający `false`, **gdy** wywołano `save(S1)`, **wtedy** wynik to `Failed(STORAGE)`, magazyn jest pusty, stan = `INVALIDATED`; **gdy** potem zapis działa i wywołano `save(S1)`, **wtedy** wynik to `Saved`, stan = `PRESENT`, a `load()` zwraca S1.
- **Zakładając** zapisany S1, szyfrator zwracający przy pierwszym zaszyfrowaniu `KEY_INVALIDATED`, a przy drugim sukces, i zapis zmieniający wartość, ale zwracający `false`, **gdy** wywołano `save(S2)`, **wtedy** wykonano wyczyszczenie, wynik to `Failed(STORAGE)`, wycofanie wykonało usunięcie, magazyn jest pusty, stan = `INVALIDATED`, a `load()` zwraca `Absent`.
- **Zakładając** zapisany S1 i szyfrator zwracający `LOCKED` albo `UNAVAILABLE`, **gdy** wywołano `save(S2)`, **wtedy** rekord jest identyczny z rekordem sprzed wywołania.
- **Zakładając** zapis rekordu przerwany zabiciem procesu (sprawdzenie na urządzeniu – założenie o `SharedPreferences.commit()`, W9), **gdy** aplikacja startuje ponownie, **wtedy** odczyt zwraca stary albo nowy sekret, nigdy `Invalidated`.

### SECRET-06: Odczyt na żądanie

`load()` czyta rekord, dekoduje go (SECRET-03), odszyfrowuje i zwraca `Loaded(TotpSecret)` utworzony przez `TotpSecret.fromBytes`. Tablica z odszyfrowanymi bajtami jest wyzerowana przed powrotem z `load` przy każdym wyniku, wyjątku i anulowaniu. Brak rekordu → `Absent` (bez odszyfrowania). Każdy `load` odszyfrowuje rekord od nowa – magazyn nie przechowuje odszyfrowanego sekretu między wywołaniami (SECRET-14). Stan po `Loaded` = `PRESENT`; po `Absent` = `ABSENT`, chyba że stan był `INVALIDATED` (wtedy bez zmian).

**Kryteria akceptacji:**

- **Zakładając** zapisany S1, **gdy** wywołano `load()` dwa razy, **wtedy** oba wyniki to `Loaded` z sekretem dającym kod `287082` dla chwili 59000 ms, to dwa różne obiekty `TotpSecret`, a szyfrator odszyfrował 2 razy.
- **Zakładając** szyfrator zwracający przy odszyfrowaniu tablicę, do której test ma referencję, **gdy** `load()` zwrócił `Loaded`, **wtedy** ta tablica zawiera same zera, a zwrócony sekret nadal daje kod `287082` dla chwili 59000 ms.
- **Zakładając** pusty magazyn, **gdy** wywołano `load()`, **wtedy** wynik to `Absent`, szyfrator nie był wywołany, stan = `ABSENT`.
- **Zakładając** stan `INVALIDATED` i pusty magazyn, **gdy** wywołano `load()`, **wtedy** wynik to `Absent`, a stan = `INVALIDATED`.

### SECRET-07: Klasyfikacja błędów kryptograficznych

Każdy wyjątek z uzyskania klucza, `Cipher.init`, `updateAAD` i `doFinal` jest klasyfikowany w tej kolejności (pierwsza pasująca reguła wygrywa):

1. sprawdzenie blokady telefonu rzuciło wyjątek → `UNAVAILABLE`;
2. telefon zablokowany → `LOCKED`;
3. w łańcuchu przyczyn jest wyjątek rozpoznany jako „użytkownik nieuwierzytelniony” (`UserNotAuthenticatedException`) → `LOCKED`;
4. w łańcuchu przyczyn jest wyjątek rozpoznany jako „klucz trwale unieważniony” (`KeyPermanentlyInvalidatedException`) → `KEY_INVALIDATED`;
5. w łańcuchu przyczyn jest `javax.crypto.AEADBadTagException` → `CORRUPTED`;
6. każdy inny → `UNAVAILABLE`.

Brak klucza przy odszyfrowaniu (źródło klucza zwróciło „brak”) jest klasyfikowany regułami 1, 2, a gdy telefon nie jest zablokowany → `KEY_INVALIDATED`. Łańcuch przyczyn = wyjątek i kolejne `cause`, najwyżej 8 elementów, przerwany przy powtórzeniu obiektu. Rozpoznanie typu wyjątku Androida i sprawdzenie blokady dostarcza środowisko Keystore (warstwa platformy); wyjątek rzucony przez rozpoznanie typu jest traktowany jak „nie pasuje”.

**Kryteria akceptacji:**

- **Zakładając** telefon zablokowany, **gdy** klasyfikowany jest wyjątek rozpoznany jako „klucz trwale unieważniony”, `AEADBadTagException` albo `IllegalStateException`, **wtedy** w każdym przypadku wynik to `LOCKED`.
- **Zakładając** sprawdzenie blokady rzucające `IllegalStateException`, **gdy** klasyfikowany jest dowolny wyjątek albo brak klucza, **wtedy** wynik to `UNAVAILABLE`.
- **Zakładając** telefon odblokowany, **gdy** klasyfikowany jest `InvalidKeyException` z przyczyną rozpoznaną jako „użytkownik nieuwierzytelniony”, **wtedy** wynik to `LOCKED`.
- **Zakładając** telefon odblokowany, **gdy** klasyfikowany jest wyjątek rozpoznany jako „klucz trwale unieważniony” (bezpośrednio i jako przyczyna `ProviderException`), **wtedy** wynik to `KEY_INVALIDATED`.
- **Zakładając** telefon odblokowany, **gdy** klasyfikowany jest `AEADBadTagException` (bezpośrednio i jako przyczyna `ProviderException`), **wtedy** wynik to `CORRUPTED`.
- **Zakładając** telefon odblokowany, **gdy** klasyfikowany jest `java.security.KeyStoreException`, `ProviderException` bez przyczyny, `IllegalStateException` albo `InvalidKeyException` bez przyczyny, **wtedy** w każdym przypadku wynik to `UNAVAILABLE`.
- **Zakładając** telefon odblokowany i rozpoznanie typu rzucające wyjątek, **gdy** klasyfikowany jest `AEADBadTagException`, **wtedy** wynik to `CORRUPTED`.
- **Zakładając** wyjątek testowy z nadpisanym `cause` wskazującym na niego samego, albo łańcuch 20 wyjątków z `AEADBadTagException` na końcu, **gdy** jest klasyfikowany przy odblokowanym telefonie, **wtedy** klasyfikacja kończy się wynikiem `UNAVAILABLE` bez zapętlenia.
- **Zakładając** telefon odblokowany i brak klucza, **gdy** klasyfikowany jest brak klucza, **wtedy** wynik to `KEY_INVALIDATED`; **zakładając** telefon zablokowany – `LOCKED`.
- **Zakładając** szyfrator z kluczem K i rekord S1 zaszyfrowany kluczem K, **gdy** rekord odszyfrowano innym kluczem programowym (bajty `0x20`…`0x3F`), **wtedy** wynik to `CORRUPTED`.

### SECRET-08: Trwały błąd – wyczyszczenie i ponowny skan

Gdy odczyt napotka rekord nieprawidłowy (SECRET-03), błąd odszyfrowania `KEY_INVALIDATED` albo `CORRUPTED`, albo odszyfrowane bajty nie dają się zamienić na `TotpSecret` (pusta tablica), magazyn wykonuje wyczyszczenie: usuwa rekord, potem usuwa klucz; wynik `load` to `Invalidated`, stan = `INVALIDATED`. Błąd usunięcia rekordu albo klucza nie zmienia wyniku (`Invalidated`) i nie wychodzi jako wyjątek. Gdy zaszyfrowanie w `save` zwróci `KEY_INVALIDATED`, magazyn wykonuje wyczyszczenie (stan = `INVALIDATED`), a potem dokładnie jedną ponowną próbę zaszyfrowania (z nowym kluczem, SECRET-01); jej wynik obsługuje SECRET-04 (ponowne `KEY_INVALIDATED` → `Failed(CRYPTO)`). Wynik `Invalidated` oznacza dla `ui`: „zeskanuj ponownie QR z AAPS”; do udanego zapisu nowego sekretu `load` nie zwraca sekretu, więc polecenia wymagające kodu nie mogą wystartować.

**Kryteria akceptacji:**

- **Zakładając** rekord nieprawidłowy (np. `"@@@@"`), **gdy** wywołano `load()`, **wtedy** wynik to `Invalidated`, szyfrator nie odszyfrowywał, rekord i klucz zostały usunięte (w tej kolejności), stan = `INVALIDATED`.
- **Zakładając** zapisany S1 i szyfrator zwracający przy odszyfrowaniu `KEY_INVALIDATED` albo `CORRUPTED`, **gdy** wywołano `load()`, **wtedy** wynik to `Invalidated`, rekord i klucz usunięte, stan = `INVALIDATED`, a następny `load()` zwraca `Absent`.
- **Zakładając** szyfrator zwracający przy odszyfrowaniu pustą tablicę, **gdy** wywołano `load()`, **wtedy** wynik to `Invalidated`, rekord i klucz usunięte.
- **Zakładając** magazyn, którego usunięcie rekordu zwraca `false`, i źródło klucza rzucające przy usuwaniu, **gdy** `load()` napotyka `CORRUPTED`, **wtedy** wynik to `Invalidated` bez wyjątku.
- **Zakładając** stan `INVALIDATED`, **gdy** `save(S2)` zwrócił `Saved`, **wtedy** stan = `PRESENT`, a `load()` zwraca S2.
- **Zakładając** zapisany S1 i szyfrator zwracający przy pierwszym zaszyfrowaniu `KEY_INVALIDATED`, a przy drugim sukces, **gdy** wywołano `save(S2)`, **wtedy** wykonano wyczyszczenie, potem drugie zaszyfrowanie, wynik to `Saved`, a `load()` zwraca S2.
- **Zakładając** szyfrator zwracający przy obu zaszyfrowaniach `KEY_INVALIDATED`, **gdy** wywołano `save(S2)`, **wtedy** wykonano dokładnie 2 zaszyfrowania, wynik to `Failed(CRYPTO)`, stan = `INVALIDATED`.
- **Zakładając** prawdziwy szyfrator z kluczem K i źródło klucza, które po zapisie S1 „traci” klucz (zwraca brak), **gdy** wywołano `load()` przy odblokowanym telefonie, **wtedy** wynik to `Invalidated`.

### SECRET-09: Telefon zablokowany – nic nie jest usuwane

Gdy odszyfrowanie zwróci `LOCKED`, `load` zwraca `Locked` („odblokuj telefon”), nie usuwa rekordu ani klucza i nie zmienia stanu. Kolejny `load` po odblokowaniu odszyfrowuje normalnie.

**Kryteria akceptacji:**

- **Zakładając** zapisany S1 i szyfrator zwracający `LOCKED`, **gdy** wywołano `load()`, **wtedy** wynik to `Locked`, rekord jest identyczny z rekordem sprzed wywołania, klucz nie był usuwany, stan = `PRESENT`.
- **Zakładając** prawdziwy szyfrator z kluczem K, zapisany S1 i środowisko zgłaszające blokadę, a źródło klucza rzucające `IllegalStateException` przy `getKey`, **gdy** wywołano `load()`, **wtedy** wynik to `Locked`; **gdy** potem środowisko zgłasza odblokowanie, a źródło klucza zwraca K, **wtedy** `load()` zwraca S1.
- **Zakładając** zapisany S1 i źródło klucza zwracające „brak” przy zablokowanym telefonie, **gdy** wywołano `load()`, **wtedy** wynik to `Locked`, a rekord nie został usunięty.
- **Zakładając** telefon z zapisanym sekretem i zablokowanym ekranem (sprawdzenie na urządzeniu, W2), **gdy** wywołano `load()`, **wtedy** wynik to `Locked`; po odblokowaniu `load()` zwraca sekret.

### SECRET-10: Inny błąd – nic nie jest usuwane

Gdy odczyt rekordu z magazynu klucz-wartość rzuci wyjątek albo odszyfrowanie zwróci `UNAVAILABLE`, `load` zwraca `Unavailable`, nie usuwa rekordu ani klucza i nie zmienia stanu. Rekord i klucz są usuwane wyłącznie w przypadkach z SECRET-08 i SECRET-11.

**Kryteria akceptacji:**

- **Zakładając** zapisany S1 i szyfrator zwracający `UNAVAILABLE`, **gdy** wywołano `load()` 3 razy, **wtedy** każdy wynik to `Unavailable`, rekord jest identyczny z rekordem sprzed wywołań, klucz nie był usuwany, stan = `PRESENT`.
- **Zakładając** magazyn klucz-wartość rzucający przy odczycie, **gdy** wywołano `load()`, **wtedy** wynik to `Unavailable`, szyfrator nie był wywołany, nic nie usunięto.
- **Zakładając** prawdziwy szyfrator z kluczem K, zapisany S1 i źródło klucza rzucające `java.security.KeyStoreException` przy `getKey` (telefon odblokowany), **gdy** wywołano `load()`, **wtedy** wynik to `Unavailable`, a rekord nie został usunięty.

### SECRET-11: Jawne usunięcie sekretu

`delete()` usuwa rekord, a gdy usunięcie rekordu zwróciło `true`, usuwa klucz. Wynik `Deleted` wtedy i tylko wtedy, gdy usunięcie rekordu zwróciło `true` (błąd usunięcia klucza nie zmienia wyniku – pozostały klucz zostanie użyty przy następnym zapisie); stan = `ABSENT`. Usunięcie rekordu zwracające `false` albo rzucające → `Failed`, klucz nie jest usuwany, stan bez zmian. `delete()` przy pustym magazynie zwraca `Deleted`.

**Kryteria akceptacji:**

- **Zakładając** zapisany S1, **gdy** wywołano `delete()`, **wtedy** wynik to `Deleted`, magazyn jest pusty, klucz usunięty, stan = `ABSENT`, a `load()` zwraca `Absent`.
- **Zakładając** pusty magazyn i brak klucza, **gdy** wywołano `delete()` dwa razy, **wtedy** oba wyniki to `Deleted`.
- **Zakładając** zapisany S1 i magazyn, którego usunięcie zwraca `false`, **gdy** wywołano `delete()`, **wtedy** wynik to `Failed`, klucz nie był usuwany, `load()` zwraca S1.
- **Zakładając** zapisany S1 i źródło klucza rzucające przy usuwaniu, **gdy** wywołano `delete()`, **wtedy** wynik to `Deleted` bez wyjątku, stan = `ABSENT`.
- **Zakładając** stan `INVALIDATED`, **gdy** wywołano `delete()`, **wtedy** wynik to `Deleted`, stan = `ABSENT`.

### SECRET-12: Stan sekretu bez odszyfrowania

Magazyn udostępnia stan sekretu jako `StateFlow` (wartość początkowa `UNKNOWN`) i operację `refreshState()`, która czyta tylko obecność rekordu (bez dekodowania i odszyfrowania): rekord istnieje → `PRESENT`; brak → `ABSENT`, chyba że stan był `INVALIDATED` (wtedy bez zmian); wyjątek odczytu → stan bez zmian. `refreshState()` zwraca stan po aktualizacji. Pozostałe przejścia stanu wynikają z SECRET-04, SECRET-06, SECRET-08, SECRET-09, SECRET-10, SECRET-11. Stan `INVALIDATED` jest trzymany tylko w pamięci: po ponownym uruchomieniu procesu `refreshState()` przy pustym magazynie daje `ABSENT`.

**Kryteria akceptacji:**

- **Zakładając** nowy obiekt magazynu, **gdy** odczytano stan, **wtedy** to `UNKNOWN`, a szyfrator, źródło klucza i magazyn klucz-wartość nie były wywołane.
- **Zakładając** magazyn klucz-wartość z rekordem `"@@@@"` (nieprawidłowym), **gdy** wywołano `refreshState()`, **wtedy** stan = `PRESENT`, a szyfrator nie był wywołany.
- **Zakładając** pusty magazyn, **gdy** wywołano `refreshState()`, **wtedy** stan = `ABSENT`.
- **Zakładając** stan `INVALIDATED` i pusty magazyn, **gdy** wywołano `refreshState()`, **wtedy** stan = `INVALIDATED`.
- **Zakładając** stan `PRESENT` i magazyn rzucający przy odczycie, **gdy** wywołano `refreshState()`, **wtedy** stan = `PRESENT` bez wyjątku.
- **Zakładając** kolektor stanu (Turbine), **gdy** kolejno: `refreshState()` przy pustym magazynie, `save(S1)`, `load()` z `CORRUPTED`, `save(S2)`, `delete()`, **wtedy** kolektor dostaje kolejno `UNKNOWN`, `ABSENT`, `PRESENT`, `INVALIDATED`, `PRESENT`, `ABSENT`.

### SECRET-13: Serializacja i wątek I/O

Operacje `save`, `load`, `delete` i `refreshState` są wzajemnie wykluczające się: najwyżej jedna wykonuje w danej chwili wywołania portów (magazyn klucz-wartość, szyfrator, źródło klucza). Wszystkie wywołania portów odbywają się na wstrzykiwanym dyspozytorze (w aplikacji `Dispatchers.IO`), nigdy na wątku wywołującego. Operacje są funkcjami `suspend` i anulowalne; anulowanie nie cofa zapisu rekordu już zatwierdzonego przez `commit()`, a stan odzwierciedla wtedy rzeczywistą zawartość magazynu.

**Kryteria akceptacji:**

- **Zakładając** magazyn klucz-wartość, którego zapis blokuje wątek do zwolnienia przez test (`CountDownLatch`), **gdy** równocześnie wywołano `save(S1)` i `load()`, **wtedy** szyfrator nie został wywołany przez `load` przed zwolnieniem zapisu, a `load` zwraca S1.
- **Zakładając** 50 równoczesnych wywołań naprzemiennie `save(S1)` i `save(S2)` na `Dispatchers.Default`, **gdy** wszystkie się zakończyły, **wtedy** liczba równocześnie trwających wywołań portów nigdy nie przekroczyła 1, a `load()` zwraca S1 albo S2.
- **Zakładając** dyspozytor testowy zapisujący nazwę wątku przy każdym wywołaniu portu, **gdy** wykonano `save`, `load`, `delete`, `refreshState`, **wtedy** każde wywołanie portu odbyło się na wątku dyspozytora.
- **Zakładając** zapis rekordu, po którym test anuluje korutynę `save`, **gdy** anulowanie dotarło po zapisie, **wtedy** wywołujący dostaje `CancellationException`, rekord zawiera S1, a stan = `PRESENT`.

### SECRET-14: Brak przechowywania odszyfrowanego sekretu

Obiekty magazynu sekretu i szyfratora (singletony) mają wyłącznie pola `final`: zależności (porty, dyspozytor), blokadę i strumień stanu sekretu – żadne pole nie jest typu `ByteArray`, `CharArray`, `String`, `CharSequence`, `TotpSecret`, `SealedSecret`, `javax.crypto.Cipher`, kolekcją ani tablicą. Odszyfrowane bajty istnieją tylko jako zmienne lokalne wywołania i są zerowane (SECRET-06); kopia bajtów do zaszyfrowania jest zerowana (SECRET-04). Zwrócony `TotpSecret` należy do wywołującego. Ograniczenia: wewnętrzne bufory JCA/Keystore (`Cipher`, `SecretKeySpec` w `TotpGenerator`) i tablica wewnątrz `TotpSecret` nie są zerowane przez aplikację – zostają do odśmiecenia.

**Kryteria akceptacji:**

- **Zakładając** klasy `KeystoreTotpSecretStore` i `AesGcmSecretCipher`, **gdy** sprawdzono ich pola refleksją, **wtedy** wszystkie są `final`, a żadne nie ma typu z listy wyżej.
- **Zakładając** wykonane `save(S1)` i `load()`, **gdy** sprawdzono refleksją wartości pól magazynu i szyfratora, **wtedy** żadna nie jest referencją do zwróconego `TotpSecret` ani do tablicy bajtów.

### SECRET-15: Brak ujawniania danych

Sekret, kody TOTP, rekord, szyfrogram, IV i klucz nie trafiają do `toString()` typów z `security` (stały tekst `NazwaTypu(***)` albo nazwa wartości wyliczeniowej bez danych), do komunikatów wyjątków ani do logów. Pakiety `security` i `security.platform` nie wywołują API logowania (`android.util.Log`, `println`, `System.out`/`err`, loggerów). Wyniki operacji nie przenoszą wyjątków ani ich komunikatów. Magazyn sekretu nie rzuca wyjątków poza `CancellationException` (SECRET-17).

**Kryteria akceptacji:**

- **Zakładając** `SealedSecret` z rekordu S1, `LoadResult.Loaded(S1)`, magazyn sekretu i szyfrator, **gdy** wywołano `toString()`, **wtedy** tekst nie zawiera bajtów S1 w żadnej postaci (`12345678901234567890`, szesnastkowo `3132333435`, Base64 `MTIzNDU2`), tekstu rekordu ani jego fragmentu dłuższego niż 3 znaki.
- **Zakładając** `SaveResult.Failed(STORAGE)`, **gdy** wywołano `toString()`, **wtedy** tekst to `Failed(STORAGE)`.
- **Zakładając** wyjątek rzucany z konstruktora `SealedSecret` dla złej długości, **gdy** odczytano komunikat, **wtedy** jest stałym tekstem bez bajtów.

### SECRET-16: Magazyn klucz-wartość na SharedPreferences, bez kopii

Rekord jest zapisywany w osobnym pliku `SharedPreferences` o nazwie `totp_secret_store` (`Context.MODE_PRIVATE`) pod kluczem `sealed_secret`. Zapis i usunięcie używają `commit()` i zwracają jego wynik; `apply()` nie jest używane. Plik nie trafia do kopii zapasowej ani transferu urządzenia: w `AndroidManifest.xml` `android:allowBackup="false"` i `android:fullBackupContent="false"`, a plik wskazany przez `android:dataExtractionRules` wyklucza domenę `sharedpref` w sekcjach `cloud-backup` i `device-transfer` (stan obecny, bez zmian). Aplikacja nie używa `EncryptedSharedPreferences` ani biblioteki `androidx.security:security-crypto`.

**Kryteria akceptacji:**

- **Zakładając** pliki `app/src/main/AndroidManifest.xml` i `app/src/main/res/xml/data_extraction_rules.xml`, **gdy** test jednostkowy JVM je przeczytał, **wtedy** `allowBackup` = `false`, `fullBackupContent` = `false`, `dataExtractionRules` = `@xml/data_extraction_rules`, a obie sekcje `cloud-backup` i `device-transfer` zawierają `<exclude domain="sharedpref" />`.
- **Zakładając** `gradle/libs.versions.toml` i `app/build.gradle.kts`, **gdy** test JVM je przeczytał, **wtedy** żaden nie zawiera `security-crypto`.
- **Zakładając** telefon z wersją debug i zapisanym sekretem (sprawdzenie na urządzeniu, W8), **gdy** odczytano `shared_prefs/totp_secret_store.xml` przez `adb shell run-as`, **wtedy** plik zawiera tylko klucz `sealed_secret` z tekstem Base64, bez bajtów sekretu ani jego zapisu Base32.

### SECRET-17: Błędy portów bez wyjątków

`save`, `load`, `delete` i `refreshState` nigdy nie rzucają wyjątku innego niż `CancellationException`: każdy wyjątek z portów (magazyn klucz-wartość, szyfrator, źródło klucza, środowisko Keystore) jest zamieniany na wynik według SECRET-04, SECRET-08, SECRET-10, SECRET-11, SECRET-12. Szyfrator nie rzuca wyjątków poza `CancellationException` – zwraca sklasyfikowany błąd. `CancellationException` jest zawsze przepuszczany.

**Kryteria akceptacji:**

- **Zakładając** każdy port po kolei rzucający `RuntimeException("x")` w każdej swojej metodzie, **gdy** wywołano kolejno `refreshState`, `save(S1)`, `load`, `delete`, **wtedy** żadne wywołanie nie rzuciło, a każdy wynik jest jednym z wyników z SECRET-04, SECRET-06, SECRET-08, SECRET-10, SECRET-11.
- **Zakładając** szyfrator z kluczem K, którego źródło klucza rzuca `RuntimeException` przy `getKey`, **gdy** zaszyfrowano i odszyfrowano, **wtedy** obie operacje zwróciły błąd `UNAVAILABLE` bez wyjątku.
- **Zakładając** magazyn klucz-wartość rzucający `CancellationException` przy zapisie, **gdy** wywołano `save(S1)`, **wtedy** wywołujący dostaje `CancellationException`, a kopia bajtów jest wyzerowana.

## Przypadki błędów

| Sytuacja | Oczekiwane zachowanie | Wymaganie |
| --- | --- | --- |
| Brak StrongBox | jedna próba bez StrongBox (TEE) | SECRET-02 |
| Generacja klucza nieudana (inny powód) | `Failed(KEY_GENERATION)`, bez obniżania parametrów | SECRET-02, SECRET-04 |
| Telefon zablokowany przy zapisie | `Locked`, nic nie zmienione | SECRET-04 |
| Zapis rekordu `false` / wyjątek (poza `CancellationException`) | `Failed(STORAGE)`; jedna próba wycofania, gdy odczyt przed zapisem się udał (bez wycofania, gdy rzucił); poprzedni albo nowy rekord, nigdy nieprawidłowy | SECRET-04, SECRET-05 |
| Proces zabity w trakcie zapisu | stary albo nowy sekret (W9) | SECRET-05 |
| Brak rekordu | `Absent` | SECRET-06 |
| Rekord nieprawidłowy (Base64, długość, wersja) | wyczyszczenie, `Invalidated` | SECRET-03, SECRET-08 |
| Brak klucza przy rekordzie (odblokowany telefon) | wyczyszczenie, `Invalidated` | SECRET-07, SECRET-08 |
| `KeyPermanentlyInvalidatedException` | wyczyszczenie, `Invalidated` (przy zapisie: wyczyszczenie i jedna ponowna próba) | SECRET-07, SECRET-08 |
| Błąd tagu GCM | wyczyszczenie, `Invalidated` | SECRET-07, SECRET-08 |
| Telefon zablokowany / `UserNotAuthenticatedException` | `Locked`, nic nie usunięte | SECRET-07, SECRET-09 |
| Sprawdzenie blokady rzuca | `Unavailable`, nic nie usunięte | SECRET-07, SECRET-10 |
| Inny wyjątek Keystore / JCA | `Unavailable` / `Failed(CRYPTO)`, nic nie usunięte | SECRET-07, SECRET-10 |
| Błąd odczytu magazynu | `Unavailable`, stan bez zmian | SECRET-10, SECRET-12 |
| Usunięcie rekordu nieudane | `delete`: `Failed`; wyczyszczenie: nadal `Invalidated` | SECRET-08, SECRET-11 |
| Usunięcie klucza nieudane | bez wpływu na wynik | SECRET-08, SECRET-11 |
| Równoczesne operacje | wykonywane po kolei | SECRET-13 |
| Anulowanie | `CancellationException`, bufory wyzerowane, stan zgodny z magazynem | SECRET-04, SECRET-13, SECRET-17 |

## Bezpieczeństwo

Zasady z [AGENTS.md](../../../AGENTS.md#bezpieczeństwo) i [planu](../../plan.md#bezpieczeństwo), które dotyczą tej funkcji:

- **Sekret zaszyfrowany kluczem, którego nie da się wyciągnąć.** Klucz AES-256 jest generowany w Keystore (StrongBox albo TEE), nieeksportowalny, działa tylko przy odblokowanym telefonie (`setUnlockedDeviceRequired(true)`). Plik `SharedPreferences` zawiera tylko szyfrogram, więc kopia pliku bez klucza nic nie daje (SECRET-01, SECRET-02, SECRET-16). Brak biometrii i brak `setUserAuthenticationRequired` – decyzja z planu.
- **Bez cichego obniżania wymagań.** Jedyne dopuszczone przejście to StrongBox → TEE przy braku StrongBox; brak klucza programowego i brak rezygnacji z `setUnlockedDeviceRequired` (SECRET-02).
- **Sekret nigdy nie jest logowany ani pokazywany** (SECRET-15); odszyfrowany tylko na czas przygotowania polecenia, bufory zerowane, brak przechowywania w singletonach (SECRET-06, SECRET-14).
- **Przejściowa blokada nie kasuje sekretu.** Rekord i klucz są usuwane tylko po pozytywnym rozpoznaniu trwałego błędu przy odblokowanym telefonie; każda niepewność (zablokowany telefon, nieznany wyjątek, błąd sprawdzenia blokady) daje wynik przejściowy bez usuwania (SECRET-07, SECRET-09, SECRET-10). Powód: telefon z AAPS jest zwykle u innej osoby, więc niepotrzebne skasowanie sekretu blokuje polecenia z kodem do czasu fizycznego dostępu do AAPS; pozostawienie nieczytelnego szyfrogramu nie daje atakującemu nic.
- **Brak kopii zapasowej i transferu urządzenia** (SECRET-16). Wpisy Keystore nie są kopiowane przez system, więc nawet skopiowany plik byłby bezużyteczny.
- **PIN AAPS** nie dotyczy tej specyfikacji: magazyn nie przyjmuje ani nie zapisuje PIN-u; `AapsPin` bez zmian.
- **Świadome ryzyko (z planu):** kto ma odblokowany telefon, może odszyfrować sekret przez aplikację; ochroną jest PIN AAPS i blokada ekranu. Root i modyfikacje systemu są poza zakresem.
- **Dane w repo:** wyłącznie sekret z RFC 6238 (S1), fikcyjny S2 i fikcyjne klucze programowe testów.

## Dane testowe

- S1: ASCII `12345678901234567890` (ziarno SHA-1 z RFC 6238, dodatek B); kontrola przez `TotpGenerator` (Spec 001): chwila 59000 ms → kod `287082` (6 ostatnich cyfr wektora `94287082`).
- S2: bajty `0x00`…`0x13` (fikcyjne).
- Klucz testowy K: bajty `0x00`…`0x1F`; drugi klucz testowy: `0x20`…`0x3F` (programowe klucze JCE, wyłącznie testy JVM).
- Rekordy syntetyczne: `[0x01][12 × 0x00][17 × 0xAA]` (prawidłowy format), warianty z SECRET-03.
- Brak plików w `dane/`.

## Weryfikacja na urządzeniu

Fakty o Android Keystore przyjęte na podstawie dokumentacji, do potwierdzenia ręcznie na telefonie, gdy powstanie `ui` (ekran konfiguracji ze skanem QR i polecenie z kodem; jak decyzja P3 ze Spec 005: bez Robolectric i `androidTest`). Wyniki (telefon, wersja Androida, wynik każdego punktu) opisuje pierwszy PR `ui`, który zapisuje sekret. Wynik niezgodny z założeniem = zmiana spec, nie obejście w kodzie.

| Nr | Do sprawdzenia | Wymaganie |
| --- | --- | --- |
| W1 | Zapis, odczyt i kod zgodny z Authenticatorem na API 28 i najnowszym dostępnym; po zabiciu procesu i ponownym starcie odczyt działa; parametry z `KeyInfo` | SECRET-01, SECRET-06 |
| W2 | Odczyt przy zablokowanym ekranie (np. odczyt wywołany 10 s po zablokowaniu przyciskiem zasilania) daje `Locked`, nic nie jest usuwane; po odblokowaniu odczyt działa. Zanotować typ wyjątku (`UserNotAuthenticatedException`, `KeyStoreException` albo inny) | SECRET-07, SECRET-09 |
| W3 | Zmiana PIN-u / wzoru blokady ekranu (bez wyłączenia blokady): odczyt nadal działa | SECRET-01 |
| W4 | Wyłączenie blokady ekranu (ustawienie „Brak” / „Przesunięcie”), potem odczyt: zanotować wynik. Zgodne ze spec: `Loaded` albo `Invalidated`. `Unavailable` w każdej próbie = zgłosić jako zmianę spec (klasyfikacja nieznanego wyjątku) | SECRET-07, SECRET-08 |
| W5 | Telefon ze StrongBox i bez: zapis działa, `KeyInfo.securityLevel` (API ≥ 31) zgodny z SECRET-02 | SECRET-02 |
| W6 | Odinstalowanie i ponowna instalacja oraz „Wyczyść dane”: stan `ABSENT`, zapis nowego sekretu działa | SECRET-12, SECRET-01 |
| W7 | Telefon bez bezpiecznej blokady ekranu od początku: zanotować, czy generacja klucza z `setUnlockedDeviceRequired(true)` się udaje (informacja dla `ui` – ewentualny komunikat „ustaw blokadę ekranu”) | SECRET-02, SECRET-04 |
| W8 | Plik `shared_prefs/totp_secret_store.xml` (wersja debug, `adb shell run-as`) zawiera tylko `sealed_secret` z Base64 | SECRET-16 |
| W9 | Zabicie procesu (`adb shell am kill` / wymuszone zatrzymanie) zaraz po zamianie sekretu: po starcie odczyt zwraca stary albo nowy sekret | SECRET-05 |
| W10 | Odblokowanie przez Smart Lock (zaufane urządzenie / miejsce), jeśli telefon to wspiera: odczyt daje `Loaded`, `Locked` albo `Unavailable` – nigdy `Invalidated` | SECRET-07, SECRET-09 |

## Otwarte pytania

Brak. Decyzje użytkownika przyjęte przy tej specyfikacji:

- [x] Klucz AES-256-GCM w Keystore, generowany w aplikacji, bez biometrii i bez `setUserAuthenticationRequired`, z `setUnlockedDeviceRequired(true)`; StrongBox, gdy jest, inaczej TEE; bez innego obniżania wymagań (SECRET-01, SECRET-02).
- [x] Utrata / unieważnienie klucza i nieudane odszyfrowanie: usunięcie rekordu i klucza, „zeskanuj ponownie QR z AAPS”; zablokowany telefon nic nie usuwa (SECRET-08, SECRET-09). Rozstrzygnięcie niepewnych przypadków w tej spec: nie kasować (SECRET-07, SECRET-10, sekcja Bezpieczeństwo).
- [x] Jeden sekret; ponowny skan zastępuje atomowo; jawne „usuń sekret”; potwierdzenie nadpisania w `ui` (SECRET-05, SECRET-11).
- [x] Osobny plik `SharedPreferences`, Base64(wersja + IV + szyfrogram z tagiem), `commit()`, I/O poza wątkiem głównym, bez `EncryptedSharedPreferences` (SECRET-03, SECRET-13, SECRET-16). Odstępstwo od [docs/plan.md](../../plan.md#wybór-technologii) (wiersz „Bezpieczne dane: … `EncryptedSharedPreferences` / DataStore z szyfrowaniem”): biblioteka `androidx.security:security-crypto` jest wycofana; szyfrowanie robi aplikacja kluczem z Keystore.
- [x] Odczyt na żądanie jako `TotpSecret`, bufory zerowane, stan obserwowalny bez odszyfrowania (SECRET-06, SECRET-12, SECRET-14).
- [x] Testy: logika czysta w JVM, warstwa Androida ręcznie W1–W10 (jak P3 ze Spec 005).

## Przyszłe zmiany (nie blokują)

- **Zerowanie `TotpSecret` po poleceniu.** `TotpSecret` nie ma metody czyszczenia (Spec 001, design: „cykl życia sekretu w pamięci określi spec `security`”). Ta spec ogranicza czas życia kopii w `security` do wywołania; obiekt przekazany w `CodeCredentials` żyje do odśmiecenia po końcu sesji. Dodanie `TotpSecret.clear()` i wywołania go przez sesję przy zakończeniu (jak `AapsPin.clear()`) wymaga zmiany Spec 001 i Spec 004 – proponowane jako osobny PR `Spec: zmiana …`.
- **Utrwalenie stanu `INVALIDATED`** po restarcie procesu (np. znacznik w `SharedPreferences`), jeśli `ui` będzie potrzebować innego komunikatu niż przy pierwszej konfiguracji.
