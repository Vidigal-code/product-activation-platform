# Operação e diagnóstico

## Saúde, logs e métricas

Liveness indica a saúde do processo. Readiness inclui conectividade com PostgreSQL. A indisponibilidade temporária do provedor aumenta a fila, mas não deve provocar reinício repetido de instâncias saudáveis.

Logs utilizam JSON ECS e identificadores de correlação. O contexto acompanha a solicitação e a chamada externa sem incluir tokens, credenciais ou conteúdo sensível.

| Medidor | Finalidade |
| --- | --- |
| `activation.provider.calls` | Duração das chamadas por resultado |
| `activation.worker.results` | Contagem dos resultados do processamento |
| `activation.requests` | Quantidade persistida por estado |
| `resilience4j.circuitbreaker.*` | Estado e comportamento do circuito |
| Métricas HTTP, JVM e datasource | Latência, erros, memória, execução e uso do pool |

Prometheus está disponível em `/actuator/prometheus`, protegido pelo escopo de monitoramento no modo JWT. Os totais por estado são atualizados por uma consulta agrupada a cada dez segundos. Uma falha de leitura preserva a amostra anterior.

Os totais incluem registros terminais ainda retidos e são globais: réplicas observam os mesmos registros. Para consolidá-los entre réplicas, utilize `max`, não `sum`. A idade da fila ainda precisa de consulta específica ao banco. Não use identificadores de clientes como labels.

## Tracing local opcional

```sh
docker compose -f docker-compose.yml -f docker-compose.observability.yml up --build -d --wait
```

O override inicia Jaeger e habilita exportação OTLP/HTTP com amostragem integral para a demonstração. A interface fica em `http://localhost:16686`. O contexto W3C é persistido, restaurado no span `activation.process` e propagado ao HTTP do provedor.

O Jaeger local guarda dados em memória transitória. Reiniciá-lo apaga os traces. Fora desse override, a exportação OTLP permanece desativada por padrão; um ambiente duradouro precisa de coletor protegido, retenção e amostragem adequadas.

## Cenário de lentidão

Considere API de 300 ms para 2,5 s, consultas de 50 ms para 1,2 s e provedor de 400 ms para 3 s, com CPU em 45% e memória em 60%.

1. **Investigação inicial:** correlacionar o início com releases, tráfego, crescimento dos dados e incidentes externos. Comparar percentis por endpoint e resultado, erros e idade da fila. CPU e memória moderadas não excluem espera ou contenção.
2. **Localização:** separar tempo de espera por thread/conexão, SQL, transação e HTTP externo. Comparar traces, locks e atividade do PostgreSQL. Usar `EXPLAIN (ANALYZE, BUFFERS)` em consultas representativas e seguras, lembrando que o comando executa a consulta.
3. **Evidências:** correlação, p50/p95/p99, timeouts, repetições, Hikari ativo/pendente, thread dumps, pausas de GC, consultas lentas, locks, I/O e trabalho pendente mais antigo.
4. **Melhorias possíveis:** corrigir acessos lentos com índices justificados, SQL eficiente e transações menores; limitar tempo e concorrência; dimensionar o pool pela capacidade medida do banco.
5. **Hipótese comum:** chamadas externas lentas podem manter conexões/transações ocupadas e ampliar espera. Confirmar com traces e `pg_stat_activity`. Esta implementação evita HTTP dentro da transação, mas rede compartilhada e excesso de repetições ainda podem afetar ambas as dependências.
6. **Validação da correção:** comparar percentis, erros, espera do banco, vazão e duração total da ativação sob carga equivalente. Fazer rollout gradual e manter alertas e rollback. Um POST rápido não demonstra, isoladamente, que o processamento da fila está saudável.

## Produção e múltiplas instâncias

A imagem executa JRE 21 com usuário sem privilégios, heap adequado a contêiner, desligamento gracioso e health check. Produção exige PostgreSQL com backups testados, TLS, JWT, provedor real, segredos injetados e endpoints de gestão privados. Migrações precisam de rollout controlado e privilégios adequados.

Múltiplas instâncias compartilham unicidade, reservas e cota no PostgreSQL. O balanceador distribui tráfego; cada worker reserva trabalho independentemente. Circuit breakers permanecem locais às instâncias.

Uma etapa futura de deploy publicaria a imagem validada em um registro, utilizaria digest imutável, aplicaria migrações compatíveis e acompanharia prontidão, erros e latência durante rollout gradual. A imagem anterior deve permanecer disponível para rollback. Essas etapas de implantação são orientações de operação, não um ambiente cloud já provisionado.

## Limitações e evolução

O provedor é simulado. Um contrato real precisa definir idempotência e consulta de status antes de habilitar recuperação automática de resultados incertos. O banco impede duplicidade local, mas não oferece execução exatamente uma vez no provedor.

Solicitações terminais não possuem endpoint público de reinício. A reconciliação deve consultar o provedor, registrar evidências e autorizar uma ação auditada; essa interface ainda não está implementada.

O modo JWT depende de um provedor de identidade externo. O modo local e o Swagger automático são destinados à demonstração em loopback, sem credenciais persistidas pelo painel.

Para emitir eventos de conclusão, a evolução proposta é gravar uma outbox na mesma transação do estado final e publicar separadamente. Ainda não há outbox, broker ou publicação de eventos implementados.

Retenção de solicitações, dashboards, alertas, coletor durável de telemetria e automação de implantação dependem do ambiente de operação. O Jaeger local é transitório. Histórico profundo pode exigir paginação por cursor. Nenhuma dessas propostas deve ser apresentada como recurso já entregue.

## Referências do projeto

- [Métricas persistidas](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/observability/RequestMetrics.java).
- [Override de observabilidade](https://github.com/Vidigal-code/product-activation-platform/blob/main/docker-compose.observability.yml).
- [Dockerfile](https://github.com/Vidigal-code/product-activation-platform/blob/main/Dockerfile).
- [Configuração da aplicação](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/resources/application.yml).

[Arquitetura](?lang=pt&menu=pt&id=105) · [Segurança](?lang=pt&menu=pt&id=106) · [Execução local](?lang=pt&menu=pt&id=103)
