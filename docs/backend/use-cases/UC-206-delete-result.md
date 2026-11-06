# UC-206 – Delete Result

**Version:** 1.0  
**Status:** Implemented

## 1. Description

This use case allows the coach to permanently delete an existing test result.

Deleting a result removes only the selected result record. The associated athlete and test must remain unchanged.

## 2. Endpoint

`DELETE /api/results/{id}`

## 3. Request

Path parameter:

| Parameter | Type | Required | Description |
|---|---|---|---|
| id | Long | Yes | ID of the result to delete |

The ID must be a positive integer.

No request body is required.

## 4. Business rules

1. The result must exist before it can be deleted.
2. Only the selected result is deleted.
3. The associated athlete must not be deleted or modified.
4. The associated test must not be deleted or modified.
5. Deletion is permanent and cannot be undone through the API.
6. The operation must not affect other results belonging to the same athlete or test.
7. Results associated with active or inactive athletes and tests can be deleted.

## 5. HTTP responses

- `204 No Content` – result successfully deleted.
- `400 Bad Request` – invalid ID (not a positive integer).
- `404 Not Found` – no result exists with the specified ID.

The successful response must not contain a response body.

## 6. Transaction handling

The operation must execute within a single service-layer transaction.

## 7. Out of scope

- Deleting multiple results in one request.
- Soft deletion or result deactivation.
- Deleting athletes or tests.
- Restoring deleted results.

## 8. Dependencies

- UC-201 – Add Result
- UC-203 – Get Result

No database schema migration is required.