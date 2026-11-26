# ADR-011: Docker Compose como orquestación; Kubernetes descartado por ahora

- **Estado:** Aceptada
- **Fecha:** 2026-11-24
- **Responsable:** Sofi — Arquitectura de Software
- **Historias relacionadas:** HU-21, HU-22

## Contexto

§3.7 pide, para el sprint 3, «contenedores y orquestación cuando aplique». §3.3 exige como mínimo
backend y base de datos contenerizados con Docker Compose, y llama a Kubernetes «la evolución
opcional». §7.3 añade que Kubernetes «no debe incorporarse sin capacidad de monitoreo y soporte».

Hoy hay **un solo servicio desplegable** (ADR-001), una base de datos administrada fuera del clúster
(Supabase) y un plan gratuito de Render que ya se encarga de reiniciar el contenedor y de consultar
su `HEALTHCHECK`.

## Alternativas consideradas

### A. Docker Compose
Orquesta varios contenedores (API, PostgreSQL, Prometheus, Grafana) con un comando, igual en todas
las máquinas.

### B. Kubernetes (por ejemplo minikube o k3s)
Aporta escalado y recuperación declarativos, que con una sola réplica y 300 solicitudes por minuto
(RNF-01) no se aprovechan. Exige, además, un equipo que sepa operarlo.

### C. Solo Render
Suficiente para producción, pero no describe el entorno completo ni permite reproducir la
observabilidad en local.

## Decisión

**A.** `docker-compose.yml` ofrece tres usos mediante perfiles:

| Comando | Qué levanta |
|---|---|
| `docker compose up -d` | Solo PostgreSQL (el flujo de siempre) |
| `docker compose --profile app up -d --build` | PostgreSQL + la API, construida con el mismo `Dockerfile` que se publica en `ghcr.io` |
| `docker compose --profile observabilidad up -d --build` | Lo anterior + Prometheus + Grafana |

Producción sigue siendo Render con la imagen del pipeline. **Kubernetes se descarta ahora, con
criterios para reabrirlo.**

## Consecuencias

### Positivas
- Un entorno completo reproducible, versionado y revisable.
- La API local corre con la **misma imagen** que producción: se prueban el `Dockerfile`, el usuario
  sin privilegios y el `HEALTHCHECK`.

### Negativas y cómo se mitigan
- Compose no escala ni se recupera solo. Es aceptable: no es el entorno de producción.
- La API en Compose usa claves JWT efímeras (sin perfil `prod`): los tokens no sobreviven a un
  reinicio. Está comentado en el archivo.

## Cuándo reabrir esta decisión
Se revisa si ocurre alguna de estas: hay más de un servicio desplegable (por ejemplo, si el frontend
o un módulo se separan), se necesita escalado horizontal con evidencia de carga, o el equipo cuenta
con capacidad de monitoreo y soporte del clúster (§7.3).

## Verificación
- `docker compose --profile observabilidad config -q` corre en el pipeline y falla si el archivo es
  inválido.
- `docker compose ps` muestra `devnet-api` como *healthy* gracias al `HEALTHCHECK` del `Dockerfile`.
