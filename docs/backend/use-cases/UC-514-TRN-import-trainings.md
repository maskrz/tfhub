# UC-514 – Import Trainings from JSON

## 1. Purpose

Validate a complete portable training JSON file without persistence, then create the user's selected trainings as new PLANNED sessions with newly selected assignments.

This is separate from the OCR workflow in UC-506–UC-509. It does not create an import draft, restore execution history or perform duplicate detection.

## 2. Actors and Preconditions

- **ADMIN** – may import to any existing group and assign any active athletes.
- **COACH** – may import only to currently assigned groups and may select only active athletes whose current groups the coach is assigned to.
- The requester is authenticated and has the ADMIN or COACH role.

## 3. File Validation

- The client uploads one UTF-8 JSON file to `POST /api/trainings/import/validate` as multipart field `file`.
- The maximum file size is 1 MiB; the file contains at most 100 trainings, 50 definitions per TEMPO training, 100 segments per definition and 100 sections per FREE_TEXT training.
- The backend validates the entire document, its `formatVersion`, all entries, nested fields and structural rules defined by UC-513 before returning any usable result.
- Reject malformed JSON, duplicate or unknown properties, trailing JSON values, unsupported versions, missing/extra type-specific fields, invalid names/sections/definitions/segments, wrong types, out-of-range values and any limit violation. Include the training index and field path in understandable Polish validation feedback where possible.
- If any entry is invalid, reject the complete file. Validation does not create trainings or persistent draft state.
- The validation response returns the normalized version-1 content. The final creation request is revalidated by the backend; the frontend is not a trust boundary.

## 4. Import Dialog and Creation Request

After validation, show every training with its name and full imported content. Exclude imported dates; each training starts with an empty date. Require a fresh assignment for each selected training.

For multiple entries, provide common group and optional date defaults. Apply changes to all rows, including rows later deselected. Each row also has its own group and date values; changing one row does not change others, and values survive selection toggles. For a single entry, the row can display its fields without a separate common-default section.

Every row is checked by default. Users may deselect entries; only checked entries are created. The Import action is disabled when no entry is checked.

### 4.1 TEMPO

- TEMPO always uses `GROUP` assignment and requires one group. Do not offer an alternate assignment mode or create participants.
- Use the common group/date defaults where applicable and permit per-entry overrides.

### 4.2 FREE_TEXT

- Offer the existing mutually exclusive `GROUP` or `ATHLETES` assignment modes per row; default to `GROUP`.
- GROUP mode requires one group and no athlete IDs. The common group default applies in this mode.
- ATHLETES mode requires one or more explicitly selected active athletes and no group assignment. Common group defaults do not become assignments in this mode.
- Do not import or restore previous athlete assignments.

## 5. Persistence and Transaction Behavior

- `POST /api/trainings/import` accepts multipart fields `file` (the same source JSON) and `metadata` (the format version plus one selection/date/assignment entry per training).
- Re-read and revalidate the complete source file, including deselected entries. Reject metadata whose version or item count does not match the file. Validate assignment and date rules for each selected entry before invoking any creation service.
- Create each selected TEMPO or FREE_TEXT training through the existing direct creation service, inside one transaction. Imported content/order is preserved exactly, including leading/trailing whitespace in FREE_TEXT section text and equipment; new database IDs are assigned.
- Every created training has status `PLANNED`, including dated trainings. A null date is valid for a planned training.
- Create no TEMPO participation, execution or result rows; FREE_TEXT has only its sections/equipment and selected assignment.
- If any selected training fails validation or persistence, roll back the entire batch. Never leave partial creations.
- Return the created IDs/count so the client can show Polish success feedback, close the dialog and refresh the list.

## 6. Authorization and Errors

- Only ADMIN and COACH may validate or create an import; ATHLETE is denied by the backend.
- Recheck each selected group and athlete against the authenticated user's current access at creation time. A coach's access to all assigned athletes must be validated by the existing FREE_TEXT creation rules.
- Never rely on client-supplied user IDs, coach scopes or the prior validation response for authorization.
- Validation or authorization errors are returned using standard API errors and must not be reported as success. Persistence errors roll back the batch.

## 7. Out of Scope

- Updating existing trainings or deduplicating imported trainings.
- Restoring dates, group/athlete assignments, status, participants, executions, results, history or OCR metadata.
- Modifying the OCR workflow or its dated-import COMPLETED behavior.
- Database schema changes or persisted import-draft records.
