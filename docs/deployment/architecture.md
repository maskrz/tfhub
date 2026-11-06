# Docelowa architektura wdrożenia

**Status:** Plan; wdrożenie produkcyjne nie jest skonfigurowane w repozytorium

## 1. Założenia środowiska docelowego

- Hostem wdrożeniowym jest Raspberry Pi 5 z 8 GB RAM.
- Host łączy się z siecią przez Wi-Fi; niezawodność połączenia i odzyskiwanie usługi po przerwie w sieci wymagają sprawdzenia podczas uruchomienia.
- Usługi TFHub są uruchamiane przez Docker Compose.
- PostgreSQL przechowuje dane w trwałym wolumenie.
- Backend Spring Boot udostępnia API.
- Frontend jest budowany do plików produkcyjnych i serwowany przez Nginx.
- Nginx serwuje frontend oraz przekazuje żądania ścieżki `/api` do backendu.
- Cloudflare Tunnel zapewnia publiczny dostęp bez wymagania publicznego adresu IP ani przekierowywania portów przychodzących na routerze.
- Publiczny ruch korzysta z HTTPS.
- Domena zostanie zarejestrowana i podłączona do Cloudflare później; nazwa domeny i konfiguracja rekordów są TBD.

Szczegóły zasobów, wersji obrazów, limitów pamięci i adresów wewnętrznych kontenerów należy określić podczas implementacji wdrożenia na docelowym urządzeniu.

## 2. Przepływ ruchu

```text
Przeglądarka
    │ HTTPS
    v
Cloudflare
    │ Cloudflare Tunnel (połączenie inicjowane z hosta)
    v
Nginx ── /api ──> Spring Boot API ──> PostgreSQL
  └────── frontend build
```

Tunel łączy się wychodząco z infrastruktury Cloudflare; nie należy konfigurować przekierowania portów przychodzących na routerze. W produkcji baza danych powinna być dostępna tylko wewnątrz wymaganej sieci usług i nie powinna być wystawiona publicznie.

## 3. Rozdzielenie środowisk

- Środowiska deweloperskie i produkcyjne są odrębne pod względem konfiguracji, danych, sekretów i uruchamianych usług.
- Konfiguracja produkcyjna i sekrety nie mogą być osadzane w obrazie frontendu ani commitowane do repozytorium.
- Istniejący główny `docker-compose.yml` uruchamia PostgreSQL dla lokalnego developmentu. Nie należy traktować go jako gotowej konfiguracji produkcyjnej ani zmieniać go w ramach tej dokumentacji.
- Szczegóły oddzielnych plików Compose, procesu budowania i wdrażania są TBD.

## 4. Home Hub

- TFHub pozostaje niezależny od Home Hub: nie współdzieli jego kontenerów, konfiguracji, danych ani cyklu aktualizacji.
- Zmiany i wdrożenia TFHub nie mogą modyfikować, zastępować ani zakłócać działających usług Home Hub.
- Nie zakłada się, że Home Hub ma być obecnie dostępny publicznie.
- W przyszłości TFHub i Home Hub mogą korzystać ze wspólnego Cloudflare Tunnel oraz osobnych subdomen. Jest to możliwość przyszła, nie warunek obecnego wdrożenia.
- Sposób współdzielenia tunelu, Nginx i konfiguracji domen wymaga osobnego uzgodnienia przed realizacją.

## 5. Dane, kopie zapasowe i operacje

- Wolumen PostgreSQL zapewnia trwałość danych przy odtwarzaniu kontenera, ale sam w sobie nie jest kopią zapasową.
- Kopie zapasowe bazy muszą być przechowywane poza Raspberry Pi, aby awaria lub utrata hosta nie zniszczyła jedynej kopii danych.
- Harmonogram, retencja, szyfrowanie i procedura testowego odtworzenia kopii są TBD i muszą być ustalone przed produkcyjnym przechowywaniem danych.
- Monitoring powinien obejmować co najmniej dostępność aplikacji, kondycję bazy i błędy usług; narzędzia, alerty i retencja logów są TBD.
- Aktualizacje systemu, obrazów kontenerów i aplikacji należy wykonywać w kontrolowany sposób z możliwością odtworzenia poprzedniej wersji. Harmonogram, zatwierdzanie aktualizacji i procedura rollbacku są TBD.
