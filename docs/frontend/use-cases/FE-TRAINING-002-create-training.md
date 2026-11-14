# FE-TRAINING-002 – Create Training

## 1. Purpose

Allow an authorized user to create either a TEMPO training with definitions and segments or a FREE_TEXT training with ordered sections and one of its two assignment modes. Athlete participations and execution data are added later only for TEMPO through FE-TRAINING-009 – Complete Training.

The view must follow the existing TFHUB visual design and interaction patterns.

## 2. Actors

- **ADMIN** – can create a training for any existing training group.
- **COACH** – can create a training only for groups assigned to that coach.

## 3. Preconditions

- The user is authenticated.
- The backend UC-502 endpoint is available.
- Required training groups already exist.

## 4. Main Flow

1. The user selects the option to create a training from the training list.
2. The frontend opens the training creation form.
3. The user enters the training name and date.
4. The user selects the training type.
5. For TEMPO, the user selects exactly one training group. For FREE_TEXT, the user chooses group or individual-athlete assignment and selects the corresponding target(s).
6. For TEMPO, the user adds one or more TrainingDefinitions. For each definition, the user:
    - Enters its name.
    - Adds one or more training segments.
    - Configures the distance and rest after each segment.
7. For FREE_TEXT, the user adds one or more sections with editable titles and content and can reorder them.
8. The user reviews the complete training structure and assignment.
9. The user submits the selected type to its UC-502 endpoint.
10. On success, the user is redirected to the created training's details view.

## 5. Training Form

### Training details

Required fields:
- Training name.
- Training date.
- For TEMPO and FREE_TEXT/GROUP, one training group.
- For FREE_TEXT, an assignment type and its corresponding target(s).

Rules:
- TEMPO always requires exactly one group.
- FREE_TEXT/GROUP requires exactly one group and no athlete targets.
- FREE_TEXT/ATHLETES requires one or more explicitly selected active athletes and no group.
- Group and athlete targets are mutually exclusive.
- ADMIN can select any existing group.
- COACH can select only groups assigned to them. For ATHLETES mode, the coach can select only athletes whose current groups are all assigned to that coach.
- Use the actual group data and existing group-selection mechanisms available in the application.
- Use the existing athlete selection patterns to select multiple active athletes; other members of their groups are not implicitly assigned.

### TrainingDefinitions

- A training must contain at least one definition.
- Each definition has a required name.
- Definitions can be added and removed.
- Definitions can be reordered.
- Each definition must contain at least one segment.
- Each definition has its own independently managed list of segments.

### TrainingSegments

Each segment contains:
- Distance.
- Rest after the segment.

Rules:
- Distance must be positive.
- Segment order must be preserved.
- Segments can be added, edited, removed, and reordered.
- Rest values must follow the backend UC-502 validation rules.
- Accept minutes (for example `4` or `1,5`), seconds (`45s`) or mixed values (`4m 10s`) and send seconds to the API.
- The last segment may have no rest value if permitted by the backend rules.
- Display segment order clearly.

### FREE_TEXT Sections

- Each section contains a free-text title and content; titles are not chosen from a fixed list.
- Sections can be added, edited, removed and reordered. At least one section with a title and content is required.
- A new FREE_TEXT training is `PLANNED` and has no athlete participation or execution records.
- FREE_TEXT assignment mode may be changed later while editing. ATHLETES mode still creates no tempo participation or execution records.

## 6. Validation

Validate the form before submission:
- Training name is required.
- Date is required.
- For TEMPO and FREE_TEXT/GROUP, group is required.
- For FREE_TEXT, assignment mode is required; GROUP requires a group and ATHLETES requires one or more active athletes.
- Training type is required.
- For TEMPO, at least one definition is required.
- Every definition must have a name.
- Every definition must contain at least one segment.
- Segment distances must be positive.
- Rest values must be valid.
- For FREE_TEXT, at least one section is required and each section must have a title and content.

Display validation errors next to the relevant fields using existing TFHUB validation patterns.

The backend remains authoritative for all validation.

## 7. UI and UX Requirements

**Visual and interaction consistency with existing TFHUB views is mandatory.**

Before implementation, inspect existing forms, modals, dynamic collections, athlete selectors, inputs, buttons, and validation messages.

Reuse existing components and conventions for:
- Form layout and headings.
- Text inputs and date pickers.
- Select controls.
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

The interface must remain usable on mobile devices, even when a training contains multiple definitions and segments.

All user-facing text must be in Polish.

## 8. Submission

- Submit using the actual backend UC-502 request DTO. TEMPO must not include assignment-mode changes or athlete assignments. FREE_TEXT must submit exactly one valid assignment target mode and no participation/execution data.
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
- Form validation works correctly.
- Successful submission creates a training plan without athlete participations through UC-502.
- Successful FREE_TEXT submission creates an ordered section-based training with the selected group or individual-athlete assignment and without tempo participation/execution records.
- Successful creation redirects to training details.
- Failed submission preserves entered data.
- UI matches existing TFHUB forms and controls.
- The view is responsive and usable on mobile.
- Existing frontend functionality remains unaffected.