# Plan humano de implementacion en C#

Proyecto: Sistema de Generacion de Horarios UdeO/UTP  
Caracter: documento explicativo para un equipo humano junior de 8 personas  
Enfoque: C#/.NET para servidor, dominio, motor, infraestructura, reportes y pruebas; React + TypeScript para interfaz web  
Arquitectura: arquitectura por capas con modulos verticales funcionales

Este documento organiza el producto completo desde cero. No divide el trabajo por
tecnologia aislada. Cada persona es responsable de uno o varios modulos
funcionales de forma vertical: base de datos, dominio, aplicacion, API,
frontend, pruebas y documentacion del mismo modulo.

## 1. Objetivo general

Construir un sistema academico completo para administrar datos, importar
catalogos, validar insumos, generar horarios de clases y examenes, optimizar la
calidad del horario, permitir edicion manual controlada, publicar resultados,
registrar sustituciones y exportar reportes PDF/XLSX.

El sistema debe quedar listo para uso operativo y para defensa academica. No se
plantea como prototipo ni como maqueta: las fases son internas para ordenar el
trabajo, pero el entregable final debe integrar todos los modulos.

## 2. Objetivos medibles

| Objetivo | Medida de aceptacion |
|---|---|
| Levantar el sistema completo | `docker compose up --build` deja PostgreSQL, API y web accesibles |
| Compilar backend C# | `dotnet test` pasa en la solucion completa |
| Compilar frontend | `pnpm run build` pasa en `web/horarios-web` |
| Reproducir base de datos | migraciones aplican en PostgreSQL limpio sin pasos manuales |
| Proteger rutas | pruebas cubren `401`, `403`, usuario inactivo y rol incorrecto |
| Cargar datos academicos | importacion valida persiste; importacion invalida no cambia catalogos |
| Validar antes de generar | errores bloqueantes quedan en `pre_validation_issue` |
| Generar horarios | plan valido produce `schedule_run`, asignaciones, no asignables y puntuacion |
| Explicar infeasibilidad | curso sin docente, aula insuficiente o laboratorio faltante genera razon clara |
| Reproducir motor | misma entrada + misma `seed` + misma `engineVersion` produce mismo resultado |
| Optimizar calidad | recocido simulado no empeora el mejor horario constructivo en benchmarks |
| Editar manualmente | sesion fijada no se mueve y LNS toca solo vecindario afectado |
| Publicar | plan `APPROVED` puede pasar a `LOCKED`; `LOCKED` no cambia plantilla base |
| Exportar | PDF/XLSX leen datos persistidos, no recalculan el motor |
| Registrar sustituciones | sustitucion vive como evento separado y no modifica `schedule_assignment` |
| Defender rendimiento | fixtures `small`, `medium`, `large`, `infeasible-room`, `infeasible-teacher` generan metricas |

## 3. Alcance funcional completo

Debe construirse todo lo siguiente:

1. Seguridad y usuarios: login, JWT, roles `SUPERADMIN`, `ADMIN`, `TEACHER`,
   `STUDENT`, usuario actual, permisos por endpoint y por pantalla.
2. Catalogos academicos: carreras, pensums, cursos, cursos por pensum,
   cohortes, areas comunes y reglas de carrera/pensum.
3. Docentes: docentes, cursos que puede impartir, disponibilidad, prioridad,
   carga minima/maxima y relacion docente-carrera-jornada.
4. Tiempo, aulas y recursos: jornadas, bloques, descansos fijos, aulas,
   capacidad, tipo, recursos de aula y recursos requeridos por curso.
5. Importacion CSV/XLSX: validacion de hojas, columnas, tipos, referencias por
   codigo, errores por fila/celda y transaccion atomica.
6. Planes de horario: crear plan, validar, generar, revisar resultado, aprobar,
   bloquear, archivar y consultar corridas.
7. Motor: dominio puro, prevalidacion, creacion de sesiones, area comun,
   heuristica constructiva, recocido simulado, puntuacion y no asignables.
8. Edicion manual: mover sesion, fijar la edicion, detectar conflicto, reparar
   vecindario con LNS, guardar nuevo `schedule_run` e idempotencia.
9. Vistas web: login, shell administrativo, dashboard, catalogos, importacion,
   disponibilidad docente, planes, grilla, conflictos, reportes, sustituciones,
   horario docente y horario estudiante.
10. Reportes: PDF y XLSX por cohorte, docente, aula, conflictos y metadatos.
11. Sustituciones: docente sustituto temporal/permanente sobre horario
   publicado, validando solapes.
12. Pruebas, benchmarks, CI, Docker, backups y documentacion de entrega.

Fuera de alcance inicial:

- Motor externo de timetabling: OR-Tools, Timefold, OptaPlanner, UniTime, FET o similares.
- Horario individual para estudiantes atrasados.
- Calendario semana por semana.
- Aplicacion movil nativa.
- Integracion real con sistema academico externo.

## 4. Decisiones tecnicas

### 4.1 Pila objetivo

| Area | Decision |
|---|---|
| Lenguaje servidor | C# moderno con nullable reference types |
| Plataforma servidor | .NET 10 LTS |
| API | ASP.NET Core Web API |
| Base de datos | PostgreSQL 16+ |
| Persistencia | EF Core + Npgsql; SQL parametrizado cuando EF no sea claro |
| Autenticacion | JWT Bearer de ASP.NET Core |
| Hash de contrasenas | `PasswordHasher<TUser>` o BCrypt.Net-Next si se evita Identity completo |
| Pruebas backend | xUnit recomendado por simplicidad |
| Pruebas con PostgreSQL | Testcontainers si Docker esta disponible |
| Frontend | React + TypeScript + pnpm + dnd-kit |
| Excel | ClosedXML o libreria equivalente con licencia compatible |
| PDF | PDFsharp/MigraDoc o libreria equivalente con licencia compatible |
| Contenedores | Docker Compose |
| Integracion continua | GitHub Actions |

Verificacion externa al 26 de junio de 2026: la politica oficial de Microsoft
lista .NET 10 como LTS activo con fin de soporte el 14 de noviembre de 2028;
.NET 8 termina soporte el 10 de noviembre de 2026. Fuente:
https://dotnet.microsoft.com/en-us/platform/support/policy/dotnet-core

### 4.2 Por que no cambiar el frontend a Blazor

C# sustituye el servidor Java, el dominio, el motor, la infraestructura y las
pruebas. La interfaz web puede mantenerse en React + TypeScript porque ya encaja
con `dnd-kit`, grillas interactivas, componentes ligeros y el contrato REST. Un
cambio a Blazor aumenta el alcance sin mejorar el motor ni la base de datos.

Si una regla academica exige C# tambien en la interfaz, se puede evaluar Blazor
como decision separada, pero no es necesario para cumplir este plan.

## 5. Arquitectura por capas

Estructura recomendada:

