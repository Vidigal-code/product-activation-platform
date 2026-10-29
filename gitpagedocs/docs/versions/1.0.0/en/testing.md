# Testing and continuous integration

Tests separate domain rules and policies from external integrations and complete workflows. Public behavior is verified through the appropriate boundary: HTTP, database transactions, provider responses or browser interaction.

| Layer | Coverage | Location |
| --- | --- | --- |
| Java unit | Retry, configuration, state transitions, correlation, error formatting and architecture dependencies | `src/test/unit/java` |
| Java integration | HTTP/JWT, API contracts, real PostgreSQL, concurrency, rollback and lease ownership | `src/test/integration/java` |
| Java end-to-end | Running HTTP application with a controlled provider | `src/test/e2e/java` |
| JavaScript unit | Scenario execution, validation, bounds, timeouts and cancellation | `src/test/unit/javascript` |
| Browser | Scenario selection, automatic execution, repeat, cancel and quota recovery | `src/test/e2e/javascript` |
| HTTP collection | API/provider contracts, concurrency, health, metrics and limits | `postman` |

## Java verification

Install JDK 21 and keep Docker available. Maven Wrapper supplies the build tool.

```sh
./mvnw test
./mvnw -Dit.test='*IT' verify
./mvnw -Dit.test='*E2E' verify
./mvnw -B -ntp verify
```

These commands run unit tests; unit plus integration tests; unit plus end-to-end tests; and the complete Java verification respectively. On Windows, replace `./mvnw` with `.\mvnw.cmd`.

Testcontainers starts disposable PostgreSQL databases independently of the Compose database. Tests requiring Docker are not silently skipped when it is unavailable. WireMock provides deterministic provider responses and delays. Security integration tests verify signed JWTs, issuer/audience, lifetime, scopes and rejection of unsupported authentication.

Concurrency tests prove that simultaneous equal submissions create one row. Persistence and worker tests cover transaction recovery, lease expiry, retries, open-circuit deferral and rejection of stale ownership. ArchUnit checks the module dependency rules.

## Swagger runner and browser tests

Use Node.js 22 or later:

```sh
npm ci
npm run test:unit
npx playwright install chromium
docker compose up -d --wait
npm run test:e2e
```

The JavaScript unit tests run independently of the backend. Browser tests use isolated Chromium sessions against the running Compose application and exercise the automatic panel. Development dependencies are not installed in the production image: the application serves the scenario scripts directly, without a frontend compilation step.

## Complete HTTP collection

```sh
docker compose --profile test run --rm newman
```

The collection generates unique identifiers, follows `Location` and polls terminal states with bounded requests and elapsed time. It covers the bundled provider fixtures, identical replay, conflict, validation, malformed JSON, missing records, media types, oversized bodies, pagination, health and metrics.

It also sends 12 concurrent equal requests and expects one `201`, eleven `200` responses and one shared ID. A bounded final burst verifies `429` and `Retry-After`. The next run accounts for quota recovery. Any assertion failure makes the command fail.

Collection settings for expected retries, body size and quota must match application settings. JWT authorization is verified by Maven security tests; the unauthenticated local collection does not claim to validate a real identity provider. For a JWT test environment, supply a suitable real token without exporting or committing it.

## Reports and CI

| Output | Location |
| --- | --- |
| Unit test reports | `target/surefire-reports` |
| Integration/end-to-end reports | `target/failsafe-reports` |
| Java coverage | `target/site/jacoco` |
| Browser screenshots and reports | `target/swagger-e2e` |

The application [CI workflow](https://github.com/Vidigal-code/product-activation-platform/blob/main/.github/workflows/ci.yml) runs on `main`, `dev` and pull requests. It configures Java and Node, installs dependencies, runs JavaScript unit tests and Maven verification, validates Compose, builds the image, runs browser/Newman scenarios and uploads reports. Actions use fixed revisions and minimum repository permissions.

The application CI validates the image without deploying the backend to an external account. The Docker build itself does not run Testcontainers; those checks run on the CI host before image construction. Documentation publication is separate from application deployment.

Build and test configuration: [pom.xml](https://github.com/Vidigal-code/product-activation-platform/blob/main/pom.xml), [package.json](https://github.com/Vidigal-code/product-activation-platform/blob/main/package.json) and [test sources](https://github.com/Vidigal-code/product-activation-platform/tree/main/src/test).

[Quick start](?lang=en&menu=en&id=103) · [Requirements](?lang=en&menu=en&id=102) · [Operations](?lang=en&menu=en&id=108)
