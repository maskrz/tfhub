# TFHub Frontend Architecture

**Status:** Initial version<br>
**Scope:** Technical architecture and development conventions for the Angular frontend<br>
**Out of scope:** Detailed screen designs, detailed user workflows, and deployment architecture

## 1. Purpose

This document defines the technical foundation for the TFHub frontend.

The frontend is an Angular application that communicates with the existing TFHub Spring Boot REST API. Authentication is part of the initial public-facing application scope, but it is planned and not yet implemented. Its architecture should remain straightforward, maintainable, and appropriate for the current size of the project while allowing the application to grow.

This document describes technical boundaries and conventions. It does not define what users can do or how individual screens should look. Those decisions will be documented separately when specific views and workflows are designed.

## 2. Architecture Principles

The frontend must follow these principles:

- **Keep it simple:** prefer the simplest solution that meets the current requirements.
- **Use Angular conventions:** follow recommended practices supported by the Angular version installed in the project.
- **Organize by feature:** keep code related to the same functional area together.
- **Separate responsibilities:** presentation components must not own HTTP communication or backend business rules.
- **Backend is authoritative:** the backend remains the source of truth for business rules, data integrity, and validation.
- **Avoid premature abstraction:** introduce shared abstractions only when there is a real, demonstrated need.
- **Minimize dependencies:** prefer Angular and browser capabilities already available in the project.
- **Keep contracts aligned:** frontend request and response types must reflect the actual backend API contracts.

## 3. Application Structure

The application should use a feature-oriented structure. The exact directory layout must follow the existing Angular project conventions and should be adjusted only when there is a practical reason.

A suggested starting point is:

```text
src/
└── app/
    ├── core/
    │   ├── layout/
    │   │   └── ...          # AppShellComponent, HeaderComponent, FooterComponent
    │   └── ...              # application-wide infrastructure
    ├── shared/
    │   └── ...              # genuinely reusable UI components and utilities
    ├── features/
    │   ├── auth/
    │   │   └── ...          # planned login view and authentication flow
    │   ├── athletes/
    │   │   └── ...          # athlete-related frontend code
    │   ├── tests/
    │   │   └── ...          # test-related frontend code
    │   └── results/
    │       └── ...          # result-related frontend code
    ├── app.config.ts
    ├── app.routes.ts
    └── app.ts
```

The structure is a guideline, not a requirement to create every directory immediately.

The application shell composes `HeaderComponent`, Angular `RouterOutlet`, and `FooterComponent`. The shell renders the header and footer once; `RouterOutlet` displays the selected feature view. Feature views contain only their own page content and should be independently structured Angular components, preferably standalone. See the shared [UI guidelines](ui-guidelines.md).

### 3.1 Core

The `core` area contains application-wide infrastructure that has a single application-level responsibility, for example:

- HTTP interceptors.
- Global error-handling infrastructure.
- Application-wide services.
- Infrastructure required by the application shell.
- Authentication state and session-checking support.

Feature-specific logic must not be placed in `core`.

### 3.2 Shared

The `shared` area contains reusable elements that are genuinely used by multiple features, for example:

- Reusable presentation components.
- Pipes and directives.
- Small, generic utilities.

Do not place feature-specific components or services in `shared` merely to make them globally accessible.

### 3.3 Features

The `features` area contains code belonging to individual functional areas.

A feature should keep its components, services, types, and other implementation details close to the functionality they support. Avoid creating empty layers or folders before they are needed.

Features should not access one another's internal implementation directly. If cross-feature reuse becomes necessary, extract a small shared abstraction only when its responsibility is clear.

## 4. Components and Application Logic

Components are responsible primarily for presenting data and handling user interaction.

Conventions:

- Keep components focused and reasonably small.
- Avoid putting HTTP requests directly in components.
- Move reusable application logic into appropriate services or focused helper functions.
- Keep backend business rules out of components.
- Use Angular dependency injection for services and other dependencies.
- Prefer explicit component inputs and outputs for communication between components.
- Avoid unnecessary component-to-component coupling.
- Extract reusable presentation components when a real reuse case exists.

Do not introduce a generic component framework or a complex state architecture in advance.

## 5. API Communication and Data Contracts

All communication with the backend must go through Angular services using Angular's HTTP capabilities.

### 5.1 API Services

- Keep HTTP calls out of presentation components.
- Place API services close to the feature they serve unless the service is genuinely application-wide.
- Keep API service methods focused on backend operations.
- Centralize the API base URL in application configuration.
- Do not hardcode environment-specific URLs in feature components or services.
- Use the existing backend endpoints. Do not invent endpoints.

### 5.2 TypeScript Models

- Define TypeScript types or interfaces for API request and response payloads.
- Align these definitions with the actual backend DTOs.
- Keep request and response types distinct when their structures or purposes differ.
- Do not expose or model JPA entities as if they were API contracts.
- Do not add frontend-only fields to API payloads unless the API contract explicitly supports them.