```text
horarios-csharp/
  Horarios.sln
  src/
    Horarios.Domain/
    Horarios.Solver/
    Horarios.Application/
    Horarios.Infrastructure/
    Horarios.Reports/
    Horarios.Api/
  tests/
    Horarios.Domain.Tests/
    Horarios.Solver.Tests/
    Horarios.Application.Tests/
    Horarios.Infrastructure.Tests/
    Horarios.Api.Tests/
    Horarios.Reports.Tests/
    Horarios.TestKit/
  web/
    horarios-web/
  docs/
```

Dependencias permitidas:

```text
Horarios.Domain         -> ninguna capa del sistema
Horarios.Solver         -> Horarios.Domain
Horarios.Application    -> Horarios.Domain, Horarios.Solver
Horarios.Infrastructure -> Horarios.Domain, Horarios.Application, EF Core, Npgsql
Horarios.Reports        -> Horarios.Application, Horarios.Infrastructure
Horarios.Api            -> Horarios.Application, Horarios.Infrastructure, Horarios.Reports
Horarios.TestKit        -> Horarios.Domain, Horarios.Solver
web/horarios-web        -> REST API
```

Reglas:

- `Domain` no conoce EF Core, ASP.NET Core, HTTP, archivos ni PostgreSQL.
- `Solver` no conoce EF Core, ASP.NET Core, usuarios, JWT ni reportes.
- `Application` contiene casos de uso, transiciones de estado, DTOs internos e
  interfaces de puertos.
- `Infrastructure` implementa `DbContext`, migraciones, repositorios, consultas
  y mapeo DB -> dominio.
- `Reports` lee corridas persistidas y genera archivos; nunca recalcula horarios.
- `Api` recibe HTTP, valida request, aplica permisos y llama casos de uso.
- `web` consume API; nunca guarda secretos ni reemplaza autorizacion del backend.

Flujo base:

```text
Controller -> caso de uso -> repositorio/consulta -> dominio/motor -> persistencia -> DTO -> frontend
```

Si una clase necesita al mismo tiempo `DbContext` y `AnnealingOptimizer`, debe
vivir en `Application` como orquestador o dividirse. El motor no debe conocer la
base de datos.

## 6. Modulos verticales del producto

Los 8 propietarios trabajan por modulo funcional. Cada modulo entrega:

- tablas y migraciones;
- entidades/configuraciones EF Core;
- reglas de dominio si aplica;
- caso de uso de aplicacion;
- endpoints;
- componentes/pantallas frontend;
- pruebas unitarias/integracion;
- documentacion breve y verificacion.

| Rol | Modulo vertical | Persona lider tecnica |
|---|---|---|
| R1 | Seguridad, usuarios, plataforma base e integracion | si, tambien codifica |
| R2 | Catalogos academicos y areas comunes |
| R3 | Docentes, disponibilidad y carga |
| R4 | Tiempo, aulas, recursos y restricciones fisicas |
| R5 | Importacion CSV/XLSX y calidad de datos |
| R6 | Planes de horario y motor constructivo |
| R7 | Optimizacion, edicion manual LNS y grilla |
| R8 | Publicacion, reportes, sustituciones, QA y entrega |

R1 coordina arquitectura, revisiones y decisiones transversales, pero no es una
persona administrativa. R1 tambien implementa modulo vertical de seguridad y
plataforma.

## 7. Contratos comunes entre modulos

### 7.1 Estados del plan

```text
DRAFT
VALIDATING
INVALID_INPUT
GENERATING
GENERATED
GENERATED_WITH_CONFLICTS
APPROVED
LOCKED
ARCHIVED
```

Transiciones principales:

| Accion | Desde | Hacia |
|---|---|---|
| crear plan | n/a | `DRAFT` |
| validar sin errores | `DRAFT`, `INVALID_INPUT`, `GENERATED`, `GENERATED_WITH_CONFLICTS` | estado previo |
| validar con errores | `DRAFT`, `INVALID_INPUT`, `GENERATED`, `GENERATED_WITH_CONFLICTS` | `INVALID_INPUT` |
| generar inicio | `DRAFT`, `INVALID_INPUT`, `GENERATED`, `GENERATED_WITH_CONFLICTS` | `GENERATING` |
| generar sin conflictos | `GENERATING` | `GENERATED` |
| generar parcial/conflictos | `GENERATING` | `GENERATED_WITH_CONFLICTS` |
| aprobar | `GENERATED`, `GENERATED_WITH_CONFLICTS` | `APPROVED` |
| edicion manual | `APPROVED` | `APPROVED` |
| bloquear/publicar | `APPROVED` | `LOCKED` |
| sustitucion | `LOCKED` | `LOCKED` |
| archivar | cualquiera excepto `VALIDATING`, `GENERATING` | `ARCHIVED` |

### 7.2 Respuesta de error comun

```json
{
  "code": "VALIDATION_FAILED",
  "message": "La solicitud tiene campos invalidos.",
  "details": {
    "fields": [
      {
        "field": "code",
        "message": "El codigo es obligatorio."
      }
    ]
  },
  "requestId": "req-20260115-0001"
}
```

Codigos estables minimos:

- `AUTH_INVALID_CREDENTIALS`
- `AUTH_TOKEN_MISSING`
- `AUTH_TOKEN_INVALID`
- `AUTH_TOKEN_EXPIRED`
- `FORBIDDEN`
- `RESOURCE_NOT_FOUND`
- `VALIDATION_FAILED`
- `DUPLICATE_CODE`
- `STATE_TRANSITION_NOT_ALLOWED`
- `IDEMPOTENCY_CONFLICT`
- `IMPORT_INVALID_FILE`
- `IMPORT_HAS_ERRORS`
- `COURSE_WITHOUT_TEACHER`
- `LAB_WITHOUT_ROOM`
- `JOURNEY_WITHOUT_ENOUGH_BLOCKS`
- `COMMON_AREA_WITHOUT_CAPACITY`
- `TEACHER_WITHOUT_AVAILABILITY`
- `FIXED_BREAK_OUT_OF_RANGE`
- `SOLVER_FAILED`
- `MANUAL_EDIT_REJECTED_BY_STATE`
- `MANUAL_EDIT_REJECTED_BY_INPUT`
- `SUBSTITUTE_TEACHER_CONFLICT`

### 7.3 Paginacion comun

Query:

```text
page=0
size=20
sort=code,asc
q=texto
active=true
```

Response:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

### 7.4 Endpoints clave

