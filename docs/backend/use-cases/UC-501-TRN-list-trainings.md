# UC-501 – List Trainings

## 1. Purpose

Provide a backend operation for retrieving active training sessions accessible to the requesting user.

The operation must enforce role-based access and training group visibility rules.

## 2. Actors

- **ADMIN** – can retrieve all active training sessions.
- **COACH** – can retrieve active training sessions belonging to groups assigned to that coach.

## 3. Preconditions

- The request is made by an authenticated user.
- The user has the ADMIN or COACH role.

## 4. Business Rules

### 4.1 Training Visibility – ADMIN

- ADMIN can retrieve all active training sessions.
- Visibility does not depend on the training group's coach assignments.
- ADMIN can retrieve training sessions belonging to any group.

### 4.2 Training Visibility – COACH

- COACH can retrieve a training session only if its assigned training group is currently assigned to that coach.
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

- Only active training sessions are included in the standard list.
- Inactive training sessions must not be returned.
- Inactive training sessions remain accessible through the training details operation, subject to its authorization rules.

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

The exact response structure and field types will be defined during API design.

## 6. Sorting

- Training sessions must be sorted by training date, newest first.
- Training sessions with the same date must have a deterministic secondary ordering.
- Sorting must be applied consistently by the backend.

## 7. Authorization Rules

- Only authenticated ADMIN and COACH users may access this operation.
- ADMIN receives all active training sessions.
- COACH receives only active training sessions belonging to groups currently assigned to that coach.
- The backend must derive the requesting user's identity and permissions from the authenticated security context.
- The operation must not rely on client-supplied user identifiers or group assignments to determine visibility.
- Access must not depend on which coach created the training session.

## 8. Validation and Error Scenarios

- **Unauthenticated request:** access is rejected.
- **User without ADMIN or COACH role:** access is rejected.
- **Unexpected server error:** the system reports an appropriate error without exposing internal implementation details.

An empty result is a valid response and must not be treated as an error.

## 9. Output

The operation returns a collection of active training sessions accessible to the requesting user.

If the user has no accessible active training sessions, the operation returns an empty collection.

The exact API response structure will be defined during API design.

## 10. Consistency Requirements

- Training visibility rules must be consistent with authorization rules for retrieving training details.
- A training session visible in the list must also be accessible through the corresponding details operation, subject to the same authorization rules and the details operation's handling of inactive sessions.
- Group visibility must be evaluated using the current coach-to-group relationships.
- A coach who is no longer assigned to a training's group must no longer see that training in the standard list.
- The backend must return each training session only once.
- The number of participants and definitions must reflect the persisted state of the training session.

## 11. Out of Scope

- Creating, editing or deactivating training sessions.
- Retrieving full training details.
- Searching and filtering training sessions.
- Pagination.
- Reviewing or accepting OCR imports.
- Training statistics and athlete training history.
- Calendar views and future training planning.

## 12. Acceptance Criteria

1. ADMIN can retrieve all active training sessions, regardless of their assigned groups.
2. COACH can retrieve only active training sessions belonging to groups currently assigned to that coach.
3. All coaches assigned to the same group have identical visibility of its training sessions.
4. Training visibility does not depend on the number of assigned participants.
5. A training session belongs to exactly one group.
6. The response includes the required identifying and summary information.
7. The participant count includes all participants assigned to the training session, including athletes who have since changed their current group.
8. Inactive training sessions are excluded.
9. Training import drafts and pending OCR imports are excluded.
10. Training sessions are sorted by date, newest first, with deterministic ordering for equal dates.
11. No duplicate training sessions are returned.
12. An empty collection is returned when no active training sessions are accessible.
13. Unauthorized users cannot retrieve the training list.