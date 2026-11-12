# UC-510 – Complete Training

## 1. Purpose

Allow an authorized user to manage athlete participation in an existing training and record or correct each participant's execution status, measured result and spike usage.

The training plan is managed separately through UC-503 – Edit Training.

## 2. Actors and Preconditions

- **ADMIN** – can complete any training.
- **COACH** – can complete a training only while currently assigned to its group.
- The requester is authenticated and has the ADMIN or COACH role.
- The training exists.

## 3. Business Rules

### 3.1 Authorization and Group

- ADMIN can manage participation and execution data for any training.
- COACH can do so only while assigned to the training's group.
- All assigned coaches have identical permissions; creator ownership is not used.
- The training group is immutable and is never accepted as a request field.

### 3.2 Athlete Participation

- Athletes can be assigned to a definition, removed, or reassigned to another definition.
- An athlete may participate in only one definition within the training.
- A new assignment is allowed only if the athlete currently belongs to the training's group at assignment time. An athlete without a current group cannot be newly assigned.
- Existing participation remains valid after an athlete changes their current group and must not be revalidated against current group membership during updates.
- A request explicitly identifies participation additions, updates, and removals. Omitted participants and executions are unchanged.
- A participation may be removed or reassigned only if none of its executions has status `RECORDED`. Reassignment is represented by removing the old participation and adding a new one; its ID is not transferred between definitions.
- Removing an eligible participation also removes its associated non-recorded execution rows.
- New participation creates one `PENDING` execution per current segment in its selected definition. Optional initial execution values may be supplied by stable segment ID.

### 3.3 Execution Data

- Execution data belongs to exactly one athlete participation and one stable training segment.
- Each participant has one execution for every segment in their definition.
- Supported statuses are `PENDING`, `RECORDED`, `COMPLETED_WITHOUT_RESULT`, and `NOT_COMPLETED`.
- A measured result is non-negative, uses seconds, and is stored as `NUMERIC(12,3)`. Zero is valid.
- A measured result is required exactly when status is `RECORDED`; it must be null for all other statuses.
- Spike usage is a required boolean independent of status and result.
- Existing execution IDs must be preserved when records remain. Values may be corrected by an authorized user.
- Execution records must continue to refer to the correct participant and segment, even if definitions or segments have been reordered.

### 3.4 Training Status

- A training created directly through UC-502 has status `PLANNED`; acceptance of an import through UC-509 creates a `COMPLETED` training.
- The first successful UC-510 save changes its status to `COMPLETED`, regardless of whether some executions remain `PENDING`.
- A failed save does not change the status.
- A completed training remains editable through UC-503 and UC-510 so authorized users can correct its plan and execution data.

## 4. API Contract

- **Method and path:** `PATCH /api/trainings/{trainingId}/participations`
- **Request:** three explicit collections: `add`, `update`, and `removeParticipationIds`.
- Each `add` item contains `definitionId`, `athleteId`, and an `executionUpdates` array that may be empty. Initial execution values are keyed by stable `segmentId`; new participation/execution IDs are not supplied. Any omitted initial state remains `PENDING` with no result and no spikes.
- Each `update` item contains the existing participation `id` and execution updates containing existing execution `id`, status, nullable measured result, and spike usage. Only the listed executions change.
- `removeParticipationIds` explicitly lists existing participation IDs to remove. A move between definitions is one removal plus one addition.
- Retained IDs must belong to this training and the specified definition/segment. The request does not accept a training group or mutable training-plan fields.
- Sequence numbers are not used to identify participants, segments or executions.
- **Response:** at least the updated training `id`.

The backend validates the complete combined result of all requested operations before persisting any changes. Duplicate athlete assignments across existing and newly added participations are rejected. Omitted participants and executions are unchanged. Additions, updates and removals are applied atomically.

## 5. Validation and Error Scenarios

- Reject unauthenticated users and users without ADMIN or COACH role.
- Reject a missing training; do not reveal an inaccessible training to an unassigned coach.
- Reject nonexistent athletes, new athletes not currently assigned to the training's group, duplicate athletes, invalid definition or segment references, duplicate IDs, and IDs belonging to another training.
- Reject removal or reassignment of a participant with any `RECORDED` execution.
- Reject invalid execution statuses, negative or out-of-range results, missing results for `RECORDED`, and results supplied for another status.
- Reject duplicate/conflicting operations, including updating and removing the same participation.
- Reject execution updates whose IDs do not belong to the specified participation or one of its current segments.
- Validation or persistence errors must not leave partial changes.

## 6. Transactional Consistency

All additions, removals, reassignments and execution updates for one request are atomic. If any validation or persistence step fails, the previous participation and execution state remains unchanged.

## 7. Out of Scope

- Editing training name, date, definitions or segments (UC-503).
- Modifying the group, deleting trainings (UC-505), athlete/group management, imports, templates and statistics.

## 8. Acceptance Criteria

1. ADMIN can complete any training; COACH can complete only a training in a currently assigned group.
2. Authorized users can add, remove and reassign athletes and edit their execution data.
3. New athletes must currently belong to the training's group; existing historical participants remain editable after group changes.
4. An athlete cannot be assigned to more than one definition in the training.
5. Participation and execution IDs are preserved for retained records; stable IDs preserve segment associations through reordering.
6. A participant with a `RECORDED` execution cannot be removed or reassigned.
7. All four statuses are supported; result/status consistency and independent spike usage are enforced.
8. Every requested participant has exactly one execution per current segment.
9. The operation validates the complete state and persists atomically.
10. Failed requests do not change the training status, and unauthorized requests cannot be updated.
