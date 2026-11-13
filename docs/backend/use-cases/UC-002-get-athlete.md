# UC-02 – Get Athlete

**Version:** 1.0  
**Status:** Implemented
**Module:** Athlete

## 1. Goal

Allow the client to retrieve a single athlete by their unique ID.

The use case returns both active and inactive athletes. Inactive athletes must remain accessible because their historical test results are preserved.

## 2. Actor

- Frontend application (client).

## 3. Preconditions

- The backend application is running.
- The athlete ID is provided in the request path.

## 4. Main Flow

1. The client sends a GET request containing the athlete ID.
2. The controller receives the request and delegates it to the Athlete Service.
3. The service retrieves the athlete by ID using the Athlete Repository.
4. If the athlete exists, the service maps the entity to an Athlete Response DTO.
5. The controller returns the athlete data with HTTP status `200 OK`.

## 5. Alternative Flows

### 5.1 Athlete Does Not Exist

1. The service cannot find an athlete with the provided ID.
2. The application returns HTTP status `404 Not Found`.
3. The response contains the standard API error structure.

### 5.2 Invalid Athlete ID

If the ID is not a valid positive integer, the application returns HTTP status `400 Bad Request`.

## 6. API Specification

**Endpoint:**

`GET /api/athletes/{id}`

**Path parameter:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| id | Long | Yes | Unique athlete identifier |

**Successful response: `200 OK`**

Example:

```json
{
  "id": 1,
  "firstName": "Jan",
  "lastName": "Kowalski",
  "birthYear": 2010,
  "gender": "MALE",
  "active": true
}
```

**Error responses:**

| HTTP Status | Condition |
|---|---|
| 400 Bad Request | Athlete ID is invalid (not a positive integer). |
| 404 Not Found | No athlete exists with the provided ID. |

## 7. Business Rules

- Athletes are retrieved by their unique database ID.
- Both active and inactive athletes must be returned.
- The endpoint must not modify any athlete data.
- The response must use an Athlete Response DTO, not the JPA entity.
- The endpoint returns exactly one athlete.
- The response includes the athlete's current training group ID and aliases. Existing athlete access rules apply to the entire response.

## 8. Acceptance Criteria

- [ ] An existing active athlete can be retrieved by ID.
- [ ] An existing inactive athlete can also be retrieved by ID.
- [ ] The response contains `id`, `firstName`, `lastName`, `birthYear`, `gender`, `active`, `trainingGroupId` and `aliases`.
- [ ] A successful request returns HTTP `200 OK`.
- [ ] A request for a non-existing athlete returns HTTP `404 Not Found`.
- [ ] A request with an invalid ID returns HTTP `400 Bad Request`.
- [ ] The endpoint does not modify database records.
- [ ] The response does not expose the JPA entity directly.

## 9. Out of Scope

- Retrieving multiple athletes.
- Searching or filtering athletes.
- Creating or updating athletes.
- Changing athlete status.
- Retrieving athlete test results.