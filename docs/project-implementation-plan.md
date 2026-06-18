# Plan maestro de implementacion

Proyecto: Sistema de Generacion de Horarios UdeO/UTP  
Estado: plan listo para ejecucion por agentes AI  
Regla: no implementar fuera del alcance de cada tarea. Primero dejar contratos, pruebas y criterio de salida claros.

## Objetivo

Construir un sistema completo para administrar datos academicos, generar horarios de clases y examenes, permitir edicion manual con reparacion local, publicar resultados y exportarlos.

El proyecto se divide en partes independientes pero conectadas:

1. Fundacion del repositorio.
2. Base de datos y migraciones.
3. Dominio y motor.
4. API backend.
5. Importacion CSV/XLSX.
6. Autenticacion y roles.
7. Interfaz web.
8. Reportes y exportaciones.
9. Sustituciones y publicacion.
10. Pruebas, benchmarks y defensa academica.
11. CI/CD, Docker y despliegue.

## Reglas para agentes AI

Cada agente debe trabajar con este contrato:

- leer `horarios.md`, `docs/database-design.md`, `docs/motor-generador-plan.md` y este documento antes de editar;
- si toca API o frontend que consume API, leer `docs/api-contracts.md`;
- no agregar librerias de timetabling: OR-Tools, Timefold, OptaPlanner, UniTime, FET ni similares;
- usar Java 21, Spring Boot 3, Maven multi-modulo, PostgreSQL, React + TypeScript;
- hacer el cambio mas pequeno que complete la tarea;
- no crear abstracciones para futuros hipoteticos;
- dejar prueba minima si toca logica no trivial;
- mantener el motor sin dependencia de Spring;
- mantener resultados reproducibles con `seed`, `engineVersion` y snapshots;
- no mezclar tareas de UI, motor y DB en el mismo PR salvo que la tarea lo pida;
- actualizar docs si cambia contrato, estado o modelo.

Formato de tarea para agentes:

```text
Tarea: <nombre corto>
Contexto obligatorio:
- <docs que debe leer>
Archivos esperados:
- <rutas permitidas>
Debe hacer:
- <acciones concretas>
No debe hacer:
- <limites>
Criterio de salida:
- <checks medibles>
Verificacion:
- <comando o prueba>
```

Definicion de terminado:

- compila;
- pruebas relevantes pasan;
- migracion SQL aplica en base limpia;
- endpoint o pantalla responde con caso feliz y caso de error;
- errores devuelven mensaje util;
- no se rompen estados de `SchedulePlan`;
- docs quedan consistentes.

## Arquitectura C4 minima

### Contexto

Actores:

- Superadmin: administra usuarios, parametros globales, carreras, ciclos y datos base.
- Coordinador academico: importa datos, genera horarios, revisa conflictos, aprueba, publica y edita.
- Docente: registra disponibilidad y consulta horario.
- Alumno: consulta horario publicado de su cohorte.

Sistemas externos:

- Navegador web.
- PostgreSQL.
- Servicio de almacenamiento del proveedor cloud para backups.
- Exportaciones locales PDF/XLSX.

### Contenedores

- `web`: React + TypeScript. Consume REST API. Maneja grillas, formularios y edicion manual.
- `api`: Spring Boot. Auth, REST, persistencia, importacion, reportes, ciclo de vida.
- `domain`: modulo Java puro. Entidades y reglas.
- `solver`: modulo Java puro. Constructiva, SA, LNS, scoring y benchmarks.
- `database`: PostgreSQL. Catalogos, planes, corridas, asignaciones, auditoria.

### Componentes backend

- `AuthController`, `AuthService`, `JwtService`.
- `AcademicCatalogController`, `AcademicCatalogService`.
- `ImportController`, `ImportService`, `ImportValidator`.
- `SchedulePlanController`, `SchedulePlanService`.
- `ScheduleGenerationService`.
- `ManualEditService`.
- `ReportService`.
- `SubstitutionService`.
- `TeacherAvailabilityService`.
- `ScheduleQueryService`.

### Componentes frontend

- `AuthLayout`.
- `AdminShell`.
- `CatalogPages`.
- `ImportWizard`.
- `SchedulePlanPage`.
- `ScheduleGrid`.
- `ManualEditDrawer`.
- `ConflictPanel`.
- `TeacherAvailabilityPage`.
- `PublishedSchedulePage`.
- `ReportsPage`.

