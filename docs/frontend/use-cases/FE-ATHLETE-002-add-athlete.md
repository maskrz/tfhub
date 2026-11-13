# FE-ATHLETE-002 — Add Athlete

**Status:** Implemented
**Module:** Frontend / Athlete Management
**Related backend functionality:** UC-001 — Add Athlete

## Goal
Allow the user to add a new athlete through a modal form directly from the Athletes view.

## Entry point
The user clicks the **"Dodaj zawodnika"** button on the Athletes view.

## Main flow
1. The application opens the shared Athlete Form modal in CREATE mode. Opening the modal does not send an API request.
2. The modal displays an empty form with the following fields:
    - First name
    - Last name
    - Birth year
    - Gender
    - Training group (required for coaches) and optional aliases
3. The user enters the athlete's data.
4. The user clicks **"Zapisz"**.
5. The frontend validates the form.
6. The frontend sends `POST /api/athletes` once using the existing create request DTO shape.
7. On success:
    - Close the modal.
    - Add the returned athlete response to the existing local collection without reloading the page or fetching details.
    - Preserve the current search, filter, and sorting state.
    - Display an accessible success notification.

## Business rules
- A newly created athlete is always active.
- The form must not contain an active/inactive field.
- The unique athlete constraint is based on first name, last name, and birth year, as defined by the backend.
- Gender must use the existing backend enum values.
- Backend validation remains authoritative.
- Aliases can be added, edited and removed before saving; each contains at most 255 characters. Alias conflicts are shown in Polish without losing entered data.
- The form provides immediate usability validation for trimmed required names, the 100-character name limit, integer birth year from 1900 through the current year, and required gender.
- The backend is authoritative for all validation and duplicate detection.
- The `active` value is not sent by the client; the API response marks a newly created athlete active.

## Alternative flows
- **Invalid form:** Display validation messages and do not send the request.
- **Duplicate athlete (HTTP 409):** Display a clear Polish message for matching first name, last name, and birth year.
- **Validation error (HTTP 400):** Show field-level messages when returned and a form-level message; retain entered values.
- **API error:** Keep the modal open, preserve entered values, and display an error message.
- **Cancel:** Close the modal without saving changes.

## UI requirements
- Use the shared Athlete Form component.
- Modal title: "Dodaj zawodnika".
- Primary action: "Zapisz".
- Secondary action: "Anuluj".
- Follow existing modal, form, and button guidelines.
- Support keyboard interaction and responsive layouts.

## Shared component structure

- `AthleteFormComponent` owns the reactive form, client-side usability validation, API submission, and field/form error presentation. Its `mode` and optional athlete inputs support both CREATE and EDIT; the selected mode determines initial values and the matching API operation.
- `AthleteFormModalComponent` owns the accessible dialog, title, cancel/save actions, and submitting-state handling. It contains the shared form and closes only after a successful save or explicit cancellation.
- `AthletesPage` opens the modal in CREATE mode and handles the returned `AthleteResponse`, appending it to the full local collection. FE-ATHLETE-003 uses the same modal and form in EDIT mode and replaces the matching local athlete.
- No list request is made when opening/canceling the form or after a successful create.

The shared form's EDIT mode and update request are implemented in [FE-ATHLETE-003](FE-ATHLETE-003-edit-athlete.md).

## API contract

Request body (`CreateAthleteRequest`):

```json
{
  "firstName": "Jan",
  "lastName": "Kowalski",
  "birthYear": 2010,
  "gender": "MALE"
}
```

The request also supports `trainingGroupId` and `aliases`. Success is HTTP `201 Created` with the athlete DTO and `active: true`. A duplicate athlete or alias returns HTTP `409 Conflict` with the corresponding Problem Details title.

## Out of scope
- Athlete profile or details view.
- Athlete activation/deactivation.
- Results or performance history.
- Backend modifications.

---
