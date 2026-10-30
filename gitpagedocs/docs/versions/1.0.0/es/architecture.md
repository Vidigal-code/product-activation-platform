# Arquitectura y procesamiento

## Trabajo persistente

La solicitud registrada también representa la unidad de trabajo. El worker reserva filas elegibles mediante los mecanismos de concurrencia de PostgreSQL, incluido `FOR UPDATE SKIP LOCKED`, y confirma la transacción antes de realizar la llamada HTTP.

Durante la inserción, Hibernate obtiene de PostgreSQL los valores iniciales de `createdAt`, `updatedAt` y del campo interno `nextAttemptAt`. Las reservas y los reintentos utilizan el mismo reloj de la base de datos. Esto evita retrasos artificiales en nuevas solicitudes cuando el reloj de la JVM está adelantado, sin migración de esquema ni cambios en identificadores, estados o contrato HTTP. Las fechas históricas no se corrigen; los cambios en el reloj del propio PostgreSQL todavía pueden afectar la programación y el vencimiento de las reservas.

La reserva tiene un vencimiento y un token de posesión. El resultado se guarda en otra transacción que exige el token vigente y una reserva sin vencer. Esto permite varias instancias e impide que un worker atrasado sobrescriba el resultado producido después de una nueva reserva.

| Origen | Destino permitido | Uso |
| --- | --- | --- |
| `PENDING` | `PROCESSING` | Reserva para un intento |
| `PENDING` | `FAILED` | Finalización por una condición operativa sin otra llamada |
| `PROCESSING` | `PENDING` | Reintento programado o aplazamiento por circuito abierto |
| `PROCESSING` | `COMPLETED` | Activación confirmada por el proveedor |
| `PROCESSING` | `FAILED` | Fallo definitivo, intentos agotados o resultado incierto sin repetición segura |
| `COMPLETED` / `FAILED` | Ninguno | Estados terminales |

No se garantiza una ejecución exactamente una vez en el sistema externo. Una cabecera `Idempotency-Key` estable solo impide efectos duplicados cuando el proveedor garantiza ese contrato.

## Política de resultados

| Resultado | Decisión |
| --- | --- |
| Éxito `2xx` | `COMPLETED`, sin repetición |
| `400` y otros errores permanentes de entrada | `FAILED`, sin reenviar el mismo contenido inválido |
| `500` y fallos transitorios | Intentos limitados con retraso creciente; `FAILED` al agotarlos |
| `429` / `503` | Considerar `Retry-After` dentro de la política de intentos limitados |
| Timeout o fallo de conexión ambiguo | Sin idempotencia confirmada, `FAILED` para reconciliación; con contrato confirmado, repetición limitada |
| Reserva vencida después de una interrupción | Recuperar dentro del límite de intentos únicamente cuando el contrato externo permita repetir de forma segura; en otro caso, requerir reconciliación |
| Circuito abierto | Devolver a `PENDING`, persistir el aplazamiento y restituir el intento que no llamó al proveedor |

Los valores predeterminados son tres intentos, un segundo de intervalo inicial, 30 segundos de máximo de backoff y una reserva de 30 segundos. El jitter reduce aleatoriamente el retraso al 80%–100% del valor calculado. Un `Retry-After` positivo válido prevalece incluso si supera el máximo de backoff. Un valor numérico imposible de programar produce `PROVIDER_RETRY_AFTER_UNSUPPORTED`, sin adelantar otra llamada.

El circuit breaker observa una ventana de 20 llamadas, un mínimo de 10 y un umbral de fallos del 50%. Se abre durante 10 segundos y permite dos llamadas de prueba en estado semiabierto. Los fallos transitorios e inciertos cuentan; los rechazos de negocio no. Cada instancia mantiene su propio estado del circuito.

El cliente utiliza timeouts configurables de conexión y lectura. Los valores predeterminados de la aplicación son dos y cinco segundos; en Compose, uno y dos segundos. La duración de la reserva debe superar los timeouts y el margen necesario para guardar el resultado.

Repetir un `500` presupone que el contrato externo permite reenviar después de esa respuesta. Si el proveedor puede completar la activación antes de devolver `500`, se necesita idempotencia confirmada o reconciliación antes de repetir. Un timeout tampoco demuestra que no hubo efecto remoto.

## Proveedor simulado

WireMock expone `POST /provider/v1/activate` y exige `Idempotency-Key`.

| `productCode` | Respuesta simulada |
| --- | --- |
| `INTERNET-500` u otro código común | `200` |
| `BUSINESS-ERROR` | `400` |
| `TEMPORARY-ERROR` | `500` |
| `TIMEOUT` | `200` después de cinco segundos |
| `RATE-LIMITED` | `429` con `Retry-After: 2` |
| `UNAVAILABLE` | `503` con `Retry-After: 2` |

El mock simula respuestas sin efectos reales ni deduplicación duradera. Por eso, Compose utiliza `PROVIDER_IDEMPOTENCY_SUPPORTED=false`. Habilitar esta opción en producción exige un contrato explícito sobre concurrencia, retención de la clave, igualdad del contenido y recuperación.

## Decisiones arquitectónicas

El código se organiza por funcionalidad y separa API, aplicación, dominio, persistencia e integración. Los DTO no exponen entidades JPA. La consulta y la creación utilizan JPA; la reserva utiliza SQL específico de PostgreSQL para preservar la concurrencia y la posesión del trabajo.

Reutilizar la solicitud persistida reduce las dependencias operativas y cubre la interrupción entre el commit y la llamada remota.

| Alternativa | Evaluación |
| --- | --- |
| Flujo síncrono | Más sencillo, pero expone al consumidor a la latencia externa y sigue necesitando una estrategia para el intervalo entre commit y HTTP |
| Tareas solo en memoria | Pierden trabajo después de una interrupción del proceso |
| Broker adicional | Puede servir a mayores volúmenes y más consumidores, pero amplía la infraestructura sin una necesidad actual demostrada |
| Trabajo persistente en PostgreSQL | Reutiliza la base de datos, permite reservas entre instancias y mantiene un control duradero de los intentos |

Esta elección requiere consultas periódicas y tiene una capacidad de procesamiento limitada. Un crecimiento sostenido exige evaluar concurrencia, índices, procesamiento por lotes y retención antes de añadir infraestructura.

## Referencias del proyecto

- [Reserva persistente](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/request/persistence/ActivationQueue.java).
- [Máquina de estados](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/request/domain/ActivationStatus.java).
- [Política de repetición](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/request/integration/RetryPolicy.java).
- [Decisiones completas](https://github.com/Vidigal-code/product-activation-platform/blob/main/docs/requirements.md).

[Contrato de la API](?lang=es&menu=es&id=104) · [Seguridad](?lang=es&menu=es&id=106) · [Operación y limitaciones](?lang=es&menu=es&id=108)

[Registros de decisiones de arquitectura (inglés y portugués)](https://github.com/Vidigal-code/product-activation-platform/blob/main/docs/adr/README.md).
