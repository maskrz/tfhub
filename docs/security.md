# TFHub Security Architecture

**Status:** Target architecture; security mechanisms are not yet implemented
**Scope:** Initial single-account browser application

## 1. Purpose

This document defines the security architecture for TFHub's planned public-facing web application. It describes the intended protection for the Angular frontend, Spring Boot REST API, and PostgreSQL database.

The requirements below are architectural direction, not a statement that the mechanisms are already implemented. Authentication is part of the initial public-facing application scope; implementation must follow this document and the backend architecture.

## 2. Security Principles

- Business functionality must not be publicly accessible.
- Enforce authentication and authorization in the backend; frontend controls are not a security boundary.
- Prefer simple, well-supported Spring Security and browser mechanisms over additional infrastructure.
- Use secure defaults and minimize externally reachable services and stored secrets.
- Do not expose credentials, tokens, personal data, stack traces, or internal configuration unnecessarily.
- Keep unresolved implementation choices explicit and decide them before the relevant mechanism is implemented.

## 3. Authentication and Account Scope

Use Spring Security for backend-enforced authentication. TFHub has exactly one authorized account, created through a controlled local initialization process and persisted in the PostgreSQL `users` table. The table stores a unique username, an Argon2id password hash, account creation time, and password-change time. The password hash is never a plaintext password and must not be stored in configuration or environment variables.

There is no registration, user CRUD API, account-management UI, multi-user or role management, or email-based password recovery. Password changes and resets are performed through local administrative procedures.

## 4. Session Management

Use Spring Security server-side sessions rather than JWT. For this single-user, same-origin browser application, a browser-managed session cookie and backend session state are sufficient; logout can invalidate the session without adding token storage or lifecycle complexity.

The session lifecycle must:

- Create an authenticated session after successful login.
- Rotate the session identifier on authentication to mitigate session fixation.
- Expire the session after 2 hours of inactivity.
- Enforce an absolute maximum lifetime of 24 hours, after which the user must authenticate again. If the framework's idle timeout does not provide this limit, enforce it separately.
- Invalidate the server-side session on logout and expire or clear the corresponding browser cookie.
- Reject requests using missing, invalid, expired, or logged-out sessions.

Use in-memory sessions for the initial single-instance deployment. A backend restart may invalidate active sessions.

## 5. Session Cookie Security

The session cookie must:

- Be `HttpOnly`.
- Be `Secure` in production.
- Use `SameSite=Lax`.
- In production, use a `__Host-` prefixed name, `Path=/`, and no `Domain` attribute, if supported by the framework.

Never store session identifiers or credentials in browser `localStorage` or `sessionStorage`.

## 6. CSRF Protection

Keep Spring Security CSRF protection enabled for browser-authenticated requests and protect all state-changing requests.

Use Angular HttpClient's built-in XSRF support with an `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` request header. The CSRF cookie must be readable by Angular; it is not the HttpOnly session cookie or an authentication credential.

The authentication API contract is:

| Method | Endpoint | Access | Behavior |
|---|---|---|---|
| `GET` | `/api/csrf` | Public | Initialize or refresh the CSRF token; return `200 OK` and set the `XSRF-TOKEN` cookie. |
| `POST` | `/api/auth/login` | Public, CSRF required | Accept `username` and `password`; establish a session on success. |
| `GET` | `/api/auth/session` | Authentication required | Return the authenticated session state, or `401 Unauthorized`. |
| `POST` | `/api/auth/logout` | Authentication required, CSRF required | Invalidate the current session; return `204 No Content`. |

Login request body:

```json
{
  "username": "coach",
  "password": "provided-by-user"
}
```

Successful login returns `200 OK` with `{"authenticated": true}`. Invalid credentials return `401 Unauthorized` using a generic standard API error response. The session check returns `200 OK` with `{"authenticated": true}` when authenticated, and `401 Unauthorized` otherwise. Logout returns no response body. After successful login and logout, the frontend calls `GET /api/csrf` to refresh the CSRF token. Never expose password hashes or other credential data through the API.

