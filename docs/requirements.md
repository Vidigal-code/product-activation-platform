# Requisitos e decisões arquiteturais

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

### Decisão arquitetural: trabalho persistente no PostgreSQL

Status: aceita.

#### Contexto

A ativação depende de um provedor que pode rejeitar a entrada, ficar indisponível ou responder depois do timeout. O serviço precisa sobreviver à queda entre a gravação local e a chamada externa, aceitar retries dos consumidores e permitir múltiplas instâncias. Não existe requisito de grande volume que justifique operar um broker adicional.

#### Decisão

Utilizar a solicitação persistida como unidade de trabalho. A criação grava `PENDING`. Um worker reserva a próxima linha elegível em transação curta, registra prazo de expiração e token de posse, e confirma a transação antes de iniciar HTTP. Outra transação registra o resultado somente se a posse ainda for válida. A consulta e a criação usam JPA; a operação específica de reserva utiliza os recursos de concorrência do PostgreSQL.

O banco garante unicidade global de `external_id`. Depois de uma violação de unicidade, a transação de inserção deve terminar antes de consultar o vencedor. Mesmo conteúdo do mesmo consumidor retorna o registro existente. Divergência ou posse de outro consumidor retorna conflito sem expor nem sobrescrever os dados. Consulta e listagem restringem resultados ao `sub` validado; o modo local utiliza `local-demo`. Registros anteriores à migração de posse precisam de atribuição auditada antes da operação com consumidores JWT.

Estados terminais são `COMPLETED` e `FAILED`. Repetições temporárias devolvem o trabalho a `PENDING` com próxima execução e contador persistidos. Reservas vencidas não autorizam sobrescrita pelo antigo worker. Sem idempotência confirmada no provedor, um resultado incerto exige reconciliação; o estado local `FAILED` não prova ausência de efeito remoto.

#### Garantias e limites

- Uma linha por `externalId`, inclusive sob criação simultânea.
- O trabalho confirmado no banco não depende da memória do processo.
- A chamada remota não mantém transação de banco aberta.
- O token de posse evita atualização local por worker substituído.
- O sistema não oferece execução exatamente uma vez no provedor.
- Uma chave estável só evita efeitos duplicados se o provedor implementar e garantir esse contrato.
- A política de repetir erros `5xx` pressupõe que esse retorno é repetível segundo o contrato externo. Provedores que podem concluir uma operação antes de retornar `500` exigem idempotência ou reconciliação.
- `Retry-After` válido prevalece sobre o teto do backoff. Um valor numérico que excede o intervalo de agendamento suportado encerra o trabalho com `PROVIDER_RETRY_AFTER_UNSUPPORTED`, sem antecipar outra chamada.
- O prazo da reserva deve superar os timeouts HTTP e a margem operacional. Expiração é uma proteção de recuperação, não um mecanismo para interromper uma chamada HTTP em curso.

#### Consequências

O PostgreSQL atende persistência e coordenação, reduzindo custo operacional. Em contrapartida, o worker tem capacidade limitada e exige índices adequados, métricas de idade da fila, limites de tentativas e retenção dos registros. A consulta periódica gera carga pequena e previsível; volume futuro deve ser medido antes de ampliar concorrência ou introduzir broker.

Readiness inclui o banco. O provedor é uma dependência do processamento; indisponibilidade temporária dele não deve provocar ciclos de reinício da aplicação.

#### Autenticação e CSRF

