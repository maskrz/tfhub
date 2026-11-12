# UC-506 – Upload Training Photo

## 1. Purpose

Provide a backend operation for uploading a training result sheet photo and creating a training import.

The operation must validate the uploaded file, enforce group-based authorization and create an import record that can be processed asynchronously.

Uploading a photo must not create or modify a persisted training session.

## 2. Actors

- **ADMIN** – can upload a training photo for any existing training group.
- **COACH** – can upload a training photo only for a group currently assigned to that coach.

## 3. Preconditions

- The request is made by an authenticated user.
- The user has the ADMIN or COACH role.
- The selected training group exists.
- The uploaded file contains exactly one training sheet photo.

## 4. Business Rules

### 4.1 Training Group Selection

- The training group must be selected before the photo is uploaded.
- Every training import must be associated with exactly one training group.
- ADMIN can select any existing training group.
- COACH can select only a group currently assigned to that coach.
- The backend must validate group access using the authenticated user's current group assignments.
- The selected group defines the scope for subsequent athlete recognition and matching.
- The group assigned to an import is immutable throughout its processing, review and finalization lifecycle.

### 4.2 Photo Upload

- One upload request must contain exactly one image.
- The system must reject files that are not supported image formats.
- Supported input formats are JPEG and PNG; other formats are rejected.
- The system must validate the actual file content rather than relying exclusively on the filename or declared MIME type.
- The system must enforce configured file size and image dimension limits.
- The system must reject empty, corrupted or unreadable image files.
- The uploaded image must be optimized to JPEG at approximately 85% quality, with its longest edge limited to 2560 pixels without upscaling.
- Only the optimized JPEG is retained as the import source; the uploaded bytes are not stored separately.
- The optimized JPEG must not be modified or overwritten during subsequent processing.
- The system must generate a unique identifier for each accepted import.
- The upload operation must not create a persisted training session, training definitions, segments, athlete participations or execution results.

### 4.3 Import Creation

- A successful upload creates a new training import record.
- The import must be associated with:
    - The selected training group.
    - The user who initiated the upload.
    - The optimized JPEG source image.
    - The import creation timestamp.
- A newly created import must enter a state indicating that processing is pending.
- The import must be eligible for asynchronous processing by UC-507 immediately after upload succeeds.
- The import must remain separate from persisted training session data until explicitly accepted through UC-509.

### 4.4 Duplicate Photo Detection

- The system should detect whether the optimized JPEG has already been submitted, using its SHA-256 hash.
- Duplicate detection must not block the upload.
- If a duplicate is detected, the system must provide a warning or duplicate indication.
- A duplicate upload must create a separate import record.
- Duplicate detection must not automatically merge imports or reuse previous OCR results.
- Duplicate hashes are non-unique: every upload creates a distinct import even when the optimized image is identical to an earlier one.

### 4.5 Import Isolation

- Uploaded photos and import drafts must remain isolated from ordinary training session data.
- A pending import must not appear in the standard training list (UC-501).
- A pending import must not be accessible through ordinary training details (UC-504).
- No athlete results may be persisted as actual training execution data during upload.

## 5. Authorization Rules

- Only authenticated ADMIN and COACH users may upload training photos.
- ADMIN may upload photos for any existing training group.
- COACH may upload photos only for groups currently assigned to that coach.
- The backend must derive the requesting user's identity and permissions from the authenticated security context.
- The backend must not rely on client-supplied user identifiers, roles or group assignments.
- Group authorization must be validated before accepting the upload.

## 6. Validation and Error Scenarios

- **Unauthenticated request:** access is rejected.
- **User without ADMIN or COACH role:** access is rejected.
- **Missing training group:** upload is rejected.
- **Non-existing training group:** upload is rejected.
- **COACH selects a group not assigned to them:** access is rejected.
- **Missing image:** upload is rejected.
- **Multiple images in one request:** upload is rejected.
- **Unsupported image format:** upload is rejected.
- **File content does not match the declared image format:** upload is rejected.
- **Empty or corrupted image:** upload is rejected.
- **File exceeds configured size or dimension limits:** upload is rejected.
- **Image cannot be stored:** import creation fails.
- **Import record cannot be created:** the upload is treated as failed.
- **Unexpected server error:** the system reports an appropriate error without exposing internal implementation details.

A failed upload must not leave an import record that references a missing or incomplete source image.

## 7. Output

A successful `POST /api/training-imports` accepts multipart fields `trainingGroupId` and one `image`, creates the import in `PENDING`, and returns `202 Accepted`. The asynchronous processor starts without waiting for OCR in the upload request. Clients retrieve status using `GET /api/training-imports/{importId}`.

The response must include at least:

- Import identifier.
- Selected training group identifier.
- Current import status.
- Whether the uploaded image was detected as a possible duplicate.

The response must not expose internal file storage paths, API credentials or other sensitive implementation details.

## 8. Consistency Requirements

- The optimized JPEG and import record must be stored consistently.
- A successful upload must produce an import that can be retrieved and processed by UC-507.
- A failed upload must not leave an incomplete import or an orphaned stored JPEG. Store the optimized image under a generated key and remove it if import persistence fails.
- Duplicate detection must not prevent creation of a new import.
- The selected training group must remain associated with the import throughout its lifecycle.
- Uploading a photo must not modify existing training sessions or athlete results.

## 9. Out of Scope

- Image alignment and rotation correction beyond the required JPEG optimization and resizing.
- OCR and handwriting recognition.
- Training name and date recognition.
- Training definition and segment recognition.
- Athlete matching.
- Draft creation and OCR result normalization.
- Reviewing or correcting recognized data.
- Accepting or rejecting an import.
- Creating a persisted training session.
- Editing existing training sessions.
- Manual entry of training results.

## 10. Acceptance Criteria

1. ADMIN can upload a training photo for any existing training group.
2. COACH can upload a training photo only for a group currently assigned to that coach.
3. The training group must be selected before upload.
4. Each upload contains exactly one image.
5. Unsupported, corrupted, empty or invalid images are rejected.
6. Configured file size and image dimension limits are enforced.
7. The optimized JPEG is retained as the source document; the unoptimized upload is not retained.
8. A successful upload creates a new training import record.
9. The import is associated with the selected group and initiating user.
10. A newly created import is marked as pending processing.
11. Duplicate photos generate a warning but do not block the upload.
12. A duplicate upload creates a separate import record.
13. Uploading a photo does not create or modify a persisted training session or its related records.
14. Pending imports are excluded from ordinary training list and details operations.
15. A successful upload returns the import identifier, group identifier, status and duplicate indication.
16. A failed upload does not leave an incomplete import or orphaned source image.
17. Unauthorized users cannot upload training photos.