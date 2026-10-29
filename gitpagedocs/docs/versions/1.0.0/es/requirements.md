# Requisitos y entrega

## Requisitos obligatorios satisfechos

| Requisito | Solución entregada |
| --- | --- |
| Java 17 o 21, Spring Boot, herramienta de construcción y base de datos relacional | Java 21, Spring Boot 3.5, Maven Wrapper y PostgreSQL 17 |
| Crear solicitudes con datos obligatorios válidos | `POST /api/v1/requests` con Bean Validation y DTO separado |
| Impedir duplicados, incluso ante solicitudes simultáneas | Restricción global `UNIQUE(external_id)` y tratamiento después del rollback |
| Persistir los campos exigidos y consultar por ID | Identificadores, cliente, producto, estado y fechas; `GET /api/v1/requests/{id}` |
| Representar los cuatro estados | `PENDING`, `PROCESSING`, `COMPLETED` y `FAILED` |
| Integrar con el proveedor y definir los resultados | Cliente HTTP con tratamiento de éxito, error de negocio, fallo temporal y timeout |
| Tratar entradas inválidas y errores de forma segura | Respuestas ProblemDetail sin stack traces ni detalles internos |
| Implementar o describir JWT/OAuth 2.0 | Resource Server implementado y flujo Client Credentials documentado |
| Probar creación, duplicidad, consulta e integración | Pruebas automatizadas mediante interfaces públicas y una base de datos real |
| Mantener organización y responsabilidades claras | Código organizado por funcionalidad, con API, aplicación, dominio, persistencia e integración |
| Responder al escenario de lentitud | Diagnóstico y criterios de validación descritos en Operación y diagnóstico |
| Proporcionar Dockerfile e instrucciones de producción | Imagen con construcción en varias etapas, ejecución sin privilegios y configuración externa |
| Documentar ejecución, decisiones y límites | README bilingüe, matriz en `docs/requirements.md` y registros en `docs/adr/` |

## Mejoras opcionales implementadas

| Grupo | Funcionalidades |
| --- | --- |
| Java moderno | DTO con `record` y contratos inmutables en las fronteras; Spring Boot 3.x y Java 21 |
| API | `201` con `Location`, repetición idéntica con `200`, conflicto con `409`, UTC/ISO-8601 y validación de formato y tamaño |
| Consulta | Paginación limitada, filtros por estado y cliente, índices por consumidor y orden estable |
| Procesamiento | Trabajo persistente, reserva con vencimiento, protección frente a workers atrasados y estados controlados por el dominio |
| Integración | Timeouts configurables, intentos limitados, backoff con jitter, `Retry-After` y circuit breaker |
| Seguridad | JWT con ámbitos de autorización, aislamiento por consumidor y límites de cuerpo y tasa compartidos mediante la base de datos |
| Persistencia | Migraciones Flyway, restricciones de integridad y pruebas concurrentes con PostgreSQL |
| Operación | Health checks, correlación, logs JSON, métricas por estado y trazas entre API, worker y proveedor |
| Calidad | Pruebas de dominio, contratos HTTP, autorización, concurrencia, arquitectura y navegación real |
| Ejecución | Compose, apagado ordenado, límites de recursos, CI e informes de pruebas |
| Demostración | Swagger con 16 escenarios automáticos y colección Postman ejecutable con Newman |
| Historial de entrega | Commits pequeños separan construcción, persistencia, API, seguridad, integración, pruebas y documentación, con mensajes que explican el cambio |
| Decisiones arquitectónicas | Registros en inglés y portugués en `docs/adr/` distinguen las decisiones aceptadas de las evoluciones propuestas |

Los eventos de finalización mediante outbox, el despliegue en la nube y una interfaz auditada de reconciliación permanecen como evoluciones documentadas. La solución no requiere infraestructura adicional de mensajería o caché.

## Referencias del proyecto

- [Matriz de requisitos y criterios de entrega](https://github.com/Vidigal-code/product-activation-platform/blob/main/docs/requirements.md).
- [README](https://github.com/Vidigal-code/product-activation-platform/blob/main/README.md).

[Contrato de la API](?lang=es&menu=es&id=104) · [Pruebas](?lang=es&menu=es&id=107) · [Limitaciones y evolución](?lang=es&menu=es&id=108)

[Registros de decisiones de arquitectura (inglés y portugués)](https://github.com/Vidigal-code/product-activation-platform/blob/main/docs/adr/README.md).
