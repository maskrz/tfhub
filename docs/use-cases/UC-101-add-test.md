# UC-101 – Add Test

**Version:** 1.0  
**Status:** Planned

## 1. Goal

Allow the coach to create a new test definition that can later be used to record athletes' results.

## 2. Endpoint

`POST /api/tests`

## 3. Request

Use a dedicated `CreateTestRequest` DTO.

Example:

```json
{
  "name": "Bieg na 40 metrów",
  "unit": "s",
  "category": "TIME",
  "higherIsBetter": false
}
```

All four fields are required.

| Field | Type | Required | Description |
|---|---|---|---|
| name | String | Yes | Test name |
| unit | String | Yes | Measurement unit: `s`, `m` or `m/s` |
| category | Enum | Yes | `TIME`, `DISTANCE` or `SPEED` |
| higherIsBetter | Boolean | Yes | Whether a higher numerical result represents a better performance |

## 4. Validation Rules

### Name
- Trim leading and trailing whitespace.
- Must not be empty after trimming.
- Maximum length: 150 characters.
- Must be unique across all tests, regardless of their active status.
- Uniqueness is case-insensitive after trimming.
- A name belonging to an inactive test cannot be reused.

### Unit
Only the following values are allowed:
- `s` – seconds
- `m` – meters
- `m/s` – meters per second

### Category
Only the following enum values are allowed:
- `TIME`
- `DISTANCE`
- `SPEED`

### Higher Is Better
- Required Boolean value.
- `true` means a higher numerical value represents a better performance.
- `false` means a lower numerical value represents a better performance.

## 5. Business Rules

1. A new test is created as active (`active = true`).
2. The test ID is generated automatically.
3. The name uniqueness check includes both active and inactive tests.
4. Two test names that differ only by letter casing are considered duplicates.
5. The system must enforce uniqueness at the database level as well as in the application.
6. No test results are created as part of this use case.

## 6. Response

**HTTP 201 Created**

Return the created test as a response DTO.

The response must contain:
- `id`
- `name`
- `unit`
- `category`
- `higherIsBetter`
- `active`

Include a `Location` header pointing to `/api/tests/{id}`.

## 7. Error Handling

| Scenario | HTTP status |
|---|---|
| Test successfully created | 201 Created |
| Missing required field | 400 Bad Request |
| Empty or too-long name | 400 Bad Request |
| Invalid unit | 400 Bad Request |
| Invalid category | 400 Bad Request |
| Missing `higherIsBetter` | 400 Bad Request |
| Duplicate test name | 409 Conflict |
| Malformed JSON | 400 Bad Request |

## 8. Persistence

- Persist the test in the `tests` table.
- Add a database-level case-insensitive unique constraint/index on the normalized test name.
- The constraint must include inactive tests.
- A Flyway migration is required if the existing schema does not already enforce this rule.
- Do not modify the `test_results` table.

## 9. Transaction

The operation must execute within a service-layer transaction.

## 10. Out of Scope

- Editing existing tests.
- Activating or deactivating tests.
- Deleting tests.
- Recording athlete results.
- Test statistics or rankings.