# FE-TEST-001 — View Tests

**Status:** Implemented
**Module:** Frontend / Test Management
**Related backend functionality:** Retrieve tests

## 1. Goal

Allow an authenticated user to browse the list of tests, search for a test by name, and optionally display inactive tests.

The view must follow the shared TFHUB application layout and the approved Test Management visual reference.

## 2. Actor

Authenticated user.

## 3. Preconditions

- The user is authenticated.
- The application shell is loaded.
- The Test Management route is accessible.
- The backend API is available.

## 4. Trigger

The user selects "Testy" from the main navigation or navigates directly to the Test Management route.

## 5. Main flow

1. The user opens the Test Management view.
2. The application retrieves all available tests, active and inactive, once from the backend when the view is initialized.
3. By default, only active tests are displayed.
4. The page displays:
   - Page title: "Testy"
   - Subtitle: "Zarządzaj testami sportowymi"
   - Search input with a search icon
   - Category filter with "Wszystkie kategorie", "Czas", "Dystans", and "Prędkość"
   - Toggle: "Pokaż nieaktywne"
   - Test table
5. The table displays the tests matching the current search and filter criteria.
6. Each row displays the test name, Polish category label, and the actions:
   - "Edytuj"
   - A status switch showing "Aktywny" or "Nieaktywny"
7. The user can enter text in the search field to narrow down the locally stored tests by name.
8. The user can enable "Pokaż nieaktywne" to include inactive tests; this filtering is local.
9. The user can disable the toggle to return to displaying active tests only; this filtering is local.
10. The user can filter locally by category; category filtering works together with search and active status.
11. The user can sort by name or category by selecting the respective table header. Selecting the active header reverses its direction; selecting the other header sorts it ascending.
12. The user can select "Edytuj" to open the shared modal in EDIT mode (FE-TEST-003).
13. The user can change a test's status using the row action described by FE-TEST-005. The list item is updated locally after success.

## 6. Business and UI rules

- Only active tests are displayed by default.
- Inactive tests are displayed only when "Pokaż nieaktywne" is enabled.
- Search and inactive-test filtering must work together.
- Category filtering works with search and inactive-test filtering.
- Name and category headers sort locally, with name ascending as the default.
- Search, category selection, status filtering and sorting do not trigger additional API requests.
- Search must not change the active/inactive filter state.
- Inactive tests must have a subtle light-red row background.
- Active tests use the standard table background.
- The table displays the test name, Polish category label and its actions; no category or status subtitle is shown below the name.
- Each row has exactly two controls: the status switch followed by the secondary "Edytuj" button.
- The status switch communicates the current state visually, through its visible "Aktywny"/"Nieaktywny" label, and through accessible switch state.
- Status changes are independent of the create/edit form and change no test definition fields.
- The view must not contain summary/statistics cards.
- The table is the main content of the page.
- All user-facing text must be in Polish.
- Use only categories supported by the backend model: TIME, DISTANCE, SPEED. Display labels should be consistent with the existing frontend conventions or documented Polish translations.
- Do not invent additional test properties or backend rules.

## 7. Loading, empty and error states

### Loading
Display a clear loading state while the test list is being retrieved. Avoid showing an empty table before the request completes.

### Empty list
If no tests are available for the selected filter, display an informative empty state in Polish.

### No search results
If tests exist but none match the search query and current filter, display a message indicating that no matching tests were found.

### API error
Display a user-friendly error message in Polish. The user must be able to retry loading the list.

### Unauthorized response
Follow the authentication and session-handling rules in `docs/security.md` and the frontend architecture. Do not implement a separate authentication mechanism in this view.

## 8. Visual and layout requirements

- Use the shared application shell.
- Do not implement a separate header or footer inside the feature view.
- Follow `docs/frontend/ui-guidelines.md`.
- Follow `docs/frontend/views/test-management.md`.
- Use the screenshot at `docs/frontend/screenshots/test-management.png` as the visual reference for layout and presentation.
- The screenshot is illustrative; backend documentation remains the source of truth for domain rules and data values.
- The view must be responsive and usable on mobile devices.

## 9. Out of scope

- Adding a test (FE-TEST-002).
- Editing a test (FE-TEST-003).
- Deleting tests.
- Test result management.
- Dashboard statistics.
- Changes to backend domain rules or API contracts.

## 10. Acceptance criteria

- [x] The Test Management view is accessible through the shared application navigation.
- [x] The view is rendered inside the shared application shell.
- [x] The list is retrieved using the existing backend API.
- [x] Only active tests are displayed by default.
- [x] Enabling "Pokaż nieaktywne" includes inactive tests.
- [x] Disabling the toggle hides inactive tests again.
- [x] Search filters tests by name.
- [x] Search and inactive filtering work together.
- [x] Category filtering works with search and inactive filtering.
- [x] The name and category columns are sortable, defaulting to name ascending.
- [x] All filtering and sorting is local and causes no additional list request.
- [x] Active and inactive rows have the specified visual appearance.
- [x] Each row contains the status switch and secondary "Edytuj" button.
- [x] Loading, empty, no-results and API-error states are handled.
- [x] Unauthorized responses follow the existing authentication rules.
- [x] The view follows the approved screenshot and frontend UI guidelines.
- [x] The layout works on desktop and mobile.
- [x] Existing frontend functionality is not broken.

## 11. Implementation notes

- The backend does not expose a collection `GET /api/tests`. The page uses the existing `POST /api/tests/search` endpoint once with an empty `TestSearchRequest`; the backend returns all tests, active and inactive, when no effective criteria are supplied.
- The complete response is stored separately in frontend state. The visible list is derived locally from that data and the current search text and inactive toggle.
- Name matching is case-insensitive and trims leading and trailing whitespace. The default status filter displays only active tests; enabling "Pokaż nieaktywne" includes both statuses.
- Typing, clearing the search, and changing the inactive toggle do not trigger additional API requests.
- Responses use the existing `TestResponse` fields. The active status is retained for local filtering, row styling and the status switch state. Categories display localized labels in the table and filter.
- The Angular HttpClient XSRF configuration uses the documented `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header. Authentication itself remains planned; HTTP 401 and 403 errors are displayed as session/access errors without implementing a separate authentication flow here.
- Status changes use `PATCH /api/tests/{id}/status` with `ChangeTestStatusRequest`; the returned `TestResponse` replaces the item in the local collection.
- Add and edit open the same modal and Test Form component; see FE-TEST-002 and FE-TEST-003.
- No backend code, endpoint, DTO, or domain rule was changed.
- Automated frontend verification is maintained alongside the implementation tests.