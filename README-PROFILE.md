# HU Perfil - DevNet

Este entregable implementa las HU **Ver y editar perfil** y **Perfil publico** dentro de un monolito modular Spring Boot.

## Limites de modulos

- `profile` contiene las reglas, casos de uso, persistencia y REST del perfil.
- `project` expone solamente `PublicProjectQuery`; Profile no usa repositorios ni entidades internas de Project.
- `identity` sera el propietario del correo y autenticacion. Por eso el modelo y las respuestas de Profile no tienen correo.

## Endpoints

### Editar perfil propio

`PUT /api/profiles/{profileId}`

```json
{
  "displayName": "Ana Torres",
  "bio": "Desarrolladora backend especializada en Java.",
  "technologies": ["Java", "Spring Boot", "PostgreSQL"],
  "githubUrl": "https://github.com/anatorres",
  "linkedinUrl": "https://www.linkedin.com/in/anatorres"
}
```

Los campos `displayName`, `bio` y `technologies` son obligatorios. Si alguno falla, la API devuelve `400` con el nombre del campo y su mensaje.

### Editar avatar

`PATCH /api/profiles/{profileId}/avatar`

```json
{ "avatarUrl": "https://storage.ejemplo.com/avatars/ana.png" }
```

En este sprint se recibe la URL final. Cuando Shared integre Supabase Storage, ese modulo debe subir el archivo y llamar este caso de uso con la URL generada.

### Ver perfil publico

`GET /api/profiles/{profileId}`

Devuelve bio, tecnologias, enlaces profesionales y `publishedProjects`. Si no existe, devuelve `404` con `{"message":"Perfil no encontrado."}`. En REST esto equivale a la pantalla de "perfil no encontrado" que luego mostraria el frontend.

## Base de datos

Configura las variables `DEVNET_DB_URL`, `DEVNET_DB_USERNAME` y `DEVNET_DB_PASSWORD`, o usa los valores por defecto de `application.yaml`. JPA crea las tablas `profiles` y `profile_technologies` al iniciar por primera vez.

> Para probar los endpoints debe existir previamente un perfil. La creacion del perfil corresponde a la HU de registro del modulo Identity. El `id` del perfil esta preparado para ser el mismo id del usuario de Identity.

## Nota sobre Project

`EmptyPublicProjectQuery` devuelve una lista vacia mientras el modulo `project` no tenga publicaciones. Cuando esa HU exista, se reemplaza por una implementacion que consulte solo los proyectos publicados; `GetPublicProfileUseCase` no requiere cambios.