## Modelo de modulos

```text
horarios/
  pom.xml
  horarios-domain/
  horarios-solver/
  horarios-testkit/
  horarios-api/
  horarios-web/
  docs/
```

Dependencias permitidas:

```text
horarios-domain -> ninguna dependencia Spring
horarios-solver -> horarios-domain
horarios-testkit -> horarios-domain, horarios-solver, JUnit 5
horarios-api    -> horarios-domain, horarios-solver, Spring Boot, JPA, PostgreSQL, OpenPDF, Apache POI
horarios-web    -> React, TypeScript, dnd-kit, shadcn/ui
```

Dependencia prohibida:

```text
horarios-solver -> horarios-api
horarios-solver -> Spring
horarios-domain -> Spring
```

Limites internos recomendados en `horarios-api`:

```text
catalog.academic -> career, curriculum, course, cohort
catalog.time     -> journey, time_block, fixed_break
catalog.teacher  -> teacher, teacher_course, teacher_availability
catalog.room     -> room, resource, room_resource, course_required_resource
planning         -> schedule_plan, schedule_session, schedule_run, assignment, violation
manualedit       -> manual_edit y reparacion local
importing        -> import_batch, import_error
reporting        -> exportes desde schedule_run persistido
```

Son paquetes/servicios, no submodulos Maven. Mantener DB normalizada compartida.

## Estados y permisos

| Estado | Puede editar datos | Puede generar | Puede aprobar | Puede publicar | Puede edicion manual |
|---|---:|---:|---:|---:|---:|
| `DRAFT` | si | si | no | no | no |
| `VALIDATING` | no | no | no | no | no |
| `INVALID_INPUT` | si | si | no | no | no |
| `GENERATING` | no | no | no | no | no |
| `GENERATED` | si | si | si | no | no |
| `GENERATED_WITH_CONFLICTS` | si | si | si, con advertencia | no | no |
| `APPROVED` | no | no | no | si | si |
| `LOCKED` | no | no | no | ya publicado | no |
| `ARCHIVED` | no | no | no | no | no |

Roles:

- `SUPERADMIN`: todo.
- `ADMIN`: catalogos academicos, planes, importaciones, generacion, aprobacion, reportes.
- `TEACHER`: disponibilidad propia y horario propio.
- `STUDENT`: horario publicado de su cohorte.

## Plan por parte

### 1. Fundacion del repositorio

Objetivo: crear estructura minima compilable.

Entregables:

- Maven multi-modulo.
- `horarios-domain`, `horarios-solver`, `horarios-testkit`, `horarios-api`.
- `horarios-web` con React + TypeScript.
- README con comandos.
- perfiles `dev` y `test`.

Tareas para agente:

```text
Tarea: scaffold-repo
Contexto obligatorio:
- horarios.md
- docs/project-implementation-plan.md
Archivos esperados:
- pom.xml
- horarios-domain/**
- horarios-solver/**
- horarios-testkit/**
- horarios-api/**
- horarios-web/**
Debe hacer:
- crear modulos vacios compilables
- crear `horarios-testkit` para fixtures y benchmarks sinteticos
- agregar Spring Boot solo en horarios-api
- agregar JUnit 5
- agregar comandos de build
No debe hacer:
- implementar motor
- crear pantallas finales
Criterio de salida:
- mvn test pasa
- pnpm build del web pasa si web ya existe
Verificacion:
- mvn test
```

### 2. Base de datos y migraciones

Objetivo: convertir `docs/database.sql` en migraciones aplicables y verificables.

Entregables:

- migracion inicial con enums, tablas, indices y vista `exam_plan`;
- prueba de migracion en PostgreSQL limpio;
- seed minimo para desarrollo.

Tareas:

- crear `V1__initial_schema.sql` desde `docs/database.sql`;
- crear seed `dev` con 2 carreras, 2 jornadas, 4 aulas, 4 docentes, 8 cursos;
- verificar FK, CHECK y UNIQUE;
- documentar cualquier ajuste en `docs/database-design.md`.

Criterio de salida:

- migracion aplica en base limpia;
- seed permite crear un `schedule_plan`;
- no hay regla compleja de motor en DB;
- `manual_edit.client_request_id` conserva idempotencia.

