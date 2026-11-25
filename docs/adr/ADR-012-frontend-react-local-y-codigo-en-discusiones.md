# ADR-012: Frontend React local como cliente de la API, y código insertable en discusiones

- **Estado:** Propuesta — los puntos marcados **[CONFIRMAR]** los debe validar quien implementó
  HU-22 y HU-23 antes de pasar a *Aceptada*
- **Fecha:** 2026-11-24
- **Responsable:** Sofi — Arquitectura de Software
- **Historias relacionadas:** HU-22 (frontend), HU-23 (valor agregado: insertar código en discusiones)

## Contexto

Hasta el sprint 2 el producto no tenía interfaz propia: Swagger UI era la única superficie
(ADR-007, y `docs/arquitectura/README.md` decía «Cliente web: no aplica»). En el sprint 3 el equipo
añadió un frontend (HU-22), recomendado por el profesor, que **corre en local y no está desplegado**.
Eso cambia dos cosas que el repositorio daba por hechas: ya existe un cliente web, y las decisiones de
sesión del ADR-004 dejan de aplicar en el vacío.

HU-23, el diferenciador de la plataforma, permite **insertar fragmentos de código en las
discusiones**. Es una entrada de usuario que otro usuario vuelve a ver, así que es un vector clásico
de XSS almacenado (OWASP A03).

## Alternativas consideradas

### A. Cliente SPA separado que consume `/api/v1` (elegida)
Respeta el monolito modular: la API sigue siendo el único artefacto desplegable y el contrato
OpenAPI es la frontera.

### B. Servir el frontend desde el mismo Spring Boot
Un solo despliegue, pero mezcla ciclos de vida y vuelve a la API dependiente de un cliente concreto.

### C. Frontend con BFF (Next.js como intermediario)
Útil si hay renderizado en servidor; añade un contenedor y un salto que hoy no se justifican.

## Decisión

1. **El frontend es un cliente más de la API REST.** No hay BFF ni lógica de negocio duplicada: las
   reglas siguen en el backend y el cliente solo mejora la experiencia (§8.4: validar en cliente no
   reemplaza la validación del servidor).
2. **Sesión.** El token de acceso (15 min) viaja como `Authorization: Bearer`; la cookie de refresco
   es `HttpOnly` y `SameSite=Strict`. Funciona porque en local el frontend (`localhost:5173` o
   `localhost:3000`) y la API (`localhost:8080`) son el **mismo sitio**; `CORS_ORIGENES` ya los
   permite. **[CONFIRMAR]** dónde guarda el frontend el token de acceso (se recomienda en memoria,
   no en `localStorage`).
3. **Contrato de errores para el cliente.** `401 AUTH_TOKEN_INVALIDO` → intentar **una** vez
   `POST /api/v1/auth/refresco`; si falla o llega `401 AUTH_REQUERIDA` → ir al inicio de sesión;
   `403 ACCESO_DENEGADO` → mostrar «sin permiso» sin reintentar. Se corrigió el servidor para que
   emita `AUTH_TOKEN_INVALIDO` (antes siempre respondía `AUTH_REQUERIDA`).
4. **Código en discusiones (HU-23).** El fragmento se trata **siempre como texto**: se muestra
   escapado, nunca como HTML (sin `dangerouslySetInnerHTML` ni equivalentes). El servidor lo valida
   como cualquier otro contenido. **[CONFIRMAR]** cómo se guarda hoy: dentro del cuerpo de la
   discusión como Markdown con bloques delimitados (` ```lenguaje `) o en un campo propio, y el
   tamaño máximo que se aplica.

## Consecuencias

### Positivas
- Una interfaz demostrable sin tocar el diseño del backend.
- El diferenciador (HU-23) se resuelve sin ampliar la superficie de ataque, porque el código nunca
  se interpreta.

### Negativas y cómo se mitigan
- **Si el frontend se despliega en otro dominio** (por ejemplo Vercel), `SameSite=Strict` deja de
  enviar la cookie de refresco y el ADR-004 hay que revisarlo (`SameSite=None; Secure` más una
  defensa contra CSRF). Mientras corra en local, no ocurre.
- El frontend no está desplegado, así que no entra en el pipeline de Render ni en el análisis de
  Sonar del backend. Cuando se despliegue, necesita su propio flujo y su evaluación de accesibilidad
  (WCAG 2.2 AA, §8.2).

## Verificación
- `ErroresApiIT` y `SeguridadTokensIT` fijan el contrato de errores que el cliente consume.
- Revisión de código del frontend: ninguna llamada a `dangerouslySetInnerHTML`, `innerHTML` ni
  `eval` sobre contenido de discusiones. **[CONFIRMAR]**
