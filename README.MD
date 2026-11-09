# TFHub — Track & Field Hub

TFHub is a web application for managing and analyzing athletic performance data.

The project aims to provide an easy-to-use tool for tracking athletes, recording test results, monitoring progress, and comparing performance over time.

## Project Goals

- Manage athletes and their basic information.
- Define and manage athletic performance tests.
- Record test results with dates.
- Track individual athlete progress over time.
- Compare athletes by age, birth year, and gender.
- Visualize performance trends and statistics.
- Maintain historical records and group statistics.

## Technology Stack

| Component | Technology |
|---|---|
| Backend | Java 21, Spring Boot |
| Frontend | Angular |
| Database | PostgreSQL 17 |
| ORM | Spring Data JPA / Hibernate |
| Build Tool | Maven |
| Containerization | Docker, Docker Compose |
| Version Control | Git |

## Project Structure

```text
tfhub/
├── docker-compose.yml
├── tfhub-api/          # Spring Boot REST API
├── tfhub-frontend/     # Angular application
└── docs/
    ├── backend/        # Backend architecture, data model, and use cases
    ├── frontend/       # Frontend requirements, design, architecture, and use cases
    ├── deployment/     # Deployment plans
    └── security.md     # Security requirements
```

## Getting Started

### Prerequisites

Make sure the following tools are installed:

- Java JDK 21
- Node.js and npm
- Docker Desktop with Docker Compose
- Git

### 1. Clone the repository

```bash
git clone https://github.com/maskrz/tfhub.git
cd tfhub
```

### 2. Configure environment variables

Create a `.env` file in the project root based on the provided example:

```bash
# Windows PowerShell
Copy-Item .env.example .env
```

Set the PostgreSQL password in `.env`:

```properties
POSTGRES_PASSWORD=your_password
```

Do not commit `.env` or any other files containing secrets.

The development Compose file binds PostgreSQL to `127.0.0.1:5432` only. Raspberry Pi production deployment uses the separate `docker-compose.prod.yml` stack and does not publish PostgreSQL or the backend.

### 3. Start PostgreSQL

From the project root, run:

```bash
docker compose up -d
```

Check the container status:

```bash
docker compose ps
```

PostgreSQL should become healthy before starting the backend.

### 4. Start the backend

The backend uses the `DB_PASSWORD` environment variable to connect to PostgreSQL. It must match the password configured in the root `.env` file.

From the project root, open a terminal:

```powershell
$env:DB_PASSWORD="your_password"
cd tfhub-api
.\mvnw.cmd spring-boot:run
```

The API will be available at:

```text
http://localhost:8080
```

Health check endpoint:

```text
http://localhost:8080/actuator/health
```

### 5. Start the frontend

Open another terminal:

```powershell
cd tfhub-frontend
npm.cmd install
npm.cmd start
```

The Angular application will be available at:

```text
http://localhost:4200
```

## Development Status

**Backend:** Initial athlete, test, and result use cases are implemented. Their scope and status are tracked in the [backend use-case catalog](docs/backend/use-cases/README.md).

**Frontend:** Angular 22 project scaffold is present. The functional frontend areas are planned in the [frontend use-case catalog](docs/frontend/use-cases/README.md); the requirements and shared design direction are documented under `docs/frontend/`.

**Security and production deployment:** Session-based authentication and authorization are implemented. The Raspberry Pi Compose deployment and operating instructions are documented in [docs/deployment/architecture.md](docs/deployment/architecture.md); public tunnel access remains a future setup.

Statistics, rankings, and charts remain future scope.

## License

Not specified yet.