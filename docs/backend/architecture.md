# TFHub – Architecture

**Status:** Initial version  
**Version:** 1.0  
**Project:** Track & Field Hub

## 1. Project Overview

TFHub is a web application designed to support athletics coaching and athlete performance tracking.

The application allows a coach to:
- Manage athletes.
- Define and manage athletic tests.
- Record test results from training sessions and competitions.
- Track athlete performance over time.
- Compare athletes and their results.
- Analyze performance trends and statistics.
- Identify personal bests and records.

### 1.1 Current scope

The application supports administrator, coach, and athlete accounts. Spring Security provides session authentication; backend services and queries enforce role and data-scope authorization.

Public registration and user/group management screens are outside the initial scope. Accounts and relationships are provisioned locally or managed directly in PostgreSQL. Coaches access only athletes in assigned training groups; athlete accounts access only linked athlete records. The security architecture and implementation requirements are defined in [`docs/security.md`](../security.md).

### 1.2 Technology stack

| Component | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4 |
| Build tool | Maven |
| API | REST |
| Persistence | Spring Data JPA, Hibernate |
| Database | PostgreSQL 17 |
| Database migrations | Flyway |
| Frontend | Angular |
| Frontend styling | SCSS |
| Version control | Git, GitHub |
| Local infrastructure | Docker Compose |

## 2. Architectural Style

TFHub follows a **modular monolith with layered architecture inside functional modules**.

The application is deployed and developed as one backend application, but its code is organized into separate functional modules.

Each module is responsible for a specific business area.

The architecture combines:
- Modular organization by business functionality.
- Layered separation of responsibilities.
- REST communication between frontend and backend.
- Centralized relational database.

### 2.1 Architectural principles

1. Keep the architecture simple and understandable.
2. Separate business logic from HTTP and persistence concerns.
3. Group related functionality into independent modules.
4. Avoid unnecessary abstractions and frameworks.
5. Keep business rules in the service layer.
6. Use DTOs for API communication.
7. Use Flyway as the only mechanism for database schema changes.
8. Write automated tests for business logic and important integration paths.
9. Avoid introducing dependencies without a clear justification.
10. Prefer maintainable and predictable solutions over premature optimization.

### 2.2 Explicitly excluded architectural patterns

The initial implementation does not require:
- Microservices.
- Event sourcing.
- CQRS.
- Full Domain-Driven Design.
- Distributed transactions.
- Message brokers.
- Complex dependency injection abstractions.

These approaches may be considered in the future if actual requirements justify them.

## 3. Backend Architecture

### 3.1 Package structure

Backend code is organized by functional modules.

The implemented structure separates classes by responsibility within each
functional module:

```text
pl.tfhub.api/
├── athlete/
│   ├── controller/
│   ├── dto/request/
│   ├── dto/response/
│   ├── entity/
│   ├── enums/
│   ├── exception/
│   ├── repository/
│   └── service/
├── test/
│   ├── controller/
│   ├── dto/request/
│   ├── dto/response/
│   ├── entity/
│   ├── enums/
│   ├── exception/
│   ├── repository/
│   └── service/
├── result/
│   ├── controller/
│   ├── dto/request/
│   ├── dto/response/
│   ├── entity/
│   ├── enums/
│   ├── exception/
│   ├── repository/
│   └── service/
├── user/
│   ├── entity/
│   ├── enums/
│   ├── repository/
│   └── service/
├── traininggroup/
│   ├── entity/
│   ├── repository/
│   └── service/
├── config/
├── common/
│   ├── exception/
│   └── validation/
└── TfhubApiApplication.java
```

Create only packages required by actual classes. Controllers, request and
response DTOs, entities, enums, repositories, services, and module-specific
exceptions belong in their respective responsibility subpackages. Application
configuration belongs in `config`.

Global exception handling and genuinely shared validation utilities belong in
`common` (for example, `common.exception` and `common.validation`). Exceptions
that represent a specific module's business conditions belong in that module's
`exception` package. Do not move module-specific behavior into `common`.

Do not create empty packages or generic abstractions without an actual use case.

### 3.2 Layer responsibilities

#### Controller

Responsible for:
- Exposing REST endpoints.
- Receiving HTTP requests.
- Validating incoming request DTOs.
- Calling the appropriate service.
- Returning HTTP responses.

Controllers must not contain business logic or directly access repositories.

#### Service

Responsible for:
- Implementing business operations.
- Enforcing business rules.
- Coordinating repository operations.
- Managing transaction boundaries.
- Converting entities into response DTOs when appropriate.

