# Criterios de aceptación en Gherkin — HU-19 (reporte de actividad y crecimiento)

Continúa [`gherkin-sprint3.md`](gherkin-sprint3.md) (HU-20) con las mismas convenciones:
`@positivo` / `@excepcional`, códigos del contrato (ADR-005) y una tabla que enlaza cada
escenario con la prueba que lo verifica hoy. La automatización con Cucumber es del sprint 3 de
Calidad (§3.7); mientras tanto cada escenario tiene su equivalente en JUnit con la base real.

Verificación manual en Swagger: [`../pruebas-sprint3-hu19.md`](../pruebas-sprint3-hu19.md).

---

## HU-19 · Reporte de actividad y crecimiento

```gherkin
# language: es
Característica: Reporte de actividad y crecimiento de la comunidad
  Como administrador
  Quiero ver un reporte de usuarios activos, publicaciones y crecimiento
  Para medir la salud de la comunidad

  Antecedentes:
    Dado que existe un administrador con el segundo factor inscrito y sesión iniciada
    Y que en las dos semanas del 3 al 16 de junio de 2019:
      | quién | qué                                    | cuándo |
      | ana   | se registra y publica un proyecto      | 4 jun  |
      | beto  | se registra y comenta el proyecto      | 5 jun  |
      | ana   | publica una discusión                  | 6 jun  |
      | cami  | se registra y no hace nada más         | 12 jun |

  # ---- Criterio 1: con un rango válido, usuarios activos y lo publicado

  @positivo @HU-19
  Escenario: Usuarios activos, proyectos, discusiones y comentarios del rango
    Cuando el administrador consulta el reporte del 3 al 16 de junio
    Entonces el sistema responde 200
    Y hay 1 proyecto, 1 discusión y 1 comentario publicados
    Y los usuarios activos son 2, "ana" y "beto"

  @positivo @HU-19
  Escenario: Un usuario activo varios días cuenta una sola vez
    Dado que "ana" fue activa el 4 y el 6 de junio
    Cuando el administrador consulta el reporte del 3 al 16 de junio
    Entonces los usuarios activos son 2 y no 3

  @positivo @HU-19
  Escenario: Lo que cae fuera del rango no se cuenta
    Cuando el administrador consulta el reporte del 10 al 16 de junio
    Entonces no hay proyectos, discusiones ni comentarios
    Y los usuarios activos son 0
    Y los nuevos registros son 1, el de "cami"

  # ---- Criterio 2: crecimiento de nuevos registros por semana o por mes

  @positivo @HU-19
  Escenario: Crecimiento por semana, con acumulado
    Cuando el administrador consulta el reporte del 3 al 16 de junio agrupado por "SEMANA"
    Entonces el crecimiento tiene 2 periodos
    Y la semana del 3 de junio tiene 2 nuevos y acumulado 2
    Y la semana del 10 de junio tiene 1 nuevo y acumulado 3

  @positivo @HU-19
  Escenario: Sin indicar agrupación, se agrupa por semana
    Cuando el administrador consulta el reporte del 3 al 16 de junio sin indicar agrupación
    Entonces la agrupación es "SEMANA"

  @positivo @HU-19
  Escenario: Crecimiento por mes cuando el rango cruza de mes
    Dado que un usuario se registró el 25 de junio y otro el 2 de julio
    Cuando el administrador consulta el reporte del 24 de junio al 7 de julio agrupado por "MES"
    Entonces el crecimiento tiene el periodo de junio con 1 nuevo
    Y el periodo de julio con 1 nuevo y acumulado 2

  # ---- Criterio 3: rango inválido

  @excepcional @HU-19
  Escenario: La fecha final anterior a la inicial se rechaza
    Cuando el administrador consulta el reporte con desde "2019-06-16" y hasta "2019-06-03"
    Entonces el sistema responde 400 con el código "RANGO_FECHAS_INVALIDO"
    Y no se calcula ninguna métrica

  @excepcional @HU-19
  Escenario: Sin fechas se rechaza
    Cuando el administrador consulta el reporte sin indicar las fechas
    Entonces el sistema responde 400 con el código "RANGO_FECHAS_REQUERIDO"

  @excepcional @HU-19
  Escenario: Un rango de más de 92 días se rechaza
    Cuando el administrador consulta el reporte del 1 de enero al 30 de junio
    Entonces el sistema responde 400 con el código "RANGO_FECHAS_DEMASIADO_AMPLIO"

  @excepcional @HU-19
  Escenario: Una agrupación desconocida se rechaza
    Cuando el administrador consulta el reporte agrupado por "ANIO"
    Entonces el sistema responde 400 con el código "AGRUPACION_INVALIDA"

  @excepcional @HU-19
  Escenario: Una fecha con formato incorrecto se rechaza
    Cuando el administrador consulta el reporte con desde "ayer"
    Entonces el sistema responde 400 con el código "PARAMETRO_INVALIDO"

  # ---- Criterio 4: solo administradores

  @excepcional @HU-19
  Esquema del escenario: Quien no es administrador recibe acceso denegado
    Dado que existe un usuario con el rol "<rol>" y sesión iniciada
    Cuando intenta consultar el reporte de actividad
    Entonces el sistema responde 403

    Ejemplos:
      | rol           |
      | DESARROLLADOR |
      | MODERADOR     |
      | VISITANTE     |

  @excepcional @HU-19
  Escenario: Un desarrollador que intenta consultar deja constancia
    Dado que "luis" es desarrollador y tiene sesión
    Cuando intenta consultar el reporte
    Entonces el sistema responde 403 con el código "ACCESO_DENEGADO"
    Y el intento queda registrado como "ACCESO_DENEGADO"

  @excepcional @HU-19
  Escenario: Un administrador sin segundo factor no puede consultar
    Dado que existe un administrador sin MFA inscrito y con sesión iniciada
    Cuando intenta consultar el reporte
    Entonces el sistema responde 403

  @excepcional @HU-19
  Escenario: Sin sesión no se puede consultar
    Cuando un visitante sin sesión intenta consultar el reporte
    Entonces el sistema responde 401 con el código "AUTH_REQUERIDA"

  @positivo @HU-19
  Escenario: El permiso del reporte es solo del administrador
    Dado que la migración V13 está aplicada
    Entonces el permiso "metricas:consultar" lo tiene exactamente el rol "ADMIN"

  # ---- Criterio 5: estado vacío

  @excepcional @HU-19
  Escenario: Sin actividad en el rango se muestra un estado vacío
    Cuando el administrador consulta el reporte del 1 al 14 de enero de 2018
    Entonces el sistema responde 200 con "sinActividad" verdadero
    Y todos los totales son 0
    Y el crecimiento viene vacío

  # ---- Cálculo bajo demanda (ADR-009)

  @positivo @HU-19
  Escenario: Cada día cerrado se calcula una sola vez
    Cuando el administrador consulta el mismo rango dos veces
    Entonces se calculan los 14 días en la primera consulta
    Y la segunda no recalcula ninguno

  @positivo @HU-19
  Escenario: Un día calculado antes de terminar se recalcula
    Dado que el 4 de junio se calculó a la 1:00 de ese día
    Y que después llegó otro comentario del 4 de junio
    Cuando el administrador consulta el reporte
    Entonces el comentario nuevo ya cuenta

  @excepcional @HU-19
  Escenario: No se consolidan días futuros
    Cuando se consolida un rango de enero de 2099
    Entonces no se crea ninguna métrica y no falla

  @positivo @HU-19
  Escenario: La definición de "activo" no se separa de la de V11
    Cuando se compara cada día del rango
    Entonces los activos en vivo coinciden con los consolidados
```

