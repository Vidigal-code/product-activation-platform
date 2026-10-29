# Operations and diagnosis

## Health, logs and metrics

Liveness describes process health. Readiness includes PostgreSQL connectivity. A temporary provider outage grows pending work; it should not trigger repeated restarts of otherwise healthy instances.

Logs use Spring Boot's ECS JSON format and correlation identifiers. The same correlation accompanies the provider call. Tokens, credentials and sensitive payloads must remain outside logs, traces and metric labels.

| Meter | Purpose |
| --- | --- |
| `activation.provider.calls` | Provider call duration, tagged by outcome |
| `activation.worker.results` | Processing result count |
| `activation.requests` | Persisted request totals for each state |
| `resilience4j.circuitbreaker.*` | Circuit state and behavior, with `name=activationProvider` |
| HTTP, JVM and datasource meters | Request latency/errors, memory, execution and connection-pool use |

Prometheus is available at `/actuator/prometheus` and requires `monitoring:read` in JWT mode. State totals refresh through one grouped query every 10 seconds by default, independently of worker enablement. A failed refresh preserves the previous sample.

State totals include retained terminal records and are global to the database. Multiple replicas see the same counts: aggregate with `max`, not `sum`. Queue age still requires a database query; it is not an implemented queue-age gauge. Avoid customer and external identifiers as metric labels.

The scheduler uses three threads by default so worker HTTP calls, metrics refresh and expired-quota cleanup can progress independently. Tune scheduling, database connections and worker capacity using measured load and provider latency.

## Optional local tracing

```sh
docker compose -f docker-compose.yml -f docker-compose.observability.yml up --build -d --wait
```

Open <http://localhost:16686>, execute a Swagger scenario or the Newman collection, and search for service `product-activation-platform`. The override adds Jaeger 2.22, enables OTLP/HTTP export and samples every trace for the demonstration.

W3C trace context is stored with the request, restored for the `activation.process` span and propagated to the provider HTTP call. Jaeger uses transient memory storage: restarting it clears traces. The UI is loopback-only; collector ports remain inside the Compose network.

OTLP export is disabled by default outside this override. A durable deployment needs a protected collector, retention and an appropriate sampling policy using `OTLP_TRACING_ENABLED`, `OTLP_TRACING_ENDPOINT` and `TRACING_SAMPLE_PROBABILITY`.

## Production slowdown scenario

Consider API latency increasing from 300 ms to 2.5 s, SQL from 50 ms to 1.2 s and provider calls from 400 ms to 3 s, while CPU is 45% and memory is 60%.

1. **First investigation:** correlate the onset with releases, traffic, data growth and provider incidents. Compare latency percentiles by endpoint/outcome, errors and queue age. Moderate CPU and memory do not rule out lock contention, I/O or connection waiting.
2. **Locate the delay:** separate thread/connection waits, SQL, transaction time and external HTTP. Compare traces with PostgreSQL activity, locks and query plans. Use `EXPLAIN (ANALYZE, BUFFERS)` on representative safe queries in a controlled environment; it executes the statement.
3. **Collect evidence:** correlation IDs, p50/p95/p99, timeout/retry counts, active/pending Hikari connections, thread dumps, GC pauses, slow SQL, locks, I/O and the oldest pending work. Record bounded outcomes without credentials or payloads.
4. **Choose improvements from evidence:** index demonstrated slow access patterns, improve inefficient SQL, shorten transactions and bound remote duration/concurrency. Size pools against measured database capacity. Adding cache or increasing every pool before finding the bottleneck can worsen contention.
5. **Test a common-cause hypothesis:** slow external calls may hold transactions/connections and amplify SQL and HTTP waits. Confirm overlap using traces and `pg_stat_activity`. This application avoids HTTP inside database transactions, but shared network problems or excessive retries can still slow both dependencies.
6. **Validate the correction:** compare p95/p99, errors, database waits, throughput and total activation duration under equivalent load. Roll out gradually, watch alert thresholds and retain a rollback option. A fast registration endpoint alone does not prove a healthy processing backlog.

## Production and multiple instances

```sh
docker build -t product-activation-platform:1.0.0 .
```

The runtime image uses Java 21 JRE, unprivileged UID 10001, container-aware heap sizing, graceful shutdown and a readiness check. Promote the same tested immutable image after vulnerability scanning.

Production requires a real provider, JWT mode, TLS, network restrictions, injected secrets and PostgreSQL with backups and restore drills. Keep management endpoints private. Apply compatible Flyway migrations through a controlled rollout and separate migration privileges from the runtime account.

Multiple instances share uniqueness, work claims and consumer quotas through PostgreSQL. A load balancer distributes traffic; workers claim work independently. Circuit breakers remain local to each instance.

A deployment stage would publish the verified image to a registry, deploy an immutable digest and observe readiness, errors and latency during gradual rollout. Keep the previous image available for rollback. These are deployment instructions; no backend cloud environment is provisioned by this repository.

## Limitations and evolution

The provider is simulated. A real contract must establish idempotency and status lookup before enabling automatic recovery of uncertain operations. Local database uniqueness does not guarantee exactly-once remote activation, and local `FAILED` does not prove that the provider performed no action.

There is no public restart endpoint for terminal requests and no implemented reconciliation interface. Reconciliation must query the provider, record evidence and authorize any subsequent action through an audited process.

Completion events would use an outbox written in the final-state transaction and a separate publisher. No outbox, broker or completion-event publication is currently implemented. Introduce a broker only when measured volume, fan-out or delivery requirements justify its operating cost.

JWT mode depends on an external identity provider. Local mode and automatic Swagger are loopback demonstrations. Long-lived operation also requires request retention, dashboards, alert thresholds, durable telemetry and deployment automation appropriate to the environment. Deep-history workloads may require cursor pagination.

References: [persisted state metrics](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/observability/RequestMetrics.java), [observability override](https://github.com/Vidigal-code/product-activation-platform/blob/main/docker-compose.observability.yml), [Dockerfile](https://github.com/Vidigal-code/product-activation-platform/blob/main/Dockerfile) and [application configuration](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/resources/application.yml).

[Architecture](?lang=en&menu=en&id=105) · [Security](?lang=en&menu=en&id=106) · [Quick start](?lang=en&menu=en&id=103)
