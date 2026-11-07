# Widok: Zarządzanie testami

**Status:** Implementacja zakończona
**Język interfejsu:** Polski
**Odniesienie wizualne:** [Zrzut ekranu widoku Testy](../screenshots/test-management.png); [modal testu](../screenshots/test-popup.png)

Zrzut jest odniesieniem dla układu, hierarchii wizualnej, odstępów, kolorów, nawigacji, obszaru wyszukiwania, tabeli i widocznych akcji. Widoczne na nim nazwy testów, kategorie, statusy i inne wartości są wyłącznie ilustracyjne; o obsługiwanych danych i zachowaniu decydują istniejący model oraz kontrakty backendu.

## 1. Cel widoku

Widok umożliwia trenerowi wyszukiwanie i przeglądanie definicji testów, dodawanie i edycję przez wspólny formularz w modalu oraz zmianę statusu przyciskiem w wierszu.

Widok jest renderowany przez wspólną powłokę aplikacji. Nie zawiera własnego nagłówka, stopki ani głównej nawigacji.

## 2. Układ strony

Na początku strony znajduje się obszar nagłówka widoku:

- tytuł: **Testy**;
- podtytuł: **Zarządzaj testami sportowymi**;
- główna akcja: **Dodaj test**.

Główna akcja **Dodaj test** jest przyciskiem primary i otwiera wspólny modal oraz formularz w trybie CREATE (FE-TEST-002).

Poniżej znajduje się obszar wyszukiwania i filtrowania, a następnie tabela testów.

## 3. Wyszukiwanie i filtrowanie

Obszar filtrów zawiera:

- pole wyszukiwania z ikoną lupy i polskim tekstem pomocniczym, np. „Szukaj testu…”;
- przełącznik **Pokaż nieaktywne**.

Zachowanie:

- Domyślnie przełącznik jest wyłączony, a lista zawiera wyłącznie aktywne testy.
- Po włączeniu przełącznika lista obejmuje aktywne i nieaktywne testy.
- Wyszukiwanie po nazwie i filtr statusu działają łącznie.
- Lista wszystkich testów (aktywnych i nieaktywnych) jest pobierana raz przy wejściu do widoku przez istniejący `POST /api/tests/search` z pustym żądaniem.
- Wyszukiwanie jest natychmiastowe i lokalne, bez rozróżniania wielkości liter; białe znaki na początku i końcu zapytania są pomijane.
- Zmiana tekstu, wyczyszczenie wyszukiwania ani przełączenie **Pokaż nieaktywne** nie powoduje kolejnych żądań API.
- Nie wprowadzać sortowania ani paginacji w UI jako nowego wymagania — obecna specyfikacja UC-103 ich nie definiuje.

## 4. Tabela testów

Na szerokim ekranie testy są prezentowane w białej, czytelnej tabeli z subtelnymi separatorami. Zawiera ona:

| Kolumna | Zawartość |
|---|---|
| Nazwa testu | Nazwa testu |
| Akcja | Przyciski „Edytuj” i statusowy |

Każdy wiersz ma dokładnie dwie kontrolki: przełącznik statusu oraz przycisk drugorzędny **Edytuj** po jego prawej stronie. Przełącznik pokazuje aktualny stan tekstem **Aktywny** lub **Nieaktywny**, a tor i pozycja białego wskaźnika przedstawiają ten sam stan. Jest dostępny jako przełącznik z etykietą i stanem ARIA; kliknięcie zmienia wyłącznie status. **Edytuj** otwiera wspólny modal i formularz w trybie EDIT (FE-TEST-003). Nie dodawać usuwania ani innych działań do wiersza.

Tabela pokazuje wyłącznie nazwę testu i akcje. Nie wyświetla pod nazwą kategorii ani statusu. Status pozostaje dostępny w danych i służy do filtrowania, stylowania wiersza oraz określenia etykiety akcji statusu.

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
- Aktywacja/dezaktywacja odbywa się kontrolką statusu w wierszu, niezależnie od formularza definicji testu.
- Zmiana statusu używa `PATCH /api/tests/{id}/status` z istniejącym `ChangeTestStatusRequest`; odpowiedź `TestResponse` aktualizuje element lokalnej kolekcji bez ponownego pobrania listy.
- Dodawanie i edycja korzystają z jednego wspólnego komponentu formularza w modalnym oknie. Tytuł to „Dodaj test” lub „Edytuj test”, zależnie od trybu. Modal ma osobne sekcje nagłówka, treści i stopki z akcjami: drugorzędną „Anuluj” i podstawową „Zapisz”.
- Formularz zawiera nazwę, jednostkę, kategorię i informację, czy wyższy wynik jest lepszy. W trybie CREATE nowy test jest aktywny zgodnie z backendem; status nie jest polem formularza. W trybie EDIT status również zmienia się poza formularzem przez akcję w wierszu.
- Błędy walidacji i API są prezentowane w modalu po polsku; modal pozostaje otwarty, a wprowadzone wartości są zachowywane.
- Zrzut modalu jest odniesieniem wizualnym dla prezentacji; zakres pól i reguły wynikają z kontraktów backendu, a nie z przykładowego zrzutu.

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
- Karta zawiera nazwę testu oraz przyciski „Edytuj” i statusowy.
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
- Szczegółowy wariant nawigacji mobilnej i breakpointy, zgodnie ze wspólnymi [wytycznymi UI](../ui-guidelines.md).
