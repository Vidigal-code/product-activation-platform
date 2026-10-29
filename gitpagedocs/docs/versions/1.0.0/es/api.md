# Contrato de la API

## Crear una solicitud

`POST /api/v1/requests` recibe JSON con `Content-Type: application/json`:

```json
{
  "externalId": "EXT-12345",
  "customerId": "CUST-10001",
  "productCode": "INTERNET-500"
}
```

Una creación devuelve `201 Created`, la cabecera `Location` y los campos `id`, `externalId`, `customerId`, `productCode`, `status`, `createdAt` y `updatedAt`. Las fechas utilizan UTC/ISO-8601. La respuesta también incluye `attemptCount` y, cuando corresponde, un `failureCode` seguro.

El `201` confirma el registro local. La activación se procesa de forma asíncrona y su resultado se consulta en la dirección devuelta.

| Situación | Respuesta |
| --- | --- |
| Solicitud nueva y válida | `201 Created` con `Location` |
| Mismo identificador y contenido del mismo consumidor | `200 OK`, mismo registro |
| Mismo identificador con contenido diferente | `409 Conflict` |
| Identificador de otro consumidor | `409 Conflict`, sin revelar su contenido |
| Cuerpo, campo o UUID inválido | `400 Bad Request` |
| Registro inexistente o perteneciente a otro consumidor | `404 Not Found` |
| Token ausente o inválido en modo JWT | `401 Unauthorized` |
| Ámbito de autorización insuficiente | `403 Forbidden` |
| Tipo de contenido incompatible | `415 Unsupported Media Type` |
| Cuerpo superior al límite | `413 Content Too Large` |
| Cuota de llamadas agotada | `429 Too Many Requests` con `Retry-After` |
| Fallo interno inesperado | `500` con ProblemDetail genérico |

## Validación

`externalId` y `customerId` admiten entre 1 y 100 caracteres; `productCode`, entre 1 y 80. El primer carácter debe ser una letra ASCII o un dígito. Los demás admiten letras ASCII, dígitos, punto, guion bajo, dos puntos y guion. Se rechazan los espacios sin eliminarlos silenciosamente. También se rechazan los campos JSON desconocidos y los documentos adicionales después del objeto JSON.

`X-Correlation-Id` admite entre 1 y 64 letras ASCII, dígitos, puntos, guiones bajos o guiones. Los valores ausentes o inválidos se sustituyen por un identificador generado.

## Duplicidad y concurrencia

`externalId` es globalmente único y distingue mayúsculas de minúsculas. La restricción `UNIQUE` de PostgreSQL constituye la garantía final; una consulta previa a la inserción no sería suficiente.

Si dos inserciones compiten, la transacción perdedora termina en rollback antes de consultar el registro ganador. El mismo contenido del mismo consumidor devuelve ese registro. Un contenido distinto o la pertenencia a otro consumidor producen un conflicto. Un fallo terminal no libera el identificador para una nueva activación.

## Consultar y listar

`GET /api/v1/requests/{id}` devuelve la solicitud y su estado actual. Un registro inexistente o perteneciente a otro consumidor produce `404`.

`GET /api/v1/requests?status=COMPLETED&customerId=CUST-10001&page=0&size=20` lista únicamente los registros del consumidor actual. Los filtros son opcionales. La respuesta contiene `items`, `page`, `size`, `totalElements`, `totalPages` y `hasNext`, con orden fijo `createdAt ASC, id ASC`.

La primera página es `0`, el tamaño predeterminado es 20, el máximo es 100 y la página máxima es 10000. Los límites son configurables. Un historial extenso puede requerir cursores y retención después de evaluar los planes de consulta.

## Índices del listado

La [migración V2](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/resources/db/migration/V2__isolate_consumers_and_index_request_queries.sql) crea estos índices:

| Índice | Columnas | Consulta prevista |
| --- | --- | --- |
| `ix_activation_consumer_created` | `(consumer_id, created_at, id)` | Solicitudes del consumidor sin filtro opcional |
| `ix_activation_consumer_status_created` | `(consumer_id, status, created_at, id)` | Solicitudes del consumidor filtradas por estado |
| `ix_activation_consumer_customer_created` | `(consumer_id, customer_id, created_at, id)` | Solicitudes del consumidor filtradas por cliente |

Los predicados de igualdad preceden a las columnas del orden estable. Los índices cubren los principales patrones de acceso sin indexar todas las combinaciones. Con ambos filtros opcionales presentes, PostgreSQL puede elegir una vía de acceso y evaluar el predicado restante. Confirme los planes reales con `EXPLAIN (ANALYZE, BUFFERS)` en un entorno controlado antes de añadir otro índice compuesto; los índices adicionales consumen espacio y aumentan el coste de escritura. La restricción independiente `UNIQUE(external_id)` protege la deduplicación, no la paginación.

## Referencias del proyecto

- [Controller de la API](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/request/api/ActivationRequestController.java).
- [Migraciones](https://github.com/Vidigal-code/product-activation-platform/tree/main/src/main/resources/db/migration).

[Procesamiento](?lang=es&menu=es&id=105) · [Seguridad](?lang=es&menu=es&id=106) · [Ejemplos locales](?lang=es&menu=es&id=103)
