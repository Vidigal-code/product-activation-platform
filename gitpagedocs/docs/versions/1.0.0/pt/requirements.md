# Requisitos e entrega

## Requisitos obrigatórios atendidos

| Requisito | Solução entregue |
| --- | --- |
| Java 17 ou 21, Spring Boot, build e banco relacional | Java 21, Spring Boot 3.5, Maven Wrapper e PostgreSQL 17 |
| Criar solicitações com dados obrigatórios válidos | `POST /api/v1/requests` com Bean Validation e DTO separado |
| Evitar duplicidade, inclusive simultânea | Restrição global `UNIQUE(external_id)` e tratamento após rollback |
| Persistir os campos exigidos e consultar por ID | Identificadores, cliente, produto, estado e datas; `GET /api/v1/requests/{id}` |
| Representar os quatro estados | `PENDING`, `PROCESSING`, `COMPLETED` e `FAILED` |
| Integrar com provedor e definir resultados | Cliente HTTP com tratamento de sucesso, erro de negócio, falha temporária e timeout |
| Tratar entradas inválidas e erros com segurança | Respostas ProblemDetail sem stack traces ou detalhes internos |
| Implementar ou descrever JWT/OAuth 2.0 | Resource Server implementado e fluxo Client Credentials documentado |
| Testar criação, duplicidade, consulta e integração | Testes automatizados pelas fronteiras públicas e pelo banco real |
| Manter organização e responsabilidades claras | Código organizado por funcionalidade, com API, aplicação, domínio, persistência e integração |
| Responder ao cenário de lentidão | Diagnóstico e critérios de validação descritos em Operação e diagnóstico |
| Fornecer Dockerfile e instruções de produção | Imagem em múltiplos estágios, execução sem privilégios e configuração externa |
| Documentar execução, decisões e limites | README bilíngue, matriz em `docs/requirements.md` e registros em `docs/adr/` |

## Diferenciais implementados

| Grupo | Recursos |
| --- | --- |
| Java moderno | DTOs com `record` e contratos imutáveis nas fronteiras; Spring Boot 3.x e Java 21 |
| API | `201` com `Location`, repetição idêntica com `200`, conflito com `409`, UTC/ISO-8601, validação de formato e tamanho |
| Consulta | Paginação limitada, filtros por estado e cliente, índices por consumidor e ordenação estável |
| Processamento | Trabalho persistente, reserva com vencimento, proteção contra worker atrasado e estados controlados pelo domínio |
| Integração | Timeouts configuráveis, tentativas limitadas, backoff com jitter, `Retry-After` e circuit breaker |
| Segurança | JWT com escopos, isolamento por consumidor, limites de corpo e de taxa compartilhados no banco |
| Persistência | Migrações Flyway, restrições de integridade e testes concorrentes com PostgreSQL |
| Operação | Health checks, correlação, logs JSON, métricas por estado e tracing entre API, worker e provedor |
| Qualidade | Testes de domínio, contratos HTTP, autorização, concorrência, arquitetura e navegação real |
| Execução | Compose, desligamento gracioso, limites de recursos, CI e relatórios de testes |
| Experimentação | Swagger com 16 cenários automáticos e coleção Postman executável por Newman |
| Histórico de entrega | Commits pequenos separam build, persistência, API, segurança, integração, testes e documentação, com mensagens sobre a mudança |
| Decisões arquiteturais | Registros em inglês e português em `docs/adr/` distinguem decisões aceitas de evoluções propostas |

Eventos de conclusão com outbox, implantação cloud e interface auditada de reconciliação permanecem como evolução documentada. Não há infraestrutura adicional de mensageria ou cache para executar a solução.

## Referências do projeto

- [Matriz de requisitos e critérios de entrega](https://github.com/Vidigal-code/product-activation-platform/blob/main/docs/requirements.md).
- [README](https://github.com/Vidigal-code/product-activation-platform/blob/main/README.md).

[Contrato da API](?lang=pt&menu=pt&id=104) · [Testes](?lang=pt&menu=pt&id=107) · [Limitações e evolução](?lang=pt&menu=pt&id=108)

[Registros de decisão arquitetural (inglês e português)](https://github.com/Vidigal-code/product-activation-platform/blob/main/docs/adr/README.md).
