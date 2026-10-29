# Pruebas e integración continua

Las pruebas separan las políticas y las reglas de dominio de las integraciones y los flujos completos.

| Capa | Alcance | Ubicación |
| --- | --- | --- |
| Unitaria Java | Políticas, configuración, estados, correlación, errores y dependencias arquitectónicas | `src/test/unit/java` |
| Integración Java | HTTP/JWT, API, base de datos real, concurrencia, transacciones y reserva | `src/test/integration/java` |
| End-to-end Java | Aplicación HTTP en ejecución con un proveedor controlado | `src/test/e2e/java` |
| Unitaria JavaScript | Ejecutor de escenarios, validaciones, límites, timeout y cancelación | `src/test/unit/javascript` |
| Navegador | Selección de los 16 escenarios, repetición, cancelación y recuperación de cuota | `src/test/e2e/javascript` |
| Colección HTTP | Contratos de API, proveedor, concurrencia, salud, métricas y límites | `postman` |

Para Java, instale JDK 21 y mantenga Docker disponible:

```sh
./mvnw test
./mvnw -Dit.test='*IT' verify
./mvnw -Dit.test='*E2E' verify
./mvnw verify
```

Los comandos ejecutan, respectivamente, pruebas unitarias; unitarias y de integración; unitarias y end-to-end; todas las capas Java. En Windows, sustituya `./mvnw` por `.\mvnw.cmd`. Testcontainers utiliza PostgreSQL desechable, independiente de la base de datos de Compose. Las pruebas que necesitan Docker no se omiten silenciosamente cuando no está disponible.

Para los escenarios de Swagger, utilice Node.js 22 o superior:

```sh
npm ci
npm run test:unit
npx playwright install chromium
docker compose up -d --wait
npm run test:e2e
```

Los informes Java se generan en `target/surefire-reports`, `target/failsafe-reports` y `target/site/jacoco`. Las capturas del navegador están en `target/swagger-e2e`. Las dependencias Node se utilizan para desarrollo y pruebas; la imagen de producción de la aplicación no requiere una compilación de frontend.

La CI se ejecuta en `main`, `dev` y pull requests: configura Java y Node, instala dependencias, ejecuta pruebas unitarias JavaScript y Maven verify, valida Compose, construye la imagen, ejecuta pruebas de navegador y Newman y publica los informes. Las actions utilizan revisiones fijadas y permisos mínimos de lectura del repositorio. La CI no realiza despliegues en cuentas externas.

## Referencias del proyecto

- [Configuración Maven](https://github.com/Vidigal-code/product-activation-platform/blob/main/pom.xml).
- [Pruebas](https://github.com/Vidigal-code/product-activation-platform/tree/main/src/test).
- [Workflow de CI](https://github.com/Vidigal-code/product-activation-platform/blob/main/.github/workflows/ci.yml).
- [Comandos Node](https://github.com/Vidigal-code/product-activation-platform/blob/main/package.json).

[Ejecución local](?lang=es&menu=es&id=103) · [Requisitos](?lang=es&menu=es&id=102) · [Operación](?lang=es&menu=es&id=108)
