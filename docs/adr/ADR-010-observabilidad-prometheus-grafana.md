# ADR-010: Observabilidad con Micrometer, Prometheus y Grafana, y un token propio para el scraper

- **Estado:** Aceptada
- **Fecha:** 2026-11-24
- **Responsable:** Sofi — Arquitectura de Software
- **Historias relacionadas:** HU-21 (logging estructurado), HU-19 y HU-20 (lecturas administrativas)

## Contexto

§3.7 pide para el sprint 3 «observabilidad con Grafana/Prometheus» y §4.1 pide «logs,
métricas, trazas, dashboards y alertas accionables». Hasta el sprint 2 solo había logs JSON
con `traceId` (HU-21) y `/actuator/health`. Los objetivos de
[requisitos-no-funcionales.md](../arquitectura/requisitos-no-funcionales.md) (RNF-01, RNF-02,
RNF-03) decían medirse «en Micrometer, tablero de Grafana», y ese tablero no existía.

Restricciones reales:

- `SeguridadConfig` decía que las métricas «nunca» están abiertas. Se respeta esa intención.
- El JWT de usuario caduca a los 15 minutos y un scraper no inicia sesión: no sirve para
  autenticar a Prometheus.
- Render, en plan gratuito, no ofrece una red privada para que un Prometheus propio lea la API.

## Alternativas consideradas

### A. Micrometer + Prometheus + Grafana en Docker Compose, con token propio del scraper
Reproducible en cualquier máquina del equipo, se versiona (tablero y alertas son archivos) y no
depende de una cuenta externa.

### B. Grafana Cloud leyendo directamente la API en Render
Da observabilidad sobre producción, pero exige un endpoint alcanzable desde Internet, una cuenta
y credenciales fuera del repositorio. Queda **previsto**, no descartado.

### C. Quedarse con logs JSON
Los logs responden «qué pasó en esta petición», no «cómo se comporta el servicio». No permiten
ver percentiles ni saturación.

### D. Dejar `/actuator/prometheus` autenticado con el JWT de usuario
Es lo más simple, y no funciona: el scraper no tiene cómo obtener ni renovar el token.

## Decisión

**A**, con estas piezas:

1. `micrometer-registry-prometheus` publica `/actuator/prometheus`. Solo `health`, `info` y
   `prometheus` están expuestos.
2. El histograma de `http.server.requests` está activo, para calcular el **p95 por ruta** y
   compararlo con los presupuestos de RNF-02.
3. El endpoint exige `Authorization: Metricas <METRICAS_TOKEN>`. Lo verifica `FiltroMetricas`, con
   comparación en tiempo constante y **fallando cerrado** si el token configurado está vacío.
   Cualquier otro endpoint de Actuator sigue exigiendo un JWT de usuario. **El esquema es
   `Metricas` y no `Bearer`** a propósito: el resource server de Spring Security intenta validar como
   JWT toda cabecera `Bearer`, y el token del scraper no lo es, así que lo rechazaba con 401 antes de
   que llegara el filtro (se descubrió al levantar el stack con Docker Compose).
4. En producción `METRICAS_TOKEN` **no tiene valor por defecto**: sin él la aplicación no arranca,
   como ocurre con las claves JWT.
5. Prometheus y Grafana corren con `docker compose --profile observabilidad up`. El tablero
   «DevNet - API» y las alertas viven en `observabilidad/` y se aprovisionan solos.

## Consecuencias

### Positivas
- Los RNF pasan de «objetivo» a «medible»: p95 por ruta, errores 5xx, saturación del pool de
  Hikari, tasa de aciertos de caché y volumen de 401/403.
- Un pico de 401/403 se ve en el tablero y se investiga en `GET /api/v1/auditoria` (HU-20): la
  métrica avisa del volumen y la auditoría dice quién fue.
- Todo es código revisable en un pull request.

### Negativas y cómo se mitigan
- **Producción no se está recolectando todavía.** Prometheus corre en local. Mitigación: el
  endpoint y el token ya existen en la imagen desplegada; conectar Grafana Cloud es solo
  configuración (opción B).
- **El endpoint de métricas pasa por `permitAll` en la cadena de Spring Security** y lo custodia
  un filtro aparte. Es una excepción a la regla de «todo Actuator autenticado» y se acepta porque
  el filtro es pequeño, está probado y falla cerrado.
- El token es un secreto compartido sin rotación automática. Mitigación: se rota cambiando la
  variable en Render y en Prometheus; documentado en `.env.example`.

## Verificación

- `FiltroMetricasTest`: sin token, token distinto, esquema `Bearer` y token vacío → 401.
- `docker compose --profile observabilidad up -d` y, en Prometheus → Status → Targets,
  `devnet-api` en **UP**.
- En Grafana, el tablero «DevNet - API» muestra datos tras unas peticiones a Swagger.
