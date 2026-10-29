# Executar localmente

São necessários Git, Docker Engine/Desktop com contêineres Linux e Docker Compose v2. O ambiente completo não exige Java instalado no host. O primeiro build baixa as dependências e as imagens.

Windows PowerShell, a partir da raiz do repositório:

```powershell
.\scripts\setup-local.ps1
docker compose up --build -d --wait
docker compose ps
```

Linux/macOS:

```sh
sh scripts/setup-local.sh
docker compose up --build -d --wait
docker compose ps
```

O script gera uma senha aleatória do banco em `.env`, preservando um arquivo existente. O arquivo é ignorado pelo Git. As portas podem ser ajustadas conforme `.env.example`. Alterar a senha no arquivo não modifica a credencial de um volume PostgreSQL já inicializado.

| Recurso | Endereço padrão na máquina local |
| --- | --- |
| API | `http://localhost:8080` |
| Swagger | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI | `http://localhost:8080/v3/api-docs` |
| Readiness | `http://localhost:8080/actuator/health/readiness` |
| Liveness | `http://localhost:8080/actuator/health/liveness` |
| WireMock | `http://localhost:8089/__admin/mappings` |
| PostgreSQL | `localhost:5432`, banco e usuário `activation` |

O Compose usa explicitamente `SECURITY_MODE=local`. A demonstração não autentica e publica portas somente em `127.0.0.1`. Ambientes compartilhados devem usar JWT, TLS e controles de rede.

## Experimentar sem preencher campos

No Swagger, selecione **Exemplo automático**. A seleção inicia o cenário imediatamente. O painel prepara os dados, gera identificadores, executa pré-requisitos e acompanha o resultado. **Executar novamente** utiliza uma nova solicitação; **Cancelar** interrompe as chamadas locais, sem desfazer trabalho já gravado.

Os 16 cenários cobrem sucesso, repetição, conflito, cinco falhas simuladas do provedor, validação, campo desconhecido, consulta existente, consulta inexistente, UUID inválido, listagem, corpo excedido e limite de chamadas. As operações nativas do Swagger continuam disponíveis para uso manual.

A coleção completa também pode ser executada automaticamente:

```sh
docker compose --profile test run --rm newman
```

O runner temporário verifica os contratos, acompanha estados terminais, testa 12 criações simultâneas e executa uma rajada limitada para observar `429`. A próxima execução respeita `Retry-After` quando a cota anterior ainda estiver esgotada. O profile `test` não faz parte da inicialização normal da aplicação.

Para o Postman, importe `postman/product-activation.postman_collection.json` e `postman/local.postman_environment.json`, selecione o ambiente e execute a coleção completa. Em modo local, `bearerToken` fica vazio. Um ambiente JWT requer token real; não exporte nem versione essa credencial.

## Comandos operacionais

```sh
docker compose logs -f app
docker compose stop
docker compose up -d --wait
docker compose down
```

`down` preserva o volume do banco. Remover os volumes apaga as solicitações persistidas e deve ser uma decisão explícita de reinicialização do ambiente.

## Referências do projeto

- [Docker Compose](https://github.com/Vidigal-code/product-activation-platform/blob/main/docker-compose.yml).
- [Configuração local PowerShell](https://github.com/Vidigal-code/product-activation-platform/blob/main/scripts/setup-local.ps1).
- [Configuração local POSIX](https://github.com/Vidigal-code/product-activation-platform/blob/main/scripts/setup-local.sh).
- [Coleção Postman](https://github.com/Vidigal-code/product-activation-platform/blob/main/postman/product-activation.postman_collection.json).

[Contrato da API](?lang=pt&menu=pt&id=104) · [Testes](?lang=pt&menu=pt&id=107) · [Segurança](?lang=pt&menu=pt&id=106)
