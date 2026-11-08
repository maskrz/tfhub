# FE-ATHLETE-004 — Athlete Profile

## Goal

Implement an athlete profile view that allows the coach to:
- View and edit basic athlete information.
- Add a new result for the athlete.
- Browse the athlete's historical results.
- Filter results by tests and date range.
- Analyze performance progress using a chart.

## Entry points and navigation

The athlete profile must be accessible by clicking an athlete from:
- Athletes list.
- Dashboard club records.
- Results list.

All entry points must navigate to the same athlete profile view.

Use the athlete ID in the route. Do not rely on the athlete's name as an identifier.

## Page layout

The page consists of the following sections, in this order:

1. Athlete information.
2. Results filters.
3. Results matrix.
4. Progress chart.

Follow the existing TFHUB application layout, shared header, footer, typography, colors, spacing, and component guidelines.

All UI text must be in Polish.

## 1. Athlete information

Display:
- First name and last name.
- Birth year.
- Gender.
- Active/inactive status.

Actions:
- **Edytuj zawodnika** — open the existing athlete form in edit mode.
- **Dodaj wynik** — open the existing single-result form, with the current athlete preselected.

Requirements:
- Reuse existing forms and API operations.
- After editing, update the displayed athlete information without reloading the page.
- After successfully adding a result, refresh the available test list and the displayed results if the current filters include the new result.
- Preserve the current page state where possible.

## 2. Results filters

Display:
- Multi-select test dropdown.
- Start date.
- End date.
- **Wczytaj wyniki** button.

### Test selection

Populate the dropdown using the backend operation that returns tests for which this athlete has at least one result.

Requirements:
- Include tests with historical results even if they are inactive.
- Do not show tests for which the athlete has no results.
- Allow selecting multiple tests.
- Display test names and units.
- Do not assume that only active tests are available.

### Date range

Allow the coach to specify an optional start and end date.

Validate that the start date is not later than the end date.

### Loading results

Results must not be fetched automatically whenever a filter changes.

Fetch results only after the coach clicks **Wczytaj wyniki**.

Send the selected test IDs and optional date range to the backend.

Show an appropriate loading state while fetching data.

If no results match the selected filters, display a clear empty state.

If the request fails, show an error message and do not silently replace previously loaded results with empty data.

## 3. Results matrix

Display a table representing the selected tests and their historical results.

### Structure

- Rows represent result dates.
- Columns represent selected tests.
- First column contains the date.
- Each remaining column represents one selected test.
- Cell values contain the recorded result and its unit.

### Ordering

- Dates must be sorted ascending (oldest first).
- The newest date must appear at the bottom.
- Test columns should follow the order selected by the coach, where practical.

### Multiple results on the same date

The backend allows multiple results for the same athlete, test, and date.

If multiple results exist for the same test and date:
- Display all of them in the same cell.
- Keep each result visually distinguishable.
- Each individual value must remain editable separately.

Do not aggregate, average, or discard duplicate results.

If a test has no result on a given date, display an empty cell.

### Editing results

- Desktop: double-click an individual result value to open the existing Edit Result modal.
- Mobile: a single tap on an individual result value must open the same modal.
- Reuse the existing result form and update operation.
- After a successful update, refresh the affected matrix data and chart.
- Preserve the currently selected tests and date range.

Do not add a delete action to the athlete profile.

## 4. Progress chart

Display one chart below the results matrix.

The chart visualizes the results of all currently selected tests.

### X-axis

- Use actual calendar dates.
- Dates must have proportional spacing.
- For example, 01.01 and 03.01 must be much closer together than 01.01 and 31.01.
- Do not use equally spaced categorical date labels.

### Y-axis and units

Tests can have different units, currently:
- Seconds (s).
- Meters (m).
- Meters per second (m/s).

Default behavior:
- Tests sharing the same unit use one common Y-axis and scale.
- Different units use separate Y-axes.
- Each axis must clearly display its unit.
- Each test is represented by a separate series with a distinguishable color.
- Use actual recorded values; do not normalize or convert the underlying results.

