# FE-TRAINING-007 – Review Training Import

## 1. Purpose

Allow an authorized user to inspect and correct the current OCR draft before the separate explicit acceptance or rejection in FE-TRAINING-008.

## 2. Actors and Preconditions

- **ADMIN** – may review imports for any group.
- **COACH** – may review imports only while currently assigned to the import's group.
- The user is authenticated and backend UC-508 is available.
- The import is in `REVIEW_REQUIRED` state and has a current draft.

## 3. Main Flow

1. The user selects a review-required import from the training list and the frontend retrieves `GET /api/training-imports/{importId}/draft`.
2. The frontend retrieves the optimized JPEG from `GET /api/training-imports/{importId}/source-image`.
3. The user compares the image with recognized data, corrects the draft and resolves every athlete row by matching or explicitly skipping it.
4. The frontend retrieves athlete candidates from `GET /api/training-imports/{importId}/athlete-candidates?query=...`.
5. The user saves all corrections with `PUT /api/training-imports/{importId}/draft`.
6. The frontend displays backend validation results and remains in review until the user explicitly accepts or rejects through FE-TRAINING-008.

## 4. Review Data

Display the training name and date, the immutable training group, ordered definitions and segments, athlete rows and matching state, per-segment execution status/result/spike usage, recognized text and uncertainty indicators.

The user may:

- Correct the name and date.
- Add, rename, reorder and remove definitions.
- Add, edit, reorder and remove segments, including distance and rest.
- Add an existing eligible athlete to a definition, match an unresolved or incorrectly matched row, or explicitly skip it.
- Correct each execution status, measured result and spike usage independently.

The group selected at upload is read-only. Athlete candidate results and assignments must be restricted to that group for both ADMIN and COACH users. The user cannot create athletes or change their group assignments.

## 5. Validation and Saving

- Keep all athlete rows matched or explicitly skipped before acceptance; show unresolved rows clearly.
- Validate the form for usability, while treating backend validation as authoritative.
- Use the backend's draft validation result to show whether the draft is ready for acceptance and which issues remain.
- Preserve user edits when saving fails and display field-level or general errors using existing TFHUB patterns.
- Save the current draft atomically. Do not present or create per-edit history or draft versions.
- The optimized JPEG remains available for comparison and is not changed by review.

## 6. UI, Loading, and Error Handling

- Follow existing TFHUB forms, tables/cards, selectors, buttons, notifications and responsive patterns.
- Use the training-details layout: a training summary, segment table and athlete-by-segment results table with editable data. Highlight fields needing input or verification with the warning background and descriptive text.
- Use the searchable athlete selector and the training-completion time and spike controls. Reset buttons restore values loaded when the review page was opened, including after saving while the page remains open.
- All user-facing text must be in Polish.
- Show loading, empty, validation and API error states.
- Disable repeated save submissions while saving.
- If processing is still pending or failed, show the current status and retry path on the training list from FE-TRAINING-006 instead of an editable draft.
- Do not display raw model responses or internal storage paths.
- Keep the image and editing workflow usable on mobile without requiring horizontal scrolling for the whole page.

## 7. Out of Scope

- Uploading a photo or retrying OCR except for linking to FE-TRAINING-006.
- Accepting or rejecting an import (FE-TRAINING-008).
- Changing the import's training group.
- Creating athletes or changing group assignments.
- Persisting training sessions, participations or executions.
- Draft revision history or an audit trail of edits.
- Any backend changes.

## 8. Acceptance Criteria

1. Authorized users can retrieve the current draft and optimized JPEG.
2. The upload-selected group is displayed read-only.
3. Athlete candidate search and matching are restricted to the import's group, including for ADMIN users.
4. Users can correct recognized training information, definitions, segments, athlete matches/skips and executions.
5. Recognition text and uncertainty are visible, and uncertain fields can be manually corrected.
6. Every athlete row must be matched or explicitly skipped before acceptance.
7. Saving sends the current draft to UC-508 and reports backend validation results.
8. Failed saves preserve user edits; successful saves do not automatically accept or reject the import.
9. Draft changes do not create or alter persisted training data.
10. The UI uses existing TFHUB patterns, Polish text and responsive behavior.