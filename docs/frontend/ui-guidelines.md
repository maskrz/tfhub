# Wspólne wytyczne UI

**Status:** Początkowa specyfikacja
**Zakres:** Wspólny układ aplikacji, kierunek wizualny i zasady kompozycji widoków

## 1. Kierunek wizualny

TFHUB powinien wyglądać jak nowoczesna, minimalistyczna aplikacja do zarządzania sportem: czytelna, uporządkowana i nastawiona na sprawną pracę z danymi.

Wspólny język wizualny:

- ciemny, grafitowy nagłówek;
- jasne tło obszaru roboczego;
- białe powierzchnie treści, tabel i kart;
- fioletowy kolor akcentu dla głównych akcji, odnośników i aktywnej nawigacji, zgodny z logo TFHub;
- subtelne obramowania, oszczędne cienie i umiarkowanie zaokrąglone narożniki;
- czytelna typografia i swobodne odstępy;
- stonowane kolory statusów; kolor nie może być jedynym nośnikiem informacji.
- logo TFHub w lewym górnym rogu nagłówka i dopasowana ikona karty przeglądarki.

Nagłówek używa banera TFHub z `tfhub-frontend/public/tfhub-banner.png`. Ikona favicon pochodzi z wybranego znaku z arkusza identyfikacji i jest udostępniana jako `favicon.ico` oraz `favicon.png`.

Dołączony zrzut ekranu jest odniesieniem dla układu widoku Zarządzanie testami: [Test Management](screenshots/test-management.png). Przedstawione na nim zielone akcenty i nawigacja są historyczne; obowiązuje kierunek opisany w tym dokumencie. Przykładowe nazwy, kategorie, statusy i wartości są ilustracyjne; nie ustanawiają reguł domenowych ani obsługiwanych wartości.

Podstawowy fiolet to `#6A1B9A`, a stan hover/pressed to `#581583`. Na ciemnym nagłówku wskaźniki i fokus używają `#C49BFF`; jasny fiolet `#F4EBFA` może wyróżniać zaznaczenie. Zielony pozostaje kolorem semantycznym sukcesu i stanów aktywnych lub zakończonych; czerwony służy błędom i akcjom destrukcyjnym. Kolory wykresów zachowują znaczenie danych.

Docelowe wspólne zasady układu to maksymalna szerokość 80 rem z wyjątkowym wariantem 100 rem dla gęstych widoków, odstępy boczne 1 rem na telefonie i 2 rem na komputerze oraz skala odstępów .25, .5, .75, 1, 1.5, 2 i 3 rem. Tytuł strony powinien mieć rozmiar `clamp(1.75rem, 3vw, 2.25rem)`, tytuł sekcji 1.25–1.5 rem, a tekst bazowy 1 rem z interlinią 1.5. Karty mają subtelne obramowanie, promień .75 rem i zwykle 1–1.5 rem odstępu wewnętrznego. Istniejące widoki będą dostosowywane stopniowo; nie wszystkie stosują jeszcze te wartości.

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
- główną nawigację zgodną z rolą;
- obszar użytkownika/konta.

Aktywny element nawigacji musi być widoczny i rozpoznawalny również bez polegania wyłącznie na kolorze. Nagłówek jest częścią powłoki aplikacji, nie poszczególnych widoków.

Główna nawigacja i przycisk „Menu” są widoczne tylko po zalogowaniu; marka i obszar logowania pozostają widoczne również dla niezalogowanych.

Na wąskich ekranach przycisk „Menu” rozwija pionową nawigację z tymi samymi elementami zależnymi od roli, bez wymuszania poziomego przewijania strony. Wybór odnośnika lub kliknięcie poza menu zamyka nawigację; Escape zamyka najpierw otwarte podmenu, a następnie nawigację, przywracając fokus do odpowiedniego przycisku.

Kolejność dla ADMIN to „Dashboard”, „Zawodnicy”, „Treningi”, „Wyniki” i menu „Konfiguracja” z pozycjami „Definicje sprawdzianów”, „Konkurencje PZLA” i „Zarządzanie kontami”. Dla COACH menu „Konfiguracja” jest niedostępne; ATHLETE zachowuje odnośnik „Moje wyniki”. Menu użytkownika zawiera „Zmiana hasła” i „Wyloguj”.

Menu „Konfiguracja” otwiera się przyciskiem zarówno na komputerze, jak i w nawigacji mobilnej. Wybranie odnośnika, kliknięcie poza menu lub Escape zamyka je; Escape przywraca fokus do jego przycisku. W nawigacji mobilnej Escape zamyka najpierw menu „Konfiguracja”, a kolejne naciśnięcie zamyka nawigację. Dostęp do wszystkich pozycji pozostaje ograniczony do ADMIN.

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

## 8. Przyciski i akcje

Przyciski o tej samej roli korzystają ze wspólnych stylów i tokenów z globalnego arkusza `src/styles.scss`. Stosować klasy współdzielone, zamiast definiować odrębne kolory, odstępy i stany dla każdej akcji.

