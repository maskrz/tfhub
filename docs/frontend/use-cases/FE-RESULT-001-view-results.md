Implement **FE-RESULT-001 – View Results** for the TFHUB frontend.

## 1. Start by inspecting the project

Before making changes, read and understand:

- `.github/copilot-instructions.md`
- Frontend architecture, requirements and UI guidelines.
- Existing frontend use cases for Athletes and Tests.
- Existing Results backend implementation and documentation, especially:
    - Backend UC-204 – Get Results.
    - Result entity and response DTOs.
    - Result controller, service and repository.
    - Existing API endpoint and supported query parameters.

Follow the existing project conventions. Reuse existing components, services, styles and patterns wherever appropriate.

**Important:**
- Do not invent API endpoints, request parameters or response fields.
- Do not modify the backend.
- Do not implement Add, Edit or Delete functionality in this task.
- Do not introduce unnecessary abstractions or dependencies.
- If the backend API does not provide enough information to implement the view, stop and report the limitation before making backend changes.

## 2. Results view

Create a Results view consistent with the existing Athletes and Tests views.

Page heading:
- Title: `Wyniki`
- Subtitle: `Przeglądaj i analizuj wyniki sportowe`

The main element should be a table displaying results.

### Table columns

Use this exact order:

1. **Data** – result date.
2. **Zawodnik** – athlete's first and last name.
3. **Test** – test name.
4. **Wynik** – numeric value followed by its unit.
5. **Rodzaj** – result type.
6. **Akcje** – Edit and Delete actions.

Display numeric values consistently, respecting the precision returned by the backend. Use Polish locale formatting where appropriate.

Translate result types:
- `TRAINING` → `Trening`
- `COMPETITION` → `Zawody`

The default sorting should be by date, newest first.

Historical results must remain visible even if the associated athlete or test is currently inactive. Do not filter them out based on current active status.

### Filters

Place filters above the table:

- A text search field searching across athlete's name and test name.
- Athlete dropdown.
- Test dropdown.
- Date range filter (from / to).
- Result type dropdown:
    - Wszystkie
    - Trening
    - Zawody

All filters should work together.

The default state should show all available results, without a date restriction or type restriction.

### Actions column

Display two actions for each row:
- `Edytuj`
- `Usuń`

Use the existing button styles and UI guidelines. Edit and Delete functionality will be implemented in separate use cases, so do not implement their actual workflows yet. Do not make the buttons appear functional if they are not wired to anything; use an appropriate temporary approach consistent with the project.

## 3. Data loading and performance

Initially, the application currently has approximately 200 results, with expected growth over time.

For this first implementation:
- Do not introduce pagination.
- Prefer loading the results collection once when entering the view.
- Apply search, filters and sorting locally in the frontend.
- Changing filters or sorting must not trigger additional API requests.
- Avoid unnecessary API calls when loading athlete/test dropdown options. Reuse data already available if appropriate.

However, inspect the actual backend query and response before choosing the implementation. Do not assume that the endpoint returns the entire history or that its response contains all required display fields.

If the current API cannot support this approach, report the limitation and propose the smallest reasonable solution. Do not change the backend yourself.

Keep the implementation straightforward and make it possible to introduce server-side filtering or pagination later without unnecessary rework.

## 4. UI and responsiveness

Follow the existing TFHUB visual design:
- Dark charcoal shared header.
- Light workspace.
- White table/content surface.
- Green primary accent.
- Subtle borders and restrained shadows.
- Consistent typography, spacing, button styles and rounded corners.
- Polish interface labels.
- Mobile-first responsive layout.

Reuse the existing application shell. Do not duplicate the header or footer.

Keep the view clean and practical. Do not add summary cards, charts or statistics.

## 5. UI states

Handle the standard states consistently with the existing views:

- Loading.
- Results available.
- No results in the database.
- No results matching the current filters.
- API error.
- Unauthorized response, if applicable.

Make sure clearing filters restores the complete result list.

## 6. Tests

Add or update frontend tests covering at least:

- Initial results loading.
- Default sorting by date descending.
- Text search across athlete and test names.
- Filtering by athlete.
- Filtering by test.
- Filtering by date range.
- Filtering by result type.
- Combining multiple filters.
- Clearing filters.
- Historical results linked to inactive athletes or tests remain visible.
- Changing filters and sorting does not trigger additional API requests.
- Empty, loading and error states.

Follow the testing conventions already used in the project.

## 7. Documentation

Create a detailed use case document for **FE-RESULT-001 – View Results**.

First inspect the existing frontend use case documentation structure and follow its naming and formatting conventions.

Document:
- Purpose and scope.
- User interaction and table columns.
- Filtering and sorting behavior.
- Data loading strategy.
- API integration based on the actual backend implementation.
- UI states.
- Validation and error handling.
- Explicitly excluded functionality (Add, Edit and Delete workflows).

Update any existing frontend documentation or UC catalog if the project uses one.

## 8. Final report

After implementation, report:

1. What was implemented.
2. Files created or modified.
3. Which backend endpoint and response DTO were used.
4. How filtering and sorting work.
5. Tests executed and their results.
6. Any limitations or deviations from this specification.

Do not commit changes.

Keep the scope strictly limited to FE-RESULT-001.

## Implementation notes

**Status:** Implemented

The Results view is available at `/results` and from the shared main navigation. It loads the full results collection and the athlete and test collections once in parallel when the view is entered:

- `GET /api/results` returns `ResultResponse[]` (`id`, `athleteId`, `testId`, `resultDate`, `value`, `type`) without query parameters.
- `POST /api/athletes/search` with `{}` returns `AthleteResponse[]`.
- `POST /api/tests/search` with `{}` returns `TestResponse[]`.

The result response does not contain athlete names, test names, or units, so the two existing search endpoints provide the display data and filter options. Their unfiltered responses include inactive records. Results are joined by IDs in the frontend and are not removed when the athlete or test is inactive.

Search across athlete and test names, athlete and test selection, inclusive date bounds, result type, and date sorting are all applied locally. Date sorting defaults to newest first, with descending result ID as the tie-breaker. Changing any filter, clearing filters, or changing sort direction does not make additional API requests.

The table displays the date, athlete, test, numeric value with the test unit, translated result type, and actions. Date-only values are formatted in the Polish locale; numeric values use Polish formatting with up to three fraction digits. On small screens, rows are presented as cards.

The actions column has a visually blank header, matching the Tests and Athletes tables; its header retains an accessible name.

Edit and Delete are shown as disabled secondary buttons with accessible labels and explanatory tooltips because those workflows belong to later use cases. Loading, empty, no-match, retryable API error, unauthorized, and forbidden states are handled in the view.