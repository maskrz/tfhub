# Wymagania frontendowe

**Status:** Wersja początkowa
**Zakres:** Wymagania dla aplikacji Angular; bez szczegółowych specyfikacji ekranów

## 1. Cel i użytkownicy

TFHub wspiera trenera w zarządzaniu zawodnikami, definicjami testów lekkoatletycznych i wynikami. Początkową grupą użytkowników jest jeden trener korzystający z jednego konta.

Aplikacja będzie dostępna z publicznego Internetu, ale korzystanie z niej musi wymagać uwierzytelnienia. Rejestracja publiczna i zarządzanie rolami nie należą do pierwszego wydania.

## 2. Urządzenia i kierunek wizualny

- Projektowanie rozpoczyna się od małych ekranów telefonów.
- Interfejs musi dostosowywać się także do tabletów i komputerów.
- Kierunek wizualny: czytelny, sportowy panel trenerski, który ułatwia szybkie wprowadzanie i przeglądanie danych.
- Kolorystyka, typografia, ikony i szczegółowy system wizualny pozostają do ustalenia w dokumencie [UI Design](ui-design.md).

## 3. Zakres funkcjonalny pierwszego wydania

Główne obszary aplikacji:

- **Zawodnicy** – przeglądanie, filtrowanie, dodawanie, edycja oraz aktywowanie i dezaktywowanie.
- **Testy** – przeglądanie, filtrowanie, dodawanie, edycja oraz aktywowanie i dezaktywowanie.
- **Wyniki** – przeglądanie i filtrowanie, dodawanie pojedynczego wyniku lub grupy wyników, edycja oraz usuwanie.
- **Uwierzytelnianie** – logowanie i wylogowanie.
- **Dashboard** – główny punkt wejścia do aplikacji; zakres i zawartość wskaźników są TBD.

Frontend odwzorowuje istniejące kontrakty API oraz reguły określone w [dokumentacji backendu](../backend/README.md) i [use case'ach backendu](../backend/use-cases/README.md). Backend pozostaje źródłem prawdy dla danych, walidacji i reguł biznesowych. Frontend może zapewniać walidację dla wygody użytkownika, ale nie może zastępować walidacji serwerowej.

## 4. Poza zakresem początkowego wydania

Statystyki, rankingi i wykresy są odroczone do przyszłego zakresu. Nie należy zakładać konkretnych obliczeń, sposobów porównywania ani prezentacji danych bez zatwierdzonego wymagania.

## 5. Zachowanie interfejsu

- Błędy API i walidacji muszą być przedstawiane jasno, bez ujawniania technicznych szczegółów lub danych wewnętrznych.
- Formularz powinien wskazywać błędy przy odpowiednich polach, zachowując możliwość poprawienia danych.
- Podczas pobierania lub wysyłania danych należy pokazać stan ładowania i zapobiec niezamierzonym powtórzeniom operacji zmieniających dane.
- Widoki list powinny odróżniać stan ładowania, błąd pobierania, pustą listę i listę z danymi.
- Stan pusty powinien informować, że brak danych jest poprawnym wynikiem, i w odpowiednim kontekście wskazywać dostępną akcję.
- Po operacji użytkownik otrzymuje czytelne potwierdzenie sukcesu albo informację o błędzie.
- Treść i ton komunikatów oraz zachowanie po wygaśnięciu sesji są TBD, z zastrzeżeniem wymagań bezpieczeństwa z [dokumentu bezpieczeństwa](../security.md).