Services are the primary location for business logic.

Use `@Transactional` where an operation requires transactional consistency. Use read-only transactions for queries where appropriate.

#### Repository

Responsible for:
- Database access.
- CRUD operations.
- Custom database queries.
- Filtering and retrieving data.

Repositories should use Spring Data JPA.

Repositories must not contain business logic.

#### Entity

Responsible for representing persistent domain data.

Entities:
- Are mapped to PostgreSQL tables using Jakarta Persistence.
- Must not be returned directly from REST controllers.
- Should not contain HTTP-specific logic.
- Should reflect the agreed database model.

#### DTO

Responsible for defining the API contract.

Use separate request and response DTOs.

DTOs:
- Protect the internal entity model from direct exposure.
- Define which data can be submitted or retrieved.
- Support API validation.
- Prevent accidental modification of internal fields.

Do not introduce unnecessary DTO layers for internal service-to-service calls unless they provide a clear benefit.

### 3.3 Dependency direction

The expected dependency flow is:

```text
Controller
    |
    v
Service
    |
    v
Repository
    |
    v
Database
```

Controllers may depend on services.

Services may depend on repositories.

Repositories may depend on JPA and database infrastructure.

Lower layers must not depend on controllers.

Modules must not directly access another module's repository. Cross-module
operations should use the other module's public service layer.

Avoid circular dependencies between modules.

### 3.4 Authentication and authorization boundary

Spring Security configuration belongs in application-wide `config/`. Login, logout, and current-session endpoints expose safe DTOs only. `user/` contains account identity and role persistence, not public user-management endpoints. Services and repository queries must enforce role, group, and athlete ownership for both reads and writes; URL rules alone are insufficient. No registration or user/group-management UI is included.

## 4. Frontend Architecture

Angular is responsible for:
- User interface.
- Navigation.
- Forms and input validation.
- Displaying athlete and test data.
- Displaying test results and statistics.
- Communicating with the backend REST API.

### 4.1 Proposed structure

```text
src/app/
│
├── core/
│   ├── services/
│   ├── interceptors/
│   └── models/
│
├── shared/
│   ├── components/
│   ├── pipes/
│   └── directives/
│
├── features/
│   ├── athletes/
│   │   ├── pages/
│   │   ├── components/
│   │   └── services/
│   │
│   ├── tests/
│   │   ├── pages/
│   │   ├── components/
│   │   └── services/
│   │
│   └── results/
│       ├── pages/
│       ├── components/
│       └── services/
│
└── app.routes.ts
```

This is an initial organizational proposal. The structure should grow with actual functionality.

### 4.2 Frontend principles

1. Organize code by feature.
2. Keep API communication in Angular services.
3. Do not implement business rules exclusively in the frontend.
4. Use TypeScript interfaces or types for API data.
5. Reuse common UI components where appropriate.
6. Keep components focused on presentation and user interaction.
7. Use Angular routing for navigation.
8. Avoid unnecessary global state management libraries.

## 5. Frontend–Backend Communication

The frontend communicates with the backend through REST API endpoints over HTTP.

The API uses JSON for request and response bodies.

### 5.1 API conventions

- Base path: `/api`
- Resource names: plural nouns.
- Use HTTP methods according to their semantics.
- Use appropriate HTTP status codes.
- Use DTOs for all API request and response bodies.
- Validate incoming data on the backend.

Example endpoints:

```text
GET    /api/athletes
GET    /api/athletes/{id}
POST   /api/athletes
PUT    /api/athletes/{id}
PATCH  /api/athletes/{id}/deactivate

GET    /api/tests
POST   /api/tests

GET    /api/results
POST   /api/results
PUT    /api/results/{id}
DELETE /api/results/{id}
```

These are illustrative endpoint conventions. Exact endpoints and behavior must be defined in the relevant use-case documentation.

### 5.2 API versioning

No explicit API versioning is required initially.

The API may evolve while the application is in active development.

Breaking changes should be coordinated with the frontend.

## 6. Database and Persistence

PostgreSQL is the primary persistent storage.

Spring Data JPA and Hibernate are used for ORM.

### 6.1 Database schema management

Flyway is the sole owner of database schema changes.

Rules:
- Every schema change must be introduced through a new Flyway migration.
- Existing migration files must never be modified after they have been applied.
- Migration files use the naming convention `V<version>__<description>.sql`.
- Hibernate must not automatically modify the schema.

Hibernate configuration:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

### 6.2 Database conventions

