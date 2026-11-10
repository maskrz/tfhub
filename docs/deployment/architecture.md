# Deployment Architecture

## 1. Purpose

This document describes the target deployment architecture for TFHub.

The initial deployment target is a Raspberry Pi 5 (8 GB RAM). TFHub runs as a Docker Compose stack and is initially reachable only from a browser on the Pi at `http://localhost:8081` (or the configured loopback port). Cloudflare Tunnel, public DNS, and external access are not configured by this deployment.

This document describes the implemented Compose architecture and the Raspberry Pi operating procedure. It does not claim that deployment has been tested on the target Pi.

## 2. Scope and principles

The initial local deployment must:

- run TFHub and its dependencies on the Raspberry Pi;
- provide local browser access without using a HomeCenter port;
- keep PostgreSQL and the Spring Boot API inaccessible directly from the public internet;
- use HTTPS for browser access if public access is enabled later;
- persist application data across container restarts and updates;
- keep secrets out of source control;
- support database backups that can be copied outside the Raspberry Pi;
- avoid interfering with the existing HomeCenter installation.

Keep the deployment simple. Do not introduce Kubernetes, Redis, a separate database server, a tunnel container, or additional infrastructure unless a concrete requirement justifies it.

## 3. High-level architecture

The current local request path is:

```text
Pi-local browser
    |
    | HTTP on 127.0.0.1:8081 (configurable)
    v
Nginx container (Angular static files)
    |
    | /api on backend-network
    v
Spring Boot API
    | database-network                 | backup-egress-network (outbound only)
    v                                  v
PostgreSQL container                 Google Drive via rclone crypt
```

Docker network membership limits which services can communicate:

```text
edge-network:     web (cloudflared may be added later)
backend-network:  web, api
database-network: api, db
```

The current service paths are `host loopback → web`, `web → api`, and `api → db`. Because `web` is not on `database-network`, it cannot reach PostgreSQL through Docker network membership. The backend and database networks are internal Docker networks. A future `cloudflared` service can be attached only to `edge-network`, where it can reach `web` but not `api` or `db`.

Responsibilities:

- **Cloudflare** and **cloudflared** are future optional ingress components; neither is configured now.
- **Nginx** serves the built Angular application and reverse-proxies `/api` requests to the Spring Boot API. The browser uses a same-origin API, so CORS configuration should not be needed.
- **Spring Boot** runs the backend and enforces application authentication and authorization rules. Cloudflare is not a replacement for application-level security.
- **PostgreSQL** stores application data and is reachable only by the backend over a private Docker network.

Production session cookies remain `Secure` and use the `__Host-TFHUBSESSION` prefix. Public use therefore requires HTTPS at the future ingress; do not weaken the cookie settings for local HTTP.

## 4. Docker Compose services

The production Compose stack contains these services:

| Service | Responsibility | Network exposure |
|---|---|---|
| `web` | Nginx serving Angular and proxying `/api` | Published on configurable host loopback port, default `127.0.0.1:8081` |
| `api` | Spring Boot application | Receives API traffic from `web`; connects to `db` |
| `db` | PostgreSQL database | Private access from `api` only |

The production stack is `docker-compose.prod.yml`. `docker-compose.yml` remains the separate local-development PostgreSQL setup. The PostgreSQL image uses official `postgres:17-alpine`; its initial admin credentials are used only by the database container, while the API connects as the non-superuser `TFHUB_DB_USER`.

### 4.1 Network boundaries

Attach only the following services to each network:

| Network | Members |
|---|---|
| `edge-network` | `web` (attach a future `cloudflared` service here) |
| `backend-network` | `web`, `api` |
| `database-network` | `api`, `db` |
| `backup-egress-network` | `api` |

The current membership permits `web → api`, `api → db`, and outbound backup traffic from `api`; PostgreSQL and the backend publish no host ports. The API's additional non-internal network is required for Google Drive uploads and is not shared with the web or database services. If a tunnel is added later, attach it only to `edge-network`. Do not attach a service to an additional network unless a concrete requirement justifies it.

