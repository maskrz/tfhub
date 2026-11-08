# BE-STAT-001: Provide Dashboard Statistics

## 1. Goal

Provide aggregated statistics and recent results required by the TFHUB dashboard.

The backend must return only the data required by the frontend. It must not expose the entire results collection for frontend-side aggregation.

The functionality belongs to a dedicated `statistics` module.

## 2. Scope

The use case consists of two read-only API operations:

1. Retrieve dashboard overview data.
2. Retrieve club records for a selected test and season.

No existing CRUD operations are modified.

## 3. API Endpoints

### 3.1. Get Dashboard Overview

**Endpoint**

`GET /api/statistics/dashboard`

Returns the data required to initialize the dashboard.

**Response structure**

`DashboardOverviewResponse`:

| Field | Type | Description |
|---|---|---|
| activeAthleteCount | integer | Number of active athletes |
| availableTests | array | Tests available in the club records selector |
| availableSeasons | array of integers | Calendar years containing results |
| recentResults | array | Latest 10 results |

`availableTests` item:

| Field | Type | Description |
|---|---|---|
| id | long | Test ID |
| name | string | Test name |
| category | enum | TIME, DISTANCE, SPEED |
| unit | string | Result unit |
| active | boolean | Whether the test is active |

Only tests with at least one recorded result are included. Both active and inactive tests must be available to preserve access to historical club records.

`recentResults` item:

| Field | Type | Description |
|---|---|---|
| id | long | Result ID |
| resultDate | date | Date of the result |
| athleteId | long | Athlete ID |
| athleteName | string | Full athlete name |
| testId | long | Test ID |
| testName | string | Test name |
| value | decimal | Result value |
| unit | string | Result unit |
| type | enum | TRAINING or COMPETITION |

**Business rules**

- Count only active athletes.
- Include results belonging to inactive athletes.
- Include results belonging to inactive tests.
- Include both TRAINING and COMPETITION results.
- Return no more than 10 recent results.
- Return results sorted by `resultDate` descending.
- Use result ID descending as a secondary sort to ensure deterministic ordering.
- Return available seasons as distinct calendar years derived from existing result dates, sorted descending.
- Return available tests sorted alphabetically by name.
- Do not return complete athlete, test or result entities.

**Empty states**

- No active athletes: `activeAthleteCount = 0`.
- No tests with results: `availableTests = []`.
- No recorded results: `availableSeasons = []` and `recentResults = []`.

### 3.2. Get Club Records

**Endpoint**

`GET /api/statistics/records?testId={testId}&season={season}`

The `season` parameter is optional. When omitted, all available results are considered.

**Parameters**

| Parameter | Required | Description |
|---|---|---|
| testId | Yes | ID of the selected test |
| season | No | Calendar year, e.g. 2026 |

**Response structure**

`ClubRecordsResponse`:

| Field | Type | Description |
|---|---|---|
| testId | long | Selected test ID |
| season | integer / null | Selected year, or null for all time |
| FEMALE | array | Records for athletes whose gender is `FEMALE` |
| MALE | array | Records for athletes whose gender is `MALE` |

Each record entry:

| Field | Type | Description |
|---|---|---|
| place | integer | Ranking position |
| athleteId | long | Athlete ID |
| athleteName | string | Full athlete name |
| value | decimal | Best result |
| unit | string | Result unit |
| resultDate | date | Date of the selected result |

**Business rules**

1. Filter results by the selected test.
2. If a season is provided, include only results dated between January 1 and December 31 of that calendar year.
3. Include both TRAINING and COMPETITION results.
4. Include inactive athletes.
5. Every athlete must appear at most once in each response.
6. Select the best result for each athlete within the selected period.
7. Use the test's `higherIsBetter` property to determine the result comparison direction:
    - TIME: lower value is better.
    - DISTANCE and SPEED: higher value is better.
8. If an athlete has multiple results with the same best value, select the most recent result date. Use result ID as a secondary tie-breaker.
9. Calculate ranking positions using competition ranking:
    - 1, 1, 3, 4...
    - Athletes with equal best values receive the same position.
10. Return separate collections keyed by the gender enum values `FEMALE` and `MALE`.
11. Sort each collection by ranking position, then athlete surname and first name.
12. If no results match the selected test and season, return empty collections.

## 4. Database and Query Requirements

Statistics must be calculated in the database.

Do not:
- Fetch all results and calculate rankings in Java.
- Fetch all athletes and count active ones in memory.
- Load complete JPA entities when a projection is sufficient.
- Make one query per athlete.
- Return unnecessary data to Angular.

Recommended query techniques:

- `COUNT` for active athlete count.
- `DISTINCT` for available seasons.
- `ORDER BY` and `LIMIT` for recent results.
- SQL joins for athlete and test names.
- Window functions (`ROW_NUMBER()` and `RANK()`) for club records.

The club records query should:
1. Filter results by test and optional season.
2. Select the best result for each athlete.
3. Calculate ranking positions, including ties.
4. Return separate collections for `FEMALE` and `MALE`.

Use database-side pagination/limiting where applicable. The dashboard does not require an unbounded results response.

## 5. Module Structure

Add a dedicated module following the existing backend conventions:

```text
statistics/
├── controller/
├── dto/
│   └── response/
├── repository/
├── service/
└── exception/
```

Use read-only DTO projections for database aggregation.

**Architectural note:** Statistics is a cross-module reporting feature. Its dedicated read-only query repository may perform controlled joins across athlete, test and result tables. This is an explicit exception to the usual rule that modules access each other through services. It must not expose or modify other modules' entities.

## 6. Validation and Error Handling

- `testId` must be a positive integer.
- `season`, when provided, must be a valid calendar year.
- Unknown test ID returns HTTP 404.
- Invalid query parameters return HTTP 400.
- Use the existing centralized exception handling and error response format.
- Both operations are read-only and must not modify database state.

## 7. Performance Requirements

- All calculations and filtering must happen in PostgreSQL.
- Use only the columns needed by the response.
- Avoid N+1 queries.
- The overview operation must return a fixed, bounded number of recent results.
- The records operation must return at most one row per athlete.
- The implementation must remain efficient as the number of historical results grows.

## 8. Acceptance Criteria

- [ ] Dashboard overview returns the active athlete count.
- [ ] Available tests include historical tests with recorded results.
- [ ] Available seasons are derived from existing results.
- [ ] Recent results are limited to 10 entries.
- [ ] Recent results contain athlete and test display data.
- [ ] Club records can be retrieved for a selected test.
- [ ] Club records can be retrieved for all time or a selected calendar year.
- [ ] Both training and competition results are included.
- [ ] Inactive athletes are included in club records.
- [ ] Each athlete appears only once.
- [ ] Best result selection respects `higherIsBetter`.
- [ ] Equal results receive equal ranking positions.
- [ ] `FEMALE` and `MALE` records are returned separately using the enum values as response fields.
- [ ] Empty result sets are handled correctly.
- [ ] No complete results collection is fetched for frontend-side aggregation.
- [ ] Tests cover ranking, ties, result direction, season filtering and inactive athletes.
- [ ] Existing CRUD functionality remains unchanged.
