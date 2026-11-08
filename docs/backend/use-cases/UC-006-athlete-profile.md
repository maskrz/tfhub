# UC-006 — Athlete Profile Data

## Goal

Provide the backend data required by the athlete profile view.

The functionality extends the existing `athlete` and `result` modules. No new backend module should be introduced.

The backend must support:
- Retrieving an athlete's tests for which at least one result exists.
- Retrieving the athlete's results for selected tests and an optional date range.
- Including historical results associated with inactive tests.
- Preserving multiple results for the same athlete, test, and date.

## Scope

### 1. Athlete information

Reuse the existing athlete retrieval functionality.

The frontend needs:
- Athlete ID
- First name
- Last name
- Birth year
- Gender
- Active status

Do not duplicate existing athlete functionality or introduce a separate athlete profile entity.

### 2. Tests with recorded results

Provide a way to retrieve the tests for which the specified athlete has at least one recorded result.

Each returned test must contain:
- Test ID
- Test name
- Unit
- Category
- Higher-is-better information, if already available in the existing test model/DTO

Requirements:
- Include active and inactive tests, provided that the athlete has at least one result for the test.
- Do not return tests without any results for the specified athlete.
- Return each test only once.
- Use the existing test model and module conventions.
- Do not expose JPA entities.

### 3. Athlete result history

Provide a way to retrieve results belonging to a specified athlete.

Supported filtering:
- Selected test IDs (multiple values).
- Optional start date.
- Optional end date.

Requirements:
- Return only results belonging to the specified athlete.
- Apply selected test IDs when provided.
- Apply date range filters when provided.
- Include both TRAINING and COMPETITION results.
- Include historical results associated with inactive tests.
- Preserve multiple results for the same test and date. Do not aggregate or overwrite them.
- Return enough information for the frontend to build the results matrix and chart.

Each result must provide:
- Result ID
- Test ID
- Test name
- Test unit
- Result date
- Result value
- Result type

The frontend must be able to identify and edit each individual result using its ID.

### 4. Ordering

Return results in a deterministic order:
- Result date ascending.
- For results with the same date, use a stable secondary ordering (for example, test ID and result ID).

The frontend may use this ordering to construct the matrix, but must not rely on ordering to identify individual results.

## Technical requirements

- Extend existing `athlete`, `test`, and `result` functionality only as necessary.
- Prefer extending the existing `result` module for result-history queries.
- Reuse existing services, repositories, DTOs, validation, and exception-handling conventions.
- Do not introduce a new module, microservice, entity, or database table unless a genuine data-model requirement is discovered.
- Do not expose JPA entities through REST controllers.
- Use database-level filtering. Do not retrieve all results and filter them in memory.
- Avoid N+1 queries when retrieving result details with test information.
- Respect existing transaction and service-layer conventions.
- Keep existing API behavior backward-compatible.

## API design

Inspect the existing controllers, services, repositories, request/response DTOs, and endpoint conventions before making changes.

Determine the most appropriate API operations for:
1. Retrieving tests with results for a given athlete.
2. Retrieving filtered results for a given athlete.

Reuse existing endpoints where possible. Add or extend endpoints only where necessary.

Do not assume endpoint paths, query parameter names, or DTO structures without checking the existing implementation.

## Acceptance criteria

- [ ] Athlete information can be retrieved using existing functionality.
- [ ] The frontend can retrieve all tests for which the athlete has recorded results.
- [ ] Inactive tests are included when historical results exist.
- [ ] Tests without results for the athlete are excluded.
- [ ] Results can be filtered by multiple test IDs.
- [ ] Results can be filtered by an optional date range.
- [ ] Both TRAINING and COMPETITION results are returned.
- [ ] Multiple results for the same test and date are preserved as separate records.
- [ ] Each result contains its ID, test information, date, value, and type.
- [ ] Queries are executed in the database rather than filtering the complete result collection in memory.
- [ ] Existing result and athlete functionality continues to work.
- [ ] Automated tests cover filtering, inactive tests, duplicate same-day results, and empty result sets.

## Out of scope

- Creating or editing athletes.
- Creating, editing, or deleting tests.
- Creating, editing, or deleting results.
- Calculating personal bests or rankings.
- Aggregating results into averages or other statistics.
- Generating charts or preparing chart-specific datasets.
- Any frontend implementation.