Only Nginx publishes a port, bound to host loopback. `backend-network` and `database-network` are internal.

### 4.2 Persistent data

PostgreSQL data is stored in the named Docker volume `tfhub-prod-postgres-data`, mounted at `/var/lib/postgresql/data` in the database container.

The database volume is persistent application data. Do not remove it as part of routine deployment or cleanup commands. In particular, `docker compose down -v` must not be used casually because it removes named volumes.

Application containers should be replaceable: rebuilding or recreating the `web`, `api`, or `cloudflared` container must not delete PostgreSQL data.

## 5. Public access and Cloudflare Tunnel

Cloudflare Tunnel is not configured in the initial deployment. For a future public deployment, TFHub may be exposed through a Cloudflare Tunnel using a hostname configured in Cloudflare DNS.

Requirements:

- The Raspberry Pi establishes the tunnel using an outbound connection.
- No public IP address, inbound port forwarding, or directly exposed Raspberry Pi service is required.
- The public hostname is routed through the tunnel to the Nginx service.
- The tunnel credentials/token are supplied at runtime and must not be committed to Git.
- The tunnel should start automatically with the Compose stack and reconnect after temporary network interruptions.

A stable named hostname requires a domain configured in Cloudflare. Domain registration is a separate prerequisite; it is not assumed to have been completed.

If the Raspberry Pi loses internet connectivity, TFHub will be unavailable remotely until connectivity is restored. Wi-Fi is acceptable for the initial deployment, but connection stability and upload bandwidth should be checked in practice.

Cloudflare Tunnel provides the external access path; it does not remove the need for Spring Security, CSRF protection, secure cookies, or server-side session handling described in `docs/security.md`.

## 6. Nginx and API routing

Nginx is the single application entry point inside the Docker deployment.

It must:

- serve the Angular production build;
- route `/api` requests to the Spring Boot service;
- avoid exposing internal service ports;
- support Angular client-side routing by returning the application entry point for frontend routes;
- set or overwrite proxy headers deliberately rather than blindly forwarding client-supplied values.

The frontend and API should share the same public origin. For example, the browser loads the application from the configured TFHub hostname and sends API requests to the `/api` path on that hostname.

This avoids the need for a separate public API hostname and ordinary cross-origin browser requests.

## 7. Backend and database configuration

The Spring Boot service connects to PostgreSQL using the internal Compose service name and port, not `localhost`.

The production Compose file activates the `prod` Spring profile and supplies database connection values through environment variables. `application-prod.properties` uses the Compose hostname `db`, enables framework handling of the overwritten proxy headers, retains secure session cookies, and exposes only the existing Actuator health endpoint for internal container health checks. Nginx does not proxy Actuator paths, and neither API nor database has a published host port.

Requirements:

- use environment-specific Spring configuration where necessary;
- provide database credentials at runtime;
- keep `DB_PASSWORD` and other secrets out of committed files;
- use a dedicated PostgreSQL application user rather than the database superuser;
- store each account's Argon2id password hash in the `users` table; never place plaintext passwords in configuration or environment variables;
- keep Hibernate schema handling consistent with the backend architecture: Flyway manages schema changes and Hibernate validates the schema;
- do not expose database connection details or credentials in logs.

The production database must use a persistent named volume. Flyway manages schema migrations; the initial administrator and user/group/athlete relationships are provisioned through the documented local administrative procedure and are not seeded with default credentials.

## 8. Secrets and configuration

Secrets must not be stored in the Git repository or embedded in Docker images.

At minimum, production secrets include:

- PostgreSQL credentials;
- Cloudflare Tunnel credentials/token;
- any non-password secret strictly required by the controlled local account initialization procedure.

The account password is entered through the local initialization procedure and only its Argon2id hash is stored in PostgreSQL. Never put the plaintext account password in configuration or environment variables.

For production, copy `.env.example` to `.env.production`, set the `TFHUB_*` values, and restrict file access with `chmod 600`. The file is excluded by the root `.gitignore`. Keep the admin and application database passwords distinct; Compose passes only the application credentials to the API. The database init script runs only when the named volume is first created.

