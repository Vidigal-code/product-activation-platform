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

## Navegação da documentação com GitPageDocs 0.0.30

Este guia descreve o runtime GitPageDocs **0.0.30**. A versão documental deste projeto continua **1.0.0**. Atualizar o texto e a configuração não atualiza, por si só, o runtime publicado pelo workflow.

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

O conteúdo funcional acima descreve este projeto. A configuração do visualizador acompanha o GitPageDocs **0.0.30**; ela não altera a versão da aplicação nem o diretório documental `1.0.0`.

Com `@gitpagedocs/cli` 0.0.30 instalado, execute na raiz deste repositório:

```sh
gitpagedocs mcp start
```

O cliente MCP inicia um processo stdio; não há porta HTTP nem opção `--cwd`. Configure o diretório de trabalho do processo como a raiz do projeto. `list_files`, `read_file` e os recursos `project://readme` e `project://structure` permitem inspecionar o conteúdo sem provedor externo. `write_file` grava o conteúdo completo do arquivo indicado, após revisão.

`update_documentation` chama um provedor e só substitui a região entre `gitpagedocs:start` e `gitpagedocs:end`; sem os marcadores, acrescenta uma região. Não use esse fluxo para substituir automaticamente estas páginas completas. `validate_docs` é uma revisão por provedor, não uma verificação determinística de todos os links. O comando `gitpagedocs docs` atualiza regiões de README/CONTRIBUTING/SECURITY com conteúdo padrão do GitPageDocs; não atualiza estas páginas específicas.

Antes de publicar, valide o JSON, os caminhos de todos os idiomas, o índice e os links no visualizador. A publicação usa o workflow do repositório; o pin do runtime precisa ser atualizado separadamente para uma release disponível. Preserve os menus, temas, ícones, caminhos públicos e a versão documental existentes.

O workflow `.github/workflows/gitpagedocs-pages.yml` publica a documentação em `/docs` a partir de `main`, com runtime fixado por commit e redirecionamento na raiz. Preserve esse caminho e os pins de ações ao atualizar o runtime; alterações em outra branch não publicam automaticamente.

O GitPageDocs 0.0.30 usa `performance.mode: "auto"` nos efeitos dos temas: reduz moderadamente a densidade no desktop e usa um orçamento menor abaixo de 768px, mantendo as camadas do tema. Partículas ocultas ficam pausadas e retomam a mesma fase ao mudar a largura. Para manter a densidade completa, mescle `site.backgroundEffects.overrides.performance: { "mode": "full" }` em `gitpagedocs/config.json`; a mesma opção pode ser definida em `site.backgroundEffects.themes[themeId].performance`. Um override explícito de `layers` mantém a contagem completa quando não há mode definido nos overrides. Use `"auto"` explicitamente para aplicar o orçamento também às camadas personalizadas.

## Família de tema fixa e modo claro/escuro

`HideThemeSelector: true` oculta o seletor completo de temas e mantém a família escolhida por `ThemeDefault`. A alternância claro/escuro continua disponível quando essa família possui um par válido. Um tema diferente vindo da URL ou do armazenamento do navegador não substitui a família fixa.

`ThemeModeDefault` é o fallback inicial. Um modo válido em `modetheme=light` ou `modetheme=dark` na URL, um modo salvo ou o botão do leitor pode selecionar claro/escuro dentro dessa família. O modo escolhido permanece após recarregar e navegar entre documentos. Em telas estreitas, abra Menu para acessar o controle padrão. Sem um par válido, o controle não aparece.

As duas entradas do catálogo de layouts devem habilitar `supportsLightAndDarkModes`, compartilhar o mesmo `supportsLightAndDarkModesReference` não vazio e declarar valores opostos de `mode` (`light` e `dark`). Este projeto usa o catálogo oficial com pares; preserve sua família e preferências ao atualizar os pins do runtime ou dos layouts.

## Altura do guia lateral desktop