### 3. Dominio y motor

Objetivo: implementar el plan de `docs/motor-generador-plan.md`.

Orden:

1. objetos de valor: `TimeRange`, `TimeSlot`, `TimeGrid`, `RoomCoordinate`;
2. entidades: `Course`, `Teacher`, `Room`, `Cohort`, `SchedulableSession`, `Assignment`;
3. `Schedule` con indices por sesion, docente, aula y cohorte;
4. `ProblemPreValidator`;
5. `SessionFactory` y `CommonAreaMerger`;
6. `CandidateGenerator`;
7. `HardConstraintChecker`;
8. `ConstructiveScheduler`;
9. `Score` e `IncrementalSoftScorer`;
10. `AnnealingOptimizer`;
11. `NeighborhoodRepairer`;
12. `BenchmarkRunner`.

Criterio de salida:

- misma entrada + misma `seed` + misma version produce mismo resultado;
- area comun genera una sola sesion con multiples cohortes;
- docente global no se solapa entre carreras;
- aula no se solapa;
- cohorte no se solapa;
- `FixedBreak` nunca recibe sesion;
- infeasible retorna parcial + razones.

Plan detallado vive en `docs/motor-generador-plan.md`.

### 4. API backend

Objetivo: exponer contratos REST estables para UI y agentes.

Contrato detallado: `docs/api-contracts.md`. Ese archivo define DTOs exactos,
headers, paginacion, codigos de error, transiciones de estado, permisos y
ejemplos JSON. Esta seccion solo resume endpoints minimos.

Endpoints minimos:

| Metodo | Ruta | Rol | Resultado |
|---|---|---|---|
| `POST` | `/api/auth/login` | publico | JWT |
| `GET` | `/api/me` | todos | usuario actual |
| `GET` | `/api/catalog/careers` | admin | carreras |
| `POST` | `/api/catalog/careers` | admin | carrera creada |
| `GET` | `/api/schedule-plans` | admin | planes |
| `POST` | `/api/schedule-plans` | admin | plan creado |
| `POST` | `/api/schedule-plans/{id}/validate` | admin | issues |
| `POST` | `/api/schedule-plans/{id}/generate` | admin | run iniciado o completado |
| `GET` | `/api/schedule-plans/{id}/result` | admin | horario |
| `GET` | `/api/schedule-plans/{id}/violations` | admin | conflictos |
| `POST` | `/api/schedule-plans/{id}/approve` | admin | estado `APPROVED` |
| `POST` | `/api/schedule-plans/{id}/lock` | admin | estado `LOCKED` |
| `POST` | `/api/schedule-plans/{id}/manual-edits` | admin | reparacion |
| `POST` | `/api/imports/academic-data` | admin | lote |
| `GET` | `/api/imports/{id}/errors` | admin | errores |
| `GET` | `/api/teacher/availability` | teacher | disponibilidad |
| `PUT` | `/api/teacher/availability` | teacher | disponibilidad guardada |
| `GET` | `/api/teacher/schedule` | teacher | horario propio |
| `GET` | `/api/public/schedules/cohorts/{id}` | student/admin | horario publicado |
| `GET` | `/api/reports/schedule-plans/{id}.pdf` | admin | PDF |
| `GET` | `/api/reports/schedule-plans/{id}.xlsx` | admin | Excel |

Formato de error:

```json
{
  "code": "COURSE_WITHOUT_TEACHER",
  "message": "El curso no tiene docente habilitado.",
  "details": {
    "entityType": "course",
    "entityId": 10
  }
}
```

Reglas API:

- no devolver stack traces;
- usar codigos estables;
- validar transiciones de estado antes de ejecutar;
- todas las mutaciones admin registran usuario y fecha;
- `generate` acepta `seed`, `solverMode`, `timeLimitSeconds`, `weights`;
- `manual-edits` usa `clientRequestId` para idempotencia.

### 5. Importacion CSV/XLSX

Objetivo: cargar datos reales desde dia 1 sin corromper catalogos.

Flujo:

1. subir archivo;
2. guardar `import_batch` en `UPLOADED`;
3. leer hojas o CSV;
4. validar columnas requeridas;
5. validar referencias por codigo;
6. crear `import_error` por fila;
7. si hay errores, marcar `INVALID`;
8. si no hay errores, persistir en transaccion y marcar `IMPORTED`.

