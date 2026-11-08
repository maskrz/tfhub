# FE-RESULT-003 – Add Multiple Results

## 1. Purpose

Allow the user to enter results for multiple athletes performing the same test on the same date.

The feature is designed for fast group data entry. Instead of opening a separate form for every athlete, the user selects the test and date once and enters individual results in a single form.

## 2. Scope

### In scope
- Opening the Add Multiple Results modal from the Results view.
- Selecting one test.
- Selecting one result date.
- Selecting one result type.
- Displaying a list of eligible athletes.
- Entering individual result values.
- Skipping athletes with empty result fields.
- Submitting all entered results in one batch API request.
- Updating the Results table after successful creation.
- Handling validation and API errors.

### Out of scope
- Adding a single result (FE-RESULT-002).
- Editing results (FE-RESULT-004).
- Deleting results (FE-RESULT-005).
- Changes to backend functionality or database schema.

## 3. Entry point

The Results view should provide a separate action:

- `Dodaj wiele wyników`

Clicking it opens the Add Multiple Results modal.

Keep the existing `Dodaj wynik` action for single-result entry.

## 4. Modal form

Title: `Dodaj wiele wyników`

### Shared fields

| Field | Description |
|---|---|
| Test | Searchable dropdown for selecting a test |
| Data | Result date, defaulting to today's date |
| Rodzaj | Dropdown: Trening / Zawody |

The selected test's unit must be clearly visible. Require a test selection before batch submission.

The test dropdown filters its options as the user types. Above the athlete rows, provide a local text filter that initially shows every eligible athlete and hides nonmatching rows as the user types. Filtering the list must not clear values already entered for temporarily hidden athletes.

### Athlete results list

Display all eligible athletes when the modal opens, even before a test is selected. The athlete filter initially starts empty, so every eligible athlete is visible.

Each row should contain:

| Column | Description |
|---|---|
| Zawodnik | Athlete's first and last name |
| Wynik | Numeric input for the athlete's result |

Use the existing athlete data source and follow the backend eligibility rules. Do not invent additional restrictions.

Only display athletes who are eligible for new results according to the existing application and backend rules.

All result inputs should initially be empty.

The list should be easy to scan and use on desktop and mobile devices.

## 5. Input behavior

- Each athlete has an independent result input.
- Empty inputs mean that no result will be created for that athlete.
- The user must be able to leave any number of fields empty.
- At least one valid result must be entered before submission.
- Values must be positive.
- Support up to three decimal places, consistent with the backend NUMERIC(12,3) definition.
- Do not silently round or truncate values.
- Display validation errors close to the relevant inputs.

Do not create results for athletes with empty inputs.

## 6. Submission

When the user clicks `Zapisz wyniki`:

1. Validate the shared fields.
2. Validate all non-empty result inputs.
3. Build a batch containing only athletes with entered values.
4. Submit the batch using the existing Add Multiple Results API (Backend UC-202).
5. Send exactly one batch request.

Respect the backend batch limit of 100 results.

Do not send duplicate athlete IDs in one batch.

The backend batch operation is transactional. Treat the response as an all-or-nothing operation.

### Successful submission

On success:
- Close the modal.
- Add all created results to the local Results collection.
- Reapply current filters and sorting.
- Show a success notification indicating how many results were added.
- Do not reload the page or fetch the entire collection again.

### Failed submission

On failure:
- Keep the modal open.
- Preserve all entered values.
- Display a useful error message.
- Allow the user to correct the data and retry.
- Do not partially update the local Results collection.

### Cancellation

Clicking `Anuluj` closes the modal without saving.

No API request should be sent.

## 7. UI and design

- Follow the existing TFHUB visual design.
- Reuse existing modal, form and input patterns.
- Use the established primary and secondary button styles.
- Keep shared fields visually separate from the athlete results list.
- Display the test unit prominently.
- Make the athlete list compact and easy to use.
- Ensure the modal is responsive and usable on mobile.
- Avoid unnecessary abstractions and duplicated code.

## 8. API integration

Use the existing backend Add Multiple Results implementation (Backend UC-202).

Inspect the actual controller, request DTO, response DTO, endpoint and validation rules.

Do not invent endpoint paths, request fields or response properties.

The frontend should submit all non-empty results in one batch request.

Do not implement this feature by calling the single-result API repeatedly.

## 9. Acceptance criteria

- [ ] User can open the Add Multiple Results modal.
- [ ] User can select a test, date and result type.
- [ ] The selected test's unit is visible.
- [ ] Eligible athletes are displayed.
- [ ] Every athlete has an independent result input.
- [ ] Empty fields are skipped.
- [ ] At least one valid result is required.
- [ ] Invalid values are clearly communicated.
- [ ] All entered results are submitted in one batch request.
- [ ] The batch respects the backend maximum of 100 results.
- [ ] Successful submission updates the Results table without reloading the page.
- [ ] Existing filters and sorting remain applied.
- [ ] API failure preserves all entered values.
- [ ] Failed batch submission does not partially update the frontend collection.
- [ ] Cancellation does not send an API request.
- [ ] The interface works on desktop and mobile.

## 10. Dependencies

- FE-RESULT-001 – View Results
- Backend UC-202 – Add Multiple Results
- Existing Athlete and Test data sources
- Existing shared frontend modal and form conventions

## 11. Future extensions

Editing and deleting individual results will be implemented separately in FE-RESULT-004 and FE-RESULT-005.

## 12. Implementation notes

- The Results view reuses the already-loaded athlete and test collections. The form offers active athletes and active tests only, matching the backend's current UC-202 eligibility checks.
- Results are submitted in one `POST /api/results/batch` request using `CreateMultipleResultsRequest`; the response is `CreateMultipleResultsResponse`, containing `createdCount` and the created `ResultResponse` items.
- Empty athlete values are excluded from the payload. The form validates positive values, up to three decimal places and the database's maximum of nine integer digits, and rejects batches larger than 100 before submission.
- Changing the selected test clears all entered athlete values and announces that they were cleared, so values cannot be submitted accidentally under a different test.
- Test searching is integrated into the test dropdown. The athlete list filter matches first and last names; filtering does not discard values entered for hidden athletes.
- On success, the returned results are joined to the already-loaded athlete and test data and appended locally; current filters and sorting remain unchanged. No follow-up GET request is made.
- The backend additionally enforces active-athlete and active-test eligibility and processes the batch transactionally. A backend failure leaves the modal open and does not change the local collection.