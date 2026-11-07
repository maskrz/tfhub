FE-TEST-002 — Add Test

Status: Implemented

Goal

Allow the authenticated user to add a new sports test using a modal dialog, without navigating to a separate page.

Entry Point

The user clicks the "Dodaj test" button in the Test Management view.

Main Flow

The user opens the Test Management view.

The user clicks "Dodaj test".

The application opens a modal dialog titled "Dodaj test".

The application displays an empty test form.

The user enters the test details:

Name

Unit

Category (TIME, DISTANCE, SPEED)

Higher is better

The user clicks "Zapisz".

The application validates the form.

The application sends the request using the existing backend Add Test API.

On success:

The modal closes.

The Test Management list is updated without a full page reload.

The new test is visible if it matches the current list filter.

The user remains on the Test Management view.

Alternative Flows

Validation Error

Invalid or missing fields are highlighted.

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

The list remains unchanged.

Acceptance Criteria

Adding a test does not navigate to a separate page.

The form is displayed in a modal dialog.

The form contains the fields accepted by the create request: name, unit, category and higher-is-better.

The backend creates new tests as active; active status is not a create-form field.

Client-side validation is consistent with backend validation.

The existing backend API is used; no backend changes are required.

Successful creation updates the list without a full page reload.

API errors do not discard the entered values.

The modal is usable on desktop and mobile.

The UI follows the existing TFHUB design guidelines and Test Management visual reference.

Out of Scope

Editing existing tests.

Changing backend business rules.

Managing test categories or units as separate resources.