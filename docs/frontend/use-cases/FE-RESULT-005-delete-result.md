# FE-RESULT-005 – Delete Result

## 1. Purpose

Allow the user to permanently delete an existing test result.

Deletion must require explicit user confirmation to prevent accidental data loss.

## 2. Scope

### In scope
- Triggering deletion from the Results table.
- Displaying a confirmation dialog.
- Showing the details of the selected result.
- Calling the existing Delete Result backend API.
- Removing the deleted result from the local Results collection.
- Handling API errors and loading states.

### Out of scope
- Adding results (FE-RESULT-002 and FE-RESULT-003).
- Editing results (FE-RESULT-004).
- Undo or result recovery.
- Changes to backend functionality or database schema.

## 3. Entry point

Each result row contains a `Usuń` action.

Clicking it opens a confirmation dialog.

## 4. Confirmation dialog

Title: `Usuń wynik`

Display a clear description of the result being deleted:

- Athlete's first and last name.
- Test name.
- Result value and unit.
- Result date.
- Result type.

Confirmation message:

`Czy na pewno chcesz usunąć ten wynik? Tej operacji nie można cofnąć.`

Actions:
- `Usuń` – destructive primary action.
- `Anuluj` – secondary action.

Use the established TFHUB destructive action styling. Red should be reserved for this destructive operation.

## 5. User interaction

### Confirmation
1. User clicks `Usuń` in a result row.
2. Confirmation dialog opens with the selected result's details.
3. User confirms deletion.
4. Frontend sends one DELETE request to the existing backend API.
5. On successful response:
    - Close the dialog.
    - Remove the result from the local Results collection.
    - Reapply current filters and sorting.
    - Show a success notification.

Do not reload the page or fetch the entire Results collection again.

### Cancellation
- Clicking `Anuluj` closes the dialog.
- No API request is sent.
- The Results collection remains unchanged.

### Failed deletion
- Keep the result in the local collection.
- Display a useful error message.
- Allow the user to retry.
- Do not remove the result optimistically before backend confirmation.

### Submission state
- Prevent duplicate DELETE requests while one is in progress.
- Show an appropriate loading state.
- Prevent accidental repeated confirmation.

## 6. UI and design

- Follow the existing TFHUB visual design.
- Reuse existing confirmation dialog or modal patterns if available.
- Use red styling only for the destructive confirmation action.
- Make the dialog responsive.
- Keep the confirmation concise and unambiguous.

## 7. API integration

Use the existing backend Delete Result implementation (Backend UC-206).

Inspect the actual controller, endpoint and response behavior.

The backend contract currently specifies:
- `DELETE /api/results/{id}`
- Positive result ID.
- Successful deletion returns HTTP 204 No Content.
- Missing result returns HTTP 404.
- Only the selected result is deleted.
- Deletion is allowed even if the linked athlete or test is inactive.

Do not modify backend code or database schema.

## 8. Acceptance criteria

- [ ] User can trigger deletion from a Results table row.
- [ ] Confirmation dialog displays the correct result details.
- [ ] Deletion requires explicit confirmation.
- [ ] Cancel closes the dialog without an API request.
- [ ] Confirm sends exactly one DELETE request.
- [ ] Successful deletion removes the result from the local collection.
- [ ] Current filters and sorting remain applied.
- [ ] No full page reload or unnecessary GET request occurs.
- [ ] API failure leaves the result visible.
- [ ] API error is communicated clearly.
- [ ] Duplicate requests are prevented.
- [ ] Deletion works for results linked to inactive athletes or tests.
- [ ] The dialog works on desktop and mobile.

## 9. Dependencies

- FE-RESULT-001 – View Results
- Backend UC-206 – Delete Result
- Existing frontend confirmation dialog patterns

## 10. Completion

After implementation, all five Results frontend use cases should be complete:

- FE-RESULT-001 – View Results
- FE-RESULT-002 – Add Result
- FE-RESULT-003 – Add Multiple Results
- FE-RESULT-004 – Edit Result
- FE-RESULT-005 – Delete Result

## 11. Implementation notes

- The Results view opens a native confirmation dialog populated from the selected local result row; it makes no additional GET request.
- Confirmation sends `DELETE /api/results/{id}` and expects `204 No Content`. The result remains in the local collection until the request succeeds.
- On success, only the matching result ID is removed locally. Filters and sorting remain unchanged. On failure, the dialog remains open, displays an error, and allows retry.
- The backend allows deletion regardless of whether the linked athlete or test is active.