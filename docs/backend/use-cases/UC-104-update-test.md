# UC-104 – Update Test

**Version:** 1.0  
**Status:** Implemented

## 1. Goal

Allow the coach to update an existing test definition.

## 2. Endpoint

`PUT /api/tests/{id}`

## 3. Request

Use a dedicated `UpdateTestRequest` DTO.

All four fields are required:

```json
{
  "name": "Bieg na 40 metrów",
  "unit": "s",
  "category": "TIME",
  "higherIsBetter": false
}
```

| Field | Type | Required |
|---|---|---|
| name | String | Yes |
| unit | String | Yes |
| category | Enum | Yes |
| higherIsBetter | Boolean | Yes |

## 4. Validation Rules

### Name
- Trim leading and trailing whitespace.
- Must not be empty after trimming.
- Maximum length: 150 characters.
- Must be unique across all tests, including inactive tests.
- Uniqueness is case-insensitive after trimming.
- When checking uniqueness, exclude the test currently being updated.
- Keeping the current name is allowed.

### Unit
Allowed values:
- `s`
- `m`
- `m/s`

### Category
Allowed values:
- `TIME`
- `DISTANCE`
- `SPEED`

### Higher Is Better
- Required Boolean value.
- `true` means a higher numerical value represents a better performance.
- `false` means a lower numerical value represents a better performance.

## 5. Business Rules

1. The test must exist.
2. Both active and inactive tests can be updated.
3. The `active` field must remain unchanged.
4. The test ID must remain unchanged.
5. Updating the test must not modify or delete associated results.
6. A duplicate name belonging to another test results in HTTP 409.
7. Changing `unit` or `category` is allowed only if the test has no associated results.
8. If at least one result exists for the test, changing `unit` or `category` must result in HTTP 409.
9. Updating other fields while keeping `unit` and `category` unchanged is allowed even when results exist.
10. Updating a test with unchanged values is allowed.

## 6. Response

**HTTP 200 OK**

Return the updated test as a `TestResponse` DTO containing:

- `id`
- `name`
- `unit`
- `category`
- `higherIsBetter`
- `active`

## 7. Error Handling

| Scenario | HTTP status |
|---|---|
| Test successfully updated | 200 OK |
| Test does not exist | 404 Not Found |
| Invalid or non-positive ID | 400 Bad Request |
| Invalid request or missing required fields | 400 Bad Request |
| Duplicate test name | 409 Conflict |
| Unit or category change when results exist | 409 Conflict |

Use the existing centralized exception-handling conventions.

## 8. Persistence and Transactions

- Update the existing record in the `tests` table.
- Do not physically delete or recreate the test.
- Preserve all associated test results.
- Execute the operation within a service-layer transaction.
- No database migration is required.

## 9. Out of Scope

- Activating or deactivating tests.
- Deleting tests.
- Modifying associated results.
- Bulk updates.