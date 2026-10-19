# Spec NNN: <nazwa funkcji>

- **Status:** szkic
- **Prefiks wymagań:** `XXX`
- **Pakiety:** `<pakiet>` (np. `otp`)
- **Powiązane:** [docs/plan.md](../../plan.md#sekcja), inne specyfikacje

## Cel

Jedno–dwa zdania: jaki problem rozwiązuje ta funkcja i dla kogo.

## Zakres

- co wchodzi

**Poza zakresem:**

- co świadomie pomijamy (i ewentualnie kiedy wróci)

## Wymagania

Każde wymaganie jest jednoznaczne i testowalne. Bez słów typu „szybko”, „zwykle”, „odpowiednio” – podaj liczby i warunki.

### XXX-01: <krótka nazwa>

<Opis wymagania w jednym–dwóch zdaniach.>

**Kryteria akceptacji:**

- **Zakładając** <stan początkowy>, **gdy** <zdarzenie>, **wtedy** <oczekiwany wynik>.

### XXX-02: <krótka nazwa>

…

## Przypadki błędów

| Sytuacja | Oczekiwane zachowanie | Wymaganie |
| --- | --- | --- |
| <błąd> | <reakcja> | XXX-0N |

## Bezpieczeństwo

Które zasady z [AGENTS.md](../../../AGENTS.md#bezpieczeństwo) dotyczą tej funkcji i jak są spełnione. Jeśli żadne – napisz to wprost.

## Dane testowe

Skąd pochodzą (RFC, zebrane SMS-y z AAPS) i gdzie leżą (`dane/`). Żadnych prawdziwych sekretów, PIN-ów ani numerów telefonu.

## Otwarte pytania

- [ ] Pytanie, które blokuje zatwierdzenie. Specyfikacja z otwartymi pytaniami nie może mieć statusu `zatwierdzona`.
