# Use Cases

**Version:** 1.1

This document is the central catalog of use cases for TFHub.

Detailed specifications are maintained in separate files in this directory.

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
| UC-002 | Get Athlete | Retrieve one athlete by ID, including inactive athletes. | Implemented |
| UC-003 | Search Athletes | Retrieve athletes with optional status filtering and name search. | Implemented |
| UC-004 | Update Athlete | Update an athlete's personal information. | Implemented |
| UC-005 | Change Athlete Status | Activate or deactivate an athlete. | Implemented |

Detailed specifications:
- [UC-001 Add Athlete](UC-001-add-athlete.md)
- [UC-002 Get Athlete](UC-002-get-athlete.md)
- [UC-003 Search Athletes](UC-003-search-athlete.md)
- [UC-004 Update Athlete](UC-004-update-athlete.md)
- [UC-005 Change Athlete Status](UC-005-change-athlete-status.md)

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

## 4. Statistics Module

Provides read-only dashboard aggregates and club records.

| ID | Use Case | Description | Status |
|---|---|---|---|
| BE-STAT-001 | Dashboard Statistics | Retrieve dashboard overview data and club records. | Implemented |

Detailed specification:
- [BE-STAT-001 Dashboard Statistics](UC-301-dashboard-stats.md)

## 5. Account Management Module

Administrators manage application accounts and their existing group/athlete assignments.

| ID | Use Case | Description | Status |
|---|---|---|---|
| UC-402 | Account Management | List, filter, create, update, activate, deactivate, and reset account passwords. | Verified |

Detailed specification:
- [UC-402 Account Management](UC-402-account-management.md)

---

## Future Modules

The following functionality is intentionally outside the scope of the initial implementation:

- Groups and team management.
- Competition management.

These areas may be introduced when the core athlete, test and result workflows are complete.