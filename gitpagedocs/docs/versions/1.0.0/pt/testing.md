# Testes e integração contínua

Os testes separam políticas e regras de domínio das integrações e dos fluxos completos.

| Camada | Escopo | Local |
| --- | --- | --- |
| Unitária Java | Políticas, configurações, estados, correlação, erros e dependências arquiteturais | `src/test/unit/java` |
| Integração Java | HTTP/JWT, API, banco real, concorrência, transações e reserva | `src/test/integration/java` |
| End-to-end Java | Aplicação HTTP em execução com provedor controlado | `src/test/e2e/java` |
| Unitária JavaScript | Executor dos cenários, validações, limites, timeout e cancelamento | `src/test/unit/javascript` |
| Navegador | Seleção dos 16 cenários, repetição, cancelamento e recuperação de quota | `src/test/e2e/javascript` |
| Coleção HTTP | Contratos da API, provedor, concorrência, saúde, métricas e limites | `postman` |

Para Java, instale JDK 21 e mantenha Docker disponível:

```sh
./mvnw test
./mvnw -Dit.test='*IT' verify
./mvnw -Dit.test='*E2E' verify
./mvnw verify
```

Os comandos executam, respectivamente, unitários; unitários e integração; unitários e end-to-end; todas as camadas Java. No Windows, substitua `./mvnw` por `.\mvnw.cmd`. Testcontainers usa PostgreSQL descartável, independente do banco do Compose. Os testes que precisam de Docker não são ignorados silenciosamente quando ele está indisponível.

Para os cenários do Swagger, use Node.js 22 ou superior:

```sh
npm ci
npm run test:unit
npx playwright install chromium
docker compose up -d --wait
npm run test:e2e
```

Relatórios Java ficam em `target/surefire-reports`, `target/failsafe-reports` e `target/site/jacoco`. Capturas de navegador ficam em `target/swagger-e2e`. As dependências Node são de desenvolvimento e teste; não há compilação de frontend para a imagem de produção.

A CI executa em `main`, `dev` e pull requests: configura Java e Node, instala dependências, roda testes unitários JavaScript e Maven verify, valida Compose, constrói a imagem, executa testes de navegador e Newman e publica relatórios. As actions utilizam revisões fixadas e permissão mínima de leitura do repositório. A CI não realiza implantação em conta externa.

## Referências do projeto

- [Configuração Maven](https://github.com/Vidigal-code/product-activation-platform/blob/main/pom.xml).
- [Testes](https://github.com/Vidigal-code/product-activation-platform/tree/main/src/test).
- [Workflow de CI](https://github.com/Vidigal-code/product-activation-platform/blob/main/.github/workflows/ci.yml).
- [Comandos Node](https://github.com/Vidigal-code/product-activation-platform/blob/main/package.json).

[Execução local](?lang=pt&menu=pt&id=103) · [Requisitos](?lang=pt&menu=pt&id=102) · [Operação](?lang=pt&menu=pt&id=108)
