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

## Navegação da documentação com GitPageDocs 0.0.23

As opções desta seção requerem o runtime GitPageDocs **0.0.23**. A versão documental deste projeto continua **1.0.0**. Atualizar o texto e a configuração não atualiza, por si só, o runtime publicado pelo workflow.

### Índice por tags

Em `gitpagedocs/docs/versions/1.0.0/config.json`, cada item de `routes-md` usa `RouteguideBrand: true` e `RouteGuideTags: ["h1", "h2", "h3"]`. O índice mantém os três primeiros níveis de títulos; os demais títulos continuam no documento. A grafia das propriedades diferencia maiúsculas e minúsculas.

```json
{ "RouteguideBrand": true, "RouteGuideTags": ["h1", "h2", "h3"] }
```

Omitir `RouteGuideTags` preserva o comportamento legado, com `h1` até `h6`. Uma lista vazia (`[]`) remove as entradas automáticas por tag; marcadores explícitos configurados em `markdownMarkers.items` ainda podem compor o índice.

Para incluir outros elementos, acrescente nomes de tags permitidas pelo sanitizador, por exemplo `"p"`, `"blockquote"`, `"pre"`, `"table"`, `"img"`, `"details"` ou `"summary"`. Não existe curinga: `"*"`, tags perigosas e entradas inválidas são ignorados com diagnóstico. Incluir `p`, `li`, `div` ou `span` pode gerar muitas entradas; prefira somente as tags úteis para a leitura.

Os rótulos usam título, texto alternativo ou texto do elemento. Elementos vazios como `br`, `hr`, `img` e `input` podem usar o nome da tag. IDs automáticos são estáveis, únicos e separados por bloco. Use `markdownMarkers.items` quando precisar escolher uma ocorrência, definir rótulo próprio ou aplicar um destaque específico.

### Rolagem, telas pequenas e dobráveis

Com a rolagem do índice desbloqueada, o item ativo é centralizado, inclusive ao navegar por Anterior/Próximo. O cadeado preserva a posição escolhida manualmente no índice. Isso permite consultar outra parte do índice sem que o acompanhamento automático reposicione a lista.

Ao validar em celular, confira o menu recolhido, alvos de toque, foco por teclado, tabelas e blocos de código com rolagem horizontal. Em dispositivos dobráveis, repita a navegação com a tela dobrada e aberta, nas duas orientações, e confirme que o item ativo permanece acessível após a mudança de largura. Use o controle de pausa quando houver efeitos animados e respeite a preferência de movimento reduzido.

## Manutenção da documentação e MCP local

O conteúdo funcional acima descreve este projeto. A configuração do visualizador acompanha o GitPageDocs **0.0.23**; ela não altera a versão da aplicação nem o diretório documental `1.0.0`.

Com `@gitpagedocs/cli` 0.0.23 instalado, execute na raiz deste repositório:

```sh
gitpagedocs mcp start
```

O cliente MCP inicia um processo stdio; não há porta HTTP nem opção `--cwd`. Configure o diretório de trabalho do processo como a raiz do projeto. `list_files`, `read_file` e os recursos `project://readme` e `project://structure` permitem inspecionar o conteúdo sem provedor externo. `write_file` grava o conteúdo completo do arquivo indicado, após revisão.

`update_documentation` chama um provedor e só substitui a região entre `gitpagedocs:start` e `gitpagedocs:end`; sem os marcadores, acrescenta uma região. Não use esse fluxo para substituir automaticamente estas páginas completas. `validate_docs` é uma revisão por provedor, não uma verificação determinística de todos os links. O comando `gitpagedocs docs` atualiza regiões de README/CONTRIBUTING/SECURITY com conteúdo padrão do GitPageDocs; não atualiza estas páginas específicas.

Antes de publicar, valide o JSON, os caminhos de todos os idiomas, o índice e os links no visualizador. A publicação usa o workflow do repositório; o pin do runtime precisa ser atualizado separadamente para uma release disponível. Preserve os menus, temas, ícones, caminhos públicos e a versão documental existentes.

O workflow `.github/workflows/gitpagedocs-pages.yml` publica a documentação em `/docs` a partir de `main`, com runtime fixado por commit e redirecionamento na raiz. Preserve esse caminho e os pins de ações ao atualizar o runtime; alterações em outra branch não publicam automaticamente.

O GitPageDocs 0.0.23 usa `performance.mode: "auto"` nos efeitos dos temas: reduz moderadamente a densidade no desktop e usa um orçamento menor abaixo de 768px, mantendo as camadas do tema. Partículas ocultas ficam pausadas e retomam a mesma fase ao mudar a largura. Para manter a densidade completa, mescle `site.backgroundEffects.overrides.performance: { "mode": "full" }` em `gitpagedocs/config.json`; a mesma opção pode ser definida em `site.backgroundEffects.themes[themeId].performance`. Um override explícito de `layers` mantém a contagem completa quando não há mode definido nos overrides. Use `"auto"` explicitamente para aplicar o orçamento também às camadas personalizadas.