Verify token initialization, refresh, and state-changing request validation with a simple end-to-end test.

## 7. API Access Control

- Require authentication for all business endpoints.
- Return HTTP `401 Unauthorized` for unauthenticated API requests; do not redirect API clients to an HTML login page.
- Return HTTP `403 Forbidden` when an authenticated request is not permitted, including when CSRF validation fails.
- Allow only `POST /api/auth/login` and `GET /api/csrf` without authentication. `GET /api/auth/session` returns `401 Unauthorized` when no authenticated session exists.
- Do not expose Actuator or a health endpoint publicly. Reconsider a minimal health endpoint only if a concrete deployment requirement appears.

The backend must enforce these rules regardless of Angular route or component visibility.

## 8. Password Storage

- Use Spring Security's `PasswordEncoder` with Argon2id.
- Start with OWASP's recommended minimum Argon2id parameters: 19 MiB memory, 2 iterations, and parallelism 1. Benchmark on the Raspberry Pi before deployment.
- Add BouncyCastle only if required by the selected Spring Security implementation.
- Store the encoded password hash in PostgreSQL's `users.password_hash` column; never store plaintext passwords or reversible password encryption.
- Provision the account and perform password changes or resets through local administrative procedures, not public API endpoints.
- Do not log passwords or include them in API responses.
- Do not implement custom cryptography.

## 9. Login Abuse Protection

Apply simple server-side, in-memory rate limiting to login: allow no more than 5 failed attempts per source IP within 15 minutes, followed by a 15-minute cooldown. Do not permanently lock the account.

Use generic login error messages and do not rely on Angular controls or client-side throttling. Log relevant authentication failures without logging submitted credentials or secrets.

## 10. Angular Security Responsibilities

- Do not store credentials, session identifiers, or authentication tokens in `localStorage` or `sessionStorage`.
- Use browser-managed session cookies; frontend JavaScript must not attempt to manage the session identifier.
- Use Angular HttpClient's built-in XSRF support with the `XSRF-TOKEN` cookie, `X-XSRF-TOKEN` header, and backend CSRF initialization endpoint.
- Handle `401` responses by presenting the unauthenticated state and directing the user to the login flow as appropriate.
- Handle `403` responses as access or request-forbidden errors; do not treat them as proof that the user is unauthenticated.
- Route guards may improve navigation and user experience, but they are not access control. The backend must protect every business endpoint.
- Never include secrets in frontend source, build-time public configuration, or production bundles.

## 11. Same-Origin Deployment and CORS

The preferred production arrangement is for Nginx to serve the Angular production build and reverse-proxy `/api` requests to Spring Boot under the same public origin. This keeps browser requests same-origin and avoids unnecessary CORS configuration.

Do not enable permissive CORS as a substitute for authentication, CSRF protection, or correct reverse-proxy routing. If a separate origin is later required, explicitly define and restrict allowed origins, credentials, methods, and headers before enabling cross-origin requests.

## 12. Transport Security and Cloudflare Tunnel

- Public access must use HTTPS.
- Nginx serves the frontend and reverse-proxies `/api` to the Spring Boot API, in line with the target deployment architecture.
- Cloudflare Tunnel provides public connectivity without requiring a public IP address or inbound router port forwarding. It does not replace application authentication or authorization.
- Trust forwarded headers only from the known Nginx proxy on the private Docker network. Configure Nginx to overwrite incoming forwarding headers rather than passing untrusted client values through.
- Ensure there is no unintended public route directly to the origin, Spring Boot, PostgreSQL, or other internal services. Expose only the intended ingress path.
- The origin and tunnel configuration must preserve secure scheme and host information needed by the application without trusting arbitrary proxy sources.

Keep the proxy configuration simple and verify it during deployment.

## 13. Secrets and Configuration

