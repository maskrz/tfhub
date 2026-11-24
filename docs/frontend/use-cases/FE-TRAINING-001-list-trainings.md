# FE-TRAINING-001 – List Trainings

## 1. Purpose

Provide a frontend view allowing authenticated users to browse training sessions they are authorized to access.

The view must follow the existing TFHUB visual design and interaction patterns.

## 2. Actors

- **ADMIN** – can view all planned trainings and may include completed trainings.
- **COACH** – can view trainings belonging to groups assigned to that coach and may include completed trainings.
- **ATHLETE** – can view only FREE_TEXT trainings assigned to their current group or directly to them.
- For athletes, the backend omits group trainings on a date with an individual training assigned to that athlete, irrespective of status. Undated trainings are unaffected.

## 3. Preconditions

- The user is authenticated.
- The backend UC-501 endpoint is available.

## 4. Main Flow

1. The user opens the **Treningi** section from the application navigation.
2. The frontend retrieves the training list from the backend UC-501 endpoint.
3. The view displays the available trainings.
4. The user can browse the list and use the available search and filtering controls.
5. The user can switch between **Lista** and **Kalendarz** on the same page. **Lista** is selected by default.
6. In **Kalendarz**, the user browses one month at a time, selects a day, and opens a training's existing details view.
7. The user selects a training in **Lista** to open its details (FE-TRAINING-004).
8. The user opens the type-appropriate details view; only TEMPO trainings offer the tempo edit/completion flows, while FREE_TEXT trainings offer section editing and completion.
9. The user can select trainings and initiate bulk actions (FE-TRAINING-010), including permanent deletion (FE-TRAINING-005).
10. The user can initiate training creation (FE-TRAINING-002), subject to the existing role permissions.
11. The user can start an OCR import by selecting **Importuj ze zdjęcia** (FE-TRAINING-006).
12. The user can start a JSON training import or export selected trainings as specified by FE-TRAINING-011.
13. Active OCR imports are shown as separate rows above persisted trainings in **Lista**, independently of training filters and sorting.

## 5. Displayed Information

Each training list item must display:

- Training name.
- Training date.
- Assignment information: training group for TEMPO/GROUP mode, or individual-athlete assignment for FREE_TEXT/ATHLETES. The group may be null in ATHLETES mode.
- Selected-athlete count for ATHLETES-mode FREE_TEXT; do not present these targets as TEMPO participants.
- Training type.
- Number of participants for TEMPO; FREE_TEXT has no participant count.

Use the actual response DTO returned by the implemented UC-501 endpoint. Do not invent or duplicate data that is not provided by the backend.

Each item includes its status. The default filter displays all trainings; users can select planned or completed trainings.

Retrieve active import summaries separately from `GET /api/training-imports`. Show their group, processing status, duplicate warning, recognized name and training date when available, and a distinct import identifier; do not present them as persisted sessions. Accepted and rejected imports are not included. Pending values that are not yet recognized are displayed as unavailable. Failed imports display the safe failure message when available and provide retry. Review-required imports link to the draft review screen.

## 6. Search, Filtering, and Sorting

- Provide search and filtering only where appropriate for the available training data and consistent with existing TFHUB list views.
- Reuse the same interaction patterns, controls, visual styling, and behavior as existing search and filtering controls.
- Do not introduce a new filtering UX or custom control style.
- Do not implement filtering that requires unsupported backend functionality. If filtering is performed locally, it must operate on the data actually loaded by the endpoint.
- Follow existing list sorting conventions where applicable.
- Show `Bez daty` for undated plans. Undated sessions appear before dated sessions in both date-sort directions and are excluded while any date-range filter is active.
- Provide a status filter with **Planowane**, **Zakończone**, and **Wszystkie statusy** options, following existing TFHUB status-filter patterns.
- Provide a training-type filter for **Wszystkie typy**, **Trening tempowy**, and **Trening ogólny**.
- Load completed trainings through UC-501's `includeCompleted` option; apply the selected status filter to the returned data.
- Training search, filters and sorting do not hide or reorder active import rows.
- The group, status, and training-type filters are shared by **Lista** and **Kalendarz**. Their selected values remain unchanged when switching views or months.
- In **Kalendarz**, show only the group, status, and training-type filters; search and date-range filters remain available only in **Lista**. Their values are retained when switching views but do not filter Calendar.
- Calendar group options continue to come from the existing authorized training-list response. Group filtering remains local and must not broaden backend visibility.

