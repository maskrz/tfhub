# UC-102 – Get Test

**Version:** 1.0  
**Status:** Planned

## 1. Goal

Allow the coach to retrieve a single test by its unique ID.

## 2. Endpoint

`GET /api/tests/{id}`

## 3. Request

Path parameter:

| Parameter | Type | Required | Description |
|---|---|---|---|
| id | Long | Yes | Unique test identifier |

No request body is required.

## 4. Business Rules

1. Retrieve the test with the specified ID.
2. Both active and inactive tests can be retrieved.
3. The endpoint must return the complete test information.
4. The endpoint is read-only and must not modify any data.
5. Return a response DTO, never a JPA entity.

## 5. Response

**HTTP 200 OK**

Return a `TestResponse` DTO containing:

- `id`
- `name`
- `unit`
- `category`
- `higherIsBetter`
- `active`

## 6. Error Handling

| Scenario | HTTP status |
|---|---|
| Test found | 200 OK |
| Test does not exist | 404 Not Found |
| Invalid or non-positive ID | 400 Bad Request |

Use the existing centralized exception-handling conventions.

## 7. Transaction

Use a read-only service-layer transaction, following existing project conventions.

## 8. Out of Scope

- Searching or listing tests.
- Creating, updating or changing test status.
- Recording athlete results.
- Statistics or rankings.