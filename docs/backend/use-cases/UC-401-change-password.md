# UC-401 – Change Password

## 1. Goal

Allow an authenticated user to change their own password without requiring a user profile or a separate user-management interface.

The functionality extends the existing authentication module.

## 2. Actors

- **Authenticated user** – changes their own password.

## 3. Preconditions

- The user is authenticated.
- The user has an active application session.
- The user knows their current password.

## 4. Main flow

1. The user submits a password change request containing:
    - current password;
    - new password.
2. The backend identifies the user using the authenticated Spring Security context.
3. The backend retrieves the corresponding user account.
4. The backend verifies the current password against the stored password hash.
5. The backend validates the new password according to the application's password policy.
6. The backend hashes the new password using the existing Argon2id password encoder.
7. The backend updates the user's password hash.
8. The backend applies the defined session invalidation policy.
9. The backend returns a successful response.

## 5. Alternative flows

### A1. Incorrect current password

- The backend rejects the request.
- The password remains unchanged.
- The backend returns a controlled client error using the existing API error response convention.

### A2. Invalid new password

- The backend rejects the request.
- The password remains unchanged.
- The backend returns the standard validation response.

### A3. Unauthenticated request

- The backend rejects the request with HTTP 401.
- No password data is modified.

### A4. Invalid request

- Missing, null or blank required fields result in a validation error.
- No password data is modified.

## 6. API

### Endpoint

`POST /api/auth/change-password`

### Request

```json
{
  "currentPassword": "current-password",
  "newPassword": "new-password"
}
```

### Success response

`204 No Content`

### Error responses

Follow existing backend conventions for:
- HTTP 401 – unauthenticated request;
- HTTP 400 – incorrect current password or invalid request data;
- other errors according to existing global exception handling.

## 7. Business rules

- A user can change only their own password.
- The user identity must come from the authenticated security context, never from request parameters or request body.
- The current password must be verified before changing the password.
- Password fields must be non-blank and no longer than the existing 1024-character authentication limit; no additional complexity policy is introduced.
- Passwords must never be stored in plain text.
- The new password must be hashed using the existing Argon2id configuration.
- The existing `password_changed_at` column is updated as part of the password change.
- Password changes must be transactional.
- Passwords and password hashes must never be written to application logs.
- Existing authentication, authorization and CSRF mechanisms must remain unchanged.

## 8. Session handling

After a successful password change, the current session is invalidated and the user must authenticate again.

The application currently stores sessions in the servlet container's in-memory session store and has no session registry or user-to-session index. It can invalidate the current session, but cannot enumerate or invalidate other sessions for the same user; those sessions remain authenticated until they expire or are otherwise invalidated. No additional session infrastructure is introduced.

CSRF protection remains required for this state-changing endpoint.

## 9. Security

- The endpoint is available only to authenticated users.
- CSRF protection remains enabled.
- The backend must not allow changing another user's password.
- Password hashes must not be exposed in API responses.
- Internal exception details must not be exposed to clients.

## 10. Out of scope

- User profile management.
- User registration.
- Password recovery / forgotten password flow.
- Administrative password reset.
- User management CRUD.
- Frontend implementation.
- Changes to roles or permissions.

## 11. Acceptance criteria

- An authenticated user can successfully change their own password.
- An incorrect current password is rejected.
- Invalid new passwords are rejected.
- The new password is stored as an Argon2id hash.
- The new password can be used for subsequent authentication.
- The old password can no longer be used.
- Unauthenticated requests are rejected.
- CSRF protection is preserved.
- Session invalidation behavior is implemented and documented.
- Automated tests cover the main and alternative flows.