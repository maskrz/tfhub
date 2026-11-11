# UC-508 – Review Training Import

## 1. Purpose

Provide backend operations for retrieving, reviewing and correcting a training import draft generated through UC-507.

The operation must allow an authorized user to correct all recognized training information, resolve athlete matching issues and prepare the import for final acceptance.

Reviewing or modifying a draft must not create or modify persisted training session data.

## 2. Actors

- **ADMIN** – can review imports belonging to any training group.
- **COACH** – can review imports belonging to groups currently assigned to that coach.

## 3. Preconditions

- The request is made by an authenticated user.
- The user has the ADMIN or COACH role.
- The training import exists.
- The import has a successfully generated draft.
- The import is in the REVIEW_REQUIRED state.
- The requesting user is authorized to access the import.

## 4. Business Rules

### 4.1 Import Review

- An import in REVIEW_REQUIRED state can be retrieved for review.
- The review operation must return the complete draft, including:
    - Recognized training name and date.
    - Selected training group.
    - Training definitions.
    - Training segments.
    - Segment distances and rest periods.
    - Recognized athlete rows.
    - Athlete matching information.
    - Execution data for each athlete and segment.
    - Spike usage.
    - Recognition uncertainty indicators and relevant recognition details.
- The original uploaded image must remain available for comparison with recognized data.
- The draft must preserve the relationship between definitions, segments, athletes and execution data.
- The review operation must not modify the original uploaded image or the persisted training data.

### 4.2 Editable Training Information

The authorized user must be able to correct all recognized training information, including:

- Training name.
- Training date.
- Training group, subject to authorization rules.
- Training definition names.
- Training segment distances.
- Training segment rest periods.
- Training segment ordering.
- Athlete rows and athlete assignments.
- Execution statuses.
- Measured results.
- Spike usage.

The corrected draft must satisfy the plan rules defined in UC-502 and the athlete participation/execution rules defined in UC-510.

### 4.3 Training Group

- Every import must remain associated with exactly one existing active training group.
- ADMIN may change the selected group to any existing active group.
- COACH may change the selected group only to a group currently assigned to that coach.
- The group selected during upload is the initial group context for athlete recognition.
- Changing the group must trigger revalidation of all athlete assignments.
- Athletes assigned to the previous group must not remain automatically assigned if they do not belong to the newly selected group.
- The system must not automatically move athletes between groups.
- The import must not be accepted while any assigned athlete does not belong to its selected group.

### 4.4 Athlete Matching

- Athlete matching must be restricted to the import's currently selected training group.
- A recognized athlete may be matched to an existing athlete belonging to that group.
- An ambiguous or unmatched athlete must remain unresolved until explicitly handled.
- The authorized user must be able to:
    - Match an unresolved row to an existing athlete from the selected group.
    - Change an existing athlete assignment to another eligible athlete from the selected group.
    - Explicitly skip an athlete row.
- The system must not create new athlete records during review.
- An athlete row marked as skipped must not produce athlete participation or execution records after acceptance.
- An athlete row must not be assigned to more than one athlete participation within the same training session.
- An athlete may participate in only one training definition within a given training session.
- If changing the selected group invalidates an existing athlete assignment, that assignment must become unresolved and require review.
- Every athlete row must be either correctly matched or explicitly skipped before acceptance.

### 4.5 Training Definitions and Segments

- The authorized user may add, rename, reorder or remove training definitions.
- Every training definition must have a non-empty name.
- At least one training definition must remain in the draft.
- Every training definition must contain at least one segment.
- The authorized user may add, modify, reorder or remove segments.
- Every segment must have a positive distance.
- Segment order must be unique within its definition.
- Each segment may define a rest period after its execution.
- The rest period after the final segment is optional.
- The draft must not contain execution data referring to a removed segment.
- All execution data must refer to a valid athlete row and segment within the same draft.

### 4.6 Execution Data Correction

- The authorized user may correct execution data for every athlete row and segment.
- The system must distinguish between:
    - **PENDING** – no execution information has been provided.
    - **RECORDED** – a measured result has been recorded.
    - **COMPLETED_WITHOUT_RESULT** – the athlete completed the segment, but no measured result is available.
    - **NOT_COMPLETED** – the athlete did not complete the segment.
- A measured result must be a non-negative numeric value.
- A measured result may only be provided for an execution with RECORDED status.
- RECORDED status requires a valid measured result.
- Spike usage must be editable independently of execution status and measured result.
- The authorized user must be able to replace uncertain or incorrectly recognized values.
- The authorized user must be able to correct a crossed-out or unreadable result.
- The authorized user must be able to change the execution status when the original recognition was incorrect.
- The system must not infer a measured result from an empty cell.
- Recognition uncertainty must not prevent the user from manually correcting a value.

### 4.7 Draft Validation

- All corrected data must be validated before it can be considered ready for acceptance.
- The draft must contain a valid training name and date.
- The draft must contain exactly one existing active training group.
- The draft must contain at least one valid training definition.
- Every definition must contain at least one valid segment.
- All assigned athletes must belong to the selected training group.
- All athlete rows must be explicitly matched or skipped.
- An athlete must not be assigned to multiple definitions within the same training session.
- All execution data must reference valid draft entities.
- All execution statuses and measured results must satisfy the rules defined in UC-510.
- Recognition uncertainty flags may remain as historical information after the user has manually corrected the corresponding values.
- The backend must distinguish between unresolved recognition issues and corrected data.
- The import must not become ready for acceptance while any mandatory validation issue remains.

