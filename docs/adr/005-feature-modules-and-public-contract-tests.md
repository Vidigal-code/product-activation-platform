# ADR 005 — Feature modules and public contract tests

## 🇺🇸 English Description

<details>
<summary><strong>View Details</strong></summary>

### Status and context

Accepted. The service needs readable boundaries and tests that detect behavioral regressions without introducing generic layers. A single activation capability does not justify separate deployable services or framework-independent abstractions for every component.

### Decision

Keep one Spring Boot application organized around the activation request feature, separating API, application orchestration, domain, persistence and provider integration. Cross-cutting security, web error handling, documentation and observability have dedicated packages. Use Spring MVC, Bean Validation, Spring Data JPA and framework configuration where they fit the problem.

Use records for request/response contracts and separate DTOs from JPA entities. Centralize the state transition matrix in the domain and have queue operations use it while enforcing ownership atomically in SQL. API code calls application services rather than repositories directly. Avoid extra repository wrappers or transport abstractions without a concrete need.

Validate through public behavior at several boundaries:

| Layer | Responsibility |
| --- | --- |
| Unit | State transitions, retry/configuration rules, correlation and error formatting |
| Architecture | ArchUnit dependency constraints between feature packages |
| Integration | HTTP/JWT contracts and real PostgreSQL transactions, uniqueness, quotas and leases |
| Java end-to-end | Running HTTP application with controlled provider responses |
| JavaScript unit | Automatic scenario execution, bounded waits, errors and cancellation |
| Browser and collection | Swagger interaction and complete HTTP scenarios through Playwright and Newman |

PostgreSQL Testcontainers tests are the authority for database-specific behavior. WireMock controls provider success, errors and delays. Mocking is used at appropriate external boundaries rather than replacing the business behavior under verification. The Maven Wrapper and pinned browser dependencies make execution reproducible.

### Consequences and alternatives

This organization keeps related behavior together and allows deployment as one service. JPA mapping remains in the persisted domain entity; the design does not claim complete framework independence. Database-specific concurrency code is isolated in persistence and intentionally tested against PostgreSQL.

An H2-only suite would not establish PostgreSQL locking and rollback behavior. Mock-only tests would not exercise transaction recovery. Testing everything through a browser would make feedback slower and diagnosis less precise. The selected layers balance focused feedback with realistic contracts, while requiring Docker for integration verification.

Separate `unit`, `integration` and `e2e` source directories make scope explicit. No delivery claim relies solely on a coverage percentage; correctness includes negative, concurrent and uncertain-result cases. Test counts belong to reports for each revision, not to this architectural contract.

### Evidence

- [Feature packages](../../src/main/java/com/vidigal/activation/request).
- [Test layout](../../src/test).
- [Maven lifecycle and dependencies](../../pom.xml).
- [Browser test commands](../../package.json).

</details>

## 🇧🇷 Descrição em Português

<details>
<summary><strong>Ver Detalhes</strong></summary>

### Estado e contexto

Aceita. O serviço precisa de fronteiras legíveis e testes capazes de detectar regressões de comportamento sem introduzir camadas genéricas. Uma única capacidade de ativação não justifica serviços implantáveis separados nem abstrações independentes do framework para cada componente.

### Decisão

Manter uma aplicação Spring Boot organizada pela funcionalidade de solicitação de ativação, separando API, orquestração de aplicação, domínio, persistência e integração com o provedor. Segurança, erros web, documentação e observabilidade possuem pacotes próprios. Utilizar Spring MVC, Bean Validation, Spring Data JPA e configuração do framework quando atendem ao problema.

Utilizar records nos contratos de entrada/saída e separar DTOs das entidades JPA. Centralizar a matriz de transições no domínio e utilizá-la nas operações da fila, preservando a verificação atômica de posse em SQL. A API chama serviços de aplicação, sem acessar repositórios diretamente. Evitar wrappers adicionais de repositório ou abstrações de transporte sem necessidade concreta.

Validar comportamento público em diferentes fronteiras:

| Camada | Responsabilidade |
| --- | --- |
| Unitária | Transições, regras de repetição/configuração, correlação e formatação de erros |
| Arquitetura | Restrições ArchUnit de dependência entre pacotes |
| Integração | Contratos HTTP/JWT e transações, unicidade, cotas e reservas no PostgreSQL real |
| End-to-end Java | Aplicação HTTP em execução com respostas controladas do provedor |
| Unitária JavaScript | Execução de cenários automáticos, esperas limitadas, erros e cancelamento |
| Navegador e coleção | Interação com Swagger e cenários HTTP completos por Playwright e Newman |

Testcontainers com PostgreSQL é a referência para comportamento específico do banco. WireMock controla sucesso, erros e atrasos do provedor. Mocks são usados em fronteiras externas adequadas, sem substituir o comportamento de negócio verificado. Maven Wrapper e dependências de navegador fixadas permitem execução reproduzível.

### Consequências e alternativas

A organização reúne comportamentos relacionados e permite implantar um único serviço. O mapeamento JPA permanece na entidade de domínio persistida; o desenho não afirma independência completa do framework. Código de concorrência específico do banco fica na persistência e é testado intencionalmente com PostgreSQL.

Uma suite somente H2 não comprovaria locks e rollback do PostgreSQL. Testes apenas com mocks não exercitariam recuperação transacional. Validar tudo pelo navegador tornaria o retorno mais lento e o diagnóstico menos preciso. As camadas escolhidas equilibram retorno focado e contratos realistas, exigindo Docker para integração.

Diretórios separados `unit`, `integration` e `e2e` deixam o escopo explícito. Nenhuma afirmação de entrega depende somente de percentual de cobertura; correção inclui casos negativos, concorrentes e de resultado incerto. Quantidades de testes pertencem aos relatórios de cada revisão, não a este contrato arquitetural.

### Evidências

- [Pacotes da funcionalidade](../../src/main/java/com/vidigal/activation/request).
- [Organização dos testes](../../src/test).
- [Ciclo Maven e dependências](../../pom.xml).
- [Comandos de testes de navegador](../../package.json).

</details>
