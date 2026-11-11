# FE-TRAINING-002 – Create Training

## 1. Purpose

Allow an authorized user to create a training session, define its training variants, configure segments, and assign athletes to each variant.

The view must follow the existing TFHUB visual design and interaction patterns.

## 2. Actors

- **ADMIN** – can create a training for any existing training group.
- **COACH** – can create a training only for groups assigned to that coach.

## 3. Preconditions

- The user is authenticated.
- The backend UC-502 endpoint is available.
- Required training groups and athletes already exist.

## 4. Main Flow

1. The user selects the option to create a training from the training list.
2. The frontend opens the training creation form.
3. The user enters the training name and date.
4. The user selects exactly one training group.
5. The user adds one or more TrainingDefinitions.
6. For each definition, the user:
    - Enters its name.
    - Adds one or more training segments.
    - Configures the distance and rest after each segment.
    - Assigns athletes to the definition.
7. The user reviews the complete training structure.
8. The user submits the form.
9. The frontend sends the complete request to the backend UC-502 endpoint.
10. On success, the user is redirected to the created training's details view.

## 5. Training Form

### Training details

Required fields:
- Training name.
- Training date.
- Training group.

Rules:
- Exactly one group must be selected.
- The group cannot be changed after the training is created.
- ADMIN can select any existing group.
- COACH can select only groups assigned to them.
- Use the actual group data and existing group-selection mechanisms available in the application.

### TrainingDefinitions

- A training must contain at least one definition.
- Each definition has a required name.
- Definitions can be added and removed.
- Definitions can be reordered.
- Each definition must contain at least one segment.
- Each definition has its own independently managed list of segments and assigned athletes.

### TrainingSegments

Each segment contains:
- Distance.
- Rest after the segment.

Rules:
- Distance must be positive.
- Segment order must be preserved.
- Segments can be added, edited, removed, and reordered.
- Rest values must follow the backend UC-502 validation rules.
- The last segment may have no rest value if permitted by the backend rules.
- Display segment order clearly.

### Athlete assignment

- Athletes are assigned separately to each definition.
- Only athletes belonging to the selected training group can be assigned.
- An athlete can belong to only one definition within the same training.
- The UI must prevent assigning the same athlete to multiple definitions.
- Changing the selected group must refresh the available athlete list and clear assignments that are no longer valid.
- Use existing athlete-selection controls and patterns wherever possible.

## 6. Validation

Validate the form before submission:
- Training name is required.
- Date is required.
- Group is required.
- At least one definition is required.
- Every definition must have a name.
- Every definition must contain at least one segment.
- Segment distances must be positive.
- Rest values must be valid.
- Athlete assignments must be valid and unique across definitions.

Display validation errors next to the relevant fields using existing TFHUB validation patterns.

The backend remains authoritative for all validation.

## 7. UI and UX Requirements

**Visual and interaction consistency with existing TFHUB views is mandatory.**

Before implementation, inspect existing forms, modals, dynamic collections, athlete selectors, inputs, buttons, and validation messages.

Reuse existing components and conventions for:
- Form layout and headings.
- Text inputs and date pickers.
- Select controls.
- Athlete selection.
- Add/remove buttons.
- Reordering controls.
- Validation messages.
- Confirmation and error notifications.
- Responsive and mobile layouts.

Do not introduce a new visual design or custom control style.

The form should clearly distinguish:
- Training details.
- Individual definitions.
- Segments within each definition.
- Athletes assigned to each definition.

The interface must remain usable on mobile devices, even when a training contains multiple definitions and segments.

All user-facing text must be in Polish.

## 8. Submission

- Submit the complete training structure using the actual backend UC-502 request DTO.
- Do not invent or transform API fields beyond what the backend contract requires.
- Prevent duplicate submissions while a request is in progress.
- On success, navigate to the created training details view.
- On validation or server error, preserve entered data and display an appropriate message.
- Follow existing application error-handling conventions.

## 9. Out of Scope

- Editing existing trainings.
- Viewing training details implementation.
- Deactivating trainings.
- OCR and training imports.
- Backend changes.

## 10. Acceptance Criteria

- Authorized users can open the training creation form.
- ADMIN can select any existing group.
- COACH can select only assigned groups.
- User can create multiple definitions.
- User can configure and reorder segments within each definition.
- User can assign athletes to definitions.
- Duplicate athlete assignments are prevented.
- Form validation works correctly.
- Successful submission creates a complete training through UC-502.
- Successful creation redirects to training details.
- Failed submission preserves entered data.
- UI matches existing TFHUB forms and controls.
- The view is responsive and usable on mobile.
- Existing frontend functionality remains unaffected.