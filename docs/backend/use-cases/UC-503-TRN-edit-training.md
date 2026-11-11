# UC-503 – Edit Training Plan

## 1. Purpose

Allow an authorized user to edit the plan of an existing active training session: its name, date, definitions and segments. Athlete assignments and execution data are managed separately through UC-510 – Complete Training.

The operation preserves the training session's identity, group and historical execution associations.

## 2. Actors and Preconditions

- **ADMIN** – can edit any active training session.
- **COACH** – can edit an active training session only while currently assigned to its group.
- The requester is authenticated and has the ADMIN or COACH role.
- The training exists and is active.

## 3. Business Rules

### 3.1 Training Session

- The training name and date may be changed.
- The training must retain a non-empty name and valid date.
- The training group is immutable and is not accepted as an editable request field.
- Inactive trainings cannot be edited.
- The operation does not edit athlete assignments or execution status, results, or spike usage.

### 3.2 Authorization

- ADMIN can edit any active training.
- COACH can edit only trainings belonging to groups currently assigned to that coach.
- All coaches assigned to a group have identical permissions.
- Permissions do not depend on who created the training.
- Authorization is evaluated from the authenticated user and current group assignments.

### 3.3 Definitions

- Definitions can be added, renamed, reordered and removed.
- Every training must contain at least one definition; every definition must have a non-empty name and at least one segment.
- Existing definition IDs must be preserved when retained.
- A definition can be removed only if none of its athlete executions has status `RECORDED`.
- Removing an eligible definition also removes its athlete assignments and their execution rows, including pending and other non-`RECORDED` executions. The frontend must clearly communicate this consequence before submission.
- Removing a definition must not affect data belonging to other definitions.

### 3.4 Segments

- Segments can be added, edited, reordered and removed within a definition.
- Each segment has a positive distance, a unique sequence number within its definition, and an optional non-negative rest period.
- Existing segment IDs must be preserved when retained; sequence numbers are ordering only.
- A segment with any `RECORDED` execution cannot be removed or have its distance changed.
- Rest and order may be changed regardless of execution status.
- Removing an eligible segment removes its associated execution rows for all participants, including pending and other non-`RECORDED` executions.
- Adding a segment creates a `PENDING` execution for each participant already assigned to that definition. This is derived data initialization, not an edit to existing execution values.

### 3.5 Historical Data

- The operation must not change existing execution status, measured result or spike usage.
- Reordering definitions or segments must preserve execution associations by stable IDs.
- Existing participants remain valid after their current training group changes.
- Any rejected modification must leave the entire training unchanged.

## 4. API Contract

- **Method and path:** `PUT /api/trainings/{trainingId}`
- **Request:** training `name`, `trainingDate`, and the complete ordered `definitions` collection. Each definition contains nullable `id`, `name`, `sequenceNumber`, and `segments`; each segment contains nullable `id`, `sequenceNumber`, `distanceM`, and nullable `restAfterSeconds`.
- The request has no `trainingGroupId`, athlete assignments, or execution records. These values cannot be changed through this endpoint.
- A null definition or segment ID identifies a new item. Existing IDs identify retained items and must belong to this training/definition.
- **Response:** at least the updated training `id`.

The exact JSON DTO and error representation follow the backend API conventions.

## 5. Validation and Error Scenarios

- Reject unauthenticated users and users without ADMIN or COACH role.
- Reject a missing training; do not reveal an inaccessible training to an unassigned coach.
- Reject edits to inactive trainings and edits that attempt to change the training group.
- Reject blank names, invalid dates, an empty definitions collection, blank definition names, empty segment collections, invalid distances/rests, duplicate order numbers, or IDs outside the training.
- Reject removal of a definition with any `RECORDED` execution.
- Reject removal of a segment with any `RECORDED` execution or a change to its distance while such an execution exists.
- Validation or persistence errors must not leave partial changes.

## 6. Transactional Consistency

The full plan update, including any required creation/removal of derived `PENDING` execution rows, is atomic. If validation or persistence fails, no training or child data is partially modified.

## 7. Out of Scope

- Adding, removing or moving athlete assignments except as the unavoidable consequence of deleting an eligible definition.
- Editing athlete execution status, result or spike usage.
- Editing inactive trainings, changing the training group, deactivation/reactivation, imports, templates and statistics.

## 8. Acceptance Criteria

1. ADMIN can edit any active training; COACH can edit only while assigned to its group.
2. Name, date, definition names/order, segment distances/rest/order can be edited.
3. The training group cannot be changed and is not part of this request.
4. Existing definition and segment IDs remain stable when items are retained or reordered.
5. A definition or segment with a `RECORDED` execution cannot be removed; a segment distance with a `RECORDED` execution cannot be changed.
6. Removing a definition with no `RECORDED` execution removes its assignments and associated executions; removing an eligible segment removes its executions.
7. Adding a segment initializes a `PENDING` execution for each existing participant in that definition.
8. Existing execution values are unchanged by plan edits.
9. All changes are atomic, and inaccessible/inactive trainings cannot be edited.
