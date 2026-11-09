# FE-ATHLETE-001 — View Athletes

**Status:** Implemented
**Module:** Frontend / Athlete Management
**Related backend functionality:** Search athletes and change athlete status

## Goal
Allow the authenticated user to browse and manage the list of athletes.

The view is strictly a management interface. It does not provide athlete profiles, training history, test results, or statistics.

## Entry Point
The user selects "Zawodnicy" from the main application navigation.

## Main Flow
1. The user opens the Athletes view.
2. The application retrieves the complete athlete list once using `POST /api/athletes/search` with an empty `SearchAthletesRequest`.
3. The view displays:
    - Page title: "Zawodnicy"
    - Subtitle: "Zarządzaj listą zawodników"
    - "Dodaj zawodnika" primary action
    - Search input
    - Gender filter
    - Training-group multi-select filter
    - Birth-year range filters
    - "Pokaż nieaktywnych" toggle
    - Athletes table
5. The table displays each athlete's current training group, or "Bez grupy" for an unassigned athlete.
6. By default, only active athletes are displayed.
7. The user can search by first name, last name, or Polish gender label.
8. Gender can also be selected from a dedicated filter.
9. The user can filter by one or more accessible training groups; an empty selection means all accessible groups.
10. Administrators can additionally filter for athletes without an assigned group.
11. The user can filter using an inclusive birth-year range with optional lower and upper bounds.
12. The user can enable "Pokaż nieaktywnych" to include inactive athletes.
13. Search, gender, group, birth-year range, and active-status filtering work together.
14. The user can sort by athlete name, birth year, or gender using the table headers.
15. The "Dodaj zawodnika" action opens FE-ATHLETE-002.
16. Each row provides a status toggle and an "Edytuj" action that opens FE-ATHLETE-003.

## Table Columns

| Column | Description |
|---|---|
| Zawodnik | First name and last name |
| Rok urodzenia | Athlete's birth year |
| Płeć | Athlete's gender |
| Grupa treningowa | Current group name, or "Bez grupy" |
| Akcje | Active/inactive status toggle and "Edytuj" action |

The name, birth-year, and gender headers are sortable. The initial order is athlete surname ascending, then first name ascending; selecting the active header reverses the order. Name and gender use Polish locale-aware comparison.

## Search and Filtering
- Search by first name, last name, and Polish gender label.
- Search is case-insensitive.
- Leading and trailing whitespace is ignored.
- The gender dropdown supports all athletes, men, or women.
- Birth year can be constrained by optional "Od" and "Do" inclusive bounds.
- Search, gender, birth-year range, and inactive filtering are combined.
- Group filtering supports multiple groups and is combined with all other filters. Administrators can include unassigned athletes; coaches only see groups assigned to them.
- Sorting is local and can be used with every filter. Name sorting compares surname first and first name as a tie-breaker.
- Default view displays active athletes only.
- Search, gender, group, birth-year range, status filtering, and sorting are performed locally; changing them does not send additional requests.
- The page loads the accessible training groups once to display names and populate the group filter.
- Search terms are trimmed, case-insensitive, and can match first and last name in any order.

Prefer local filtering after the initial API request if the existing API supports retrieving all athletes, including inactive ones.

Do not issue additional API requests for every search input change or filter toggle.

Inspect the actual backend API contract before implementation. Do not assume endpoint paths or parameter names.

If the API cannot provide all required data in a single initial request, report the limitation rather than modifying the backend.

## Athlete Status Management
- Status is changed directly from the table using a toggle.
- Active state is represented by a green toggle.
- Inactive state is represented by a neutral grey toggle.
- The UI must clearly communicate the current state.
- Use the existing backend activation/deactivation API operations.
- Update the local list after a successful operation.
- On API failure, restore the previous state and display a user-friendly error.
- Do not physically delete athletes.

## Add and Edit Entry Points
- "Dodaj zawodnika" opens the CREATE modal and shared form defined in FE-ATHLETE-002.
- "Edytuj" opens the selected athlete in the shared EDIT modal defined in FE-ATHLETE-003.

## API Contract

- `POST /api/athletes/search` with `{}` returns `AthleteResponse[]`, including active and inactive athletes when no criteria are specified.
- `PATCH /api/athletes/{id}/status` accepts `ChangeAthleteStatusRequest` (`{ "active": boolean }`) and returns the updated `AthleteResponse`.
- `AthleteResponse` includes nullable `trainingGroupId`; the page uses the authenticated user's accessible groups to display group names.

## UI States
Support:
- Loading state.
- Empty state (no athletes).
- No search results state.
- API error state with retry.
- Unauthorized state, consistent with existing authentication handling.

## Design and Layout
- Follow the existing TFHUB UI guidelines.
- Reuse the shared application shell.
- Use the same visual conventions as the Test Management view.
- Keep the table as the main element of the page.
- Use the shared primary and secondary button styles.
- Ensure responsive desktop and mobile layouts.
- Use Polish UI labels.

## Future Scope — Explicitly Excluded
The view is currently only for athlete management.

Do not implement:
- Athlete profile or detail page.
- Athlete performance dashboard.
- Test results or training history.
- Athlete statistics.
- Navigation to an athlete profile.
- Links to other athlete-related modules.

The table structure should remain compatible with a future athlete profile entry point, but no such navigation should be implemented now.

## Acceptance Criteria
- The Athletes view is accessible through the main navigation.
- The view displays the athlete management list.
- Only active athletes are displayed by default.
- The user can include inactive athletes.
- The list is loaded once per view initialization and contains inactive athletes.
- Search works by first name, last name, and gender label.
- Gender and birth-year filters work together with search and status filtering.
- The current group is shown for each athlete.
- Multiple group filters work together with the existing local filters. Administrators can select "Bez grupy"; coaches only receive their assigned group options.
- Sorting by surname then first name, birth year, and gender works locally and together with filters.
- Each athlete has an Edit action.
- Each athlete has an activation/deactivation toggle.
- Status changes use the existing backend API.
- Failed status changes do not leave the UI in an incorrect state.
- Add opens the FE-ATHLETE-002 modal; Edit opens the FE-ATHLETE-003 modal.
- Loading, empty, error, and no-results states are supported.
- No athlete profile or performance functionality is introduced.