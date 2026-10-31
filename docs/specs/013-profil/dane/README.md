# Dane testowe: odpowiedzi SMS z AAPS dla PROFILE

- **Wersja AAPS:** jak w [Spec 002](../../002-parser-odpowiedzi/dane/README.md) (3.4.2.6) – decyzja użytkownika (P6 w [spec.md](../spec.md#otwarte-pytania)).
- **Język:** polski, bez znaków diakrytycznych.
- **Źródło (pliki zebrane z telefonu: `profile-status.txt`, `profile-list.txt`, `profile-prosba-o-kod.txt`, `profile-bledny-format.txt`):** SMS-y z telefonu użytkownika, przepisane znak w znak. Nazwy profili (`LocalProfile1`, `LocalProfile2`) to domyślne nazwy AAPS, nie dane osobowe.
- **Pliki wygenerowane z zasobów AAPS 3.4.2.6** (`profile-sukces.txt`, `profile-bazowy-uaktualniony.txt`, `profile-nieprawidlowy.txt`): nie pochodzą z telefonu, to teksty `values-pl-rPL` po `stripAccents`; opis w Uwagach i w [aaps-kod-zrodlowy.md](aaps-kod-zrodlowy.md).
- **Anonimizacja (pliki z telefonu):** imię z nazwy konta Authenticatora zastąpione fikcyjnym `Jan`. Brak numerów telefonu, PIN-ów i kodów TOTP.

| Plik | Polecenie wysłane | Wiadomość AAPS | Linie |
| --- | --- | --- | --- |
| [profile-status.txt](profile-status.txt) | `PROFILE STATUS` | aktualny profil z procentem | 1 |
| [profile-list.txt](profile-list.txt) | `PROFILE LIST` | lista numerowana „N. nazwa” | 2 |
| [profile-prosba-o-kod.txt](profile-prosba-o-kod.txt) | `PROFILE 1` | prośba o kod dla `LocalProfile1 100%` | 1 |
| [profile-bledny-format.txt](profile-bledny-format.txt) | `PROFILE 99` (numer spoza listy) i `PROFILE 1 0` (procent 0) | odmowa, ta sama treść w obu przypadkach | 1 |
| [profile-sukces.txt](profile-sukces.txt) | `PROFILE 1 <p>` + poprawny kod | potwierdzenie zmiany profilu (`sms_profile_switch_created`), tylko do nadawcy – **z kodu AAPS** | 1 |
| [profile-bazowy-uaktualniony.txt](profile-bazowy-uaktualniony.txt) | brak (powiadomienie do wszystkich numerów po ustawieniu profilu w pompie, `profile_set_ok`) | powiadomienie, nie potwierdzenie – **z kodu AAPS** | 1 |
| [profile-nieprawidlowy.txt](profile-nieprawidlowy.txt) | `PROFILE LIST` przy pustej liście albo porażka zmiany po kodzie (`invalid_profile`) | odmowa – **z kodu AAPS** | 1 |
| [aaps-kod-zrodlowy.md](aaps-kod-zrodlowy.md) | – | raport z analizy kodu AndroidAPS 3.4.2.6 dla BOLUS i PROFILE (źródło plików oznaczonych „z kodu AAPS”) | – |

Uwagi:

- `profile-list.txt`: użytkownik potwierdził listę numerowaną „N. nazwa” w dwóch liniach; dokładny znak podziału linii w SMS-ie nie jest potwierdzony (P2 w spec). Plik ma linie rozdzielone LF.
- `profile-status.txt`: użytkownik najpierw podał `LocalPofile1 (115%)`, potem potwierdził, że to literówka – właściwa treść to `LocalProfile1 (115%)`.
- Pliki mają linie rozdzielone LF, bez końcowego znaku nowej linii. Wykonawca kopiuje je bez zmian do `app/src/test/resources/parser/aaps-3.4.2.6-pl/`.
- Pliki oznaczone „z kodu AAPS” (2026-10-31) nie pochodzą z telefonu: treść to zasób `values-pl-rPL` AAPS 3.4.2.6 po `stripAccents` (bez diakrytyków, `ł` → `l`), tak jak AAPS wysyła SMS. Do potwierdzenia na telefonie (W5 w spec); różnica = PR `Spec:` z poprawką, bez nadpisywania plików z telefonu.
- `profile-list.txt`: separator LF potwierdza kod AAPS (`Plugin.kt:676-682`).
- „Zly kod” dla profilu: wspólny `zly-kod.txt` ze Spec 002 (z kodu AAPS: ta sama treść, `AuthRequest.kt:51-56`).
