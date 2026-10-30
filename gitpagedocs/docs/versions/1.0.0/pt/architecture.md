# Arquitetura e processamento

## Trabalho persistente

A solicitação gravada também representa a unidade de trabalho. O worker reserva linhas elegíveis com mecanismos de concorrência do PostgreSQL, incluindo `FOR UPDATE SKIP LOCKED`, e confirma a transação antes do HTTP.

Na inserção, o Hibernate obtém do PostgreSQL os valores iniciais de `createdAt`, `updatedAt` e do campo interno `nextAttemptAt`. Reservas e novas tentativas usam o mesmo relógio do banco. Isso evita atraso artificial na elegibilidade de novas solicitações quando a JVM está com o relógio adiantado, sem migração de schema nem alteração de identificadores, estados ou contrato HTTP. Datas históricas não são corrigidas; mudanças no relógio do próprio PostgreSQL ainda podem afetar o agendamento e o vencimento das reservas.

A reserva possui prazo e token de posse. A gravação do resultado acontece em outra transação e exige posse válida. Isso permite múltiplas instâncias e impede que um worker atrasado sobrescreva o resultado produzido após uma nova reserva.

| Origem | Destino permitido | Uso |
| --- | --- | --- |
| `PENDING` | `PROCESSING` | Reserva para tentativa |
| `PENDING` | `FAILED` | Encerramento por condição operacional sem nova chamada |
| `PROCESSING` | `PENDING` | Nova tentativa agendada ou adiamento por circuito aberto |
| `PROCESSING` | `COMPLETED` | Ativação confirmada pelo provedor |
| `PROCESSING` | `FAILED` | Falha definitiva, orçamento esgotado ou resultado incerto sem repetição segura |
| `COMPLETED` / `FAILED` | Nenhum | Estados terminais |

Não há execução exatamente uma vez no sistema externo. Uma chave `Idempotency-Key` estável só impede efeitos duplicados quando o provedor garante esse contrato.

## Política de resultados

| Retorno | Decisão |
| --- | --- |
| Sucesso `2xx` | `COMPLETED`, sem repetição |
| `400` e demais erros permanentes de entrada | `FAILED`, sem reenviar o mesmo conteúdo inválido |
| `500` e falhas transitórias | Tentativas limitadas com atraso crescente; `FAILED` ao esgotar o orçamento |
| `429` / `503` | Considerar `Retry-After` na repetição limitada |
| Timeout ou falha de conexão ambígua | Sem idempotência confirmada, `FAILED` para reconciliação; com contrato confirmado, repetição limitada |
| Reserva vencida após interrupção | Recuperar dentro do orçamento somente quando o contrato externo torna a repetição segura; caso contrário, encaminhar para reconciliação |
| Circuito aberto | Devolver a `PENDING`, persistir o adiamento e restituir a tentativa que não chamou o provedor |

Por padrão, são três tentativas, intervalo inicial de um segundo, teto de backoff de 30 segundos e reserva de 30 segundos. O jitter reduz aleatoriamente o atraso para 80%–100% do valor calculado. `Retry-After` positivo válido prevalece mesmo acima do teto. Um valor numérico impossível de agendar produz `PROVIDER_RETRY_AFTER_UNSUPPORTED`, sem antecipar outra chamada.

O circuit breaker observa janela de 20 chamadas, mínimo de 10 e limiar de falhas de 50%. Abre por 10 segundos e permite duas sondagens em meio-aberto. Falhas transitórias e incertas contam; rejeições de negócio não. O estado do circuito pertence a cada instância.

O cliente utiliza timeouts de conexão e leitura configuráveis. Os padrões da aplicação são dois e cinco segundos; no Compose, um e dois segundos. A duração da reserva precisa superar os timeouts e a margem necessária para gravar o resultado.

Repetir `500` pressupõe que o contrato externo permita reenvio após esse retorno. Se o provedor puder concluir a ativação antes de responder `500`, é necessário confirmar idempotência ou reconciliar antes de repetir. Um timeout também não prova ausência de efeito remoto.

## Provedor simulado

O WireMock disponibiliza `POST /provider/v1/activate` e exige `Idempotency-Key`.

| `productCode` | Retorno simulado |
| --- | --- |
| `INTERNET-500` ou código comum | `200` |
| `BUSINESS-ERROR` | `400` |
| `TEMPORARY-ERROR` | `500` |
| `TIMEOUT` | `200` após cinco segundos |
| `RATE-LIMITED` | `429` com `Retry-After: 2` |
| `UNAVAILABLE` | `503` com `Retry-After: 2` |

O mock simula respostas, sem efeitos reais nem deduplicação durável. Por isso, `PROVIDER_IDEMPOTENCY_SUPPORTED=false` no Compose. Habilitar a opção em produção exige um contrato explícito sobre concorrência, retenção da chave, igualdade do conteúdo e recuperação.

## Decisões arquiteturais

O serviço usa a solicitação persistida como unidade de trabalho. A decisão reduz dependências operacionais e cobre a interrupção entre commit e chamada remota. Consulta e criação usam JPA; a reserva utiliza SQL específico do PostgreSQL para preservar concorrência e posse.

| Alternativa | Avaliação |
| --- | --- |
| Fluxo síncrono | Mais simples, mas expõe o consumidor à latência externa e ainda exige estratégia para a janela entre commit e HTTP |
| Tarefas somente em memória | Perdem trabalho após uma interrupção do processo |
| Broker adicional | Pode atender volumes e consumidores maiores, mas amplia a infraestrutura sem necessidade atual demonstrada |
| Trabalho persistente no PostgreSQL | Reutiliza o banco, permite reserva entre instâncias e mantém controle durável de tentativas |

O custo dessa escolha é a consulta periódica e a capacidade limitada do worker. Crescimento sustentado exige avaliar concorrência, índices, processamento em lotes e retenção antes de adicionar infraestrutura.

## Referências do projeto

- [Reserva persistente](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/request/persistence/ActivationQueue.java).
- [Máquina de estados](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/request/domain/ActivationStatus.java).
- [Política de repetição](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/request/integration/RetryPolicy.java).
- [Decisões completas](https://github.com/Vidigal-code/product-activation-platform/blob/main/docs/requirements.md).

[Contrato da API](?lang=pt&menu=pt&id=104) · [Segurança](?lang=pt&menu=pt&id=106) · [Operação e limitações](?lang=pt&menu=pt&id=108)

[Registros de decisão arquitetural (inglês e português)](https://github.com/Vidigal-code/product-activation-platform/blob/main/docs/adr/README.md).
