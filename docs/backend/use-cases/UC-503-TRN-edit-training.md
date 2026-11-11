# UC-503 – Edit Training

## 1. Purpose

Provide a backend operation for editing an existing training session, including its training definitions, segments, athlete participation and execution data.

The operation must enforce role-based access and preserve the consistency of historical training data.

## 2. Actors

- **ADMIN** – can edit any active training session.
- **COACH** – can edit active training sessions belonging to groups currently assigned to that coach.

## 3. Preconditions

- The request is made by an authenticated user.
- The user has the ADMIN or COACH role.
- The training session exists and is active.
- The requesting user is authorized to access and edit the training session.

## 4. Business Rules

### 4.1 Training Session

- The training name may be changed.
- The training date may be changed.
- The training session must retain a non-empty name and a valid date.
- The training session must remain assigned to its original training group.
- The training group cannot be changed after creation.
- An active training session may be edited by ADMIN or an authorized COACH.
- An inactive training session cannot be edited.
- Editing a training session must preserve its identity and existing historical relationships.

### 4.2 Training Group and Authorization

- ADMIN can edit any active training session.
- COACH can edit an active training session only if its group is currently assigned to that coach.
- All coaches assigned to the training's group have identical editing permissions.
- Editing permissions do not depend on which coach created the training session.
- The backend must validate current coach-to-group assignments.
- A coach who is no longer assigned to the training's group cannot edit it, even if they created it.
- The training group assignment is immutable and cannot be modified through this operation.

### 4.3 Training Definitions

- Training definitions may be added, modified or removed.
- Each training definition must have a non-empty name.
- Every training definition must contain at least one segment.
- A training definition cannot be removed if any athlete has execution data recorded for any of its segments.
- A training definition without recorded execution data may be removed, including its associated athlete participations and pending execution records.
- Removing a definition must not remove or modify execution data belonging to other definitions.
- Training definition order must be deterministic when retrieved.

### 4.4 Training Segments

- Segments may be added, modified, removed or reordered.
- Each segment must have a positive distance.
- Segment order must be unique within its training definition.
- Each segment may define a rest period after its execution.
- The rest period after the final segment is optional.
- A segment may be removed only if no athlete has recorded execution data for that segment.
- A segment with recorded execution data cannot be removed.
- If any recorded execution data exists for a segment, its distance cannot be changed.
- Other segment properties, including rest period and sequence order, may be modified even if execution data exists.
- Removing a segment without recorded execution data must also remove its associated pending or otherwise unrecorded execution entries.
- Segment ordering must remain valid after modifications.

### 4.5 Athlete Participation

- Athletes may be added to or removed from training definitions.
- An athlete must belong to the training session's group at the time of initial assignment.
- Athletes without an assigned training group cannot be added.
- The backend must validate athlete eligibility using the athlete's current group assignment when adding a new participation.
- An athlete may participate in only one training definition within a given training session.
- An athlete may participate in multiple training sessions over time.
- Adding a group to a training session does not automatically assign its athletes.
- An athlete may be removed from a training session only if no execution data has been recorded for any of their segments.
- If an athlete has recorded execution data, their participation cannot be removed.
- Existing participation remains associated with the training session's group, regardless of later changes to the athlete's current group.
- A change to an athlete's current group must not invalidate or modify existing historical participation.
- Existing participation must not be revalidated against the athlete's current group when editing unrelated training data.

### 4.6 Athlete Execution Data

- Execution data may be added or modified for existing athlete participations.
- Execution data is maintained separately for each athlete and planned segment.
- A measured result must be a non-negative numeric value.
- The system must distinguish between:
  - **PENDING** – no execution information has been provided.
  - **RECORDED** – a measured result has been recorded.
  - **COMPLETED_WITHOUT_RESULT** – the athlete completed the segment, but no measured result is available.
  - **NOT_COMPLETED** – the athlete did not complete the segment.
- A measured result may only be provided for an execution with status RECORDED.
- Spike usage must be recorded separately for each segment execution.
- Execution status and spike usage are independent attributes.
- Existing recorded execution data may be corrected or updated by an authorized user.
- Changing an execution status away from RECORDED must not retain a measured result.
- Changing an execution status to RECORDED requires a valid measured result.
- Changing spike usage must not modify the measured result or execution status.
- The backend must preserve the relationship between execution data, its athlete participation and its planned segment.

### 4.7 Historical Data Integrity

- The training session's group is immutable.
- Existing athlete participations remain part of the historical training session even if the athletes later change groups.
- Historical execution data must not be removed or modified as a side effect of unrelated changes.
- Removing a segment, definition or athlete participation must follow the restrictions defined in sections 4.3, 4.4 and 4.5.
- The operation must not silently discard recorded execution data.

