# Dane testowe: odpowiedzi SMS z AAPS dla BOLUS

- **Wersja AAPS:** jak w [Spec 002](../../002-parser-odpowiedzi/dane/README.md) (3.4.2.6) – decyzja użytkownika (P9 w [spec.md](../spec.md#otwarte-pytania)).
- **Język:** polski, bez znaków diakrytycznych.
- **Źródło:** SMS-y z telefonu użytkownika, przepisane znak w znak (przecinki dziesiętne, spacje, podział linii). Każda odpowiedź przyszła jako jeden SMS.
- **Anonimizacja:** imię z nazwy konta Authenticatora zastąpione fikcyjnym `Jan`. Brak numerów telefonu, PIN-ów i kodów TOTP.

| Plik | Polecenie wysłane | Wiadomość AAPS | Linie |
| --- | --- | --- | --- |
| [bolus-prosba-o-kod-0-10.txt](bolus-prosba-o-kod-0-10.txt) | `BOLUS 0.1` | prośba o kod, 0,10 j. (bez spacji przed `U`) | 1 |
| [bolus-prosba-o-kod-obcieta-5-00.txt](bolus-prosba-o-kod-obcieta-5-00.txt) | `BOLUS 99` | prośba o kod z ilością obciętą do max bolusa AAPS (5,00 j.) | 1 |
| [bolus-sukces-1-20.txt](bolus-sukces-1-20.txt) | `BOLUS` 1,20 j. + poprawny kod | potwierdzenie podania 1,20 j. (spacja przed `U`) i stan pompy | 4 |

Uwagi:

- Linie 2–4 potwierdzenia (`Ost. Pol.`, `Tymcz.`, `Bat.`) to stan pompy w chwili odpowiedzi; parser ich nie sprawdza (BOL-09).
- Pliki mają linie rozdzielone LF, bez końcowego znaku nowej linii (`.gitattributes` wymusza LF). Wykonawca kopiuje je bez zmian do `app/src/test/resources/parser/aaps-3.4.2.6-pl/`.
- Nie mamy jeszcze (niezweryfikowane): odmowy przez minimalny odstęp, odpowiedzi przy bolusie w kolejce, błędu bolusa, „Zly kod” dla bolusa. Nowe SMS-y = nowe pliki, bez nadpisywania tych.
