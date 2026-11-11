# UC-505 – Deactivate Training

## 1. Purpose

Provide a backend operation for deactivating an existing training session.

Deactivation must preserve the training session and all its related historical data while excluding it from the standard training list.

## 2. Actors

- **ADMIN** – can deactivate any active training session.
- **COACH** – can deactivate active training sessions belonging to groups currently assigned to that coach.

## 3. Preconditions

- The request is made by an authenticated user.
- The user has the ADMIN or COACH role.
- The training session exists and is active.
- The requesting user is authorized to deactivate the training session.

## 4. Business Rules

### 4.1 Deactivation

- Deactivation changes the training session status from active to inactive.
- Deactivation must not physically delete the training session.
- Deactivation must preserve all related data, including:
  - Assigned training group.
  - Training definitions.
  - Training segments.
  - Athlete participations.
  - Athlete execution data.
  - Measured results.
  - Spike usage information.
- Deactivation is allowed regardless of whether the training session has participants or recorded execution data.
- An inactive training session cannot be deactivated again.
- Reactivation is not supported in this use case.

### 4.2 Visibility After Deactivation

- Inactive training sessions are excluded from the standard training list (UC-501).
- Inactive training sessions remain accessible through the training details operation (UC-504), subject to its authorization rules.
- ADMIN can retrieve inactive training sessions regardless of group assignments.
- COACH can retrieve inactive training sessions only while currently assigned to their group.
- Deactivation does not change the training group's assignment or any historical participant relationships.

### 4.3 Editing After Deactivation

- An inactive training session cannot be edited through UC-503.
- An inactive training session cannot have its athlete participations or execution data changed through UC-510.
- Its definitions, segments, participants and execution data remain unchanged.
- Historical details remain available through UC-504.

## 5. Authorization Rules

- Only authenticated ADMIN and COACH users may deactivate training sessions.
- ADMIN can deactivate any active training session.
- COACH can deactivate an active training session only if its group is currently assigned to that coach.
- All coaches assigned to the training's group have identical deactivation permissions.
- Authorization does not depend on which coach created the training session.
- The backend must derive the requesting user's identity and permissions from the authenticated security context.
- The backend must not rely on client-supplied user identifiers, roles or group assignments.

## 6. Validation and Error Scenarios

- **Unauthenticated request:** access is rejected.
- **User without ADMIN or COACH role:** access is rejected.
- **Training session does not exist:** the operation returns an appropriate not-found response.
- **Training session exists but COACH is not assigned to its group:** the operation returns the same not-found response as for a nonexistent training session.
- **Training session is already inactive:** deactivation is rejected.
- **Unexpected server error:** the system reports an appropriate error without exposing internal implementation details.

The backend must not reveal whether an inaccessible training session exists.

Deactivation must not be rejected solely because the training session contains participants or recorded execution data.

## 7. Output

The operation confirms successful deactivation of the training session.

The response must identify the affected training session.

The exact response structure and field types will be defined during API design.

## 8. Transactional Consistency

- Deactivation must be performed atomically.
- The training session status must be changed only if the operation succeeds.
- Related historical data must remain intact.
- A failed operation must not leave the training session in a partially modified state.

## 9. Out of Scope

- Physically deleting training sessions.
- Reactivating inactive training sessions.
- Editing inactive training sessions.
- Modifying training definitions, segments, participants or execution data.
- Changing training group assignments.
- Managing training groups or coach-to-group assignments.
- Deleting or modifying OCR import records.

## 10. Acceptance Criteria

1. ADMIN can deactivate any active training session.
2. COACH can deactivate an active training session only if currently assigned to its group.
3. All coaches assigned to the same group have identical deactivation permissions.
4. Deactivation changes the training session status to inactive.
5. Deactivation is allowed even when participants or recorded execution data exist.
6. No related historical data is physically deleted or modified.
7. Inactive training sessions are excluded from UC-501.
8. Authorized users can still retrieve inactive training session details through UC-504.
9. Inactive training sessions cannot be edited through UC-503 or completed through UC-510.
10. An already inactive training session cannot be deactivated again.
11. Inaccessible and nonexistent training sessions produce the same not-found response.
12. Deactivation is performed atomically.
13. Unauthorized users cannot deactivate training sessions.