| Escenario | Prueba automática |
|---|---|
| Activos, proyectos, discusiones y comentarios | `ReporteActividadIT` · «cuenta usuarios activos (distintos), proyectos, discusiones y comentarios del rango»; `ConsultarReporteActividadTest` |
| Un activo varios días cuenta una vez | `ReporteActividadIT` · «un usuario activo varios dias del rango cuenta una sola vez» |
| Lo de fuera del rango no se cuenta | `ReporteActividadIT` · «lo que cae fuera del rango no se cuenta» |
| Crecimiento por semana | `ReporteActividadIT` · «por semana: nuevos de cada semana (lunes) y acumulado»; `ReporteActividadTest` |
| Agrupación por defecto | `ReporteActividadIT` · «sin indicar agrupacion, agrupa por semana»; `AgrupacionTest` |
| Crecimiento por mes | `ReporteActividadIT` · «por mes: el rango cruza de junio a julio…» |
| Fecha final anterior | `ReporteActividadIT` · «la fecha final anterior a la inicial responde 400…»; `RangoFechasReporteTest`; `ConsultarReporteActividadTest` |
| Sin fechas | `ReporteActividadIT` · «sin fechas responde 400 RANGO_FECHAS_REQUERIDO»; `RangoFechasReporteTest` |
| Más de 92 días | `ReporteActividadIT` · «un rango de mas de 92 dias…»; `RangoFechasReporteTest` |
| Agrupación desconocida | `ReporteActividadIT` · «una agrupacion desconocida responde 400…»; `AgrupacionTest` |
| Formato de fecha incorrecto | `ReporteActividadIT` · «una fecha con formato incorrecto responde 400 PARAMETRO_INVALIDO» |
| No calcula nada con rango inválido | `ReporteActividadIT` · «un rango rechazado no calcula nada» |
| Desarrollador / moderador / visitante → 403 | `ReporteActividadIT` · «un desarrollador recibe 403…», «un moderador con MFA tampoco…», «un visitante recibe 403» |
| Admin sin MFA → 403 | `ReporteActividadIT` · «un administrador SIN MFA inscrito recibe 403…» |
| Sin sesión → 401 | `ReporteActividadIT` · «sin token recibe 401» |
| Permiso solo del ADMIN | `ReporteActividadIT` · «V13 concede metricas:consultar exactamente a ADMIN»; `EsquemaIT` |
| Estado vacío | `ReporteActividadIT` · «un rango sin actividad responde 200 con totales en 0…»; `ReporteActividadTest`; `ConsultarReporteActividadTest` |
| Un día se calcula una sola vez | `ReporteActividadIT` · «la primera consulta calcula los 14 dias y la segunda no recalcula…» |
| Recalcula lo que quedó a medias | `ReporteActividadIT` · «un dia calculado antes de que terminara se recalcula…» |
| No consolida el futuro | `ReporteActividadIT` · «el procedimiento no consolida dias futuros…», «…rechaza un rango invertido» |
| Misma definición de activo que V11 | `ReporteActividadIT` · «coincide, dia por dia, con usuarios_activos que consolida V11…» |

## Cobertura de los criterios

| Historia | Criterios de aceptación | Escenarios | Positivos | Excepcionales |
|---|---|---|---|---|
| HU-19 | 5 | 21 | 10 | 11 |

Cada criterio tiene al menos un escenario; el de «solo administradores» tiene uno por cada rol
que no lo es, y el cálculo bajo demanda (ADR-009) tiene los suyos aparte.
