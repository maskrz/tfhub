# Katalog use case'ów frontendu

**Status:** Katalog use case'ów frontendu; statusy poszczególnych przypadków określają ich implementację
**Konwencja ID:** `FE-<OBSZAR>-<NNN>`, np. `FE-AUTH-001`. Prefiks `FE` odróżnia przypadki użycia interfejsu od identyfikatorów backendu (`UC-001`, `UC-101`, `UC-201` itd.).

Katalog opisuje planowaną funkcjonalność interfejsu. Kontrakty API i reguły biznesowe pozostają zgodne z dokumentacją backendu. Nie należy dopisywać szczegółowych wymagań ekranów ani zmieniać reguł backendu bez uzgodnienia.

## Uwierzytelnianie (`FE-AUTH`)

| ID | Use case | Status |
|---|---|---|
| FE-AUTH-001 | Logowanie do aplikacji | Implemented |
| FE-AUTH-002 | Wylogowanie z aplikacji | Implemented |

## Testy (`FE-TEST`)

| ID | Use case | Status |
|---|---|---|
| FE-TEST-001 | Wyświetlenie listy testów | Implemented |
| FE-TEST-002 | Dodanie testu | Implemented |
| FE-TEST-003 | Edycja testu | Implemented |
| FE-TEST-005 | Aktywowanie lub dezaktywowanie testu z listy | Implemented |

## Zawodnicy (`FE-ATHLETE`)

| ID | Use case | Status |
|---|---|---|
| FE-ATHLETE-001 | Wyświetlenie listy zawodników | Implemented |
| FE-ATHLETE-002 | Dodanie zawodnika | Implemented |
| FE-ATHLETE-003 | Edycja zawodnika | Implemented |
| FE-ATHLETE-004 | Profil zawodnika | Implemented |

## Wyniki (`FE-RESULT`)

| ID | Use case | Status |
|---|---|---|
| FE-RESULT-001 | Wyświetlenie listy wyników | Implemented |
| FE-RESULT-002 | Dodanie pojedynczego wyniku | Implemented |
| FE-RESULT-003 | Dodanie wielu wyników | Implemented |
| FE-RESULT-004 | Edycja wyniku | Implemented |
| FE-RESULT-005 | Usunięcie wyniku | Implemented |

## Treningi (`FE-TRAINING`)

| ID | Use case | Status |
|---|---|---|
| FE-TRAINING-001 | Lista treningów | Implemented |
| FE-TRAINING-002 | Utworzenie treningu | Implemented |
| FE-TRAINING-003 | Edycja planu treningu | Implemented |
| FE-TRAINING-004 | Podgląd treningu | Implemented |
| FE-TRAINING-005 | Usunięcie treningu | Implemented |
| FE-TRAINING-006 | Import treningu ze zdjęcia | Implemented |
| FE-TRAINING-007 | Weryfikacja importu treningu | Implemented |
| FE-TRAINING-008 | Akceptacja lub odrzucenie importu treningu | Implemented |
| FE-TRAINING-009 | Uzupełnienie treningu | Implemented |

## Przyszły zakres

Statystyki, rankingi i wykresy pozostają poza początkowym zakresem. Konkretne przypadki użycia i ich identyfikatory należy dodać po uzgodnieniu wymagań.
