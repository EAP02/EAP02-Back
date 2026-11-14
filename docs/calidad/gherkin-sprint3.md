# Criterios de aceptación en Gherkin — HU-20 (registro de auditoría)

Continúa [`gherkin-sprint2.md`](gherkin-sprint2.md) con las mismas convenciones:
`@positivo` / `@excepcional`, códigos HTTP y de error del contrato (ADR-005) y una tabla que
enlaza cada escenario con la prueba que lo verifica hoy. La automatización directa con
Cucumber es del sprint 3 de Calidad (§3.7); mientras tanto cada escenario tiene su
equivalente en JUnit con la base real.

Verificación manual en Swagger: [`../pruebas-sprint3-hu20.md`](../pruebas-sprint3-hu20.md).

---

## HU-20 · Registro de auditoría

```gherkin
# language: es
Característica: Registro de auditoría de acciones sensibles
  Como administrador
  Quiero un registro de las acciones sensibles
  Para tener trazabilidad de lo que ocurre en la plataforma

  Antecedentes:
    Dado que existe un administrador con el segundo factor inscrito y sesión iniciada

  # ---- Criterio 1: cada inicio de sesión, exitoso o fallido, queda registrado

  @positivo @HU-20
  Escenario: Un inicio de sesión exitoso queda registrado
    Dado que "ana" inicia sesión con sus credenciales
    Cuando el administrador consulta el registro filtrando por "INICIO_SESION" y por "ana"
    Entonces el sistema responde 200 con un registro
    Y el registro indica que "ana" fue quien actuó
    Y el objeto afectado es su usuario

  @excepcional @HU-20
  Escenario: Un inicio de sesión fallido queda registrado con el correo intentado
    Dado que alguien intenta iniciar sesión con la contraseña equivocada de "ana"
    Cuando el administrador consulta el registro filtrando por "INICIO_SESION_FALLIDO"
    Entonces el primer registro tiene como objeto el correo de "ana"
    Y el registro no tiene actor

  # ---- Criterio 2: cambios de rol y eliminaciones de contenido

  @positivo @HU-20
  Escenario: Asignar un rol queda registrado con quién lo asignó
    Dado que el administrador asigna el rol "MODERADOR" a "luis"
    Cuando consulta el registro de la tabla "usuario_rol" filtrando por su propio id
    Entonces aparece un registro "INSERT" sobre el rol de "luis"
    Y el estado posterior incluye quién lo asignó

  @positivo @HU-20
  Escenario: Retirar un rol queda registrado con el estado anterior
    Dado que se retira un rol a "luis"
    Cuando el administrador consulta los registros "DELETE" de la tabla "usuario_rol"
    Entonces aparece un registro con el estado anterior del rol

  @positivo @HU-20
  Escenario: El autor que elimina su propio comentario deja registro
    Dado que "ana" comentó en una discusión
    Cuando "ana" elimina su comentario
    Entonces el registro contiene una operación "DELETE" de "ana" sobre ese comentario

  @positivo @HU-20
  Escenario: Un administrador que elimina un comentario ajeno deja registro y de quién era
    Dado que "ana" comentó en una discusión
    Cuando el administrador elimina el comentario de "ana"
    Entonces el registro contiene una operación "DELETE" del administrador sobre ese comentario
    Y el estado anterior indica que el autor era "ana"

  # ---- Criterio 3: cada registro dice quién, qué, cuándo y sobre qué objeto

  @positivo @HU-20
  Escenario: Un acceso denegado trae quién, qué, cuándo, sobre qué y la traza
    Dado que "luis", sin permiso, intentó consultar el registro
    Cuando el administrador consulta los registros "ACCESO_DENEGADO" de "luis"
    Entonces el registro incluye el nombre de usuario de "luis"
    Y la acción "ACCESO_DENEGADO"
    Y la fecha
    Y el objeto "GET /api/v1/auditoria"
    Y el identificador de traza

  # ---- Criterio 4: no se almacenan contraseñas ni tokens

  @excepcional @HU-20
  Escenario: El registro no contiene contraseñas ni tokens
    Dado que se han hecho inicios de sesión correctos y uno con contraseña equivocada
    Cuando el administrador consulta el registro
    Entonces la respuesta no contiene ninguna contraseña ni ningún token
    Y ninguna fila de la tabla de auditoría los contiene

  @excepcional @HU-20
  Escenario: Un cambio sobre un usuario no guarda sus credenciales
    Dado que cambia el estado de MFA de "luis"
    Cuando el administrador consulta los registros "UPDATE" de la tabla "usuario"
    Entonces aparece el id de "luis"
    Y no aparecen ni el hash de la contraseña ni el secreto de MFA

  # ---- Criterio 5: solo los administradores pueden consultar

  @excepcional @HU-20
  Esquema del escenario: Quien no es administrador recibe acceso denegado
    Dado que existe un usuario con el rol "<rol>" y sesión iniciada
    Cuando intenta consultar el registro de auditoría
    Entonces el sistema responde 403

    Ejemplos:
      | rol           |
      | DESARROLLADOR |
      | MODERADOR     |
      | VISITANTE     |

  @excepcional @HU-20
  Escenario: Un desarrollador que intenta consultar deja constancia
    Dado que "luis" es desarrollador y tiene sesión
    Cuando intenta consultar el registro
    Entonces el sistema responde 403 con el código "ACCESO_DENEGADO"
    Y el intento queda registrado como "ACCESO_DENEGADO"

  @excepcional @HU-20
  Escenario: Un administrador sin segundo factor no puede consultar
    Dado que existe un administrador sin MFA inscrito y con sesión iniciada
    Cuando intenta consultar el registro
    Entonces el sistema responde 403

  @excepcional @HU-20
  Escenario: Sin sesión no se puede consultar
    Cuando un visitante sin sesión intenta consultar el registro
    Entonces el sistema responde 401 con el código "AUTH_REQUERIDA"

  @excepcional @HU-20
  Escenario: El registro no se puede modificar por la API
    Cuando el administrador intenta borrar o crear registros por la API
    Entonces el sistema responde 405

  # ---- Consulta: paginación, rango y filtros

  @positivo @HU-20
  Escenario: Los registros se paginan del más reciente al más antiguo
    Dado que "ana" ha iniciado sesión tres veces
    Cuando el administrador pide páginas de 2 registros de sus inicios de sesión
    Entonces la primera página tiene 2 registros y la segunda, 1
    Y el total es 3 en 2 páginas
    Y el identificador del primero es mayor que el del segundo

  @positivo @HU-20
  Escenario: El rango de fechas incluye el día de hoy
    Dado que "ana" inició sesión hoy
    Cuando el administrador consulta con desde y hasta iguales a la fecha de hoy
    Entonces el registro de "ana" aparece

  @excepcional @HU-20
  Escenario: Sin actividad en el rango se muestra un estado vacío
    Cuando el administrador consulta un rango de fechas sin actividad
    Entonces el sistema responde 200 con una lista vacía y total 0

  @excepcional @HU-20
  Escenario: La fecha final anterior a la inicial se rechaza
    Cuando el administrador consulta con desde "2026-11-13" y hasta "2026-11-01"
    Entonces el sistema responde 400 con el código "RANGO_FECHAS_INVALIDO"

  @excepcional @HU-20
  Escenario: Una operación desconocida se rechaza y se listan las válidas
    Cuando el administrador consulta con la operación "BORRAR_TODO"
    Entonces el sistema responde 400 con el código "AUDITORIA_OPERACION_INVALIDA"
    Y el mensaje lista las operaciones válidas

  @excepcional @HU-20
  Esquema del escenario: Un tamaño de página fuera de 1 a 100 se rechaza
    Cuando el administrador pide páginas de <tamano> registros
    Entonces el sistema responde 400 con el código "PAGINACION_INVALIDA"

    Ejemplos:
      | tamano |
      | 0      |
      | 101    |

  @excepcional @HU-20
  Esquema del escenario: Un parámetro con formato incorrecto se rechaza
    Cuando el administrador consulta con <parametro> igual a "<valor>"
    Entonces el sistema responde 400 con el código "PARAMETRO_INVALIDO"

    Ejemplos:
      | parametro | valor        |
      | actorId   | no-es-un-uuid |
      | desde     | ayer         |
```

