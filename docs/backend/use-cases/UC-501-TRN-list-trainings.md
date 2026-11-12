# UC-501 – List Trainings

## 1. Purpose

Provide a backend operation for retrieving training sessions accessible to the requesting user.

The operation must enforce role-based access and training group visibility rules. By default it returns planned sessions; callers may explicitly request completed sessions as well.

## 2. Actors

- **ADMIN** – can retrieve all planned training sessions and, when requested, all completed training sessions.
- **COACH** – can retrieve planned training sessions belonging to groups assigned to that coach and, when requested, completed sessions in those groups.

## 3. Preconditions

- The request is made by an authenticated user.
- The user has the ADMIN or COACH role.

## 4. Business Rules

### 4.1 Training Visibility – ADMIN

- ADMIN can retrieve all planned training sessions by default.
- When `includeCompleted=true`, ADMIN can retrieve planned and completed training sessions.
- Visibility does not depend on the training group's coach assignments.
- ADMIN can retrieve training sessions belonging to any group.

### 4.2 Training Visibility – COACH

- COACH can retrieve a training session only if its assigned training group is currently assigned to that coach.
- By default, only planned training sessions are returned. When `includeCompleted=true`, completed sessions in assigned groups are also returned.
- A training session belongs to exactly one training group.
- A training session without an assigned group is not possible.
- Visibility does not depend on whether the training session has any participants.
- All coaches assigned to the training's group have identical visibility.
- Visibility is based on current coach-to-group assignments, not on which coach created the training session.

### 4.3 Training and Group Relationships

- Every training session must belong to exactly one training group.
- The training group assigned to a training session cannot be changed after creation.
- Training sessions for different groups are separate records, even if they have the same name, date and training plan.
- Athlete participation is separate from training group assignment.
- Assigning a group to a training session does not automatically assign its athletes.
- Only athletes belonging to the training's group at the time of assignment can participate in that training session.
- A later change to an athlete's group does not change their historical participation in existing training sessions.

### 4.4 Training Status

- A newly created session has status `PLANNED`.
- The first successful UC-510 completion save changes its status to `COMPLETED`, even if some execution values remain `PENDING`.
- Completed sessions remain editable and can be corrected by authorized users.
- Only planned sessions are included by default. Completed sessions are included only when the caller explicitly sets `includeCompleted=true`.
- Each response item includes its `status`.
- Both statuses remain accessible through the training details operation, subject to its authorization rules.

### 4.5 Import Drafts

- Only persisted training sessions are included.
- Training import drafts and pending OCR imports must not be returned as ordinary training sessions.
- A training session created from an OCR import becomes visible only after the import has been accepted and the training session has been persisted.

## 5. Returned Information

For each accessible training session, the operation returns sufficient information to identify and summarize it.

The response must include at least:

- Training identifier.
- Training name.
- Training date.
- Assigned training group, including its identifier and name.
- Number of training definitions.
- Number of participating athletes.

The number of participating athletes must include all athletes assigned to the training session, regardless of their current training group.

The response must not expose data belonging to unrelated modules or sensitive information.

The response includes the session's status in addition to the identifying and summary information above.

## 6. API Contract

- **Method and path:** `GET /api/trainings`
- **Query parameter:** optional `includeCompleted` boolean, default `false`.
- When `includeCompleted=false` or omitted, return only `PLANNED` sessions.
- When `includeCompleted=true`, return both `PLANNED` and `COMPLETED` sessions.
- The response item includes `status` so clients can filter or label it.

## 7. Sorting

- Training sessions must be sorted by training date, newest first.
- Training sessions with the same date must have a deterministic secondary ordering.
- Sorting must be applied consistently by the backend.

## 8. Authorization Rules

- Only authenticated ADMIN and COACH users may access this operation.
- ADMIN receives all planned training sessions by default, or all sessions when `includeCompleted=true`.
- COACH receives only sessions belonging to currently assigned groups, with completed sessions included only when `includeCompleted=true`.
- The backend must derive the requesting user's identity and permissions from the authenticated security context.
- The operation must not rely on client-supplied user identifiers or group assignments to determine visibility.
- Access must not depend on which coach created the training session.

## 9. Validation and Error Scenarios

- **Unauthenticated request:** access is rejected.
- **User without ADMIN or COACH role:** access is rejected.
- **Invalid `includeCompleted` value:** the request is rejected as an invalid query parameter.
- **Unexpected server error:** the system reports an appropriate error without exposing internal implementation details.

An empty result is a valid response and must not be treated as an error.

## 10. Output

The operation returns a collection of training sessions accessible to the requesting user, filtered by the `includeCompleted` parameter.

If the user has no sessions matching the requested status, the operation returns an empty collection.

## 11. Consistency Requirements

- Training visibility rules must be consistent with authorization rules for retrieving training details.
- A training session visible in the list must also be accessible through the corresponding details operation, subject to the same authorization rules.
- Group visibility must be evaluated using the current coach-to-group relationships.
- A coach who is no longer assigned to a training's group must no longer see that training in the standard list.
- The backend must return each training session only once.
- The number of participants and definitions must reflect the persisted state of the training session.

## 12. Out of Scope

- Creating, editing or deleting training sessions (UC-505).
- Retrieving full training details.
- Searching and filtering by fields other than training status.
- Pagination.
- Reviewing or accepting OCR imports.
- Training statistics and athlete training history.
- Calendar views and future training planning.

## 13. Acceptance Criteria

1. ADMIN can retrieve all planned training sessions by default, regardless of their assigned groups.
2. COACH can retrieve only planned training sessions belonging to groups currently assigned to that coach by default.
3. All coaches assigned to the same group have identical visibility of its training sessions.
4. Training visibility does not depend on the number of assigned participants.
5. A training session belongs to exactly one group.
6. The response includes the required identifying and summary information.
7. The participant count includes all participants assigned to the training session, including athletes who have since changed their current group.
8. Completed training sessions are excluded by default and included only when `includeCompleted=true`.
9. Training import drafts and pending OCR imports are excluded.
10. Training sessions are sorted by date, newest first, with deterministic ordering for equal dates.
11. No duplicate training sessions are returned.
12. Each returned item includes its status, and an empty collection is returned when no sessions match the requested status.
13. Unauthorized users cannot retrieve the training list.