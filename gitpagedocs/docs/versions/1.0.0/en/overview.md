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

## Documentation navigation with GitPageDocs 0.0.26

The options in this section require the GitPageDocs **0.0.26** runtime. This project's documentation version remains **1.0.0**. Updating text and configuration does not itself update the runtime deployed by the workflow.

### Tag-based contents

In `gitpagedocs/docs/versions/1.0.0/config.json`, each `routes-md` item uses `RouteguideBrand: true` and `RouteGuideTags: ["h1", "h2", "h3"]`. The contents list includes the first three heading levels; other headings remain in the document. Property names are case-sensitive.

```json
{ "RouteguideBrand": true, "RouteGuideTags": ["h1", "h2", "h3"] }
```

Omitting `RouteGuideTags` preserves the legacy behavior, from `h1` through `h6`. An empty list (`[]`) removes automatic tag entries; explicit markers configured in `markdownMarkers.items` can still populate the contents list.

To include other elements, add tag names allowed by the sanitizer, such as `"p"`, `"blockquote"`, `"pre"`, `"table"`, `"img"`, `"details"` or `"summary"`. There is no wildcard: `"*"`, dangerous tags and invalid entries are ignored with a diagnostic. Including `p`, `li`, `div` or `span` may produce many entries; select only tags useful for reading.

Labels use a title, alternative text or element text. Empty elements such as `br`, `hr`, `img` and `input` can use the tag name. Automatic IDs are stable, unique and scoped to each block. Use `markdownMarkers.items` to select an occurrence, supply a custom label or apply a specific highlight.

### Scrolling, small screens and foldables

With contents scrolling unlocked, the active entry is centered, including navigation through Previous/Next. The lock preserves the position selected manually in the contents list. This lets readers inspect another part of the list without automatic tracking repositioning it.

When validating on a phone, check the collapsed menu, touch targets, keyboard focus, tables and horizontally scrollable code blocks. On foldable devices, repeat navigation folded and unfolded, in both orientations, and confirm the active entry remains accessible after the width changes. Use the pause control when animated effects are present and respect reduced-motion preferences.

## Documentation maintenance and local MCP

The functional content above describes this project. Viewer configuration targets GitPageDocs **0.0.26**; it does not change the application version or the `1.0.0` documentation directory.

With `@gitpagedocs/cli` 0.0.26 installed, run from this repository's root:

```sh
gitpagedocs mcp start
```

The MCP client starts a stdio process; there is no HTTP port or `--cwd` option. Set the process working directory to the project root. `list_files`, `read_file` and the `project://readme` and `project://structure` resources inspect content without an external provider. `write_file` writes the complete target file after review.

`update_documentation` calls a provider and only replaces the region between `gitpagedocs:start` and `gitpagedocs:end`; without those markers, it appends a region. Do not use that flow to automatically replace these complete pages. `validate_docs` is a provider review, not a deterministic check of every link. The `gitpagedocs docs` command updates README/CONTRIBUTING/SECURITY regions with standard GitPageDocs content; it does not update these project-specific pages.

Before publishing, validate JSON, paths for every language, the contents list and links in the viewer. Publication uses the repository workflow; its runtime pin must be updated separately to an available release. Preserve existing menus, themes, icons, public paths and the documentation version.

The `.github/workflows/gitpagedocs-pages.yml` workflow publishes documentation under `/docs` from `main`, using a runtime pinned to a commit and a root redirect. Preserve that path and action pins when updating the runtime; changes on another branch do not publish automatically.

GitPageDocs 0.0.26 uses `performance.mode: "auto"` for theme effects: it moderately reduces density on desktop and uses a smaller budget below 768px while retaining the theme's layers. Hidden particles pause and resume the same phase when the width changes. To keep full density, merge `site.backgroundEffects.overrides.performance: { "mode": "full" }` into `gitpagedocs/config.json`; the same option is available in `site.backgroundEffects.themes[themeId].performance`. An explicit `layers` override keeps the complete count when no mode is set in the overrides. Select `"auto"` explicitly to apply the budget to custom layers too.

## Fixed theme family and light/dark mode

`HideThemeSelector: true` hides the full theme picker and keeps the family selected by `ThemeDefault`. It still allows light/dark switching when that family has a valid pair. A different theme from the URL or browser storage cannot replace the fixed family.

`ThemeModeDefault` is the initial fallback. A valid mode from `modetheme=light` or `modetheme=dark` in the URL, a saved mode, or the reader’s toggle can select light/dark within that family. The selected mode survives reloads and document navigation. On narrow screens, open Menu to reach the standard control. Without a valid pair, the control is omitted.

Both entries in the layout catalog must enable `supportsLightAndDarkModes`, share the same nonempty `supportsLightAndDarkModesReference`, and declare opposite `mode` values (`light` and `dark`). This project uses the official paired catalog; keep its family and preferences when updating runtime or layout pins.

## Desktop side-guide height

On desktop screens wider than 1024 CSS pixels, the side guide ends after its last entry instead of stretching to the article height. Since 0.0.25, it can grow to the available viewport height below the header before its entries need internal scrolling. This side layout takes precedence over `TocScrollMaxHeightDesktop`; centered guides above the content and layouts at 1024 pixels and below retain their previous sizing rules. A small bar in the active entry's theme color follows the section marked while scrolling. The indicator uses the existing section tracking without continuous animation. In GitPageDocs 0.0.26, side guides have an extra 0.5rem of space below the header (8px at the default root font size) and retain 1rem below the card at the viewport limit. This adjustment applies only above 1024 CSS pixels; smaller layouts are unchanged.
