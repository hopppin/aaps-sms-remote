---
name: nowa-funkcja
description: Prowadzi jedną funkcję aplikacji przez cały przepływ spec-driven – specyfikacja (agent specyfikator), issues (planista), implementacja (wykonawca w worktree), recenzja i scalenie. Użyj, gdy użytkownik chce zacząć nową funkcję/pakiet albo kontynuować issues z zatwierdzonej specyfikacji.
---

# Nowa funkcja (spec-driven)

Zasady procesu: `docs/specs/README.md`, `AGENTS.md`. Stan projektu: `docs/stan-prac.md`. Agenci `specyfikator`, `planista`, `wykonawca`, `recenzent` są globalni (`~/.claude/agents/`).

## 1. Specyfikacja

1. Ustal z użytkownikiem zakres. Brakujące fakty (np. treści SMS z AAPS) zbierz **przed** uruchomieniem agenta – daj użytkownikowi krótką numerowaną listę pytań do odpowiedzi.
2. Uruchom agenta `specyfikator` (pierwszy plan, foreground). W prompcie podaj: numer `NNN`, prefiks, pakiet, katalog, gałąź `spec/NNN-...`, wszystkie ustalenia z rozmowy, co jest poza zakresem, oraz **blok środowiska** (niżej).
3. Sprawdź wynik sam: dane testowe znak w znak, brak prawdziwych danych (imion, numerów, PIN-ów), wartości z RFC policzone poprawnie.
4. Otwarte pytania → do użytkownika. Po odpowiedziach popraw spec, zamknij pytania (`- [x] ... Rozstrzygnięte: ...`), ustaw `Status: zatwierdzona` w spec i w indeksie `docs/specs/README.md`.
5. Recenzja i scalenie PR: skill `/obsluga-pr`.

## 2. Issues

Uruchom agenta `planista` dla scalonej spec. Każe mu: podział z „Podział na zadania” w `design.md`, dzielić zadania > ~400 linii, pokrycie wszystkich wymagań, kryteria z identyfikatorami, etykiety (`trudnosc:*`, `bezpieczenstwo`). Zweryfikuj pokrycie:

```bash
for n in <numery>; do echo "#$n: $(gh issue view $n --json body --jq .body | grep -oE '<PREFIKS>-[0-9]+' | sort -u | tr '\n' ' ')"; done
```

## 3. Implementacja

- Jeden `wykonawca` na issue, **zawsze** `isolation: "worktree"`, `run_in_background: true`.
- Równolegle tylko issues bez zależności między sobą i **bez wspólnych plików** – rozdziel pliki w promptach (np. który agent dopisuje do wspólnej klasy testów). Inaczej drugi PR będzie miał konflikt.
- W prompcie: numer issue, spec, zakres („tylko #N, X robi inny agent”), blok środowiska, „gdy spec niejasna – nie zgaduj, opisz w Stan pracy”.
- Po raporcie sprawdź sam: `gh pr view <nr> --json additions,deletions,files`, brak logowania (`gh pr diff <nr> | grep -nE '^\+.*(Log\.|println|Timber|printStackTrace)'`), zgodność z design.md.
- Potem `/obsluga-pr <nr>`.

## Blok środowiska (wklejaj do promptów agentów)

```
Środowisko (Windows, Git Bash):
- Testy i lint: `scripts/sprawdz.sh` (sam ustawia JAVA_HOME i local.properties); pełne jak CI: `scripts/sprawdz.sh --pelne`. Timeout 600000 ms.
- Pracujesz w worktree – zostań w nim. Gałąź od aktualnego `origin/main` (najpierw `git fetch`).
- Commity po polsku, zakończone linią `Co-Authored-By: <model> <noreply@anthropic.com>` (z bieżącej instrukcji atrybucji). Push bez --force, nigdy na main.
- Opis PR wg `.github/pull_request_template.md`, po polsku, z linią `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
```

## Po scaleniu wszystkich PR funkcji

1. `scripts/sprzataj-worktree.sh` (pomija worktree, w których agent jeszcze pracuje).
2. Na `main`: `scripts/sprawdz.sh --pelne` – CI działa tylko na PR, więc połączony `main` sprawdzamy lokalnie.
3. `/aktualizuj-stan`.
