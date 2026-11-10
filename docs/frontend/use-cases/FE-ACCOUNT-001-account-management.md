# FE-ACCOUNT-001 – Account Management

## 1. Goal

Provide an administration interface for managing application accounts.

The interface allows ADMIN users to list, create, edit, activate, deactivate and reset passwords for application accounts.

The frontend integrates with the backend functionality described in `uc-402-account-management.md`.

## 2. Actors

- ADMIN – full access to account management.
- COACH – no access.
- ATHLETE – no access.

## 3. Navigation

Add a new navigation entry:

`Zarządzanie kontami`

Requirements:
- Visible only to users with the ADMIN role.
- Available through the existing application navigation.
- Follow existing navigation and authorization conventions.
- Direct access to the route by non-admin users must follow the existing unauthorized-access behavior.

## 4. Account list

Create an account management view containing a list of application accounts.

Display the following information:

- Username.
- Role.
- Account status (active/inactive).
- Assigned training groups for COACH accounts.
- Assigned athlete for ATHLETE accounts.

Provide filters for:
- Username.
- Role: ADMIN, COACH, ATHLETE.
- Status: active, inactive.

Requirements:
- Display account data returned by the backend.
- Load the complete account list and apply username, role, and status filters locally in the frontend.
- Provide a clear loading state.
- Provide an empty state when no accounts match the filters.
- Display errors using existing application feedback conventions.
- Allow the administrator to open an account for editing.
- Provide an action to create a new account.

## 5. Create account

Provide a popup for creating an account.

Fields:
- Username.
- Initial password.
- Role.
- Role-specific assignments.

### Role-dependent fields

**ADMIN**
- No assignment fields.

**COACH**
- Multi-select training groups.
- At least one group must be selected.

**ATHLETE**
- Single-select athlete.
- Search/filter available athletes in the selection dropdown.
- Exactly one athlete must be selected.

Requirements:
- Changing the role must update the visible assignment fields.
- Clear or reset assignments that are no longer applicable after a role change.
- Validate required fields.
- Display validation errors in Polish.
- Prevent duplicate submissions.
- Submit the data using the backend API.
- Close the popup and refresh the account list after successful creation.

New accounts are active by default. Do not add an active-status field to the creation form unless required by the actual backend contract.

## 6. Edit account

Use the same popup/component as account creation, adapted for editing.

Allow editing:
- Username.
- Role.
- Role-specific assignments.

Display the current account status.

Allow changing the status through a separate activate/deactivate action in the account list or account editing interface.

Requirements:
- Load existing account data into the form.
- Display assignments appropriate to the current role.
- Apply the same role-dependent validation as during creation.
- Save changes through the backend API.
- Refresh the list after successful updates.
- Display backend validation errors in a user-friendly way.

Do not expose password hashes or existing passwords.

## 7. Activate and deactivate accounts

Provide an action to activate or deactivate an account.

Requirements:
- Clearly distinguish active and inactive accounts.
- Ask for confirmation before deactivation.
- Provide appropriate feedback after successful activation/deactivation.
- Refresh account data after the operation.
- Display backend errors, including the last-active-ADMIN protection error.
- Do not implement physical account deletion.

## 8. Reset password

Provide a separate popup for administrator password reset.

Fields:
- New password.
- Repeat new password.

Requirements:
- Both fields are required.
- Passwords must match.
- Use password input types.
- Do not require the current password.
- Do not force a password change at the next login.
- Do not display or store passwords after the operation.
- Prevent duplicate submissions.
- Display success feedback and close the popup after successful reset.
- Display backend validation and error responses appropriately.

## 9. User experience

- All UI labels and messages must be in Polish.
- Follow existing application styling and component conventions.
- Reuse existing popup/modal, form, select, multi-select and confirmation components.
- Do not introduce another UI library.
- Use consistent spacing, typography and button styles.
- Ensure the interface works on desktop and mobile.
- Clearly distinguish destructive or security-sensitive actions.
- Provide clear success and error feedback.
- Do not use browser alerts if the application already has a notification or confirmation mechanism.

## 10. API integration

Use the account management endpoints implemented in backend UC-402.

Before implementing API integration:
- Inspect the actual backend controllers and DTOs.
- Verify endpoint paths, request structures, response structures and validation errors.
- Reuse existing frontend HTTP services and conventions.
- Do not invent endpoint paths or DTO properties.
- Do not duplicate API logic in components.

## 11. Authorization and security

- Only ADMIN users can access account management.
- Follow existing frontend authentication and route guard conventions.
- Frontend authorization is supplementary; backend authorization remains authoritative.
- Never store passwords in local storage, session storage or other persistent frontend storage.
- Do not log passwords or sensitive authentication data.

## 12. Out of scope

- Backend implementation.
- User registration.
- Password recovery.
- User profile management.
- Training group CRUD.
- Athlete CRUD.
- Physical account deletion.
- Forced password change after administrator password reset.
- Audit log interface.

## 13. Acceptance criteria

- Only ADMIN users can see and access account management.
- ADMIN can list and filter accounts.
- ADMIN can create accounts with all three supported roles.
- The form displays the correct assignment fields for each role.
- ADMIN can edit usernames, roles and assignments.
- ADMIN can activate and deactivate accounts.
- ADMIN can reset account passwords.
- Invalid data is handled correctly.
- Backend business-rule errors are displayed clearly.
- The list refreshes after successful operations.
- Existing application navigation and authentication behavior remain intact.
- The interface follows existing application styling and responsive conventions.