### Hierarchia

- **Podstawowy (primary):** główna akcja w danym kontekście, np. „Dodaj test” lub „Zapisz”. Fioletowe tło `#6A1B9A`, biały tekst i brak widocznego obramowania; po najechaniu tło zmienia się na `#581583`.
- **Drugorzędny (secondary):** akcja wspierająca, np. „Edytuj” lub „Anuluj”. Białe lub bardzo jasne tło, subtelna neutralna ramka i ciemny grafitowy tekst; po najechaniu tło delikatnie się zmienia.
- **Status aktywności:** kontrolka przełączająca, nie standardowy przycisk akcji. Zielony tor oznacza stan aktywny, szary — nieaktywny, a biały wskaźnik położeniem pokazuje bieżący stan. Tekst stanu i dostępne nazwy/stan ARIA muszą jednoznacznie przekazywać, czy element jest aktywny. Zmiana koloru lub położenia ma subtelne przejście. Czerwieni nie używa się dla zwykłej aktywacji ani dezaktywacji.

Nie podkreślać wizualnie każdej akcji. Przycisk zamknięcia modalu może używać wariantu ikonowego drugorzędnego. Ponowienie operacji w stanie błędu może być podstawową akcją tego stanu.

### Wspólne stany interakcji

- **Domyślny:** konsekwentna wysokość, odstępy wewnętrzne, promień zaokrąglenia, krój pisma i wyrównanie. Elementy o tej samej roli mają ten sam wygląd.
- **Hover:** subtelna zmiana tła lub odcienia; nie polegać wyłącznie na zmianie kursora.
- **Focus-visible:** wyraźny obrys widoczny podczas nawigacji klawiaturą i z odpowiednim odstępem od kontrolki.
- **Disabled:** kontrolka jest wizualnie przygaszona, nie reaguje na hover i nie uruchamia akcji.
- **Loading:** jeśli operacja trwa, zapobiec ponownemu wysłaniu, wyłączyć kontrolkę i pokazać zrozumiały tekst ładowania, np. „Zapisywanie…”. Nie przedstawiać stanu oczekiwania jako sukcesu.

Zachować czytelny kontrast tekstu oraz stanów. Animacje powinny być krótkie i respektować ustawienie ograniczenia ruchu systemowego. Kontrolki przełączające muszą obsługiwać klawiaturę i dostępny stan, np. `role="switch"` z `aria-checked`.

Dialogi formularzy powinny używać natywnego dialogu modalnego z dostępną nazwą, zatrzymywać fokus wewnątrz i przywracać go po zamknięciu. Escape zamyka najpierw otwartą listę wyboru, a dopiero następne naciśnięcie dialog; nie zamyka dialogu podczas zapisywania. Poziomo przewijane tabele danych powinny mieć nazwany, dostępny z klawiatury obszar przewijania.

## 9. Pola formularzy i filtry

- Wszystkie pola tekstowe, wyszukiwarki i listy wyboru korzystają ze wspólnej definicji w `src/styles.scss`: jednakowa wysokość 3,25 rem, białe tło, neutralne obramowanie, grafitowy tekst i ten sam promień narożnika. Definicję stosuje się niezależnie od tego, czy pole znajduje się w filtrze, formularzu, czy modalu.
- Klasa `.field-control` zapewnia wspólną bazę dla pól tekstowych i list wyboru. Klasa `.field-control-shell` zapewnia tę samą bazę dla wyszukiwarek z ikoną; wewnętrzne pole tekstowe pozostaje bez własnego obramowania.
- Fokus klawiatury używa wyraźnego fioletowego obrysu z odstępem od kontrolki; pola nieprawidłowe zachowują czytelne czerwone obramowanie. Na ciemnych powierzchniach należy użyć jasnego fioletu.
- Pola wyboru używające własnej ikony strzałki powinny pozostawić 0,9 rem odstępu od prawej krawędzi do ikony oraz odpowiednie miejsce wewnątrz pola, aby tekst nie nachodził na strzałkę. Wspólna klasa `.select-with-arrow` w `src/styles.scss` zapewnia ten sam wygląd w filtrach i formularzach.
- Etykiety filtrów są umieszczane nad polami, nie obok nich. Wspólna klasa `.filter-field` z `src/styles.scss` ustala układ pionowy, odstęp i wygląd etykiety dla wyszukiwarek, list wyboru, pól daty/liczbowych i przełączników filtrujących. Przełącznik jest wyrównany pionowo w obszarze kontrolki o wysokości wspólnej z pozostałymi polami; jego etykieta znajduje się nad nim. Należy używać `.filter-field` we wszystkich widokach zamiast definiować lokalne warianty etykiet.

## 10. Otwarte kwestie

- Konkretne breakpointy oraz szczegółowe zasady ikon i wyjątków układu.
