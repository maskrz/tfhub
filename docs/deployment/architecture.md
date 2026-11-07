# Deployment Architecture

## 1. Purpose

This document describes the target deployment architecture for TFHub.

The initial deployment target is a Raspberry Pi 5 (8 GB RAM) connected to the local network over Wi-Fi. TFHub will be accessible remotely through a Cloudflare Tunnel. The application will run as a Docker Compose stack.

This document defines the intended architecture and operational boundaries. It does not describe the step-by-step installation procedure or claim that the production deployment is already implemented.

## 2. Scope and principles

The initial deployment must:

- run TFHub and its dependencies on the Raspberry Pi;
- provide browser access from outside the local network without requiring a public IP address or router port forwarding;
- keep PostgreSQL and the Spring Boot API inaccessible directly from the public internet;
- use HTTPS for public browser access;
- persist application data across container restarts and updates;
- keep secrets out of source control;
- support backups stored outside the Raspberry Pi;
- avoid interfering with the existing Home Hub installation.

Keep the deployment simple. Do not introduce Kubernetes, Redis, a separate database server, or additional infrastructure unless a concrete requirement justifies it.

## 3. High-level architecture

The intended request path is:

```text
User's browser
    |
    | HTTPS
    v
Cloudflare
    |
    | Cloudflare Tunnel
    v
cloudflared container
    | edge-network
    v
Nginx container
    | /                         | /api (backend-network)
    v                           v
Angular static files        Spring Boot API
                                |
                                | database-network
                                v
                           PostgreSQL
```

Docker network membership limits which services can communicate:

```text
edge-network:     cloudflared, web
backend-network:  web, api
database-network: api, db
```

The only intended service paths are `cloudflared → web`, `web → api`, and `api → db`. Because `web` is not on `database-network`, `web → db` is not possible through Docker network membership. `cloudflared` is only on `edge-network`, so it cannot reach `api` or `db` through Docker network membership.

Responsibilities:

- **Cloudflare** provides the public hostname, DNS, and HTTPS at the edge.
- **cloudflared** establishes an outbound tunnel from the Raspberry Pi to Cloudflare. No inbound router port forwarding is required.
- **Nginx** serves the built Angular application and reverse-proxies `/api` requests to the Spring Boot API. The browser uses a same-origin API, so CORS configuration should not be needed.
- **Spring Boot** runs the backend and enforces application authentication and authorization rules. Cloudflare is not a replacement for application-level security.
- **PostgreSQL** stores application data and is reachable only by the backend over a private Docker network.

The browser-to-Cloudflare connection uses HTTPS. Traffic between containers may use HTTP inside the private Docker networks; TLS between containers is not required for the initial single-host deployment.

## 4. Docker Compose services

The production Compose stack is expected to contain these services:

| Service | Responsibility | Network exposure |
|---|---|---|
| `cloudflared` | Maintains the Cloudflare Tunnel | Outbound connection to Cloudflare; no public inbound port |
| `web` | Nginx serving Angular and proxying `/api` | Receives traffic from `cloudflared` |
| `api` | Spring Boot application | Receives API traffic from `web`; connects to `db` |
| `db` | PostgreSQL database | Private access from `api` only |

Service names are logical names and may be adjusted during implementation, provided the architecture remains the same.

### 4.1 Network boundaries

Attach only the following services to each network:

| Network | Members |
|---|---|
| `edge-network` | `cloudflared`, `web` |
| `backend-network` | `web`, `api` |
| `database-network` | `api`, `db` |

This network membership permits only `cloudflared → web`, `web → api`, and `api → db`. In particular, `web → db`, `cloudflared → api`, and `cloudflared → db` are not possible through Docker network membership. Do not attach a service to an additional network unless a concrete requirement justifies it.

PostgreSQL and the Spring Boot API must not publish host ports in production. Only Nginx is reachable through the tunnel.

### 4.2 Persistent data

PostgreSQL data must be stored in a named Docker volume so that container recreation or image updates do not remove the database.

The database volume is persistent application data. Do not remove it as part of routine deployment or cleanup commands. In particular, `docker compose down -v` must not be used casually because it removes named volumes.

Application containers should be replaceable: rebuilding or recreating the `web`, `api`, or `cloudflared` container must not delete PostgreSQL data.

## 5. Public access and Cloudflare Tunnel

TFHub will be exposed through a Cloudflare Tunnel using a hostname configured in Cloudflare DNS.

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

Production configuration must be supplied separately from local development configuration.

Requirements:

- use environment-specific Spring configuration where necessary;
- provide database credentials at runtime;
- keep `DB_PASSWORD` and other secrets out of committed files;
- use a dedicated PostgreSQL application user rather than the database superuser;
- store the single account's Argon2id password hash in the `users` table; never place the plaintext password in configuration or environment variables;
- keep Hibernate schema handling consistent with the backend architecture: Flyway manages schema changes and Hibernate validates the schema;
- do not expose database connection details or credentials in logs.

The production database must use a persistent named volume. Database initialization and schema migration must be repeatable and must not rely on manually editing the live database.

## 8. Secrets and configuration

Secrets must not be stored in the Git repository or embedded in Docker images.

At minimum, production secrets include:

- PostgreSQL credentials;
- Cloudflare Tunnel credentials/token;
- any non-password secret strictly required by the controlled local account initialization procedure.

The account password is entered through the local initialization procedure and only its Argon2id hash is stored in PostgreSQL. Never put the plaintext account password in configuration or environment variables.