Keep both the development `.env` and production `.env.production` untracked. `.env.example` contains placeholders for both sets of variables; never put real credentials in it.

Changing PostgreSQL credentials in `.env.production` after the volume has been initialized does not change the existing database roles. Password changes must be applied to PostgreSQL itself using a controlled procedure.

### TFHUB business-data backup

The API image includes `rclone`. Set `TFHUB_BACKUP_RCLONE_CONFIG_DIR` in `.env.production` to the absolute host path of a dedicated configuration directory containing `rclone.conf`. Production Compose mounts the directory at `/run/secrets/tfhub-rclone`, allowing rclone to replace the config file when OAuth tokens refresh. The file must contain an OAuth-configured Google Drive remote and an rclone `crypt` remote named by `TFHUB_BACKUP_RCLONE_REMOTE`, configured to point only to the dedicated `TFHUB_BACKUPS` destination. The crypt remote's filename encryption must remain enabled. Keep the crypt recovery secret independently of the Pi.

Create the OAuth and crypt remotes on the Pi with `rclone config`. For example, configure the crypt remote's underlying path as `gdrive:TFHUB_BACKUPS`, keep filename encryption enabled, and use `tfhub-crypt` as its name. The API runs as UID/GID `10001`; make both the dedicated directory and `rclone.conf` owned by that identity, with modes `700` and `600` respectively. The directory must be writable for refreshed OAuth tokens to persist; restrict it to this configuration and do not mount a shared directory. After changing ownership, use appropriately privileged host commands to administer the configuration. Never copy the config into the repository or image. Docker Compose attaches only `api` to the additional outbound-capable `backup-egress-network`; it does not publish an API or database port. This network provides internet access for rclone and does not itself restrict destinations to Google Drive.

Plaintext XLSX output and Apache POI working files use the API's size-limited `/tmp` tmpfs (512 MiB) and are removed after execution. Monitor Pi memory and the maximum workbook size against this limit. The on-demand backup is synchronous; Nginx grants only `POST /api/admin/backup` a longer proxy timeout, while application authorization and CSRF protection remain enforced by Spring Security.

## 9. Security boundaries

The deployment must follow `docs/security.md`.

Key requirements:

- all business API endpoints require authentication;
- Spring Security uses server-side sessions; do not introduce JWT for this deployment;
- only login and CSRF initialization endpoints are unauthenticated through the application proxy; Actuator health is available only on the unexposed backend container port for Docker health checking;
- production session cookies use the agreed secure cookie settings;
- CSRF protection remains enabled;
- Nginx is the only internal service that forwards browser requests to the API;
- the backend trusts forwarded headers only from the known internal proxy;
- Nginx overwrites untrusted forwarding headers;
- the database and backend are not directly exposed to the internet or published on host ports;
- Actuator may be used only for local or internal checks, never routed through Nginx or Cloudflare;
- secrets are provided at runtime and are not committed.

The future public request/proxy chain is:

```text
Browser -> Cloudflare -> cloudflared -> Nginx -> Spring Boot
```

The real client IP used for login rate limiting must be derived only from a correctly configured trusted proxy chain. The backend must not trust arbitrary client-supplied forwarding headers.

## 10. Backups and recovery

PostgreSQL backups are required because the database volume alone does not protect against device failure, accidental deletion, or storage corruption.

Backups are not automated by this Compose stack. The runbook below provides a manual PostgreSQL dump and restore procedure. For production operations:

- create a database backup daily;
- encrypt backup files before transferring or storing them outside the Raspberry Pi;
- retain approximately 30 daily and 12 monthly backups;
- perform a restore test quarterly;
- keep backup credentials separate from the application database credentials.

Copy completed backups to storage independent of the Raspberry Pi so that a device failure does not destroy both the live database and its backups. Encryption and off-device retention are operational responsibilities, not configured by this repository.

A backup is not considered verified until it has been successfully restored in a test environment.

## 11. Updates and operations

The production commands in the runbook consistently use `docker-compose.prod.yml` and `.env.production`.

