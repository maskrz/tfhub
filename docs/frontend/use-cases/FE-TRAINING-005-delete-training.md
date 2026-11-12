# FE-TRAINING-005 – Delete Training

## 1. Purpose

Allow authorized users to permanently delete a training session and all its related plan, participation, and execution data.

Deletion cannot be undone through the application and requires explicit confirmation.

## 2. Actors and Preconditions

- **ADMIN** – can delete any accessible training.
- **COACH** – can delete a training only while currently assigned to its group.
- The user is authenticated.
- Backend UC-505 is available.
- The training is accessible through the training list or details view.

## 3. Main Flow

1. The user selects **Usuń** for a training in the list or **Usuń trening** in its details.
2. The frontend displays a confirmation dialog naming the training and warning that its plan, participants, and saved results will be permanently deleted and cannot be restored.
3. The user explicitly confirms deletion.
4. The frontend calls `DELETE /api/trainings/{trainingId}`.
5. While the request is in progress, confirmation actions are disabled.
6. On success, the frontend removes the training from the list or navigates from details back to the training list.

## 4. Display and Interaction Requirements

- Offer deletion for planned and completed training sessions.
- Require explicit confirmation; canceling or closing the dialog must not call the backend.
- Clearly distinguish **Anuluj** and **Usuń trening** actions.
- Prevent repeated submission while the request is in progress.
- Do not remove the training from the UI until the backend confirms success.
- On failure, keep the training visible and the confirmation dialog open, show a Polish error, and allow retry.
- The confirmation dialog and actions must remain usable on mobile.

## 5. Authorization and Error Handling

- Backend authorization remains authoritative.
- Handle authentication, authorization, not-found, and unexpected backend errors using existing application patterns.
- Do not display success or change the list before the backend confirms deletion.

## 6. Out of Scope

- Restoring deleted training data.
- Deleting athletes, groups, accounts, or unrelated records.
- Editing or completing a training.
- OCR and training imports.

## 7. Acceptance Criteria

1. Authorized users can start deleting a training from the list and details views.
2. Planned and completed trainings can be deleted.
3. The confirmation clearly warns that the plan, participants, and saved results will be permanently deleted.
4. Canceling or closing confirmation leaves data unchanged and sends no request.
5. Confirming calls UC-505 and prevents repeated submissions while pending.
6. Success removes the training from the list or returns from details to the list.
7. Failure keeps the training visible and displays an error without closing the dialog.
8. The interaction follows existing TFHUB visual and responsive patterns.
