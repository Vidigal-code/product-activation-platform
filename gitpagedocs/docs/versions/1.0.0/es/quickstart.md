# Ejecución local

Se necesitan Git, Docker Engine/Desktop con contenedores Linux y Docker Compose v2. El entorno completo no requiere Java instalado en el host. La primera construcción descarga las dependencias y las imágenes.

Windows PowerShell, desde la raíz del repositorio:

```powershell
.\scripts\setup-local.ps1
docker compose up --build -d --wait
docker compose ps
```

Linux/macOS:

```sh
sh scripts/setup-local.sh
docker compose up --build -d --wait
docker compose ps
```

El script genera una contraseña aleatoria para la base de datos en `.env` y conserva cualquier archivo existente. Git ignora ese archivo. Los puertos se pueden ajustar según `.env.example`. Cambiar la contraseña del archivo no modifica la credencial de un volumen PostgreSQL ya inicializado.

| Recurso | Dirección predeterminada en la máquina local |
| --- | --- |
| API | `http://localhost:8080` |
| Swagger | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI | `http://localhost:8080/v3/api-docs` |
| Readiness | `http://localhost:8080/actuator/health/readiness` |
| Liveness | `http://localhost:8080/actuator/health/liveness` |
| WireMock | `http://localhost:8089/__admin/mappings` |
| PostgreSQL | `localhost:5432`, base de datos y usuario `activation` |

Compose selecciona explícitamente `SECURITY_MODE=local`. La demostración no exige autenticación y publica los puertos únicamente en `127.0.0.1`. Los entornos compartidos deben utilizar JWT, TLS y controles de red.

## Probar sin completar campos

En Swagger, seleccione **Exemplo automático**. La selección inicia el escenario inmediatamente. El panel prepara los datos, genera identificadores, ejecuta los pasos previos y consulta el resultado. **Executar novamente** utiliza una solicitud nueva; **Cancelar** interrumpe las llamadas locales sin deshacer el trabajo ya registrado. Estos son los nombres de los controles de la interfaz.

Los 16 escenarios cubren éxito, repetición, conflicto, cinco fallos simulados del proveedor, validación, campo desconocido, consulta existente, consulta inexistente, UUID inválido, listado, cuerpo excesivo y límite de llamadas. Las operaciones nativas de Swagger siguen disponibles para uso manual.

La colección completa también se puede ejecutar automáticamente:

```sh
docker compose --profile test run --rm newman
```

El runner temporal verifica los contratos, espera los estados finales, prueba 12 creaciones simultáneas y ejecuta una ráfaga limitada para observar `429`. La siguiente ejecución respeta `Retry-After` si la cuota anterior sigue agotada. El perfil `test` no forma parte del arranque normal de la aplicación.

Para Postman, importe `postman/product-activation.postman_collection.json` y `postman/local.postman_environment.json`, seleccione el entorno y ejecute la colección completa. En modo local, `bearerToken` permanece vacío. Un entorno JWT necesita un token real; no exporte ni versione esa credencial.

## Comandos operativos

```sh
docker compose logs -f app
docker compose stop
docker compose up -d --wait
docker compose down
```

`down` conserva el volumen de la base de datos. Eliminar los volúmenes borra las solicitudes persistidas y debe ser una decisión explícita de reinicialización del entorno.

## Referencias del proyecto

- [Docker Compose](https://github.com/Vidigal-code/product-activation-platform/blob/main/docker-compose.yml).
- [Configuración local PowerShell](https://github.com/Vidigal-code/product-activation-platform/blob/main/scripts/setup-local.ps1).
- [Configuración local POSIX](https://github.com/Vidigal-code/product-activation-platform/blob/main/scripts/setup-local.sh).
- [Colección Postman](https://github.com/Vidigal-code/product-activation-platform/blob/main/postman/product-activation.postman_collection.json).
- [Entorno Postman](https://github.com/Vidigal-code/product-activation-platform/blob/main/postman/local.postman_environment.json).

[Contrato de la API](?lang=es&menu=es&id=104) · [Pruebas](?lang=es&menu=es&id=107) · [Seguridad](?lang=es&menu=es&id=106)
