# UC-501 – List Trainings

## 1. Purpose

Provide a backend operation for retrieving training sessions accessible to the requesting user.

The operation must enforce role-based access and training group visibility rules. It returns both training types in one diary; callers may filter by type. By default it returns planned sessions; callers may explicitly request completed sessions as well.

## 2. Actors

- **ADMIN** – can retrieve all planned training sessions and, when requested, all completed training sessions.
- **COACH** – can retrieve planned training sessions belonging to groups assigned to that coach and, when requested, completed sessions in those groups.
- **ATHLETE** – can retrieve only FREE_TEXT sessions assigned to the athlete's current group or explicitly assigned to that athlete.

## 3. Preconditions

- The request is made by an authenticated user.
- The user has the ADMIN, COACH or ATHLETE role.

## 4. Business Rules

### 4.1 Training Visibility – ADMIN

- ADMIN can retrieve all planned training sessions by default.
- When `includeCompleted=true`, ADMIN can retrieve planned and completed training sessions.
- Visibility does not depend on the training group's coach assignments.
- ADMIN can retrieve training sessions belonging to any group.

### 4.2 Training Visibility – COACH

- COACH can retrieve a training session only if its assigned training group is currently assigned to that coach.
- By default, only planned training sessions are returned. When `includeCompleted=true`, completed sessions in assigned groups are also returned.
- Trainings have type `TEMPO` or `FREE_TEXT`; omitting the type filter returns both.
- TEMPO belongs to exactly one group. FREE_TEXT is assigned to either one group or one or more athletes, never both.
- Visibility does not depend on whether the training session has any participants.
- All coaches assigned to a group have identical visibility of its group-assigned sessions.
- Visibility is based on current coach-to-group assignments, not on which coach created the training session.
- A coach can view an ATHLETES-mode FREE_TEXT session only while currently assigned to every selected athlete's current group.

### 4.3 Training and Group Relationships

- TEMPO sessions belong to one group. FREE_TEXT sessions use either `GROUP` with one group or `ATHLETES` with one or more explicit athlete targets.
- TEMPO's group assignment is unchanged; FREE_TEXT assignment can be changed through UC-503.
- Training sessions for different groups are separate records, even if they have the same name, date and training plan.
- Athlete participation is separate from training group assignment.
- For TEMPO, assigning a group does not automatically assign its athletes; UC-510 manages participation.
- GROUP-mode FREE_TEXT visibility follows current group membership. ATHLETES-mode visibility is limited to explicit targets and does not include other group members.
- A later change to an athlete's group does not change their historical participation in existing training sessions.

### 4.3.1 Athlete Visibility

- ATHLETE list results contain only FREE_TEXT sessions visible through the athlete's current group (GROUP mode) or explicit target row (ATHLETES mode); TEMPO sessions are not included.
- Athlete accounts can list planned and completed visible FREE_TEXT sessions; `includeCompleted` retains its existing default of false.
- For a calendar date with one or more FREE_TEXT sessions explicitly assigned to the athlete, omit all group-assigned sessions on that date from that athlete's list. Other athletes' assignments do not affect the list.
- Apply this override regardless of session status and before status filtering. Undated sessions do not override each other. Staff lists are unaffected; access to group-session details by direct link remains unchanged.

### 4.4 Training Status

- A session created directly through UC-502 has status `PLANNED`; acceptance of a dated import creates a `COMPLETED` session and acceptance of an undated import creates a `PLANNED` session.
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
- Assignment type and, for FREE_TEXT, selected-athlete count. The group is null for ATHLETES mode.
- Number of training definitions.
- Number of participating athletes.
- Training type (`TEMPO` or `FREE_TEXT`).

The number of participating athletes must include TEMPO participants, regardless of their current training group. FREE_TEXT sessions have no tempo definitions/participations; selected-athlete assignment is not a participation record.

The response must not expose data belonging to unrelated modules or sensitive information.

The response includes the session's status in addition to the identifying and summary information above.

## 6. API Contract

