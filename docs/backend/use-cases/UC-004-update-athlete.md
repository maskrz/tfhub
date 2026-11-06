# UC-004 – Update Athlete

**Version:** 1.0  
**Status:** Implemented
**Module:** Athlete

## 1. Goal

Allow the client to update an existing athlete's personal information.

The athlete's active status is not modified by this use case.

## 2. Actor

- Frontend application (client).

## 3. Preconditions

- The backend application is running.
- The athlete ID is provided.
- The athlete exists in the database.

## 4. Main Flow

1. The client sends a PUT request containing the athlete ID and updated athlete data.
2. The controller validates the request.
3. The controller delegates the operation to the Athlete Service.
4. The service retrieves the athlete by ID.
5. The service validates the updated data and checks for duplicate athletes.
6. The service updates the athlete's personal information.
7. The service saves the changes.
8. The controller returns the updated athlete with HTTP status `200 OK`.

## 5. Alternative Flows

### 5.1 Athlete Does Not Exist

If no athlete exists with the provided ID, return HTTP status `404 Not Found`.

### 5.2 Duplicate Athlete

If another athlete already has the same first name, last name and birth year, return HTTP status `409 Conflict`.

The duplicate check must exclude the athlete currently being updated.

### 5.3 Invalid Request Data

If the request contains invalid data, return HTTP status `400 Bad Request`.

## 6. API Specification

**Endpoint:**

`PUT /api/athletes/{id}`

**Path parameter:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| id | Long | Yes | Unique athlete identifier |

**Request DTO:** `UpdateAthleteRequest`

Required fields:

| Field | Type | Validation |
|---|---|---|
| firstName | String | Required, trimmed, not empty, maximum 100 characters. |
| lastName | String | Required, trimmed, not empty, maximum 100 characters. |
| birthYear | Integer | Required, between 1900 and the current calendar year. |
| gender | Gender enum | Required: MALE or FEMALE. |

Example request:

```json
{
  "firstName": "Janusz",
  "lastName": "Nowak",
  "birthYear": 2010,
  "gender": "MALE"
}
```

### Successful response: `200 OK`

Return the updated Athlete Response DTO.

Example:

```json
{
  "id": 1,
  "firstName": "Janusz",
  "lastName": "Nowak",
  "birthYear": 2010,
  "gender": "MALE",
  "active": true
}
```

## 7. Business Rules

- Only existing athletes can be updated.
- The following fields can be modified: `firstName`, `lastName`, `birthYear`, `gender`.
- The `active` field must not be modified by this operation.
- First and last names must be trimmed before saving.
- Duplicate detection uses `(firstName, lastName, birthYear)`.
- Duplicate detection must exclude the athlete being updated.
- An athlete's active status must remain unchanged.
- Both active and inactive athletes can be updated.
- The response must use an Athlete Response DTO, never a JPA entity.
- The operation must be transactional.

## 8. Acceptance Criteria

- [ ] An existing athlete can be updated.
- [ ] First name can be updated.
- [ ] Last name can be updated.
- [ ] Birth year can be updated.
- [ ] Gender can be updated.
- [ ] Leading and trailing whitespace in names is removed.
- [ ] Invalid or missing required fields return HTTP `400 Bad Request`.
- [ ] Birth year outside the supported range returns HTTP `400 Bad Request`.
- [ ] Updating an athlete to data already used by another athlete returns HTTP `409 Conflict`.
- [ ] Updating an athlete without changing their unique identifying fields is allowed.
- [ ] Updating a non-existing athlete returns HTTP `404 Not Found`.
- [ ] Active status remains unchanged.
- [ ] Both active and inactive athletes can be updated.
- [ ] The response contains the updated athlete data.
- [ ] The response does not expose JPA entities.

## 9. Out of Scope

- Creating athletes.
- Retrieving athletes.
- Searching athletes.
- Activating or deactivating athletes.
- Modifying athlete test results.