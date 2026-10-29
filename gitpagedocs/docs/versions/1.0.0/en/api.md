# API and provider scenarios

The API uses JSON under `/api/v1/requests`. Dates use UTC in ISO-8601 format. JWT mode requires the scopes described in [Security](?lang=en&menu=en&id=106); the bundled local environment disables authentication explicitly.

## Create a request

`POST /api/v1/requests` accepts:

```json
{
  "externalId": "EXT-12345",
  "customerId": "CUST-10001",
  "productCode": "INTERNET-500"
}
```

| Field | Validation |
| --- | --- |
| `externalId` | Required; 1–100 characters; globally unique and case sensitive |
| `customerId` | Required; 1–100 characters |
| `productCode` | Required; 1–80 characters |

Each identifier starts with an ASCII letter or digit. Remaining characters may be ASCII letters, digits, `.`, `_`, `:` or `-`. Whitespace is rejected rather than trimmed. Unknown JSON properties, malformed JSON and trailing JSON tokens are rejected.

A new request returns `201 Created`, its representation and a `Location` header. Registration commits `PENDING`; processing continues asynchronously. Read the location to follow the current status.

Identical content with the same `externalId` from the same consumer returns `200 OK` and the existing record, including a record already in `FAILED`. Different content returns `409 Conflict`. An identifier owned by another consumer also returns a generic conflict. None of these cases overwrite the original request or create another job.

## Read one request

`GET /api/v1/requests/{id}` returns `200` with:

| Field | Meaning |
| --- | --- |
| `id` | Server-generated UUID |
| `externalId`, `customerId`, `productCode` | Accepted request identifiers |
| `status` | `PENDING`, `PROCESSING`, `COMPLETED` or `FAILED` |
| `createdAt`, `updatedAt` | UTC timestamps |
| `attemptCount` | Persisted processing attempt count |
| `failureCode` | Sanitized reason when relevant |

A missing request or one owned by another consumer returns `404`. An invalid UUID returns `400`. A successfully retrieved request in `FAILED` still returns HTTP `200`: the lookup succeeded, while the stored activation failed or requires reconciliation.

## List and filter

```text
GET /api/v1/requests?status=COMPLETED&customerId=CUST-10001&page=0&size=20
```

Filters are optional. Results are restricted to the current consumer and ordered by `createdAt ASC, id ASC`. The response contains `items`, `page`, `size`, `totalElements`, `totalPages` and `hasNext`.

Pages start at zero. Defaults are size 20, maximum size 100 and maximum page 10000. The configured bounds are enforced; unsupported status values and invalid filters return `400`. Deep-history access may eventually require cursor pagination and retention management.

## Listing indexes

[Migration V2](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/resources/db/migration/V2__isolate_consumers_and_index_request_queries.sql) creates these indexes:

| Index | Columns | Intended query |
| --- | --- | --- |
| `ix_activation_consumer_created` | `(consumer_id, created_at, id)` | Consumer's requests without an optional filter |
| `ix_activation_consumer_status_created` | `(consumer_id, status, created_at, id)` | Consumer's requests filtered by status |
| `ix_activation_consumer_customer_created` | `(consumer_id, customer_id, created_at, id)` | Consumer's requests filtered by customer |

The equality predicates precede the stable ordering columns. These indexes support the principal access patterns without indexing every combination. With both optional filters present, PostgreSQL may choose one access path and evaluate the remaining predicate. Confirm actual plans with `EXPLAIN (ANALYZE, BUFFERS)` in a controlled environment before adding another composite index; extra indexes consume storage and increase write cost. The independent `UNIQUE(external_id)` constraint protects deduplication, not pagination.

## Errors and limits

Errors use `application/problem+json` with a consistent ProblemDetail structure. Internal exceptions, stack traces, provider payloads and credentials are not returned.

| Status | Situation |
| --- | --- |
| `400` | Invalid body, field, query parameter or UUID |
| `401` | Missing or invalid bearer token in JWT mode |
| `403` | Token lacks the required scope |
| `404` | Request unavailable to the current consumer |
| `409` | External identifier conflicts with content or ownership |
| `413` | Request body exceeds the configured byte limit |
| `415` | Unsupported request media type |
| `429` | Consumer quota exhausted; response includes `Retry-After` |
| `500` | Unexpected internal failure with a generic response |

The default body limit is 16 KiB. The default quota is 300 API calls per consumer per one-minute window. The quota is shared through PostgreSQL across application instances.

`X-Correlation-Id` accepts 1–64 letters, digits, `.`, `_` or `-`. Missing or invalid values are replaced with a generated identifier. Correlation flows through logs and the provider call.

## Bundled provider fixtures

The local provider requires an `Idempotency-Key`, which the application sends using the stable request UUID.

| `productCode` | Provider response | Final local result under Compose defaults |
| --- | --- | --- |
| `INTERNET-500` or an ordinary code | `200` | `COMPLETED` |
| `BUSINESS-ERROR` | `400` | `FAILED`, `PROVIDER_HTTP_400`, without retry |
| `TEMPORARY-ERROR` | `500` | Limited retries, then `FAILED`, `PROVIDER_HTTP_500` |
| `TIMEOUT` | `200` delayed for five seconds | `FAILED`, `PROVIDER_IO_UNCERTAIN`; remote result is unknown |
| `RATE-LIMITED` | `429`, `Retry-After: 2` | Limited retries, then `FAILED`, `PROVIDER_HTTP_429` |
| `UNAVAILABLE` | `503`, `Retry-After: 2` | Limited retries, then `FAILED`, `PROVIDER_HTTP_503` |

These provider statuses are processing outcomes, not the status of the initial API registration. Registration can return `201` before the provider rejects the activation. Provider throttling is also separate from the API's consumer quota.

Swagger includes automatic scenarios for these fixtures, replay, conflicting content, invalid input, unknown properties, lookup, missing/invalid IDs, filtered listing, body size and quota exhaustion. The runner supplies fresh identifiers and checks outcomes without manual copying. See [Quick start](?lang=en&menu=en&id=103) and the [OpenAPI configuration](https://github.com/Vidigal-code/product-activation-platform/tree/main/src/main/java/com/vidigal/activation/documentation).

For retry, uncertainty and circuit behavior, read [Architecture](?lang=en&menu=en&id=105).
