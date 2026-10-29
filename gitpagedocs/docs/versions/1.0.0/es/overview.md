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
