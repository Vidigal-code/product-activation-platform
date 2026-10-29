# ADR 006 — Container operation and observability

## 🇺🇸 English Description

<details>
<summary><strong>View Details</strong></summary>

### Status and context

Accepted. Execution must be reproducible and operational failures diagnosable without hiding provider latency behind a fast registration response. Docker execution is required; Compose, CI, health checks and richer telemetry are implemented improvements. Provisioning cloud infrastructure is not required.

### Decision

Build the application with Maven Wrapper and use a multi-stage Dockerfile with Java 21 runtime, an unprivileged user, container-aware memory settings, graceful shutdown and a readiness check. Keep credentials, URLs, timeouts and capacity parameters outside source code. Compose starts the application, PostgreSQL and WireMock, with an optional temporary Newman runner.

The application CI validates Java and JavaScript tests, configuration, image construction, browser flows and HTTP scenarios. It does not publish a backend image to an external registry or deploy the activation service to a cloud account. Promoting the tested image by immutable digest, controlled schema rollout and production rollback remain deployment guidance.

Expose process liveness and database-aware readiness without internal details. Provider outages are processing failures, not a reason to repeatedly restart otherwise healthy instances. Emit structured ECS JSON logs with correlation and bounded result fields, avoiding secrets and sensitive payloads.

Use Micrometer for HTTP/provider latency, worker outcomes, JVM/pool diagnostics, circuit metrics and persisted counts by status. A grouped query refreshes global state totals periodically; failure preserves the previous sample. Replicas observe the same totals, so aggregation across instances uses `max`, not `sum`. These counts include retained terminal records; queue age still requires a separate database query.

Persist W3C trace context with the request, restore it in `activation.process` and propagate it to the provider HTTP call. OTLP export is disabled by default. An optional Compose override enables Jaeger for local tracing, with transient in-memory storage and loopback UI; production requires a protected collector and suitable sampling/retention.

### Consequences and alternatives

One Compose command supplies a reproducible demonstration without cloud accounts or extra messaging infrastructure. It is not a production security profile: production requires JWT, TLS, network controls, managed secrets, backup/restore procedures and controlled database privileges.

Readiness that included provider availability could remove every healthy instance during an external outage. Logging full bodies would expose data and increase cost. Adding unbounded identifiers to metric labels would raise cardinality. The selected telemetry provides correlation without those dependencies, but still needs production dashboards, alert thresholds and retention policies.

Instrumentation and periodic counts have measurable overhead. Monitor database capacity and queue age before increasing worker concurrency or polling frequency. For latency incidents, separate thread/pool wait, SQL, transaction and remote-call time; validate changes using comparable load, percentiles, errors and total activation duration, not only POST latency.

### Evidence

- [Container image](../../Dockerfile) and [local environment](../../docker-compose.yml).
- [Application CI](../../.github/workflows/ci.yml).
- [Persisted-state metrics](../../src/main/java/com/vidigal/activation/observability/RequestMetrics.java).
- [Tracing and configuration](../../src/main/resources/application.yml).
- [Optional local tracing](../../docker-compose.observability.yml).

</details>

## 🇧🇷 Descrição em Português

<details>
<summary><strong>Ver Detalhes</strong></summary>

### Estado e contexto

Aceita. A execução precisa ser reproduzível e as falhas diagnosticáveis, sem esconder latência do provedor atrás de uma resposta rápida de registro. Docker é obrigatório; Compose, CI, saúde e telemetria ampliada são diferenciais implementados. Provisionar infraestrutura cloud não é obrigatório.

### Decisão

Construir com Maven Wrapper e usar Dockerfile em múltiplos estágios com runtime Java 21, usuário sem privilégios, memória adequada a contêiner, desligamento gracioso e verificação de prontidão. Manter credenciais, URLs, timeouts e parâmetros de capacidade fora do código. O Compose inicia aplicação, PostgreSQL e WireMock, com runner Newman temporário opcional.

A CI da aplicação valida testes Java e JavaScript, configuração, construção da imagem, fluxos de navegador e cenários HTTP. Ela não publica a imagem do backend em registro externo nem implanta o serviço em conta cloud. Promover a imagem testada por digest imutável, controlar migrações e realizar rollback em produção permanecem orientações de implantação.

Expor liveness do processo e readiness com banco, sem detalhes internos. Falhas do provedor são problemas do processamento, não motivo para reiniciar repetidamente instâncias saudáveis. Emitir logs JSON ECS com correlação e campos de resultado limitados, sem segredos ou payloads sensíveis.

Utilizar Micrometer para latência HTTP/provedor, resultados do worker, diagnóstico JVM/pool, circuito e totais persistidos por estado. Uma consulta agrupada atualiza periodicamente os totais globais; falhas preservam a amostra anterior. Réplicas observam os mesmos totais, portanto a agregação entre instâncias usa `max`, não `sum`. Esses totais incluem registros terminais retidos; a idade da fila ainda exige consulta separada ao banco.

Persistir contexto W3C com a solicitação, restaurá-lo em `activation.process` e propagá-lo ao HTTP do provedor. Exportação OTLP fica desabilitada por padrão. Um override opcional do Compose habilita Jaeger local, com armazenamento transitório em memória e interface em loopback; produção exige coletor protegido, amostragem e retenção adequadas.

### Consequências e alternativas

Um comando Compose fornece demonstração reproduzível sem conta cloud ou mensageria adicional. Esse não é um perfil de segurança de produção: produção exige JWT, TLS, controles de rede, gestão de segredos, procedimentos de backup/restauração e privilégios controlados no banco.

Incluir o provedor na readiness poderia retirar todas as instâncias saudáveis durante indisponibilidade externa. Registrar corpos completos exporia dados e aumentaria custo. Identificadores ilimitados em labels elevariam a cardinalidade. A telemetria escolhida permite correlação sem essas dependências, mas ainda exige dashboards, alertas e políticas de retenção em produção.

Instrumentação e contagens periódicas têm custo mensurável. Monitorar capacidade do banco e idade da fila antes de ampliar concorrência ou frequência de consulta. Em incidentes de lentidão, separar espera por thread/pool, SQL, transação e chamada remota; validar mudanças com carga equivalente, percentis, erros e duração total da ativação, não apenas latência do POST.

### Evidências

- [Imagem de contêiner](../../Dockerfile) e [ambiente local](../../docker-compose.yml).
- [CI da aplicação](../../.github/workflows/ci.yml).
- [Métricas de estados persistidos](../../src/main/java/com/vidigal/activation/observability/RequestMetrics.java).
- [Tracing e configuração](../../src/main/resources/application.yml).
- [Tracing local opcional](../../docker-compose.observability.yml).

</details>
