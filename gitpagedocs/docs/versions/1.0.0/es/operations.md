# Operación y diagnóstico

## Salud, logs y métricas

Liveness indica la salud del proceso. Readiness incluye la conectividad con PostgreSQL. Una indisponibilidad temporal del proveedor aumenta la cola, pero no debe provocar reinicios repetidos de instancias sanas.

Los logs utilizan JSON ECS e identificadores de correlación. El contexto acompaña a la solicitud y a la llamada externa sin incluir tokens, credenciales ni contenido sensible.

| Medidor | Finalidad |
| --- | --- |
| `activation.provider.calls` | Duración de las llamadas por resultado |
| `activation.worker.results` | Recuento de los resultados del procesamiento |
| `activation.requests` | Cantidad persistida por estado |
| `resilience4j.circuitbreaker.*` | Estado y comportamiento del circuito |
| Métricas HTTP, JVM y datasource | Latencia, errores, memoria, ejecución y uso del pool |

El endpoint compatible con Prometheus está disponible en `/actuator/prometheus`, protegido por el ámbito de monitorización en modo JWT. Los totales por estado se actualizan mediante una consulta agrupada cada diez segundos. Un fallo de lectura conserva la muestra anterior.

Los totales incluyen registros terminales todavía retenidos y son globales: las réplicas observan los mismos registros. Para consolidarlos entre réplicas, utilice `max`, no `sum`. La antigüedad del trabajo pendiente todavía requiere una consulta específica a la base de datos. No utilice identificadores de clientes como etiquetas de métricas.

## Trazas locales opcionales

```sh
docker compose -f docker-compose.yml -f docker-compose.observability.yml up --build -d --wait
```

El archivo adicional inicia Jaeger y habilita la exportación OTLP/HTTP con muestreo completo para la demostración. La interfaz está en `http://localhost:16686`. El contexto W3C se persiste, se restaura en el span `activation.process` y se propaga a la llamada HTTP al proveedor.

Jaeger local guarda los datos en memoria transitoria. Reiniciarlo elimina las trazas. Fuera de esta configuración adicional, la exportación OTLP permanece desactivada de forma predeterminada; un entorno duradero necesita un colector protegido, retención y muestreo adecuados.

## Escenario de lentitud

Considere una API que pasa de 300 ms a 2,5 s, consultas que pasan de 50 ms a 1,2 s y un proveedor que pasa de 400 ms a 3 s, con CPU al 45% y memoria al 60%.

1. **Investigación inicial:** relacionar el inicio con versiones publicadas, tráfico, crecimiento de datos e incidentes externos. Comparar percentiles por endpoint y resultado, errores y antigüedad de la cola. Un uso moderado de CPU y memoria no excluye esperas o contención.
2. **Localización:** separar el tiempo de espera por hilos o conexiones, SQL, transacciones y HTTP externo. Comparar trazas, bloqueos y actividad de PostgreSQL. Utilizar `EXPLAIN (ANALYZE, BUFFERS)` con consultas representativas y seguras, recordando que el comando ejecuta la consulta.
3. **Evidencias:** correlación, p50/p95/p99, timeouts, reintentos, conexiones Hikari activas y pendientes, volcados de hilos, pausas de GC, consultas lentas, bloqueos, I/O y antigüedad del trabajo pendiente más antiguo.
4. **Mejoras posibles:** corregir accesos lentos mediante índices justificados, SQL eficiente y transacciones más cortas; limitar tiempo y concurrencia; dimensionar el pool según la capacidad medida de la base de datos.
5. **Hipótesis común:** las llamadas externas lentas pueden mantener ocupadas conexiones o transacciones y aumentar las esperas. Confirmarlo con trazas y `pg_stat_activity`. Esta implementación evita HTTP dentro de la transacción, pero una red compartida y un exceso de reintentos todavía pueden afectar a ambas dependencias.
6. **Validación de la corrección:** comparar percentiles, errores, esperas de la base de datos, rendimiento y duración total de la activación con una carga equivalente. Desplegar gradualmente y mantener alertas y rollback. Un POST rápido no demuestra por sí solo que el procesamiento de la cola funcione correctamente.

## Producción y múltiples instancias

La imagen ejecuta JRE 21 con un usuario sin privilegios, heap adaptado al contenedor, apagado ordenado y health check. Producción requiere PostgreSQL con copias de seguridad cuya restauración se haya probado, TLS, JWT, un proveedor real, secretos inyectados y endpoints de administración privados. Las migraciones necesitan un despliegue controlado y privilegios adecuados.

Varias instancias comparten la unicidad, las reservas y la cuota mediante PostgreSQL. El balanceador distribuye el tráfico; cada worker reserva trabajo de forma independiente. Los circuit breakers permanecen locales a cada instancia.

Una futura etapa de despliegue publicaría la imagen validada en un registro, utilizaría un digest inmutable, aplicaría migraciones compatibles y observaría la disponibilidad, los errores y la latencia durante un despliegue gradual. La imagen anterior debe permanecer disponible para rollback. Estas etapas son orientaciones operativas; no representan un entorno de nube ya aprovisionado.

## Limitaciones y evolución

El proveedor es simulado. Un contrato real debe definir idempotencia y consulta de estado antes de habilitar la recuperación automática de resultados inciertos. La base de datos impide duplicados locales, pero no garantiza una ejecución exactamente una vez en el proveedor.

Las solicitudes terminales no tienen un endpoint público de reinicio. La reconciliación debe consultar al proveedor, registrar evidencias y autorizar una acción auditada; esa interfaz todavía no está implementada. Un estado local `FAILED` después de un resultado incierto no demuestra que la activación remota haya fallado.

El modo JWT depende de un proveedor de identidad externo. El modo local y los escenarios automáticos de Swagger están destinados a demostraciones en loopback, sin credenciales persistidas por el panel.

Para emitir eventos de finalización, la evolución propuesta consiste en guardar una outbox en la misma transacción que el estado final y publicar por separado. Todavía no hay una outbox, un broker ni publicación de eventos implementados.

La retención de solicitudes, los dashboards, las alertas, un colector duradero de telemetría y la automatización del despliegue dependen del entorno operativo. Jaeger local es transitorio. Un historial extenso puede requerir paginación por cursor. Estas propuestas permanecen como trabajo futuro.

## Referencias del proyecto

- [Métricas persistidas](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/java/com/vidigal/activation/observability/RequestMetrics.java).
- [Configuración adicional de observabilidad](https://github.com/Vidigal-code/product-activation-platform/blob/main/docker-compose.observability.yml).
- [Dockerfile](https://github.com/Vidigal-code/product-activation-platform/blob/main/Dockerfile).
- [Configuración de la aplicación](https://github.com/Vidigal-code/product-activation-platform/blob/main/src/main/resources/application.yml).

[Arquitectura](?lang=es&menu=es&id=105) · [Seguridad](?lang=es&menu=es&id=106) · [Ejecución local](?lang=es&menu=es&id=103)
