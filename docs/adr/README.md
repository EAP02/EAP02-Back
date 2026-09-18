# Registro de decisiones arquitectónicas (ADR)

Cada decisión relevante se registra con **contexto, alternativas, elección,
consecuencias, responsable y fecha**, según exige §4.4 de los lineamientos.

Un ADR no se edita cuando la realidad cambia: se crea uno nuevo que lo **supersede** y
el anterior queda marcado como reemplazado. El historial es el valor del registro.

## Índice

| ID | Decisión | Estado | Fecha |
|---|---|---|---|
| [ADR-001](ADR-001-estilo-arquitectonico.md) | Monolito modular con separación hexagonal | Aceptada — regla 1 reemplazada por ADR-006 | 2026-09-13 |
| [ADR-002](ADR-002-estilo-api-rest-openapi.md) | API REST versionada con contrato OpenAPI | Aceptada | 2026-09-13 |
| [ADR-003](ADR-003-persistencia-supabase-flyway.md) | PostgreSQL administrado en Supabase, esquema versionado con Flyway | Aceptada | 2026-09-13 |
| [ADR-004](ADR-004-identidad-propia-github-oidc.md) | Identidad propia en Spring Security con GitHub como IdP federado | Aceptada | 2026-09-13 |
| [ADR-005](ADR-005-contrato-errores-traceid.md) | Contrato de errores uniforme y correlación por `traceId` | Aceptada | 2026-09-13 |
| [ADR-006](ADR-006-contratos-entre-modulos-en-api.md) | El contrato entre módulos se publica en `<módulo>.api` | Aceptada | 2026-09-18 |

## Estados posibles

`Propuesta` · `Aceptada` · `Reemplazada por ADR-NNN` · `Descartada`

## Plantilla

```markdown
# ADR-NNN: <título en forma de decisión>

- **Estado:** Propuesta
- **Fecha:** AAAA-MM-DD
- **Responsable:** <nombre> — <curso>
- **Historias relacionadas:** AB#<id>

## Contexto
Qué fuerza la decisión. Restricciones reales, no deseos.

## Alternativas consideradas
### A. <nombre>
### B. <nombre>
### C. <nombre>

## Decisión
Qué se eligió y por qué esa y no las otras.

## Consecuencias
### Positivas
### Negativas y cómo se mitigan

## Verificación
Cómo se comprueba que la decisión sigue vigente en el código.
```

La sección **Verificación** no es estándar en la plantilla MADR, pero la añadimos a
propósito: un ADR sin forma de comprobarlo en el código es una intención, no una
decisión de arquitectura.