Em telas desktop com mais de 1024 pixels CSS, o guia lateral termina após o último item, sem se estender até o fim do artigo. Desde a 0.0.25, ele pode crescer até a altura disponível da janela abaixo do cabeçalho antes de precisar de rolagem interna. Esse layout lateral tem prioridade sobre `TocScrollMaxHeightDesktop`; o guia centralizado acima do conteúdo e os layouts de até 1024 pixels mantêm suas regras anteriores de dimensionamento. Uma pequena barra na cor do item ativo acompanha a seção marcada durante a rolagem. O indicador usa o acompanhamento de seções existente, sem animação contínua. No GitPageDocs 0.0.26, os guias laterais ganham mais 0.5rem de espaço abaixo do cabeçalho (8px com a fonte raiz padrão) e mantêm 1rem abaixo do cartão no limite da viewport. Esse ajuste vale somente acima de 1024 pixels CSS; os layouts menores são preservados.

## Controles de leitura no GitPageDocs 0.0.29

Desde a 0.0.27, Código fonte limita sua área de conteúdo à altura disponível da janela e mantém listas longas de arquivos e prévias com rolagem interna, quando essa visualização está habilitada. Desde a 0.0.28, o guia do documento reserva 1.5rem (24 pixels CSS com a fonte raiz padrão) entre os itens e a barra de rolagem. Chromium/WebKit usam uma barra de 4px onde há suporte; outros navegadores mantêm a barra fina nativa. O trilho transparente e o indicador translúcido acompanham o tema, e o modo de alto contraste preserva os controles nativos.

Na 0.0.29, Anterior, Próximo e Voltar ao topo ficam dentro do cartão Markdown, depois do conteúdo, com 8px de espaço no fluxo normal para não cobrir textos, tabelas ou código. Os botões com ícones seguem o visual compacto de copiar, baixar e tela cheia, com 36px no desktop e controles de toque preservados no celular. Configure `site.documentNavigation.position` como `left`, `center` ou `right`; o padrão é `center`. Os botões dos limites continuam desabilitados, e `site.backToTop.enabled` e `minPageHeightViewports` ainda controlam a exibição de Voltar ao topo.

O ícone padrão do modo foco é `LuScanEye`, diferente do ícone de tela cheia. Configurações explícitas de ícone ou texto continuam disponíveis em `site.icons.FocusMode`. Foco por teclado, preferência de movimento reduzido, traduções e regras de acesso ao conteúdo são preservados. Ao alterar a configuração, confira os três alinhamentos, o último parágrafo e os limites da navegação no desktop e no celular.

## Arquivos de código fonte e navegação da árvore no GitPageDocs 0.0.30

Desde a 0.0.30, Código fonte reconhece arquivos UTF-16LE e UTF-16BE pela marca de ordem de bytes (BOM). O visualizador remove a marca inicial e preserva acentos, texto de alfabetos não latinos e emoji. Arquivos sem BOM de UTF-16 continuam sendo decodificados como UTF-8, incluindo arquivos UTF-8 com BOM. Isso corrige a exibição de arquivos como um `.gitignore` em UTF-16 sem alterar o arquivo do repositório.

A árvore de arquivos permite rolagem horizontal e vertical e exibe os nomes completos de arquivos e pastas. Clicar na linha de uma pasta expande ou recolhe seus itens sem trocar o arquivo aberto nem o endereço atual. A listagem central, os caminhos de navegação e os resultados de pastas filtrados continuam permitindo navegar pelos diretórios. Confira nomes longos e pastas com vários níveis nas duas direções de rolagem; abrir e fechar uma pasta repetidamente deve preservar o arquivo selecionado e o endereço.

O visualizador não tenta adivinhar a codificação de arquivos UTF-16 sem BOM nem adiciona rotas de Código fonte automaticamente. Onde essa visualização estiver configurada, abra um `.gitignore` UTF-16 válido pela lista de diretórios e confira se `node_modules/` está legível. Confira também nomes ou conteúdos de arquivo com acentos, emoji e a volta ao diretório no desktop e no celular. A versão documental continua `1.0.0`.
