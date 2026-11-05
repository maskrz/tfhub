# UC-204 – Get Results

**Version:** 1.0  
**Status:** Planned

## 1. Description

This use case allows the client to retrieve multiple test results, optionally filtered by athlete, test, date range, and result type.

It is intended to support displaying result history for individual athletes, reviewing results for a particular test, and retrieving results from a specific testing period.

## 2. Endpoint

`GET /api/results`

## 3. Request

All query parameters are optional.

| Parameter | Type | Required | Description |
|---|---|---|---|
| athleteId | Long | No | Filter by athlete ID |
| testId | Long | No | Filter by test ID |
| fromDate | LocalDate | No | Include results from this date onwards |
| toDate | LocalDate | No | Include results up to this date |
| type | TestResultType | No | TRAINING or COMPETITION |

Date parameters must use ISO format `YYYY-MM-DD`.

## 4. Validation rules

1. If provided, `athleteId` must be a positive integer.
2. If provided, `testId` must be a positive integer.
3. If provided, `fromDate` and `toDate` must be valid ISO dates.
4. If both dates are provided, `fromDate` must not be later than `toDate`.
5. If provided, `type` must be either `TRAINING` or `COMPETITION`.
6. Invalid query parameters must result in `400 Bad Request`.

## 5. Filtering rules

1. All provided criteria must be combined using AND logic.
2. `athleteId` filters results belonging to the specified athlete.
3. `testId` filters results associated with the specified test.
4. `fromDate` is inclusive.
5. `toDate` is inclusive.
6. `type` filters by result type.
7. If no filters are provided, all results must be returned.
8. An unknown but valid `athleteId` or `testId` must return an empty list, not `404 Not Found`.
9. Results must be returned regardless of whether their associated athlete or test is active or inactive.

## 6. Response

Response: `List<TestResultResponse>`

Each item uses the response structure defined in UC-203:

- id
- athleteId
- testId
- resultDate
- value
- type

Return a JSON array. If no results match the criteria, return an empty array.

## 7. Sorting

Results must be sorted deterministically:

1. `resultDate` descending (newest first).
2. `id` descending when multiple results have the same date.

No additional sorting parameters are required.

## 8. HTTP responses

- `200 OK` – query executed successfully, including when no results match.
- `400 Bad Request` – invalid query parameter or invalid date range.

## 9. Transaction handling

Use a read-only service-layer transaction.

## 10. Out of scope

- Pagination.
- Client-controlled sorting.
- Full athlete or test objects in the response.
- Aggregations, statistics, or rankings.
- Exporting results to CSV or Excel.

## 11. Dependencies

- UC-201 – Add Result
- UC-202 – Add Multiple Results
- UC-203 – Get Result

No database schema migration is required.