| Endpoint | Modulo owner |
|---|---|
| `POST /api/auth/login` | R1 |
| `GET /api/me` | R1 |
| `GET/POST/PATCH /api/catalog/careers` | R2 |
| `GET/POST/PATCH /api/catalog/curricula` | R2 |
| `GET/POST/PATCH /api/catalog/courses` | R2 |
| `GET/POST/PATCH /api/catalog/cohorts` | R2 |
| `GET/POST/PATCH /api/catalog/common-areas` | R2 |
| `GET/POST/PATCH /api/catalog/teachers` | R3 |
| `GET/POST/PATCH /api/catalog/teacher-courses` | R3 |
| `GET/PUT /api/teacher/availability` | R3 |
| `GET/POST/PATCH /api/catalog/journeys` | R4 |
| `GET/POST/PATCH /api/catalog/fixed-breaks` | R4 |
| `GET/POST/PATCH /api/catalog/rooms` | R4 |
| `GET/POST/PATCH /api/catalog/resources` | R4 |
| `POST /api/imports/academic-data` | R5 |
| `GET /api/imports/{id}/errors` | R5 |
| `GET/POST /api/schedule-plans` | R6 |
| `POST /api/schedule-plans/{id}/validate` | R6 |
| `POST /api/schedule-plans/{id}/generate` | R6 |
| `GET /api/schedule-plans/{id}/result` | R6/R7 |
| `GET /api/schedule-plans/{id}/violations` | R6/R7 |
| `POST /api/schedule-plans/{id}/approve` | R6 |
| `POST /api/schedule-plans/{id}/manual-edits` | R7 |
| `GET /api/reports/schedule-plans/{id}.pdf` | R8 |
| `GET /api/reports/schedule-plans/{id}.xlsx` | R8 |
| `POST /api/substitutions` | R8 |
| `GET /api/substitutions` | R8 |
| `GET /api/public/schedules/cohorts/{id}` | R8 |
| `GET /api/teacher/schedule` | R8/R3 |

## 8. Modelo de datos minimo

### 8.1 Enums

- `user_role`: `SUPERADMIN`, `ADMIN`, `TEACHER`, `STUDENT`
- `schedule_type`: `CLASSES`, `EXAMS`
- `plan_status`: `DRAFT`, `VALIDATING`, `INVALID_INPUT`, `GENERATING`,
  `GENERATED`, `GENERATED_WITH_CONFLICTS`, `APPROVED`, `LOCKED`, `ARCHIVED`
- `issue_severity`: `ERROR`, `WARNING`
- `assignment_status`: `ASSIGNED`, `UNASSIGNED`
- `room_type`: `THEORY`, `LAB`, `MIXED`
- `schedule_run_status`: `RUNNING`, `COMPLETED`, `COMPLETED_WITH_CONFLICTS`, `FAILED`
- `manual_edit_status`: `APPLIED_CLEAN`, `APPLIED_WITH_REPAIR`,
  `APPLIED_WITH_REMAINING_CONFLICTS`, `REJECTED_BY_STATE`, `REJECTED_BY_INPUT`
- `import_batch_status`: `UPLOADED`, `VALIDATING`, `VALID`, `INVALID`,
  `IMPORTED`, `FAILED`

### 8.2 Tablas por owner

| Owner | Tablas |
|---|---|
| R1 | `app_user` |
| R2 | `career`, `curriculum`, `course`, `curriculum_course`, `cohort`, `common_area_rule`, `common_area_career` |
| R3 | `teacher`, `teacher_course`, `teacher_availability`, `teacher_career_journey` |
| R4 | `journey`, `time_block`, `fixed_break`, `room`, `resource`, `room_resource`, `course_required_resource` |
| R5 | `import_batch`, `import_error` |
| R6 | `schedule_plan`, `schedule_session_group`, `schedule_session`, `schedule_session_cohort`, `schedule_run`, `schedule_assignment`, `pre_validation_issue`, `section_suggestion` |
| R7 | `manual_edit`, `schedule_violation` |
| R8 | `substitution_event`, vista `exam_plan` |

### 8.3 Reglas que viven en motor, no en DB

- solape por docente;
- solape por aula;
- solape por cohorte;
- capacidad combinada de area comun durante asignacion;
- caminata entre aulas;
- ventanas muertas;
- contiguidad de bloques;
- balance de carga docente;
- reparacion LNS.

La DB protege claves foraneas, unicidad, `CHECK`, idempotencia e indices. El
motor decide horarios.

## 9. Orden global de construccion

Ruta critica:

```text
0. Alineacion y contratos
1. Solution .NET + CI + Docker base
2. Migraciones base y DbContext
3. Seguridad/login y catalogos base
4. Tiempo/aulas/docentes
5. Importacion
6. Dominio + solver constructivo
7. Planes, validacion, generacion y resultado
8. Frontend operativo de catalogos/importacion/planes
9. Recocido simulado + benchmarks
10. Edicion manual LNS + grilla
11. Reportes, publicacion, sustituciones
12. Hardening, QA, documentacion de defensa
```

Regla de integracion:

- Cada modulo puede avanzar con mocks locales solo si el contrato ya existe.
- El mock se elimina cuando el endpoint real del modulo dependiente queda listo.
- Ningun modulo se considera terminado si solo existe backend o solo existe UI.
- Cada vertical termina con verificacion runnable.

## 10. Plan por fases

Duracion orientativa para 8 personas junior: 16 a 20 semanas. Las fases se
pueden solapar cuando las dependencias esten cerradas.

### Fase 0 - Alineacion

Objetivo: que todos entiendan el mismo producto.

| ID | Owner | Tarea | Depende de | Salida medible |
|---|---|---|---|---|
| F0.1 | R1 | Crear documento corto de decisiones C# | nada | `docs/decisiones-csharp.md` |
| F0.2 | Todos | Leer alcance, DB, API, motor, UI y benchmarks | F0.1 | lista de dudas cerrada |
| F0.3 | R1 | Congelar estructura de capas y nombres de proyectos | F0.1 | `Horarios.sln` planificada |
| F0.4 | R1/R8 | Definir convenciones de ramas, PR, revision y DoD | F0.1 | guia de trabajo |
| F0.5 | Todos | Dividir primer lote de tareas por vertical | F0.2 | tablero con owners |

Criterio de salida:

- Todos conocen roles, estados, endpoints, tablas y algoritmo.
- No queda duda abierta sobre motores externos: no se usan.
- Cada persona sabe su modulo vertical.

### Fase 1 - Fundacion tecnica

Objetivo: repo C# compilable con CI minimo.

| ID | Owner | Tarea | Depende de | Salida medible |
|---|---|---|---|---|
| F1.1 | R1 | Crear `Horarios.sln` y proyectos por capa | F0 | `dotnet build` |
| F1.2 | R1 | Activar nullable, warnings, analyzers basicos | F1.1 | build sin warnings criticos |
| F1.3 | R8 | Crear proyectos de prueba | F1.1 | `dotnet test` sin fallos |
| F1.4 | R1 | API base con healthcheck y OpenAPI | F1.1 | `GET /health` |
| F1.5 | R8 | Docker Compose con PostgreSQL/API/web | F1.4 | compose levanta |
| F1.6 | R8 | CI backend/frontend/docker | F1.1 | pipeline verde |
| F1.7 | R7 | Frontend base en `web/horarios-web` con pnpm | F1.1 | `pnpm run build` |

