# UC-201 – Add Result

**Version:** 1.0  
**Status:** Implemented

## 1. Goal

Allow the coach to record a single test result for an athlete.

## 2. Endpoint

`POST /api/results`

## 3. Request

Use a dedicated `CreateResultRequest` DTO.

Example:

```json
{
  "athleteId": 1,
  "testId": 2,
  "resultDate": "2026-11-05",
  "value": 5.42,
  "type": "TRAINING"
}
```

All fields are required.

| Field | Type | Required | Description |
|---|---|---|---|
| athleteId | Long | Yes | ID of the athlete |
| testId | Long | Yes | ID of the test |
| resultDate | LocalDate | Yes | Date when the result was achieved |
| value | BigDecimal | Yes | Numeric result value |
| type | Enum | Yes | `TRAINING` or `COMPETITION` |

## 4. Validation Rules

### Athlete
- The athlete must exist.
- Both active and inactive athletes are allowed.
- A result can be added for an inactive athlete to supplement historical data.

### Test
- The test must exist.
- The test must be active.
- Inactive tests cannot be used to create new results.
- The test's unit and category are not supplied in the request; they are determined by the selected test.

### Result Date
- Required.
- Must be a valid ISO date in `YYYY-MM-DD` format.
- No additional restriction on past or future dates is introduced in this use case.

### Value
- Required.
- Must be greater than zero.
- Maximum precision: 3 decimal places.
- Must fit the database column `NUMERIC(12,3)`.
- No automatic conversion based on test category or unit is performed.
- The value is stored as a numeric value in the unit defined by the test.

### Type
Allowed values:
- `TRAINING`
- `COMPETITION`

## 5. Business Rules

1. The result must reference an existing athlete and an existing active test.
2. Inactive athletes are allowed.
3. Inactive tests are not allowed for new results.
4. Multiple results for the same athlete, test and date are allowed.
5. The result ID is generated automatically.
6. The result must be persisted without modifying the athlete or test.
7. The result's date is stored as a date without time or timezone.

## 6. Response

**HTTP 201 Created**

Return a dedicated `ResultResponse` DTO containing:

- `id`
- `athleteId`
- `testId`
- `resultDate`
- `value`
- `type`

Include a `Location` header pointing to `/api/results/{id}`.

Do not expose JPA entities through the API.

## 7. Error Handling

| Scenario | HTTP status |
|---|---|
| Result successfully created | 201 Created |
| Missing required field | 400 Bad Request |
| Invalid date format | 400 Bad Request |
| Invalid result value | 400 Bad Request |
| Invalid result type | 400 Bad Request |
| Invalid or non-positive athlete/test ID | 400 Bad Request |
| Athlete does not exist | 404 Not Found |
| Test does not exist | 404 Not Found |
| Test is inactive | 409 Conflict |
| Malformed JSON | 400 Bad Request |

Use the existing centralized exception-handling conventions.

## 8. Persistence

- Persist the result in the `test_results` table.
- Use the existing foreign keys to reference athletes and tests.
- Do not modify the athlete or test records.
- No database migration is required.

## 9. Transaction

The operation must execute within a service-layer transaction.

## 10. Out of Scope

- Creating multiple results in one request.
- Editing or deleting results.
- Searching or retrieving results.
- Statistics and rankings.
- Automatic conversion or formatting of result values.