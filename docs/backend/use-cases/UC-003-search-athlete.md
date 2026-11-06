# UC-003 – Search Athletes

**Version:** 1.2
**Status:** Implemented
**Module:** Athlete

## 1. Goal

Allow the client to search and filter athletes using multiple optional criteria.

The search supports filtering by first name, last name, birth year, active status and gender.

## 2. Actor

- Frontend application (client).

## 3. Preconditions

- The backend application is running.

## 4. Main Flow

1. The client sends a POST request to the athlete search endpoint.
2. The request contains a SearchAthletesRequest DTO.
3. The controller validates and delegates the request to the Athlete Service.
4. The service retrieves athletes matching all provided criteria using the Athlete Repository.
5. The service maps the entities to Athlete Response DTOs.
6. The controller returns the collection with HTTP status `200 OK`.

## 5. Search Criteria

All search criteria are optional.

| Field | Type | Matching rule |
|---|---|---|
| firstName | String | Case-insensitive partial match (`%value%`). |
| lastName | String | Case-insensitive partial match (`%value%`). |
| birthYear | Integer | Exact match. |
| active | Boolean | Exact match. |
| gender | Gender enum | Exact match (`MALE` or `FEMALE`). |

### 5.1 Text Search

- `firstName` and `lastName` support partial matching.
- Matching is case-insensitive.
- Leading and trailing whitespace is trimmed.
- Null, empty or whitespace-only values are ignored.

### 5.2 Birth Year

- `birthYear` is optional and, when provided, must match the athlete's birth year exactly.
- Valid birth years range from 1900 through the current calendar year.
- Invalid values (not an integer or outside the supported range) return HTTP `400 Bad Request`.
- An omitted birth year does not restrict the search.

### 5.3 Combining Criteria

- All provided criteria are combined using logical AND.
- An athlete must match every provided criterion to appear in the results.
- If no criteria are provided, all athletes are returned, including inactive athletes.
- An omitted `birthYear` does not restrict the search.

## 6. API Specification

**Endpoint:**

`POST /api/athletes/search`

**Request DTO:** `SearchAthletesRequest`

All fields are optional.

Example:

```json
{
  "firstName": "Jan",
  "lastName": "Kow",
  "birthYear": 2010,
  "active": true,
  "gender": "MALE"
}
```

Search athletes born in 2010:

```json
{
  "birthYear": 2010,
  "gender": "FEMALE",
  "active": true
}
```

Other examples:

Search all athletes:

```json
{}
```

Search active athletes:

```json
{
  "active": true
}
```

Search female athletes:

```json
{
  "gender": "FEMALE"
}
```

Search by first name:

```json
{
  "firstName": "Jan"
}
```

Search by last name:

```json
{
  "lastName": "Kow"
}
```

### Successful response: `200 OK`

The endpoint returns an array of Athlete Response DTOs.

Example:

```json
[
  {
    "id": 1,
    "firstName": "Jan",
    "lastName": "Kowalski",
    "birthYear": 2010,
    "gender": "MALE",
    "active": true
  }
]
```

If no athletes match the criteria, return an empty array with HTTP `200 OK`.

## 7. Business Rules

- Search is a read-only operation and must not modify athlete data.
- All provided criteria must match (logical AND).
- When provided, `birthYear` must exactly match the athlete's birth year.
- An omitted `birthYear` does not restrict the search.
- Birth year must be an integer from 1900 through the current calendar year.
- Text matching is case-insensitive and supports partial matches.
- Empty text criteria are ignored.
- No criteria means all athletes are returned.
- Inactive athletes are included unless explicitly filtered using `active`.
- The response must contain Athlete Response DTOs, never JPA entities.
- The request must use a dedicated SearchAthletesRequest DTO, separate from the Athlete entity and response DTO.
- No pagination or sorting is required at this stage.

## 8. Validation and Error Handling

- Invalid gender values must return HTTP `400 Bad Request`.
- Invalid birth year values (not an integer or outside the supported range) must return HTTP `400 Bad Request`.
- Invalid request JSON must return HTTP `400 Bad Request`.
- All error responses must follow the existing API error response structure.

## 9. Acceptance Criteria

- [ ] An empty request returns all athletes.
- [ ] First name supports case-insensitive partial matching.
- [ ] Last name supports case-insensitive partial matching.
- [ ] Leading and trailing whitespace in text criteria is ignored.
- [ ] Empty text criteria are ignored.
- [ ] Active status filtering works correctly.
- [ ] Gender filtering works correctly.
- [ ] Birth year filtering matches exactly.
- [ ] An omitted birth year does not restrict the search.
- [ ] Birth years below 1900, later than the current year, or not valid integers return HTTP `400 Bad Request`.
- [ ] Multiple criteria are combined using AND.
- [ ] Inactive athletes are included when no active filter is provided.
- [ ] No matches return an empty array with HTTP `200 OK`.
- [ ] The response contains all athlete fields.
- [ ] The response does not expose JPA entities.
- [ ] Invalid gender and malformed JSON return HTTP `400 Bad Request`.
- [ ] The endpoint does not modify database records.

## 10. Out of Scope

- Pagination and sorting.
- Retrieving a single athlete by ID.
- Creating or updating athletes.
- Changing athlete status.
- Retrieving athlete test results.