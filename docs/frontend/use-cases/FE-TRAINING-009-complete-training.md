# FE-TRAINING-009 – Complete Training

## 1. Purpose

Allow an authorized user to assign athletes to a training's definitions and record or correct their execution statuses, results and spike usage. This is the **Uzupełnij trening** task and is separate from editing the plan.

## 2. Actors and Preconditions

- **ADMIN** – can complete any training.
- **COACH** – can complete trainings in groups assigned to that coach.
- The user is authenticated, the training exists, and backend UC-510 is available.

## 3. Main Flow

1. The user opens a training from the list or details view and chooses **Uzupełnij trening**.
2. The frontend loads complete training details, including all definitions, segments, historical participants and executions.
3. The user assigns athletes to definitions, removes or reassigns eligible athletes, and updates execution data.
4. The frontend submits explicit additions, execution updates, and removals to UC-510. Omitted participants and executions remain unchanged.
5. On success, the frontend stays on the completion view and displays a dismissible success notice that closes automatically after five seconds. The frontend refreshes the saved training data so later edits use persisted participation and execution IDs.

The first successful save changes the training status from `PLANNED` to `COMPLETED`, even if some executions remain `PENDING`. Failed saves do not change the status. Completed trainings remain editable for later corrections.

## 4. Athlete Assignments

- Display each definition and its planned segments.
- Allow athletes to be added to, removed from, and moved between definitions.
- Prevent an athlete from being assigned to more than one definition in the training.
- New assignments are limited to athletes currently belonging to the training's group.
- Existing participants remain visible and editable even if their current group has changed.
- A participant with a `RECORDED` execution cannot be removed or moved. To move an eligible participant, remove the old participation and create a new assignment.
- Do not remove participants or executions merely because they are absent from a request; only explicit removals take effect.

## 5. Execution Data

Display results in a table with one athlete per row and one planned segment per column. Each athlete/segment cell has a result input and an independent spikes checkbox labeled `K`.

- An empty result means `PENDING`.
- A valid time means `RECORDED`; zero is valid and values are submitted as seconds. Accept seconds with an optional fractional part, or minutes and seconds with an optional fractional part.
- Normalize commas/dots used as two separators into `minutes:seconds,fraction`, and normalize semicolons to colons. A single dot is changed to a comma. A one-digit fraction entered with a comma is padded with a trailing zero; a single dot fraction keeps its entered precision.
- Do not add a `00:` prefix when minutes are omitted. Fractional seconds and minutes are optional; when present, seconds after minutes must be in the range 00–59.
- Apply formatting after the result field loses focus. Invalid input is indicated by a red border without an inline validation message.
- `X` means `COMPLETED_WITHOUT_RESULT`.
- `-` means `NOT_COMPLETED`.
- Existing API results with thousandths are rounded to hundredths for display and editing.
- The `K` checkbox records spike usage independently for that athlete and segment.

New participants start with pending executions for every segment. The form may update those values before saving.

## 6. Data Integrity and Submission

- Preserve existing participation and execution IDs when retained.
- Associate execution records with stable segment IDs, never sequence numbers.
- Preserve existing participation and execution IDs; updates target only explicitly listed execution IDs.
- Send only execution values changed in the form so a stale page cannot overwrite another coach's concurrent updates.
- Do not change the training group or plan fields.
- Validate athlete uniqueness and result/status consistency in the UI; the backend remains authoritative.
- Prevent duplicate submissions.
- On backend validation or network failure, retain all user changes and show the error clearly.

## 7. UI Requirements

Follow existing TFHub form, athlete-selector, validation, loading/error and responsive patterns. Keep the results table horizontally usable on narrow screens. All user-facing text is in Polish.

## 8. Out of Scope

- Editing training name, date, definitions or segments (FE-TRAINING-003).
- Viewing implementation, deletion (FE-TRAINING-005), imports, athlete records or group assignments.

## 9. Acceptance Criteria

1. Authorized users can open the completion view for a planned or completed training.
2. Historical participants remain available after their current group changes.
3. New assignments are limited to athletes currently in the training group.
4. The same athlete cannot appear in multiple definitions.
5. Athlete rows and segment columns provide per-cell time, `X`, `-`, and `K` controls; these map to all four execution statuses and independent spike usage.
6. Existing IDs and segment associations are preserved.
7. Recorded execution prevents removal or reassignment of its participant.
8. Backend errors preserve form changes.
9. After success, the user remains on the completion view and can dismiss the success notice or allow it to close after five seconds.
10. The UI is consistent with TFHub and usable on mobile devices.
11. A successful save marks a planned training as completed; failed saves leave its status unchanged.
