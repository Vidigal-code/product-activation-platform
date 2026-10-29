# Architecture decisions / Decisões arquiteturais

## 🇺🇸 English Description

<details>
<summary><strong>View Details</strong></summary>

These records explain decisions implemented by Product Activation Platform, their tradeoffs and operational limits. Each decision is accepted and documents the current service; proposed evolution is explicitly distinguished from implemented behavior. The [requirements matrix](../requirements.md) maps required functionality and optional improvements.

| ID | Decision | Scope |
| --- | --- | --- |
| [001](001-durable-activation.md) | Durable activation in PostgreSQL | Transactions, leases, recovery, ownership tokens and alternatives |
| [002](002-idempotency-and-consumer-ownership.md) | Local idempotency and consumer ownership | Uniqueness, concurrent replay, isolation, pagination and migration |
| [003](003-provider-failure-policy.md) | Explicit provider failure policy | Timeouts, bounded retries, Retry-After, circuit breaking and uncertainty |
| [004](004-resource-server-and-api-guardrails.md) | Resource Server and API guardrails | JWT, scopes, CSRF model, shared quotas, request size and credentials |
| [005](005-feature-modules-and-public-contract-tests.md) | Feature modules and public contract tests | Package boundaries, DTOs, domain rules and test layers |
| [006](006-container-operation-and-observability.md) | Container operation and observability | Reproducible execution, CI, health, metrics, logs and tracing |

The service does not guarantee exactly-once provider execution. A real provider contract, audited reconciliation interface, completion outbox, backend cloud deployment and production retention/dashboard policies remain external prerequisites or documented evolution, as detailed in the records. No dedicated authentication server or broker is included.

Execution commands and operating instructions are in the [project README](../../README.md).

</details>

## 🇧🇷 Descrição em Português

<details>
<summary><strong>Ver Detalhes</strong></summary>

Estes registros explicam as decisões implementadas no Product Activation Platform, seus tradeoffs e limites operacionais. Cada decisão está aceita e descreve o serviço atual; propostas de evolução são distinguidas explicitamente do comportamento implementado. A [matriz de requisitos](../requirements.md) relaciona obrigações e diferenciais.

| ID | Decisão | Escopo |
| --- | --- | --- |
| [001](001-durable-activation.md) | Ativação persistente no PostgreSQL | Transações, reservas, recuperação, tokens de posse e alternativas |
| [002](002-idempotency-and-consumer-ownership.md) | Idempotência local e posse por consumidor | Unicidade, repetição concorrente, isolamento, paginação e migração |
| [003](003-provider-failure-policy.md) | Política explícita de falhas do provedor | Timeouts, tentativas limitadas, Retry-After, circuito e incerteza |
| [004](004-resource-server-and-api-guardrails.md) | Resource Server e limites da API | JWT, escopos, modelo CSRF, cotas compartilhadas, tamanho e credenciais |
| [005](005-feature-modules-and-public-contract-tests.md) | Módulos por funcionalidade e testes de contratos públicos | Fronteiras de pacotes, DTOs, regras de domínio e camadas de teste |
| [006](006-container-operation-and-observability.md) | Operação em contêineres e observabilidade | Execução reproduzível, CI, saúde, métricas, logs e tracing |

O serviço não garante execução exatamente uma vez no provedor. Contrato real de integração, interface auditada de reconciliação, outbox de conclusão, deploy cloud do backend e políticas de retenção/dashboards de produção permanecem pré-requisitos externos ou evolução documentada, conforme cada registro. Não há servidor de autenticação próprio nem broker incluídos.

Comandos e instruções operacionais estão no [README do projeto](../../README.md).

</details>
