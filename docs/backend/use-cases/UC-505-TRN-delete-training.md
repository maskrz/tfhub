# UC-505 – Delete Training

## 1. Purpose

Permanently delete an existing training session and all data owned by that session.

Deletion is irreversible. It removes the training session, its definitions and segments, athlete participations, execution records, measured results, and spike-usage data.

## 2. Actors

- **ADMIN** – can delete any training session.
- **COACH** – can delete a training session belonging to a group currently assigned to that coach.

## 3. Preconditions

- The request is made by an authenticated user.
- The user has the ADMIN or COACH role.
- The training session exists.
- The requesting user is authorized to delete the training session.

## 4. Business Rules

### 4.1 Deletion

- Deletion permanently removes the training session, regardless of its status or whether it contains participant or execution data.
- All session-owned records are deleted in the same transaction:
  - Training definitions.
  - Training segments.
  - Athlete participations.
  - Segment executions, including measured results and spike usage.
- The operation must not delete or modify the associated training group, athletes, or their other data.
- Database foreign keys remain restrictive; the service deletes dependent records in dependency order rather than relying on cascading deletes.
- No tombstone or soft-deleted training record is retained.

### 4.2 Visibility After Deletion

- A deleted training session no longer appears in UC-501 results, including when `includeCompleted=true`.
- UC-504 returns not found for the deleted session.
- Deletion does not change the training group's assignment or any athlete's current group.

## 5. Authorization Rules

- Only authenticated ADMIN and COACH users may delete training sessions.
- ADMIN can delete any training session.
- COACH can delete a session only if its group is currently assigned to that coach.
- All coaches assigned to the training's group have identical deletion permissions.
- Authorization does not depend on which coach created the training session.
- The backend derives the requesting user's identity and permissions from the authenticated security context.
- The backend must not rely on client-supplied user identifiers, roles, or group assignments.
- An inaccessible session and a nonexistent session produce the same not-found response.

## 6. API Contract

- **Method and path:** `DELETE /api/trainings/{trainingId}`
- **Request:** no request body.
- **Success response:** `204 No Content`.

## 7. Validation and Error Scenarios

- **Unauthenticated request:** access is rejected.
- **User without ADMIN or COACH role:** access is rejected.
- **Training ID is not a positive integer:** return HTTP 400.
- **Training session does not exist:** return HTTP 404.
- **Training session exists but COACH is not assigned to its group:** return the same HTTP 404 as for a nonexistent session.
- **Unexpected server error:** report an appropriate error without exposing internal implementation details.
- Existing participants or execution records do not prevent deletion.

## 8. Transactional Consistency

- The complete deletion is atomic.
- Delete segment executions and participations before segments and definitions, then delete the session.
- If any step fails, the transaction rolls back and the training and all its related data remain intact.
- Lock the training row while checking authorization and deleting, preventing concurrent training edits from leaving partial records.

## 9. Out of Scope

- Deleting training groups, athletes, accounts, or other training sessions.
- Modifying historical test results or athlete records outside this training session.
- Deleting or modifying OCR import records.
- Undo, restore, or reactivation.

## 10. Acceptance Criteria

1. ADMIN can delete any existing training session.
2. COACH can delete a training session only while currently assigned to its group.
3. Coaches assigned to the same group have identical deletion permissions.
4. Planned and completed sessions can be deleted.
5. Deletion permanently removes the session and every definition, segment, participation, and execution owned by it.
6. No associated training group, athlete, or unrelated data is deleted or modified.
7. Deleted sessions no longer appear in planned-only or include-completed lists and cannot be retrieved by UC-504.
8. Sessions with recorded execution data can be deleted.
9. Inaccessible and nonexistent sessions produce the same not-found response.
10. The entire deletion is atomic; a failure leaves all session data intact.
11. Unauthorized users cannot delete training sessions.
