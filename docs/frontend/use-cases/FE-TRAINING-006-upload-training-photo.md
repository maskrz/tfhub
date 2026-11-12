# FE-TRAINING-006 – Upload Training Photo

## 1. Purpose

Allow an authorized user to upload one training-sheet photo and start an asynchronous training import. The upload must not create persisted training data.

## 2. Actors and Preconditions

- **ADMIN** – may upload for any existing training group.
- **COACH** – may upload for groups currently assigned to that coach.
- The user is authenticated and backend UC-506 is available.
- The user can access at least one existing training group.

## 3. Main Flow

1. The user selects **Importuj ze zdjęcia** from the training list.
2. The frontend loads available groups using the existing accessible-groups API.
3. The user selects one group and one training-sheet image.
4. The frontend submits `trainingGroupId` and the image as multipart form data to `POST /api/training-imports`.
5. On success, the modal closes and the training list refreshes active import rows, showing a duplicate warning when indicated.
6. The training list polls `GET /api/training-imports` every five seconds while an import is `PENDING`, `PROCESSING`, or `FAILED` with a scheduled automatic retry.
7. A `REVIEW_REQUIRED` row links to draft review. A `FAILED` row displays the safe processing error and offers retry on the training list.

## 4. Group and Image Selection

- Require exactly one existing group and one image.
- ADMIN may select any existing group; COACH may select only currently assigned groups.
- Display the selected group in the resulting import as read-only; it cannot be changed later.
- Allow the user to select a supported image from the device. Client-side checks are for usability only; the backend validates actual content, size and dimensions.
- The backend creates and retains only the optimized JPEG. The frontend must not assume the uploaded bytes are retained.
- A possible-duplicate indication is a warning, not a blocking error; each upload remains a separate import.

## 5. Processing Status and Retry

Display understandable Polish labels for `PENDING`, `PROCESSING`, `REVIEW_REQUIRED` and `FAILED`. Refresh the import list while processing is active or an automatic retry is scheduled, stop polling when the page is left or no import requires it, and do not use WebSockets.

For `FAILED`, display the safe backend failure message and a **Ponów przetwarzanie** action in its training-list row. Disable repeated retry while the request is pending. Call `POST /api/training-imports/{importId}/retry`; on success refresh the list and resume polling. Retry uses the same retained optimized JPEG and must not create another import.

The frontend must not display raw model responses or internal storage paths. Acceptance is always a separate explicit action in FE-TRAINING-008.

## 6. UI, Loading, and Error Handling

- Follow existing TFHUB forms, file controls, buttons, notifications and loading patterns.
- All user-facing text must be in Polish.
- Prevent duplicate upload submissions and preserve the selected group/file after recoverable errors.
- Handle upload, network, authorization and validation failures without presenting a success state.
- Keep the flow usable on mobile devices, including narrow layouts and touch controls.
- Backend authorization and validation remain authoritative.

## 7. Out of Scope

- OCR review or correction (FE-TRAINING-007).
- Accepting or rejecting an import (FE-TRAINING-008).
- Editing a persisted training or creating athletes.
- Retaining or displaying the unoptimized upload.
- Review, acceptance and rejection UI.

## 8. Acceptance Criteria

1. ADMIN and COACH users can upload only for groups allowed by UC-506.
2. The user must select exactly one group and one image.
3. The upload is sent as multipart data to UC-506 and does not wait for OCR completion.
4. Success refreshes the active-import rows and displays the possible-duplicate warning.
5. Active processing and scheduled automatic retries are polled; `REVIEW_REQUIRED` links to review and `FAILED` permits retry.
6. Retry calls UC-507, is not repeatable while pending, and resumes status polling.
7. Upload or retry failure does not display success or create a misleading UI state.
8. The selected group is read-only after upload.
9. The UI follows existing TFHUB visual and responsive conventions, with Polish user-facing text.