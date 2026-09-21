# DevNet — monolito modular

Backend de una red social para desarrolladores. Spring Boot 4 sobre PostgreSQL,
organizado en tres módulos de negocio (`identity`, `profile`, `project`) más un kernel
compartido.

## Documentación

| Dónde | Qué |
|---|---|
| [docs/adr/](docs/adr/) | Las seis decisiones de arquitectura, con su contexto y su verificación |
| [docs/arquitectura/](docs/arquitectura/) | Vistas C4, diagrama de paquetes, proceso principal y RNF |
| [docs/bd/](docs/bd/) | Modelo lógico, diccionario de datos, índices y consultas clave |

## Requisitos

- JDK 17
- Docker, para la base de datos local y las pruebas de integración
- Maven 3.9+ o el wrapper incluido

## Ejecutar en local

```bash
docker compose up -d     # PostgreSQL 16 en el puerto 5432
./mvnw spring-boot:run
```

No hace falta configurar nada más: los valores por defecto de `application.yml` apuntan a
ese contenedor. **Flyway aplica las migraciones al arrancar.**

Swagger UI: <http://localhost:8080/swagger-ui.html>

## Pruebas

```bash
./mvnw test      # unitarias y de arquitectura. No necesitan Docker
```

Las pruebas de integración terminan en `IT` y usan Testcontainers. Hoy **no las ejecuta
nadie**: Surefire solo recoge `*Test` y el `maven-failsafe-plugin` no está en el `pom.xml`.

## Configuración para integración y producción

Todo llega por variable de entorno; ver [`.env.example`](.env.example). El perfil `prod`
no arranca si falta alguna, que es preferible a un servicio corriendo con una credencial
por defecto.

```bash
export DATABASE_URL='jdbc:postgresql://HOST:5432/BASE?sslmode=require'
export DATABASE_USERNAME='USUARIO'
export DATABASE_PASSWORD='CLAVE'
```

En IntelliJ IDEA pueden agregarse en **Run > Edit Configurations > Environment variables**.

## Flujo mínimo

1. `POST /api/v1/auth/registro`
2. `POST /api/v1/auth/inicio-sesion`
3. Usar `Authorization: Bearer <token>`
4. `PUT /api/v1/perfiles/{usuarioId}`
5. `POST /api/v1/proyectos`
6. `GET /api/v1/perfiles/{usuarioId}`

Al registrar una cuenta, `identity` publica `UsuarioRegistrado` y `profile` crea el perfil
inicial. La lectura pública del perfil obtiene los proyectos desde
`project.api.ProyectoPublicoConsulta`. No hay contratos de negocio en `shared`: cada
módulo publica el suyo en su propio paquete `api`
([ADR-006](docs/adr/ADR-006-contratos-entre-modulos-en-api.md)).

El inicio de sesión devuelve además una cookie `devnet_refresco` que renueva el token de
acceso en `POST /api/v1/auth/refresco` sin volver a pedir la contraseña.

## El esquema lo gobierna Flyway

`spring.jpa.hibernate.ddl-auto=validate`, en todos los entornos. Hibernate **nunca** crea
ni modifica el esquema: solo comprueba que las entidades correspondan a lo que Flyway
construyó, y si no coinciden la aplicación no arranca ([ADR-003](docs/adr/ADR-003-persistencia-supabase-flyway.md)).

Estuvo en `update` durante la integración de módulos, lo que dejaba el esquema en manos de
Hibernate y vaciaba de sentido el modelo de datos. Revertido.
