# Product Activation Platform

Serviço de ativação de produtos com API REST, processamento persistente e PostgreSQL.

## 🇺🇸 English Description

<details>
<summary><strong>View Details</strong></summary>

### Purpose and scope

[Read the documentation](https://vidigal-code.github.io/product-activation-platform/docs/), available in Portuguese, English and Spanish.

Receive activation requests, keep their status and call an external provider. The implementation uses Java 21, Spring Boot 3.5.16, Spring MVC, Spring Data JPA/Hibernate, PostgreSQL 17, Flyway, Bean Validation and Maven Wrapper. Tests use JUnit 5, Mockito, Testcontainers and WireMock. Spring Boot manages compatible dependency versions; explicit overrides are listed in `pom.xml`.

The required challenge scope is creation, lookup, duplicate prevention under concurrency, provider integration, safe errors, security design, tests, production analysis, Docker and execution instructions. Selected improvements include durable asynchronous processing, leases, stale-worker protection, JWT authorization, correlation, health checks, migrations and CI. The [requirements matrix](docs/requirements.md) separates required functionality from optional improvements.

### Run from a clean checkout

Prerequisites: Git, Docker Engine/Desktop with Linux containers and Docker Compose v2. Java is unnecessary for the complete Docker environment. Allow approximately 2 GB of free container memory; the first build also downloads dependencies.

Windows PowerShell:

```powershell
Set-Location product-activation-platform
.\scripts\setup-local.ps1
docker compose up --build -d --wait
docker compose ps
```

Linux/macOS:

```sh
cd product-activation-platform
sh scripts/setup-local.sh
docker compose up --build -d --wait
docker compose ps
```

The setup script creates an ignored `.env` with a random database password and preserves any existing file. `.env.example` documents adjustable ports. Do not commit `.env`. If the selected ports are occupied, change them before starting the environment. Changing a password in `.env` does not change an already initialized PostgreSQL volume: rotate the database credential consistently.

| Service | Local address |
| --- | --- |
| API | `http://localhost:8080` |
| Readiness | `http://localhost:8080/actuator/health/readiness` |
| Liveness | `http://localhost:8080/actuator/health/liveness` |
| Provider administration | `http://localhost:8089/__admin/mappings` |
| PostgreSQL | `localhost:5432`, database/user `activation` |

Compose explicitly selects `SECURITY_MODE=local`, which disables authentication for this demonstration. All published ports bind to `127.0.0.1`. Use the default JWT mode and a TLS gateway for any shared environment.

```sh
docker compose logs -f app
docker compose stop
docker compose up -d --wait
docker compose down
```

`down` preserves the database volume. `docker compose down --volumes` permanently deletes local requests; use it only to intentionally reset the demonstration.

### API walkthrough

PowerShell:

```powershell
$body = @{ externalId = 'EXT-12345'; customerId = 'CUST-10001'; productCode = 'INTERNET-500' } | ConvertTo-Json
$request = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/requests' -ContentType 'application/json' -Body $body
$request
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/requests/$($request.id)"
```

POSIX shell:

```sh
curl -i http://localhost:8080/api/v1/requests \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-request-001' \
  --data '{"externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500"}'
curl -i http://localhost:8080/api/v1/requests/REPLACE_WITH_RESPONSE_ID
```

New requests return `201 Created`, a `Location` header and the persisted request. Processing is asynchronous: poll the returned location for the final status. Responses contain `id`, `externalId`, `customerId`, `productCode`, `status`, `createdAt` and `updatedAt`; timestamps use UTC/ISO-8601. DTOs are separate from persistence entities.

| Situation | Result |
| --- | --- |
| New valid request | `201 Created` with `Location` |
| Same external ID and content | `200 OK`, existing request; no new activation job |
| Same external ID, different content | `409 Conflict`, no overwrite |
| External ID belongs to another consumer | `409 Conflict`, no record details |
| Invalid body, field or UUID | `400 Bad Request` |
| Request absent or owned by another consumer | `404 Not Found` |
| Missing/invalid token in JWT mode | `401 Unauthorized` |
| Insufficient scope | `403 Forbidden` |
| Unexpected internal error | `500`, generic ProblemDetail |

The database `UNIQUE` constraint is the final authority for `externalId`. A failed insert rolls back before the winning record is queried in a separate transaction. Two concurrent equal requests therefore produce one row, with creation and replay responses. External IDs are globally unique and case sensitive; reuse after failure returns the existing failed request.

`externalId` and `customerId` accept 1–100 characters; `productCode` accepts 1–80. Each starts with an ASCII letter or digit and then permits letters, digits, `.`, `_`, `:` and `-`. Whitespace is rejected rather than silently trimmed. Unknown JSON properties are rejected. Responses also expose an attempt count and a sanitized failure code when relevant. `X-Correlation-Id` accepts 1–64 letters, digits, `.`, `_` or `-`; invalid/missing values are replaced with a generated identifier.

`GET /api/v1/requests?status=COMPLETED&customerId=CUST-10001&page=0&size=20` lists only the current consumer's requests. Filters are optional. The response contains `items`, `page`, `size`, `totalElements`, `totalPages` and `hasNext`; ordering is `createdAt ASC, id ASC`. Defaults are size 20, maximum size 100 and maximum page 10000, configured through `activation.pagination`. Bounded offset pagination avoids unbounded page sizes; deep-history workloads should evolve toward cursors and retention.

[Migration V2](src/main/resources/db/migration/V2__isolate_consumers_and_index_request_queries.sql) creates three listing indexes:

| Index | Columns | Intended query |
| --- | --- | --- |
| `ix_activation_consumer_created` | `(consumer_id, created_at, id)` | Consumer's requests without an optional filter |
| `ix_activation_consumer_status_created` | `(consumer_id, status, created_at, id)` | Consumer's requests filtered by status |
| `ix_activation_consumer_customer_created` | `(consumer_id, customer_id, created_at, id)` | Consumer's requests filtered by customer |

Equality predicates come first, followed by the stable ordering columns. This supports each access pattern without indexing every filter combination. When both optional filters are supplied, PostgreSQL may choose one access path and evaluate the remaining predicate; an optimal plan is not guaranteed. Verify representative query plans with `EXPLAIN (ANALYZE, BUFFERS)` in a controlled environment before adding another composite index, accounting for its storage and write cost. The separate `UNIQUE(external_id)` constraint protects deduplication.

### Processing and provider policy

The request row also holds persistent work. A worker claims eligible rows atomically with PostgreSQL locking, commits, calls the provider outside the database transaction and records the outcome in a new transaction. A lease allows recovery after a crash. An ownership token protects against a late worker overwriting a newer result. Multiple instances share the same database without a separate queue.

Normal transitions are `PENDING → PROCESSING → COMPLETED/FAILED`. Retryable failures return to `PENDING` with a persisted due time and attempt count. Terminal states cannot be restarted through the public API. A stable `Idempotency-Key` identifies each request at the provider; delivery is not guaranteed exactly once.

| Provider result | Decision |
| --- | --- |
| `2xx` | `COMPLETED`; no retry |
| `400` and other permanent `4xx` | `FAILED`; same invalid input is not sent again |
| `500` and transient server failures | Limited retries with increasing delay, then `FAILED` |
| `429` / `503` | Limited retry policy also considers `Retry-After` |
| Timeout/connection ambiguity | Without provider idempotency, `FAILED` for reconciliation; with a verified idempotency contract, bounded retries |
| Expired lease after worker failure | Without provider idempotency, stop uncertain work for reconciliation; with that contract, reclaim within the retry budget |

Defaults are three attempts, 1-second initial delay, a 30-second backoff cap and a 30-second lease. A valid positive `Retry-After` is honored even beyond the backoff cap; malformed or negative values use normal backoff. Numeric values that exceed the supported scheduling range produce `FAILED` with `PROVIDER_RETRY_AFTER_UNSUPPORTED`, avoiding an early retry. Compose uses a 1-second connection timeout and a 2-second read timeout. The lease must exceed the maximum HTTP attempt duration plus the database update margin. See the [requirements](docs/requirements.md) and [architecture decision records](docs/adr/README.md).

Retry jitter reduces calculated backoff randomly to 80–100% by default (`RETRY_JITTER_FACTOR=0.2`); zero disables it. `Retry-After` remains the minimum accepted delay. The Resilience4j circuit breaker observes a window of 20 calls, at least 10 calls and a 50% failure threshold; transient and uncertain failures count, while business rejections do not. It opens for 10 seconds, then permits two half-open probes. An open circuit avoids HTTP, returns work to `PENDING` with a persisted delay and restores its unused attempt budget. All limits are configurable through `CIRCUIT_BREAKER_*`; circuit state is local to each instance.

WireMock simulates response contracts, not real side effects or durable deduplication, so Compose sets `PROVIDER_IDEMPOTENCY_SUPPORTED=false`. Enabling this option for a real provider requires a documented guarantee covering concurrent calls, retention, identical payloads and recovery. A `500` retry also assumes the provider contract permits replay after that response; otherwise treat it as uncertain and reconcile. A timeout does not prove the activation failed remotely.

Use a fresh `externalId` for each demonstration:

| `productCode` | Mock result |
| --- | --- |
| `INTERNET-500` or another ordinary code | `200` |
| `BUSINESS-ERROR` | `400` |
| `TEMPORARY-ERROR` | `500` |
| `TIMEOUT` | `200` delayed for 5 seconds |
| `RATE-LIMITED` | `429` with `Retry-After: 2` |
| `UNAVAILABLE` | `503` with `Retry-After: 2` |

The mock requires `Idempotency-Key`. Its bounded request journal is for local diagnostics only and must not receive real customer data or credentials.

### Swagger and automated Postman scenarios

With Compose running, open [Swagger UI](http://localhost:8080/swagger-ui/index.html) or the [OpenAPI document](http://localhost:8080/v3/api-docs). Compose enables `API_DOCS_ENABLED=true` for the local demonstration; documentation is disabled by default outside it.

In Swagger UI, choose an **Exemplo automático**. Selection starts the scenario immediately: the runner generates fresh identifiers, fills the body, follows the created request and checks the expected HTTP responses and terminal status. Progress and response JSON appear in the same panel. **Executar novamente** starts a fresh run; **Cancelar** stops local requests/polling but does not undo a request already persisted by the server. The native Swagger operations remain available for manual requests. The automatic panel is intended for the local demonstration and does not store credentials or tokens.

Run all HTTP scenarios automatically, without copying request IDs:

```sh
docker compose --profile test run --rm newman
```

The temporary Newman container uses the application on the Compose network. It checks creation, lookup, identical replay, conflicting content, validation, malformed JSON, unknown IDs, media types, oversized bodies (`413`), filtered pagination, all six provider fixtures, health, metrics and 12 concurrent creates (one `201`, eleven `200`, one ID). A final bounded burst verifies consumer throttling (`429` and `Retry-After`). Each run generates unique external IDs, follows `Location`, and polls until a terminal state with bounded counts and elapsed time. Any failed assertion makes the command fail. The profile is not part of normal application startup.

For the Postman application, import [the collection](postman/product-activation.postman_collection.json) and [the local environment](postman/local.postman_environment.json), select that environment and run the **whole collection** in Collection Runner. `baseUrl` defaults to `http://localhost:8080`; adjust it if `APP_PORT` changes. Polling settings, expected retries, `requestsPerWindow` and `maxRequestBodyBytes` must match application configuration. The burst test intentionally exhausts local quota; the next run waits for the returned `Retry-After` before creation. These scenarios require the bundled provider fixtures and the documented retry/idempotency policy.

`bearerToken` is empty in local mode. Against a JWT test environment, provide a real token with `requests:read`, `requests:write` and `monitoring:read` without exporting or committing it. JWT `401`/`403` and cookie/Basic rejection are verified by the Maven security tests; the local collection does not claim to exercise identity-provider authorization without an issuer.

### Tests and continuous integration

Install JDK 21 and keep Docker running to execute all tests:

```powershell
.\mvnw.cmd -B -ntp verify
```

```sh
./mvnw -B -ntp verify
```

`verify` runs unit tests, integration tests against disposable PostgreSQL containers, packages the application and produces the JaCoCo report. Docker access is required; integration tests are not silently skipped when it is unavailable. Tests create isolated data and do not use the Compose database. Use `./mvnw test` for the faster unit/component feedback loop, with `verify` as the delivery gate.

Unit tests live under `src/test/unit/java` and cover policies, configuration, the domain state machine, architectural dependency rules with ArchUnit, correlation and error formatting. Integration tests under `src/test/integration/java` cover HTTP/JWT contracts, API behavior and PostgreSQL transitions, uniqueness, concurrency, transaction recovery and worker ownership. End-to-end tests under `src/test/e2e/java` exercise the running HTTP application. WireMock supplies deterministic provider failures and delays. Reports are under `target/surefire-reports`, `target/failsafe-reports` and `target/site/jacoco`.

Use `./mvnw test` for unit tests, `./mvnw -Dit.test='*IT' verify` for unit plus integration tests, `./mvnw -Dit.test='*E2E' verify` for unit plus end-to-end tests, and `./mvnw verify` for all layers. On Windows, replace `./mvnw` with `.\mvnw.cmd`.

With Node.js 22 or later, use the following commands to validate automatic Swagger scenarios and browser interaction:

```sh
npm ci
npm run test:unit
npx playwright install chromium
docker compose up -d --wait
npm run test:e2e
```

`test:unit` runs `node --test src/test/unit/javascript/*.test.cjs` independently of a backend. Browser tests use an isolated Chromium session against the running Compose application; screenshots and reports are saved under `target/swagger-e2e`. These Node dependencies are for development and CI only; the production image serves the scripts directly without a frontend build.

The [CI workflow](.github/workflows/ci.yml) runs on `main`, `dev` and pull requests: Java 21, Node.js 22, Maven verification, JavaScript unit tests, Compose validation, Docker build, browser interaction, automated Newman scenarios and report upload. Actions use verified commit hashes and minimum repository permissions. Local development proceeds on `dev`; validated changes are promoted to `main` with focused commits.

### Security and configuration

The default mode is an OAuth 2.0 JWT Resource Server. Configure `JWT_ISSUER_URI` and `JWT_AUDIENCE`; clients obtain tokens from the identity provider using Client Credentials. Tokens must have a trusted signature, matching issuer and audience, and valid lifetime. Public keys come from the issuer's discovery/JWKS endpoints and rotate through the identity provider. Reading requires `requests:read`; creation requires `requests:write`; Prometheus requires `monitoring:read`. Health probes expose no details.

CSRF protection is disabled for system-to-system clients using explicit `Authorization: Bearer` headers. Cookies and Basic never authenticate requests; JSON submitted as `text/plain` is rejected. Statelessness alone does not prevent CSRF. Enable CSRF before introducing browser clients, cookie authentication or Basic, following [Spring Security's CSRF guidance](https://docs.spring.io/spring-security/reference/6.5/features/exploits/csrf.html). Local mode has no authentication and remains restricted to the loopback demonstration.

Ownership uses the validated JWT `sub`, which must be nonblank and at most 255 characters; callers cannot choose it in the JSON body. Lookup and listing enforce that identity, and replay never exposes another consumer's record. External IDs remain globally unique. Local mode uses `local-demo`. Migration V2 assigns historical rows to `local-demo`; a JWT deployment with existing data requires an audited ownership migration because the original consumer cannot be inferred.

The API permits 300 requests per consumer per one-minute window by default (`RATE_LIMIT_MAX_REQUESTS`, `RATE_LIMIT_WINDOW`). Each window starts at that consumer's first request; it is not a calendar minute or sliding-window algorithm. PostgreSQL atomically shares the quota across instances. Authorized API calls, including validation errors and missing records, consume quota; health and metrics do not. Exhaustion returns `429` and `Retry-After`. A burst near a window boundary can reach twice the configured allowance. Expired counters are removed in bounded batches every minute (`RATE_LIMIT_CLEANUP_INTERVAL`).

Bodies are limited to 16 KiB (`REQUEST_MAX_BODY_BYTES`) and rejected with `413` before deserialization. A known oversized `Content-Length` is rejected before authentication/quota; a streamed body is checked while read, after the quota check. Keep stricter edge limits appropriate to deployment.

Store the provider credential in a secret manager and inject `PROVIDER_TOKEN` at deployment; keep it out of source control and logs. Rotate through the secret manager, deploy/reload configuration, and revoke the old credential after the overlap period. Client credentials belong to the calling systems, not this Resource Server. `local` mode is only for the bundled demonstration.

When `PROVIDER_TOKEN` is set, `PROVIDER_BASE_URL` must use HTTPS. HTTP is accepted only for the exact loopback hosts `localhost`, `127.0.0.1` and `[::1]`, supporting local integration tests. Invalid authenticated transport configuration prevents application startup.

Main configuration groups are `DATABASE_*`, `PROVIDER_*`, `RETRY_*`, `WORKER_*`, `SECURITY_MODE` and `JWT_*`. Actual defaults and supported keys are in [application.yml](src/main/resources/application.yml). URL, timeout, retry and credential settings are externalized. PostgreSQL checks, indexes and Flyway migrations protect persisted invariants. `spring.jpa.open-in-view=false` and explicit short transactions keep remote latency away from connection ownership.

`PROVIDER_ACTIVATION_PATH` defaults to `/provider/v1/activate` and is resolved under `PROVIDER_BASE_URL`; the path must stay within the configured provider origin.

### Production and observability

Build with `docker build -t product-activation-platform:1.0.0 .`. Promote the same immutable image after tests and vulnerability scanning. The runtime uses Java 21 JRE, UID 10001, container-aware heap sizing, graceful shutdown and a readiness health check. In production, use managed PostgreSQL with backups/restore drills, a real provider URL, JWT mode, TLS, network restrictions and secrets injected by the platform. Keep management endpoints private and restrict metrics access. Run schema changes with a controlled rollout and separate migration privileges from the runtime account.

Request correlation flows into logs and provider calls. Liveness checks the process; readiness includes PostgreSQL connectivity. A provider outage increases the queue but does not automatically require restarting healthy instances. Prometheus exposes supported Micrometer metrics at `/actuator/prometheus`; monitor request and provider latency, errors, pool wait time, queue age, retry counts and final outcomes. Never use customer IDs or external IDs as metric labels.

Application meters include `activation.provider.calls` (timer tagged by `outcome`), `activation.worker.results` (counter tagged by `result`) and `activation.requests` (gauge tagged by the four persisted states), alongside Spring's HTTP, JVM and datasource metrics. One grouped query refreshes state counts every 10 seconds (`METRICS_REFRESH_INTERVAL`), independently of worker enablement; a failed refresh preserves the previous sample. Counts include retained terminal rows, so configure retention for long-lived deployments. Multiple replicas observe the same global counts: aggregate with `max`, not `sum`. Queue age still requires a database query.

Logs use Spring Boot's ECS JSON format (`LOG_FORMAT=ecs`), with correlation and bounded outcome fields. The scheduler uses three threads by default (`SCHEDULING_POOL_SIZE`), allowing worker HTTP calls, metrics refresh and rate-counter maintenance to progress independently. Tune against database capacity and provider latency.

For an optional local tracing demonstration, run:

```sh
docker compose -f docker-compose.yml -f docker-compose.observability.yml up --build -d --wait
```

Open [Jaeger UI](http://localhost:16686), run the Newman collection, then search for service `product-activation-platform`. The override adds Jaeger 2.22, enables OTLP/HTTP export and samples every trace locally. W3C context is persisted with the request, restored for `activation.process`, and propagated to the provider HTTP call. Circuit metrics use `resilience4j.circuitbreaker.*` with `name=activationProvider`. Jaeger uses transient memory storage; restarting it clears traces. Its UI is bound to loopback and collector ports remain inside the Compose network. Outside this demo, OTLP export defaults to disabled; configure `OTLP_TRACING_ENABLED`, `OTLP_TRACING_ENDPOINT` and `TRACING_SAMPLE_PROBABILITY` for a protected production collector, retention and sampling policy.

For additional instances, distribute HTTP traffic and let workers claim database rows independently. A future completion event should use a transactional outbox alongside the state update before publishing to a broker. Add a broker only when volume, fan-out or delivery requirements justify its operating cost. No Redis, Kafka, RabbitMQ, LocalStack, Kubernetes or cloud account is required for this challenge.

A production delivery stage would publish the tested image to a private registry, deploy its immutable digest, apply compatible migrations and check readiness/error/latency indicators during gradual rollout. Keep the previous image available for rollback. Deployment credentials and secrets belong to the target platform; this repository's CI builds and validates without publishing to an external account.

### Production slowdown scenario

Given API latency `300 ms → 2.5 s`, SQL `50 ms → 1.2 s`, provider `400 ms → 3 s`, CPU 45% and memory 60%:

1. **First investigation:** correlate onset with releases, traffic, data growth and provider incidents. Compare latency percentiles by route and outcome, error rates and queue age; moderate CPU/memory do not rule out waiting or lock contention.
2. **Locate the delay:** split request duration into thread/connection waiting, SQL, transaction time and remote time. Compare traces and PostgreSQL activity/locks/query plans. Run `EXPLAIN (ANALYZE, BUFFERS)` on representative safe queries in a controlled environment because it executes the statement.
3. **Useful evidence:** correlation IDs, request/provider p50/p95/p99, timeout/retry counts, Hikari active/pending connections, thread dumps, GC pauses, PostgreSQL slow statements/locks/I/O and oldest pending work. Record outcomes without tokens or payloads.
4. **Candidate improvements:** index demonstrated slow access patterns, shorten transactions, fix inefficient SQL, bound remote time and concurrency, tune pools against measured database capacity, and isolate provider processing. Do not increase every pool or add cache without identifying the bottleneck.
5. **Common cause hypothesis:** slow provider calls hold transactions and connections, causing pool exhaustion and longer SQL/HTTP waits; verify overlap in traces and `pg_stat_activity`. This implementation keeps HTTP outside database transactions, but a shared network incident or retry surge can still affect both dependencies.
6. **Validate the fix:** compare p95/p99, error rate, database waits, throughput and end-to-end activation time at equivalent load. Use a staged release, alert thresholds and a rollback plan. A quick `POST` alone does not mean the activation backlog is healthy.

### Limitations and future improvements

The provider is simulated. A real contract must define uncertain outcomes and idempotency before enabling automatic recovery. Manual reconciliation must check the provider before any new attempt; no public endpoint restarts terminal work. JWT mode requires an external identity provider, as intended by the challenge.

Future operational work includes an audited reconciliation interface, completion events using a transactional outbox, production dashboards, alert thresholds and a managed telemetry destination. The database worker keeps operating cost low; sustained queue growth calls for measured batching/concurrency and retention management. Deployment must validate the provider's idempotency/reconciliation contract and apply environment-specific capacity and security policies.

</details>

## 🇧🇷 Descrição em Português

<details>
<summary><strong>Ver Detalhes</strong></summary>

### Objetivo e escopo

[Leia a documentação](https://vidigal-code.github.io/product-activation-platform/docs/), disponível em português, inglês e espanhol.

Receber solicitações de ativação, persistir seu estado e acionar um provedor externo. A solução utiliza Java 21, Spring Boot 3.5.16, Spring MVC, Spring Data JPA/Hibernate, PostgreSQL 17, Flyway, Bean Validation e Maven Wrapper. Os testes usam JUnit 5, Mockito, Testcontainers e WireMock. O Spring Boot gerencia versões compatíveis das dependências; versões explícitas estão em `pom.xml`.

O escopo obrigatório compreende criação, consulta, prevenção de duplicidade concorrente, integração, erros seguros, segurança, testes, análise de produção, Docker e instruções. Os diferenciais selecionados incluem processamento assíncrono persistente, reservas com vencimento, proteção contra workers atrasados, autorização JWT, correlação, health checks, migrações e CI. A [matriz de requisitos](docs/requirements.md) distingue obrigações e melhorias opcionais.

### Executar a partir de um clone limpo

Pré-requisitos: Git, Docker Engine/Desktop com contêineres Linux e Docker Compose v2. O ambiente completo não exige Java instalado na máquina. Reserve aproximadamente 2 GB de memória livre para os contêineres; o primeiro build também baixa dependências.

Windows PowerShell:

```powershell
Set-Location product-activation-platform
.\scripts\setup-local.ps1
docker compose up --build -d --wait
docker compose ps
```

Linux/macOS:

```sh
cd product-activation-platform
sh scripts/setup-local.sh
docker compose up --build -d --wait
docker compose ps
```

O script cria `.env`, ignorado pelo Git, com senha aleatória do banco e preserva um arquivo existente. `.env.example` contém as portas configuráveis. Não versione `.env`. Caso alguma porta esteja ocupada, altere-a antes de iniciar. Mudar a senha no arquivo não altera um volume PostgreSQL já inicializado: a credencial do banco também precisa ser rotacionada.

| Serviço | Endereço local |
| --- | --- |
| API | `http://localhost:8080` |
| Readiness | `http://localhost:8080/actuator/health/readiness` |
| Liveness | `http://localhost:8080/actuator/health/liveness` |
| Administração do provedor | `http://localhost:8089/__admin/mappings` |
| PostgreSQL | `localhost:5432`, banco/usuário `activation` |

O Compose seleciona explicitamente `SECURITY_MODE=local`, sem autenticação para a demonstração. Todas as portas publicadas usam `127.0.0.1`. Em um ambiente compartilhado, utilize o modo JWT padrão e um gateway com TLS.

```sh
docker compose logs -f app
docker compose stop
docker compose up -d --wait
docker compose down
```

`down` preserva o volume do banco. `docker compose down --volumes` exclui definitivamente as solicitações locais; utilize somente para reiniciar intencionalmente a demonstração.

### Experimentar a API

PowerShell:

```powershell
$body = @{ externalId = 'EXT-12345'; customerId = 'CUST-10001'; productCode = 'INTERNET-500' } | ConvertTo-Json
$request = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/requests' -ContentType 'application/json' -Body $body
$request
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/requests/$($request.id)"
```

Shell POSIX:

```sh
curl -i http://localhost:8080/api/v1/requests \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-request-001' \
  --data '{"externalId":"EXT-12345","customerId":"CUST-10001","productCode":"INTERNET-500"}'
curl -i http://localhost:8080/api/v1/requests/SUBSTITUA_PELO_ID_DA_RESPOSTA
```

Uma criação retorna `201 Created`, cabeçalho `Location` e a solicitação persistida. O processamento é assíncrono: consulte o endereço retornado para acompanhar o resultado. A representação contém `id`, `externalId`, `customerId`, `productCode`, `status`, `createdAt` e `updatedAt`, com datas UTC/ISO-8601. DTOs e entidades de persistência são separados.

| Situação | Resultado |
| --- | --- |
| Nova solicitação válida | `201 Created` com `Location` |
| Mesmo identificador e conteúdo | `200 OK`, registro existente; sem novo trabalho de ativação |
| Mesmo identificador, conteúdo diferente | `409 Conflict`, sem sobrescrever o registro |
| Identificador externo pertence a outro consumidor | `409 Conflict`, sem detalhes do registro |
| Corpo, campo ou UUID inválido | `400 Bad Request` |
| Registro inexistente ou de outro consumidor | `404 Not Found` |
| Token ausente/inválido no modo JWT | `401 Unauthorized` |
| Escopo insuficiente | `403 Forbidden` |
| Erro interno inesperado | `500`, ProblemDetail genérico |

A restrição `UNIQUE` do banco garante a unicidade de `externalId`. Uma inserção conflitante sofre rollback antes da consulta ao registro vencedor em outra transação. Duas solicitações idênticas simultâneas geram uma linha, com respostas de criação e repetição. O identificador é global, diferencia maiúsculas/minúsculas e continua reservado após uma falha; repetir a solicitação retorna o registro existente.

`externalId` e `customerId` aceitam de 1 a 100 caracteres; `productCode`, de 1 a 80. Todos começam com letra ASCII ou dígito e permitem letras, dígitos, `.`, `_`, `:` e `-` nas demais posições. Espaços são rejeitados, sem remoção silenciosa. Propriedades JSON desconhecidas são rejeitadas. A resposta também informa contador de tentativas e código de falha sanitizado, quando aplicável. `X-Correlation-Id` aceita de 1 a 64 letras, dígitos, `.`, `_` ou `-`; valores ausentes/inválidos são substituídos por um identificador gerado.

`GET /api/v1/requests?status=COMPLETED&customerId=CUST-10001&page=0&size=20` lista somente solicitações do consumidor atual. Os filtros são opcionais. A resposta contém `items`, `page`, `size`, `totalElements`, `totalPages` e `hasNext`, ordenados por `createdAt ASC, id ASC`. Os padrões são tamanho 20, tamanho máximo 100 e página máxima 10000, configurados em `activation.pagination`. A paginação por offset é limitada; consultas de histórico profundo devem evoluir para cursores e retenção.

A [migração V2](src/main/resources/db/migration/V2__isolate_consumers_and_index_request_queries.sql) cria três índices para a listagem:

| Índice | Colunas | Consulta prevista |
| --- | --- | --- |
| `ix_activation_consumer_created` | `(consumer_id, created_at, id)` | Solicitações do consumidor sem filtro opcional |
| `ix_activation_consumer_status_created` | `(consumer_id, status, created_at, id)` | Solicitações do consumidor filtradas por estado |
| `ix_activation_consumer_customer_created` | `(consumer_id, customer_id, created_at, id)` | Solicitações do consumidor filtradas por cliente |

Os predicados de igualdade vêm primeiro, seguidos pelas colunas da ordenação estável. Assim, cada padrão de acesso é apoiado sem indexar todas as combinações de filtros. Quando os dois filtros opcionais são informados, o PostgreSQL pode escolher um caminho de acesso e avaliar o predicado restante; não há garantia de plano ótimo. Verifique planos representativos com `EXPLAIN (ANALYZE, BUFFERS)` em ambiente controlado antes de criar outro índice composto, considerando seu custo de armazenamento e escrita. A restrição separada `UNIQUE(external_id)` protege contra duplicidade.

### Processamento e política de integração

A própria linha da solicitação representa o trabalho persistente. O worker reserva registros elegíveis atomicamente com locks do PostgreSQL, confirma a transação, chama o provedor fora dela e grava o resultado em nova transação. A reserva tem prazo de validade para recuperação após queda. Um token de posse impede que um worker atrasado sobrescreva o resultado de outro. Múltiplas instâncias compartilham o banco, dispensando uma fila adicional.

O fluxo normal é `PENDING → PROCESSING → COMPLETED/FAILED`. Falhas repetíveis retornam a `PENDING`, mantendo contador de tentativas e próxima execução no banco. Estados terminais não são reiniciados pela API pública. Uma `Idempotency-Key` estável identifica a solicitação no provedor; não há promessa de execução exatamente uma vez.

| Retorno do provedor | Decisão |
| --- | --- |
| `2xx` | `COMPLETED`; encerra o processamento |
| `400` e demais `4xx` permanentes | `FAILED`; não repete a mesma entrada inválida |
| `500` e falhas temporárias do servidor | Tentativas limitadas com intervalo crescente; `FAILED` ao esgotar |
| `429` / `503` | Política limitada também considera `Retry-After` |
| Timeout/conexão com resultado incerto | Sem idempotência no provedor, `FAILED` para reconciliação; com contrato confirmado, repetição limitada |
| Reserva vencida após falha do worker | Sem idempotência no provedor, interrompe trabalho incerto para reconciliação; com contrato, recupera dentro do limite de tentativas |

Os padrões são três tentativas, intervalo inicial de 1 segundo, teto de backoff de 30 segundos e reserva de 30 segundos. Um `Retry-After` positivo válido é respeitado mesmo acima desse teto; valores malformados ou negativos utilizam o backoff normal. Valores numéricos que excedem o intervalo de agendamento suportado produzem `FAILED` com `PROVIDER_RETRY_AFTER_UNSUPPORTED`, evitando repetição antecipada. O Compose utiliza conexão de 1 segundo e leitura de 2 segundos. A reserva precisa exceder a duração máxima de uma chamada HTTP e a margem para atualizar o banco. Consulte os [requisitos](docs/requirements.md) e os [registros de decisão arquitetural](docs/adr/README.md).

O jitter reduz aleatoriamente o backoff calculado para 80–100% por padrão (`RETRY_JITTER_FACTOR=0.2`); zero desativa essa variação. `Retry-After` continua sendo o atraso mínimo aceito. O circuit breaker Resilience4j observa uma janela de 20 chamadas, mínimo de 10 e limiar de falhas de 50%; falhas transitórias e incertas contam, rejeições de negócio não. Ele abre por 10 segundos e permite duas sondagens em meio-aberto. Enquanto aberto, evita HTTP, devolve o trabalho a `PENDING` com atraso persistido e restitui a tentativa não utilizada. Os limites usam `CIRCUIT_BREAKER_*`; o estado do circuito é local a cada instância.

O WireMock simula respostas, sem efeitos de ativação ou deduplicação durável; por isso o Compose usa `PROVIDER_IDEMPOTENCY_SUPPORTED=false`. Ativar a opção com um provedor real exige contrato documentado para concorrência, retenção da chave, conteúdo idêntico e recuperação. Repetir um `500` também pressupõe autorização pelo contrato do provedor; caso contrário, trate o resultado como incerto e reconcilie. Timeout não prova que a ativação falhou no sistema externo.

Use um `externalId` novo em cada demonstração:

| `productCode` | Resposta simulada |
| --- | --- |
| `INTERNET-500` ou outro código comum | `200` |
| `BUSINESS-ERROR` | `400` |
| `TEMPORARY-ERROR` | `500` |
| `TIMEOUT` | `200` após atraso de 5 segundos |
| `RATE-LIMITED` | `429` com `Retry-After: 2` |
| `UNAVAILABLE` | `503` com `Retry-After: 2` |

O mock exige `Idempotency-Key`. Seu histórico limitado de chamadas serve apenas ao diagnóstico local; não envie dados reais de clientes ou credenciais.

### Swagger e cenários Postman automatizados

Com o Compose em execução, abra o [Swagger UI](http://localhost:8080/swagger-ui/index.html) ou o [documento OpenAPI](http://localhost:8080/v3/api-docs). O Compose habilita `API_DOCS_ENABLED=true` na demonstração local; a documentação fica desabilitada por padrão fora dela.

No Swagger UI, selecione um **Exemplo automático**. A seleção inicia o cenário imediatamente: o executor gera identificadores novos, preenche o corpo, consulta a solicitação criada e verifica os retornos HTTP e o estado final esperado. O progresso e o JSON das respostas aparecem no mesmo painel. **Executar novamente** inicia uma nova execução; **Cancelar** interrompe as chamadas/consultas locais, mas não desfaz uma solicitação já persistida no servidor. As operações nativas do Swagger continuam disponíveis para chamadas manuais. O painel automático destina-se à demonstração local e não armazena credenciais nem tokens.

Execute todos os cenários HTTP automaticamente, sem copiar identificadores:

```sh
docker compose --profile test run --rm newman
```

O contêiner temporário Newman acessa a aplicação pela rede do Compose. Verifica criação, consulta, repetição idêntica, conflito, validação, JSON malformado, registro inexistente, tipo de conteúdo, corpo excessivo (`413`), paginação filtrada, seis cenários do provedor, saúde, métricas e 12 criações simultâneas (um `201`, onze `200`, um ID). Uma rajada limitada ao final verifica a cota por consumidor (`429` e `Retry-After`). Cada execução gera identificadores externos únicos, segue `Location` e consulta até o estado terminal com limites de tentativas e tempo. Qualquer assertiva inválida falha o comando. O profile não participa da inicialização normal da aplicação.

No aplicativo Postman, importe [a coleção](postman/product-activation.postman_collection.json) e [o ambiente local](postman/local.postman_environment.json), selecione o ambiente e execute **a coleção inteira** no Collection Runner. `baseUrl` inicia em `http://localhost:8080`; ajuste se alterar `APP_PORT`. Intervalo/limites de consulta, tentativas esperadas, `requestsPerWindow` e `maxRequestBodyBytes` devem corresponder à configuração da aplicação. A rajada esgota a cota local de propósito; a execução seguinte aguarda o `Retry-After` recebido antes da criação. Os cenários dependem dos mocks incluídos e da política documentada de repetição/idempotência.

`bearerToken` fica vazio no modo local. Em um ambiente JWT de teste, informe um token real com `requests:read`, `requests:write` e `monitoring:read`, sem exportá-lo nem versioná-lo. Os testes Maven verificam `401`/`403` e rejeição de cookie/Basic; a coleção local não afirma testar autorização do provedor de identidade sem um emissor configurado.

### Testes e integração contínua

Instale JDK 21 e mantenha o Docker em execução para rodar a verificação completa:

```powershell
.\mvnw.cmd -B -ntp verify
```

```sh
./mvnw -B -ntp verify
```

`verify` executa testes unitários e de integração com PostgreSQL descartável, empacota a aplicação e gera o relatório JaCoCo. O Docker é obrigatório; sua indisponibilidade não oculta nem pula testes de integração. Os dados dos testes são isolados do banco do Compose. `./mvnw test` oferece retorno mais rápido para testes unitários/componentes; a entrega exige `verify`.

Testes unitários ficam em `src/test/unit/java` e cobrem políticas, configuração, máquina de estados do domínio, dependências arquiteturais com ArchUnit, correlação e formatação de erros. Testes de integração em `src/test/integration/java` cobrem contratos HTTP/JWT, API e transições, unicidade, concorrência, recuperação de transações e posse no PostgreSQL. Testes end-to-end em `src/test/e2e/java` exercitam a aplicação HTTP em execução. O WireMock fornece falhas e atrasos determinísticos do provedor. Relatórios ficam em `target/surefire-reports`, `target/failsafe-reports` e `target/site/jacoco`.

Use `./mvnw test` para unitários, `./mvnw -Dit.test='*IT' verify` para unitários e integração, `./mvnw -Dit.test='*E2E' verify` para unitários e end-to-end, e `./mvnw verify` para todas as camadas. No Windows, substitua `./mvnw` por `.\mvnw.cmd`.

Com Node.js 22 ou superior, utilize os comandos abaixo para validar os cenários automáticos do Swagger e a interação no navegador:

```sh
npm ci
npm run test:unit
npx playwright install chromium
docker compose up -d --wait
npm run test:e2e
```

`test:unit` executa `node --test src/test/unit/javascript/*.test.cjs` sem depender de um backend. Os testes de navegador usam uma sessão isolada do Chromium contra a aplicação do Compose; capturas e relatórios ficam em `target/swagger-e2e`. As dependências Node são exclusivas do desenvolvimento e da CI; a imagem de produção serve os scripts diretamente, sem build de frontend.

O [workflow de CI](.github/workflows/ci.yml) executa em `main`, `dev` e pull requests: Java 21, Node.js 22, verificação Maven, testes unitários JavaScript, validação do Compose, build da imagem, interação no navegador, cenários Newman automatizados e publicação dos relatórios. As actions usam hashes de commit verificados e permissão mínima no repositório. O desenvolvimento ocorre em `dev`; mudanças validadas são promovidas para `main` com commits focados.

### Segurança e configuração

O modo padrão é OAuth 2.0 Resource Server com JWT. Configure `JWT_ISSUER_URI` e `JWT_AUDIENCE`; os consumidores obtêm tokens no provedor de identidade pelo fluxo Client Credentials. São validados assinatura confiável, emissor, audiência e validade temporal. As chaves públicas vêm dos endpoints de descoberta/JWKS do emissor, que também controla sua rotação. Consulta exige `requests:read`; criação exige `requests:write`; Prometheus exige `monitoring:read`. Os probes de saúde não expõem detalhes.

A proteção CSRF está desativada para clientes sistema-a-sistema com `Authorization: Bearer` explícito. Cookies e Basic não autenticam requisições; JSON enviado como `text/plain` é rejeitado. Ser stateless, isoladamente, não impede CSRF. Habilite CSRF antes de introduzir clientes de navegador, autenticação por cookie ou Basic, conforme a [orientação do Spring Security](https://docs.spring.io/spring-security/reference/6.5/features/exploits/csrf.html). O modo local não autentica e permanece restrito à demonstração em loopback.

A posse utiliza o `sub` do JWT validado, não vazio e com até 255 caracteres; o consumidor não pode escolhê-la pelo JSON. Consulta e listagem aplicam essa identidade, e a repetição nunca expõe registros de outro consumidor. Identificadores externos continuam globalmente únicos. O modo local usa `local-demo`. A migração V2 atribui registros históricos a `local-demo`; um ambiente JWT com dados existentes exige migração auditada de posse, pois o consumidor original não pode ser inferido.

A API permite, por padrão, 300 requisições por consumidor em uma janela de um minuto (`RATE_LIMIT_MAX_REQUESTS`, `RATE_LIMIT_WINDOW`). A janela começa na primeira requisição daquele consumidor; não corresponde ao minuto do relógio nem usa algoritmo de janela deslizante. O PostgreSQL compartilha a cota atomicamente entre instâncias. Chamadas autorizadas à API, inclusive erros de validação e registros ausentes, consomem cota; saúde e métricas não consomem. O excesso retorna `429` e `Retry-After`. Uma rajada na fronteira de duas janelas pode alcançar o dobro da cota. Contadores vencidos são removidos em lotes limitados a cada minuto (`RATE_LIMIT_CLEANUP_INTERVAL`).

O corpo tem limite de 16 KiB (`REQUEST_MAX_BODY_BYTES`) e recebe `413` antes da desserialização. Um `Content-Length` conhecido acima do limite é rejeitado antes da autenticação/cota; um corpo transmitido sem tamanho conhecido é verificado durante a leitura, após a cota. Aplique limites adicionais na borda conforme o ambiente.

Armazene a credencial externa em secret manager e injete `PROVIDER_TOKEN` no deploy, sem versioná-la nem registrá-la nos logs. Rotacione no gerenciador, atualize a configuração e revogue a credencial anterior após o período de coexistência. Credenciais Client Credentials pertencem aos consumidores; este serviço apenas valida os tokens. O modo `local` destina-se à demonstração incluída.

Quando `PROVIDER_TOKEN` estiver definido, `PROVIDER_BASE_URL` deve usar HTTPS. HTTP é aceito somente para os hosts de loopback exatos `localhost`, `127.0.0.1` e `[::1]`, permitindo testes locais de integração. A aplicação não inicializa com configuração inválida de transporte autenticado.

Os grupos de configuração são `DATABASE_*`, `PROVIDER_*`, `RETRY_*`, `WORKER_*`, `SECURITY_MODE` e `JWT_*`. A lista efetiva e os padrões estão em [application.yml](src/main/resources/application.yml). URLs, timeouts, repetições e credenciais são externos ao código. Restrições e índices PostgreSQL com migrações Flyway preservam invariantes. `spring.jpa.open-in-view=false` e transações curtas evitam manter conexões ocupadas pela latência remota.

`PROVIDER_ACTIVATION_PATH` tem padrão `/provider/v1/activate` e é resolvido sob `PROVIDER_BASE_URL`; o caminho deve permanecer na origem configurada do provedor.

### Produção e observabilidade

Gere a imagem com `docker build -t product-activation-platform:1.0.0 .` e promova o mesmo artefato imutável após testes e análise de vulnerabilidades. A execução usa JRE 21, UID 10001, heap ajustado ao contêiner, desligamento gracioso e health check de prontidão. Em produção, use PostgreSQL gerenciado com backups e exercícios de restauração, URL real do provedor, modo JWT, TLS, restrições de rede e segredos injetados pela plataforma. Mantenha endpoints de gestão privados e acesso restrito às métricas. Controle o rollout de migrações e separe privilégios de alteração do schema dos privilégios da aplicação.

A correlação acompanha logs e chamadas externas. Liveness verifica o processo; readiness inclui conectividade com PostgreSQL. Uma indisponibilidade do provedor aumenta a fila, sem justificar reiniciar automaticamente instâncias saudáveis. Métricas Micrometer são exportadas em `/actuator/prometheus`; acompanhe latência da API/provedor, erros, espera do pool, idade da fila, tentativas e resultados finais. Não utilize identificadores de clientes ou solicitações como labels de métricas.

Os medidores próprios incluem `activation.provider.calls` (timer por `outcome`), `activation.worker.results` (contador por `result`) e `activation.requests` (gauge pelos quatro estados persistidos), além das métricas HTTP, JVM e datasource do Spring. Uma consulta agrupada atualiza os totais a cada 10 segundos (`METRICS_REFRESH_INTERVAL`), independentemente do worker; uma falha preserva a amostra anterior. Os totais incluem registros terminais retidos: configure retenção para ambientes duradouros. Réplicas observam os mesmos totais globais; agregue com `max`, não com `sum`. A idade da fila ainda exige consulta ao banco.

Logs usam JSON ECS nativo do Spring Boot (`LOG_FORMAT=ecs`), com correlação e campos de resultado limitados. O agendador utiliza três threads por padrão (`SCHEDULING_POOL_SIZE`), permitindo que chamadas HTTP do worker, atualização de métricas e limpeza de cotas avancem independentemente. Ajuste conforme capacidade do banco e latência externa.

Para a demonstração local opcional de tracing, execute:

```sh
docker compose -f docker-compose.yml -f docker-compose.observability.yml up --build -d --wait
```

Abra o [Jaeger UI](http://localhost:16686), execute a coleção Newman e pesquise o serviço `product-activation-platform`. O override adiciona Jaeger 2.22, habilita exportação OTLP/HTTP e amostra todos os traces locais. O contexto W3C é persistido com a solicitação, restaurado em `activation.process` e propagado à chamada HTTP externa. Métricas do circuito usam `resilience4j.circuitbreaker.*` com `name=activationProvider`. O Jaeger armazena traces em memória transitória: reiniciá-lo apaga os dados. A interface utiliza loopback, e as portas do coletor ficam somente na rede do Compose. Fora da demonstração, a exportação OTLP fica desabilitada por padrão; configure `OTLP_TRACING_ENABLED`, `OTLP_TRACING_ENDPOINT` e `TRACING_SAMPLE_PROBABILITY` para um coletor de produção protegido, com retenção e amostragem adequadas.

Para múltiplas instâncias, distribua o tráfego e deixe os workers reservarem linhas independentemente. Um futuro evento de conclusão deve usar outbox transacional junto à atualização do estado, antes da publicação em um broker. Adicione fila/tópico apenas quando volume, consumidores e garantias de entrega justificarem o custo. Redis, Kafka, RabbitMQ, LocalStack, Kubernetes e conta cloud não são necessários para executar o desafio.

Uma etapa de entrega em produção publicaria a imagem testada em registro privado, implantaria seu digest imutável, aplicaria migrações compatíveis e acompanharia prontidão, erros e latência durante rollout gradual. Mantenha a imagem anterior disponível para rollback. Credenciais de deploy e segredos pertencem à plataforma de destino; a CI deste repositório constrói e valida sem publicar em uma conta externa.

### Cenário de lentidão em produção

Com API `300 ms → 2,5 s`, SQL `50 ms → 1,2 s`, provedor `400 ms → 3 s`, CPU 45% e memória 60%:

1. **O que investigar primeiro:** correlacionar o início com releases, tráfego, crescimento dos dados e incidentes do provedor. Comparar percentis por endpoint/resultado, erros e idade da fila. CPU e memória moderadas não descartam espera ou contenção.
2. **Como localizar o problema:** decompor a duração entre espera por thread/conexão, SQL, transação e chamada externa. Comparar traces, atividade/locks e planos do PostgreSQL. Executar `EXPLAIN (ANALYZE, BUFFERS)` em consultas representativas e seguras em ambiente controlado, pois o comando executa a instrução.
3. **Logs e métricas úteis:** correlação, p50/p95/p99 da API/provedor, timeouts/retries, conexões Hikari ativas/pendentes, thread dumps, pausas de GC, consultas lentas, locks/I/O e trabalho pendente mais antigo. Registrar resultados sem tokens ou payloads.
4. **Melhorias possíveis:** índices para acessos comprovadamente lentos, transações menores, SQL eficiente, limites de tempo/concorrência e dimensionamento do pool conforme a capacidade medida do banco. Isolar o processamento externo; não aumentar todos os pools nem adicionar cache sem diagnóstico.
5. **Hipótese de causa comum:** chamadas lentas mantêm transações/conexões abertas, esgotando o pool e ampliando espera de SQL/HTTP. Confirmar sobreposição em traces e `pg_stat_activity`. Esta implementação mantém HTTP fora das transações; incidentes de rede compartilhada ou excesso de retries ainda podem afetar ambas as dependências.
6. **Como validar após publicar:** comparar p95/p99, erros, espera do banco, vazão e duração total da ativação sob carga equivalente. Usar rollout gradual, alertas e plano de rollback. Um `POST` rápido não garante que a fila esteja saudável.

### Limitações e melhorias futuras

O provedor é simulado. Um contrato real precisa esclarecer resultados incertos e idempotência antes de habilitar recuperação automática. A reconciliação manual deve consultar o provedor antes de nova tentativa; não há endpoint público para reiniciar estados terminais. O modo JWT requer um provedor de identidade externo, conforme o escopo do desafio.

Evoluções operacionais incluem interface auditada de reconciliação, eventos de conclusão com outbox transacional, dashboards, limites de alerta e destino gerenciado para telemetria. O worker no banco reduz custo operacional; crescimento sustentado da fila exige medir concorrência, processamento em lotes e retenção. O deploy deve validar o contrato real de idempotência/reconciliação e aplicar políticas de capacidade e segurança do ambiente.

</details>

### Referências técnicas / Technical references

- [Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/index.html), [Spring Security JWT Resource Server](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html).
- [Maven Wrapper](https://maven.apache.org/tools/wrapper/), [PostgreSQL version support](https://www.postgresql.org/support/versioning/).
- [Docker Compose startup order](https://docs.docker.com/compose/how-tos/startup-order/), [WireMock Docker](https://wiremock.org/docs/standalone/docker/).
- [GitHub setup-java](https://github.com/actions/setup-java), [GitHub checkout](https://github.com/actions/checkout).
- [Micrometer gauges](https://docs.micrometer.io/micrometer/reference/1.15/concepts/gauges.html), [Jaeger 2.22](https://www.jaegertracing.io/docs/2.22/getting-started/), [ArchUnit](https://www.archunit.org/userguide/html/000_Index.html).
