# ADR 001 — Trabalho persistente no PostgreSQL

Status: aceita.

## Contexto

A ativação depende de um provedor que pode rejeitar a entrada, ficar indisponível ou responder depois do timeout. O serviço precisa sobreviver à queda entre a gravação local e a chamada externa, aceitar retries dos consumidores e permitir múltiplas instâncias. Não existe requisito de grande volume que justifique operar um broker adicional.

## Decisão

Utilizar a solicitação persistida como unidade de trabalho. A criação grava `PENDING`. Um worker reserva a próxima linha elegível em transação curta, registra prazo de expiração e token de posse, e confirma a transação antes de iniciar HTTP. Outra transação registra o resultado somente se a posse ainda for válida. A consulta e a criação usam JPA; a operação específica de reserva utiliza os recursos de concorrência do PostgreSQL.

O banco garante unicidade global de `external_id`. Depois de uma violação de unicidade, a transação de inserção deve terminar antes de consultar o vencedor. Mesmo conteúdo do mesmo consumidor retorna o registro existente. Divergência ou posse de outro consumidor retorna conflito sem expor nem sobrescrever os dados. Consulta e listagem restringem resultados ao `sub` validado; o modo local utiliza `local-demo`. Registros anteriores à migração de posse precisam de atribuição auditada antes da operação com consumidores JWT.

Estados terminais são `COMPLETED` e `FAILED`. Repetições temporárias devolvem o trabalho a `PENDING` com próxima execução e contador persistidos. Reservas vencidas não autorizam sobrescrita pelo antigo worker. Sem idempotência confirmada no provedor, um resultado incerto exige reconciliação; o estado local `FAILED` não prova ausência de efeito remoto.

## Garantias e limites

- Uma linha por `externalId`, inclusive sob criação simultânea.
- O trabalho confirmado no banco não depende da memória do processo.
- A chamada remota não mantém transação de banco aberta.
- O token de posse evita atualização local por worker substituído.
- O sistema não oferece execução exatamente uma vez no provedor.
- Uma chave estável só evita efeitos duplicados se o provedor implementar e garantir esse contrato.
- A política de repetir erros `5xx` pressupõe que esse retorno é repetível segundo o contrato externo. Provedores que podem concluir uma operação antes de retornar `500` exigem idempotência ou reconciliação.
- `Retry-After` válido prevalece sobre o teto do backoff. Um valor numérico que excede o intervalo de agendamento suportado encerra o trabalho com `PROVIDER_RETRY_AFTER_UNSUPPORTED`, sem antecipar outra chamada.
- O prazo da reserva deve superar os timeouts HTTP e a margem operacional. Expiração é uma proteção de recuperação, não um mecanismo para interromper uma chamada HTTP em curso.

## Consequências

O PostgreSQL atende persistência e coordenação, reduzindo custo operacional. Em contrapartida, o worker tem capacidade limitada e exige índices adequados, métricas de idade da fila, limites de tentativas e retenção dos registros. A consulta periódica gera carga pequena e previsível; volume futuro deve ser medido antes de ampliar concorrência ou introduzir broker.

Readiness inclui o banco. O provedor é uma dependência do processamento; indisponibilidade temporária dele não deve provocar ciclos de reinício da aplicação.

## Autenticação e CSRF / Authentication and CSRF

CSRF permanece desativado para Bearer explícito sistema-a-sistema. Cookies/Basic não autenticam e `text/plain` não cria solicitações. Introduzir navegador ou credenciais automáticas exige habilitar CSRF. Consulte o [modelo de segurança](../../README.md#segurança-e-configuração).

CSRF remains disabled for explicit system-to-system Bearer authentication. Cookies/Basic cannot authenticate and `text/plain` cannot create requests. Browser clients or automatically attached credentials require enabling CSRF. See the [security model](../../README.md#security-and-configuration).

## Evolução

Para publicar eventos de conclusão, persistir um evento outbox na mesma transação do estado final e publicá-lo separadamente. Para reconciliação, consultar a referência/chave no provedor, registrar evidência e somente então concluir ou autorizar nova tentativa auditada. Para volume maior, avaliar processamento em lotes, particionamento/retenção e fila externa conforme medições.

## Alternativas consideradas

Processamento síncrono é mais simples, mas expõe o consumidor à latência externa e continua exigindo estratégia para a janela entre commit e HTTP. Tarefas apenas em memória perdem trabalho em quedas. Um broker atenderia coordenação, mas adicionaria infraestrutura sem requisito proporcional nesta entrega.