| Escenario | Prueba automática |
|---|---|
| Inicio exitoso registrado | `AuditoriaIT` · «un inicio exitoso aparece con quien, que, cuando y sobre que objeto» |
| Inicio fallido registrado | `AuditoriaIT` · «un inicio fallido tambien queda, con el correo intentado y sin actor» |
| Asignar un rol | `AuditoriaIT` · «asignar un rol deja una fila en usuario_rol con quien lo asigno»; `BaseDatosSprint3IT` (trigger V10) |
| Retirar un rol | `AuditoriaIT` · «quitar un rol tambien queda, con el estado anterior» |
| El autor elimina lo suyo | `AuditoriaIT` · «el autor que elimina su propio comentario deja registro» |
| El administrador elimina lo ajeno | `AuditoriaIT` · «la intervencion de un administrador sobre un comentario ajeno…» |
| Quién, qué, cuándo, objeto y traza | `AuditoriaIT` · «un acceso denegado trae actor, accion, fecha, objeto (la ruta) y la traza»; `ConsultarAuditoriaTest` |
| Sin contraseñas ni tokens | `AuditoriaIT` · «ni la tabla ni la respuesta contienen la contrasena ni el token» |
| Cambio de usuario sin credenciales | `AuditoriaIT` · «un cambio en usuario solo guarda id, estado y mfa…» |
| Desarrollador / moderador / visitante → 403 | `AuditoriaIT` · «un desarrollador recibe 403…», «un moderador con MFA tampoco puede…», «un visitante recibe 403» |
| Admin sin MFA → 403 | `AuditoriaIT` · «un administrador SIN MFA inscrito recibe 403…» |
| Sin sesión → 401 | `AuditoriaIT` · «sin token recibe 401» |
| Solo lectura (405) | `AuditoriaIT` · «el registro es de solo lectura…» |
| Paginación | `AuditoriaIT` · «pagina del mas reciente al mas antiguo y cuenta el total»; `PaginacionTest` |
| Rango con hoy | `AuditoriaIT` · «el rango incluye el dia de hoy (hora de Colombia)»; `RangoFechasTest` |
| Estado vacío | `AuditoriaIT` · «sin actividad en el rango devuelve un estado vacio…»; `ConsultarAuditoriaTest` |
| Rango invertido | `AuditoriaIT` · «la fecha final anterior a la inicial responde 400…»; `RangoFechasTest` |
| Operación desconocida | `AuditoriaIT` · «una operacion desconocida responde 400…»; `FiltroAuditoriaTest` |
| Tamaño de página inválido | `AuditoriaIT` · «un tamano de pagina fuera de 1..100…»; `PaginacionTest` |
| Parámetro con formato incorrecto | `AuditoriaIT` · «un id o una fecha con formato incorrecto responde 400…» |

## Cobertura de los criterios

| Historia | Criterios de aceptación | Escenarios | Positivos | Excepcionales |
|---|---|---|---|---|
| HU-20 | 5 (los 4 de la historia más el acceso restringido) | 21 | 8 | 13 |

Cada criterio tiene al menos un escenario positivo o excepcional que lo ejercita, y el
criterio «solo administradores» tiene escenarios para cada rol que no lo es.