Criterio de salida:

- Se puede compilar y probar sin logica de negocio.
- API responde healthcheck.
- CI ejecuta backend, frontend y build Docker.

### Fase 2 - Base de datos compartida

Objetivo: migraciones aplicables y ownership claro por tabla.

| ID | Owner | Tarea | Depende de | Salida medible |
|---|---|---|---|---|
| F2.1 | R1/R8 | Crear `ApplicationDbContext` y estrategia de migraciones/enums | F1 | migracion base aplica |
| F2.2 | R4 | Migrar jornadas, bloques y descansos | F2.1 | bloques persistibles |
| F2.3 | R2 | Migrar academia base: carreras, pensums, cursos, cohortes | F2.1/F2.2 | FK/unique/check pasan |
| F2.4 | R4 | Migrar aulas, recursos y recursos requeridos por curso | F2.1/F2.3 | aulas/recursos persistibles |
| F2.5 | R3 | Migrar docentes, disponibilidad y relaciones docente-carrera-jornada | F2.2-F2.4 | disponibilidad persistible |
| F2.6 | R2 | Migrar area comun | F2.2/F2.3 | area comun persistible |
| F2.7 | R1 | Migrar `app_user` y roles | F2.3/F2.5 | usuario seed login-ready |
| F2.8 | R5 | Migrar importacion | F2.3-F2.5 | lote y errores persistibles |
| F2.9 | R6 | Migrar planes, sesiones, corridas y asignaciones | F2.3-F2.6 | run vacio persistible |
| F2.10 | R7 | Migrar violaciones y edicion manual | F2.9 | idempotencia manual |
| F2.11 | R8 | Migrar sustituciones y vista `exam_plan` | F2.9 | overlay consultable |
| F2.12 | R8 | Test de migraciones en PostgreSQL limpio | F2.1-F2.11 | test verde |
| F2.13 | R2/R3/R4/R1 | Seed dev minimo | F2.12 | admin + datos para plan |

Criterio de salida:

- Base limpia queda lista con una sola ejecucion de migraciones.
- Indices minimos existen.
- Ninguna regla compleja del motor vive en constraints SQL.

### Fase 3 - Verticales de catalogos y seguridad

Objetivo: que los datos base se administren desde UI real.

| ID | Owner | Tarea | Depende de | Salida medible |
|---|---|---|---|---|
| F3.1 | R1 | Login, JWT, `/api/me`, middleware de errores | F2.7 | tests 200/401 |
| F3.2 | R1 | Pantalla login y manejo de sesion | F3.1 | login/logout manual |
| F3.3 | R2 | API y UI de carreras, pensums, cursos, cohortes | F2.3/F3.1 | CRUD y paginacion |
| F3.4 | R2 | API y UI de area comun | F2.6/F3.3 | area comun guarda cohortes |
| F3.5 | R3 | API y UI de docentes/cursos habilitados | F2.5/F3.1 | docente vinculado a cursos |
| F3.6 | R3 | API y UI de disponibilidad docente | F3.5/F2.2 | matriz por dia/bloque |
| F3.7 | R4 | API y UI de jornadas/bloques/descansos | F2.2/F3.1 | bloques generados |
| F3.8 | R4 | API y UI de aulas/recursos | F2.4/F3.7 | lab/capacidad/recursos |
| F3.9 | R8 | Pruebas integracion catalogos/auth | F3.1-F3.8 | 200/400/401/403/409 |

Criterio de salida:

- No hay datos mock en catalogos.
- Campos desconocidos se rechazan.
- Duplicados devuelven `DUPLICATE_CODE`.
- UI oculta acciones que el backend rechazaria.

### Fase 4 - Importacion y calidad de datos

Objetivo: cargar datos reales sin corromper catalogos.

| ID | Owner | Tarea | Depende de | Salida medible |
|---|---|---|---|---|
| F4.1 | R5 | Definir formato CSV/XLSX por hoja | F3 | plantilla documentada |
| F4.2 | R5 | Parser CSV/XLSX | F4.1 | filas leidas |
| F4.3 | R5 | Validar hojas y columnas | F4.2 | errores por hoja/columna |
| F4.4 | R5 | Validar tipos y enums | F4.2 | errores por celda |
| F4.5 | R5/R2/R3/R4 | Validar referencias por codigo | F3/F4.4 | FK logicas |
| F4.6 | R5 | Guardar `import_batch` e `import_error` | F2.8 | errores paginados |
| F4.7 | R5 | Persistir import valido en transaccion | F4.5 | rollback si falla |
| F4.8 | R5 | Endpoint `POST /api/imports/academic-data` | F4.6/F4.7 | valida/importa |
| F4.9 | R5 | UI `ImportWizard` | F4.8 | errores filtrables |
| F4.10 | R8 | Tests import valido/invalido | F4.8/F4.9 | invalido no cambia datos |

Criterio de salida:

- Import invalido no modifica catalogos.
- Cada error incluye hoja, fila, columna, valor, codigo y accion sugerida.
- CSV y XLSX comparten estructura logica.

### Fase 5 - Dominio y motor constructivo

Objetivo: primer horario parcial/factible reproducible.

| ID | Owner | Tarea | Depende de | Salida medible |
|---|---|---|---|---|
| F5.1 | R6 | Value objects: `TimeRange`, `TimeSlot`, `RoomCoordinate` | F1 | unit tests |
| F5.2 | R6 | Entidades dominio: `Course`, `Teacher`, `Room`, `Cohort`, `SchedulableSession`, `Assignment` | F5.1 | invariantes |
| F5.3 | R6 | `Schedule` con indices por sesion/docente/aula/cohorte | F5.2 | add/remove/move |
| F5.4 | R6/R4 | `TimeGridBuilder` y `FixedBreak` | F3.7 | descanso bloquea |
| F5.5 | R6/R2 | `SessionFactory` y `CommonAreaMerger` | F3.3/F3.4 | area comun una sesion |
| F5.6 | R6 | `ProblemPreValidator` | F3/F4 | issues bloqueantes |
| F5.7 | R6/R3/R4 | `CandidateGenerator` | F5.4-F5.6 | candidatos compatibles |
| F5.8 | R6 | `HardConstraintChecker` | F5.3/F5.7 | sin solapes |
| F5.9 | R6 | `DifficultyRanker` determinista | F5.7 | orden reproducible |
| F5.10 | R6 | `ConstructiveScheduler` | F5.7-F5.9 | asignaciones/no asignables |
| F5.11 | R8 | Tests constructivo | F5.10 | misma entrada = mismo resultado |

Criterio de salida:

- Docente, aula y cohorte no se solapan.
- `FixedBreak` nunca recibe sesiones.
- Curso sin docente o aula queda `UNASSIGNED` con razon.
- Area comun bloquea todas las cohortes involucradas.

### Fase 6 - Planes, generacion y resultado

Objetivo: conectar DB, API, motor y UI.