## 6.1 Calendar View

- Display a compact monthly calendar with seven columns, Monday first, previous/next month controls, and **Dzisiaj**.
- Calendar cells show the day number, a training count when nonzero, and restrained planned/completed indicators. Do not show training names or descriptions inside cells.
- Selecting a day displays its trainings below the calendar. Each entry shows its name, status, and group when available, and links to the existing FE-TRAINING-004 details route.
- Show undated trainings in a separate **Bez daty** section below the selected-day list.
- When entering Calendar, select today if it is in the displayed month; otherwise select the first day with trainings, falling back to the first of the month.
- Calendar is for browsing only; it does not add or edit training sessions or change dates.

## 7. Navigation and Actions

- Selecting a training opens its details view (FE-TRAINING-004).
- Provide an action to create a new training (FE-TRAINING-002) for authorized users.
- Provide **Importuj ze zdjęcia** to start FE-TRAINING-006 for ADMIN and COACH users.
- Provide **Edytuj trening** and **Uzupełnij trening** only for TEMPO records; FREE_TEXT records navigate to their section editor and expose completion only while planned. Authorization remains backend-enforced.
- Display the planned/completed state as a read-only status label.
- Provide selection and **Akcje** above the list; deletion is available only through the bulk menu and requires explicit confirmation (FE-TRAINING-005).
- ATHLETE users have read-only access to visible FREE_TEXT list items and do not receive create, import, edit, complete or delete actions.
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
- Load imports independently of trainings. While any import is `PENDING`, `PROCESSING`, or `FAILED` with `nextRetryAt`, refresh only the import list every five seconds, without overlapping requests. Stop when no import meets these conditions or the view is left. Preserve the last successful import list and report refresh errors.
- Keep the existing full-list request for **Lista** and its group-filter options. On entering Calendar and when changing months, make one UC-501 request with `month=yyyy-MM`, `includeCompleted=true`, and `includeUndated=true`; do not request once per day. Apply the shared group, status, and training-type filters locally to the returned month and undated records.
- Calendar loading and errors use the existing feedback patterns; a failed month request can be retried without changing filters.

## 10. Authorization

- The frontend must respect the authenticated user's role and available actions.
- Do not rely on frontend authorization as a security mechanism. The backend remains authoritative.
- Do not display trainings that the backend did not return.
- The frontend must not infer visibility from group membership or locally broaden the backend result.

## 11. Out of Scope

- Creating, editing, completing, or importing training data; import upload behavior is specified in FE-TRAINING-006 and deletion behavior in FE-TRAINING-005.
- Training details implementation.
- Backend changes beyond the UC-501 month-and-undated query used by Calendar.

## 12. Acceptance Criteria

- The Treningi section is accessible from the application navigation.
- The list is loaded from the implemented UC-501 endpoint.
- All returned trainings display the required information.
- Search and filters follow existing TFHUB patterns and work correctly.
- Selecting a training navigates to its details.
- Each listed training provides distinct links to edit its plan and complete its athlete participation/results.
- Users can filter planned, completed, or all returned trainings; the default is all statuses.
- Users can filter both training types together or by type without changing the existing status behavior.
- Planned and completed list items display their status and offer edit/completion actions; both statuses can be selected for deletion after confirmation.
- Authorized users can initiate training creation.
- Loading, empty, and error states are handled.
- The view is responsive and usable on mobile devices.
- The visual style and behavior of controls match existing TFHUB views.
- Active imports are shown above trainings and refresh independently while processing or an automatic retry is scheduled.
- ATHLETE users see only their assigned FREE_TEXT trainings and have no staff-only training or import actions.
- Existing frontend functionality remains unaffected.
- The page defaults to **Lista** and switching views preserves the shared filters.
- Calendar month retrieval is limited to one month plus undated sessions in one request, with existing backend authorization enforced.
- Calendar shows Monday-first weekday alignment, per-day counts and status indicators, selected-day training details links, and undated sessions.
- Calendar remains usable on mobile without horizontal page scrolling.