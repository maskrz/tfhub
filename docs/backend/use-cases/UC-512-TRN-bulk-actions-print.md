# UC-512 – Bulk Training Actions and Print Data

The following operations accept `{"trainingIds":[1,2]}`. IDs must be positive, unique integers and the list must not be empty. Every ID is checked against the authenticated user's current access, not trusted from the client.

- `POST /api/trainings/bulk-delete`: ADMIN/COACH only. Returns 204. Locks in ID order and applies UC-505 to every training in one transaction. Any invalid, missing, inaccessible or failing item rolls back the batch. Accepted-import image removal remains after commit.
- `POST /api/trainings/bulk-complete`: ADMIN/COACH only. Locks and authorizes every selection. Completed trainings are unchanged. Undated plans are skipped; dated plans use the existing TEMPO UC-510 empty participation update or FREE_TEXT completion transition. The response is `{"completedIds":[1],"alreadyCompletedIds":[],"skippedUndatedIds":[2]}`. Other failures roll back all updates.
- `POST /api/trainings/print-preview`: ADMIN, COACH and ATHLETE, with UC-504 visibility per training. Returns `{"trainings":[...]}` with only names, dates, types, assignments, ordered definitions/segments or sections, and staff-visible assigned names and equipment. Athlete responses exclude equipment and assigned athlete names; TEMPO is unavailable to athletes. No execution or result data is returned. A missing or inaccessible selection fails the entire request.

Existing single-training endpoints and semantics are unchanged. Normal API validation, error, session and CSRF conventions apply.
