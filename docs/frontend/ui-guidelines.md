# Wspólne wytyczne UI

**Status:** Początkowa specyfikacja
**Zakres:** Wspólny układ aplikacji, kierunek wizualny i zasady kompozycji widoków

## 1. Kierunek wizualny

TFHUB powinien wyglądać jak nowoczesna, minimalistyczna aplikacja do zarządzania sportem: czytelna, uporządkowana i nastawiona na sprawną pracę z danymi.

Wspólny język wizualny:

- ciemny, grafitowy nagłówek;
- jasne tło obszaru roboczego;
- białe powierzchnie treści, tabel i kart;
- zielony kolor akcentu dla głównych akcji i aktywnej nawigacji;
- subtelne obramowania, oszczędne cienie i umiarkowanie zaokrąglone narożniki;
- czytelna typografia i swobodne odstępy;
- stonowane kolory statusów; kolor nie może być jedynym nośnikiem informacji.

Dołączony zrzut ekranu jest głównym odniesieniem wizualnym dla widoku Zarządzanie testami: [Test Management](screenshots/test-management.png). Służy do określenia układu, hierarchii wizualnej, odstępów, kolorów, nawigacji, obszaru wyszukiwania, struktury tabeli i widocznych akcji. Przykładowe nazwy, kategorie, statusy i wartości na zrzucie są ilustracyjne; nie ustanawiają reguł domenowych ani obsługiwanych wartości.

## 2. Wspólny układ aplikacji

Cała aplikacja korzysta ze wspólnego `AppShellComponent`:

```text
AppShellComponent
├── HeaderComponent
├── RouterOutlet
│   └── aktualnie wybrany widok funkcji
└── FooterComponent
```

`AppShellComponent` renderuje `HeaderComponent` i `FooterComponent` dokładnie raz. Angular `RouterOutlet` renderuje aktualny widok nawigacji pomiędzy nimi. Widoki funkcjonalne zawierają wyłącznie własną treść strony; nie implementują ponownie nagłówka, stopki ani głównej nawigacji.

Pierwsze widoki funkcjonalne to:

- Panel główny (Dashboard);
- Zarządzanie testami;
- Zarządzanie zawodnikami;
- Wprowadzanie wyników;
- Lista wyników.

Są to zakresy planowane; poza specyfikacją widoku Zarządzanie testami nie oznacza to, że pozostałe widoki są już zaprojektowane lub zaimplementowane.

## 3. Nagłówek

Wspólny nagłówek zawiera:

- markę/logo TFHUB;
- główną nawigację: „Panel główny”, „Testy”, „Zawodnicy”, „Wyniki”;
- obszar użytkownika/konta.

Aktywny element nawigacji musi być widoczny i rozpoznawalny również bez polegania wyłącznie na kolorze. Nagłówek jest częścią powłoki aplikacji, nie poszczególnych widoków.

Na wąskich ekranach nagłówek ma zachować dostęp do tych samych elementów bez wymuszania poziomego przewijania całej strony. Szczegółowy wariant prezentacji nawigacji mobilnej pozostaje do ustalenia przy implementacji.

## 4. Stopka

Wspólna stopka zawiera:

- markę TFHUB;
- krótki opis aplikacji;
- wersję aplikacji.

Stopka jest renderowana przez `AppShellComponent` i nie może być powielana w widokach funkcjonalnych.

## 5. Kompozycja komponentów

- `AppShellComponent` odpowiada za wspólną ramę aplikacji oraz miejsce dla `RouterOutlet`.
- `HeaderComponent` odpowiada za markę, główną nawigację, oznaczenie aktywnego widoku i obszar konta.
- `FooterComponent` odpowiada za wspólne informacje w stopce.
- Widok funkcjonalny odpowiada za treść i interakcje właściwe dla swojej strony.
- Widoki funkcjonalne powinny być niezależnymi komponentami Angular, najlepiej standalone, zgodnie z konwencjami architektury frontendu.
- Wspólny komponent w `shared` należy wprowadzać wtedy, gdy jest rzeczywiście używany w więcej niż jednym miejscu. Nie tworzyć osobnego komponentu dla każdego prostego elementu interfejsu.
- Komunikacja z API pozostaje w serwisach Angular, a reguły biznesowe i walidacja autorytatywna pozostają po stronie backendu.

## 6. Responsywność

- Projektować mobile-first i dostosować układ do telefonów, tabletów i komputerów.
- Na małych ekranach układać treść w jednej kolumnie, zapewnić czytelne cele dotykowe i nie wymagać poziomego przewijania całej strony.
- Dla danych listowych preferować karty na telefonach; na większych ekranach stosować tabele, jeśli ułatwiają porównywanie rekordów.
- Zachować kolejność informacji, dostępność akcji oraz rozpoznawalność stanów również w układzie mobilnym.
- Nie dodawać mobilnych funkcji lub akcji, których nie określają wymagania danego widoku.
- Konkretne szerokości breakpointów nie są na tym etapie ustalone.

## 7. Język interfejsu

Cały tekst widoczny dla użytkownika — nawigacja, nagłówki, etykiety, akcje, statusy, błędy, puste stany i powiadomienia — musi być po polsku. Nazwy techniczne API, typów i wartości enumów pozostają zgodne z backendem i nie muszą być pokazywane bezpośrednio użytkownikowi.

Stosować spójne polskie nazewnictwo i poprawne znaki diakrytyczne. Techniczne identyfikatory i nazwy komponentów, takie jak `AppShellComponent`, pozostają w języku angielskim.

## 8. Otwarte kwestie

- Sposób zwijania głównej nawigacji na wąskich ekranach.
- Konkretne breakpointy i szczegółowy system typografii, kolorów oraz ikon.
- Zawartość i interakcje obszaru użytkownika/konta; uwierzytelnianie jest planowane i opisane w `../security.md`, ale nie jest zaimplementowane.
