# ADR 004 — Resource Server and API guardrails

## 🇺🇸 English Description

<details>
<summary><strong>View Details</strong></summary>

### Status and context

Accepted. The API is consumed by other systems and must distinguish authorization from ownership. Invalid traffic and excessive request bodies must not consume unlimited resources. The security requirement permits implementation or design documentation; this service implements JWT authorization, consumer isolation and shared request limits.

### Decision

Use Spring Security as a stateless OAuth 2.0 Resource Server. Clients obtain tokens from an external identity provider through Client Credentials. Validate signature, issuer, audience and temporal claims using discovery/JWKS; require expiration and a usable subject. Discovery is lazy, so application startup does not require immediate issuer availability, although authentication still depends on valid key material.

Creation requires `requests:write`, lookup/listing require `requests:read`, and Prometheus requires `monitoring:read`. Health probes expose no internal details. Other routes are denied in JWT mode. Ownership follows the validated subject, as specified in [ADR 002](002-idempotency-and-consumer-ownership.md).

Authentication uses explicit `Authorization: Bearer`. Cookies and Basic do not authenticate, sessions do not retain authentication, and CSRF is disabled for this system-to-system model. Statelessness alone is not a CSRF defense. Introducing browser authentication or automatically attached credentials requires revisiting the model and enabling appropriate CSRF protection.

Local mode is an explicit demonstration setting. The provided Compose binds published ports to loopback and enables Swagger locally. The application does not replace network controls merely because `local` mode is selected. Shared environments must use JWT, TLS and network restrictions.

Use an atomic PostgreSQL upsert for a per-consumer fixed quota window starting with the consumer's first request. This shares quotas across replicas without a separate cache. Authorized API calls, including validation errors and missing records, consume quota; health and metrics do not. Exhaustion returns `429` and `Retry-After`. Expired windows are deleted in bounded batches.

Bound request bodies and reject excess with `413`. Known oversized `Content-Length` is checked before authentication/quota; unknown-length bodies are bounded while read, after the quota check. ProblemDetail standardizes validation, authentication, authorization and application errors without stack traces or internal exception messages.

### Consequences and alternatives

JWT avoids implementing a token issuer, but production depends on an external identity provider, correct scopes and key rotation. Provider credentials come from deployment secrets; authenticated remote HTTP is rejected unless HTTPS is used, with exact loopback exceptions for local tests. Rotation requires updating injected configuration and revoking the previous credential after overlap.

Shared quotas add database work and make the limiter depend on PostgreSQL. A fixed window is simple but permits bursts near a boundary of up to twice the allowance. A sliding-window algorithm or gateway enforcement could be evaluated if traffic demands tighter smoothing. In-memory limits would diverge across replicas; adding a separate distributed store is not justified by current requirements.

Body limits complement, rather than replace, connection limits and edge controls. Configurable quotas and sizes require capacity planning; the defaults are not universal production recommendations.

### Evidence

- [Security filter chains](../../src/main/java/com/vidigal/activation/security/SecurityConfiguration.java).
- [JWT decoder](../../src/main/java/com/vidigal/activation/security/JwtDecoderConfiguration.java).
- [Shared quotas](../../src/main/java/com/vidigal/activation/security/ConsumerRateLimiter.java).
- [Centralized API errors](../../src/main/java/com/vidigal/activation/web/ApiExceptionHandler.java).
- [Local network bindings](../../docker-compose.yml).

</details>

## 🇧🇷 Descrição em Português

<details>
<summary><strong>Ver Detalhes</strong></summary>

### Estado e contexto

Aceita. A API é consumida por outros sistemas e precisa distinguir autorização de posse. Tráfego inválido e corpos excessivos não podem consumir recursos ilimitados. O requisito de segurança admite implementação ou descrição; o serviço implementa autorização JWT, isolamento e limites compartilhados.

### Decisão

Utilizar Spring Security como OAuth 2.0 Resource Server stateless. Os consumidores obtêm tokens em um provedor de identidade externo pelo fluxo Client Credentials. Validar assinatura, emissor, audiência e validade temporal por descoberta/JWKS; exigir expiração e subject utilizável. A descoberta é tardia, portanto a inicialização não depende da disponibilidade imediata do emissor, embora autenticar continue dependendo de chaves válidas.

Criação exige `requests:write`, consulta/listagem exigem `requests:read`, e Prometheus exige `monitoring:read`. Probes de saúde não expõem detalhes internos. Outras rotas são negadas no modo JWT. A posse segue o subject validado, conforme a [ADR 002](002-idempotency-and-consumer-ownership.md).

A autenticação usa `Authorization: Bearer` explícito. Cookies e Basic não autenticam, sessões não mantêm autenticação e CSRF fica desativado nesse modelo sistema-a-sistema. Ser stateless, isoladamente, não protege contra CSRF. Introduzir autenticação de navegador ou credenciais anexadas automaticamente exige revisar o modelo e habilitar proteção CSRF adequada.

O modo local é uma configuração explícita de demonstração. O Compose fornecido publica portas em loopback e habilita Swagger localmente. Selecionar `local` não faz a aplicação substituir controles de rede. Ambientes compartilhados devem usar JWT, TLS e restrições de rede.

Utilizar upsert atômico no PostgreSQL para cota por consumidor em janela fixa iniciada na primeira chamada daquele consumidor. Isso compartilha limites entre réplicas sem cache adicional. Chamadas autorizadas à API, inclusive erros de validação e registros ausentes, consomem cota; saúde e métricas não consomem. O esgotamento retorna `429` e `Retry-After`. Janelas vencidas são excluídas em lotes limitados.

Limitar o corpo e rejeitar excesso com `413`. `Content-Length` conhecido acima do limite é verificado antes da autenticação/cota; corpos sem tamanho conhecido são limitados durante a leitura, após a cota. ProblemDetail padroniza erros de validação, autenticação, autorização e aplicação sem stack traces ou mensagens internas de exceções.

### Consequências e alternativas

JWT evita implementar um emissor de tokens, mas a produção depende de provedor de identidade externo, escopos corretos e rotação de chaves. Credenciais do provedor vêm de segredos do deploy; HTTP remoto autenticado é rejeitado sem HTTPS, com exceções exatas de loopback para testes. A rotação exige atualizar a configuração injetada e revogar a credencial anterior após coexistência.

Cotas compartilhadas acrescentam trabalho ao banco e fazem o limitador depender do PostgreSQL. A janela fixa é simples, mas permite rajadas na fronteira de até duas vezes a cota. Janela deslizante ou controle no gateway podem ser avaliados se o tráfego exigir distribuição mais uniforme. Limites em memória divergiriam entre réplicas; um armazenamento distribuído adicional não se justifica pelos requisitos atuais.

Limites de corpo complementam limites de conexão e controles de borda. Cotas e tamanhos configuráveis exigem planejamento de capacidade; os padrões não são recomendações universais de produção.

### Evidências

- [Cadeias de segurança](../../src/main/java/com/vidigal/activation/security/SecurityConfiguration.java).
- [Decodificador JWT](../../src/main/java/com/vidigal/activation/security/JwtDecoderConfiguration.java).
- [Cotas compartilhadas](../../src/main/java/com/vidigal/activation/security/ConsumerRateLimiter.java).
- [Erros centralizados da API](../../src/main/java/com/vidigal/activation/web/ApiExceptionHandler.java).
- [Publicação local de portas](../../docker-compose.yml).

</details>
