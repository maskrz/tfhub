# FE-TRAINING-001 – List Trainings

## 1. Purpose

Provide a frontend view allowing authenticated users to browse training sessions they are authorized to access.

The view must follow the existing TFHUB visual design and interaction patterns.

## 2. Actors

- **ADMIN** – can view all planned trainings and may include completed trainings.
- **COACH** – can view trainings belonging to groups assigned to that coach and may include completed trainings.

## 3. Preconditions

- The user is authenticated.
- The backend UC-501 endpoint is available.

## 4. Main Flow

1. The user opens the **Treningi** section from the application navigation.
2. The frontend retrieves the training list from the backend UC-501 endpoint.
3. The view displays the available trainings.
4. The user can browse the list and use the available search and filtering controls.
5. The user selects a training to open its details (FE-TRAINING-004).
6. For any training, the user can navigate directly to **Edytuj trening** (FE-TRAINING-003) or **Uzupełnij trening** (FE-TRAINING-009).
7. The user can initiate permanent deletion of a training from its row (FE-TRAINING-005).
8. The user can initiate training creation (FE-TRAINING-002), subject to the existing role permissions.
9. The user can start an OCR import by selecting **Importuj ze zdjęcia** (FE-TRAINING-006).

## 5. Displayed Information

Each training list item must display:

- Training name.
- Training date.
- Training group.
- Number of participants.

Use the actual response DTO returned by the implemented UC-501 endpoint. Do not invent or duplicate data that is not provided by the backend.

Each item includes its status. The default filter displays all trainings; users can select planned or completed trainings.

## 6. Search, Filtering, and Sorting

- Provide search and filtering only where appropriate for the available training data and consistent with existing TFHUB list views.
- Reuse the same interaction patterns, controls, visual styling, and behavior as existing search and filtering controls.
- Do not introduce a new filtering UX or custom control style.
- Do not implement filtering that requires unsupported backend functionality. If filtering is performed locally, it must operate on the data actually loaded by the endpoint.
- Follow existing list sorting conventions where applicable.
- Provide a status filter with **Planowane**, **Zakończone**, and **Wszystkie statusy** options, following existing TFHUB status-filter patterns.
- Load completed trainings through UC-501's `includeCompleted` option; apply the selected status filter to the returned data.

## 7. Navigation and Actions

- Selecting a training opens its details view (FE-TRAINING-004).
- Provide an action to create a new training (FE-TRAINING-002) for authorized users.
- Provide **Importuj ze zdjęcia** to start FE-TRAINING-006 for ADMIN and COACH users.
- Provide separate **Edytuj trening** and **Uzupełnij trening** links for all list items. These links only navigate to FE-TRAINING-003 and FE-TRAINING-009; authorization remains backend-enforced.
- Display the planned/completed state as a read-only status label.
- Provide **Usuń** for all items (FE-TRAINING-005), requiring explicit confirmation before permanent deletion.
- Use a blank, accessible actions-column header rather than displaying the text **Akcje**.

## 8. UI and UX Requirements

**Visual and interaction consistency with existing TFHUB views is mandatory.**

Before implementation, inspect existing list views, especially athlete and test lists.

Reuse their established patterns for:
- Page layout and headings.
- Tables, cards, or list rows.
- Search fields and filters.
- Dropdowns and select controls.
- Buttons and action placement.
- Loading indicators.
- Empty states.
- Error messages.
- Pagination, if used by existing list views.
- Responsive behavior and mobile layout.

Do not redesign existing controls or introduce new visual conventions.

All user-facing labels, messages, buttons, and validation text must be in Polish.

The view must be mobile-first and consistent with the existing TFHUB application.

## 9. Loading, Empty, and Error States

- Display the existing application loading indicator while retrieving data.
- Display a consistent empty state when no trainings are available or match the current filters.
- Display errors using the existing application error-handling and notification patterns.
- Handle failed requests without breaking the rest of the application.

## 10. Authorization

- The frontend must respect the authenticated user's role and available actions.
- Do not rely on frontend authorization as a security mechanism. The backend remains authoritative.
- Do not display trainings that the backend did not return.

## 11. Out of Scope

- Creating, editing, completing, or importing training data; import upload behavior is specified in FE-TRAINING-006 and deletion behavior in FE-TRAINING-005.
- Training details implementation.
- Any backend changes.

## 12. Acceptance Criteria

- The Treningi section is accessible from the application navigation.
- The list is loaded from the implemented UC-501 endpoint.
- All returned trainings display the required information.
- Search and filters follow existing TFHUB patterns and work correctly.
- Selecting a training navigates to its details.
- Each listed training provides distinct links to edit its plan and complete its athlete participation/results.
- Users can filter planned, completed, or all returned trainings; the default is all statuses.
- Planned and completed list items display their status and offer edit/completion actions; both statuses can be deleted after confirmation.
- Authorized users can initiate training creation.
- Loading, empty, and error states are handled.
- The view is responsive and usable on mobile devices.
- The visual style and behavior of controls match existing TFHUB views.
- Existing frontend functionality remains unaffected.