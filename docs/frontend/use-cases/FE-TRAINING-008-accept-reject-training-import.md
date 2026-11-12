# FE-TRAINING-008 – Accept or Reject Training Import

## 1. Purpose

Allow an authorized user to explicitly accept the complete reviewed draft into one persisted training or reject it without creating a training.

## 2. Actors and Preconditions

- **ADMIN** – may accept or reject imports for any group.
- **COACH** – may accept or reject imports only while currently assigned to the import's group.
- The user is authenticated and backend UC-509 is available.
- The import is in `REVIEW_REQUIRED` state.

## 3. Main Flow

The current review screen supports acceptance of a saved valid draft. The rejection UI remains unimplemented.

1. The user reviews the current saved draft in FE-TRAINING-007.
2. The frontend shows **Zaakceptuj trening** only when the backend reports the draft ready for acceptance; **Odrzuć import** remains available.
3. Each action requires explicit confirmation using the existing TFHUB dialog pattern. Acceptance confirms creation of the complete training; rejection confirms that the import will be closed without creating a training.
4. On confirmation, the frontend calls `POST /api/training-imports/{importId}/accept` or `POST /api/training-imports/{importId}/reject`.
5. While the request is pending, disable both finalization actions.
6. On acceptance success, navigate to the created training details using the returned training ID.
7. On rejection success, show the rejected state and provide navigation back to the training list.

## 4. Confirmation and Data Integrity

- Acceptance and rejection are separate, explicit whole-import operations; never automatically finalize based on OCR confidence or draft validity.
- Acceptance uses the latest saved draft. The backend revalidates it and atomically creates the training with status `PLANNED`, definitions, segments, matched participations and execution data.
- Skipped athlete rows do not create persisted participation or execution records.
- Rejection does not create a training.
- The optimized JPEG, current draft and processing attempts remain retained after either outcome. The UI must not imply that individual draft-edit history is stored.
- The group remains the immutable group selected at upload.

## 5. Loading, Success, and Error Handling

- Do not change the displayed import state or navigate as though finalization succeeded until the backend confirms success.
- Prevent repeated acceptance/rejection while a request is in progress.
- On backend error, keep the user on the current draft and show a Polish error; allow retry when the import remains eligible.
- If the import is already accepted/rejected or no longer accessible, show the backend error and do not claim a new training was created.
- All user-facing text must be in Polish.
- Keep confirmations and actions accessible and usable on mobile.

## 6. Out of Scope

- Uploading, processing or reviewing the import (FE-TRAINING-006 and FE-TRAINING-007).
- Reopening or editing accepted/rejected imports.
- Deleting source images or persisted trainings.
- Any backend changes.

## 7. Acceptance Criteria

1. Only authorized users can access finalization; backend authorization remains authoritative.
2. Acceptance requires a saved draft that the backend reports ready.
3. Acceptance and rejection each require explicit confirmation; canceling sends no request.
4. Acceptance calls UC-509 once, disables repeat submission, and on success opens the created `PLANNED` training.
5. Rejection calls UC-509 once, disables repeat submission, and creates no training.
6. Backend errors do not change the UI to a success/finalized state.
7. The import's optimized JPEG and current draft remain retained after finalization.
8. The UI follows existing TFHUB dialog, notification, Polish-language and responsive conventions.