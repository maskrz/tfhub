# UC-504 – View Training

## 1. Purpose

Provide a backend operation for retrieving complete details of an existing training session, including its training definitions, segments, athlete participation and execution data.

The operation must enforce role-based access and training group visibility rules.

## 2. Actors

- **ADMIN** – can retrieve details of any training session.
- **COACH** – can retrieve details of training sessions belonging to groups currently assigned to that coach.

## 3. Preconditions

- The request is made by an authenticated user.
- The user has the ADMIN or COACH role.
- The training session exists.

## 4. Business Rules

### 4.1 Training Visibility – ADMIN

- ADMIN can retrieve any existing training session.
- Visibility does not depend on the training group's coach assignments.
- ADMIN can retrieve both planned and completed training sessions.

### 4.2 Training Visibility – COACH

- COACH can retrieve a training session only if its group is currently assigned to that coach.
- A training session belongs to exactly one training group.
- All coaches assigned to the training's group have identical visibility.
- Visibility does not depend on which coach created the training session.
- COACH can retrieve both planned and completed training sessions belonging to their assigned groups.
- A coach who is no longer assigned to the training's group cannot retrieve its details.

### 4.3 Training Information

The operation must return the complete structure of the training session, including:

- Training identifier, name, date and status.
- Assigned training group.
- All training definitions.
- All segments belonging to each definition.
- All athlete participations.
- Execution data for participating athletes and their segments.

The response must represent the persisted state of the training session.

### 4.4 Athlete Participation and Historical Group

- Athlete participation belongs to the training session, not to the athlete's current group.
- The training session's assigned group represents the historical group context for all its participants.
- A later change to an athlete's current group does not modify historical participation or execution data.
- Current athlete group assignments must not be used to filter historical training details.
- Authorized coaches can view all participants and execution data belonging to the training session, including athletes who have since moved to another group.

### 4.5 Execution Data Visibility

- All execution records belonging to the training session must be included.
- The operation must distinguish between:
  - **PENDING** – no execution information has been provided.
  - **RECORDED** – a measured result has been recorded.
  - **COMPLETED_WITHOUT_RESULT** – the athlete completed the segment, but no measured result is available.
  - **NOT_COMPLETED** – the athlete did not complete the segment.
- Spike usage must be returned independently of execution status.
- A missing measured result must not be interpreted as a zero value.
- Participants without any recorded execution results must still be included if they have been assigned to the training session.

### 4.6 Empty Training Sessions

- A training session may have no participating athletes.
- A training session may have participants without any recorded execution data.
- The operation must return the training session and its complete planned structure even when no execution data exists.
- Empty collections must be returned where applicable rather than treating missing related records as an error.

## 5. Authorization Rules

- Only authenticated ADMIN and COACH users may retrieve training details.
- ADMIN can retrieve any existing training session.
- COACH can retrieve a training session only if currently assigned to its group.
- Authorization must be evaluated using the authenticated security context and current coach-to-group assignments.
- The backend must not rely on client-supplied user identifiers, roles or group assignments.
- The backend must not filter historical participants using their current group assignments.

## 6. Validation and Error Scenarios

- **Unauthenticated request:** access is rejected.
- **User without ADMIN or COACH role:** access is rejected.
- **Training session does not exist:** the operation returns an appropriate not-found response.
- **Training session exists but COACH is not assigned to its group:** the operation returns the same not-found response as for a nonexistent training session.
- **Unexpected server error:** the system reports an appropriate error without exposing internal implementation details.

The backend must not reveal whether an inaccessible training session exists.

## 7. Output

The operation returns the complete details of the requested training session.

The response must include at least:

- Training identifier.
- Training name.
- Training date.
- Training status.
- Assigned training group identifier and name.
- Training definitions.
- Ordered training segments, including their distances and rest periods.
- Participating athletes.
- Athlete identifiers and identifying information required to distinguish participants.
- Execution data for each participant and segment, including status, measured result where available and spike usage.

The exact response structure and field types will be defined during API design.

## 8. Ordering Requirements

- Training definitions must have deterministic ordering.
- Segments within each definition must be ordered by their sequence number.
- Participating athletes must be sorted alphabetically by last name and then first name.
- Execution data must be associated with the corresponding athlete and segment.
- Ordering must be applied consistently by the backend.

## 9. Consistency Requirements

- Training visibility rules must be consistent with UC-501 – List Trainings, UC-503 – Edit Training Plan, and UC-510 – Complete Training.
- A planned training session visible in the standard list must also be accessible through this operation to the same authorized user.
- Completed training sessions are excluded from the default list but remain accessible through this operation.
- Historical participation and execution data must remain visible after an athlete changes their current group.
- The returned training group must represent the immutable group assigned to the training session.
- The operation must not modify any persisted training data.

## 10. Out of Scope

- Creating, editing or deleting training sessions (UC-505).
- Reviewing or accepting OCR imports.
- Training statistics and athlete training history.
- Searching or filtering within a training session.
- Calendar views and future training planning.
- Editing athlete records or changing athlete group assignments.

## 11. Acceptance Criteria

1. ADMIN can retrieve details of any existing training session.
2. COACH can retrieve details only of training sessions belonging to groups currently assigned to that coach.
3. All coaches assigned to the same group have identical visibility.
4. Both planned and completed training sessions can be retrieved by authorized users.
5. An inaccessible training session returns the same not-found response as a nonexistent training session.
6. The response includes the complete training structure, including definitions and segments.
7. All assigned participants are included, even if they have no recorded execution results.
8. All execution data is returned with its status, measured result where available and spike usage.
9. Historical participants remain visible after changing their current group.
10. Historical execution data is not filtered using current athlete group assignments.
11. Training definitions and segments have deterministic ordering.
12. Participants are sorted by last name and then first name.
13. Empty participant and execution collections are returned normally.
14. The operation does not modify persisted training data.
15. Unauthorized users cannot retrieve training details.