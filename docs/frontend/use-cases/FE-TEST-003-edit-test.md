FE-TEST-003 — Edit Test

Status: Implemented

Goal

Allow the authenticated user to edit an existing sports test using the same modal and form component used for adding a test.

Entry Point

The user clicks the "Edytuj" action in a test row in the Test Management view.

Main Flow

The user opens the Test Management view.

The user clicks "Edytuj" for a selected test.

The application opens a modal dialog titled "Edytuj test".

The application displays the shared test form populated with the selected test's current data:

Name

Unit

Category (TIME, DISTANCE, SPEED)

Higher is better

The user modifies the desired fields.

The user clicks "Zapisz".

The application validates the form.

The application sends the update request using the existing backend Update Test API.

On success:

The modal closes.

The Test Management list is updated without a full page reload.

The updated test is displayed according to the current search and active-status filters.

The user remains on the Test Management view.

Alternative Flows

Validation Error

Invalid fields are highlighted.

Relevant validation messages are displayed.

The modal remains open.

No API request is sent when client-side validation fails.

Backend Error

The modal remains open.

A user-friendly error message is displayed.

The entered form values are preserved.

Cancel

The user clicks "Anuluj" or closes the modal.

The modal closes without saving changes.

The original test data remains unchanged.

Business Rules

Activation and deactivation are performed by the separate status action in each Test Management table row, not through this form.

The form contains only name, unit, category and higher-is-better, matching the existing `UpdateTestRequest`. The active status remains unchanged by the update request.

Existing backend rules concerning changes to tests with associated results must be respected.

The frontend must not bypass backend validation or introduce conflicting business rules.

Acceptance Criteria

Editing a test does not navigate to a separate page.

The same form component is used for adding and editing tests.

The form is populated with the selected test's current data.

The modal title clearly distinguishes adding from editing.

Successful updates refresh the affected list item without a full page reload.

Changes to definition fields are reflected in the current list filter.

API errors do not discard the entered values.

The UI follows the existing TFHUB design guidelines and Test Management visual reference.

The modal is usable on desktop and mobile.

Out of Scope

Adding new tests.

Changing backend business rules.

Editing test results.