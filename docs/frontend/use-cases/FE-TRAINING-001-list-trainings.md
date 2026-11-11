# FE-TRAINING-001 – List Trainings

## 1. Purpose

Provide a frontend view allowing authenticated users to browse training sessions they are authorized to access.

The view must follow the existing TFHUB visual design and interaction patterns.

## 2. Actors

- **ADMIN** – can view all active trainings.
- **COACH** – can view active trainings belonging to groups assigned to that coach.

## 3. Preconditions

- The user is authenticated.
- The backend UC-501 endpoint is available.

## 4. Main Flow

1. The user opens the **Treningi** section from the application navigation.
2. The frontend retrieves the training list from the backend UC-501 endpoint.
3. The view displays the available trainings.
4. The user can browse the list and use the available search and filtering controls.
5. The user selects a training to open its details (FE-TRAINING-004).
6. The user can initiate training creation (FE-TRAINING-002), subject to the existing role permissions.

## 5. Displayed Information

Each training list item must display:

- Training name.
- Training date.
- Training group.
- Number of participants.

Use the actual response DTO returned by the implemented UC-501 endpoint. Do not invent or duplicate data that is not provided by the backend.

Only active trainings are displayed. Filtering by active/inactive status is not required.

## 6. Search, Filtering, and Sorting

- Provide search and filtering only where appropriate for the available training data and consistent with existing TFHUB list views.
- Reuse the same interaction patterns, controls, visual styling, and behavior as existing search and filtering controls.
- Do not introduce a new filtering UX or custom control style.
- Do not implement filtering that requires unsupported backend functionality. If filtering is performed locally, it must operate on the data actually loaded by the endpoint.
- Follow existing list sorting conventions where applicable.

## 7. Navigation and Actions

- Selecting a training opens its details view (FE-TRAINING-004).
- Provide an action to create a new training (FE-TRAINING-002) for authorized users.
- Do not implement editing or deactivation actions in this use case unless an existing shared navigation pattern requires a simple link to another view.

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

- Creating, editing, or deactivating trainings.
- Training details implementation.
- OCR and training imports.
- Any backend changes.

## 12. Acceptance Criteria

- The Treningi section is accessible from the application navigation.
- The list is loaded from the implemented UC-501 endpoint.
- All returned trainings display the required information.
- Search and filters follow existing TFHUB patterns and work correctly.
- Selecting a training navigates to its details.
- Authorized users can initiate training creation.
- Loading, empty, and error states are handled.
- The view is responsive and usable on mobile devices.
- The visual style and behavior of controls match existing TFHUB views.
- Existing frontend functionality remains unaffected.