| ID | Owner | Tarea | Depende de | Salida medible |
|---|---|---|---|---|
| F6.1 | R6 | API/UI para crear/listar planes | F2.9/F3.1 | plan `DRAFT` |
| F6.2 | R6 | Mapper DB -> `SchedulingProblem` | F3/F5 | snapshot completo |
| F6.3 | R6 | `POST /validate` y persistencia de issues | F5.6/F6.2 | errores guardados |
| F6.4 | R6 | Crear `schedule_run` con seed/config/input_snapshot | F2.9/F6.2 | run `RUNNING` |
| F6.5 | R6 | `POST /generate` constructivo | F5.10/F6.4 | resultado persistido |
| F6.6 | R6 | Persistir sesiones, asignaciones, no asignables y score | F6.5 | `GET /result` |
| F6.7 | R6/R7 | `GET /violations` y panel de conflictos | F6.6 | conflictos visibles |
| F6.8 | R6 | Aprobar y bloquear plan | F6.6 | estados correctos |
| F6.9 | R7 | `SchedulePlanPage` con acciones por estado | F6.1-F6.8 | flujo completo |
| F6.10 | R8 | Tests integracion schedule | F6.1-F6.9 | validar/generar/aprobar |

Criterio de salida:

- Fallo tecnico no deja plan colgado en `GENERATING`.
- Resultados leen corrida persistida.
- Seed, pesos y `engineVersion` quedan guardados.

### Fase 7 - Optimizacion, edicion manual y grilla

Objetivo: mejorar calidad del horario y reparar cambios manuales.

| ID | Owner | Tarea | Depende de | Salida medible |
|---|---|---|---|---|
| F7.1 | R7 | `Score` con metricas separadas | F5.10 | compare por prioridad |
| F7.2 | R7/R4 | `RoomDistanceCalculator` | F5.1/F3.8 | distancia correcta |
| F7.3 | R7 | `IncrementalSoftScorer` | F7.1/F7.2 | delta local |
| F7.4 | R7 | Moves: propuesta, evaluacion, aplicacion reversible | F5.3/F7.3 | invalido no muta |
| F7.5 | R7 | `AnnealingOptimizer` | F7.4 | mejor no empeora |
| F7.6 | R6/R7 | Integrar generacion constructiva + SA | F6.5/F7.5 | score mejora |
| F7.7 | R7 | `ManualEditCommand` y `ManualEditApplier` | F6.6/F7.5 | edicion limpia |
| F7.8 | R7 | `NeighborhoodSelector` y `NeighborhoodRepairer` | F7.7 | LNS repara |
| F7.9 | R7 | API `POST /manual-edits` con idempotencia | F2.10/F7.8 | retry no duplica |
| F7.10 | R7 | `ScheduleGrid` por cohorte/docente/aula | F6.6 | filtros reales |
| F7.11 | R7 | Drag and drop con drawer | F7.9/F7.10 | envia edit |
| F7.12 | R8 | Benchmarks y tests LNS | F7.5-F7.11 | pinned no se mueve |

Criterio de salida:

- SA no empeora el mejor global.
- Sesion fijada nunca se mueve.
- LNS toca solo vecindario afectado.
- Reintento con mismo `clientRequestId` devuelve resultado existente.

### Fase 8 - Publicacion, reportes, sustituciones y entrega

Objetivo: cerrar el ciclo operativo.

| ID | Owner | Tarea | Depende de | Salida medible |
|---|---|---|---|---|
| F8.1 | R8 | Servicio de reportes desde `schedule_run` | F6.6 | no recalcula |
| F8.2 | R8 | PDF por cohorte/docente/aula/conflictos | F8.1 | archivo abre |
| F8.3 | R8 | XLSX con hojas y metadatos | F8.1 | archivo abre |
| F8.4 | R8 | Endpoints de descarga | F8.2/F8.3 | headers correctos |
| F8.5 | R8 | UI reportes | F8.4 | descarga real |
| F8.6 | R8 | API sustituciones | F2.11/F6.8 | crea evento |
| F8.7 | R8/R3 | Validar solape docente sustituto | F8.6 | rechaza conflicto |
| F8.8 | R8 | Overlay de sustituciones en consultas publicadas | F8.6 | vista combina datos |
| F8.9 | R8 | UI sustituciones y vistas publicas | F8.8 | docente/alumno consultan |
| F8.10 | R8 | Backup/restore, Docker final, checklist QA | todo | entrega reproducible |
| F8.11 | Todos | Bug bash final | todo | backlog critico cerrado |

Criterio de salida:

- Reportes disponibles solo en estados permitidos.
- Sustituciones no modifican plantilla base.
- Alumno solo consulta su cohorte.
- Docente solo consulta horario propio y sustituciones relacionadas.
- Entrega levanta desde cero.

## 11. Backlog por persona, de primero a ultimo

### R1 - Seguridad, usuarios, plataforma base e integracion

Responsabilidad vertical: `app_user`, autenticacion, autorizacion, estructura
base, manejo de errores, healthcheck, login UI e integracion general.

1. Crear solution, proyectos y referencias de capas.
2. Configurar nullable, warnings, formato y convenciones.
3. Crear API base, healthcheck y OpenAPI.
4. Crear `ApplicationDbContext` base con R8.
5. Migrar `app_user` y enum de roles.
6. Crear seed de admin de desarrollo.
7. Implementar hash de contrasenas.
8. Implementar login JWT.
9. Implementar `/api/me`.
10. Implementar politicas por rol.
11. Crear middleware de errores y `requestId`.
12. Crear pantalla Login y persistencia segura de token en frontend.
13. Crear guardas visuales por rol en shell.
14. Revisar transiciones de estado con R6.
15. Revisar dependencias prohibidas antes de entrega.
16. Codificar parte de `SchedulePlan` si hay bloqueo de integracion.

Dependencias fuertes:

- Login depende de DB de usuarios.
- Frontend completo depende de token y roles.
- Cada modulo necesita middleware de errores estable.

Medidas de cierre:

- `POST /api/auth/login` probado con credenciales validas e invalidas.
- `GET /api/me` probado con token valido, faltante, invalido y expirado.
- Ningun error devuelve stack trace.
- R1 participa en codigo, no solo coordinacion.

### R2 - Catalogos academicos y areas comunes

Responsabilidad vertical: carreras, pensums, cursos, cursos por pensum,
cohortes, areas comunes, UI academica y pruebas de catalogos.

1. Migrar `career`.
2. Migrar `curriculum`.
3. Migrar `course`.
4. Migrar `curriculum_course`.
5. Migrar `cohort`.
6. Migrar `common_area_rule`.
7. Migrar `common_area_career`.
8. Crear entidades EF y configuraciones.
9. Crear casos de uso de CRUD academico.
10. Crear endpoints paginados y validaciones.
11. Crear UI de carreras.
12. Crear UI de pensums y cursos por semestre.
13. Crear UI de cohortes por carrera/jornada.
14. Crear UI de areas comunes.
15. Crear tests de duplicado, FK invalida y paginacion.
16. Apoyar a R5 en validacion de importacion academica.
17. Apoyar a R6 en `SessionFactory` y `CommonAreaMerger`.

