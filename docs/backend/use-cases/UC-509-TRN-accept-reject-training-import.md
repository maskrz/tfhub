# UC-509 – Accept or Reject Training Import

## 1. Purpose

Provide backend operations for accepting or rejecting a reviewed training import.

Acceptance must atomically create a persisted training session and all its related data from the validated import draft.

Rejection must preserve the import, its current draft, processing attempts and optimized JPEG without creating a training session.

## 2. Actors

- **ADMIN** – can accept or reject imports belonging to any training group.
- **COACH** – can accept or reject imports belonging to groups currently assigned to that coach.

## 3. Preconditions

- The request is made by an authenticated user.
- The user has the ADMIN or COACH role.
- The training import exists.
- The import is in the REVIEW_REQUIRED state.
- The import has a generated draft.
- The requesting user is authorized to access and accept or reject the import.

## 4. Business Rules

### 4.1 Acceptance

- Acceptance confirms the entire training import in one operation.
- The user must explicitly confirm the complete reviewed draft.
- Individual cells, athletes or training definitions cannot be accepted separately.
- Acceptance creates exactly one persisted training session.
- The training session must be created using the data from the latest saved and validated draft.
- The persisted training session must include:
    - Training name and date.
    - Exactly one assigned training group.
    - All training definitions.
    - All training segments.
    - All matched athlete participations.
    - All valid execution data.
- Athlete rows explicitly marked as skipped must not create athlete participations or execution records.
- Unresolved athlete rows must prevent acceptance.
- Only athletes matched to existing athletes belonging to the selected training group may be persisted.
- An athlete may participate in only one training definition within the created training session.
- Training definitions and segments must preserve their reviewed order.
- Execution status, measured results and spike usage must preserve the values confirmed during review.
- The created training session must have status `COMPLETED`, even if some executions remain `PENDING`.
- The created training session must retain the immutable group selected during upload.
- The import must transition to ACCEPTED only after the complete training session and its related data have been successfully persisted.

### 4.2 Acceptance Validation

- The complete draft must be validated again during acceptance.
- The draft must contain a non-empty training name and valid date.
- The import must reference the existing training group selected during upload; the group is immutable.
- The requesting user must still be authorized for the selected group.
- The draft must contain at least one training definition.
- Every definition must have a non-empty name and at least one valid segment.
- Every segment must have a positive distance and valid unique ordering within its definition.
- A null distance blocks acceptance; the validation error identifies the definition and segment.
- All athlete rows must be explicitly matched or skipped.
- All matched athletes must exist and belong to the selected group at the time of acceptance.
- No athlete may be assigned to multiple definitions within the same training session.
- All execution data must reference valid participants and segments.
- Execution statuses and measured results must satisfy the rules defined in UC-510.
- A measured result must be a non-negative numeric value and may only be persisted with RECORDED status.
- Spike usage must be persisted independently for each segment execution.
- Recognition uncertainty may remain as historical information if the user has manually reviewed and corrected the corresponding data.
- The system must not rely solely on AI confidence indicators to determine whether an import can be accepted.

### 4.3 Rejection

- The authorized user may explicitly reject an import in REVIEW_REQUIRED state.
- Rejection changes the import status to REJECTED.
- Rejection must preserve:
    - The optimized JPEG retained by UC-506.
    - The generated draft.
    - Recognized and corrected values.
    - Athlete matching decisions.
    - Processing-attempt history and the current corrected draft. The system does not keep a revision history or an audit trail of individual draft edits.
- Rejection must not create a persisted training session.
- A rejected import cannot be accepted or edited again.
- A rejected import remains available for historical inspection, subject to authorization rules.

### 4.4 Import Lifecycle

- An import in REVIEW_REQUIRED state may be accepted or rejected.
- An ACCEPTED import cannot be accepted or rejected again.
- A REJECTED import cannot be accepted or rejected again.
- An import in PENDING, PROCESSING or FAILED state cannot be accepted or rejected.
- The final status transition must be persisted consistently with the corresponding operation.
- The system must prevent concurrent acceptance or rejection of the same import.
- Repeated requests must not create duplicate training sessions or duplicate execution records.

### 4.5 Source Image Retention

- The optimized JPEG must be retained after acceptance.
- The optimized JPEG must be retained after rejection.
- Acceptance or rejection must not modify or delete the source image.
- The source image must remain associated with the import for future inspection.
- If the accepted training is later permanently deleted under UC-505, the linked import and its source image are also deleted.

## 5. Authorization Rules

- Only authenticated ADMIN and COACH users may accept or reject training imports.
- ADMIN can accept or reject imports belonging to any existing training group.
- COACH can accept or reject imports only while currently assigned to the import's selected group.
- Authorization must be checked against the immutable group selected during upload.
- A coach who is no longer assigned to the import's group cannot accept or reject it.
- The backend must derive the requesting user's identity and permissions from the authenticated security context.
- The backend must not rely on client-supplied user identifiers, roles or group assignments.

