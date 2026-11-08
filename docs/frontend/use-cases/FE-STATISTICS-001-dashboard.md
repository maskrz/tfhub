# UC-301: Dashboard

## 1. Goal

Implement the TFHUB main dashboard.

The dashboard provides:
- Quick access to the most frequently used actions.
- The number of active athletes.
- Club records for a selected test and season.
- The latest recorded results.

All statistical data must be retrieved from the backend statistics module. The frontend must not download the complete results collection or calculate club rankings locally.

## 2. Layout

The dashboard must follow the existing TFHUB visual guidelines and the approved dashboard mockup.

### 2.1. Page Header

At the top of the main content:
- Title: `Panel główny`

Use the existing shared application shell. Do not create another header or footer.

### 2.2. Quick Actions

Display three primary action buttons near the top of the dashboard:

1. `Dodaj wynik`
2. `Dodaj grupę wyników`
3. `Dodaj zawodnika`

Behavior:
- Add result opens the existing single-result form modal.
- Add group results opens the existing multiple-results form modal.
- Add athlete opens the existing athlete form modal.
- Reuse existing components and services. Do not duplicate forms or business logic.
- After a successful operation, refresh the dashboard data affected by the operation.

Layout:
- Display buttons next to each other on desktop.
- Allow wrapping or stacking on smaller screens.
- Maintain consistent spacing and button styles.

### 2.3. Active Athletes

Display a compact summary card containing:
- Label: `Aktywni zawodnicy`
- Active athlete count.

The value comes from the dashboard overview API.

Clicking the card navigates to the existing Athletes view.

Do not calculate the count in the frontend.

### 2.4. Club Records

Display a section titled:

`Rekordy klubowe`

Above the records tables, display two selectors:

**Test selector**
- Populate options using `availableTests` from the dashboard overview response.
- Display the test name.
- Use the test ID as the option value.
- Include inactive tests returned by the backend.
- When the dashboard loads, select the first available test.
- When the selected test changes, retrieve club records again.

**Season selector**
- Default option: `Wszystkie sezony`.
- Populate the remaining options using `availableSeasons`.
- Use the selected calendar year as the option value.
- When the season changes, retrieve club records again.
- When `Wszystkie sezony` is selected, omit the `season` query parameter.

**Records tables**

Display two separate tables:
- `Kobiety` — records for `FEMALE`.
- `Mężczyźni` — records for `MALE`.

Desktop layout:
- Display both tables side by side in two equal-width columns.

Mobile layout:
- Stack the tables vertically, one below the other.
- Preserve the same table structure and information.
- Avoid horizontal page overflow.

Each table must contain exactly four columns:

| Column | Description |
|---|---|
| Miejsce | Ranking position |
| Zawodnik | Athlete full name |
| Wynik | Result value and unit |
| Data | Result date |

Behavior:
- Display records in the order returned by the backend.
- Do not calculate rankings or select best results in Angular.
- Display ties using the place value returned by the backend.
- Clicking an athlete navigates to the athlete profile, if the profile route is available. Do not invent a route or implement the athlete profile as part of this use case.
- Both training and competition results are included by the backend.
- Inactive athletes must remain visible.

**Empty states**
- If there are no available tests, display an informative empty state instead of the selectors and tables.
- If the selected test and season have no records, display an informative empty state in both tables.
- Do not display fake or placeholder records.

### 2.5. Recent Results

Display a section titled:

`Ostatnie wyniki`

Display the latest 10 results returned by the dashboard overview endpoint.

Columns:

| Column | Description |
|---|---|
| Data | Result date |
| Zawodnik | Athlete full name |
| Test | Test name |
| Wynik | Result value and unit |

Behavior:
- Preserve the order returned by the backend.
- Do not fetch the complete results collection.
- Clicking an athlete navigates to the athlete profile, if the profile route is available.
- Include results belonging to inactive athletes and inactive tests.
- Both TRAINING and COMPETITION results are displayed.

If there are no results, display an informative empty state.

## 3. Backend Integration

Use the existing backend statistics API.

### 3.1. Dashboard Overview

`GET /api/statistics/dashboard`

The response provides:
- `activeAthleteCount`
- `availableTests`
- `availableSeasons`
- `recentResults`

Use the actual response DTO and field names implemented in the backend.

Load the overview when the dashboard is initialized.

### 3.2. Club Records

`GET /api/statistics/records?testId={testId}&season={season}`

Parameters:
- `testId` — required.
- `season` — optional.

Use the actual response DTO implemented in the backend.

The response separates records by gender using the existing gender enum:
- `FEMALE`
- `MALE`

Map these values to the Polish section labels:
- `FEMALE` → `Kobiety`
- `MALE` → `Mężczyźni`

Do not expect `women` or `men` response properties.

Retrieve records:
- After the initial overview has loaded and a test is selected.
- Whenever the selected test changes.
- Whenever the selected season changes.

Do not retrieve records if there is no available test.

## 4. Data Loading and State Management

- Load dashboard overview once when the dashboard is initialized.
- Load club records only after a valid test has been selected.
- Do not make API requests when the user changes unrelated UI state.
- Show loading indicators while requests are in progress.
- Prevent stale responses from overwriting data for a newer test/season selection.
- Handle API errors using existing application error-handling conventions.
- Do not display misleading zero values when data has not loaded or an API request has failed.
- Preserve selected test and season when refreshing dashboard data, provided the selected options still exist.
- After adding or editing data through quick actions, refresh only the dashboard data affected by that operation.

## 5. Responsive Design

Desktop:
- Quick actions arranged horizontally.
- Active athletes summary displayed compactly.
- Test and season selectors displayed above club records.
- Women's and men's records tables displayed side by side.
- Recent results displayed below club records.

Mobile:
- Quick actions adapt to the available width.
- Active athletes summary remains readable.
- Selectors adapt to the available width.
- Women's and men's tables are stacked vertically.
- Recent results remain readable without horizontal page overflow.
- Maintain appropriate spacing between sections.

## 6. Out of Scope

Do not implement:
- Athlete profile functionality.
- Athlete personal bests.
- Training statistics or training planning.
- Charts or progress graphs.
- Additional statistical calculations in the frontend.
- New backend endpoints.
- Authentication or authorization changes.
- Pagination for dashboard data.

## 7. Acceptance Criteria

- [ ] Dashboard is available as the main application view.
- [ ] Dashboard follows the approved visual layout and existing UI guidelines.
- [ ] Three quick actions open the existing forms.
- [ ] Active athlete count is retrieved from the backend.
- [ ] Clicking the active athletes card opens the existing Athletes view.
- [ ] Test selector uses `availableTests` from the overview response.
- [ ] Season selector uses `availableSeasons` and defaults to all time.
- [ ] Club records are retrieved using the selected test and optional season.
- [ ] Records are separated using `FEMALE` and `MALE`.
- [ ] Women and men tables are displayed side by side on desktop.
- [ ] Women and men tables are stacked on mobile.
- [ ] Both tables have exactly four columns: Miejsce, Zawodnik, Wynik, Data.
- [ ] Ranking is displayed exactly as returned by the backend.
- [ ] Recent results display no more than 10 entries.
- [ ] Recent results have four columns: Data, Zawodnik, Test, Wynik.
- [ ] No complete results collection is downloaded.
- [ ] No ranking or best-result calculations are performed in Angular.
- [ ] Loading, empty and error states are handled.
- [ ] Existing CRUD functionality remains unchanged.
- [ ] No unnecessary API requests are made.
- [ ] Desktop and mobile layouts are usable.