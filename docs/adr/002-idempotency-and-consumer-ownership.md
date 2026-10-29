# ADR 002 — Local idempotency and consumer ownership

## 🇺🇸 English Description

<details>
<summary><strong>View Details</strong></summary>

### Status and context

Accepted. Concurrent requests must not create two valid records with the same `externalId`. Consumers retry calls and must not read or reuse another consumer's data. Duplicate prevention is required; explicit replay semantics and consumer isolation are implemented improvements.

### Decision

Make `external_id` globally unique with a PostgreSQL constraint. An optional early lookup is an optimization, never the concurrency guarantee. Insert in a separate transaction and flush to detect the constraint violation. If the named uniqueness constraint is violated, let rollback finish before querying the winning row. Other integrity errors propagate instead of being disguised as duplicates.

| Situation | Response |
| --- | --- |
| New request | `201 Created` with `Location` |
| Same external ID, customer and product, from the same consumer | `200 OK` with the existing record |
| Same external ID with different content | `409 Conflict`, no overwrite |
| External ID owned by another consumer | Generic `409`, no record details |
| Lookup of an absent or differently owned record | `404 Not Found` |

Consumer identity comes from the validated JWT `sub`, not the request body. Local mode uses `local-demo`. Lookup and listing apply ownership in database queries. The API uses separate DTOs, rejects unknown JSON fields and validates identifiers without silently trimming or changing their case.

The request stores UTC timestamps and its current state. Failed requests retain their external ID; replay returns that failed record and does not schedule a new activation. Listing uses optional status/customer filters, bounded page sizes and offsets, stable `createdAt, id` order, and consumer/filter indexes.

### Consequences and limits

The database resolves races across instances and prevents divergent duplicates. Global uniqueness also means two consumers cannot use the same external ID, even for different records. Callers should allocate identifiers accordingly; changing to per-consumer uniqueness would change the public contract and require a migration.

Migration V2 assigns historical rows to `local-demo` because their original consumer cannot be inferred. A JWT deployment with existing data requires an audited ownership migration. Retention policies must also consider the duplicate-prevention horizon: deleting a record removes its uniqueness protection.

Local replay does not guarantee provider idempotency; that contract is addressed in [ADR 003](003-provider-failure-policy.md). Offset limits prevent unrestricted requests but do not make deep pages inexpensive. Cursors and data retention remain possible evolutions.

### Alternatives and verification

A check-then-insert sequence alone races. Process-local locks do not coordinate replicas. Returning `409` for every repeat would force consumers to treat legitimate network retries as errors. Comparing the owner and original business fields supports safe replay without a separate idempotency store.

Tests use real PostgreSQL to exercise parallel creation, equality/conflict responses, rollback recovery and cross-consumer access. Database constraints remain authoritative even if an application path misses an early lookup.

### Evidence

- [Registration and replay](../../src/main/java/com/vidigal/activation/request/application/RegistrationService.java).
- [Request API](../../src/main/java/com/vidigal/activation/request/api/ActivationRequestController.java).
- [Uniqueness schema](../../src/main/resources/db/migration/V1__create_activation_requests.sql).
- [Ownership migration and indexes](../../src/main/resources/db/migration/V2__isolate_consumers_and_index_request_queries.sql).

</details>

## 🇧🇷 Descrição em Português

<details>
<summary><strong>Ver Detalhes</strong></summary>

### Estado e contexto

Aceita. Requisições concorrentes não podem criar dois registros válidos com o mesmo `externalId`. Consumidores repetem chamadas e não devem consultar ou reutilizar dados de outro consumidor. Evitar duplicidade é obrigatório; semântica explícita de repetição e isolamento são diferenciais implementados.

### Decisão

Garantir unicidade global de `external_id` com restrição PostgreSQL. A consulta antecipada é uma otimização, nunca a garantia de concorrência. Inserir em transação separada e executar flush para detectar a violação. Se houver violação da restrição nomeada de unicidade, aguardar o rollback terminar antes de consultar a linha vencedora. Outros erros de integridade são propagados, sem serem mascarados como duplicidade.

| Situação | Resposta |
| --- | --- |
| Nova solicitação | `201 Created` com `Location` |
| Mesmo identificador externo, cliente e produto, do mesmo consumidor | `200 OK` com o registro existente |
| Mesmo identificador externo com conteúdo diferente | `409 Conflict`, sem sobrescrita |
| Identificador externo de outro consumidor | `409` genérico, sem detalhes do registro |
| Consulta de registro ausente ou de outro consumidor | `404 Not Found` |

A identidade vem do `sub` do JWT validado, não do corpo. O modo local utiliza `local-demo`. Consulta e listagem aplicam a posse nas consultas ao banco. A API utiliza DTOs separados, rejeita campos JSON desconhecidos e valida identificadores sem remover espaços ou alterar maiúsculas/minúsculas silenciosamente.

A solicitação guarda datas UTC e o estado atual. Falhas mantêm o identificador externo; a repetição retorna o registro com falha e não agenda nova ativação. A listagem utiliza filtros opcionais por estado/cliente, tamanhos e offsets limitados, ordem estável `createdAt, id` e índices por consumidor/filtros.

### Consequências e limites

O banco resolve disputas entre instâncias e impede duplicidades divergentes. A unicidade global também significa que dois consumidores não podem utilizar o mesmo identificador externo, mesmo para registros diferentes. Os sistemas devem alocar identificadores conforme essa regra; mudar para unicidade por consumidor alteraria o contrato público e exigiria migração.

A migração V2 atribui registros históricos a `local-demo`, pois o consumidor original não pode ser inferido. Um ambiente JWT com dados existentes exige migração auditada de posse. Políticas de retenção também precisam considerar o prazo de prevenção de duplicidade: excluir um registro remove sua proteção de unicidade.

A repetição local não garante idempotência no provedor, cujo contrato está na [ADR 003](003-provider-failure-policy.md). Limitar offsets evita requisições irrestritas, mas não torna páginas profundas baratas. Cursores e retenção permanecem como possíveis evoluções.

### Alternativas e verificação

Consultar antes de inserir, isoladamente, sofre concorrência. Locks em memória não coordenam réplicas. Retornar `409` para toda repetição obrigaria consumidores a tratar retries legítimos de rede como erro. Comparar proprietário e campos de negócio originais permite repetição segura sem um armazenamento adicional de idempotência.

Testes com PostgreSQL real verificam criação paralela, respostas para igualdade/conflito, recuperação após rollback e acesso entre consumidores. As restrições do banco permanecem como autoridade mesmo sem consulta antecipada em algum caminho da aplicação.

### Evidências

- [Registro e repetição](../../src/main/java/com/vidigal/activation/request/application/RegistrationService.java).
- [API de solicitações](../../src/main/java/com/vidigal/activation/request/api/ActivationRequestController.java).
- [Schema de unicidade](../../src/main/resources/db/migration/V1__create_activation_requests.sql).
- [Migração de posse e índices](../../src/main/resources/db/migration/V2__isolate_consumers_and_index_request_queries.sql).

</details>