## 6. Validation and Error Scenarios

- **Unauthenticated request:** access is rejected.
- **User without ADMIN or COACH role:** access is rejected.
- **Import does not exist:** the operation returns an appropriate not-found response.
- **COACH is not currently assigned to the import's group:** the operation returns the same not-found response as for a nonexistent import.
- **Import is not in REVIEW_REQUIRED state:** acceptance or rejection is rejected.
- **Draft is missing or invalid:** acceptance is rejected.
- **Training group does not exist:** acceptance is rejected.
- **COACH is no longer assigned to the selected group:** acceptance or rejection is rejected.
- **Training name or date is invalid:** acceptance is rejected.
- **No valid training definitions exist:** acceptance is rejected.
- **Training definition has no segments:** acceptance is rejected.
- **Invalid segment distance or ordering:** acceptance is rejected.
- **Unresolved athlete rows remain:** acceptance is rejected.
- **Matched athlete does not exist or no longer belongs to the selected group:** acceptance is rejected.
- **Athlete is assigned to multiple definitions:** acceptance is rejected.
- **Execution data is invalid or references missing draft entities:** acceptance is rejected.
- **Invalid execution status or measured result:** acceptance is rejected.
- **Measured result supplied for a status other than RECORDED:** acceptance is rejected.
- **Persistence failure while creating the training session:** the entire acceptance operation is rolled back.
- **Concurrent acceptance or rejection:** only one operation may succeed.
- **Import is already ACCEPTED or REJECTED:** further finalization requests are rejected.
- **Unexpected server error:** the system reports an appropriate error without exposing internal implementation details.

Rejection does not require the draft to pass acceptance validation because its purpose is to discard the import from the active workflow.

## 7. Output

### 7.1 Acceptance Output

A successful acceptance returns information identifying:

- The import identifier.
- The final import status.
- The newly created training session identifier.

Use `POST /api/training-imports/{importId}/accept`.

### 7.2 Rejection Output

A successful rejection returns information identifying:

- The import identifier.
- The final import status.

Use `POST /api/training-imports/{importId}/reject`.

## 8. Transactional Consistency

### 8.1 Acceptance

- Creation of the training session and all related records must be performed atomically.
- The import status must transition to ACCEPTED in the same transaction as training session creation.
- If any validation or persistence step fails, the entire operation must be rolled back.
- No partially created training session, definitions, segments, athlete participations or execution records may remain after failure.
- The import must remain in REVIEW_REQUIRED state if acceptance fails.
- Concurrent requests must not create duplicate training sessions.
- The backend must ensure that only one successful acceptance can occur for a given import.

### 8.2 Rejection

- Rejection must be performed atomically.
- The import status must transition to REJECTED only if the operation succeeds.
- The draft and source image must remain intact.
- Rejection must not create or modify persisted training session data.

## 9. Out of Scope

- Uploading training photos.
- Running OCR or retrying image processing.
- Reviewing or editing import drafts.
- Creating or modifying athlete records.
- Changing athlete group assignments.
- Modifying accepted training sessions.
- Reactivating rejected imports.
- Reopening accepted or rejected imports.
- Directly deleting source images or import history; deleting an accepted training under UC-505 also deletes its linked import.
- Automatic acceptance based on AI confidence.
- Training statistics and athlete training history.

## 10. Acceptance Criteria

1. ADMIN can accept or reject imports belonging to any training group.
2. COACH can accept or reject imports only while currently assigned to the import's selected group.
3. Acceptance requires an import in REVIEW_REQUIRED state.
4. Rejection requires an import in REVIEW_REQUIRED state.
5. Acceptance confirms the complete reviewed draft in one operation.
6. The complete draft is validated again before acceptance.
7. All athlete rows must be matched or explicitly skipped before acceptance.
8. Only existing athletes belonging to the selected group can be persisted as participants.
9. Skipped athlete rows do not create athlete participations or execution records.
10. Acceptance creates exactly one training session with status `COMPLETED`, even if some executions remain `PENDING`.
11. The created training session contains the reviewed definitions, segments, participants and execution data. Import acceptance remains an atomic path that creates a complete session; normal post-creation participation and execution changes use UC-510.
12. Execution statuses, measured results and spike usage are preserved.
13. The import transitions to ACCEPTED only after successful creation of all training data.
14. Acceptance is atomic: failure leaves no partially created training data and the import remains REVIEW_REQUIRED.
15. Rejection changes the import status to REJECTED.
16. Rejection does not create a training session or execution records.
17. Rejected imports retain their current draft, optimized JPEG and processing-attempt history; individual draft-edit history is not retained.
18. Accepted imports retain their optimized JPEG unless the linked training is later permanently deleted under UC-505.
19. ACCEPTED and REJECTED imports cannot be finalized again.
20. Concurrent requests cannot create duplicate training sessions.
21. Unauthorized users cannot accept or reject training imports.