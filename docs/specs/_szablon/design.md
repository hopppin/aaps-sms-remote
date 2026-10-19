# Design NNN: <nazwa funkcji>

Dotyczy: [spec.md](spec.md)

## Przegląd

Krótko: jak rozwiązanie realizuje wymagania i gdzie leży w architekturze z [AGENTS.md](../../../AGENTS.md#architektura).

## Interfejsy

Publiczne typy i funkcje (sygnatury w Kotlinie), które będą używane przez inne pakiety.

```kotlin
interface Przyklad {
    fun zrob(wejscie: Wejscie): Wynik
}
```

## Model danych i stany

Typy danych, a dla logiki stanowej tabela przejść:

| Stan | Zdarzenie | Nowy stan | Akcja | Wymaganie |
| --- | --- | --- | --- | --- |
| | | | | |

## Decyzje

| Decyzja | Alternatywy | Powód |
| --- | --- | --- |
| | | |

## Mapowanie wymagań na testy

| Wymaganie | Test (klasa / rodzaj) |
| --- | --- |
| XXX-01 | `XxxTest` – jednostkowy |

## Podział na zadania

Proponowane kawałki do jednego PR (ok. 200–400 linii), w kolejności zależności. `planista` zakłada z nich issues.

1. <zadanie> – wymagania XXX-01, XXX-02