Operational requirements:

- review operating-system, Docker, container-image, and application updates monthly;
- apply critical security fixes promptly;
- inspect application and proxy logs when diagnosing failures;
- avoid logging passwords, session identifiers, tokens, or unnecessary athlete data;
- keep logs for approximately 30 days where practical;
- verify that containers restart after a Raspberry Pi reboot;
- periodically verify that backups can be restored.

Do not add a separate monitoring platform for the initial release. Use Docker health checks and local logs where appropriate. Revisit monitoring only if operational experience demonstrates a need.

## 12. HomeCenter coexistence

The existing HomeCenter installation must remain operational and must not be modified as part of the initial TFHub deployment.

Deployment constraints:

- TFHub production uses its own Compose project (`tfhub-prod`), networks, and named volume.
- TFHub must not replace, reconfigure, or stop HomeCenter services.
- Avoid host-port conflicts with services already used by HomeCenter.
- Use separate persistent volumes and application configuration.
- Do not share databases, Docker networks, or PostgreSQL data with HomeCenter.
- Do not run the development Compose file on the Pi: its PostgreSQL port is for local development only.

A single Cloudflare Tunnel may eventually route multiple hostnames to separate applications, but the initial TFHub deployment must not require changes to HomeCenter. Any future consolidation of tunnel configuration must be planned and tested separately.

## 13. Environments

At minimum, distinguish:

### Local development

- Backend and frontend may run directly on the development computer.
- PostgreSQL may run in Docker.
- The root development Compose file binds PostgreSQL to `127.0.0.1:5432`; do not use that development file for Raspberry Pi production.
- Local configuration and credentials are separate from production.
- Production-only cookie settings such as `Secure` must not prevent local HTTP development.

### Raspberry Pi production

- Services run through `docker-compose.prod.yml`.
- Configure production values in the untracked `.env.production`.
- The database uses `tfhub-prod-postgres-data`.
- Nginx binds to `127.0.0.1:8081` by default; change `TFHUB_HTTP_PORT` to choose another host port.
- No public tunnel or external access is configured.

## 14. Raspberry Pi deployment and operations

Run these commands from the repository root on the Raspberry Pi. The official PostgreSQL, Maven/Java, Node, and Nginx images support ARM64; building on the Pi selects its native architecture.

### Prerequisites and initial configuration

Check the target architecture, Docker Engine and Compose versions, and available disk space rather than assuming specific versions:

```sh
uname -m
docker --version
docker compose version
df -h /
```

The expected architecture is `aarch64`. Clone or update the project source, then create the protected production environment file:

```sh
cp .env.example .env.production
nano .env.production
chmod 600 .env.production
```

Set `TFHUB_HTTP_PORT` (default `8081`), `TFHUB_DB_NAME`, `TFHUB_DB_ADMIN_USER`, `TFHUB_DB_ADMIN_PASSWORD`, `TFHUB_DB_USER`, and `TFHUB_DB_PASSWORD`. Generate two different random passwords with `openssl rand -hex 32`; put one in each password field and do not use the example placeholders. `TFHUB_DB_ADMIN_USER` is for PostgreSQL initialization/administration; the API uses only `TFHUB_DB_USER` and `TFHUB_DB_PASSWORD`.

Validate the Compose configuration without printing resolved secrets, build, and start:

```sh
docker compose --env-file .env.production -f docker-compose.prod.yml config -q
docker compose --env-file .env.production -f docker-compose.prod.yml build
docker compose --env-file .env.production -f docker-compose.prod.yml up -d
```

Open `http://localhost:8081` in a browser on the Pi. The port is loopback-only, so other LAN devices cannot connect. Production session and CSRF cookies remain `Secure`; do not change those settings for local access. Future remote access requires HTTPS.

### Health, logs, restart, and update

```sh
docker compose --env-file .env.production -f docker-compose.prod.yml ps
docker compose --env-file .env.production -f docker-compose.prod.yml logs --tail=100 web api db
docker compose --env-file .env.production -f docker-compose.prod.yml restart
```