Dependencias fuertes:

- Importacion depende de catalogos academicos.
- Planes dependen de cohortes y cursos.
- Area comun depende de jornada de R4.
- Motor constructivo depende de sesiones creadas desde estos datos.

Medidas de cierre:

- Se puede crear una carrera con pensum, cursos y cohorte desde UI.
- Area comun crea una regla que luego produce una sola sesion fisica.
- Cambios invalidos devuelven errores estables.

### R3 - Docentes, disponibilidad y carga

Responsabilidad vertical: docentes, cursos habilitados, disponibilidad,
relacion carrera-jornada, carga y vistas de docente.

1. Migrar `teacher`.
2. Migrar `teacher_course`.
3. Migrar `teacher_availability`.
4. Migrar `teacher_career_journey`.
5. Crear entidades EF/configuraciones.
6. Crear endpoints de docentes.
7. Crear endpoints de cursos habilitados por docente.
8. Crear endpoints de disponibilidad propia.
9. Crear reglas de permiso: docente solo edita su disponibilidad.
10. Crear UI de docentes.
11. Crear UI de cursos por docente.
12. Crear matriz de disponibilidad por dia/bloque.
13. Crear validacion de carga minima/maxima.
14. Apoyar `CandidateGenerator` con docentes habilitados.
15. Apoyar sustituciones validando solapes de docente.
16. Crear tests de disponibilidad y permisos.

Dependencias fuertes:

- Disponibilidad depende de jornadas/bloques de R4.
- Generacion depende de docentes con cursos habilitados.
- Sustituciones dependen de calendario docente.

Medidas de cierre:

- Docente puede guardar disponibilidad propia.
- Admin puede vincular docente a cursos.
- Prevalidacion detecta curso sin docente.

### R4 - Tiempo, aulas, recursos y restricciones fisicas

Responsabilidad vertical: jornadas, bloques, descansos, aulas, recursos,
laboratorios, capacidad, UI de tiempo/espacios.

1. Migrar `journey`.
2. Migrar `time_block`.
3. Migrar `fixed_break`.
4. Migrar `room`.
5. Migrar `resource`.
6. Migrar `room_resource`.
7. Migrar `course_required_resource`.
8. Crear entidades EF/configuraciones.
9. Crear generacion de bloques por jornada.
10. Crear CRUD jornadas y descansos.
11. Crear CRUD aulas y recursos.
12. Crear UI de jornadas/bloques.
13. Crear UI de aulas/recursos.
14. Crear `TimeGridBuilder` con R6.
15. Crear calculo de distancia aula con R7.
16. Apoyar `CandidateGenerator` con capacidad, laboratorio y recursos.
17. Crear tests de bloques, descansos y capacidad.

Dependencias fuertes:

- Disponibilidad docente depende de jornadas.
- Area comun depende de jornada.
- Candidate generation depende de aulas, capacidad y recursos.
- Benchmarks dependen de datos fisicos realistas.

Medidas de cierre:

- Jornada genera bloques validos.
- Descanso fijo bloquea asignaciones.
- Curso que requiere laboratorio no usa aula teorica sin recurso.

### R5 - Importacion CSV/XLSX y calidad de datos

Responsabilidad vertical: plantillas, parser, validaciones, lote de importacion,
errores, transaccion, UI de importacion.

1. Definir plantilla de hojas/columnas.
2. Crear migracion/configuracion de `import_batch`.
3. Crear migracion/configuracion de `import_error`.
4. Implementar lectura CSV.
5. Implementar lectura XLSX.
6. Validar hojas obligatorias.
7. Validar columnas obligatorias.
8. Validar tipos: numero, booleano, fecha, hora, enum.
9. Validar referencias por codigo con R2/R3/R4.
10. Detectar duplicados dentro del archivo antes de persistir.
11. Guardar errores con hoja/fila/columna/valor/codigo/mensaje.
12. Persistir lote valido en transaccion.
13. Crear endpoint de importacion.
14. Crear endpoint de errores paginados.
15. Crear `ImportWizard` frontend.
16. Crear filtros por hoja/columna/estado.
17. Crear tests de import valido, invalido y rollback.

Dependencias fuertes:

- Necesita catalogos base para validar referencias.
- Solver depende de importacion para datos reales.
- UI de planes depende de datos ya cargados.

Medidas de cierre:

- Archivo invalido no cambia ninguna tabla de catalogo.
- Archivo valido crea datos consultables.
- Errores son accionables para usuario junior/no tecnico.

### R6 - Planes de horario y motor constructivo

Responsabilidad vertical: plan de horario, prevalidacion, dominio del motor,
constructiva, generacion, persistencia de corridas, UI de planes.

1. Crear value objects de dominio.
2. Crear entidades de dominio puras.
3. Crear `Schedule` con indices.
4. Migrar/configurar `schedule_plan`.
5. Migrar/configurar sesiones, corridas y asignaciones.
6. Crear API/UI para crear y listar planes.
7. Crear mapper DB -> `SchedulingProblem`.
8. Crear `SessionFactory`.
9. Crear `CommonAreaMerger` con R2.
10. Crear `ProblemPreValidator`.
11. Crear `CandidateGenerator` con R3/R4.
12. Crear `HardConstraintChecker`.
13. Crear `DifficultyRanker`.
14. Crear `ConstructiveScheduler`.
15. Crear endpoint `validate`.
16. Crear endpoint `generate`.
17. Persistir input snapshot, seed, version, score y resultado.
18. Crear endpoint `result`.
19. Crear endpoint `approve` y `lock`.
20. Crear UI `SchedulePlanPage`.
21. Crear tests unitarios y de integracion.

Dependencias fuertes:

- Depende de catalogos, docentes, tiempo/aulas e importacion.
- R7 depende del resultado para optimizar y editar.
- R8 depende de corridas persistidas para reportes.

Medidas de cierre:

- Plan valido genera horario parcial/factible.
- Plan invalido queda con issues.
- Misma entrada y seed generan mismo resultado constructivo.

### R7 - Optimizacion, edicion manual LNS y grilla

Responsabilidad vertical: scoring, SA, LNS, manual edits, violaciones, grilla,
drag and drop y panel de conflictos.

