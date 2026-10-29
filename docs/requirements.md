# Requisitos e critérios de entrega

## 🇧🇷 Descrição em Português

<details>
<summary><strong>Ver Detalhes</strong></summary>

### Matriz de requisitos

O escopo obrigatório deriva do desafio de ativação de produtos. A classificação abaixo preserva a distinção do enunciado; a entrega também inclui os diferenciais técnicos selecionados. Os tópicos de microsserviços/cloud solicitam explicação e não exigem infraestrutura externa.

| Item | Classificação | Entrega e evidência |
| --- | --- | --- |
| Java 17/21, Spring Boot, Maven e banco relacional | Obrigatório | Java 21, Spring Boot 3.5, Maven Wrapper, PostgreSQL 17; `pom.xml` |
| Criar e validar solicitação | Obrigatório | `POST /api/v1/requests`; DTO separado e Bean Validation |
| Evitar duplicidade simultânea | Obrigatório | Restrição `UNIQUE(external_id)` e tratamento após rollback |
| Campos exigidos e consulta por ID | Obrigatório | Persistência, UTC e `GET /api/v1/requests/{id}` |
| Estados PENDING/PROCESSING/COMPLETED/FAILED | Obrigatório | Transições explícitas e estados terminais protegidos |
| Provedor com sucesso, 400, 500 e timeout | Obrigatório | Cliente HTTP configurável e fixtures WireMock |
| Erros de validação, ausência, conflito, integração e inesperados | Obrigatório | ProblemDetail e falha persistida sem detalhes internos |
| Segurança JWT/OAuth2 descrita ou implementada | Obrigatório | Resource Server, escopos, configuração e fluxo Client Credentials no README |
| Testes de criação, duplicidade, consulta e integração | Obrigatório | Testes unitários/componentes e integração com banco real |
| Organização e manutenção | Obrigatório | Organização por funcionalidade, responsabilidades separadas, sem camadas genéricas |
| Quatro respostas sobre lentidão | Obrigatório | README nos dois idiomas |
| Dockerfile e instruções de produção | Obrigatório | Build em dois estágios, usuário sem privilégios, configuração externa |
| README com execução, decisões, pendências e evolução | Obrigatório | README nos dois idiomas |
| 201 + Location, repetição 200 e conflito 409 | Diferencial implementado | Semântica documentada; concorrência resulta em uma linha |
| Migrações e ambiente único | Diferencial implementado | Flyway e Docker Compose com aplicação, banco e provedor; runner efêmero no profile `test` |
| Trabalho assíncrono persistente | Diferencial implementado | Reserva, vencimento, posse e atualização fora da chamada remota |
| Timeouts e política de repetição | Diferencial implementado | Configuração de conexão/leitura, tentativas limitadas e backoff |
| Segurança por escopos | Diferencial implementado | Resource Server e testes de autorização |
| Isolamento por consumidor | Diferencial implementado | `sub` validado em criação, consulta, listagem e repetição; GET alheio 404 e conflito genérico |
| Health e correlação | Diferencial implementado | Actuator, correlação persistida e cabeçalho externo |
| CI e relatórios de testes | Diferencial implementado | GitHub Actions, Maven verify, Testcontainers e JaCoCo |
| Cenário de causa comum e validação da correção | Diferencial documentado | README, itens 5 e 6 da análise de produção |
| Paginação e filtros | Diferencial implementado | `GET /api/v1/requests`, status/customerId, limites configuráveis e ordenação estável; índices por consumidor |
| Rate limiting e tamanho do corpo | Diferencial implementado | Cota por consumidor compartilhada no PostgreSQL, limpeza periódica, `429`/`Retry-After` e limite de bytes com `413` |
| Circuit breaker e jitter | Diferencial implementado | Resilience4j, adiamento persistido sem consumir tentativa e backoff aleatório limitado; `Retry-After` preservado |
| Tracing distribuído e logs JSON | Diferencial implementado | JSON ECS, Micrometer/OpenTelemetry, contexto W3C persistido entre API/worker/provedor; Jaeger opcional em override |
| Métricas por estado, latência e resultado | Diferencial implementado | Gauges persistidos por estado, timers de API/provedor, contador de resultados e métricas do circuito |
| Máquina de estados testada | Diferencial implementado | Domínio centraliza transições; fila utiliza essas regras e protege posse atomicamente; testes da matriz completa |
| Recursos modernos da linguagem | Diferencial implementado | Java 21, records para contratos/DTOs e APIs compatíveis gerenciadas pelo Spring Boot 3.5 |
| Eventos de conclusão e cloud | Diferencial documentado | Outbox, secrets, múltiplas instâncias e promoção de imagem |
| Testes de dependências arquiteturais | Diferencial implementado | ArchUnit: domínio independente, API sem persistência direta e integração sem API/persistência |
| Organização dos testes | Diferencial implementado | Pastas `unit`, `integration` e `e2e`, seleção por Maven e explicação no README |
| Contratos Swagger e Postman | Complemento implementado | OpenAPI com exemplos, painel que executa cenários ao selecionar e coleção autocontida; testes Node/Playwright e Newman no Docker e na CI |

### Decisões arquiteturais

Os [registros de decisão](adr/README.md) explicam contexto, alternativas, consequências e limitações da implementação em inglês e português. A matriz acima define o escopo; os ADRs registram as escolhas técnicas.

### Critérios de verificação

1. Preparar `.env` com o script e executar `docker compose up --build -d --wait`.
2. Selecionar um exemplo automático no Swagger para criar uma solicitação e consultar até estado terminal.
3. Selecionar repetição e conflito para verificar o mesmo ID e o conflito por conteúdo diferente.
4. Executar `mvnw verify` com Docker disponível para validar a suite Java completa, incluindo concorrência PostgreSQL.
5. Executar `npm ci`, `npm run test:unit`, `npx playwright install chromium` e, com Compose iniciado, `npm run test:e2e` para validar cenários e interação no navegador.
6. Executar `docker compose --profile test run --rm newman` para validar a coleção HTTP completa.
7. Conferir os relatórios de testes e o estado dos health checks.