CSRF permanece desativado para Bearer explícito sistema-a-sistema. Cookies/Basic não autenticam e `text/plain` não cria solicitações. Introduzir autenticação para navegador ou credenciais automáticas exige habilitar CSRF. O Swagger local não utiliza autenticação e permanece restrito à demonstração em loopback. Consulte o [modelo de segurança](../README.md#segurança-e-configuração).

#### Evolução

Para publicar eventos de conclusão, persistir um evento outbox na mesma transação do estado final e publicá-lo separadamente. Para reconciliação, consultar a referência/chave no provedor, registrar evidência e somente então concluir ou autorizar nova tentativa auditada. Para volume maior, avaliar processamento em lotes, particionamento/retenção e fila externa conforme medições.

#### Alternativas consideradas

Processamento síncrono é mais simples, mas expõe o consumidor à latência externa e continua exigindo estratégia para a janela entre commit e HTTP. Tarefas apenas em memória perdem trabalho em quedas. Um broker atenderia coordenação, mas adicionaria infraestrutura sem requisito proporcional nesta entrega.

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

### Architecture decision: durable work in PostgreSQL

Status: accepted.

#### Context

Activation depends on a provider that can reject input, become unavailable or respond after a timeout. The service must survive a crash between the local write and the external call, accept consumer retries and support multiple instances. No high-volume requirement justifies operating an additional broker.

#### Decision

Use the persisted request as the work unit. Creation stores `PENDING`. A worker claims the next eligible row in a short transaction, records lease expiry and an ownership token, and commits before starting HTTP. Another transaction records the outcome only while ownership remains valid. Lookup and creation use JPA; the specific claim operation uses PostgreSQL concurrency features.

The database guarantees global uniqueness of `external_id`. After a uniqueness violation, the insert transaction must end before querying the winner. Identical content from the same consumer returns the existing record. Different content or another owner produces a conflict without exposing or overwriting data. Lookup and listing restrict results to the validated `sub`; local mode uses `local-demo`. Records predating the ownership migration need audited assignment before operation with JWT consumers.

Terminal states are `COMPLETED` and `FAILED`. Temporary retries return work to `PENDING` with a persisted due time and attempt count. Expired leases never authorize an old worker to overwrite a result. Without confirmed provider idempotency, an uncertain outcome requires reconciliation; local `FAILED` does not prove the absence of a remote effect.

#### Guarantees and limits

- One row per `externalId`, including concurrent creation.
- Work committed to the database does not depend on process memory.
- The remote call does not hold an open database transaction.
- The ownership token prevents local updates by a replaced worker.
- The system does not guarantee exactly-once execution at the provider.
- A stable key prevents duplicate effects only if the provider implements and guarantees that contract.
- Retrying `5xx` assumes the external contract permits replay after that response. Providers that can complete an operation before returning `500` require idempotency or reconciliation.
- Valid `Retry-After` takes precedence over the backoff cap. A numeric value beyond the supported scheduling range ends work with `PROVIDER_RETRY_AFTER_UNSUPPORTED`, without sending an early retry.
- The lease duration must exceed HTTP timeouts and the operational margin. Expiry protects recovery; it does not interrupt an HTTP call already in progress.

#### Consequences

PostgreSQL provides persistence and coordination, reducing operating cost. The worker has limited capacity and needs suitable indexes, queue-age metrics, attempt limits and record retention. Periodic queries create a small, predictable load; future volume must be measured before increasing concurrency or introducing a broker.

Readiness includes the database. The provider is a processing dependency; its temporary unavailability should not cause application restart loops.

#### Authentication and CSRF

CSRF remains disabled for explicit system-to-system Bearer authentication. Cookies/Basic cannot authenticate and `text/plain` cannot create requests. Introducing browser authentication or automatically attached credentials requires enabling CSRF. Local Swagger uses no authentication and remains restricted to the loopback demonstration. See the [security model](../README.md#security-and-configuration).

#### Evolution

To publish completion events, persist an outbox event in the final-state transaction and publish it separately. Reconciliation should query the provider reference/key, record evidence and only then complete the request or authorize an audited retry. For higher volume, evaluate batching, partitioning/retention and an external queue based on measurements.

#### Alternatives considered

Synchronous processing is simpler, but exposes the consumer to external latency and still requires a strategy for the gap between commit and HTTP. Memory-only tasks lose work after crashes. A broker would provide coordination while adding infrastructure without a proportional requirement in this delivery.

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