1. Migrar/configurar `schedule_violation`.
2. Migrar/configurar `manual_edit`.
3. Crear `Score` con metricas separadas.
4. Crear `RoomDistanceCalculator`.
5. Crear `IncrementalSoftScorer`.
6. Crear `MoveProposal`.
7. Crear `MoveGenerator`.
8. Crear `MoveEvaluator`.
9. Crear `MoveApplier` reversible.
10. Crear `AnnealingOptimizer`.
11. Integrar SA en generacion con R6.
12. Crear `ManualEditCommand`.
13. Crear `ManualEditApplier`.
14. Crear `NeighborhoodSelector`.
15. Crear `NeighborhoodRepairer`.
16. Crear endpoint manual edit con idempotencia.
17. Crear `ScheduleGrid` por cohorte/docente/aula.
18. Crear `ConflictPanel`.
19. Crear drag and drop solo en `APPROVED`.
20. Crear tests de SA, moves, LNS y UI manual basica.

Dependencias fuertes:

- SA depende de constructiva.
- LNS depende de resultado persistido.
- Drag and drop depende de endpoint manual edit.
- Reportes usan violaciones persistidas.

Medidas de cierre:

- SA no empeora mejor horario.
- Movimiento invalido no muta schedule.
- LNS no mueve sesiones fijadas.
- UI muestra conflictos restantes.

### R8 - Publicacion, reportes, sustituciones, QA y entrega

Responsabilidad vertical: reportes, vistas publicadas, sustituciones,
benchmarks, CI, Docker final, backup/restore y calidad.

1. Crear estrategia de pruebas.
2. Crear CI backend/frontend/docker.
3. Configurar Docker Compose dev/prod.
4. Crear fixtures `small`, `medium`, `large`, `infeasible-room`, `infeasible-teacher`.
5. Crear `BenchmarkRunner`.
6. Migrar/configurar `substitution_event`.
7. Crear vista `exam_plan` si la UI o reportes la necesitan.
8. Crear servicio de reportes desde `schedule_run`.
9. Crear PDF por cohorte/docente/aula/conflictos.
10. Crear XLSX con hojas y metadatos.
11. Crear endpoints de descarga.
12. Crear UI de reportes.
13. Crear API de sustituciones.
14. Validar sustituto sin solape con R3.
15. Crear overlay de sustituciones para vistas publicadas.
16. Crear vista de horario para docente.
17. Crear vista de horario para estudiante/cohorte.
18. Crear backup/restore documentado.
19. Ejecutar bug bash final.
20. Preparar checklist de defensa academica.

Dependencias fuertes:

- Reportes dependen de corridas persistidas.
- Sustituciones dependen de horario `LOCKED`.
- Benchmarks dependen del solver.
- Entrega final depende de todos.

Medidas de cierre:

- PDF/XLSX abren y tienen metadatos.
- Alumno no puede ver otra cohorte.
- Sustitucion no cambia plantilla base.
- CI completo queda verde.

## 12. Dependencias entre modulos

| Modulo | Depende de | Bloquea a |
|---|---|---|
| R1 Seguridad/plataforma | Fase 1 y `app_user` | todos los endpoints y pantallas protegidas |
| R2 Academia | DB base, seguridad | importacion, sesiones, planes |
| R3 Docentes | seguridad, jornadas | candidate generation, sustituciones |
| R4 Tiempo/aulas | DB base | disponibilidad, solver, area comun |
| R5 Importacion | R2/R3/R4 | datos reales para generacion |
| R6 Planes/motor constructivo | R2/R3/R4/R5 | optimizacion, reportes |
| R7 SA/LNS/grilla | R6 | edicion manual, UI avanzada |
| R8 Reportes/publicacion/QA | R6/R7 | entrega final |

Orden de desbloqueo recomendado:

1. R1 entrega login y errores.
2. R2/R3/R4 entregan catalogos minimos.
3. R5 entrega importacion.
4. R6 entrega constructiva y resultado.
5. R7 entrega SA y LNS.
6. R8 entrega reportes/publicacion y cierre.

## 13. Orden recomendado de PR

`PR` significa pull request; queda definido en el glosario.

1. `C01-solution-dotnet`
2. `C02-ci-docker-base`
3. `C03-db-context-migrations-base`
4. `C04-auth-users-login`
5. `C05-academic-catalogs`
6. `C06-time-rooms-resources`
7. `C07-teachers-availability`
8. `C08-import-academic-data`
9. `C09-domain-core`
10. `C10-schedule-indexes`
11. `C11-session-factory-prevalidation`
12. `C12-constructive-solver`
13. `C13-schedule-plan-api-ui`
14. `C14-generate-result-violations`
15. `C15-score-sa`
16. `C16-benchmarks`
17. `C17-schedule-grid`
18. `C18-manual-edit-lns`
19. `C19-reports`
20. `C20-substitutions`
21. `C21-public-teacher-student-views`
22. `C22-hardening-backup-restore`
23. `C23-final-qa-defense`

Regla: un PR puede tocar varias capas, pero de un solo modulo vertical. Ejemplo:
un PR de R3 puede tocar migracion, API y UI de disponibilidad docente. No debe
mezclar disponibilidad docente con reportes o LNS.

## 14. Plan de pruebas

### 14.1 Unitarias

- `TimeRange.Overlaps`.
- `RoomCoordinate.DistanceTo`.
- Invariantes de dominio.
- `Schedule` add/remove/move e indices.
- `SessionFactory`.
- `CommonAreaMerger`.
- `ProblemPreValidator`.
- `CandidateGenerator`.
- `HardConstraintChecker`.
- `ConstructiveScheduler`.
- `Score`.
- `MoveEvaluator`, `MoveApplier`.
- `AnnealingOptimizer`.
- `NeighborhoodRepairer`.
- `ManualEditApplier`.

### 14.2 Integracion API/DB

- Migraciones aplican en PostgreSQL limpio.
- Login valido e invalido.
- Permisos por rol.
- CRUD catalogos.
- Disponibilidad docente propia y ajena.
- Import valido.
- Import invalido sin cambios.
- Validacion de plan.
- Generacion de plan.
- Consulta de resultado.
- Consulta de violaciones.
- Aprobar y bloquear.
- Edicion manual idempotente.
- Reportes PDF/XLSX.
- Sustituciones y overlay.

### 14.3 Frontend

- Build TypeScript.
- Login muestra error claro.
- Shell oculta vistas por rol.
- Catalogos cargan y guardan.
- Import wizard muestra errores.
- Plan page respeta estados.
- Grilla renderiza asignaciones.
- Drag and drop abre drawer y envia manual edit.
- Reportes descargan.
- Vista docente/alumno no muestra controles admin.

### 14.4 Benchmarks

| Fixture | Proposito | Criterio |
|---|---|---|
| `small` | humo rapido, area comun, lab, fixed break | termina < 2s |
| `medium` | pensum viejo/nuevo, varias jornadas | SA no empeora constructiva |
| `large` | escala cercana al caso real | respeta `timeLimitSeconds` |
| `infeasible-room` | demanda excede aulas | explica falta de aula |
| `infeasible-teacher` | curso sin docente | explica falta de docente |

Metricas obligatorias:

- `seed`;
- `timeLimitSeconds`;
- `elapsedMillis`;
- `withinTimeLimit`;
- asignaciones constructivas;
- no asignables constructivos;
- errores de prevalidacion;
- `constructiveScore`;
- `annealedScore`;
- violaciones separadas por categoria.

