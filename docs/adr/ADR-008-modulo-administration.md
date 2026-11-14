# ADR-008: Las lecturas administrativas viven en un módulo `administration`

- **Estado:** Aceptada
- **Fecha:** 2026-11-14
- **Responsable:** _[completar nombre]_ — Arquitectura de Software
- **Historias relacionadas:** HU-20 (registro de auditoría), HU-19 (reporte de actividad)

## Contexto

HU-20 pide que un administrador consulte el registro de auditoría y HU-19, un reporte de
actividad y crecimiento. Ninguna de las dos pertenece a un dominio de negocio existente:
la auditoría atraviesa a todos los módulos (la escriben `identity`, `project`,
`interaction`, `messaging` y los triggers de V10) y el reporte agrega datos de varios.
Además comparten reglas: solo ADMIN, rango de fechas y paginación.

La escritura de auditoría ya vive en `shared.audit`. Faltaba decidir dónde va la **lectura**.

## Alternativas consideradas

### A. En `shared.audit`, junto a la escritura
Cercano a la tabla, pero `shared` es el kernel y la regla 4 de `FronterasModularesTest`
le prohíbe conocer el negocio. Un controlador con paginación, filtros y resolución de
nombres de usuario no es kernel, y le haría depender de `identity`.

### B. En `identity`
`identity` ya guarda los eventos de sesión, pero no es dueño de la auditoría de
comentarios, proyectos o mensajes. Lo convertiría en el módulo que lee lo de todos.

### C. Un módulo `administration`, con las cuatro capas (elegida)

## Decisión

Se crea el módulo `administration` (`api`, `domain`, `application`, `infrastructure`).
Consume `identity.api.UsuarioDirectorio` para el nombre de quien actuó y `shared` para
errores y para el enum de operaciones de auditoría, y no lo consume ningún otro módulo.
Lee `auditoria` por JDBC con sentencias estáticas. HU-19 reutilizará `RangoFechas` y
`Paginacion`, que viven en su dominio.

## Consecuencias

- Las lecturas transversales de administración tienen un hogar y no pesan sobre el kernel
  ni sobre un módulo de negocio.
- `administration` se añade a `MODULOS` en `FronterasModularesTest`: su frontera se verifica
  en cada build como la de los demás.
- El módulo lee tablas de otros por SQL (`auditoria` hoy; `metricas_diarias` en HU-19). Es el
  mismo acoplamiento de datos que ya tienen `interaction` y `discussion` con `publicacion`
  (nota N3 de `uml-paquetes.puml`), y se acepta por la misma razón: no hay importación entre
  módulos, solo SQL de lectura.
- La consulta de la auditoría no se audita a sí misma (hallazgo H-26 de OWASP).
