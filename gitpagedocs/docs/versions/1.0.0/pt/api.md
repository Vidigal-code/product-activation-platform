# Contrato da API

## Criar uma solicitação

`POST /api/v1/requests` recebe JSON:

```json
{
  "externalId": "EXT-12345",
  "customerId": "CUST-10001",
  "productCode": "INTERNET-500"
}
```

Uma criação retorna `201 Created`, cabeçalho `Location` e os campos `id`, `externalId`, `customerId`, `productCode`, `status`, `createdAt` e `updatedAt`. Datas usam UTC/ISO-8601. A resposta também informa `attemptCount` e, quando aplicável, um `failureCode` seguro.

O `201` confirma o registro local. O resultado da ativação é assíncrono e deve ser consultado pelo endereço retornado.

| Situação | Resposta |
| --- | --- |
| Solicitação nova e válida | `201 Created` com `Location` |
| Mesmo identificador e conteúdo, do mesmo consumidor | `200 OK`, mesmo registro |
| Mesmo identificador com conteúdo diferente | `409 Conflict` |
| Identificador de outro consumidor | `409 Conflict`, sem revelar seu conteúdo |
| Corpo, campo ou UUID inválido | `400 Bad Request` |
| Registro ausente ou de outro consumidor | `404 Not Found` |
| Token ausente ou inválido no modo JWT | `401 Unauthorized` |
| Escopo insuficiente | `403 Forbidden` |
| Tipo de conteúdo incompatível | `415 Unsupported Media Type` |
| Corpo acima do limite | `413 Content Too Large` |
| Cota de chamadas esgotada | `429 Too Many Requests` com `Retry-After` |
| Falha interna inesperada | `500` com ProblemDetail genérico |

## Validação

`externalId` e `customerId` aceitam de 1 a 100 caracteres; `productCode`, de 1 a 80. O primeiro caractere é uma letra ASCII ou um dígito. Os demais permitem letras ASCII, dígitos, ponto, sublinhado, dois-pontos e hífen. Espaços são rejeitados, sem remoção silenciosa. Campos JSON desconhecidos e conteúdo adicional após o objeto são rejeitados.

`X-Correlation-Id` aceita de 1 a 64 letras, dígitos, pontos, sublinhados ou hífens. Valores ausentes ou inválidos são substituídos por um identificador gerado.

## Duplicidade e concorrência

`externalId` é globalmente único e diferencia maiúsculas de minúsculas. A restrição `UNIQUE` do PostgreSQL é a garantia final; consultar antes de inserir não seria suficiente.

Se duas inserções concorrem, a transação perdedora termina em rollback antes da consulta ao registro vencedor. Conteúdo igual do mesmo consumidor retorna esse registro. Conteúdo diferente ou pertencente a outro consumidor retorna conflito. Uma falha terminal não libera o identificador para nova ativação.

## Consultar e listar

`GET /api/v1/requests/{id}` retorna a solicitação e seu estado atual. A ausência do registro ou sua posse por outro consumidor produz `404`.

`GET /api/v1/requests?status=COMPLETED&customerId=CUST-10001&page=0&size=20` lista somente registros do consumidor atual. Os filtros são opcionais. A resposta contém `items`, `page`, `size`, `totalElements`, `totalPages` e `hasNext`, com ordem `createdAt ASC, id ASC`.

O tamanho padrão é 20, o máximo é 100 e a página máxima é 10000. Os limites são configuráveis. Histórico profundo pode exigir cursores e retenção após avaliação dos planos de consulta.

## Índices da listagem

A [migração V2](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/resources/db/migration/V2__isolate_consumers_and_index_request_queries.sql) cria os seguintes índices:

| Índice | Colunas | Consulta prevista |
| --- | --- | --- |
| `ix_activation_consumer_created` | `(consumer_id, created_at, id)` | Solicitações do consumidor sem filtro opcional |
| `ix_activation_consumer_status_created` | `(consumer_id, status, created_at, id)` | Solicitações do consumidor filtradas por estado |
| `ix_activation_consumer_customer_created` | `(consumer_id, customer_id, created_at, id)` | Solicitações do consumidor filtradas por cliente |

Os predicados de igualdade precedem as colunas da ordenação estável. Os índices atendem aos principais padrões de acesso sem indexar todas as combinações. Com os dois filtros opcionais presentes, o PostgreSQL pode escolher um caminho de acesso e avaliar o predicado restante. Confirme os planos reais com `EXPLAIN (ANALYZE, BUFFERS)` em ambiente controlado antes de criar outro índice composto; índices adicionais consomem espaço e aumentam o custo de escrita. A restrição independente `UNIQUE(external_id)` protege a deduplicação, não a paginação.

## Referências do projeto

- [Controller da API](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/request/api/ActivationRequestController.java).
- [Migrações](https://github.com/Vidigal-code/product-activation-platform/tree/main/src/main/resources/db/migration).

[Processamento](?lang=pt&menu=pt&id=105) · [Segurança](?lang=pt&menu=pt&id=106) · [Exemplos locais](?lang=pt&menu=pt&id=103)