- Table and column names use English snake_case.
- Tables use plural names.
- Primary keys use `BIGINT` identity columns.
- Foreign keys must explicitly represent relationships.
- Required fields use `NOT NULL`.
- Database constraints should enforce important data-integrity rules.
- Enum-like values are stored as strings, not PostgreSQL native enums.
- Historical records must be preserved where business requirements require it.

### 6.3 Transaction management

Transactions are managed by Spring.

Business operations that modify multiple related records must execute atomically where necessary.

Transaction boundaries should normally be defined at the service layer.

## 7. Validation and Error Handling

### 7.1 Validation

Validation should occur at two levels:

**Backend input validation**
- Jakarta Bean Validation.
- DTO annotations such as `@NotNull`, `@NotBlank`, `@Positive` and `@Min`.
- Custom validation where business rules require it.

**Database validation**
- `NOT NULL`.
- `UNIQUE`.
- `CHECK`.
- Foreign key constraints.

Frontend validation improves usability but does not replace backend validation.

### 7.2 Error handling

Use centralized exception handling with Spring `@RestControllerAdvice`.

The API should return consistent error responses.

Expected error categories include:
- Validation errors – HTTP 400.
- Missing resources – HTTP 404.
- Business rule violations – HTTP 400 or 409, depending on the situation.
- Unexpected server errors – HTTP 500.

Do not expose stack traces, database credentials or internal implementation details in API responses.

## 8. Testing Strategy

Testing should cover different levels of the application.

### 8.1 Unit tests

Test isolated business logic.

Examples:
- Athlete validation rules.
- Test result validation.
- Personal best calculations.
- Comparison and ranking logic.

Unit tests should not require a running PostgreSQL database.

### 8.2 Integration tests

Verify cooperation between application components.

Examples:
- REST endpoints.
- Service and repository interaction.
- JPA mappings.
- Flyway migrations.
- Database constraints.

Integration tests should use an isolated test database.

Testcontainers may be introduced to provide PostgreSQL for integration tests.

Tests must not depend on the developer's local database password or existing local data.

### 8.3 Frontend tests

Test important UI behavior, form validation and frontend services.

## 9. Configuration and Secrets

Environment-specific configuration must not be committed to Git.

Rules:
- Keep secrets in environment variables or local ignored configuration files.
- Provide `.env.example` with placeholder values.
- Never commit passwords, tokens or private credentials.
- Keep development and test configuration separate.
- Do not hardcode database credentials in source code.

## 10. Logging and Monitoring

Spring Boot Actuator may be used for local or internal health checks. Do not expose Actuator endpoints publicly through Cloudflare or Nginx. Local Docker health checks or other internal checks are acceptable; do not add a public health endpoint without a concrete requirement.

Use structured and meaningful application logs.

Do not log:
- Passwords.
- Authentication tokens.
- Sensitive personal information unnecessarily.

Additional monitoring infrastructure is not required initially.

## 11. Deployment and Infrastructure

Local development infrastructure is managed using Docker Compose.

PostgreSQL runs in a Docker container.

Backend and frontend may run directly from the development environment.

Production deployment details will be defined separately when the hosting environment is selected.

## 12. Development Workflow

Development should follow an incremental implementation process.

For each feature:

1. Define the use case.
2. Confirm business rules and acceptance criteria.
3. Update documentation if necessary.
4. Implement backend functionality.
5. Add automated tests.
6. Implement frontend functionality.
7. Verify end-to-end behavior.
8. Review the generated code.
9. Commit the completed change.

AI coding assistants may be used to generate and modify code, but they must follow the architecture and project instructions.

Architectural decisions must be reviewed and approved before implementation.

## 13. AI Coding Assistant Guidelines

GitHub Copilot should follow these rules:

1. Read and follow this architecture document.
2. Follow the data model and use-case documentation.
3. Do not invent new business requirements.
4. Do not modify database schema outside Flyway migrations.
5. Do not bypass the service layer.
6. Do not expose JPA entities directly through REST endpoints.
7. Do not introduce new frameworks or dependencies without justification.
8. Follow existing naming and package conventions.
9. Add tests for new business logic.
10. Do not refactor unrelated parts of the application without explicit instruction.
11. Ask for clarification when requirements are ambiguous or contradictory.
12. Prefer small, focused changes over large rewrites.

## 14. Future Considerations

The architecture should remain open to future expansion:

- User and training-group management screens.
- Parent accounts and multiple athlete profiles per account.
- Training plans and training sessions.
- Advanced performance analytics.
- Import of external competition results.
- Historical performance reports.
- Data export.

These features are not part of the initial implementation and should not be added without an explicit requirement.

---

**End of document**