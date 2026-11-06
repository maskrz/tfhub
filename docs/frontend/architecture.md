# Architektura frontendu

**Status:** Docelowe założenia początkowe
**Technologia:** Angular standalone; konfiguracja repozytorium wskazuje Angular 22, TypeScript 6 i SCSS

## 1. Organizacja według funkcji

Kod aplikacji należy grupować według funkcjonalnych obszarów: `auth`, `dashboard`, `athletes`, `tests` i `results`. W obrębie obszaru komponenty, modele i usługi należy dodawać wtedy, gdy są potrzebne; nie tworzyć pustych katalogów ani dodatkowych warstw dla samej struktury.

Proponowany układ zgodny z istniejącym katalogiem `src/` i aplikacją standalone:

```text
src/
├── app/
│   ├── core/
│   │   ├── auth/
│   │   └── http/
│   ├── shared/
│   │   └── ui/
│   ├── features/
│   │   ├── auth/
│   │   ├── dashboard/
│   │   ├── athletes/
│   │   ├── tests/
│   │   └── results/
│   ├── app.config.ts
│   ├── app.routes.ts
│   └── app.ts
├── main.ts
└── styles.scss
```

To propozycja, nie wymóg tworzenia wszystkich katalogów od razu. Konkretne nazwy plików mogą wynikać z konwencji Angular CLI używanej w danym obszarze.

## 2. Komponenty i logika aplikacji

- Komponenty ekranów koordynują interakcje w ramach funkcji.
- Komponenty prezentacyjne odpowiadają za wyświetlanie danych i zdarzenia użytkownika; nie powinny zawierać komunikacji HTTP ani powielać reguł biznesowych.
- Logikę specyficzną dla funkcji umieszczać w jej obszarze. Wspólne elementy UI wprowadzać do `shared/ui` dopiero wtedy, gdy faktycznie są współdzielone.
- Unikać globalnego zarządzania stanem i dodatkowych bibliotek bez uzasadnionej potrzeby.

## 3. API i modele

- Komunikacja z backendem odbywa się przez dedykowane usługi API dla funkcji, np. `AthleteApiService`, `TestApiService` i `ResultApiService`.
- Komponenty nie wykonują bezpośrednio żądań HTTP.
- TypeScriptowe typy i interfejsy odzwierciedlają publiczne kontrakty API. Nie przenosić do frontendu wewnętrznych modeli encji JPA.
- Kontrakty backendu i specyfikacje use case'ów backendu są źródłem prawdy; zmiany kontraktów uzgadniać i dokumentować.
- Sesja logowania, obsługa cookies i CSRF muszą być zgodne z [wymaganiami bezpieczeństwa](../security.md). Szczegóły integracji z backendem są TBD do czasu implementacji Spring Security.

## 4. Routing, formularze i walidacja

- Routing konfigurować w `app.routes.ts`, korzystając z routingu Angular standalone i leniwego ładowania funkcji tam, gdzie jest to uzasadnione.
- Trasy wymagające zalogowania zabezpieczyć mechanizmem klienta; autoryzacja serwerowa pozostaje obowiązkowa i nie może zależeć od guarda frontendu.
- Dla złożonych formularzy preferować Angular Reactive Forms, które są już dostępne w zależnościach projektu.
- Walidację klienta stosować dla użyteczności, zgodnie z kontraktem API. Backend zawsze ponownie waliduje dane.

## 5. Błędy i stany widoków

- Obsługę powtarzalnych zachowań HTTP, w tym błędów API i przypadków sesji, realizować przez istniejące mechanizmy Angular, np. interceptory, bez duplikowania logiki w każdym komponencie.
- Każdy widok pobierający dane powinien rozróżniać ładowanie, błąd, brak wyników i dostępne dane.
- Błędy prezentować w sposób zrozumiały i nie ujawniać szczegółów implementacyjnych.
- Szczegółowe zachowanie odświeżenia sesji lub ponownego logowania pozostaje TBD.

## 6. Testowanie

- Testować zachowanie komponentów, walidację formularzy i usługi komunikujące się z API.
- Testy powinny być niezależne od prawdziwego backendu i danych lokalnych; żądania HTTP zastępować kontrolowanymi odpowiedziami testowymi.
- Korzystać z konfiguracji testowej istniejącej w repozytorium: Angular CLI z builderem unit-test i Vitest. Nie dodawać odrębnego frameworka bez uzasadnienia i akceptacji.
