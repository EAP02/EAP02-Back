# Arquitectura de la solución — DevNet

Documentación del curso de **Arquitectura de Software**. Las decisiones que sostienen
todo lo que hay aquí están en [docs/adr/](../adr/); este directorio contiene las vistas.

## Contenido

| Archivo | Vista | Entregable |
|---|---|---|
| [c4-01-contexto.puml](c4-01-contexto.puml) | C4 nivel 1 — contexto del sistema | Sprint 1 |
| [c4-02-contenedores.puml](c4-02-contenedores.puml) | C4 nivel 2 — contenedores | Sprint 1 |
| [c4-03-componentes-api.puml](c4-03-componentes-api.puml) | C4 nivel 3 — componentes de la API | Sprint 1 |
| [uml-paquetes.puml](uml-paquetes.puml) | Paquetes y componentes **con interfaces** | Sprint 1 (literal) |
| [uml-despliegue.puml](uml-despliegue.puml) | Despliegue | Sprint 1, se refina en Sprint 2 |
| [proceso-principal.md](proceso-principal.md) | Máquinas de estado y reglas de negocio | Sprint 1 |
| [requisitos-no-funcionales.md](requisitos-no-funcionales.md) | Atributos de calidad con objetivos medibles | Sprint 1 |

## Vista lógica en capas

§4.2 pide un modelo por capas explícito. En DevNet las capas viven **dentro de cada
módulo**, no como paquetes horizontales del sistema completo (ver
[ADR-001](../adr/ADR-001-estilo-arquitectonico.md) para por qué).

| Capa de §4.2 | Dónde está en DevNet | Contenido |
|---|---|---|
| Presentación | No aplica | El perfil avanzado no contempla frontend. Swagger UI cumple la función de superficie de interacción |
| Aplicación | `<modulo>.application` | Casos de uso, orquestación, límites transaccionales |
| Dominio | `<modulo>.domain` | Entidades, objetos de valor, reglas, políticas ABAC, puertos. Sin Spring ni JPA |
| Infraestructura | `<modulo>.infrastructure` | Adaptadores JPA, cliente de GitHub, almacenamiento de archivos |
| Integración | `<modulo>.api` + `shared` | Contrato OpenAPI versionado, validación, errores uniformes, `traceId` |

## Responsabilidades por componente

Correspondencia con la tabla de §4.1 de los lineamientos:

| Componente de §4.1 | En DevNet |
|---|---|
| Cliente web | No aplica (frontend N/A en el perfil avanzado) |
| Gestión de identidad y acceso | Módulo `identity`, con GitHub como IdP federado |
| API Gateway | No aplica. Con un solo contenedor, un gateway añadiría un salto sin aportar enrutamiento ni balanceo real |
| Backend | Los ocho módulos de negocio más el kernel `shared` |
| Base de datos transaccional | PostgreSQL 16 en Supabase |
| Repositorio de recursos | Supabase Storage, tras la interfaz `AlmacenArchivos` |
| Observabilidad | Actuator + Micrometer → Prometheus/Grafana Cloud; logs JSON con `traceId` |
| Plataforma DevOps | GitHub Actions; backlog y métricas en Azure DevOps |

La no adopción de API Gateway se documenta aquí a propósito: §4.1 lo condiciona a "cuando
aplique", y dejar constancia de la evaluación evita que se lea como omisión.

## Cómo renderizar

- **VS Code:** extensión *PlantUML* (jebbs), `Alt+D` para previsualizar.
- **Sin instalar nada:** pegar el contenido en <https://www.plantuml.com/plantuml>.
- Exportar a PNG o SVG antes de cada sustentación y adjuntar en Azure DevOps.

Los `.md` usan Mermaid, que GitHub renderiza directamente sin herramientas.

## Regla de mantenimiento

§4.4 lo exige y conviene tomárselo en serio: **la documentación de arquitectura se
actualiza en el mismo sprint en que cambia la solución.** Un diagrama que no corresponde al
código desplegado es peor que no tener diagrama, porque se defiende algo que no existe.

En la práctica: si un pull request cambia una frontera entre módulos, un contrato o el
despliegue, ese mismo pull request actualiza el diagrama correspondiente. Es parte de la
Definition of Done (§9.1).