Hojas obligatorias:

- `careers`
- `curricula`
- `courses`
- `curriculum_courses`
- `cohorts`
- `teachers`
- `teacher_courses`
- `teacher_availability`
- `rooms`
- `journeys`
- `fixed_breaks`
- `common_areas`
- `common_area_careers`

Criterio de salida:

- import invalido no modifica datos existentes;
- errores incluyen hoja, fila, columna, valor y accion sugerida;
- nombres no se usan como FK;
- codigos repetidos se reportan antes de persistir.

### 6. Autenticacion y seguridad

Objetivo: proteger acciones por rol con JWT.

Entregables:

- login con email/password;
- hash de password con BCrypt;
- JWT con expiracion;
- filtros Spring Security;
- `@PreAuthorize` o equivalente en endpoints;
- usuario actual disponible en auditoria.

Reglas:

- docente solo edita su disponibilidad;
- alumno solo ve horario de su cohorte;
- admin no puede cambiar password sin flujo explicito;
- tokens expirados devuelven `401`;
- rol insuficiente devuelve `403`.

Pruebas minimas:

- login valido;
- login invalido;
- teacher no puede generar horario;
- student no puede ver otra cohorte;
- admin puede generar.

### 7. Interfaz web

Objetivo: interfaz operativa, no landing page.

Vistas obligatorias:

- Login.
- Dashboard admin con planes recientes y alertas.
- Catalogos: carreras, pensums, cursos, cohortes, docentes, aulas, jornadas.
- Import Wizard.
- Plan de horario: datos, pre-validacion, generacion, resultado.
- Grilla de horario por cohorte, docente y aula.
- Panel de conflictos/no asignables.
- Edicion manual con drag and drop.
- Disponibilidad docente.
- Horario publicado para alumno/docente.
- Reportes.

Reglas UX:

- primera pantalla despues de login muestra trabajo real, no marketing;
- grilla permite filtrar por carrera, cohorte, docente, aula y jornada;
- conflictos visibles al lado del horario;
- edicion manual muestra antes/despues y conflictos restantes;
- botones criticos deshabilitados segun estado;
- errores de importacion descargables y filtrables;
- docente marca disponibilidad por bloques, no por texto libre.

Contrato de grilla:

```text
columnas = dias
filas = bloques de jornada
celda = asignacion o vacia
color = curso o tipo de conflicto
badge = aula, docente, cohorte
drag = solo en APPROVED para edicion manual admin
```

Criterio de salida:

- admin puede importar, validar, generar, revisar, aprobar y bloquear;
- teacher puede guardar disponibilidad;
- student puede ver horario publicado;
- UI no permite accion que backend rechazaria por estado;
- conflictos se entienden sin abrir consola.

### 8. Reportes y exportaciones

Objetivo: entregar PDF, Excel y vista web.

Reportes minimos:

- horario por carrera/cohorte;
- horario por docente;
- horario por aula;
- conflictos y no asignables;
- resumen de score;
- reporte de importacion;
- benchmark para defensa academica.

PDF:

- usar OpenPDF;
- una pagina por cohorte/docente cuando aplique;
- incluir plan, ciclo, jornada, fecha de generacion y estado.

Excel:

- usar Apache POI;
- una hoja por vista;
- incluir hoja `conflicts`;
- incluir hoja `metadata` con seed, engineVersion, pesos y tiempos.

Criterio de salida:

- PDF abre sin errores;
- XLSX abre en LibreOffice/Excel;
- exportacion no recalcula motor;
- exportacion usa `schedule_run` persistido.

### 9. Sustituciones y publicacion

Objetivo: registrar cambios temporales o permanentes sin modificar plantilla base.

Flujo:

1. horario pasa a `LOCKED`;
2. admin crea `substitution_event`;
3. evento referencia `schedule_assignment`;
4. vista publicada combina plantilla + sustituciones vigentes;
5. auditoria conserva docente original y sustituto.

Reglas:

- sustitucion no mueve aula ni bloque;
- sustitucion permanente no cambia plantilla base;
- fecha `ends_at` puede ser nula si es permanente;
- docente sustituto no debe solaparse en el mismo periodo.

Criterio de salida:

- plantilla aprobada queda intacta;
- vista docente refleja sustitucion vigente;
- reporte puede mostrar original y sustituto.

