# Wymagania bezpieczeństwa

**Status:** Wymagania docelowe; funkcje bezpieczeństwa nie są jeszcze wdrożone

## 1. Uwierzytelnianie i zakres konta

- Publiczny dostęp do aplikacji nie oznacza dostępu do danych: wszystkie funkcje aplikacyjne i chronione endpointy API wymagają uwierzytelnienia.
- W początkowej wersji istnieje jedno konto użytkownika.
- Nie ma publicznej rejestracji, samoobsługowego tworzenia kont ani zarządzania rolami.
- Logowanie i wylogowanie muszą być dostępne jako jawne operacje.
- Sposób początkowego utworzenia konta, zmiany hasła i odzyskania dostępu jest TBD; nie udostępniać publicznej rejestracji jako domyślnego rozwiązania.

## 2. Docelowa architektura

- Uwierzytelnianie i ochrona endpointów backendu mają być oparte na Spring Security.
- Dla przeglądarkowej aplikacji przewidziana jest sesja serwerowa identyfikowana przez bezpieczne cookie, nie token przechowywany w kodzie lub trwałym magazynie przeglądarki.
- Cookie sesyjne musi mieć `HttpOnly`, `Secure` w produkcji oraz odpowiednią politykę `SameSite`. `Lax` jest proponowanym punktem wyjścia przy tym samym originie frontend/API; ostateczna konfiguracja zależy od domeny i przepływu logowania.
- Chronione endpointy API muszą odrzucać żądania bez prawidłowej sesji.
- Dokładny zakres endpointów publicznych, takich jak logowanie i health check, należy ustalić przy implementacji.

## 3. CSRF, hasła i sekrety

- Włączyć ochronę CSRF dla operacji zmieniających stan, zgodnie z użytym mechanizmem sesyjnym.
- Sposób przekazywania i odświeżania tokenu CSRF między Angular i backendem jest TBD i musi być zaprojektowany przed wdrożeniem logowania.
- Hasła przechowywać wyłącznie w postaci bezpiecznie haszowanej, przy użyciu mechanizmu i parametrów obsługiwanych przez Spring Security.
- Hasła jawne, klucze, tokeny i inne sekrety nie mogą trafiać do repozytorium, logów ani kodu frontendowego.
- Sekrety i dane konta konfigurować poza repozytorium, z kontrolą dostępu odpowiednią dla środowiska.

## 4. Ochrona dostępu i infrastruktury

- W produkcji udostępniać aplikację przez HTTPS.
- Ograniczyć dostęp sieciowy do PostgreSQL do backendu i wymaganych zadań administracyjnych; baza nie powinna być publicznym endpointem.
- Ograniczyć próby logowania przez throttling lub równoważny mechanizm ograniczający nadużycia. Progi i sposób czasowej blokady są TBD.
- Komunikaty błędów logowania nie powinny ujawniać, czy konkretna nazwa użytkownika istnieje.
- Nie logować haseł, identyfikatorów sesji ani innych sekretów.

## 5. Stan wdrożenia

W repozytorium na dzień przygotowania dokumentacji nie znaleziono konfiguracji Spring Security ani implementacji logowania/sesji. Ten dokument określa wymagania, a nie zmianę działającego backendu. Wdrożenie wymaga osobnego zakresu i testów bezpieczeństwa.
