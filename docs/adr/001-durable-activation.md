# ADR 001 — Durable activation in PostgreSQL

## 🇺🇸 English Description

<details>
<summary><strong>View Details</strong></summary>

### Status and context

Accepted. Activation must survive process failure between local persistence and the external call. The provider can be slow or unavailable, and several application instances may process work. Durable asynchronous processing is an implemented improvement over the required creation and lookup contract.

### Decision

Use the persisted request as the work unit. Creation commits `PENDING` before returning. The worker claims eligible rows in a short PostgreSQL transaction using `FOR UPDATE SKIP LOCKED`, records a lease expiry and ownership token, and commits before making HTTP calls. A separate transaction records the outcome only while both token and unexpired lease still match.

Creation and lookup use JPA. The claim and conditional update operations use parameterized PostgreSQL SQL because their concurrency guarantees are specific to the database. Flyway versions the schema, indexes and integrity constraints.

Generate the initial `createdAt`, `updatedAt` and `nextAttemptAt` values with PostgreSQL's clock during the insert, using Hibernate `@CurrentTimestamp(event = INSERT, source = SourceType.DB)`. Claims and retries already use the database clock. This keeps initial eligibility on the same time source and avoids an artificial queue delay when the JVM clock runs ahead. The mapping requires no schema migration and does not change identifiers, states or the HTTP contract.

The normal flow is `PENDING → PROCESSING → COMPLETED/FAILED`. A temporary retry returns to `PENDING` with a persisted due time and attempt count. The domain also permits `PENDING → FAILED` for operational termination. Terminal states have no outgoing transition. An open circuit defers work and restores the unused attempt, as described in [ADR 003](003-provider-failure-policy.md).

Expired leases do not authorize an old worker to write. Recovery of uncertain work depends on confirmed provider idempotency; without it, the request is terminated for reconciliation rather than automatically resubmitted. The lease must exceed the HTTP timeouts plus the operational margin for storing the result. Expiration does not interrupt an HTTP call already running.

### Consequences and limits

- Committed work survives application restarts without depending on process memory.
- Multiple instances coordinate claims through the same database, and remote latency does not hold an open database transaction.
- PostgreSQL becomes both storage and coordinator, reducing infrastructure while increasing dependence on its availability and capacity.
- Polling creates recurring load. Indexes, bounded retries, queue-age diagnosis and retention are necessary as data grows. Concurrent processing does not guarantee completion order.
- The ownership token protects local writes; it does not cancel remote effects or provide exactly-once execution at the provider.
- Database-generated insertion timestamps apply to new records. Historical timestamps are not corrected, and scheduling remains dependent on PostgreSQL's clock; changing that clock can still affect due times and lease expiry.

### Alternatives and evolution

A synchronous flow is simpler but exposes clients to provider latency and still needs a strategy for the commit/HTTP gap. Memory-only tasks lose work on failure. A broker adds operational cost without a demonstrated requirement at the current scale.

Evaluate batching, retention, partitioning or an external queue based on measurements. Future completion events should use a transactional outbox written with the final state and published separately. No outbox or event publisher is implemented. Reconciliation should query the provider reference/key, record evidence and only then complete or authorize an audited retry; there is no public restart or reconciliation endpoint.

### Evidence

- [Database-generated insertion timestamps](../../src/main/java/com/vidigal/activation/request/domain/ActivationRequest.java).
- [Reservation and conditional writes](../../src/main/java/com/vidigal/activation/request/persistence/ActivationQueue.java).
- [Worker orchestration](../../src/main/java/com/vidigal/activation/request/application/ActivationWorker.java).
- [Domain transitions](../../src/main/java/com/vidigal/activation/request/domain/ActivationStatus.java).
- [Versioned migrations](../../src/main/resources/db/migration).

</details>

## 🇧🇷 Descrição em Português

<details>
<summary><strong>Ver Detalhes</strong></summary>

### Estado e contexto

Aceita. A ativação precisa sobreviver à interrupção entre a persistência local e a chamada externa. O provedor pode ficar lento ou indisponível, e várias instâncias podem processar trabalho. O processamento assíncrono durável é um diferencial implementado sobre o contrato obrigatório de criação e consulta.

