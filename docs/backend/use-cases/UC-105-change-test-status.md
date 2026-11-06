# UC-105 – Change Test Status

**Version:** 1.0  
**Status:** Implemented

## 1. Goal

Allow the coach to activate or deactivate a test without physically deleting it or modifying its associated historical results.

## 2. Endpoint

`PATCH /api/tests/{id}/status`

## 3. Request

Use a dedicated `ChangeTestStatusRequest` DTO.

```json
{
  "active": false
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| active | Boolean | Yes | New test status |

## 4. Business Rules

1. The test must exist.
2. The `active` field is updated to the value provided in the request.
3. Both active and inactive tests can have their status changed.
4. Repeating a request with the current status is allowed (idempotent operation).
5. Deactivating a test must not delete the test or any associated results.
6. Activating a test must not modify any other test data.
7. This use case must not modify any other test fields.
8. Historical results associated with the test must remain accessible.

## 5. Response

**HTTP 200 OK**

Return the updated test as a `TestResponse` DTO containing:

- `id`
- `name`
- `unit`
- `category`
- `higherIsBetter`
- `active`

## 6. Error Handling

| Scenario | HTTP status |
|---|---|
| Test successfully activated or deactivated | 200 OK |
| Test does not exist | 404 Not Found |
| Invalid or non-positive test ID | 400 Bad Request |
| Missing `active` field or invalid request body | 400 Bad Request |
| Malformed JSON | 400 Bad Request |

Use the existing centralized exception-handling conventions.

## 7. Persistence and Transactions

- Update the existing `active` field in the `tests` table.
- Do not physically delete any records.
- Preserve all associated historical results.
- Execute the operation within a service-layer transaction.
- No database migration is required.

## 8. Out of Scope

- Editing test data.
- Deleting tests.
- Modifying or deleting associated results.
- Bulk activation or deactivation.
- Recording new athlete results.