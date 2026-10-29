# Segurança e configuração

O modo padrão é OAuth 2.0 Resource Server com JWT. O consumidor obtém um token no provedor de identidade pelo fluxo Client Credentials e o envia em `Authorization: Bearer`. Este serviço não emite tokens.

São verificados assinatura, emissor, audiência e validade temporal. Descoberta e JWKS fornecem as chaves públicas e permitem sua rotação. Configure `JWT_ISSUER_URI` e `JWT_AUDIENCE` para o ambiente real.

| Operação | Escopo |
| --- | --- |
| Criar solicitação | `requests:write` |
| Consultar ou listar | `requests:read` |
| Consultar Prometheus | `monitoring:read` |

O proprietário é o `sub` validado do JWT, com até 255 caracteres e sem valor vazio. Ele não pode ser definido no corpo da requisição. O modo local utiliza `local-demo`. Registros anteriores à migração de posse exigem atribuição auditada antes da operação com consumidores JWT.

Cookies e Basic não autenticam. CSRF permanece desativado para comunicação sistema-a-sistema com Bearer explícito. Alterar esse modelo para autenticação de navegador ou credenciais anexadas automaticamente exige revisar e habilitar proteção CSRF adequada. O Swagger local não autentica e permanece em loopback.

A cota padrão é de 300 chamadas por consumidor em uma janela de um minuto, compartilhada atomicamente entre instâncias no PostgreSQL. A janela começa na primeira chamada do consumidor; não é deslizante nem alinhada ao relógio. Chamadas autorizadas à API, inclusive `400` e `404`, consomem cota. Saúde e métricas não consomem. Uma rajada na fronteira entre janelas pode alcançar duas vezes o limite. Contadores vencidos são removidos periodicamente em lotes limitados.

O corpo tem limite padrão de 16 KiB. `Content-Length` acima do limite é rejeitado antes da autenticação e da cota; corpos de tamanho desconhecido são limitados durante a leitura, após a verificação da cota.

Segredos devem vir da plataforma de execução. `PROVIDER_TOKEN` não deve ser versionado ou registrado em logs. Sua presença exige HTTPS na URL externa, com exceção dos hosts exatos de loopback para testes locais. A rotação deve permitir coexistência temporária, atualização da configuração e revogação da credencial anterior.

| Grupo | Configurações principais |
| --- | --- |
| Banco | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `DATABASE_POOL_SIZE` |
| Provedor | `PROVIDER_BASE_URL`, `PROVIDER_ACTIVATION_PATH`, `PROVIDER_CONNECT_TIMEOUT`, `PROVIDER_READ_TIMEOUT`, `PROVIDER_TOKEN`, `PROVIDER_IDEMPOTENCY_SUPPORTED` |
| Repetição e reserva | `RETRY_MAX_ATTEMPTS`, `RETRY_INITIAL_DELAY`, `RETRY_MAX_DELAY`, `RETRY_JITTER_FACTOR`, `WORKER_POLL_INTERVAL`, `WORKER_LEASE_DURATION` |
| Segurança | `SECURITY_MODE`, `JWT_ISSUER_URI`, `JWT_AUDIENCE` |
| Limites | `REQUEST_MAX_BODY_BYTES`, `RATE_LIMIT_MAX_REQUESTS`, `RATE_LIMIT_WINDOW` |
| Documentação | `API_DOCS_ENABLED`, `DOCS_POLL_INTERVAL_MS`, `DOCS_TIMEOUT_MS`, `DOCS_REQUEST_TIMEOUT_MS` |
| Telemetria | `METRICS_REFRESH_INTERVAL`, `OTLP_TRACING_ENABLED`, `OTLP_TRACING_ENDPOINT`, `TRACING_SAMPLE_PROBABILITY`, `LOG_FORMAT` |

A relação completa e os valores padrão ficam em `src/main/resources/application.yml`. O Compose define algumas substituições próprias para a demonstração.

## Referências do projeto

- [Configuração de segurança](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/security/SecurityConfiguration.java).
- [Variáveis da aplicação](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/resources/application.yml).

[Contrato da API](?lang=pt&menu=pt&id=104) · [Execução local](?lang=pt&menu=pt&id=103) · [Operação](?lang=pt&menu=pt&id=108)
