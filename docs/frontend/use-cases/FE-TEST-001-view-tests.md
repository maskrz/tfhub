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
   - Toggle: "Pokaż nieaktywne"
   - Test table
5. The table displays the tests matching the current search and filter criteria.
6. Each row displays:
   - Test name
   - "Edytuj" action
7. The user can enter text in the search field to narrow down the locally stored tests by name.
8. The user can enable "Pokaż nieaktywne" to include inactive tests; this filtering is local.
9. The user can disable the toggle to return to displaying active tests only; this filtering is local.
10. The user can select "Edytuj" to navigate to the planned test editing route. The editing functionality is covered by FE-TEST-004 and is not part of this UC.

## 6. Business and UI rules

- Only active tests are displayed by default.
- Inactive tests are displayed only when "Pokaż nieaktywne" is enabled.
- Search and inactive-test filtering must work together.
- Search must not change the active/inactive filter state.
- Inactive tests must have a subtle light-red row background.
- Active tests use the standard table background.
- The table displays only the test name and the "Edytuj" action; category and status subtitles are not shown.
- Each row has exactly one visible action: "Edytuj".
- There must be no Activate/Deactivate action in the table.
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
- Editing a test (FE-TEST-004).
- Activating or deactivating a test.
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
- [x] Active and inactive rows have the specified visual appearance.
- [x] Each row contains exactly one "Edytuj" action.
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
- Responses use the existing `TestResponse` fields. The active status is retained for local filtering and row styling; only the test name and edit action are displayed in each row.
- The Angular HttpClient XSRF configuration uses the documented `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header. Authentication itself remains planned; HTTP 401 and 403 errors are displayed as session/access errors without implementing a separate authentication flow here.
- The editing route is registered as `/tests/:id/edit` with an informational placeholder only. The editing form is out of scope and remains part of FE-TEST-004.
- No backend code, endpoint, DTO, or domain rule was changed.
- Automated frontend verification is maintained alongside the implementation tests.