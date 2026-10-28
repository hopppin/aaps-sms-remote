# Dane testowe: odpowiedzi SMS z AAPS dla PROFILE

- **Wersja AAPS:** jak w [Spec 002](../../002-parser-odpowiedzi/dane/README.md) (3.4.2.6) – do potwierdzenia (P6 w [spec.md](../spec.md#otwarte-pytania)).
- **Język:** polski, bez znaków diakrytycznych.
- **Źródło:** SMS-y z telefonu użytkownika, przepisane znak w znak. Nazwy profili (`LocalProfile1`, `LocalProfile2`) to domyślne nazwy AAPS, nie dane osobowe.
- **Anonimizacja:** imię z nazwy konta Authenticatora zastąpione fikcyjnym `Jan`. Brak numerów telefonu, PIN-ów i kodów TOTP.

| Plik | Polecenie wysłane | Wiadomość AAPS | Linie |
| --- | --- | --- | --- |
| [profile-status.txt](profile-status.txt) | `PROFILE STATUS` | aktualny profil z procentem | 1 |
| [profile-list.txt](profile-list.txt) | `PROFILE LIST` | lista numerowana „N. nazwa” | 2 |
| [profile-prosba-o-kod.txt](profile-prosba-o-kod.txt) | `PROFILE 1` | prośba o kod dla `LocalProfile1 100%` | 1 |
| [profile-bledny-format.txt](profile-bledny-format.txt) | `PROFILE 99` (numer spoza listy) i `PROFILE 1 0` (procent 0) | odmowa, ta sama treść w obu przypadkach | 1 |

Uwagi:

- `profile-list.txt`: użytkownik potwierdził listę numerowaną „N. nazwa” w dwóch liniach; dokładny znak podziału linii w SMS-ie nie jest potwierdzony (P2 w spec). Plik ma linie rozdzielone LF.
- `profile-status.txt`: użytkownik najpierw podał `LocalPofile1 (115%)`, potem potwierdził, że to literówka – właściwa treść to `LocalProfile1 (115%)`.
- Pliki mają linie rozdzielone LF, bez końcowego znaku nowej linii. Wykonawca kopiuje je bez zmian do `app/src/test/resources/parser/aaps-3.4.2.6-pl/`.
- Nie mamy jeszcze (niezweryfikowane): odpowiedzi po poprawnym kodzie zmiany profilu, „Zly kod” dla profilu.
