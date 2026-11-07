# UC-202 – Add Multiple Results

**Version:** 1.0  
**Status:** Implemented

## 1. Description

This use case allows the coach to create multiple test results in a single request.

It is intended primarily for entering results for a group of athletes after a testing session.

All results in one request share the same test, result date, and result type. Each result entry specifies an athlete and their individual value.

The operation is atomic: either all results are created successfully, or none of them are persisted.

## 2. Endpoint

`POST /api/results/batch`

## 3. Request

Request DTO: `CreateMultipleResultsRequest`

Fields:

| Field | Type | Required | Description |
|---|---|---|---|
| testId | Long | Yes | ID of the test |
| resultDate | LocalDate | Yes | Date of the results, ISO format YYYY-MM-DD |
| type | TestResultType | Yes | TRAINING or COMPETITION |
| results | Array | Yes | List of individual athlete results |

Each element in `results` contains:

| Field | Type | Required | Description |
|---|---|---|---|
| athleteId | Long | Yes | ID of the athlete |
| value | BigDecimal | Yes | Individual result value |

### 3.1 Validation rules

- `testId` must be a positive integer.
- `resultDate` must be a valid ISO date.
- `type` must be either `TRAINING` or `COMPETITION`.
- `results` must contain at least one element.
- `results` must contain no more than 100 elements.
- Every `athleteId` must be a positive integer.
- Every `athleteId` must be unique within the request.
- Every referenced athlete must be active.
- Every `value` must be greater than zero.
- Every `value` must have no more than 3 decimal places.
- Every `value` must fit the database column `NUMERIC(12,3)`.
- Values must not be silently rounded or truncated.

## 4. Business rules

1. The referenced test must exist.
2. The referenced test must be active. Results cannot be created for inactive tests.
3. Every referenced athlete must exist and be active.
5. Each athlete can appear only once in a single batch request.
6. The same athlete can still have multiple results for the same test and date through separate requests, as defined in UC-201.
7. All results share the `testId`, `resultDate`, and `type` provided at the top level.
8. The operation must be atomic. If any entry is invalid or any referenced entity cannot be found, no results from the request may be persisted.
9. Result IDs are generated automatically.
10. Results must be returned in the same order as the corresponding entries in the request.

## 5. Response

Response DTO: `CreateMultipleResultsResponse`

Fields:

| Field | Type | Description |
|---|---|---|
| createdCount | Integer | Number of created results |
| results | Array of TestResultResponse | Created results |

Each result uses the response structure defined in UC-201:

- id
- athleteId
- testId
- resultDate
- value
- type

## 6. HTTP responses

- `201 Created` – all results were successfully created.
- `400 Bad Request` – invalid request, missing fields, invalid values, empty list, more than 100 entries, or duplicate athlete IDs within the request.
- `404 Not Found` – referenced test or at least one referenced athlete does not exist.
- `409 Conflict` – referenced test or athlete exists but is inactive.

Use the existing API error response structure and conventions.

The response does not include a `Location` header because this operation creates multiple independent resources rather than one resource.

## 7. Transaction handling

The entire operation must execute within a single database transaction.

If any validation or persistence step fails, the transaction must roll back and no results from the batch may remain in the database.

## 8. Out of scope

- Creating results for multiple tests in one request.
- Different result dates or types within one batch.
- Multiple attempts for the same athlete within one batch.
- Updating or deleting existing results.
- Importing results from CSV or Excel.
- Frontend implementation.

## 9. Dependencies

- UC-201 – Add Result
- Existing Athlete and Test modules

No database schema migration is required.