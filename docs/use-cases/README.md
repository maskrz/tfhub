# Use Cases

**Version:** 1.1

This document is the central catalog of use cases for TFHub.

Detailed specifications are maintained in separate files under `docs/use-cases/`.

## Status Definitions

- **Planned** – identified, not yet specified.
- **Specified** – detailed specification approved.
- **In Progress** – implementation has started.
- **Implemented** – implementation completed.
- **Verified** – implementation verified through automated tests and/or documented manual acceptance tests.

---

## 1. Athlete Module

Manages athlete records and their active status.

| ID     | Use Case | Description | Status |
|--------|---|---|---|
| UC-001 | Add Athlete | Create a new athlete. | Implemented |
| UC-002 | Get Athlete | Retrieve one athlete by ID, including inactive athletes. | Planned |
| UC-003 | Get All Athletes | Retrieve athletes with optional status filtering and name search. | Planned |
| UC-004 | Update Athlete | Update an athlete's personal information. | Planned |
| UC-005 | Change Athlete Status | Activate or deactivate an athlete. | Planned |

Detailed specifications:
- [UC-001 Add Athlete](UC-001-add-athlete.md)

## 2. Test Module

Manages the catalogue of performance tests used to evaluate athletes.

| ID     | Use Case | Description | Status |
|--------|---|---|---|
| UC-101 | Add Test | Create a new test definition. | Implemented |
| UC-102 | Get Test | Retrieve one test by ID. | Implemented |
| UC-103 | Search Tests | Search tests using optional criteria. | Implemented |
| UC-104 | Update Test | Update a test's definition, such as its name or measurement settings. | Implemented |
| UC-105 | Change Test Status | Activate or deactivate a test. | Implemented |

Detailed specifications:
- [UC-101 Add Test](UC-101-add-test.md)
- [UC-103 Search Tests](UC-103-search-test.md)

Business rules:
- Each test has a name, unit, category and information indicating whether higher values are better.
- Tests with existing results must not have their measurement unit or category changed in a way that would reinterpret historical results.
- Tests are deactivated instead of physically deleted.

## 3. Result Module

Manages individual athlete performance results, including single and group data entry.

| ID     | Use Case | Description | Status |
|--------|---|---|---|
| UC-201 | Add Result | Record one athlete's result for a selected test and date. | Implemented |
| UC-202 | Add Multiple Results | Record results for multiple athletes in one operation, using a shared test and date. | Implemented |
| UC-203 | Get Result | Retrieve one result by ID. | Implemented |
| UC-204 | Get Results | Retrieve results with optional filters, such as athlete, test, date range and result type. | Implemented |
| UC-205 | Update Result | Modify an existing result. | Implemented |
| UC-206 | Delete Result | Delete an existing result. | Implemented |

Business rules:
- A result belongs to exactly one athlete and one test.
- A result contains a date, numeric value and type (`TRAINING` or `COMPETITION`).
- Multiple results for the same athlete, test and date are technically allowed.
- Group entry uses one selected test and date, with individual values assigned to athletes.
- Adding multiple results should be handled as one operation: either all valid results are saved or none are saved.
- Results can be edited and deleted.

---

## Future Modules

The following functionality is intentionally outside the scope of the initial implementation:

- Statistics and reports (personal bests, progress charts, rankings and comparisons).
- Training sessions and attendance.
- Groups and team management.
- Competition management.

These areas may be introduced when the core athlete, test and result workflows are complete.