O comando de build da imagem não roda Testcontainers dentro do estágio Docker. A CI executa a verificação Java e os testes unitários JavaScript no host antes de construir a imagem; testes de navegador e Newman exercitam o ambiente Compose em seguida.

</details>

## 🇺🇸 English Description

<details>
<summary><strong>View Details</strong></summary>

### Requirements matrix

The mandatory scope comes from the product activation challenge. This classification preserves the distinction in the specification; the delivery also includes the selected technical improvements. The microservices/cloud topics require explanation, without requiring external infrastructure.

| Item | Classification | Delivery and evidence |
| --- | --- | --- |
| Java 17/21, Spring Boot, Maven and relational database | Required | Java 21, Spring Boot 3.5, Maven Wrapper, PostgreSQL 17; `pom.xml` |
| Create and validate a request | Required | `POST /api/v1/requests`; separate DTO and Bean Validation |
| Prevent concurrent duplicates | Required | `UNIQUE(external_id)` constraint and handling after rollback |
| Required fields and lookup by ID | Required | Persistence, UTC and `GET /api/v1/requests/{id}` |
| PENDING/PROCESSING/COMPLETED/FAILED states | Required | Explicit transitions and protected terminal states |
| Provider success, 400, 500 and timeout | Required | Configurable HTTP client and WireMock fixtures |
| Validation, missing record, conflict, integration and unexpected errors | Required | ProblemDetail and persisted failure without internal details |
| JWT/OAuth2 security design or implementation | Required | Resource Server, scopes, configuration and Client Credentials flow in README |
| Creation, duplicate, lookup and integration tests | Required | Unit/component tests and integration against a real database |
| Organization and maintenance | Required | Feature organization, separate responsibilities, no generic layers |
| Four answers about latency | Required | README in both languages |
| Dockerfile and production instructions | Required | Two-stage build, unprivileged user, external configuration |
| README covering execution, decisions, limitations and evolution | Required | README in both languages |
| 201 + Location, replay 200 and conflict 409 | Implemented improvement | Documented semantics; concurrency produces one row |
| Migrations and unified environment | Implemented improvement | Flyway and Docker Compose with application, database and provider; temporary runner in the `test` profile |
| Durable asynchronous work | Implemented improvement | Lease, expiry, ownership and update outside the remote call |
| Timeouts and retry policy | Implemented improvement | Connection/read configuration, bounded attempts and backoff |
| Scope authorization | Implemented improvement | Resource Server and authorization tests |
| Consumer isolation | Implemented improvement | Validated `sub` in creation, lookup, listing and replay; another owner's GET returns 404 and conflicts remain generic |
| Health and correlation | Implemented improvement | Actuator, persisted correlation and external header |
| CI and test reports | Implemented improvement | GitHub Actions, Maven verify, Testcontainers and JaCoCo |
| Common cause scenario and verification of the fix | Documented improvement | README production analysis, items 5 and 6 |
| Pagination and filters | Implemented improvement | `GET /api/v1/requests`, status/customerId, configurable limits and stable order; consumer indexes |
| Rate limiting and request size | Implemented improvement | Consumer quota shared in PostgreSQL, periodic cleanup, `429`/`Retry-After` and byte limit with `413` |
| Circuit breaker and jitter | Implemented improvement | Resilience4j, persisted deferral without consuming an attempt and bounded randomized backoff; preserved `Retry-After` |
| Distributed tracing and JSON logs | Implemented improvement | ECS JSON, Micrometer/OpenTelemetry, W3C context persisted across API/worker/provider; optional Jaeger override |
| State, latency and outcome metrics | Implemented improvement | Persisted state gauges, API/provider timers, outcome counter and circuit metrics |
| Tested state machine | Implemented improvement | Domain centralizes transitions; queue uses these rules and protects ownership atomically; full transition matrix tests |
| Modern language features | Implemented improvement | Java 21, records for contracts/DTOs and compatible APIs managed by Spring Boot 3.5 |
| Completion events and cloud | Documented improvement | Outbox, secrets, multiple instances and image promotion |
| Architectural dependency tests | Implemented improvement | ArchUnit: independent domain, API without direct persistence and integration without API/persistence |
| Test organization | Implemented improvement | `unit`, `integration` and `e2e` directories, Maven selection and README explanation |
| Swagger and Postman contracts | Implemented addition | OpenAPI examples, a panel that runs scenarios on selection and a self-contained collection; Node/Playwright tests and Newman in Docker and CI |

### Architecture decisions

The [decision records](adr/README.md) explain the implementation context, alternatives, consequences and limitations in English and Portuguese. The matrix above defines scope; ADRs record technical choices.

### Verification criteria

1. Prepare `.env` with the setup script and run `docker compose up --build -d --wait`.
2. Select an automatic Swagger example to create a request and follow it to a terminal state.
3. Select replay and conflict to verify the same ID and the conflict for different content.
4. Run `mvnw verify` with Docker available to validate the complete Java suite, including PostgreSQL concurrency.
5. Run `npm ci`, `npm run test:unit`, `npx playwright install chromium` and, with Compose running, `npm run test:e2e` to validate scenarios and browser interaction.
6. Run `docker compose --profile test run --rm newman` to validate the complete HTTP collection.
7. Check test reports and health status.

The image build does not run Testcontainers inside its Docker stage. CI runs Java verification and JavaScript unit tests on the host before building the image; browser tests and Newman then exercise the Compose environment.

</details>
