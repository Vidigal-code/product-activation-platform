# Security and configuration

The default mode is an OAuth 2.0 Resource Server using JWT. Calling systems obtain access tokens from an identity provider through Client Credentials and send them in `Authorization: Bearer`. This service validates tokens; it does not issue them.

## Identity and authorization

Configure `JWT_ISSUER_URI` and `JWT_AUDIENCE` for the deployment. Validation checks the trusted signature, matching issuer and audience, required expiration and temporal validity. Discovery and JWKS provide the public signing keys and support rotation at the identity provider.

| Operation | Required scope |
| --- | --- |
| Create a request | `requests:write` |
| Read or list requests | `requests:read` |
| Read Prometheus metrics | `monitoring:read` |

Ownership comes from the validated JWT `sub`, which must be nonblank and no longer than 255 characters. Callers cannot choose an owner in the request body. Lookup and listing enforce that identity; another consumer's request returns `404`, and an external-ID ownership conflict reveals no record details.

Local mode uses `local-demo`. Migration V2 assigns earlier rows to that identity. A JWT deployment with historical records needs an audited ownership migration because the original consumer cannot be inferred.

## Local demonstration and browser boundaries

Compose explicitly selects `SECURITY_MODE=local`, enables API documentation and binds published ports to loopback. This mode is for a local demonstration. Shared environments should use JWT, TLS and private management endpoints.

Cookies and Basic authentication never authenticate API calls. CSRF protection is disabled for system-to-system requests that explicitly attach Bearer tokens. Introducing browser authentication or automatically attached credentials requires reviewing and enabling appropriate CSRF protection. Statelessness alone does not prevent CSRF.

The automatic Swagger panel is local-only and does not persist credentials. Documentation is disabled by default outside the Compose demonstration. The static documentation site does not host the activation backend.

## Quotas and body limits

The default quota is 300 API calls per consumer within a one-minute window. PostgreSQL updates the quota atomically, sharing it across instances. The window starts with the consumer's first request; it is neither a calendar-minute window nor a sliding window.

Authorized API calls, including validation errors and missing records, consume quota. Health and metrics endpoints do not. Exhaustion returns `429` with `Retry-After`. A burst across a window boundary can reach twice the configured allowance. Expired counters are removed periodically in bounded batches.

Bodies are limited to 16 KiB by default. A known oversized `Content-Length` is rejected before authentication and quota accounting. Unknown-length bodies are bounded while being read, after the quota check. Oversized bodies return `413` before deserialization. Production gateways should apply appropriate additional limits.

## Secrets and provider transport

Keep provider credentials in the deployment platform's secret manager and inject `PROVIDER_TOKEN` at runtime. Do not commit credentials or include them in logs, metrics or traces. Rotate with a controlled overlap: provision the replacement, deploy the updated configuration, verify it and revoke the old credential. Client Credentials secrets belong to the calling systems, not to this Resource Server.

When a provider token is present, `PROVIDER_BASE_URL` must use HTTPS. HTTP is accepted only for the exact loopback hosts `localhost`, `127.0.0.1` and `[::1]`, supporting local tests. Invalid authenticated transport configuration prevents startup.

`PROVIDER_ACTIVATION_PATH` defaults to `/provider/v1/activate` and must remain within the configured provider origin. Provider response bodies and internal exception details are not exposed through public API errors.

## Configuration groups

| Group | Principal environment variables |
| --- | --- |
| Database | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `DATABASE_POOL_SIZE` |
| Provider | `PROVIDER_BASE_URL`, `PROVIDER_ACTIVATION_PATH`, `PROVIDER_CONNECT_TIMEOUT`, `PROVIDER_READ_TIMEOUT`, `PROVIDER_TOKEN`, `PROVIDER_IDEMPOTENCY_SUPPORTED` |
| Retry and lease | `RETRY_MAX_ATTEMPTS`, `RETRY_INITIAL_DELAY`, `RETRY_MAX_DELAY`, `RETRY_JITTER_FACTOR`, `WORKER_POLL_INTERVAL`, `WORKER_LEASE_DURATION` |
| Security | `SECURITY_MODE`, `JWT_ISSUER_URI`, `JWT_AUDIENCE` |
| Resource limits | `REQUEST_MAX_BODY_BYTES`, `RATE_LIMIT_MAX_REQUESTS`, `RATE_LIMIT_WINDOW` |
| Documentation | `API_DOCS_ENABLED`, `DOCS_POLL_INTERVAL_MS`, `DOCS_TIMEOUT_MS`, `DOCS_REQUEST_TIMEOUT_MS` |
| Telemetry | `METRICS_REFRESH_INTERVAL`, `OTLP_TRACING_ENABLED`, `OTLP_TRACING_ENDPOINT`, `TRACING_SAMPLE_PROBABILITY`, `LOG_FORMAT` |

The complete defaults are in [application.yml](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/resources/application.yml). Compose overrides some values for the demonstration. The [security configuration](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/security/SecurityConfiguration.java) defines the HTTP access rules.

[API](?lang=en&menu=en&id=104) · [Quick start](?lang=en&menu=en&id=103) · [Operations](?lang=en&menu=en&id=108)