- Never commit real passwords, tokens, private keys, or other secrets to Git.
- Supply secrets at runtime using root-restricted secret files or an equivalent protected Compose mechanism.
- Runtime secrets include database passwords, account provisioning secrets, and Cloudflare Tunnel tokens.
- Use separate credentials and secret values for development and production.
- Do not embed secrets in Angular bundles, Docker images, source code, example configuration, or logs.
- Example configuration may contain placeholders only.
- Restrict access to production configuration and rotate credentials if they are exposed.

## 14. Database and Infrastructure Security

- Keep PostgreSQL on a private Docker network; do not expose its port publicly.
- Use dedicated database credentials for the application with only the permissions it needs.
- Do not use database superuser credentials for routine application access.
- Store daily encrypted PostgreSQL backups outside the Raspberry Pi. Retain 30 daily and 12 monthly backups, and perform a restore test quarterly.
- Review operating system, Docker image, and application dependency updates monthly; apply critical security fixes promptly.
- Do not expose Actuator endpoints publicly.
- Keep development and production environments, data, and credentials separate.

## 15. Logging and Error Handling

- Log relevant authentication and authorization events, including successful authentication, failed authentication, logout, and denied access, at an appropriate level.
- Never log passwords, session identifiers, CSRF tokens, Cloudflare Tunnel tokens, or other secrets.
- Avoid unnecessary athlete personal data in application and access logs.
- Do not return stack traces, internal configuration, SQL details, or other implementation details in API errors.
- Return consistent client-safe error responses while retaining appropriate diagnostic detail only in protected server-side logs.
- Retain logs for approximately 30 days and restrict access to operationally authorized users.

## 16. Security Verification Checklist

- [ ] All business endpoints reject unauthenticated requests with HTTP `401`, without an HTML redirect.
- [ ] Only `POST /api/auth/login` and `GET /api/csrf` are accessible without authentication; the session check returns 401 when unauthenticated.
- [ ] Authenticated sessions rotate their identifier after login, expire as configured, and are invalidated at logout.
- [ ] Production session cookies are `HttpOnly`, `Secure`, and use the approved `SameSite` policy.
- [ ] No credential or session identifier is stored in browser web storage.
- [ ] CSRF protection remains enabled and state-changing browser requests are validated.
- [ ] The single account is provisioned locally; only its adaptive password hash is stored in PostgreSQL, and credentials are never exposed through the API.
- [ ] Login attempt throttling is enforced server-side.
- [ ] Angular route guards are not relied upon to secure API data or operations.
- [ ] PostgreSQL and internal application services are not publicly reachable.
- [ ] Forwarded headers are trusted only from configured proxies.
- [ ] Secrets are supplied at runtime and are absent from Git, frontend bundles, images, and logs.
- [ ] API errors do not disclose stack traces or internal configuration.
- [ ] Backups can be restored, and update and monitoring procedures are defined for production.

## 17. Out of Scope

The initial security scope does not include:

- Public registration or self-service account creation.
- Multiple user accounts, user administration, roles, or permissions management.
- JWT-based authentication.
- MFA.
- Public business functionality.
- Introducing Redis or other infrastructure without a demonstrated need.

These are not implied future requirements; any scope expansion requires an explicit decision.

## 18. Open Decisions

The architecture is agreed. Verify these implementation details before production:

1. Confirm the selected Spring Security implementation supports Argon2id; benchmark the OWASP minimum parameters on the Raspberry Pi. Add BouncyCastle only if required.
2. Verify framework support for the production `__Host-` session cookie.
3. Implement and end-to-end test the documented login/session/logout contract and CSRF token initialization at `GET /api/csrf`, refresh after login/logout, and Angular HttpClient request validation.
4. Verify the 24-hour absolute session lifetime is enforced independently of the 2-hour idle timeout.
5. Define username maximum length and case-normalization in the migration, then test login rate limiting, trusted proxy/header handling, local account initialization/reset, and backup/restore procedures in the deployment environment.
