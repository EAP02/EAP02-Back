# DevNet — Red Social para Desarrolladores

Proyecto del **equipo avanzado** de CodeF@ctory UdeA. Caso 13.

Backend robusto integrado a base de datos, sin frontend propio (el perfil avanzado
marca el frontend como N/A). La superficie de demostración es **Swagger UI** más una
colección de peticiones y los tableros de observabilidad.

---

## Qué hay en este repositorio de documentación

| Ruta | Curso | Contenido |
|---|---|---|
| [docs/adr/](docs/adr/) | Arquitectura | Registro de decisiones arquitectónicas (ADR) |
| [docs/arquitectura/](docs/arquitectura/) | Arquitectura | C4, diagrama de paquetes, despliegue, proceso principal, RNF |
| [docs/bd/](docs/bd/) | Bases de Datos | Consultas clave, modelo lógico, diccionario, índices, consultas no triviales |
| [backend/src/main/resources/db/migration/](backend/src/main/resources/db/migration/) | Bases de Datos | Modelo físico versionado con Flyway |

**El modelo físico y el script de estructuras son el mismo artefacto.** No se mantiene
un `.sql` de documentación aparte de las migraciones: la carpeta de Flyway es la única
fuente de verdad del esquema, y el modelo lógico documenta el *porqué*.

---

## Alcance por curso

Este equipo cubre **Arquitectura de Software** y **Bases de Datos**. Los entregables de
Calidad de Software y Gestión de Proyectos los producen los demás integrantes; esta
documentación les sirve de insumo pero no los reemplaza.

## Trazabilidad con Azure DevOps

El backlog vive en Azure DevOps y el código en GitHub. Para enlazarlos:

1. Instalar la app **Azure Boards** en la organización de GitHub.
2. Escribir `AB#<id>` en los mensajes de commit y en los títulos de pull request.

Con eso cada historia de usuario queda enlazada a sus commits, su PR, su ejecución de
pipeline y su despliegue sin trabajo manual. Es criterio evaluado (§4.3, Trazabilidad).

## Convención de ramas

```
main                          rama protegida, sin commits directos
feature/<id-historia>-<slug>  vida corta, integra por pull request
```

---

## Estado de los entregables de Sprint 1

### Arquitectura de Software

- [x] Diagrama C4 de contexto — [c4-01-contexto.puml](docs/arquitectura/c4-01-contexto.puml)
- [x] Diagrama C4 de contenedores — [c4-02-contenedores.puml](docs/arquitectura/c4-02-contenedores.puml)
- [x] Diagrama C4 de componentes — [c4-03-componentes-api.puml](docs/arquitectura/c4-03-componentes-api.puml)
- [x] Diagrama de paquetes y componentes **con interfaces** — [uml-paquetes.puml](docs/arquitectura/uml-paquetes.puml)
- [x] Diagrama de despliegue — [uml-despliegue.puml](docs/arquitectura/uml-despliegue.puml)
- [x] Estilo arquitectónico preliminar justificado — [ADR-001](docs/adr/ADR-001-estilo-arquitectonico.md)
- [x] Mínimo 3 ADR priorizados — hay 5
- [ ] Proyecto base Spring Boot en GitHub
- [ ] Al menos una HU implementada
- [ ] Despliegue inicial

### Bases de Datos

- [x] Entidades y relaciones — [02-modelo-logico.md](docs/bd/02-modelo-logico.md)
- [x] Preguntas / consultas clave — [01-consultas-clave.md](docs/bd/01-consultas-clave.md)
- [x] Modelo lógico normalizado — [02-modelo-logico.md](docs/bd/02-modelo-logico.md)
- [x] Modelo físico inicial — [V1__baseline.sql](backend/src/main/resources/db/migration/V1__baseline.sql)

---

## Cómo renderizar los diagramas

Los `.puml` usan [C4-PlantUML](https://github.com/plantuml-stdlib/C4-PlantUML).

- **VS Code:** extensión *PlantUML* (jebbs) + `Alt+D` para previsualizar.
- **Sin instalar nada:** pegar el contenido en <https://www.plantuml.com/plantuml>.
- **Exportar a PNG/SVG** para adjuntar en Azure DevOps antes de cada sustentación.

Los diagramas de estado y el MER están en Mermaid dentro de los `.md`, así que GitHub
los renderiza directamente en el navegador sin herramientas adicionales.