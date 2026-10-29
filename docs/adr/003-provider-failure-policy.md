# ADR 003 — Explicit provider failure policy

## 🇺🇸 English Description

<details>
<summary><strong>View Details</strong></summary>

### Status and context

Accepted. The service must define outcomes for success, business errors, server failures and timeout. A timeout is ambiguous: the provider may have completed the activation. Bounded retries, `Retry-After` and circuit breaking are implemented improvements, not guarantees of exactly-once execution.

### Decision

Use Spring `RestClient` with a JDK HTTP client, configurable connection/read timeouts and redirects disabled. Keep the provider origin/path and optional credential in external configuration. Send a stable `Idempotency-Key` derived from the persisted request ID and propagate correlation.

| Outcome | Policy |
| --- | --- |
| `2xx` | Complete without retry |
| Permanent response, including `400` | Fail without resubmitting invalid input |
| `5xx` or `429` | Retry within the configured attempt budget, then fail |
| `429` / `503` with valid `Retry-After` | Honor the provider delay when it exceeds calculated backoff |
| Timeout or ambiguous transport error | Retry only with confirmed provider idempotency; otherwise fail locally for reconciliation |
| Open circuit | Make no HTTP call; persist deferral and restore the unused attempt |

Backoff doubles up to the configured cap. Jitter reduces the calculated delay within the configured range; `Retry-After` remains the minimum accepted delay, even above the cap. The header supports seconds and HTTP dates. Malformed dates, negative values and expired dates fall back to normal backoff. Numeric values beyond the supported PostgreSQL scheduling horizon terminate with `PROVIDER_RETRY_AFTER_UNSUPPORTED`; the client does not retry prematurely.

The circuit breaker counts transient and uncertain results, not business rejections. It opens after the configured sample/threshold, then allows bounded half-open probes. Each instance has its own circuit state. Work deferred by the circuit stays durable in PostgreSQL.

### Contract assumptions and limits

Retrying `5xx` assumes the provider permits replay after that response. If an operation may complete before a `500`, the contract must guarantee idempotency or the policy must treat that result as uncertain and require reconciliation.

`PROVIDER_IDEMPOTENCY_SUPPORTED` is false in Compose because WireMock simulates responses without durable side effects or deduplication. Enabling it for a real provider requires guarantees covering concurrency, key retention, identical payloads and recovery. A local `FAILED` after uncertainty does not prove the remote activation failed.

The lease must exceed timeouts and the update margin. Correlation and stable keys aid diagnosis but cannot replace a provider status query. There is no automated provider reconciliation or public restart endpoint.

### Alternatives and verification

Retrying every failure risks duplicate side effects and repeated invalid input. Never retrying reduces availability during temporary failures. An unbounded retry loop can overload dependencies and leave work indefinitely active. The selected policy makes those tradeoffs explicit and persists retry timing and attempt limits.

WireMock tests exercise status codes, delayed responses, retry headers and circuit behavior. Policy tests cover attempt bounds, jitter and delay calculation without waiting for real delays. Configuration defaults remain in [application.yml](../../src/main/resources/application.yml).

### Evidence

- [HTTP client and classification](../../src/main/java/com/vidigal/activation/request/integration/ActivationProvider.java).
- [Retry policy](../../src/main/java/com/vidigal/activation/request/integration/RetryPolicy.java).
- [Circuit breaker](../../src/main/java/com/vidigal/activation/request/integration/ProviderCircuitBreaker.java).
- [Simulated provider fixtures](../../infra/wiremock/mappings).

</details>

## 🇧🇷 Descrição em Português

<details>
<summary><strong>Ver Detalhes</strong></summary>

### Estado e contexto

Aceita. O serviço precisa definir resultados para sucesso, erro de negócio, falha de servidor e timeout. Timeout é ambíguo: o provedor pode ter concluído a ativação. Repetições limitadas, `Retry-After` e circuit breaker são diferenciais implementados, não garantias de execução exatamente uma vez.

### Decisão

Utilizar `RestClient` do Spring com cliente HTTP do JDK, timeouts configuráveis de conexão/leitura e redirecionamentos desabilitados. Manter origem/caminho do provedor e credencial opcional em configuração externa. Enviar `Idempotency-Key` estável derivada do ID persistido e propagar correlação.

| Resultado | Política |
| --- | --- |
| `2xx` | Concluir sem repetição |
| Resposta permanente, incluindo `400` | Falhar sem reenviar entrada inválida |
| `5xx` ou `429` | Repetir dentro do orçamento configurado e falhar ao esgotá-lo |
| `429` / `503` com `Retry-After` válido | Respeitar o atraso informado quando superar o backoff calculado |
| Timeout ou erro de transporte ambíguo | Repetir somente com idempotência confirmada; caso contrário, falhar localmente para reconciliação |
| Circuito aberto | Não chamar HTTP; persistir adiamento e restituir a tentativa não utilizada |

O backoff dobra até o teto configurado. O jitter reduz o atraso calculado dentro da faixa configurada; `Retry-After` permanece como atraso mínimo, mesmo acima do teto. O cabeçalho aceita segundos e datas HTTP. Datas malformadas, valores negativos e datas vencidas utilizam o backoff normal. Valores numéricos além do horizonte de agendamento suportado pelo PostgreSQL encerram com `PROVIDER_RETRY_AFTER_UNSUPPORTED`; o cliente não repete antecipadamente.

O circuito contabiliza resultados transitórios e incertos, não rejeições de negócio. Abre após a amostra/limiar configurados e permite sondagens limitadas em meio-aberto. Cada instância mantém seu próprio estado do circuito. O trabalho adiado permanece durável no PostgreSQL.

### Pressupostos do contrato e limites

Repetir `5xx` pressupõe que o provedor permita reenvio após essa resposta. Se uma operação puder concluir antes de retornar `500`, o contrato precisa garantir idempotência ou a política deve considerar esse resultado incerto e exigir reconciliação.

`PROVIDER_IDEMPOTENCY_SUPPORTED` é falso no Compose porque WireMock simula respostas sem efeitos ou deduplicação duráveis. Habilitá-lo para um provedor real exige garantias sobre concorrência, retenção da chave, conteúdo idêntico e recuperação. Um `FAILED` local após incerteza não prova falha na ativação remota.

A reserva deve superar os timeouts e a margem para atualização. Correlação e chaves estáveis ajudam no diagnóstico, mas não substituem consulta de status ao provedor. Não há reconciliação automática com o provedor nem endpoint público de reinício.

### Alternativas e verificação

Repetir toda falha arrisca efeitos duplicados e reenvio de entrada inválida. Nunca repetir reduz disponibilidade em falhas temporárias. Repetição ilimitada pode sobrecarregar dependências e manter trabalho ativo indefinidamente. A política escolhida explicita esses tradeoffs e persiste o agendamento e o limite de tentativas.

Testes WireMock exercitam códigos HTTP, respostas atrasadas, cabeçalhos de repetição e comportamento do circuito. Testes de política cobrem limites de tentativas, jitter e cálculo de atraso sem aguardar atrasos reais. Os padrões permanecem em [application.yml](../../src/main/resources/application.yml).

### Evidências

- [Cliente HTTP e classificação](../../src/main/java/com/vidigal/activation/request/integration/ActivationProvider.java).
- [Política de repetição](../../src/main/java/com/vidigal/activation/request/integration/RetryPolicy.java).
- [Circuit breaker](../../src/main/java/com/vidigal/activation/request/integration/ProviderCircuitBreaker.java).
- [Fixtures do provedor simulado](../../infra/wiremock/mappings).

</details>