The backend documentation and implementation are authoritative when determining API contracts.

### 5.3 Error Handling

- Handle HTTP errors consistently.
- Preserve useful validation details returned by the backend.
- Avoid duplicating the same error-handling logic across components.
- Use an HTTP interceptor only for genuinely cross-cutting behavior.
- Feature-specific error interpretation should remain close to the relevant feature.

## 6. Forms and Validation

Use Angular Reactive Forms for forms that require structured state, validation, or dynamic behavior.

Conventions:

- Keep form definitions and validation logic organized and understandable.
- Use client-side validation to provide timely feedback to users.
- Treat backend validation as authoritative.
- Handle validation errors returned by the API.
- Do not independently redefine business constraints in the frontend.
- Avoid duplicating validation rules in multiple places when a shared frontend rule is justified.

This section defines technical conventions only. It does not prescribe specific fields or validation rules for individual forms.

## 7. Routing

Use Angular Router for application routing.

Conventions:

- Keep route configuration clear and maintainable.
- Use lazy loading where it provides a meaningful benefit, particularly for feature areas.
- Keep route definitions separate from unrelated component implementation details.
- Use Angular route guards to keep unauthenticated users out of protected views once authentication is implemented. Guards improve navigation and user experience; backend access control remains the security boundary.
- Do not define specific application routes or navigation items in this document.

The actual route structure will be defined alongside the relevant UI and feature specifications.

### 7.1 Authentication flow

The planned initial frontend includes a login view, authentication state handling, protected application routes, and logout functionality. Authentication uses the server-side session established by Spring Security and its browser-managed session cookie. Do not use JWT or store credentials or session identifiers in browser storage.

The frontend should check the current session through the backend, treat HTTP `401` as unauthenticated, and handle HTTP `403` as a forbidden request rather than as an automatic logout. The login and CSRF API contract is defined in `docs/security.md`. Authentication remains planned until implemented and verified.

## 8. Application State

Start with Angular's built-in state management capabilities.

- Keep local UI state close to the component that owns it.
- Share state through services only when multiple parts of the application genuinely need it.
- Do not introduce NgRx or another external state management library without a demonstrated requirement and prior approval.
- Avoid maintaining a second, inconsistent copy of backend data.
- Define clear ownership and update behavior for any state shared across components.

The application should not adopt a global state-management architecture merely in anticipation of future complexity.

## 9. Loading, Empty, and Error States

Frontend features should handle the main asynchronous states explicitly:

- Loading or in-progress state.
- Successful response with data.
- Successful response with no data.
- API or network error.
- Form submission in progress and submission failure, where applicable.

The presentation of these states belongs to the UI design and feature specifications. This document establishes only the technical expectation that asynchronous states must be handled deliberately.

## 10. Testing

Use the testing framework and tooling already configured in the Angular project.

Testing should focus on meaningful behavior:

- Unit tests for services and utility logic.
- Component tests for important component behavior.
- Tests for API request construction and response/error handling.
- Tests for important form validation and interaction logic.
- Integration tests where they provide meaningful confidence across component and service boundaries.

Do not add or replace testing frameworks as part of ordinary feature implementation without justification and approval.

## 11. Dependencies and Architectural Changes

- Prefer built-in Angular capabilities and existing project dependencies.
- Do not add a library for functionality that can be implemented simply with existing tools.
- Every new dependency must have a clear technical justification.
- Significant changes to the application structure or architectural approach require approval before implementation.
- Keep dependency versions compatible with the Angular version and the rest of the project.

## 12. Relationship with the Backend

The frontend is a client of the existing backend, not a second implementation of its domain logic.

Therefore:

- The backend owns business rules and data integrity.
- The backend owns authoritative validation.
- The frontend may perform basic validation for usability, but must not be treated as a security or data-integrity boundary.
- API contracts must be derived from the backend DTOs and documented endpoints.
- Changes to backend contracts may require corresponding frontend updates.
- Frontend implementation must not silently change backend behavior or business rules.

Refer to the backend documentation under `docs/backend/` for backend architecture, data model, and use-case specifications.

## 13. Out of Scope

The following topics are intentionally not defined here:

- Functional requirements.
- Detailed screen and page layouts.
- Visual design, colors, typography, and responsive breakpoints.
- Navigation items and user workflows.
- Detailed frontend use cases.
- Security configuration.
- Production deployment and infrastructure.
- Statistics, rankings, and charts.

These topics will be documented separately when their scope and decisions are established.

Shared visual and layout principles are defined in [UI guidelines](ui-guidelines.md); detailed view requirements belong in `views/`.

## 14. Open Decisions

The following decisions should be made only when they become relevant:

- Exact feature-level directory and file naming conventions, based on the first implemented features.
- UI component library or design system, if one is needed.
- Whether any shared application state requires a dedicated solution.

Do not resolve these decisions by introducing dependencies or abstractions prematurely.
