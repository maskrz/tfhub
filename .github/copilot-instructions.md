# GitHub Copilot Instructions – TFHub

## 1. Project Context

TFHub (Track & Field Hub) is an athletics coaching application.

The application is initially designed for a single coach and is used to manage athletes, athletic tests and test results.

Before implementing any feature, read and follow:

- `docs/backend/architecture.md`
- `docs/backend/data-model.md`
- Relevant backend use-case documentation in `docs/backend/use-cases/`, when available.

These documents are the primary source of truth for architectural and business decisions.

If requirements are missing, ambiguous or contradictory, ask for clarification instead of inventing business rules.

## 2. Technology Stack

Backend:
- Java 21
- Spring Boot 4
- Maven
- Spring Data JPA
- Hibernate
- PostgreSQL 17
- Flyway
- Jakarta Bean Validation

Frontend:
- Angular
- TypeScript
- SCSS

Infrastructure:
- Docker Compose
- Git and GitHub

Use the existing dependencies and project configuration. Do not introduce new dependencies without a clear technical justification.

## 3. Architecture Rules

The application follows a modular monolith architecture with layered organization inside functional modules.

Backend modules are organized by functionality:

- `athlete`
- `test`
- `result`

Place classes in responsibility-based subpackages:

- `controller/` for REST controllers
- `dto/request/` and `dto/response/` for API request and response DTOs
- `entity/` for JPA entities
- `enums/` for module-specific enums
- `repository/` for Spring Data repositories
- `service/` for business logic and transaction handling
- `exception/` for module-specific exceptions

Application-wide configuration belongs in `config/`. Global exception
handling and genuinely shared utilities belong in `common/`, such as
`common/exception/` or `common/validation/`. Keep module-specific exceptions
and business logic within their owning module; do not create empty packages.

Follow the responsibilities described in `docs/backend/architecture.md`.

Important rules:

1. Controllers must not contain business logic.
2. Controllers must not access repositories directly.
3. Services contain business logic and coordinate operations.
4. Repositories are responsible for persistence.
5. JPA entities must not be exposed directly through REST endpoints.
6. Use request and response DTOs.
7. A module must not access another module's repository directly; use that
   module's service layer for cross-module operations.
8. Avoid circular dependencies between modules.
9. Do not introduce unnecessary architectural abstractions.

Do not implement full DDD, CQRS, event sourcing or microservices.

## 4. Database Rules

Flyway is the only mechanism for database schema changes.

Rules:

1. Never modify an existing Flyway migration that has already been applied.
2. Every schema modification requires a new migration.
3. Use the naming convention `V<version>__<description>.sql`.
4. Do not use Hibernate to automatically create or update database schema.
5. Hibernate must use `ddl-auto=validate`.
6. Keep entity mappings consistent with the existing PostgreSQL schema.
7. Preserve historical data.
8. Do not introduce cascading deletes that could remove historical results.

Before creating or modifying entities, inspect the existing Flyway migrations.

## 5. Business Rules

Follow `docs/backend/data-model.md` and relevant backend use-case documentation.

Important rules include:

- Athletes are deactivated instead of physically deleted.
- Tests with existing results are deactivated instead of physically deleted.
- Historical results must be preserved.
- Test results use the canonical unit defined by their test.
- Test result values must be positive.
- Result dates use calendar dates without time or timezone.
- Result types are `TRAINING` and `COMPETITION`.
- Athlete gender values are `MALE` and `FEMALE`.
- Test categories are `TIME`, `DISTANCE` and `SPEED`.
- Test direction (`higher_is_better`) is defined individually for each test.

Do not introduce additional business rules without approval.

## 6. Java Coding Conventions

- Use clear and descriptive English names.
- Follow standard Java naming conventions.
- Prefer constructor injection.
- Use Java enums for controlled sets of values.
- Use `LocalDate` for calendar dates.
- Use `BigDecimal` for precise numeric test results.
- Use appropriate Jakarta Persistence annotations.
- Use Jakarta Bean Validation for input validation.
- Avoid unnecessary Lombok usage unless explicitly approved.
- Avoid overly complex methods and classes.
- Prefer readable code over clever shortcuts.

Do not expose internal implementation details through API responses.

## 7. API Conventions

- REST API base path: `/api`.
- Use plural resource names.
- Use appropriate HTTP methods and status codes.
- Validate incoming request DTOs.
- Return consistent error responses.
- Do not expose JPA entities directly.
- Follow existing endpoint and DTO naming conventions.

Exact endpoint behavior must follow the relevant use-case documentation.

## 8. Angular Conventions

- Organize frontend code by functional features.
- Keep API communication in Angular services.
- Keep components focused on presentation and user interaction.
- Use TypeScript types or interfaces for API data.
- Implement frontend validation for usability.
- Do not rely on frontend validation as the only enforcement of business rules.
- Reuse shared components where appropriate.
- Avoid introducing global state management libraries without justification.

## 9. Testing Requirements

New business logic should have automated tests.

Use unit tests for:
- Business rules.
- Calculations.
- Validation logic.
- Service behavior where practical.

Use integration tests for:
- Repository operations.
- JPA mappings.
- REST endpoints.
- Flyway migrations.
- Database constraints.

Integration tests must use an isolated test database.

Do not make tests dependent on the developer's local database password or existing local data.

Do not skip tests merely to make a build pass without explaining why.

## 10. Implementation Workflow

For every requested feature:

1. Read the relevant architecture and data model documentation.
2. Read the relevant use-case specification, if available.
3. Inspect existing code before making changes.
4. Identify which files need to be created or modified.
5. Explain the proposed implementation briefly.
6. Implement only the requested scope.
7. Add or update automated tests.
8. Check consistency with the database schema.
9. Summarize all changes.
10. Report tests executed and their results.

Prefer small, focused changes.

Do not refactor unrelated code.

Do not silently change architectural decisions or business rules.

## 11. AI Assistant Behavior

Copilot is an implementation assistant, not the owner of project architecture or business requirements.

Copilot should:

- Follow project documentation.
- Reuse existing patterns.
- Explain non-obvious technical decisions.
- Point out potential inconsistencies.
- Ask questions when requirements are unclear.
- Avoid unnecessary code generation.
- Avoid modifying unrelated files.
- Never invent credentials or secrets.
- Never commit or push changes unless explicitly requested.

If implementation requires a change to architecture or data model, propose the change first and wait for approval.

## 12. Definition of Done

A feature is considered complete when:

- It meets the documented acceptance criteria.
- Its implementation follows the project architecture.
- Database changes use Flyway migrations where required.
- Input validation is implemented.
- Relevant automated tests are added or updated.
- Existing functionality is not unintentionally broken.
- The code is understandable and maintainable.
- The implementation has been reviewed by the project owner.

## 13. Documentation and Scope Rules

- Project documentation is the source of truth for architecture, requirements, and approved rules. If documentation conflicts, report the conflict rather than choosing a new rule independently.
- Before implementing frontend features, read the relevant documents in `docs/frontend/`, including requirements, UI design, architecture, and the applicable use-case catalog or specification.
- Do not independently change approved business rules or API contracts.
- Architectural changes require prior approval from the project owner.
- Keep each use-case implementation within that use case's scope; do not add related but unrequested functionality.
- If implementation requires changing an established decision, explain the conflict and obtain approval first, then update the relevant documentation.
- Do not introduce dependencies without technical justification and approval.
- Preserve existing documents and their references. Do not move or remove documentation without a clear reason and updates to all affected references.
