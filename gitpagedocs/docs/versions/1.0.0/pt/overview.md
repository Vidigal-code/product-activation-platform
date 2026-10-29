# Visão geral

Product Activation Platform recebe solicitações de ativação de produtos, persiste seu estado e aciona um provedor externo. A API confirma o registro antes de iniciar a chamada remota; o consumidor acompanha o resultado por consulta.

A aplicação utiliza Java 21, Spring Boot 3.5.16, Spring MVC, Spring Data JPA/Hibernate, PostgreSQL 17, Flyway, Bean Validation e Maven Wrapper. O PostgreSQL também coordena o trabalho pendente. A execução local inclui aplicação, banco e provedor WireMock no Docker Compose.

O fluxo principal é:

1. Validar e registrar a solicitação como `PENDING`.
2. Reservar o trabalho no banco e confirmar a transação.
3. Chamar o provedor sem manter transação de banco aberta.
4. Gravar o resultado somente se a reserva ainda pertencer ao worker.
5. Disponibilizar o estado atual por consulta.

Esta documentação estática descreve o serviço. Para utilizar a API e o Swagger, inicie o ambiente local. Não há backend hospedado junto ao site de documentação.

## Referências do projeto

- [Dependências e versões](https://github.com/Vidigal-code/product-activation-platform/blob/main/pom.xml).
- [Configuração da aplicação](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/resources/application.yml).

[Requisitos e entrega](?lang=pt&menu=pt&id=102) · [Executar localmente](?lang=pt&menu=pt&id=103) · [Arquitetura](?lang=pt&menu=pt&id=105)
