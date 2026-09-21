# ADR-007: Swagger UI queda habilitado también en producción

- **Estado:** Aceptada
- **Fecha:** 2026-09-19
- **Responsable:** _[completar nombre]_ — Arquitectura de Software
- **Historias relacionadas:** transversal; condiciona la demostración de cada sprint

**Supersede la restricción del [ADR-002](ADR-002-estilo-api-rest-openapi.md) que limitaba
Swagger UI a los entornos no productivos.** El resto de aquella decisión —versionado en la
ruta, contrato generado desde el código, política de compatibilidad— sigue íntegra.

## Contexto

El ADR-002 decidió exponer Swagger UI solo fuera de producción, y `application-prod.yml`
lo implementaba apagando `springdoc.swagger-ui` y `springdoc.api-docs`. El razonamiento era
el §6.1: «configuración endurecida» en el despliegue, y un contrato navegable es superficie
de exploración.

Ese razonamiento choca de frente con otras dos decisiones del mismo proyecto:

- El perfil avanzado marca el frontend como **N/A** (§1.3). No hay cliente web propio.
- El propio ADR-002 llama a Swagger UI «la superficie de demostración del proyecto», y el
  diagrama de contexto dice que los actores consumen la API a través de Swagger UI y de
  colecciones de peticiones.

Es decir: el único entorno desplegado no tenía ninguna superficie por la que demostrar el
producto. Las demostraciones de sprint y la sustentación ocurren contra Render, no contra
la máquina de nadie.

La alternativa que se venía aplicando —encenderlo para grabar y apagarlo después— deja la
documentación afirmando una cosa y el sistema haciendo otra durante esos períodos, que es
justo lo que el §4.4 pretende evitar.

## Alternativas consideradas

### A. Mantenerlo apagado y demostrar en local

Es lo más endurecido. Pero demostrar en `localhost` no prueba que el despliegue funcione:
oculta precisamente los fallos que solo aparecen en producción —la conexión al pooler, las
claves JWT por variable de entorno, el arranque en frío—, que son los que han costado
tiempo real en este proyecto.

### B. Encenderlo solo durante las demostraciones

Dos variables de entorno y un redespliegue cada vez. Funciona, pero introduce un estado del
sistema que ningún documento describe, y depende de que alguien se acuerde de apagarlo.
Un olvido produce exactamente la situación que esta decisión quiere evitar: contradicción
silenciosa entre el ADR y la realidad.

### C. Dejarlo encendido y registrarlo

Se asume la exposición del contrato y se documenta por qué es aceptable en este alcance.

## Decisión

Se adopta la **alternativa C**. Swagger UI y `/v3/api-docs` quedan habilitados en todos los
entornos, incluido producción.

Lo que sostiene la decisión:

**Exponer el contrato no concede acceso.** Swagger UI es documentación de solo lectura. Las
rutas protegidas siguen exigiendo un JWT con los permisos correspondientes, verificados en
el servidor en cada operación (§6.2), y eso no cambia porque alguien sepa que el endpoint
existe. La seguridad por desconocimiento de la ruta no era un control con el que se contara.

**El alcance es académico y los datos no son reales.** No hay información personal de
terceros, ni datos de negocio, ni relación contractual con usuarios. El perjuicio de una
exploración no autorizada es acotado, y la base es reconstruible desde las migraciones.

**El valor de demostración es alto y concreto.** Es la única superficie de interacción del
producto, y demostrar sobre el despliegue real es lo que acredita el §3.3 —«desplegar en
nube»— frente a una demostración local que no prueba el despliegue.

### Lo que NO cambia

- Los mensajes de error siguen sin exponer detalles internos: el ADR-005 manda, y la causa
  de una violación de integridad o el mensaje del parser se registran en el log y nunca en
  la respuesta.
- `/actuator/**` sigue exigiendo autenticación, salvo `health` e `info`. Las métricas no se
  abren.
- Los orígenes CORS siguen restringidos por lista blanca.
- Las credenciales siguen fuera del código, por variable de entorno (§7.3).

## Consecuencias

### Positivas

- Existe una superficie de demostración en el entorno desplegado, que es donde ocurren las
  demostraciones de sprint y la sustentación.
- Desaparece el estado transitorio de la alternativa B y, con él, la posibilidad de que el
  sistema contradiga a su documentación por un olvido.
- El contrato publicado es verificable por cualquiera contra el despliegue, que es lo que
  §3.3 pide al hablar de «definir y publicar el contrato».

### Negativas y cómo se mitigan

| Riesgo | Mitigación |
|---|---|
| Un tercero enumera todos los endpoints y sus esquemas | Aceptado. La autorización se verifica en el servidor por operación; conocer la ruta no concede nada |
| Facilita construir peticiones malformadas dirigidas | La validación de entrada es la misma para todos; un 422 con detalle por campo no revela estado del sistema |
| Facilita el sondeo automatizado de `/auth/**` | **Sin mitigar hoy.** El límite de tasa con Bucket4j que el ADR-004 declara pendiente pasa a ser el control más relevante de este ADR. El bloqueo por cinco intentos fallidos acota el ataque por credenciales, pero no el volumen de peticiones |
| Esta decisión no sería aceptable con datos reales | Explícito: si el proyecto saliera del ámbito académico o incorporase datos de personas reales, **este ADR debe revisarse antes**, y volver al comportamiento del ADR-002 es un cambio de una línea en `application-prod.yml` |

## Verificación

En el servicio desplegado:

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://<host>/swagger-ui.html   # 200
curl -s -o /dev/null -w '%{http_code}\n' https://<host>/v3/api-docs        # 200
curl -s -o /dev/null -w '%{http_code}\n' https://<host>/actuator/metrics   # 401
```

Los tres a la vez. El tercero es el que demuestra que esto es una apertura deliberada y
acotada del contrato, y no un despliegue sin endurecer.

La configuración vive en
[`application-prod.yml`](../../src/main/resources/application-prod.yml); no depende de
variables de entorno, para que el estado del sistema esté en el repositorio y no en el
panel de un proveedor.