The `db`, `api`, and `web` services have container health checks. To update the source, then rebuild and recreate services without removing persistent data:

```sh
git pull --ff-only
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
```

The named database volume survives this update. Do not use `docker compose down -v`, remove `tfhub-prod-postgres-data`, or run volume-pruning commands as routine maintenance. Docker-reported unsupported memory/swap limits are not configured; the API JVM is capped at 768 MiB and container logs rotate at 10 MiB × 3 files. Check actual disk and memory usage on the Pi.

### Database backup and restore

Create a custom-format dump, protect it, and copy it off the Pi:

```sh
umask 077
backup="$HOME/tfhub-$(date +%F).dump"
docker compose --env-file .env.production -f docker-compose.prod.yml exec -T db \
  sh -c 'PGPASSWORD="$TFHUB_DB_PASSWORD" pg_dump --format=custom --no-owner --no-acl --username="$TFHUB_DB_USER" "$POSTGRES_DB"' \
  > "$backup"
chmod 600 "$backup"
```

**Destructive restore:** this replaces objects in the selected TFHUB database. Confirm the backup and target first, stop the API, restore the dump, and start the API only after a successful restore. It does not stop or alter HomeCenter.

```sh
set -e
backup="$HOME/tfhub-YYYY-MM-DD.dump"
docker compose --env-file .env.production -f docker-compose.prod.yml stop api
docker compose --env-file .env.production -f docker-compose.prod.yml exec -T db \
  sh -c 'PGPASSWORD="$TFHUB_DB_PASSWORD" pg_restore --exit-on-error --clean --if-exists --no-owner --no-acl --username="$TFHUB_DB_USER" --dbname="$POSTGRES_DB"' \
  < "$backup"
docker compose --env-file .env.production -f docker-compose.prod.yml start api
```

### Troubleshooting and future tunnel

- If `web` cannot bind its port, set another free loopback port in `TFHUB_HTTP_PORT` and recreate the `web` service.
- If `api` is unhealthy, inspect `logs api db`; verify the application credentials and that the database volume was initialized successfully.
- If PostgreSQL credentials were changed after initialization, update the role in the database as well; editing `.env.production` alone does not rotate it.
- Check free space with `df -h /` and Docker usage with `docker system df`. The SD card is the only database storage in this setup; copy backups off-device.
- The Compose project does not configure Cloudflare Tunnel. In a future controlled change, attach `cloudflared` only to `edge-network` and route it to `web:80`; do not publish the backend or database. Review Nginx's trusted forwarded-header values for the actual tunnel path before enabling remote access.

## 15. Deployment verification boundaries

The following items require deployment testing on the target Pi and have not been verified from this development machine:

- Verify that the selected Raspberry Pi OS, Docker Engine, and Docker Compose versions support the required images and architecture.
- Verify that all selected container images support the Raspberry Pi's CPU architecture.
- Verify that the production Compose stack starts correctly after a host reboot.
- Verify that Nginx routing, Angular client-side routes, and `/api` proxying work locally.
- Verify that the Docker network memberships allow only the intended service paths and that PostgreSQL and the API have no published host ports.
- Configure and verify Cloudflare Tunnel separately before any remote access.
- Verify the trusted proxy/header configuration and real client IP handling for login rate limiting.
- Verify that production secrets are not present in the repository, image layers, or logs.
- Verify that PostgreSQL data survives container recreation and that backups can be restored.
- Verify that TFHub does not conflict with or interrupt HomeCenter.
- Verify that the Raspberry Pi's Wi-Fi connection and upload bandwidth are sufficient for normal use.

These are verification tasks, not unresolved architectural choices.

## 16. Out of scope

The initial deployment does not include:

- Kubernetes or container orchestration beyond Docker Compose;
- Redis or an external session store;
- a separate database host;
- a public database or backend port;
- a separate public API hostname;
- multi-node or high-availability deployment;
- a dedicated monitoring platform;
- changes to the existing HomeCenter architecture.

These may be reconsidered only when a concrete requirement arises.
