# FE-TRAINING-005 – Deactivate Training

## 1. Purpose

Allow authorized users to deactivate an active training session without deleting its data.

Deactivation removes the training from standard active training lists while preserving its complete history and allowing authorized users to view its details.

## 2. Actors and Preconditions

- **ADMIN** – can deactivate any active training.
- **COACH** – can deactivate an active training only while currently assigned to its group.
- The user is authenticated.
- Backend UC-505 is available.
- The training is currently active.

## 3. Main Flow

1. The user opens the available actions for an active training.
2. The user selects **Dezaktywuj trening**.
3. The frontend displays a confirmation dialog explaining that:
    - the training will disappear from the active training list;
    - its definitions, segments, participants and execution data will be preserved;
    - the operation cannot be undone through the application.
4. The user confirms the operation.
5. The frontend calls the backend UC-505 endpoint.
6. On success:
    - the training is marked as inactive;
    - the user is returned to the training list or the current view is refreshed;
    - the training is no longer presented as active.

## 4. Display and Interaction Requirements

- Offer deactivation only for active trainings.
- Use the existing TFHUB confirmation dialog/popup pattern.
- Clearly distinguish **Anuluj** and **Dezaktywuj** actions.
- Prevent repeated submission while the request is in progress.
- After successful deactivation, update the UI consistently with the existing list and detail views.
- Do not physically delete any training data.
- Inactive trainings remain accessible through the authorized details view (FE-TRAINING-004), but cannot be edited or deactivated again.
- Do not provide a reactivation action.

## 5. Authorization and Error Handling

- Backend authorization and active-state validation remain authoritative.
- Handle authorization errors and other backend errors using existing application patterns.
- If the operation fails, keep the training active in the UI and display an appropriate Polish error message.
- Do not display success before the backend confirms the operation.

## 6. Out of Scope

- Physical deletion of training data.
- Reactivation of inactive trainings.
- Editing training details or execution data.
- OCR and training imports.

## 7. Acceptance Criteria

1. Authorized users can deactivate active trainings.
2. The user must explicitly confirm the operation.
3. Successful deactivation updates the UI and removes the training from active lists.
4. All training data is preserved.
5. Inactive trainings remain viewable by authorized users.
6. Inactive trainings cannot be deactivated again or reactivated.
7. Failed requests do not incorrectly change the UI state.
8. The interaction follows existing TFHUB UI patterns and works on mobile.