### 4.8 Review Lifecycle

- Only imports in REVIEW_REQUIRED state may be modified through this operation.
- Saving corrections must not change the import to ACCEPTED or REJECTED.
- A successfully saved draft remains in REVIEW_REQUIRED state until explicitly accepted or rejected through UC-509.
- A draft must be validated again during acceptance.
- The system must not automatically accept an import because all recognition fields appear certain.
- The authorized user confirms the entire import in one operation through UC-509, not individual cells or rows.
- The original source image must be retained throughout review and after acceptance or rejection.

## 5. Authorization Rules

- Only authenticated ADMIN and COACH users may review training imports.
- ADMIN can review imports belonging to any training group.
- COACH can review imports only while currently assigned to the import's selected group.
- A coach who is no longer assigned to the import's group cannot retrieve or modify its draft.
- Changing the import group must be authorized against the requesting user's current group assignments.
- The backend must derive the requesting user's identity and permissions from the authenticated security context.
- The backend must not rely on client-supplied user identifiers, roles or group assignments.

## 6. Validation and Error Scenarios

- **Unauthenticated request:** access is rejected.
- **User without ADMIN or COACH role:** access is rejected.
- **Import does not exist:** the operation returns an appropriate not-found response.
- **COACH is not currently assigned to the import's group:** the operation returns the same not-found response as for a nonexistent import.
- **Import is not in REVIEW_REQUIRED state:** draft modification is rejected.
- **Missing or blank training name:** modification is rejected.
- **Missing or invalid training date:** modification is rejected.
- **Selected group does not exist or is inactive:** modification is rejected.
- **COACH selects a group not assigned to them:** modification is rejected.
- **Athlete does not belong to the selected group:** the assignment is rejected.
- **Athlete does not exist:** the assignment is rejected.
- **Athlete is assigned more than once within the same training session:** modification is rejected.
- **Athlete row remains unresolved or is neither matched nor skipped:** the draft cannot be marked ready for acceptance.
- **Training definition has a blank name:** modification is rejected.
- **Training definition has no segments:** modification is rejected.
- **Training definition is removed while it is the last remaining definition:** modification is rejected.
- **Segment has an invalid or non-positive distance:** modification is rejected.
- **Duplicate or invalid segment order:** modification is rejected.
- **Execution data references a missing athlete row or segment:** modification is rejected.
- **Invalid execution status or measured result:** modification is rejected.
- **RECORDED status without a valid measured result:** modification is rejected.
- **Measured result supplied for a status other than RECORDED:** modification is rejected.
- **Unexpected server error:** the system reports an appropriate error without exposing internal implementation details.

Validation failures must not leave partially modified draft data.

## 7. Output

The operation must provide the complete current draft and its validation state.

The response must include at least:

- Import identifier.
- Import status.
- Selected training group identifier and name.
- Recognized and corrected training information.
- Training definitions and segments.
- Athlete rows and their matching or skipped status.
- Execution data, including status, measured result where available and spike usage.
- Recognition uncertainty information.
- Draft validation results indicating whether the import is ready for acceptance.

The exact response structure and field types will be defined during API design.

## 8. Transactional Consistency

- Changes to a draft must be persisted atomically.
- A failed modification must not leave partially updated draft data.
- Changing the selected group and revalidating athlete assignments must be performed consistently.
- Removing or modifying definitions and segments must preserve valid references within the draft.
- Draft changes must not affect the original source image.
- Draft changes must not create or modify persisted training session or execution records.
- The backend must validate the complete draft before saving it as ready for acceptance.

## 9. Out of Scope

- Uploading training photos.
- Running OCR or retrying image processing.
- Creating new athlete records.
- Modifying existing athlete records or group assignments.
- Accepting or rejecting imports.
- Creating persisted training sessions and execution records.
- Editing already accepted or rejected imports.
- Automatic acceptance based on model confidence.
- Training statistics and athlete training history.

## 10. Acceptance Criteria

1. ADMIN can retrieve and review imports belonging to any training group.
2. COACH can retrieve and review imports only while assigned to the import's group.
3. The complete draft and original source image are available for review.
4. The authorized user can correct training name and date.
5. The authorized user can change the training group within their authorization scope.
6. Changing the group invalidates athlete assignments that no longer belong to the selected group.
7. Athlete matching is restricted to the selected group.
8. Every athlete row must be matched to an existing athlete or explicitly skipped before acceptance.
9. The system does not create new athlete records during review.
10. Training definitions can be added, modified, reordered and removed subject to validation rules.
11. Training segments can be added, modified, reordered and removed subject to validation rules.
12. The authorized user can correct execution status, measured result and spike usage independently.
13. The system distinguishes PENDING, RECORDED, COMPLETED_WITHOUT_RESULT and NOT_COMPLETED execution states.
14. Measured results are non-negative numeric values and can only be supplied with RECORDED status.
15. Recognition uncertainty does not prevent manual correction.
16. The complete draft is validated before it can be considered ready for acceptance.
17. Saving corrections does not automatically accept or reject the import.
18. The import remains in REVIEW_REQUIRED state until UC-509 is executed.
19. Draft changes do not create or modify persisted training session or execution records.
20. Draft modifications are persisted atomically.
21. The original source image remains available after review and subsequent acceptance or rejection.
22. Unauthorized users cannot retrieve or modify training import drafts.