## 15. Definition of Done

Una tarea esta terminada solo si:

- compila;
- pruebas relevantes pasan;
- migracion aplica si cambia DB;
- endpoint devuelve DTO documentado si expone API;
- frontend consume API real si toca UI;
- errores tienen codigo estable;
- no rompe dependencias de capas;
- no agrega motor externo de timetabling;
- actualiza documentacion si cambia contrato;
- deja verificacion runnable en la descripcion del PR.

## 16. Reglas para equipo junior

- Una tarea debe durar 1 a 3 dias cuando sea posible.
- Si una tarea crece, se parte antes de codificar.
- Primero contrato minimo, luego implementacion.
- Una migracion grande se revisa antes de que otros construyan sobre ella.
- Ningun dato se relaciona por nombre; se usan codigos o IDs.
- No se optimiza el motor sin benchmark.
- No se crean abstracciones "para despues".
- No se mezclan modulos verticales sin acuerdo.
- Si hay duda de negocio, se escribe ejemplo de datos y resultado esperado.
- Toda accion destructiva necesita respaldo o transaccion.

## 17. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigacion |
|---|---:|---|
| Modulos verticales crean migraciones incompatibles | Alto | una rama de integracion DB semanal y revision R1/R8 |
| Area comun mal modelada | Alto | R2 y R6 prueban caso temprano con multiples cohortes |
| Solver se mezcla con EF Core | Alto | dependency tests y revision de capas |
| Importacion corrompe catalogos | Alto | validar todo antes de transaccion, rollback probado |
| Estados ambiguos | Medio | transiciones centralizadas y tests |
| SA consume demasiado tiempo | Medio | limite por tiempo, fixtures y metricas |
| LNS mueve sesion fijada | Alto | guard de pinned sessions y pruebas |
| Reportes recalculan motor | Medio | reportes leen solo `schedule_run` persistido |
| UI habilita accion invalida | Medio | acciones por estado + backend autoritativo |
| Libreria PDF incompatible | Medio | revision de licencia antes de codificar F8 |
| Secretos reales en repo | Alto | variables de entorno y seeds dev no productivos |

## 18. Integracion final esperada

Flujo operativo completo:

1. R1 crea usuario admin.
2. R2/R3/R4 cargan catalogos manuales o R5 importa archivo.
3. R6 crea plan `DRAFT`.
4. R6 valida plan.
5. Si hay errores, R2/R3/R4/R5 corrigen datos.
6. R6 genera horario.
7. R7 mejora con SA y muestra conflictos.
8. R6 aprueba plan.
9. R7 permite edicion manual en `APPROVED`.
10. R6 bloquea/publica plan.
11. R8 exporta PDF/XLSX.
12. R8 registra sustituciones sin cambiar plantilla.
13. Docente y alumno consultan vista publicada segun rol.

## 19. Glosario

| Termino | Significado |
|---|---|
| API | Interfaz HTTP que consume la web para leer o cambiar datos. |
| ASP.NET Core | Framework de .NET para crear APIs web. |
| Backend | Servidor: API, casos de uso, base de datos, motor y reportes. |
| Benchmark | Prueba de rendimiento repetible con datos controlados. |
| Branch / rama | Linea de trabajo separada en Git. |
| CI | Integracion continua: proceso automatico que compila y prueba cambios. |
| CRUD | Crear, leer, actualizar y eliminar registros. |
| DTO | Objeto de transferencia de datos; forma exacta de request o response. |
| EF Core | ORM de .NET para mapear clases a tablas y manejar migraciones. |
| Endpoint | Ruta API concreta, por ejemplo `POST /api/schedule-plans/{id}/generate`. |
| Fixture | Conjunto fijo de datos para pruebas. |
| Frontend | Aplicacion web usada por admin, docente y alumno. |
| Hard constraint / restriccion dura | Regla que no debe romperse: solape docente, aula, cohorte, capacidad o laboratorio. |
| Idempotencia | Repetir la misma peticion no duplica cambios. |
| JWT | Token firmado para autenticar peticiones. |
| LNS | Busqueda de Gran Vecindario; tecnica para reparar solo una zona afectada. |
| Middleware | Codigo transversal que corre alrededor de endpoints, por ejemplo errores o autenticacion. |
| Migracion | Cambio versionado de esquema de base de datos. |
| ORM | Herramienta que conecta objetos de codigo con tablas. |
| PR | Pull request; propuesta de cambio revisada antes de integrarse. |
| Rollback | Revertir una transaccion para no guardar cambios parciales. |
| Seed | Numero que fija decisiones aleatorias para reproducibilidad. |
| Smoke test | Prueba rapida para confirmar que el flujo principal no esta roto. |
| Soft constraint / restriccion blanda | Preferencia optimizable: ventanas, caminata, contiguidad o balance. |
| SA | Recocido Simulado; tecnica de optimizacion del motor. |
| Solver / motor | Codigo que construye y mejora horarios. |
| Testkit | Proyecto con fixtures, utilidades de prueba y benchmarks. |
| UCTP | University Course Timetabling Problem; problema de asignar cursos a horarios, docentes y aulas. |
| JSONB | Tipo de PostgreSQL para guardar JSON consultable. |

## 20. Fuentes locales analizadas

- `horarios.md`: reglas de negocio, restricciones, roles, estados y algoritmo.
- `README.md`: modulos actuales y comandos.
- `docs/project-implementation-plan.md`: plan maestro original.
- `docs/agent-task-backlog.md`: tareas ejecutables y dependencias.
- `docs/database-design.md`: DER, tablas, indices y reglas fuera de DB.
- `docs/database.sql`: esquema SQL fuente.
- `docs/api-contracts.md`: endpoints, DTOs, errores y permisos.
- `docs/motor-generador-plan.md`: dominio, constructiva, SA, LNS, persistencia y pruebas.
- `docs/ui-design-base.md`: base visual para `horarios-web`.
- `docs/ui-overhaul-implementation-plan.md`: pantallas y flujo UI.
- `docs/benchmarks.md`: fixtures y metricas.
- Codigo local actual: `horarios-domain`, `horarios-solver`, `horarios-api`,
  `horarios-testkit`, `horarios-web`, migraciones, pruebas, Docker y CI.

## 21. Checklist final de entrega

- `dotnet test` pasa.
- `pnpm run build` pasa.
- `docker compose up --build` levanta todo.
- Migraciones aplican en DB limpia.
- Admin puede importar, validar, generar, aprobar, bloquear, exportar.
- Docente puede registrar disponibilidad y consultar horario.
- Alumno puede consultar su cohorte publicada.
- Sustitucion se registra sin cambiar plantilla base.
- Benchmarks producen tabla reproducible.
- No hay secretos reales en repositorio.
- Documentacion explica arquitectura, roles, dependencias y defensa academica.
