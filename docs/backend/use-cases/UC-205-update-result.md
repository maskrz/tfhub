# UC-205 – Update Result

**Version:** 1.0  
**Status:** Implemented

## 1. Description

This use case allows the coach to update an existing test result.

All result fields can be modified, including the associated athlete, test, result date, value, and result type.

## 2. Endpoint

`PUT /api/results/{id}`

## 3. Request

Request DTO: `UpdateResultRequest`

Path parameter:

| Parameter | Type | Required | Description |
|---|---|---|---|
| id | Long | Yes | ID of the result to update |

Request body:

| Field | Type | Required | Description |
|---|---|---|---|
| athleteId | Long | Yes | ID of the athlete |
| testId | Long | Yes | ID of the test |
| resultDate | LocalDate | Yes | Result date, ISO format YYYY-MM-DD |
| value | BigDecimal | Yes | Result value |
| type | TestResultType | Yes | TRAINING or COMPETITION |

All request body fields are required.

## 4. Validation rules

1. Result ID must be a positive integer.
2. `athleteId` must be a positive integer.
3. `testId` must be a positive integer.
4. `resultDate` must be a valid ISO date.
5. `value` must be greater than zero.
6. `value` must have no more than 3 decimal places.
7. `value` must fit the database column `NUMERIC(12,3)`.
8. Values must not be silently rounded or truncated.
9. `type` must be either `TRAINING` or `COMPETITION`.

## 5. Business rules

1. The result being updated must exist.
2. The referenced athlete must exist.
3. Both active and inactive athletes are allowed.
4. The referenced test must exist and be active.
5. Results cannot be reassigned to an inactive test.
6. The result ID must remain unchanged.
7. Updating a result must not create a new result.
8. Multiple results for the same athlete, test, and date are allowed.
9. No automatic value conversion based on test unit or category is performed.

## 6. Response

Response DTO: `TestResultResponse`

Return the updated result using the same response structure as UC-203:

- id
- athleteId
- testId
- resultDate
- value
- type

## 7. HTTP responses

- `200 OK` – result successfully updated.
- `400 Bad Request` – invalid ID or request data.
- `404 Not Found` – result, athlete, or test does not exist.
- `409 Conflict` – referenced test exists but is inactive.

## 8. Transaction handling

The operation must execute within a single service-layer transaction.

If validation or persistence fails, the existing result must remain unchanged.

## 9. Out of scope

- Creating new results.
- Updating multiple results in one request.
- Deleting results.
- Changing test definitions or athlete data.

## 10. Dependencies

- UC-201 – Add Result
- UC-203 – Get Result

No database schema migration is required.