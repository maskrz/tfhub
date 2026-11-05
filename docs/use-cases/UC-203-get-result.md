# UC-203 – Get Result

**Version:** 1.0  
**Status:** Planned

## 1. Description

This use case allows the client to retrieve a single test result by its unique ID.

The response includes the result data and the IDs of the associated athlete and test.

## 2. Endpoint

`GET /api/results/{id}`

## 3. Request

Path parameter:

| Parameter | Type | Required | Description |
|---|---|---|---|
| id | Long | Yes | Unique result ID |

The ID must be a positive integer.

No request body is required.

## 4. Response

Response DTO: `TestResultResponse`

Fields:

| Field | Type | Description |
|---|---|---|
| id | Long | Result ID |
| athleteId | Long | Associated athlete ID |
| testId | Long | Associated test ID |
| resultDate | LocalDate | Date of the result |
| value | BigDecimal | Result value |
| type | TestResultType | TRAINING or COMPETITION |

The response must not expose JPA entities directly.

## 5. HTTP responses

- `200 OK` – result found.
- `400 Bad Request` – invalid ID (not a positive integer).
- `404 Not Found` – no result exists with the specified ID.

## 6. Business rules

1. Results can be retrieved regardless of whether the associated athlete is active or inactive.
2. Results can be retrieved regardless of whether the associated test is active or inactive.
3. This operation is read-only.
4. No additional filtering, sorting, or pagination is required.

## 7. Transaction handling

Use a read-only service-layer transaction.

## 8. Out of scope

- Retrieving multiple results.
- Searching or filtering results.
- Updating or deleting results.
- Returning full athlete or test objects instead of their IDs.

## 9. Dependencies

- UC-201 – Add Result
- UC-202 – Add Multiple Results

No database schema migration is required.