For the initial deployment, secrets may be supplied through protected runtime files or environment configuration that is excluded from Git. Restrict access to these files to the operating-system account and administrators who need it.

The root `.env` file used for Compose must remain untracked. Commit an example file containing variable names and safe placeholder values, if useful, but never real credentials.

Changing a PostgreSQL environment variable after the database volume has already been initialized does not automatically change the password stored inside PostgreSQL. Password changes must be applied to the database itself using a controlled procedure.

## 9. Security boundaries

The deployment must follow `docs/security.md`.

Key requirements:

- all business API endpoints require authentication;
- Spring Security uses server-side sessions; do not introduce JWT for this deployment;
- only login and CSRF initialization endpoints are unauthenticated; do not expose Actuator or a health endpoint publicly;
- production session cookies use the agreed secure cookie settings;
- CSRF protection remains enabled;
- Nginx is the only internal service that forwards browser requests to the API;
- the backend trusts forwarded headers only from the known internal proxy;
- Nginx overwrites untrusted forwarding headers;
- the database and backend are not directly exposed to the internet or published on host ports;
- Actuator may be used only for local or internal checks, never routed through Nginx or Cloudflare;
- secrets are provided at runtime and are not committed.

The expected request/proxy chain is:

```text
Browser -> Cloudflare -> cloudflared -> Nginx -> Spring Boot
```

The real client IP used for login rate limiting must be derived only from a correctly configured trusted proxy chain. The backend must not trust arbitrary client-supplied forwarding headers.

## 10. Backups and recovery

PostgreSQL backups are required because the database volume alone does not protect against device failure, accidental deletion, or storage corruption.

Initial backup policy:

- create a database backup daily;
- encrypt backup files before transferring or storing them outside the Raspberry Pi;
- retain approximately 30 daily and 12 monthly backups;
- perform a restore test quarterly;
- keep backup credentials separate from the application database credentials.

The backup destination and transfer mechanism can be selected during implementation. It must be independent of the Raspberry Pi so that a device failure does not destroy both the live database and its backups.

A backup is not considered verified until it has been successfully restored in a test environment.

## 11. Updates and operations

The deployment should be manageable with a small number of documented Docker Compose commands.

Operational requirements:

- review operating-system, Docker, container-image, and application updates monthly;
- apply critical security fixes promptly;
- inspect application and proxy logs when diagnosing failures;
- avoid logging passwords, session identifiers, tokens, or unnecessary athlete data;
- keep logs for approximately 30 days where practical;
- verify that containers restart after a Raspberry Pi reboot;
- periodically verify that backups can be restored.

Do not add a separate monitoring platform for the initial release. Use Docker health checks and local logs where appropriate. Revisit monitoring only if operational experience demonstrates a need.

## 12. Home Hub coexistence

The existing Home Hub installation must remain operational and must not be modified as part of the initial TFHub deployment.

Deployment constraints:

- TFHub must use its own Compose project and isolated container/service names.
- TFHub must not replace, reconfigure, or stop Home Hub services.
- Avoid host-port conflicts with services already used by Home Hub.
- Use separate persistent volumes and application configuration.
- Do not share databases or Docker networks between the two applications unless a future, explicit design decision requires it.

A single Cloudflare Tunnel may eventually route multiple hostnames to separate applications, but the initial TFHub deployment must not require changes to Home Hub. Any future consolidation of tunnel configuration must be planned and tested separately.

## 13. Environments

At minimum, distinguish:

### Local development

- Backend and frontend may run directly on the development computer.
- PostgreSQL may run in Docker.
- The current root development Compose file publishes port `5432` without restricting the host interface. This is development-only and is not the production network model; consider restricting it to `127.0.0.1:5432` as a separate configuration improvement.
- Local configuration and credentials are separate from production.
- Production-only cookie settings such as `Secure` must not prevent local HTTP development.

### Raspberry Pi production

- Services run through Docker Compose.
- Production secrets are provided at runtime.
- The database uses persistent storage.
- Public access is provided only through Cloudflare Tunnel and Nginx.
- Production configuration must not depend on files or credentials from the developer's computer.

The exact build and deployment workflow can be documented once the first deployment is implemented.

## 14. Implementation-time verification

The following items are intentionally left for implementation and deployment testing:

- Verify that the selected Raspberry Pi OS, Docker Engine, and Docker Compose versions support the required images and architecture.
- Verify that all selected container images support the Raspberry Pi's CPU architecture.
- Verify that the production Compose stack starts correctly after a host reboot.
- Verify that Nginx routing, Angular client-side routes, and `/api` proxying work through the tunnel.
- Verify that the Docker network memberships allow only the intended service paths and that PostgreSQL and the API have no published host ports.
- Verify that Cloudflare Tunnel reconnects after a temporary internet interruption.
- Verify the trusted proxy/header configuration and real client IP handling for login rate limiting.
- Verify that production secrets are not present in the repository, image layers, or logs.
- Verify that PostgreSQL data survives container recreation and that backups can be restored.
- Verify that TFHub does not conflict with or interrupt Home Hub.
- Verify that the Raspberry Pi's Wi-Fi connection and upload bandwidth are sufficient for normal use.

These are verification tasks, not unresolved architectural choices.

## 15. Out of scope

The initial deployment does not include:

- Kubernetes or container orchestration beyond Docker Compose;
- Redis or an external session store;
- a separate database host;
- a public database or backend port;
- a separate public API hostname;
- multi-node or high-availability deployment;
- a dedicated monitoring platform;
- changes to the existing Home Hub architecture.

These may be reconsidered only when a concrete requirement arises.
