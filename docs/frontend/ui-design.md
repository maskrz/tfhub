# Założenia projektu UI

**Status:** Wersja początkowa
**Zakres:** Wspólne zasady wizualne i interakcyjne; bez szczegółowych makiet

## 1. Kierunek

TFHub powinien przypominać nowoczesny, sportowy panel trenerski: być czytelny, uporządkowany i nastawiony na pracę z danymi, bez wizualnego przeładowania. Szczegółowe kolory, typografia i identyfikacja wizualna pozostają TBD.

## 2. Responsywność

- Stosować podejście mobile-first.
- Najważniejsze akcje, filtry i informacje muszą być dostępne na małym ekranie bez poziomego przewijania.
- Na telefonach preferować karty dla elementów list i układy formularzy w jednej kolumnie.
- Na większych ekranach można używać tabel, jeżeli ułatwiają porównywanie rekordów; należy zachować dostępność i czytelność.
- Układ powinien dostosowywać się płynnie do tabletów i komputerów.

## 3. Nawigacja

Główna nawigacja zapewnia przejście do:

- Dashboardu
- Zawodników
- Testów
- Wyników

Sposób prezentacji nawigacji na telefonie i większych ekranach zostanie doprecyzowany podczas projektowania ekranów.

## 4. Wspólne komponenty i interakcje

- Używać spójnych elementów formularzy, przycisków, filtrów, kart, tabel, dialogów i komunikatów.
- Formularze powinny jasno rozróżniać pola wymagane, błędy walidacji i stan wysyłania.
- Operacje nieodwracalne, takie jak usunięcie wyniku, powinny mieć odpowiednie potwierdzenie.
- Powiadomienia sukcesu i błędów powinny być widoczne, zrozumiałe i dostępne dla technologii asystujących.
- Wszystkie widoki powinny mieć rozpoznawalny stan ładowania, błędu i braku danych; nie wyświetlać pustej przestrzeni jako jedynej wskazówki.
- Szczegółowe decyzje dostępności, animacji i zachowania dialogów są TBD; należy je ustalić przed implementacją odpowiednich ekranów.