### Decisão

Utilizar a solicitação persistida como unidade de trabalho. A criação confirma `PENDING` antes de responder. O worker reserva linhas elegíveis em transação curta no PostgreSQL com `FOR UPDATE SKIP LOCKED`, registra vencimento e token de posse e confirma antes de iniciar HTTP. Outra transação grava o resultado somente enquanto o token corresponder e a reserva não tiver vencido.

Criação e consulta usam JPA. Reserva e atualizações condicionais usam SQL PostgreSQL parametrizado porque suas garantias de concorrência são específicas do banco. O Flyway versiona schema, índices e restrições de integridade.

Gerar os valores iniciais de `createdAt`, `updatedAt` e `nextAttemptAt` com o relógio do PostgreSQL durante a inserção, usando `@CurrentTimestamp(event = INSERT, source = SourceType.DB)` do Hibernate. Reservas e novas tentativas já usam o relógio do banco. Assim, a elegibilidade inicial utiliza a mesma fonte de tempo e não sofre atraso artificial quando o relógio da JVM está adiantado. O mapeamento não exige migração de schema nem altera identificadores, estados ou contrato HTTP.

O fluxo normal é `PENDING → PROCESSING → COMPLETED/FAILED`. Uma repetição temporária devolve o trabalho a `PENDING`, com próxima execução e contador persistidos. O domínio também permite `PENDING → FAILED` para encerramento operacional. Estados terminais não possuem transição de saída. Um circuito aberto adia o trabalho e restitui a tentativa não utilizada, conforme a [ADR 003](003-provider-failure-policy.md).

Reservas vencidas não autorizam escrita pelo worker antigo. Recuperar trabalho incerto depende de idempotência confirmada no provedor; sem ela, a solicitação é encerrada para reconciliação em vez de ser reenviada automaticamente. A reserva deve superar os timeouts HTTP e a margem operacional para gravar o resultado. O vencimento não interrompe uma chamada HTTP em andamento.

### Consequências e limites

- Trabalho confirmado sobrevive a reinícios sem depender da memória do processo.
- Múltiplas instâncias coordenam reservas no mesmo banco, e a latência remota não mantém transação de banco aberta.
- O PostgreSQL atende persistência e coordenação, reduzindo infraestrutura e ampliando a dependência de sua disponibilidade e capacidade.
- A consulta periódica gera carga recorrente. Índices, tentativas limitadas, diagnóstico de idade da fila e retenção são necessários com o crescimento dos dados. Processamento concorrente não garante ordem de conclusão.
- O token protege atualizações locais; não cancela efeitos remotos nem oferece execução exatamente uma vez no provedor.
- A geração das datas de inserção pelo banco vale para registros novos. Datas históricas não são corrigidas, e o agendamento continua dependente do relógio do PostgreSQL; alterar esse relógio ainda pode afetar a elegibilidade e o vencimento das reservas.

### Alternativas e evolução

O fluxo síncrono é mais simples, mas expõe consumidores à latência externa e ainda exige estratégia para a janela entre commit e HTTP. Tarefas somente em memória perdem trabalho após falhas. Um broker aumenta o custo operacional sem requisito demonstrado na escala atual.

Avaliar lotes, retenção, particionamento ou fila externa conforme medições. Eventos futuros de conclusão devem usar outbox transacional gravada com o estado final e publicada separadamente. Não há outbox nem publicador implementados. A reconciliação deve consultar a referência/chave no provedor, registrar evidências e somente então concluir ou autorizar repetição auditada; não existe endpoint público de reinício ou reconciliação.

### Evidências

- [Datas de inserção geradas pelo banco](../../src/main/java/com/vidigal/activation/request/domain/ActivationRequest.java).
- [Reserva e escritas condicionais](../../src/main/java/com/vidigal/activation/request/persistence/ActivationQueue.java).
- [Orquestração do worker](../../src/main/java/com/vidigal/activation/request/application/ActivationWorker.java).
- [Transições do domínio](../../src/main/java/com/vidigal/activation/request/domain/ActivationStatus.java).
- [Migrações versionadas](../../src/main/resources/db/migration).

</details>
