# DevNet — monolito modular

Proyecto unificado a partir de los módulos de autenticación, perfiles y proyectos.

## Requisitos

- JDK 17
- PostgreSQL
- Maven 3.9+ o el wrapper incluido

## Credenciales de base de datos

Configura estas variables antes de iniciar:

```bash
export DATABASE_URL='jdbc:postgresql://HOST:5432/BASE'
export DATABASE_USERNAME='USUARIO'
export DATABASE_PASSWORD='CLAVE'
```

En IntelliJ IDEA pueden agregarse en **Run > Edit Configurations > Environment variables**.

## Ejecutar

```bash
./mvnw clean test
./mvnw spring-boot:run
```

Swagger UI: <http://localhost:8080/swagger-ui.html>

## Flujo mínimo

1. `POST /api/v1/auth/registro`
2. `POST /api/v1/auth/inicio-sesion`
3. Usar `Authorization: Bearer <token>`
4. `PUT /api/v1/perfiles/{usuarioId}`
5. `POST /api/v1/proyectos`
6. `GET /api/v1/perfiles/{usuarioId}`

Al registrar una cuenta, `identity` publica `UsuarioRegistrado` y `profile`
crea el perfil inicial. La lectura pública del perfil obtiene los proyectos desde
`project.api.ProyectoPublicoConsulta`. No hay contratos de negocio en `shared`.

Hibernate mantiene el esquema con `spring.jpa.hibernate.ddl-auto=update`.
