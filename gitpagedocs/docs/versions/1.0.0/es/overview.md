# Descripción general

Product Activation Platform recibe solicitudes de activación de productos, conserva su estado y llama a un proveedor externo. La API confirma el registro antes de iniciar la llamada remota; el consumidor consulta el resultado posteriormente.

La aplicación utiliza Java 21, Spring Boot 3.5.16, Spring MVC, Spring Data JPA/Hibernate, PostgreSQL 17, Flyway, Bean Validation y Maven Wrapper. PostgreSQL también coordina el trabajo pendiente. El entorno local incluye la aplicación, la base de datos y un proveedor WireMock mediante Docker Compose.

El flujo principal es:

1. Validar y registrar la solicitud como `PENDING`.
2. Reservar el trabajo en la base de datos y confirmar la transacción.
3. Llamar al proveedor sin mantener abierta una transacción de base de datos.
4. Guardar el resultado únicamente si la reserva sigue perteneciendo al worker y no ha vencido.
5. Exponer el estado actual mediante consulta.

Este sitio estático documenta el servicio. Para utilizar la API y Swagger, inicie el entorno local. El sitio de documentación no aloja el backend.

## Referencias del proyecto

- [Dependencias y versiones](https://github.com/Vidigal-code/product-activation-platform/blob/main/pom.xml).
- [Configuración de la aplicación](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/resources/application.yml).

[Requisitos y entrega](?lang=es&menu=es&id=102) · [Ejecución local](?lang=es&menu=es&id=103) · [Arquitectura](?lang=es&menu=es&id=105)

## Navegación de la documentación con GitPageDocs 0.0.30

Esta guía describe el runtime GitPageDocs **0.0.30**. La versión documental de este proyecto sigue siendo **1.0.0**. Actualizar texto y configuración no actualiza por sí solo el runtime publicado por el workflow.

### Índice por etiquetas

En `gitpagedocs/docs/versions/1.0.0/config.json`, cada elemento de `routes-md` utiliza `RouteguideBrand: true` y `RouteGuideTags: ["h1", "h2", "h3"]`. El índice incluye los tres primeros niveles de encabezados; los demás permanecen en el documento. Los nombres de propiedades distinguen mayúsculas y minúsculas.

```json
{ "RouteguideBrand": true, "RouteGuideTags": ["h1", "h2", "h3"] }
```

Omitir `RouteGuideTags` conserva el comportamiento anterior, de `h1` a `h6`. Una lista vacía (`[]`) elimina las entradas automáticas por etiqueta; los marcadores explícitos configurados en `markdownMarkers.items` todavía pueden formar parte del índice.

Para incluir otros elementos, añada nombres de etiquetas permitidas por el sanitizador, como `"p"`, `"blockquote"`, `"pre"`, `"table"`, `"img"`, `"details"` o `"summary"`. No hay comodines: `"*"`, las etiquetas peligrosas y las entradas inválidas se ignoran con un diagnóstico. Incluir `p`, `li`, `div` o `span` puede generar muchas entradas; seleccione solo las etiquetas útiles para la lectura.

Los rótulos usan título, texto alternativo o texto del elemento. Los elementos vacíos como `br`, `hr`, `img` e `input` pueden usar el nombre de la etiqueta. Los IDs automáticos son estables, únicos y separados por bloque. Utilice `markdownMarkers.items` para elegir una ocurrencia, definir un rótulo propio o aplicar un resaltado específico.

### Desplazamiento, pantallas pequeñas y plegables

Con el desplazamiento del índice desbloqueado, la entrada activa queda centrada, incluso al navegar con Anterior/Siguiente. El candado conserva la posición elegida manualmente en el índice. Esto permite consultar otra parte del índice sin que el seguimiento automático desplace la lista.

Al validar en un móvil, revise el menú contraído, las áreas táctiles, el foco del teclado, las tablas y los bloques de código con desplazamiento horizontal. En dispositivos plegables, repita la navegación con la pantalla plegada y abierta, en ambas orientaciones, y compruebe que la entrada activa siga accesible tras cambiar el ancho. Utilice el control de pausa cuando haya efectos animados y respete la preferencia de movimiento reducido.

## Mantenimiento de la documentación y MCP local

El contenido funcional anterior describe este proyecto. La configuración del visor corresponde a GitPageDocs **0.0.30**; no cambia la versión de la aplicación ni el directorio documental `1.0.0`.

Con `@gitpagedocs/cli` 0.0.30 instalado, ejecute desde la raíz de este repositorio:

```sh
gitpagedocs mcp start
```

El cliente MCP inicia un proceso stdio; no hay puerto HTTP ni opción `--cwd`. Configure el directorio de trabajo del proceso como la raíz del proyecto. `list_files`, `read_file` y los recursos `project://readme` y `project://structure` permiten inspeccionar contenido sin proveedor externo. `write_file` escribe el contenido completo del archivo indicado, después de revisarlo.

`update_documentation` llama a un proveedor y solo sustituye la región entre `gitpagedocs:start` y `gitpagedocs:end`; sin esos marcadores, añade una región. No utilice ese flujo para sustituir automáticamente estas páginas completas. `validate_docs` es una revisión por proveedor, no una verificación determinística de todos los enlaces. El comando `gitpagedocs docs` actualiza regiones de README/CONTRIBUTING/SECURITY con contenido estándar de GitPageDocs; no actualiza estas páginas específicas.

Antes de publicar, valide el JSON, las rutas de todos los idiomas, el índice y los enlaces en el visor. La publicación utiliza el workflow del repositorio; el pin del runtime debe actualizarse por separado a una release disponible. Conserve los menús, temas, iconos, rutas públicas y la versión documental existentes.

El workflow `.github/workflows/gitpagedocs-pages.yml` publica la documentación en `/docs` desde `main`, con runtime fijado por commit y redirección en la raíz. Conserve esa ruta y los pins de las acciones al actualizar el runtime; los cambios en otra rama no se publican automáticamente.

GitPageDocs 0.0.30 utiliza `performance.mode: "auto"` para los efectos de los temas: reduce moderadamente la densidad en escritorio y utiliza un presupuesto menor por debajo de 768px, conservando las capas del tema. Las partículas ocultas se pausan y retoman la misma fase al cambiar el ancho. Para mantener la densidad completa, combine `site.backgroundEffects.overrides.performance: { "mode": "full" }` con `gitpagedocs/config.json`; la misma opción está disponible en `site.backgroundEffects.themes[themeId].performance`. Un override explícito de `layers` conserva la cantidad completa cuando no se define mode en los overrides. Seleccione `"auto"` explícitamente para aplicar el presupuesto también a las capas personalizadas.

## Familia de tema fija y modo claro/oscuro

`HideThemeSelector: true` oculta el selector completo de temas y mantiene la familia elegida por `ThemeDefault`. La alternancia claro/oscuro sigue disponible cuando esa familia tiene un par válido. Un tema diferente procedente de la URL o del almacenamiento del navegador no sustituye la familia fija.

`ThemeModeDefault` es el valor inicial de respaldo. Un modo válido en `modetheme=light` o `modetheme=dark` de la URL, un modo guardado o el botón del lector puede seleccionar claro/oscuro dentro de esa familia. El modo elegido se conserva al recargar y navegar entre documentos. En pantallas estrechas, abra Menu para acceder al control estándar. Sin un par válido, el control no aparece.

Las dos entradas del catálogo de layouts deben habilitar `supportsLightAndDarkModes`, compartir el mismo `supportsLightAndDarkModesReference` no vacío y declarar valores opuestos de `mode` (`light` y `dark`). Este proyecto utiliza el catálogo oficial con pares; conserve su familia y preferencias al actualizar los pins del runtime o de los layouts.

## Altura de la guía lateral de escritorio

En pantallas de escritorio con más de 1024 píxeles CSS, la guía lateral termina después de su última entrada, sin extenderse hasta el final del artículo. Desde 0.0.25, puede crecer hasta la altura disponible de la ventana debajo de la cabecera antes de necesitar desplazamiento interno. Este diseño lateral tiene prioridad sobre `TocScrollMaxHeightDesktop`; la guía centrada encima del contenido y los diseños de hasta 1024 píxeles mantienen sus reglas de tamaño anteriores. Una pequeña barra con el color de la entrada activa sigue la sección marcada al desplazarse. El indicador utiliza el seguimiento de secciones existente, sin animación continua. En GitPageDocs 0.0.26, las guías laterales ganan 0.5rem adicionales de espacio debajo de la cabecera (8px con la fuente raíz predeterminada) y conservan 1rem debajo de la tarjeta en el límite de la ventana. Este ajuste se aplica solo por encima de 1024 píxeles CSS; los diseños menores se mantienen.

## Controles de lectura en GitPageDocs 0.0.29

Desde 0.0.27, Código fuente limita su área de contenido a la altura disponible de la ventana y desplaza internamente las listas largas de archivos y las vistas previas, cuando esta vista está habilitada. Desde 0.0.28, la guía del documento reserva 1.5rem (24 píxeles CSS con la fuente raíz predeterminada) entre las entradas y la barra de desplazamiento. Chromium/WebKit usan una barra de 4px cuando existe soporte; otros navegadores conservan la barra fina nativa. La pista transparente y el indicador translúcido siguen el tema, y el modo de alto contraste mantiene los controles nativos.

En 0.0.29, Anterior, Siguiente y Volver arriba quedan dentro de la tarjeta Markdown, después del contenido, con 8px de espacio en el flujo normal para no cubrir texto, tablas ni código. Los botones con iconos siguen el aspecto compacto de copiar, descargar y pantalla completa, con 36px en escritorio y controles táctiles preservados en móvil. Configura `site.documentNavigation.position` como `left`, `center` o `right`; el valor predeterminado es `center`. Los botones de los límites siguen deshabilitados, y `site.backToTop.enabled` y `minPageHeightViewports` todavía controlan la aparición de Volver arriba.

El icono predeterminado del modo de enfoque es `LuScanEye`, distinto del icono de pantalla completa. Las opciones explícitas de icono o texto siguen disponibles en `site.icons.FocusMode`. Se conservan el foco por teclado, la preferencia de movimiento reducido, las traducciones y las reglas de acceso al contenido. Al cambiar la configuración, comprueba las tres alineaciones, el último párrafo y los límites de navegación en escritorio y móvil.

## Archivos de código fuente y navegación del árbol en GitPageDocs 0.0.30

Desde 0.0.30, Código fuente reconoce archivos UTF-16LE y UTF-16BE por su marca de orden de bytes (BOM). El visor elimina la marca inicial y conserva acentos, texto de alfabetos no latinos y emoji. Los archivos sin BOM de UTF-16 siguen decodificándose como UTF-8, incluidos los archivos UTF-8 con BOM. Esto corrige la visualización de archivos como un `.gitignore` en UTF-16 sin modificar el archivo del repositorio.

El árbol de archivos permite desplazarse horizontal y verticalmente y muestra los nombres completos de archivos y carpetas. Al hacer clic en la fila de una carpeta se expanden o contraen sus elementos sin sustituir el archivo abierto ni cambiar la dirección actual. La lista central, las rutas de navegación y los resultados filtrados de carpetas siguen permitiendo navegar por los directorios. Comprueba nombres largos y carpetas con varios niveles en ambas direcciones de desplazamiento; abrir y cerrar una carpeta repetidamente debe conservar el archivo seleccionado y la dirección.

El visor no intenta adivinar la codificación de archivos UTF-16 sin BOM ni añade rutas de Código fuente automáticamente. Donde esté configurada esta vista, abre un `.gitignore` UTF-16 válido desde la lista de directorios y comprueba que `node_modules/` sea legible. Comprueba también nombres o contenidos de archivo con acentos, emoji y la vuelta al directorio en escritorio y móvil. La versión documental sigue siendo `1.0.0`.
