# FE-RESULT-004 – Edit Result

## 1. Purpose

Allow the user to edit an existing test result.

The result is edited through a modal form opened from the Results table.

The form should reuse the existing single-result form where practical.

## 2. Scope

### In scope
- Opening the Edit Result modal from a Results table row.
- Prepopulating the form with the selected result's data.
- Editing athlete, test, date, value and result type, subject to backend business rules.
- Client-side validation.
- Integration with the existing Update Result backend API.
- Updating the Results table after successful editing.
- Handling API and validation errors.

### Out of scope
- Adding results (FE-RESULT-002).
- Adding multiple results (FE-RESULT-003).
- Deleting results (FE-RESULT-005).
- Changes to backend functionality or database schema.

## 3. Entry point

Each result row has an `Edytuj` action.

Clicking it opens the Edit Result modal for that specific result.

## 4. Modal form

Title: `Edytuj wynik`

Reuse the single-result form from FE-RESULT-002 wherever practical.

Fields:

| Field | Description |
|---|---|
| Zawodnik | Athlete selection |
| Test | Test selection |
| Data | Result date |
| Wynik | Numeric result value |
| Rodzaj | Trening / Zawody |

All fields should initially contain the selected result's current values.

Display the selected test's unit clearly.

The athlete and test dropdowns filter their options as the user types, using the already-loaded data. When the form opens, preserve the current athlete and test—including an inactive linked record—without changing the existing result. Typing in a dropdown replaces its current selection until an option is selected.

## 5. Selection rules

Follow the actual backend Update Result rules (Backend UC-205).

In particular:
- Do not invent eligibility restrictions.
- Handle inactive athletes and tests according to backend rules.
- If an existing result references an entity that is no longer selectable for new results, preserve the ability to edit the result in accordance with backend rules.
- Do not silently change the athlete or test when opening the form.

## 6. Validation

- All fields are required.
- Result value must be greater than zero.
- Support up to three decimal places, consistent with NUMERIC(12,3).
- Do not silently round or truncate values.
- Use backend validation as the authoritative source.
- Display validation messages close to the relevant fields.

## 7. User interaction

### Successful submission
1. User opens Edit Result.
2. Form is prepopulated with current data.
3. User modifies one or more fields.
4. User clicks `Zapisz`.
5. Frontend sends an update request to the existing backend API.
6. On success:
    - Close the modal.
    - Update the corresponding result in the local Results collection.
    - Reapply current filters and sorting.
    - Show a success notification.
    - Do not reload the page or fetch the entire collection again.

### Failed submission
- Keep the modal open.
- Preserve entered values.
- Display a useful error message.
- Allow retry.
- Do not modify the local Results collection.

### Cancellation
- Close the modal without saving.
- Do not send an API request.
- Leave the Results collection unchanged.

### Submission state
- Prevent duplicate submissions while a request is in progress.
- Show an appropriate loading state on the Save button.

## 8. UI and design

- Follow the existing TFHUB visual design.
- Reuse the single-result form from FE-RESULT-002.
- Avoid creating a separate duplicated form.
- Use established primary and secondary button styles.
- Ensure the modal works on desktop and mobile.

## 9. API integration

Use the existing backend Update Result implementation (Backend UC-205).

Inspect the actual controller, endpoint, request DTO, response DTO and validation rules.

Do not invent API paths, request fields or response properties.

Do not modify backend code or database schema.

## 10. Acceptance criteria

- [ ] User can open Edit Result from a table row.
- [ ] The modal contains the selected result's current data.
- [ ] Existing values are not silently changed.
- [ ] User can edit the supported fields.
- [ ] Test unit updates when the selected test changes.
- [ ] Validation prevents invalid submission.
- [ ] Existing backend update API is used.
- [ ] Successful update changes the local Results collection.
- [ ] Current filters and sorting are reapplied.
- [ ] Modal closes after successful update.
- [ ] API errors preserve the form data.
- [ ] Cancellation does not send an API request.
- [ ] Duplicate submissions are prevented.
- [ ] The form works on desktop and mobile.

## 11. Dependencies

- FE-RESULT-001 – View Results
- FE-RESULT-002 – Add Result
- Backend UC-205 – Update Result

## 12. Future extensions

Deleting results will be implemented separately in FE-RESULT-005 – Delete Result.

## 13. Implementation notes

- The existing single-result form and modal are shared between CREATE and EDIT modes; edit state is passed from the selected Results row, with no additional result GET request.
- Updates use `PUT /api/results/{id}` with `UpdateResultRequest` and receive the updated `ResultResponse`.
- In EDIT mode all loaded athletes are selectable because UC-205 permits active and inactive athletes. The currently linked inactive test remains selected and visible so opening the form does not silently alter the historical result. UC-205 requires the test on an update to be active; selecting an inactive test is therefore rejected by the backend with HTTP 409. Active tests remain selectable.
- Athlete and test filtering is integrated into the dropdown controls. The current athlete and test, including an inactive linked test, remain selected when the form opens; typing starts a new selection and filters options in place.
- Successful updates replace only the result with the matching ID in the local collection. Filters and sorting stay intact; the Results collection is not reloaded.