# FE-ATHLETE-003 — Edit Athlete

**Status:** Implemented
**Module:** Frontend / Athlete Management
**Related backend functionality:** UC-004 — Update Athlete

## Goal

Allow the user to edit an existing athlete using the same modal and Athlete Form component used by FE-ATHLETE-002.

## Entry point

The user clicks **Edytuj** in an athlete's row on the Athletes view.

## Main flow

1. The Athletes view opens the shared Athlete Form modal in EDIT mode and passes the selected athlete from its loaded collection.
2. The form is initialized with the selected athlete's personal data, group and aliases. No athlete-details GET request is made.
3. The user changes the desired values and selects **Zapisz**.
4. The shared form validates the values using the same rules as CREATE mode.
5. The frontend sends `PUT /api/athletes/{id}` with an `UpdateAthleteRequest`.
6. On success:
   - Close the modal.
   - Replace the matching athlete in the local collection with the returned `AthleteResponse`.
   - Preserve search, gender and birth-year filters, inactive toggle, and sort state.
   - Display an accessible success message.

## Fields and business rules

The form contains:

- First name
- Last name
- Birth year
- Gender
- Training group and aliases

There is no active/inactive field. The request sends the edited aliases along with personal and group data; the backend preserves the current active status and validates group alias conflicts atomically.

## Validation and errors

- Required, trimmed names, maximum name length, integer birth year within the backend-supported range, and required gender are validated for usability using the existing shared form validators.
- HTTP `400` displays server field errors when provided and a form-level message.
- HTTP `409` displays a clear Polish duplicate-athlete message.
- An alias conflict on edit, group reassignment or activation displays a clear Polish message and preserves the existing state.
- HTTP `404` reports that the athlete could not be found.
- Other API failures keep the modal open and preserve entered values.
- A request in progress disables submission and cancel actions to prevent duplicate operations or accidental dismissal.
- Canceling closes the modal without updating the local list or sending an update request.

## Shared component behavior

`AthleteFormComponent` is the single form for FE-ATHLETE-002 and FE-ATHLETE-003. The `mode` input selects:

| Behavior | CREATE | EDIT |
|---|---|---|
| Initial values | Empty | Selected athlete's current values |
| API call | `POST /api/athletes` | `PUT /api/athletes/{id}` |
| Request fields | Personal data, group, aliases | Personal data, group, aliases |
| Modal title | Dodaj zawodnika | Edytuj zawodnika |

Validation, fields, layout, error presentation, and submitting state are shared. `AthleteFormModalComponent` owns dialog presentation and actions; `AthletesPage` owns local collection updates and notifications.

## API contract

`PUT /api/athletes/{id}` request body (`UpdateAthleteRequest`):

```json
{
  "firstName": "Janusz",
  "lastName": "Nowak",
  "birthYear": 2010,
  "gender": "MALE"
}
```

Success returns HTTP `200 OK` with the updated `AthleteResponse`. Invalid data returns HTTP `400 Bad Request`; duplicate identifying data returns HTTP `409 Conflict`.

## Out of scope

- Athlete profile or details view.
- Athlete activation/deactivation in the form.
- Results or performance history.
- Backend modifications.
