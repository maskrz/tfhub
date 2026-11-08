# FE-RESULT-002 – Add Result

## 1. Purpose

Allow the user to add a single test result for an athlete.

The result is created through a modal form opened from the Results view. After successful creation, the new result is added to the existing results list without reloading the page.

The shared single-result form also supports EDIT mode for FE-RESULT-004. CREATE mode keeps the behavior described in this use case; EDIT mode prepopulates the same fields and calls the update endpoint.

## 2. Scope

### In scope
- Opening the Add Result modal from the Results view.
- Selecting an athlete.
- Selecting a test.
- Entering the result date.
- Entering the numeric result value.
- Selecting the result type.
- Client-side form validation.
- Integration with the existing Add Result backend API.
- Updating the Results table after successful creation.
- Handling API and validation errors.

### Out of scope
- Adding multiple results in one operation (FE-RESULT-003).
- Editing results (FE-RESULT-004).
- Deleting results (FE-RESULT-005).
- Database schema changes.

## 3. Entry point

The Results view contains a primary action:

- `Dodaj wynik`

Clicking this action opens the Add Result modal.

## 4. Modal form

Title: `Dodaj wynik`

Fields:

| Field | Description |
|---|---|
| Zawodnik | Searchable dropdown for selecting an athlete |
| Test | Searchable dropdown for selecting a test |
| Data | Result date, defaulting to today's date |
| Wynik | Positive numeric value |
| Rodzaj | Dropdown: Trening / Zawody |

The selected test's unit should be displayed next to the result input or otherwise clearly communicated to the user.

### Selection rules

- Follow the actual backend business rules for athlete and test eligibility.
- Only active athletes and active tests can be selected for a new result.
- Do not allow selection of tests that cannot accept new results according to backend rules.
- Display athlete and test names clearly in dropdowns.
- Typing in either dropdown control filters options locally by athlete or test name; do not use separate search fields or additional API requests.
- Typing replaces the current selection until a matching option is selected.
- Reuse existing frontend data sources and services where appropriate.

## 5. Validation

- All fields are required.
- Result value must be greater than zero.
- Result value must respect the backend's supported precision (NUMERIC(12,3)).
- Use an appropriate decimal input and allow up to three decimal places.
- Do not silently round or truncate user-entered values.
- Date and selection validation should be consistent with existing frontend forms.
- Backend validation remains authoritative.

Display validation messages close to the relevant fields.

## 6. User interaction

### Successful submission
1. User fills in the form.
2. User clicks `Zapisz`.
3. The frontend sends one request to the existing Add Result API.
4. On success:
    - Close the modal.
    - Add the created result to the local Results collection.
    - Reapply current filters and sorting.
    - Show a success notification.
    - Do not reload the page or fetch the entire collection again.

### Failed submission
- Keep the modal open.
- Preserve all entered values.
- Display a useful error message.
- Allow the user to correct the form and retry.
- If the backend reports an inactive athlete or test, explain that the selected record is no longer eligible.

### Cancellation
- Clicking `Anuluj` closes the modal.
- No API request is sent.
- The Results collection remains unchanged.

### Submission state
- Prevent duplicate submissions while a request is in progress.
- Show an appropriate loading state on the Save button.
- Allow cancellation only when consistent with existing modal patterns.

## 7. UI and design

- Reuse the existing TFHUB visual design.
- Follow the existing Athlete and Test modal/form patterns.
- Use the established primary and secondary button styles.
- Keep the form simple and compact.
- Ensure the modal is responsive and usable on mobile devices.
- Do not introduce unnecessary new components or abstractions.

## 8. API integration

Use the existing backend Add Result implementation (Backend UC-201).

Inspect the actual controller, request DTO, endpoint and validation rules. Do not invent endpoint paths, request fields or response properties.

Use the returned result data to update the frontend collection. Avoid an additional GET request if the response already contains all fields required by the Results table.

## 9. Acceptance criteria

- [ ] User can open the Add Result modal from the Results view.
- [ ] User can select an eligible athlete and test.
- [ ] The selected test's unit is visible.
- [ ] User can enter a date and a positive numeric value.
- [ ] User can select Training or Competition.
- [ ] Invalid input is clearly communicated.
- [ ] A valid result is saved through the existing backend API.
- [ ] The modal closes after successful creation.
- [ ] The Results table updates without a page reload.
- [ ] Current filters and sorting remain applied.
- [ ] API errors do not clear the form.
- [ ] Cancel closes the modal without saving.
- [ ] Duplicate submissions are prevented.
- [ ] The form works on desktop and mobile.

## 10. Dependencies

- FE-RESULT-001 – View Results
- Backend UC-201 – Add Result
- Existing shared frontend modal and form conventions

## 11. Future extensions

Multiple-result entry will be implemented separately in FE-RESULT-003 – Add Multiple Results.

## Implementation note

The eligibility rule was confirmed to exclude inactive athletes as well as inactive tests. The Add Result modal therefore offers only active athletes and tests, and the backend rejects inactive references with HTTP 409. The same active-athlete rule is enforced by the existing batch creation endpoint to prevent bypassing the single-result rule. Historical results remain available.

Athlete and test filtering is integrated into the dropdown controls; typing filters visible options without separate search fields.