# FE-TRAINING-003 – Edit Training Plan

## 1. Purpose

Allow an authorized user to edit the plan of an existing planned or completed training: its name, date, definitions and segments. Athlete assignments and execution data are managed separately through FE-TRAINING-009 – Complete Training.

This flow covers TEMPO plan editing and the type-specific FREE_TEXT editor at `/trainings/:id/free-text/edit`. TEMPO group assignment remains unchanged; the FREE_TEXT editor also allows changing between GROUP and ATHLETES assignment.

## 2. Actors and Preconditions

- **ADMIN** – can edit any training.
- **COACH** – can edit group-assigned trainings in groups assigned to that coach; ATHLETES-mode FREE_TEXT only when all selected athletes currently belong to groups assigned to that coach.
- The user is authenticated, the training exists, and backend UC-503 is available.

## 3. Main Flow

1. The user opens a training from the list or details view.
2. The user selects **Edytuj trening**.
3. The frontend loads the training using UC-504 and populates the form.
4. The user edits the name, date, definitions and segments. The TEMPO group is read-only.
5. The user submits the plan to UC-503.
6. On success, the frontend navigates to the training details view.

## 4. Editable Data

- Training name and date; the displayed training group cannot be changed.
- Add, rename, reorder and remove definitions.
- Add, edit distance/rest, reorder and remove segments within a definition.

Athlete assignments, execution status, results and spike usage are not editable here. They are managed in FE-TRAINING-009.

### FREE_TEXT Assignment

- The FREE_TEXT editor lets the user choose `GROUP` or `ATHLETES`.
- GROUP mode selects exactly one group and clears athlete targets.
- ATHLETES mode selects one or more active athletes and clears the group.
- The UI must never submit both targets or allow an empty assignment.
- ADMIN can select any active target. COACH can select only groups assigned to them or athletes whose current groups are all assigned to them.
- Existing FREE_TEXT assignment is loaded into the form; changing mode replaces the previous target set on successful save.
- Athlete selection does not create TEMPO participation or execution records. Other athletes in a selected athlete's group do not gain visibility.

## 5. Data Preservation and Deletion

- Preserve existing definition and segment IDs when they remain; use stable IDs rather than sequence numbers.
- Reordering must preserve the relationship between segments and existing execution records.
- Never update execution status, measured results, spike usage or assignments as an unrelated side effect.
- A definition with a `RECORDED` execution cannot be removed. Removing a definition without recorded executions also removes its athlete assignments and non-recorded executions. Before submission, clearly warn that assigned athletes will be removed with the definition.
- A segment with a `RECORDED` execution cannot be removed or have its distance changed. Removing an eligible segment removes its non-recorded execution rows.
- Adding a segment creates a pending execution for each currently assigned athlete in that definition.
- Backend UC-503 remains authoritative; display its validation errors without losing unsaved input.

## 6. Validation and Submission

- Validate the required training name, any supplied date, non-empty definitions and segments, non-empty definition names, positive integer distances and valid rest values. Completed sessions and TEMPO sessions with non-PENDING executions must retain their dates.
- Accept rest as minutes (for example `4` or `1,5`), seconds (`45s`) or mixed values (`4m 10s`); send seconds to the API.
- Do not send group, participation or execution fields in the TEMPO UC-503 request. Send exactly the chosen group or athlete IDs in the FREE_TEXT request.
- Prevent duplicate submissions.
- On failure, retain all user changes and display the server error.

## 7. UI Requirements

Follow FE-TRAINING-002 form patterns and existing TFHub controls, validation and responsive behavior. Clearly label the view **Edytuj trening** and show the group as immutable. All user-facing text is in Polish.

## 8. Out of Scope

- Editing TEMPO athlete participation as a separate task.
- Editing execution statuses, results or spike usage.
- Viewing or deleting trainings (FE-TRAINING-005).
- OCR and imports.
- Editing FREE_TEXT sections (handled by the type-specific editor).

## 9. Acceptance Criteria

1. Authorized users can edit a planned or completed training plan; unauthorized users are rejected.
2. Existing values load correctly and the group is read-only.
3. Definitions and segments can be edited and reordered while retained IDs remain stable.
4. Plan edits do not change retained participant assignments or execution values.
5. Deletion behavior and consequences follow UC-503 and are clearly communicated.
6. Backend errors do not discard form changes.
7. Success navigates to training details.
8. The UI follows existing TFHub patterns and works on mobile.
9. FREE_TEXT assignment mode can be changed without affecting TEMPO behavior, and only the submitted target(s) receive visibility.
