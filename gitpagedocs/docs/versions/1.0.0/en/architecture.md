# Architecture and processing

The project is organized by feature. The request feature separates API DTOs/controllers, business rules, domain state, persistence and provider integration. Security, web error handling, documentation and observability have their own supporting packages. Domain rules do not depend on the HTTP layer, and controllers do not access persistence directly.

## Durable work in PostgreSQL

The persisted request also represents the unit of work. Creation commits `PENDING` before processing begins. Workers claim eligible rows atomically using PostgreSQL concurrency features, including `FOR UPDATE SKIP LOCKED`.

During insertion, Hibernate obtains the initial `createdAt`, `updatedAt` and internal `nextAttemptAt` values from PostgreSQL. Claims and retries use the same database clock. This prevents an application JVM clock running ahead from artificially delaying new requests, without a schema migration or changes to identifiers, states or the HTTP contract. Historical timestamps are not corrected; changes to PostgreSQL's own clock can still affect scheduling and lease expiry.

A claim records `PROCESSING`, a lease deadline and an ownership token. The claim transaction finishes before the HTTP call. A separate transaction records the outcome only if the token still owns an unexpired lease. This permits multiple instances and prevents a late worker from overwriting a result after another worker has acquired the request.

| Current state | Allowed next state | Purpose |
| --- | --- | --- |
| `PENDING` | `PROCESSING` | Claim an attempt |
| `PENDING` | `FAILED` | End work for an operational condition without another call |
| `PROCESSING` | `PENDING` | Schedule a retry or defer while the circuit is open |
| `PROCESSING` | `COMPLETED` | Provider confirms activation |
| `PROCESSING` | `FAILED` | Permanent failure, exhausted budget or unsafe uncertainty |
| `COMPLETED` / `FAILED` | None | Terminal states |

The API does not restart terminal requests. Replaying an external identifier returns its existing state.

## Uniqueness and transaction boundaries

The database enforces global uniqueness of `external_id`. An initial lookup is not sufficient to prevent concurrent inserts. If insertion loses a uniqueness race, its transaction rolls back before the winning row is queried in another transaction.

The winner is returned only when consumer and content match. Conflicting content or ownership yields a generic conflict. The same identity restriction applies to lookup and listing. JPA handles creation and reads; PostgreSQL-specific operations handle atomic work claims and ownership checks.

HTTP integration never holds an open database transaction. `spring.jpa.open-in-view=false` also prevents web rendering from extending persistence access.

## Provider outcome policy

| Provider outcome | Decision |
| --- | --- |
| `2xx` | Record `COMPLETED`; no retry |
| Permanent `4xx`, including `400` | Record `FAILED`; do not resend unchanged invalid input |
| Transient server failure | Retry within the attempt budget, then record `FAILED` |
| `429` / `503` | Apply the bounded retry policy and consider `Retry-After` |
| Timeout or ambiguous connection failure | Without confirmed idempotency, record `FAILED` for reconciliation; otherwise allow bounded retries |
| Expired lease after interruption | Recover within the budget only when the provider contract makes replay safe |
| Open circuit | Persist a return to `PENDING` and restore the unused HTTP attempt budget |

The application sends a stable `Idempotency-Key` based on the request UUID. This does not by itself provide exactly-once remote execution. The provider must guarantee deduplication, concurrent-call handling, retention and identical-payload semantics.

WireMock simulates status codes and delay, not durable remote effects. Compose therefore sets `PROVIDER_IDEMPOTENCY_SUPPORTED=false`. Retrying `500` also assumes that the real provider allows replay after that response. If an activation can complete before `500`, treat the result as uncertain and require idempotency or reconciliation.

## Retry, timeout and circuit settings

Defaults are three attempts, a one-second initial delay, a 30-second backoff cap and a 30-second lease. Jitter randomly reduces calculated backoff to 80–100%; `RETRY_JITTER_FACTOR=0` disables it. The worker owns retry scheduling; the HTTP client does not introduce another independent retry loop.

A valid positive `Retry-After`, expressed in seconds or as an HTTP date, remains the minimum delay even when it exceeds the backoff cap. Malformed or negative values fall back to normal backoff. Numeric values outside the supported scheduling range end the request with `PROVIDER_RETRY_AFTER_UNSUPPORTED`, avoiding an early retry.

Connection/read timeouts default to two/five seconds in the application and one/two seconds in Compose. The lease must exceed the maximum HTTP attempt duration plus the time needed to persist the result. Lease expiry does not interrupt an HTTP request already in progress.

The circuit breaker observes 20 calls, requires at least 10 calls and opens at a 50% failure threshold. It stays open for 10 seconds and then permits two half-open probes. Transient and uncertain outcomes count as failures; business rejections do not. Open circuits avoid HTTP and defer work persistently without consuming an actual provider attempt. Circuit state is local to each instance.

## Decisions and tradeoffs

| Option | Assessment |
| --- | --- |
| Synchronous processing | Simpler, but exposes callers to provider latency and still needs a strategy for the commit/HTTP gap |
| Memory-only tasks | Lose committed processing intent after a crash |
| Additional broker | Useful when volume or fan-out justifies it, but introduces another operational dependency |
| PostgreSQL-backed work | Reuses the database for durable coordination, leases and attempt tracking |

The chosen approach reduces operating cost. Its tradeoffs are polling load and limited worker throughput. Sustained growth should lead to measured changes in concurrency, indexes, batching and retention before adding infrastructure.

Completion events would require an outbox persisted alongside the final state and a separate publisher. This is a documented evolution, not an implemented event pipeline. See [Operations](?lang=en&menu=en&id=108) for reconciliation, deployment and remaining limitations.

Implementation references: [work queue](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/request/persistence/ActivationQueue.java), [state machine](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/request/domain/ActivationStatus.java), [retry policy](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/request/integration/RetryPolicy.java) and [architecture decision record](https://github.com/Vidigal-code/product-activation-platform/blob/main/docs/requirements.md).

[API](?lang=en&menu=en&id=104) · [Security](?lang=en&menu=en&id=106) · [Operations](?lang=en&menu=en&id=108)

[Architecture decision records (English and Portuguese)](https://github.com/Vidigal-code/product-activation-platform/blob/main/docs/adr/README.md).