Automatically calculate readable axis ranges based on the displayed results. Include appropriate padding and readable tick intervals.

### Separating scales manually

The coach must be able to separate selected test series from the common scale into an additional scale.

Interaction:
1. The chart legend allows selecting one or more test series.
2. When at least one series is selected, display an action such as **Oddziel skalę**.
3. Clicking the action creates a separate Y-axis for the selected series.
4. The selected series are removed from their previous shared scale.
5. The remaining series continue using the original scale.
6. The new scale uses the same unit as the selected tests.
7. Each scale must have a clearly visible unit and range.
8. The coach must be able to restore the shared scale using an action such as **Połącz z pozostałymi**.

Important:
- This is not mathematical normalization or conversion to percentages.
- Values remain in their original units.
- Separating a scale changes only the chart presentation.
- Tests with different units must never share the same Y-axis.
- Only tests with the same unit can be grouped on one scale.
- The chart must clearly communicate which series belong to which scale.

### Chart behavior

- The chart must update when the loaded result set changes.
- It must reflect edits made to individual results.
- Each test should have a distinct and consistent color in the chart and legend.
- Missing results on a date must not be interpreted as zero.
- Keep the chart readable on desktop and mobile.
- If there are no loaded results, display an appropriate empty state instead of an empty chart.

## Data and API requirements

Inspect the existing backend API and the UC-006 implementation.

Use actual endpoint paths, request parameters, and response DTO fields. Do not invent API contracts.

Reuse:
- Existing athlete retrieval and update operations.
- Existing single-result creation and update operations.
- The new backend operations for retrieving tests with results and filtered athlete results.

Do not modify backend code as part of this frontend UC.

## Technical requirements

- Follow existing Angular architecture and component conventions.
- Reuse existing forms, modals, services, and shared components.
- Avoid duplicating result form logic.
- Keep filtering and result presentation logic separate from API communication.
- Do not introduce NgRx or another state-management library.
- Avoid unnecessary API requests.
- Handle loading, empty, and error states.
- Ensure responsive behavior for desktop and mobile.
- Add appropriate automated tests.

## Acceptance criteria

- [ ] Athlete profile can be opened from the Athletes list.
- [ ] Athlete profile can be opened from dashboard club records.
- [ ] Athlete profile can be opened from the Results list.
- [ ] All entry points open the same profile for the same athlete ID.
- [ ] Athlete information is displayed correctly.
- [ ] Athlete information can be edited using the existing form.
- [ ] A result can be added with the current athlete preselected.
- [ ] Test dropdown includes all tests with historical results, including inactive tests.
- [ ] Multiple tests can be selected.
- [ ] Optional start and end dates can be specified.
- [ ] Results are fetched only after clicking Wczytaj wyniki.
- [ ] Results matrix displays dates as rows and tests as columns.
- [ ] Dates are ordered oldest to newest.
- [ ] Multiple results for the same test and date are displayed separately in one cell.
- [ ] Each individual result can be edited.
- [ ] Desktop editing uses double-click.
- [ ] Mobile editing uses a single tap.
- [ ] No delete action is available in the profile.
- [ ] The chart uses actual dates with proportional X-axis spacing.
- [ ] Tests with the same unit share a scale by default.
- [ ] Tests with different units use separate scales.
- [ ] Selected series can be moved to a separate scale.
- [ ] Separate scales can be merged back into the shared scale.
- [ ] Chart values remain in their original units.
- [ ] Editing a result updates the matrix and chart.
- [ ] Loading, empty, and error states are handled.
- [ ] The view is responsive and consistent with existing TFHUB UI guidelines.

## Out of scope

- Athlete rankings and club records.
- Automatic personal-best calculations.
- Performance predictions.
- Training plans and training session management.
- Coach notes and athlete goals.
- Result deletion from the profile.
- Mathematical normalization or percentage-based comparison modes.
- Exporting results to files.