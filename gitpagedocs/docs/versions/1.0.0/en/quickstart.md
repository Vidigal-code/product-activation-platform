# Quick start

The complete local environment requires Git, Docker Engine or Docker Desktop with Linux containers, and Docker Compose v2. Java is not required when running the Docker environment. Allow approximately 2 GB of free container memory; the initial build downloads dependencies.

## Get the project

```sh
git clone https://github.com/Vidigal-code/product-activation-platform.git
cd product-activation-platform
```

The commands below run from the repository root.

## Start on Windows

```powershell
.\scripts\setup-local.ps1
docker compose up --build -d --wait
docker compose ps
```

## Start on Linux or macOS

```sh
sh scripts/setup-local.sh
docker compose up --build -d --wait
docker compose ps
```

The setup script creates an ignored `.env` with a random database password and preserves an existing file. Adjustable ports are described in [.env.example](https://github.com/Vidigal-code/product-activation-platform/blob/main/.env.example). Change an occupied port before startup. Never commit `.env`.

| Service | Default local address |
| --- | --- |
| Swagger UI | <http://localhost:8080/swagger-ui/index.html> |
| OpenAPI document | <http://localhost:8080/v3/api-docs> |
| Readiness | <http://localhost:8080/actuator/health/readiness> |
| Liveness | <http://localhost:8080/actuator/health/liveness> |
| Provider mappings | <http://localhost:8089/__admin/mappings> |
| PostgreSQL | `localhost:5432`, database and user `activation` |

The application, database and provider run in separate containers. Published ports bind to `127.0.0.1`. Compose selects unauthenticated `local` mode for this demonstration; shared deployments require JWT mode and TLS. See [Security](?lang=en&menu=en&id=106).

## Try complete scenarios without entering fields

Open Swagger UI and select an **Exemplo automático**. Selection immediately starts the scenario: it generates fresh identifiers, prepares the request, follows the persisted record and verifies the expected result. Progress and response JSON appear in the panel.

Use **Executar novamente** for a new run. **Cancelar** stops browser requests and polling; it does not undo work already committed by the application. The native Swagger operations remain available for manual calls.

The scenarios include successful activation, provider failures, timeout, replay, conflict, validation, lookup, listing, an oversized body and rate limiting. The quota scenario deliberately exhausts the local consumer's allowance. A subsequent run waits for the indicated recovery period before continuing.

## Run the complete HTTP collection

```sh
docker compose --profile test run --rm newman
```

The temporary container creates its own test identifiers, follows responses and checks terminal outcomes. It also verifies concurrent duplicate requests and resource limits. Any failed assertion makes the command fail. The runner is not started during normal application startup.

For the Postman application, import the [collection](https://github.com/Vidigal-code/product-activation-platform/blob/main/postman/product-activation.postman_collection.json) and [local environment](https://github.com/Vidigal-code/product-activation-platform/blob/main/postman/local.postman_environment.json), select that environment and run the whole collection. Adjust `baseUrl` when changing the application port.

## Inspect and stop the environment

```sh
docker compose logs -f app
docker compose stop
docker compose up -d --wait
docker compose down
```

`docker compose down` preserves the PostgreSQL volume. The separate command `docker compose down --volumes` permanently deletes local requests and should only be used for an intentional reset.

Changing the password in `.env` does not update a database volume that has already been initialized. Rotate the actual database credential and application configuration together.

If startup fails, inspect `docker compose ps` and application/database logs. Confirm Docker is running, the selected ports are available and the generated environment file exists. For full verification, continue to [Testing](?lang=en&menu=en&id=107); for monitoring and tracing, see [Operations](?lang=en&menu=en&id=108).