## 5. Authorization Rules

- Only authenticated ADMIN and COACH users may edit training sessions.
- ADMIN can edit any active training session.
- COACH can edit active training sessions belonging to groups currently assigned to that coach.
- All coaches assigned to the training's group have identical editing permissions.
- Authorization must be evaluated using the authenticated security context and current coach-to-group assignments.
- The backend must not rely on client-supplied user identifiers, roles or group assignments.
- The backend must independently validate all new athlete assignments.

## 6. Validation and Error Scenarios

- **Unauthenticated request:** access is rejected.
- **User without ADMIN or COACH role:** access is rejected.
- **Training session does not exist:** access is rejected.
- **Training session is inactive:** editing is rejected.
- **COACH is not currently assigned to the training's group:** access is rejected.
- **Attempt to change the training group:** modification is rejected.
- **Missing or blank training name:** modification is rejected.
- **Missing or invalid training date:** modification is rejected.
- **Attempt to leave the training without any definitions:** modification is rejected.
- **Training definition with blank name:** modification is rejected.
- **Training definition without segments:** modification is rejected.
- **Invalid or non-positive segment distance:** modification is rejected.
- **Duplicate or invalid segment order:** modification is rejected.
- **Attempt to remove a definition with recorded execution data:** modification is rejected.
- **Attempt to remove a segment with recorded execution data:** modification is rejected.
- **Attempt to change the distance of a segment with recorded execution data:** modification is rejected.
- **Attempt to remove an athlete with recorded execution data:** modification is rejected.
- **Attempt to assign an athlete who does not belong to the training's group:** modification is rejected.
- **Attempt to assign an athlete without a training group:** modification is rejected.
- **Attempt to assign an athlete to multiple definitions within the same training session:** modification is rejected.
- **Invalid execution status or measured result:** modification is rejected.
- **Measured result supplied for a status other than RECORDED:** modification is rejected.
- **RECORDED status without a valid measured result:** modification is rejected.
- **Unexpected server error:** the system reports an appropriate error without exposing internal implementation details.

Validation failures must not leave partially modified training data.

## 7. Output

The operation returns information identifying the updated training session.

The response must include at least the training identifier.

The exact response structure and field types will be defined during API design.

## 8. Transactional Consistency

- All modifications within one operation must be applied atomically.
- Training session attributes, definitions, segments, athlete participations and execution data must remain consistent.
- If any validation or persistence step fails, the entire operation must be rolled back.
- No partially modified training session or related records may remain after failure.
- The operation does not require optimistic locking or explicit concurrent modification detection in this release.

## 9. Out of Scope

- Changing the training group assigned to a training session.
- Editing inactive training sessions.
- Reactivating inactive training sessions.
- Deactivating training sessions.
- Creating or modifying training groups.
- Managing coach-to-group assignments.
- Managing athlete records or changing athlete group assignments.
- Creating training sessions from OCR imports.
- Training templates and recurring training plans.
- Training statistics and athlete training history.

## 10. Acceptance Criteria

1. ADMIN can edit any active training session.
2. COACH can edit an active training session only if currently assigned to its group.
3. All coaches assigned to the training's group have identical editing permissions.
4. The training name and date can be modified.
5. The training group cannot be changed.
6. Training definitions can be added, modified and removed subject to historical data restrictions.
7. Segments can be added, modified, removed and reordered subject to historical data restrictions.
8. A segment with recorded execution data cannot be removed.
9. The distance of a segment with recorded execution data cannot be changed.
10. Rest period and sequence order can be modified even when execution data exists.
11. Athletes can be added only if they currently belong to the training's group.
12. Existing athlete participation remains valid after the athlete changes their current group.
13. An athlete with recorded execution data cannot be removed from the training.
14. An athlete cannot participate in multiple definitions within the same training session.
15. Execution data can be added and corrected by authorized users.
16. The system distinguishes PENDING, RECORDED, COMPLETED_WITHOUT_RESULT and NOT_COMPLETED execution states.
17. Measured results are non-negative numeric values and can only be supplied with RECORDED status.
18. Spike usage is maintained independently for each segment execution.
19. Removing a definition, segment or athlete participation never silently deletes recorded execution data.
20. Inactive training sessions cannot be edited.
21. All modifications are persisted atomically.
22. Validation or persistence failure leaves no partially modified training data.
23. The operation returns the identifier of the updated training session.
24. Unauthorized users cannot modify training sessions.