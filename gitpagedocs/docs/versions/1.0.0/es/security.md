# Seguridad y configuración

El modo predeterminado es OAuth 2.0 Resource Server con JWT. El consumidor obtiene un token del proveedor de identidad mediante el flujo Client Credentials y lo envía en `Authorization: Bearer`. Este servicio no emite tokens.

Se verifican la firma, el emisor, la audiencia y la validez temporal. El descubrimiento y JWKS proporcionan las claves públicas y permiten su rotación. Configure `JWT_ISSUER_URI` y `JWT_AUDIENCE` para el entorno real.

| Operación | Ámbito de autorización |
| --- | --- |
| Crear una solicitud | `requests:write` |
| Consultar o listar | `requests:read` |
| Consultar Prometheus | `monitoring:read` |

El propietario es el `sub` validado del JWT, con un máximo de 255 caracteres y sin valores vacíos o compuestos solo por espacios. No se puede definir en el cuerpo de la solicitud. El modo local utiliza `local-demo`. Los registros anteriores a la migración de propiedad requieren una asignación auditada antes de operar con consumidores JWT.

Las consultas y los listados se limitan al consumidor autenticado. Consultar un registro ajeno devuelve `404`; intentar reutilizar su `externalId` devuelve un conflicto genérico `409`, sin revelar el contenido.

Las cookies y Basic no autentican. CSRF permanece desactivado para la comunicación entre sistemas con Bearer explícito. Cambiar este modelo a autenticación de navegador o credenciales adjuntadas automáticamente exige revisar y habilitar la protección CSRF adecuada. Swagger local no autentica y permanece restringido a loopback.

La cuota predeterminada es de 300 llamadas por consumidor en una ventana de un minuto, compartida atómicamente entre instancias mediante PostgreSQL. La ventana empieza con la primera llamada del consumidor; no es deslizante ni está alineada con el reloj. Las llamadas autorizadas a la API, incluidas las respuestas `400` y `404`, consumen cuota. Salud y métricas no la consumen. Una ráfaga en la frontera entre ventanas puede alcanzar el doble del límite. Los contadores vencidos se eliminan periódicamente en lotes limitados.

El cuerpo tiene un límite predeterminado de 16 KiB. Un `Content-Length` superior al límite se rechaza antes de la autenticación y de la cuota; los cuerpos de tamaño desconocido se limitan durante la lectura, después de verificar la cuota.

Los secretos deben proceder de la plataforma de ejecución. `PROVIDER_TOKEN` no debe versionarse ni registrarse en logs. Su presencia exige HTTPS en la URL externa, salvo para los hosts exactos de loopback usados en pruebas locales. La rotación debe permitir coexistencia temporal, actualización de la configuración y revocación de la credencial anterior.

| Grupo | Configuraciones principales |
| --- | --- |
| Base de datos | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `DATABASE_POOL_SIZE` |
| Proveedor | `PROVIDER_BASE_URL`, `PROVIDER_ACTIVATION_PATH`, `PROVIDER_CONNECT_TIMEOUT`, `PROVIDER_READ_TIMEOUT`, `PROVIDER_TOKEN`, `PROVIDER_IDEMPOTENCY_SUPPORTED` |
| Repetición y reserva | `RETRY_MAX_ATTEMPTS`, `RETRY_INITIAL_DELAY`, `RETRY_MAX_DELAY`, `RETRY_JITTER_FACTOR`, `WORKER_POLL_INTERVAL`, `WORKER_LEASE_DURATION` |
| Seguridad | `SECURITY_MODE`, `JWT_ISSUER_URI`, `JWT_AUDIENCE` |
| Límites | `REQUEST_MAX_BODY_BYTES`, `RATE_LIMIT_MAX_REQUESTS`, `RATE_LIMIT_WINDOW` |
| Documentación | `API_DOCS_ENABLED`, `DOCS_POLL_INTERVAL_MS`, `DOCS_TIMEOUT_MS`, `DOCS_REQUEST_TIMEOUT_MS` |
| Telemetría | `METRICS_REFRESH_INTERVAL`, `OTLP_TRACING_ENABLED`, `OTLP_TRACING_ENDPOINT`, `TRACING_SAMPLE_PROBABILITY`, `LOG_FORMAT` |

La lista completa y los valores predeterminados están en `src/main/resources/application.yml`. Compose sustituye algunos valores para la demostración.

## Referencias del proyecto

- [Configuración de seguridad](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/security/SecurityConfiguration.java).
- [Variables de la aplicación](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/resources/application.yml).

[Contrato de la API](?lang=es&menu=es&id=104) · [Ejecución local](?lang=es&menu=es&id=103) · [Operación](?lang=es&menu=es&id=108)
