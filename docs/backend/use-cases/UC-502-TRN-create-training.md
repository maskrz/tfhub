# UC-502 – Create Training

## 1. Purpose

Provide a backend operation for creating a new training session of type `TEMPO` or `FREE_TEXT`. Both types share name, date, assignment type and lifecycle status; tempo plans and free-text sections are type-specific. Athlete participation and execution data are managed separately through UC-510 – Complete Training.

The operation must enforce role-based access and training group assignment rules.

## 2. Actors

- **ADMIN** – can create a training session for any existing training group.
- **COACH** – can create a training session only for a group currently assigned to that coach.

## 3. Preconditions

- The request is made by an authenticated user.
- The user has the ADMIN or COACH role.
- The specified training group exists.

## 4. Business Rules

### 4.1 Training Session

- A training session must have a non-empty name.
- A training date is optional; when supplied it must be valid.
- TEMPO sessions must belong to exactly one training group.
- FREE_TEXT sessions use exactly one of two assignment modes: one group (`GROUP`) or one or more individual athletes (`ATHLETES`).
- The selected assignment mode is required during creation.
- FREE_TEXT assignment may be changed later through its edit operation; TEMPO group assignment remains unchanged.
- A training session is created with status `PLANNED`.
- Training sessions for different groups are separate records, even if they have the same name, date and training plan.

### 4.2 Training Group Assignment

- ADMIN can create a training session for any existing training group.
- COACH can create a training session only for a group currently assigned to that coach.
- The backend must validate group access using the authenticated user's current group assignments.
- The operation must not rely on client-supplied user identifiers or coach assignments.
- For FREE_TEXT/ATHLETES, ADMIN may select any active athletes. COACH may select active athletes only when currently assigned to every selected athlete's current group.
- Athlete selection is explicit; group membership does not add other athletes to an ATHLETES-mode session.

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

### 4.5 Initial Training State

- A newly created training session has status `PLANNED`.
- A `TEMPO` creation does not assign athletes or accept execution data. The newly created training has no athlete participations until UC-510 or an accepted import creates them.
- All supplied definitions and segments belong to the newly created training session.
- The training session must not be partially created if any part of the submitted data is invalid.

### 4.6 FREE_TEXT Training

- A FREE_TEXT training has a non-empty name, optional date, and at least one ordered section.
- In `GROUP` mode it has exactly one group and no athlete targets. In `ATHLETES` mode it has one or more active athlete targets and no group.
- Each section has a user-editable non-empty title and content and a unique positive sequence number.
- FREE_TEXT creation does not create definitions, athlete participations or execution data.
- The training remains `PLANNED` until an authorized coach or administrator marks it completed.

## 5. Authorization Rules

- Only authenticated ADMIN and COACH users may create training sessions.
- ADMIN may create TEMPO or GROUP-mode FREE_TEXT sessions for any existing active group, and ATHLETES-mode FREE_TEXT sessions for any active athletes.
- COACH may create group-assigned sessions only for groups currently assigned to that coach. For ATHLETES-mode FREE_TEXT, the coach must currently be assigned to every selected athlete's group.
- The backend must derive the requesting user's identity and permissions from the authenticated security context.
- The backend must independently validate the selected group or athletes and enforce mutually exclusive assignment fields.

## 6. Validation and Error Scenarios

- **Unauthenticated request:** access is rejected.
- **User without ADMIN or COACH role:** access is rejected.
- **Missing or non-existing assignment target:** creation is rejected.
- **COACH selects a group not assigned to them:** creation is rejected.
- **ATHLETES mode has no targets, includes inactive/non-existing athletes, or includes duplicate IDs:** creation is rejected.
- **COACH selects an athlete whose current group is not assigned to them:** creation is rejected.
- **Missing or blank training name:** creation is rejected.
- **Invalid training date:** creation is rejected.
- **No training definitions supplied:** creation is rejected.
- **FREE_TEXT training without sections or with blank section title/content:** creation is rejected.
- **Training definition with blank name:** creation is rejected.
- **Training definition without segments:** creation is rejected.
- **Segment with invalid or non-positive distance:** creation is rejected.
- **Duplicate or invalid segment order:** creation is rejected.
- **Unexpected server error:** the system reports an appropriate error without exposing internal implementation details.

Validation failures must not leave partially persisted training data.

## 7. Output

The operation returns information identifying the newly created training session.

The response must include at least the training identifier.

- TEMPO uses `POST /api/trainings`.
- FREE_TEXT uses `POST /api/trainings/free-text`.
- The FREE_TEXT request includes `assignmentType` and either `trainingGroupId` (`GROUP`) or `athleteIds` (`ATHLETES`), never both.
- Both return `201 Created` with the created training identifier.

## 8. Transactional Consistency

- Creation of the training session and its plan must be atomic.
- The training session, definitions and segments must be persisted in a single transaction.
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

1. ADMIN can create a training session for any existing training group.
2. COACH can create a training session only for a group currently assigned to that coach.
3. Every TEMPO and GROUP-mode FREE_TEXT session belongs to exactly one group.
4. ATHLETES-mode FREE_TEXT has one or more explicit athlete targets and no group.
5. A training session contains at least one training definition.
6. Every training definition contains at least one ordered training segment.
7. Every segment has a positive distance and a valid execution order.
8. Training creation does not assign athletes or accept execution data.
9. A newly created training session has status `PLANNED` and initially has no athlete participations.
10. The plan is persisted atomically.
11. Validation or persistence failure leaves no partially created training data.
12. The operation returns the identifier of the newly created training session.
13. Unauthorized users cannot create training sessions.
14. FREE_TEXT creation persists ordered sections, remains `PLANNED`, and creates no athlete participation or tempo data.
15. GROUP-mode visibility follows current group membership; ATHLETES-mode visibility is limited to the explicit active targets.