- **Method and path:** `GET /api/trainings`
- **Query parameters:** optional `includeCompleted` boolean, default `false`; optional `trainingType` enum (`TEMPO` or `FREE_TEXT`); optional `month` in `yyyy-MM` format; optional `includeUndated` boolean, default `false`.
- When `includeCompleted=false` or omitted, return only `PLANNED` sessions.
- When `includeCompleted=true`, return both `PLANNED` and `COMPLETED` sessions.
- When `trainingType` is omitted, return both types; when provided, return only matching records.
- When `month` is provided, return dated sessions within that calendar month. When `includeUndated=true`, also return otherwise-visible sessions whose `trainingDate` is null.
- When `month` is omitted, retain the existing unbounded list behavior; `includeUndated` has no effect.
- The response item includes `status` and `trainingType` so clients can filter or label it.
- The `trainingDate` response field may be null for planned sessions; clients must handle null.

## 7. Sorting

- Undated training sessions precede dated sessions; dated sessions are sorted by training date, newest first.
- Training sessions with the same date must have a deterministic secondary ordering.
- Sorting must be applied consistently by the backend.

## 8. Authorization Rules

- Only authenticated ADMIN, COACH and ATHLETE users may access this operation.
- ADMIN receives all planned training sessions by default, or all sessions when `includeCompleted=true`.
- COACH receives only sessions belonging to currently assigned groups, with completed sessions included only when `includeCompleted=true`.
- ATHLETE receives only visible FREE_TEXT sessions; direct assignment is checked by the backend and TEMPO is never returned.
- The backend must derive the requesting user's identity and permissions from the authenticated security context.
- The operation must not rely on client-supplied user identifiers or group assignments to determine visibility.
- Access must not depend on which coach created the training session.

## 9. Validation and Error Scenarios

- **Unauthenticated request:** access is rejected.
- **User without ADMIN, COACH or ATHLETE role:** access is rejected.
- **Invalid `includeCompleted` value:** the request is rejected as an invalid query parameter.
- **Invalid `trainingType` value:** the request is rejected as an invalid query parameter.
- **Unexpected server error:** the system reports an appropriate error without exposing internal implementation details.

An empty result is a valid response and must not be treated as an error.

## 10. Output

The operation returns a collection of training sessions accessible to the requesting user, filtered by the `includeCompleted` and optional `trainingType` parameters.

If the user has no sessions matching the requested status, the operation returns an empty collection.

## 11. Consistency Requirements

- List results are a subset of sessions authorized for details; the athlete-specific date override can hide a group session that remains accessible by direct link.
- A training session visible in the list must also be accessible through the corresponding details operation, subject to the same authorization rules.
- Group visibility must be evaluated using the current coach-to-group relationships.
- A coach who is no longer assigned to a training's group must no longer see that training in the standard list.
- The backend must return each training session only once.
- The number of participants and definitions must reflect persisted TEMPO state; FREE_TEXT assignment targets are not participation records.

## 12. Out of Scope

- Creating, editing or deleting training sessions (UC-505).
- Retrieving full training details.
- Searching and filtering by fields other than training status and training type, except for the optional calendar-month retrieval described in the API contract.
- Pagination.
- Reviewing or accepting OCR imports.
- Training statistics and athlete training history.
- Calendar views and future training planning.

## 13. Acceptance Criteria

1. ADMIN can retrieve all planned training sessions by default, regardless of their assigned groups.
2. COACH can retrieve only planned training sessions belonging to groups currently assigned to that coach by default.
3. All coaches assigned to the same group have identical visibility of its training sessions.
4. Training visibility does not depend on the number of assigned participants.
5. TEMPO sessions belong to one group; FREE_TEXT sessions have exactly one valid assignment mode.
6. The response includes the required identifying and summary information.
7. The participant count includes all participants assigned to the training session, including athletes who have since changed their current group.
8. Completed training sessions are excluded by default and included only when `includeCompleted=true`.
9. Training import drafts and pending OCR imports are excluded.
10. Undated sessions appear first; dated sessions are sorted newest first, with deterministic ordering for equal dates.
11. No duplicate training sessions are returned.
12. Each returned item includes its status and type; the optional type filter returns only the selected type, while omission returns both.
13. An empty collection is returned when no sessions match the requested status and type.
14. ATHLETE receives only assigned FREE_TEXT sessions; unselected athletes receive none.
15. Unauthorized users cannot retrieve the training list.
16. Optional month retrieval returns only dated sessions in the requested month, optionally including undated sessions, while preserving the same role and group visibility rules.