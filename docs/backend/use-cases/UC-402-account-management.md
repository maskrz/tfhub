# UC-402 – Account Management

## 1. Goal

Allow administrators to manage application accounts, including creating accounts, editing account details, assigning roles and related resources, changing passwords, and activating or deactivating accounts.

The functionality must extend the existing authentication and authorization implementation without introducing a separate authentication mechanism.

## 2. Actors

- **ADMIN** – manages all application accounts.
- **COACH** – no access to account management.
- **ATHLETE** – no access to account management.

## 3. Account model and business rules

Every account has exactly one role:

- `ADMIN`
- `COACH`
- `ATHLETE`

### ADMIN

- Has unrestricted application access.
- Has no required training group or athlete assignment.
- Can manage other administrator accounts.

### COACH

- Must be assigned to at least one training group.
- Can be assigned to multiple training groups.
- A training group can have multiple assigned coaches.
- Uses the existing `user_training_group` relationship.

### ATHLETE

- Must be assigned to exactly one athlete.
- An athlete can have at most one associated ATHLETE account.
- Uses the existing `user_athlete_access` relationship.
- The athlete's training group is derived from the athlete record. No separate training group assignment is made to the account.

### General rules

- Usernames must remain unique.
- Passwords must be stored using the existing Argon2id password hashing configuration.
- Accounts are deactivated rather than physically deleted.
- Inactive accounts cannot authenticate.
- Passwords and password hashes must never be returned by the API.
- Existing account IDs and relationships must be preserved when editing an account unless explicitly changed by the administrator.

## 4. Preconditions

- The administrator is authenticated.
- The administrator has the `ADMIN` role.
- The request passes existing CSRF protection where applicable.

## 5. Functional requirements

### 5.1. Account list

The administrator can retrieve a list of accounts.

The list should provide:
- account ID;
- username;
- role;
- active status;
- assigned training groups for COACH accounts;
- assigned athlete for ATHLETE accounts.

The endpoint should support filtering by:
- username;
- role;
- active status.

Do not expose password hashes or other authentication secrets.

### 5.2. Create account

The administrator can create an account by providing:
- username;
- initial password;
- role;
- role-specific assignments.

Validation:
- username is required and unique;
- password is required and must satisfy the existing password validation rules;
- role must be one of the supported roles;
- ADMIN accounts must not have group or athlete assignments;
- COACH accounts must have at least one valid training group;
- ATHLETE accounts must have exactly one valid athlete;
- an athlete cannot be assigned to more than one ATHLETE account.

The account and its relationships must be created atomically.

New accounts are active by default.

### 5.3. Edit account

The administrator can edit an existing account:
- username;
- role;
- role-specific assignments;
- active status.

The same validation rules as account creation apply.

Role changes must correctly update the associated relationships:
- ADMIN: no group or athlete assignments;
- COACH: one or more training groups;
- ATHLETE: exactly one athlete.

Changes to the account and its relationships must be transactional.

The backend must prevent conflicting assignments and preserve referential integrity.

### 5.4. Deactivate and reactivate account

The administrator can deactivate and reactivate accounts.

- Deactivation must prevent subsequent authentication.
- Reactivation restores the ability to authenticate.
- Deactivation must not delete account data or historical relationships unnecessarily.
- Deactivation and reactivation must respect the administrator protection rules described below.

### 5.5. Reset password

The administrator can set a new password for any account.

Requirements:
- The administrator provides the new password.
- The new password is validated using existing password validation rules.
- The password is hashed using the existing Argon2id configuration.
- The password update is transactional.
- The new password can be used immediately.
- The user is not required to change the password at the next login.
- The current password is not required.
- Passwords must never be returned or logged.

### 5.6. Administrator protection

The system must never allow an operation that leaves the application without at least one active ADMIN account.

In particular:
- The last active ADMIN cannot be deactivated.
- The last active ADMIN cannot have their role changed to COACH or ATHLETE.
- An administrator cannot remove their own ADMIN role if that would leave no active ADMIN account.
- An administrator cannot deactivate their own account if that would leave no active ADMIN account.

This invariant must be enforced by the backend, not only by the frontend.

The implementation must consider concurrent administrative requests to prevent two operations from simultaneously removing the last active administrator.

## 6. Authorization

- All account management endpoints are available exclusively to authenticated ADMIN users.
- COACH and ATHLETE users must not be able to access these endpoints.
- Authorization must be enforced on the backend for every operation.
- Unauthenticated requests return HTTP 401.
- Authenticated users without ADMIN privileges receive HTTP 403.
- Do not rely on frontend route guards as a security mechanism.

## 7. Session and security handling

- Preserve the existing Spring Security session-based authentication and CSRF configuration.
- Deactivated accounts must not be able to authenticate.
- Existing sessions belonging to a deactivated account must be invalidated where supported by the current session architecture.
- Role changes and password resets should invalidate existing sessions belonging to the affected account where supported.
- Do not introduce new session infrastructure solely for this use case.
- Document any limitations of the existing session architecture.
- Do not expose password hashes or internal security details.
- Use the existing global exception handling and API error response conventions.

## 8. Data integrity

- Use the existing `users`, `training_groups`, `athletes`, `user_training_group` and `user_athlete_access` structures.
- Do not introduce duplicate relationship tables or a second account model.
- Use existing foreign keys and constraints where available.
- Add database constraints or migrations only where necessary to guarantee business rules that cannot safely be enforced otherwise.
- Account creation, role changes and assignment updates must be transactional.
- The backend must validate that all referenced groups and athletes exist and are valid for the requested operation.
- Do not physically delete accounts.

## 9. Out of scope

- Frontend implementation.
- Public registration.
- Forgotten password / password recovery.
- Email notifications.
- User profile management.
- Group management CRUD.
- Athlete management CRUD.
- Multiple roles per account.
- Parent accounts or multiple athletes assigned to one ATHLETE account.
- Forced password change after administrator password reset.
- Audit log functionality, unless an existing mechanism already provides it.

## 10. Acceptance criteria

- Only ADMIN users can manage accounts.
- ADMIN can list and filter accounts.
- ADMIN can create accounts for all three roles.
- Role-specific assignment rules are enforced.
- ADMIN can edit usernames, roles and assignments.
- A coach can have multiple groups.
- An athlete account is associated with exactly one athlete.
- An athlete cannot be associated with multiple ATHLETE accounts.
- ADMIN can deactivate and reactivate accounts.
- Inactive accounts cannot authenticate.
- ADMIN can set a new password for any account.
- New passwords use the existing Argon2id configuration.
- No operation can leave the system without an active ADMIN account.
- Existing authentication and authorization behavior remains intact.
- Automated tests cover all important business rules, security rules and failure scenarios.