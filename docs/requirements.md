# Matriz de requisitos

O escopo obrigatório deriva do desafio de ativação de produtos. A classificação abaixo preserva a distinção do enunciado; a entrega também inclui os diferenciais técnicos selecionados. Os tópicos de microsserviços/cloud solicitam explicação e não exigem infraestrutura externa.

| Item | Classificação | Entrega e evidência |
| --- | --- | --- |
| Java 17/21, Spring Boot, Maven e banco relacional | Obrigatório | Java 21, Spring Boot 3.5, Maven Wrapper, PostgreSQL 17; `pom.xml` |
| Criar e validar solicitação | Obrigatório | `POST /api/v1/requests`; DTO separado e Bean Validation |
| Evitar duplicidade simultânea | Obrigatório | Restrição `UNIQUE(external_id)` e tratamento após rollback |
| Campos exigidos e consulta por ID | Obrigatório | Persistência, UTC e `GET /api/v1/requests/{id}` |
| Estados PENDING/PROCESSING/COMPLETED/FAILED | Obrigatório | Transições explícitas e estados terminais protegidos |
| Provedor com sucesso, 400, 500 e timeout | Obrigatório | Cliente HTTP configurável e fixtures WireMock |
| Erros de validação, ausência, conflito, integração e inesperados | Obrigatório | ProblemDetail e falha persistida sem detalhes internos |
| Segurança JWT/OAuth2 descrita ou implementada | Obrigatório | Resource Server, escopos, configuração e fluxo Client Credentials no README |
| Testes de criação, duplicidade, consulta e integração | Obrigatório | Testes unitários/componentes e integração com banco real |
| Organização e manutenção | Obrigatório | Organização por funcionalidade, responsabilidades separadas, sem camadas genéricas |
| Quatro respostas sobre lentidão | Obrigatório | README nos dois idiomas |
| Dockerfile e instruções de produção | Obrigatório | Build em dois estágios, usuário sem privilégios, configuração externa |
| README com execução, decisões, pendências e evolução | Obrigatório | README nos dois idiomas |
| 201 + Location, repetição 200 e conflito 409 | Diferencial implementado | Semântica documentada; concorrência resulta em uma linha |
| Migrações e ambiente único | Diferencial implementado | Flyway e Docker Compose com aplicação, banco e provedor; runner efêmero no profile `test` |
| Trabalho assíncrono persistente | Diferencial implementado | Reserva, vencimento, posse e atualização fora da chamada remota |
| Timeouts e política de repetição | Diferencial implementado | Configuração de conexão/leitura, tentativas limitadas e backoff |
| Segurança por escopos | Diferencial implementado | Resource Server e testes de autorização |
| Isolamento por consumidor | Diferencial implementado | `sub` validado em criação, consulta, listagem e repetição; GET alheio 404 e conflito genérico |
| Health e correlação | Diferencial implementado | Actuator, correlação persistida e cabeçalho externo |
| CI e relatórios de testes | Diferencial implementado | GitHub Actions, Maven verify, Testcontainers e JaCoCo |
| Cenário de causa comum e validação da correção | Diferencial documentado | README, itens 5 e 6 da análise de produção |
| Paginação e filtros | Diferencial implementado | `GET /api/v1/requests`, status/customerId, limites configuráveis e ordenação estável; índices por consumidor |
| Rate limiting e tamanho do corpo | Diferencial implementado | Cota por consumidor compartilhada no PostgreSQL, limpeza periódica, `429`/`Retry-After` e limite de bytes com `413` |
| Circuit breaker e jitter | Diferencial implementado | Resilience4j, adiamento persistido sem consumir tentativa e backoff aleatório limitado; `Retry-After` preservado |
| Tracing distribuído e logs JSON | Diferencial implementado | JSON ECS, Micrometer/OpenTelemetry, contexto W3C persistido entre API/worker/provedor; Jaeger opcional em override |
| Métricas por estado, latência e resultado | Diferencial implementado | Gauges persistidos por estado, timers de API/provedor, contador de resultados e métricas do circuito |
| Máquina de estados testada | Diferencial implementado | Domínio centraliza transições; fila utiliza essas regras e protege posse atomicamente; testes da matriz completa |
| Recursos modernos da linguagem | Diferencial implementado | Java 21, records para contratos/DTOs e APIs compatíveis gerenciadas pelo Spring Boot 3.5 |
| Eventos de conclusão e cloud | Diferencial documentado | Outbox, secrets, múltiplas instâncias e promoção de imagem |
| Testes de dependências arquiteturais | Diferencial implementado | ArchUnit: domínio independente, API sem persistência direta e integração sem API/persistência |
| Organização dos testes | Diferencial implementado | Pastas `unit`, `integration` e `e2e`, seleção por Maven e explicação no README |
| Contratos Swagger e Postman | Complemento implementado | OpenAPI com exemplos e coleção autocontida; Newman no Docker e na CI |

## Critérios de verificação

1. Preparar `.env` com o script e executar `docker compose up --build -d --wait`.
2. Criar uma solicitação e consultar até estado terminal.
3. Repetir a criação e verificar o mesmo ID; alterar o conteúdo e verificar conflito.
4. Executar `mvnw verify` com Docker disponível para validar a suite completa, incluindo concorrência PostgreSQL.
5. Conferir os relatórios de testes e o estado dos health checks.

O comando de build da imagem não roda Testcontainers dentro do estágio Docker. A CI executa a verificação completa no host antes de construir a imagem.
