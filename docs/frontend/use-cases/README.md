# Katalog use case'ów frontendu

**Status:** Katalog use case'ów frontendu; statusy poszczególnych przypadków określają ich implementację
**Konwencja ID:** `FE-<OBSZAR>-<NNN>`, np. `FE-AUTH-001`. Prefiks `FE` odróżnia przypadki użycia interfejsu od identyfikatorów backendu (`UC-001`, `UC-101`, `UC-201` itd.).

Katalog opisuje planowaną funkcjonalność interfejsu. Kontrakty API i reguły biznesowe pozostają zgodne z dokumentacją backendu. Nie należy dopisywać szczegółowych wymagań ekranów ani zmieniać reguł backendu bez uzgodnienia.

## Uwierzytelnianie (`FE-AUTH`)

| ID | Use case | Status |
|---|---|---|
| FE-AUTH-001 | Logowanie do aplikacji | Planowany |
| FE-AUTH-002 | Wylogowanie z aplikacji | Planowany |

## Testy (`FE-TEST`)

| ID | Use case | Status |
|---|---|---|
| FE-TEST-001 | Wyświetlenie listy testów | Implemented |
| FE-TEST-002 | Filtrowanie listy testów | Planowany |
| FE-TEST-003 | Dodanie testu | Planowany |
| FE-TEST-004 | Edycja testu | Planowany |
| FE-TEST-005 | Aktywowanie lub dezaktywowanie testu | Planowany |

## Zawodnicy (`FE-ATHLETE`)

| ID | Use case | Status |
|---|---|---|
| FE-ATHLETE-001 | Wyświetlenie listy zawodników | Planowany |
| FE-ATHLETE-002 | Filtrowanie listy zawodników | Planowany |
| FE-ATHLETE-003 | Dodanie zawodnika | Planowany |
| FE-ATHLETE-004 | Edycja zawodnika | Planowany |
| FE-ATHLETE-005 | Aktywowanie lub dezaktywowanie zawodnika | Planowany |

## Wyniki (`FE-RESULT`)

| ID | Use case | Status |
|---|---|---|
| FE-RESULT-001 | Wyświetlenie listy wyników | Planowany |
| FE-RESULT-002 | Filtrowanie listy wyników | Planowany |
| FE-RESULT-003 | Dodanie pojedynczego wyniku | Planowany |
| FE-RESULT-004 | Dodanie wielu wyników | Planowany |
| FE-RESULT-005 | Edycja wyniku | Planowany |
| FE-RESULT-006 | Usunięcie wyniku | Planowany |

## Przyszły zakres

Statystyki, rankingi i wykresy pozostają poza początkowym zakresem. Konkretne przypadki użycia i ich identyfikatory należy dodać po uzgodnieniu wymagań.
