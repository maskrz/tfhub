# FE-ACCOUNT-002 – Session Management

## 1. Goal

Allow authenticated users to view and revoke active sessions belonging to their account.

## 2. Navigation and access

- Add “Aktywne sesje” to the authenticated user's account menu.
- The view is available to ADMIN, COACH, and ATHLETE accounts.
- Protect the route with the existing authenticated route guard. Backend authorization remains authoritative.

## 3. Session list

Display each session's creation time and last activity time, and clearly identify the current session.

Requirements:

- Load sessions through the authentication API service.
- Show loading, empty, error, and successful states using existing application conventions.
- Do not display device, IP address, user-agent, or raw servlet session identifiers.

## 4. Revoke a session

- Allow the user to revoke any listed session, including the current one.
- Prevent duplicate submissions while a revoke request is in progress.
- After revoking another session, refresh the list.
- After revoking the current session, clear local authentication state and navigate to login.
- Display API errors using existing application feedback conventions.

## 5. API integration

Use the backend endpoints specified by [UC-404 Session Management](../../backend/use-cases/UC-404-session-management.md). Do not store session handles in browser storage or treat them as credentials.

## 6. Acceptance criteria

- Every authenticated role can open the session-management view.
- The view displays current and other sessions with creation and last-activity times.
- The user can revoke each session individually.
- Revoking the current session ends the browser's authenticated state.
- The view handles loading, empty, error, and in-progress states.
- Automated frontend tests cover API requests, rendered session information, successful revocation, and errors.
