# Widok: Zarządzanie testami

**Status:** Specyfikacja UI; implementacja nie została wykonana
**Język interfejsu:** Polski
**Odniesienie wizualne:** [Zrzut ekranu widoku Testy](../screenshots/test-management.png)

Zrzut jest odniesieniem dla układu, hierarchii wizualnej, odstępów, kolorów, nawigacji, obszaru wyszukiwania, tabeli i widocznych akcji. Widoczne na nim nazwy testów, kategorie, statusy i inne wartości są wyłącznie ilustracyjne; o obsługiwanych danych i zachowaniu decydują istniejący model oraz kontrakty backendu.

## 1. Cel widoku

Widok umożliwia trenerowi wyszukiwanie i przeglądanie definicji testów oraz przejście do dodawania lub edycji testu. Aktywacja i dezaktywacja odbywają się w formularzu dodawania/edycji, a nie bezpośrednio na liście.

Widok jest renderowany przez wspólną powłokę aplikacji. Nie zawiera własnego nagłówka, stopki ani głównej nawigacji.

## 2. Układ strony

Na początku strony znajduje się obszar nagłówka widoku:

- tytuł: **Testy**;
- podtytuł: **Zarządzaj testami sportowymi**;
- główna akcja: **Dodaj test**.

Główna akcja prowadzi do formularza dodawania testu. Szczegóły tego formularza określa osobna specyfikacja; ten dokument nie dodaje mu pól ani zachowań.

Poniżej znajduje się obszar wyszukiwania i filtrowania, a następnie tabela testów.

## 3. Wyszukiwanie i filtrowanie

Obszar filtrów zawiera:

- pole wyszukiwania z ikoną lupy i polskim tekstem pomocniczym, np. „Szukaj testu…”;
- przełącznik **Pokaż nieaktywne**.

Zachowanie:

- Domyślnie przełącznik jest wyłączony, a lista zawiera wyłącznie aktywne testy.
- Po włączeniu przełącznika lista obejmuje aktywne i nieaktywne testy.
- Wyszukiwanie po nazwie i filtr statusu działają łącznie (AND).
- Wyszukiwanie nazw korzysta z istniejącego backendowego wyszukiwania częściowego, bez rozróżniania wielkości liter; puste lub białe znaki nie stanowią kryterium.
- Dla zgodności z `POST /api/tests/search`: przełącznik wyłączony oznacza filtr `active: true`; włączony oznacza pominięcie filtra `active`, ponieważ backend bez tego kryterium zwraca oba statusy.
- Nie wprowadzać sortowania ani paginacji w UI jako nowego wymagania — obecna specyfikacja UC-103 ich nie definiuje.

## 4. Tabela testów

Na szerokim ekranie testy są prezentowane w białej, czytelnej tabeli z subtelnymi separatorami. Zawiera ona:

| Kolumna | Zawartość |
|---|---|
| Nazwa testu | Nazwa testu |
| Akcja | Przycisk „Edytuj” |

Każdy wiersz ma dokładnie jedną widoczną akcję: **Edytuj**. Nie dodawać przycisku aktywacji/dezaktywacji, usuwania ani innych działań do wiersza. „Edytuj” prowadzi do formularza edycji testu.

Tabela pokazuje wyłącznie nazwę testu i akcję „Edytuj”. Nie wyświetla pod nazwą kategorii ani statusu. Status pozostaje dostępny w danych i służy do filtrowania oraz stylowania wiersza.

## 5. Wygląd statusów

- Aktywne wiersze używają standardowego jasnego tła.
- Nieaktywne wiersze mają subtelne, jasnoczerwone tło, które nie dominuje nad treścią.
- Kontrast nazwy testu i akcji musi pozostać czytelny na obu tłach.

## 6. Reguły backendu i zakres

Widok korzysta z istniejącego kontraktu wyszukiwania `POST /api/tests/search` i danych `TestResponse`. Nie zmienia reguł backendu:

- Kategorie są wartościami `TIME`, `DISTANCE` i `SPEED`; warstwa prezentacji może pokazywać polskie etykiety, np. „Czas”, „Odległość” i „Szybkość”.
- Testy dezaktywuje się, a nie usuwa; historyczne wyniki pozostają dostępne.
- Nieaktywnego testu nie można użyć do dodania ani aktualizacji wyniku.
- Jeśli test ma wyniki, nie można zmienić jego jednostki ani kategorii, zgodnie z UC-104.
- Aktywacja/dezaktywacja należy do formularza testu, nie do tabeli.

Zrzut referencyjny zawiera przykładową etykietę „Wysokość”, która nie odpowiada żadnej z kategorii backendu. To drobna niespójność materiału wizualnego, nie wymaganie dotyczące kategorii ani przeszkoda dla specyfikacji widoku. Nie wprowadzać tej wartości do UI jako obsługiwanej kategorii. Dla `SPEED` można użyć polskiej etykiety „Szybkość”.

## 7. Stany widoku

Widok powinien odróżniać:

- ładowanie wyników;
- listę z dopasowanymi testami;
- brak testów spełniających kryteria;
- błąd pobierania lub wyszukiwania;
- wysyłanie operacji nawigacyjnej/formularzowej, jeśli dotyczy.

Pusta lista po filtrze jest prawidłowym wynikiem i nie powinna być przedstawiana jako błąd. Komunikaty dla użytkownika są po polsku. Nie wymyślać dodatkowych operacji naprawczych ani zmian danych w stanie pustym.

## 8. Responsywność

- Na komputerze i szerszym tablecie używać tabeli z nazwą testu w pierwszej kolumnie i akcją w ostatniej.
- Na wąskim ekranie układać te same dane jako pionowe karty, bez poziomego przewijania całej strony.
- Karta zawiera nazwę testu i ten sam pojedynczy przycisk „Edytuj”.
- Pole wyszukiwania powinno zajmować dostępną szerokość; przełącznik pozostaje czytelny i łatwy do obsługi dotykiem.
- Akcja „Dodaj test” pozostaje widoczna i osiągalna w układzie mobilnym.
- Nie dodawać innych akcji ani danych tylko dla wersji mobilnej.

## 9. Odpowiedzialności komponentów

- `AppShellComponent` dostarcza wspólny nagłówek, stopkę i `RouterOutlet`.
- `HeaderComponent` zapewnia globalną nawigację; widok testów jej nie powiela.
- Komponent strony Zarządzanie testami odpowiada za tytuł, wyszukiwanie, przełącznik statusu, stan listy i kompozycję tabeli/kart.
- Komunikacja z backendem odbywa się przez testowy serwis Angular, a nie bezpośrednio z komponentu prezentacyjnego.
- Wspólnych pól, przycisków lub tabel nie należy abstrahować bez rzeczywistego ponownego użycia.
- Backend pozostaje autorytatywny dla walidacji i reguł testów.

## 10. Otwarte kwestie

- Dokładny tekst i zachowanie stanu pustego oraz błędu pobierania.
- Zachowanie i czas opóźnienia wyszukiwania podczas wpisywania; backend nie narzuca interakcji UI.
- Szczegółowy wariant nawigacji mobilnej i breakpointy, zgodnie ze wspólnymi [wytycznymi UI](../ui-guidelines.md).
