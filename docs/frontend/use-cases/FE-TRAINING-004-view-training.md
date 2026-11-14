# FE-TRAINING-004 – View Training

## 1. Purpose

Display complete training details, including type, immutable training group and type-specific content. TEMPO details include athlete assignments and execution data; FREE_TEXT details show ordered sections and do not expose athlete participation.

## 2. Actors and Preconditions

- **ADMIN** – can view any existing training.
- **COACH** – can view a training only while currently assigned to its group.
- The user is authenticated and backend UC-504 is available.

## 3. Main Flow

1. The user opens a training from the list.
2. The frontend retrieves complete details using UC-504.
3. The view displays training name, date, type, status and group, then either ordered TEMPO definitions/segments and executions or ordered FREE_TEXT sections.
4. TEMPO users may select **Edytuj trening** (FE-TRAINING-003) or **Uzupełnij trening** (FE-TRAINING-009). FREE_TEXT users may edit sections and mark a planned training completed through `PATCH /api/trainings/{trainingId}/complete`.
5. Planned and completed trainings remain editable by authorized users. They can permanently delete the training from this view (FE-TRAINING-005).

## 4. Display and Navigation Requirements

- Display historical participants and all execution statuses regardless of athletes' current group assignments.
- Preserve definition and segment ordering and associate executions with the correct athlete and segment.
- Display rest in minutes and remaining seconds (for example `4m 10s`); show a dash when no rest was specified.
- Empty collections must render normally.
- The group is read-only.
- FREE_TEXT sections are displayed in their saved order and no athlete-participation controls are shown.
- Provide separate actions for plan editing and assigning/recording athlete participation. Do not combine them into one edit action.
- Offer **Usuń trening** for planned and completed sessions with explicit confirmation (FE-TRAINING-005).
- Backend authorization remains authoritative.

## 5. UI and Error Handling

Follow existing TFHub view, loading, empty, error, and responsive patterns. All user-facing text is in Polish. Do not expose details for trainings that the user is not authorized to view.

## 6. Out of Scope

- Editing the plan (FE-TRAINING-003).
- Assigning athletes or recording results (FE-TRAINING-009).
- Deletion (FE-TRAINING-005) and imports.

## 7. Acceptance Criteria

1. Authorized users see complete details, including historical participants and executions.
2. Definition and segment order is preserved.
3. Planned and completed trainings expose distinct **Edytuj trening** and **Uzupełnij trening** actions.
4. Planned and completed trainings can be deleted after confirmation.
5. The view handles empty, loading and error states and remains usable on mobile.
6. FREE_TEXT details are visible only to authorized staff and display ordered sections without athlete data.
