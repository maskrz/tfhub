# UC-404 – Session Management

## 1. Goal

Allow an authenticated user to review active sessions for their account and revoke any individual session.

## 2. Actors

- **Authenticated user** – reviews and revokes their own sessions.

## 3. Preconditions

- The user has an authenticated session.
- Requests that change session state pass existing CSRF protection.

## 4. Functional requirements

- The user can retrieve all active sessions belonging to their account.
- Each entry identifies whether it is the current session and shows its creation and last-activity times.
- The user can revoke any one of their sessions, including the current session.
- A revoked session must be rejected on its next request.
- Unknown session handles and handles belonging to another user return `404 Not Found`.
- The server must not expose servlet session identifiers or treat returned opaque handles as authentication credentials.

## 5. API

### List sessions

`GET /api/auth/sessions`

Returns `200 OK` with a list of the authenticated user's sessions. Each response contains an opaque session handle, creation time, last-activity time, and current-session flag.

### Revoke a session

`DELETE /api/auth/sessions/{sessionHandle}`

Returns `204 No Content` when the session is revoked. Returns `404 Not Found` when the handle is unknown or does not belong to the authenticated user. The endpoint requires authentication and CSRF validation.

## 6. Session lifetime and storage

- Sessions expire after 14 days without activity; authenticated activity renews this rolling idle timeout.
- There is no shorter absolute lifetime limit.
- Sessions and the user-to-session registry remain in memory for the initial single-instance deployment.
- A backend restart invalidates active sessions and clears the registry.
- The in-memory registry is not suitable for multiple backend instances.
- No database schema change or separate session infrastructure is required.

## 7. Security

- Session listing and revocation are scoped to the authenticated user's account, regardless of role.
- The backend enforces ownership; frontend route guards are supplementary only.
- Do not include raw servlet session identifiers, IP addresses, or user-agent values in the response.
- A revoked session cannot continue to access authenticated endpoints.

## 8. Acceptance criteria

- An authenticated user can list their own active sessions.
- The response marks the current session and contains no servlet session identifier.
- A user can revoke any listed session, including their current one.
- A session belonging to another user cannot be enumerated or revoked.
- An unknown handle returns `404`.
- Revoked sessions cannot access authenticated endpoints.
- Idle sessions expire after 14 days; active sessions are not forced to reauthenticate by a shorter absolute limit.
- Automated tests cover ownership, authorization, CSRF, revocation, and session expiry behavior.
