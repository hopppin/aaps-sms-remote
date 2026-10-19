# Dane testowe: odpowiedzi SMS z AAPS

- **Wersja AAPS:** 3.4.2.6
- **Język komunikatów:** polski (AAPS w tej wersji wysyła teksty **bez** polskich znaków diakrytycznych)
- **Źródło:** SMS-y otrzymane przez użytkownika, wklejone znak w znak (spacje, przecinki dziesiętne, kropki, podział linii). Każda odpowiedź przyszła jako jeden SMS.
- **Anonimizacja:** imię w prośbie o kod (ustawienie w AAPS) zastąpione fikcyjnym `Jan`. Pliki nie zawierają numerów telefonu, PIN-ów ani kodów TOTP.

| Plik | Polecenie wysłane | Wiadomość AAPS | Linie |
| --- | --- | --- | --- |
| [bg.txt](bg.txt) | `BG` | odpowiedź z glikemią | 1 |
| [pump.txt](pump.txt) | `PUMP` | stan pompy | 3 |
| [carbs-prosba-o-kod.txt](carbs-prosba-o-kod.txt) | `CARBS 1` | prośba o kod | 1 |
| [carbs-sukces.txt](carbs-sukces.txt) | `CARBS 1` + poprawny kod | potwierdzenie wykonania | 4 |
| [zly-kod.txt](zly-kod.txt) | `CARBS 1` + błędny kod | odrzucenie kodu | 1 |

Uwagi techniczne:

- Pliki mają linie rozdzielone LF i nie mają końcowego znaku nowej linii. `.gitattributes` wymusza dla nich LF także przy `core.autocrlf=true` (Windows); parser i tak normalizuje końce linii (PARSER-02). Testy, które sprawdzają konkretny rodzaj końca linii (LF, CRLF), budują tekst w kodzie, a nie czytają go z pliku.
- Pliki są źródłem prawdy dla testów parsera. Wykonawca kopiuje je bez zmian do zasobów testowych (patrz [design.md](../design.md#mapowanie-wymagań-na-testy)).
- Nowa wersja lub język AAPS = nowy katalog/zestaw plików z adnotacją wersji, bez nadpisywania tych.
