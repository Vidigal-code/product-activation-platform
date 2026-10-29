# Product Activation Platform

Product Activation Platform receives product activation requests, stores their status and calls an external provider. A request remains durable after application restarts, and concurrent submissions with the same external identifier cannot create duplicate records.

The application exposes a REST API and processes work asynchronously through PostgreSQL. Docker Compose starts the application, database and simulated provider together. The local Swagger panel runs complete scenarios when an example is selected, including request creation, polling and expected-result checks.

## Technology

| Area | Implementation |
| --- | --- |
| Runtime | Java 21 and Spring Boot 3.5.16 |
| HTTP API | Spring MVC, separate DTOs, Bean Validation and ProblemDetail |
| Persistence | PostgreSQL 17, Spring Data JPA/Hibernate and Flyway |
| Provider | Spring RestClient, configurable timeouts and WireMock fixtures |
| Resilience | Durable retry scheduling, leases, ownership checks and Resilience4j |
| Security | OAuth 2.0 JWT Resource Server, scopes and consumer ownership |
| Observability | Actuator, Prometheus, ECS JSON logs and optional OpenTelemetry tracing |
| Verification | JUnit 5, Mockito, Testcontainers, ArchUnit, Node tests, Playwright and Newman |
| Delivery | Maven Wrapper, Dockerfile, Docker Compose and GitHub Actions |

Spring Boot manages compatible dependency versions. Explicit versions and build plugins are recorded in [pom.xml](https://github.com/Vidigal-code/product-activation-platform/blob/main/pom.xml).

## Request lifecycle

1. The API validates the input and commits a new request as `PENDING`.
2. A worker atomically claims eligible work and records `PROCESSING`, an ownership token and a lease deadline.
3. The worker calls the provider after the claim transaction has finished.
4. A new transaction records `COMPLETED`, `FAILED` or a scheduled return to `PENDING`.
5. The consumer reads the persisted result through the API.

Database uniqueness protects `externalId` under concurrency. An identical request from the same consumer returns the existing record. Different content or another consumer's identifier returns a conflict without exposing or overwriting that record.

## Guarantees and boundaries

Committed work does not depend on an in-memory task. An expired worker cannot overwrite a newer result. Provider calls do not keep a database transaction open. These guarantees protect local persistence; they do not guarantee exactly-once execution of a remote activation.

Timeouts can leave the remote result unknown. Automatic retry of uncertain operations requires a verified provider idempotency contract. The bundled WireMock environment simulates responses and does not provide durable deduplication, so that capability is disabled locally.

PostgreSQL supplies both storage and coordination. No separate message broker or cache is required. Cloud deployment, completion events and reconciliation are described as operational decisions or future work, not as deployed components.

## Documentation map

| Page | What it covers |
| --- | --- |
| [Requirements](?lang=en&menu=en&id=102) | Mandatory scope, delivered improvements and verification criteria |
| [Quick start](?lang=en&menu=en&id=103) | Starting Docker Compose and running automatic examples |
| [API](?lang=en&menu=en&id=104) | Endpoints, validation, duplicate handling and provider scenarios |
| [Architecture](?lang=en&menu=en&id=105) | Transactions, concurrency, retry policy and tradeoffs |
| [Security](?lang=en&menu=en&id=106) | JWT, ownership, quotas, request limits and secrets |
| [Testing](?lang=en&menu=en&id=107) | Test layers, commands, reports and CI |
| [Operations](?lang=en&menu=en&id=108) | Health, metrics, tracing, deployment and latency analysis |

The documentation site is static. API and Swagger addresses beginning with `localhost` refer to the application running on your own computer.
