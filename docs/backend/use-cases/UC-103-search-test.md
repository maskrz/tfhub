# UC-103 – Search Tests

**Version:** 1.0  
**Status:** Implemented

## 1. Goal

Allow the coach to search and retrieve tests using optional filtering criteria.

## 2. Endpoint

`POST /api/tests/search`

## 3. Request

Use a dedicated `TestSearchRequest` DTO.

All fields are optional.

Example:

```json
{
  "name": "bieg",
  "unit": "s",
  "category": "TIME",
  "higherIsBetter": false,
  "active": true
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| name | String | No | Partial, case-insensitive name match |
| unit | String | No | Exact unit match |
| category | Enum | No | Exact category match |
| higherIsBetter | Boolean | No | Exact Boolean match |
| active | Boolean | No | Exact status match |

## 4. Search Rules

### Name
- Partial matching using a case-insensitive contains search.
- Trim leading and trailing whitespace.
- Null, empty or whitespace-only values are ignored.

### Unit
- Exact match.
- Allowed values: `s`, `m`, `m/s`.
- Null or omitted value is ignored.
- Invalid value results in HTTP 400.

### Category
- Exact enum match.
- Allowed values: `TIME`, `DISTANCE`, `SPEED`.
- Null or omitted value is ignored.
- Invalid value results in HTTP 400.

### Higher Is Better
- Exact Boolean match.
- Both `true` and `false` are valid filter values.
- Null or omitted value is ignored.

### Active
- Exact Boolean match.
- `true` returns active tests only.
- `false` returns inactive tests only.
- Null or omitted value is ignored.

## 5. Combining Criteria

- All supplied criteria must be combined using logical AND.
- If no effective criteria are supplied, return all tests, both active and inactive.
- An empty or whitespace-only name does not count as an effective criterion.

## 6. Response

**HTTP 200 OK**

Return a list of `TestResponse` DTOs.

Each item contains:

- `id`
- `name`
- `unit`
- `category`
- `higherIsBetter`
- `active`

An empty result must return HTTP 200 with an empty array.

## 7. Error Handling

| Scenario | HTTP status |
|---|---|
| Search completed successfully | 200 OK |
| Invalid unit | 400 Bad Request |
| Invalid category | 400 Bad Request |
| Malformed JSON | 400 Bad Request |
| Invalid request field type | 400 Bad Request |

## 8. Technical Requirements

- Use a dedicated `TestSearchRequest` DTO.
- Do not expose JPA entities through the API.
- Keep search logic in the service/repository layers.
- Use a read-only service-layer transaction.
- No pagination or sorting at this stage.
- The endpoint must not modify any data.

## 9. Out of Scope

- Creating, updating or changing test status.
- Recording athlete results.
- Statistics or rankings.