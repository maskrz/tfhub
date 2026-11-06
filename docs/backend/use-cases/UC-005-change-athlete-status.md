# UC-005 – Change Athlete Status

**Version:** 1.0  
**Status:** Implemented

## 1. Goal

Allow the coach to activate or deactivate an athlete without physically deleting the athlete or their historical test results.

## 2. Endpoint

`PATCH /api/athletes/{id}/status`

## 3. Request

A dedicated `ChangeAthleteStatusRequest` DTO:

```json
{
  "active": false
}
```

Fields:

| Field | Type | Required | Description |
|---|---|---|---|
| active | Boolean | Yes | New athlete status |

## 4. Business Rules

1. The athlete must exist.
2. The `active` field is updated to the value provided in the request.
3. Both active and inactive athletes can have their status changed.
4. Repeating a request with the current status is allowed (idempotent operation).
5. Deactivating an athlete must not delete the athlete or any associated test results.
6. Activating an athlete must not modify any other athlete data.
7. This use case must not modify any other athlete fields.

## 5. Response

**HTTP 200 OK**

Return the updated `AthleteResponse` DTO.

The response must include the updated `active` value and all other athlete fields unchanged.

## 6. Error Handling

| Scenario | HTTP status |
|---|---|
| Athlete successfully activated or deactivated | 200 OK |
| Athlete does not exist | 404 Not Found |
| Invalid or non-positive athlete ID | 400 Bad Request |
| Missing `active` field or invalid request body | 400 Bad Request |
| Malformed JSON | 400 Bad Request |

## 7. Persistence

- Update the existing `active` field in the `athletes` table.
- No database migration is required.
- Do not physically delete any records.
- Preserve all historical test results.

## 8. Transaction

The operation must execute within a service-layer transaction.

## 9. Out of Scope

- Deleting athletes.
- Editing athlete personal data.
- Modifying or deleting test results.
- Bulk activation or deactivation of athletes.