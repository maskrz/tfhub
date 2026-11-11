# UC-502 – Create Training

## 1. Purpose

Provide a backend operation for creating a new training session, including its training definitions, segments, athlete participation and optional execution results.

The operation must enforce role-based access and training group assignment rules.

## 2. Actors

- **ADMIN** – can create a training session for any existing training group.
- **COACH** – can create a training session only for a group currently assigned to that coach.

## 3. Preconditions

- The request is made by an authenticated user.
- The user has the ADMIN or COACH role.
- The specified training group exists and is active.

## 4. Business Rules

### 4.1 Training Session

- A training session must have a non-empty name.
- A training session must have a valid training date.
- Every training session must belong to exactly one training group.
- The training group is required during creation.
- The assigned training group cannot be changed after the training session has been created.
- A training session is created as active.
- Training sessions for different groups are separate records, even if they have the same name, date and training plan.

### 4.2 Training Group Assignment

- ADMIN can create a training session for any existing active training group.
- COACH can create a training session only for a group currently assigned to that coach.
- The backend must validate group access using the authenticated user's current group assignments.
- The operation must not rely on client-supplied user identifiers or coach assignments.

### 4.3 Training Definitions

- A training session must contain at least one training definition.
- Each training definition must have a non-empty name.
- Each training definition must contain at least one training segment.
- Training definitions belong to exactly one training session.
- Training definitions do not have independent training group assignments.
- A training session may contain multiple training definitions representing different training variants.

### 4.4 Training Segments

- Each training segment belongs to exactly one training definition.
- Each segment must have a positive distance.
- Segments must have a defined execution order.
- Segment order must be unique within its training definition.
- Each segment may define a rest period after its execution.
- The rest period after the final segment is optional.
- Segment order must be preserved when the training session is retrieved.

### 4.5 Athlete Participation

- Athlete participation is optional during training creation.
- Each participating athlete must belong to the training session's group at the time of assignment.
- Athletes without an assigned training group cannot participate.
- The backend must validate athlete eligibility using the athlete's current group assignment.
- A training session may contain multiple participating athletes.
- Each athlete may participate in only one training definition within a given training session.
- An athlete may participate in multiple training sessions over time.
- Assigning a group to a training session does not automatically assign its athletes.
- Historical participation remains associated with the training session's group, even if the athlete changes their current group later.

### 4.6 Athlete Execution Data

- Execution data is optional during training creation.
- Execution data is recorded separately for each participating athlete and planned training segment.
- A segment execution may contain a measured result.
- A measured result must be a non-negative numeric value.
- The system must distinguish between:
  - **PENDING** – no execution information has been provided.
  - **RECORDED** – a measured result has been recorded.
  - **COMPLETED_WITHOUT_RESULT** – the athlete completed the segment, but no measured result is available.
  - **NOT_COMPLETED** – the athlete did not complete the segment.
- A measured result may only be provided for an execution with status RECORDED.
- Execution status and spike usage are independent attributes.
- Spike usage must be recorded separately for each segment execution.
- If no execution information is supplied for a segment, it must remain PENDING.
- The backend must not interpret missing execution data as a completed or uncompleted segment.

### 4.7 Initial Training State

- A newly created training session is active.
- All supplied training definitions, segments, athlete participations and execution data belong to the newly created training session.
- The training session must not be partially created if any part of the submitted data is invalid.

## 5. Authorization Rules

- Only authenticated ADMIN and COACH users may create training sessions.
- ADMIN may create training sessions for any existing active group.
- COACH may create training sessions only for groups currently assigned to that coach.
- COACH cannot assign athletes outside the selected training group.
- The backend must derive the requesting user's identity and permissions from the authenticated security context.
- The backend must independently validate all group and athlete assignments.

## 6. Validation and Error Scenarios

- **Unauthenticated request:** access is rejected.
- **User without ADMIN or COACH role:** access is rejected.
- **Missing training group:** creation is rejected.
- **Non-existing or inactive training group:** creation is rejected.
- **COACH selects a group not assigned to them:** creation is rejected.
- **Missing or blank training name:** creation is rejected.
- **Missing or invalid training date:** creation is rejected.
- **No training definitions supplied:** creation is rejected.
- **Training definition with blank name:** creation is rejected.
- **Training definition without segments:** creation is rejected.
- **Segment with invalid or non-positive distance:** creation is rejected.
- **Duplicate or invalid segment order:** creation is rejected.
- **Athlete does not exist:** creation is rejected.
- **Athlete does not belong to the selected training group:** creation is rejected.
- **Athlete without an assigned group:** creation is rejected.
- **Athlete assigned to more than one definition within the same training session:** creation is rejected.
- **Invalid execution status or measured result:** creation is rejected.
- **Measured result supplied for a status other than RECORDED:** creation is rejected.
- **Invalid spike usage value:** creation is rejected.
- **Unexpected server error:** the system reports an appropriate error without exposing internal implementation details.

Validation failures must not leave partially persisted training data.

## 7. Output

The operation returns information identifying the newly created training session.

The response must include at least the training identifier.

The exact response structure and field types will be defined during API design.

## 8. Transactional Consistency

- Creation of the training session and all its related data must be atomic.
- Training definitions, segments, athlete participations and execution data must be persisted in a single transaction.
- If any validation or persistence step fails, the entire operation must be rolled back.
- No partially created training session or related records may remain after failure.

## 9. Out of Scope

- Editing existing training sessions.
- Changing the training group assigned to an existing training session.
- Deactivating training sessions.
- Creating or modifying training groups.
- Managing coach-to-group assignments.
- Managing athlete records or changing athlete group assignments.
- Creating training sessions from OCR imports.
- Training templates and recurring training plans.
- Training statistics and athlete training history.

## 10. Acceptance Criteria

1. ADMIN can create a training session for any existing active training group.
2. COACH can create a training session only for a group currently assigned to that coach.
3. Every created training session belongs to exactly one group.
4. The training group is required and cannot be omitted.
5. A training session contains at least one training definition.
6. Every training definition contains at least one ordered training segment.
7. Every segment has a positive distance and a valid execution order.
8. Athlete participation is optional during creation.
9. Only athletes currently belonging to the selected training group can be assigned.
10. An athlete can participate in only one training definition within the same training session.
11. Execution data is optional and recorded separately for each athlete and segment.
12. The system distinguishes PENDING, RECORDED, COMPLETED_WITHOUT_RESULT and NOT_COMPLETED execution states.
13. Measured results are non-negative numeric values and can only be supplied with RECORDED status.
14. Spike usage is recorded independently for each segment execution.
15. A newly created training session is active.
16. All training data is persisted atomically.
17. Validation or persistence failure leaves no partially created training data.
18. The operation returns the identifier of the newly created training session.
19. Unauthorized users cannot create training sessions.