### 10. Pruebas, benchmarks y defensa

Objetivo: demostrar calidad tecnica y academica.

Suites:

- unitarias de dominio;
- unitarias de solver;
- integracion API + DB;
- importacion CSV/XLSX;
- seguridad por rol;
- UI con flujos criticos;
- benchmarks.

Fixtures:

- `small`: 50 sesiones, area comun, lab, recreo fijo;
- `medium`: 300 sesiones, pensum viejo/nuevo, 2 jornadas;
- `large`: 900 sesiones, 20 carreras aproximadas;
- `infeasible-room`: demanda excede aulas;
- `infeasible-teacher`: curso sin docente disponible.

Metricas:

- tiempo a primera solucion;
- tiempo total;
- violaciones duras;
- sesiones no asignadas;
- disponibilidad docente incumplida;
- no contiguidad;
- ventanas muertas;
- distancia de caminata;
- balance de carga;
- uso de seed y version.

Criterio academico:

- tabla de alternativas descartadas;
- explicacion NP-hard por reduccion desde graph coloring;
- busqueda en cuadricula de hiperparametros;
- resultados comparables en 3-5 escenarios.

### 11. CI/CD y despliegue

Objetivo: despliegue repetible en cloud.

CI:

- build backend;
- tests backend;
- build frontend;
- lint frontend si se configura;
- validar SQL en PostgreSQL;
- construir imagen Docker.

Docker:

- `api` empaquetada como jar;
- `web` servido por nginx o por build estatico;
- `postgres` solo para dev local;
- variables por entorno.

Variables minimas:

```text
DATABASE_URL
DATABASE_USER
DATABASE_PASSWORD
JWT_SECRET
JWT_TTL_MINUTES
SPRING_PROFILES_ACTIVE
```

Deploy:

- ambiente `dev`;
- ambiente `prod`;
- backup diario de PostgreSQL;
- logs de aplicacion;
- healthcheck `/actuator/health`.

Criterio de salida:

- despliegue limpio desde cero;
- migraciones corren una vez;
- healthcheck responde;
- usuario admin inicial se puede crear por seed o comando controlado;
- backup probado al menos una vez.

## Orden recomendado de implementacion

1. `T01 - scaffold-repo`
2. `T02 - database-migration`
3. `T03 - domain-core`
4. `T04 - schedule-indexes`
5. `T05 - prevalidation-session-factory`
6. `T06 - constructive-solver`
7. `T07 - api-catalogs`
8. `T08 - import-academic-data`
9. `T09 - api-generate-result`
10. `T10 - auth-roles`
11. `T11 - frontend-shell-catalogs`
12. `T12 - frontend-import-plan`
13. `T13 - solver-sa`
14. `T14 - benchmarks`
15. `T15 - manual-edit-lns`
16. `T16 - frontend-schedule-grid`
17. `T17 - reports`
18. `T18 - substitutions`
19. `T19 - ci-docker-deploy`

Backlog copiable para agentes AI: `docs/agent-task-backlog.md`.

## ADRs base

Decisiones base documentadas en `docs/adr/`:

- `0001-java-21-spring-boot.md`
- `0002-postgresql-jsonb.md`
- `0003-motor-propio-sa-lns.md`
- `0004-maven-multimodulo.md`
- `0005-no-mvp-entrega-completa.md`
- `0006-react-typescript-dnd-kit.md`
- `0007-jwt-roles.md`
- `0008-openpdf-apache-poi.md`

Plantilla:

```text
# ADR N - Titulo

## Estado
Aceptado

## Contexto
<problema>

## Decision
<decision>

## Consecuencias
<positivas y negativas>
```

## Huecos resueltos por este plan

- faltaba plan global fuera del motor;
- faltaba contrato para agentes AI;
- faltaba C4 minimo;
- faltaba plan API completo;
- faltaba plan UI;
- faltaba seguridad por rol;
- faltaba reportes/exportaciones;
- faltaba sustituciones/publicacion;
- faltaba CI/CD y despliegue;
- faltaba orden de implementacion por tareas pequeñas.

## Fuera de alcance hasta iniciar implementacion

- optimizacion exacta;
- integracion con sistema academico externo;
- horarios individuales para estudiantes atrasados;
- calendario semana por semana;
- mobile app nativa;
- motor externo de timetabling.
