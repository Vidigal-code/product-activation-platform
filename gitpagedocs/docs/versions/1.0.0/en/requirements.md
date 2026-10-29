# Requirements and delivery

The specification requires a service that registers product activation requests, queries their status and integrates with an external provider. The distinction below separates required behavior from implemented improvements and documented future work.

## Required functionality

| Requirement | Delivery |
| --- | --- |
| Java, Spring Boot, Maven and relational persistence | Java 21, Spring Boot 3.5.16, Maven Wrapper and PostgreSQL 17 |
| Create and validate requests | `POST /api/v1/requests`, separate DTOs and Bean Validation |
| Persist required identifiers, status and dates | `externalId`, `customerId`, `productCode`, lifecycle status and UTC timestamps |
| Query a request by identifier | `GET /api/v1/requests/{id}` with `404` for an unavailable record |
| Prevent duplicates, including concurrent calls | Database `UNIQUE(external_id)` and winner lookup after transaction rollback |
| Track the four required states | `PENDING`, `PROCESSING`, `COMPLETED` and `FAILED`, with explicit transitions |
| Handle provider success, rejection, server failure and timeout | Configurable HTTP client, persistent outcomes and WireMock fixtures |
| Return consistent, safe errors | Central ProblemDetail responses and sanitized failure codes |
| Describe or implement JWT/OAuth 2.0 security | Resource Server, scopes, issuer/audience validation and Client Credentials guidance |
| Test creation, duplicates, lookup and integration | Unit tests, HTTP contracts and PostgreSQL integration tests |
| Separate responsibilities and support maintenance | Feature-oriented modules with API, business, persistence and integration boundaries |
| Explain production latency investigation | Evidence, hypotheses and validation steps in [Operations](?lang=en&menu=en&id=108) |
| Provide Docker and execution instructions | Container image, Compose environment and [Quick start](?lang=en&menu=en&id=103) |
| Document decisions, limits and evolution | This documentation and the repository README |

## Implemented improvements

| Improvement | Behavior |
| --- | --- |
| Modern Java features | `record` DTOs and immutable boundary contracts; Spring Boot 3.x and Java 21 |
| HTTP creation and replay semantics | New requests return `201` with `Location`; identical replay returns `200`; conflict returns `409` |
| Durable asynchronous processing | PostgreSQL work claims, lease expiry, recovery policy and stale-worker protection |
| Controlled retries | Bounded attempts, increasing delay, jitter and preserved `Retry-After` |
| Circuit breaker | Temporary provider failures open a local circuit; deferred work retains its unused attempt budget |
| Consumer isolation | Validated JWT `sub` controls creation, lookup, listing and replay |
| Filtered pagination | Optional status/customer filters, bounded page size and stable ordering |
| Resource limits | PostgreSQL-shared consumer quotas, `429` with `Retry-After` and a body limit returning `413` |
| Database migrations | Flyway migrations, constraints and indexes |
| Operational visibility | Health probes, correlation, state gauges, latency/outcome metrics and JSON logs |
| Distributed tracing | W3C context persists across API, worker and provider; optional local Jaeger environment |
| Architecture checks | ArchUnit tests for dependency boundaries and a tested lifecycle transition matrix |
| Repeatable delivery | Maven verification, Docker build, browser scenarios and Newman in GitHub Actions |
| Interactive API examples | OpenAPI, an automatic Swagger scenario panel and a self-contained Postman collection |
| Focused delivery history | Small commits separate build, persistence, API, security, integration, tests and documentation, with messages explaining the change |
| Architecture decision records | English and Portuguese records in `docs/adr/` distinguish accepted decisions from proposed evolution |

## Documented scope and remaining work

Completion events and cloud deployment are architectural guidance. There is no deployed cloud account, external message broker or transactional outbox in this implementation. An outbox would be added in the same transaction as the terminal state before publishing completion events independently.

The external provider is simulated. A real integration must define idempotency, uncertain outcomes, credential rotation and permissible retries. WireMock does not prove the absence of duplicate real-world side effects.

An audited reconciliation interface remains future work. Before repeating an uncertain activation, operators must query the provider and record evidence. No public endpoint restarts terminal requests. Production dashboards, alert thresholds, retention policies and a managed telemetry destination also depend on the deployment environment.

## Acceptance checks

1. Run the setup script and `docker compose up --build -d --wait` from a clean checkout.
2. Select an automatic Swagger example and verify its HTTP result and terminal state.
3. Exercise replay and conflict; check that replay returns the same record and conflict does not overwrite it.
4. Run `./mvnw -B -ntp verify` with JDK 21 and Docker available. On Windows, use `.\mvnw.cmd`.
5. Run the JavaScript unit tests and browser tests described in [Testing](?lang=en&menu=en&id=107).
6. Run `docker compose --profile test run --rm newman` for the complete HTTP collection.
7. Inspect test reports, readiness and operational metrics.

The image build does not run Testcontainers inside Docker. CI verifies Java and JavaScript before building the image, then exercises the running Compose environment.

See the [repository requirements matrix](https://github.com/Vidigal-code/product-activation-platform/blob/main/docs/requirements.md) for scope and delivery criteria, [Architecture](?lang=en&menu=en&id=105) for guarantees and tradeoffs, and [Testing](?lang=en&menu=en&id=107) for executable verification.

[Architecture decision records (English and Portuguese)](https://github.com/Vidigal-code/product-activation-platform/blob/main/docs/adr/README.md).
