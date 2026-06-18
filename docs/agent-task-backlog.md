# Backlog ejecutable para agentes AI

Proyecto: Sistema de Generacion de Horarios UdeO/UTP  
Uso: cada tarea debe ejecutarse como PR pequeno. No mezclar tareas.

## Reglas globales

- Leer siempre `horarios.md` y `docs/project-implementation-plan.md`.
- Si toca API o frontend que consume API, leer `docs/api-contracts.md`.
- Si toca motor, leer `docs/motor-generador-plan.md`.
- Si toca base de datos, leer `docs/database-design.md` y `docs/database.sql`.
- No usar motores de timetabling externos.
- No agregar dependencias sin necesidad clara.
- No cambiar decisiones acordadas sin documentar ADR.
- Cada tarea termina con verificacion runnable.

## T01 - scaffold-repo

Contexto obligatorio:

- `horarios.md`
- `docs/project-implementation-plan.md`

Archivos esperados:

- `pom.xml`
- `horarios-domain/pom.xml`
- `horarios-solver/pom.xml`
- `horarios-testkit/pom.xml`
- `horarios-api/pom.xml`
- `horarios-web/**`
- `README.md`

Debe hacer:

- crear Maven multi-modulo;
- crear modulos `horarios-domain`, `horarios-solver`, `horarios-testkit`, `horarios-api`;
- crear proyecto React + TypeScript en `horarios-web`;
- configurar Java 21;
- agregar JUnit 5;
- dejar `horarios-solver` dependiente solo de `horarios-domain`;
- dejar `horarios-testkit` dependiente solo de `horarios-domain`, `horarios-solver` y JUnit 5;
- dejar `horarios-api` como unico modulo Spring Boot.

No debe hacer:

- implementar entidades reales;
- crear motor;
- crear pantallas finales;
- conectar DB.

Criterio de salida:

- `mvn test` pasa;
- build web pasa;
- README lista comandos.

Verificacion:

```bash
mvn test
cd horarios-web && pnpm run build
```

## T02 - database-migration

Contexto obligatorio:

- `docs/database-design.md`
- `docs/database.sql`

Archivos esperados:

- `horarios-api/src/main/resources/db/migration/V1__initial_schema.sql`
- `horarios-api/src/test/**`
- `docs/database-design.md` si hay ajuste.

Debe hacer:

- mover schema a migracion;
- configurar Flyway o migrador Spring equivalente;
- probar que aplica en PostgreSQL limpio;
- conservar enums, FK, CHECK, indices y vista `exam_plan`.

No debe hacer:

- cambiar modelo por gusto;
- meter reglas de motor en DB;
- crear endpoints.

Criterio de salida:

- schema aplica sin error;
- tabla `schedule_plan` existe;
- vista `exam_plan` existe;
- `manual_edit` conserva FK compuestas.

Verificacion:

```bash
mvn test
```

## T03 - domain-core

Contexto obligatorio:

- `docs/motor-generador-plan.md`
- `docs/project-implementation-plan.md`

Archivos esperados:

- `horarios-domain/src/main/java/**`
- `horarios-domain/src/test/java/**`

Debe hacer:

- crear value objects: `TimeRange`, `TimeSlot`, `RoomCoordinate`;
- crear entidades inmutables base: `Course`, `Teacher`, `Room`, `Cohort`, `SchedulableSession`, `Assignment`;
- crear enums minimos para estado/tipo;
- validar invariantes en constructores.

No debe hacer:

- implementar solver;
- usar Spring;
- crear repositorios JPA.

Criterio de salida:

- duraciones invalidas fallan;
- `TimeRange.overlaps` cubre bordes;
- `RoomCoordinate.distanceTo` usa formula acordada.

Verificacion:

```bash
mvn -pl horarios-domain test
```

## T04 - schedule-indexes

Contexto obligatorio:

- `docs/motor-generador-plan.md`

Archivos esperados:

- `horarios-solver/src/main/java/**`
- `horarios-solver/src/test/java/**`

Debe hacer:

- crear `Schedule`;
- mantener indices por sesion, docente, aula y cohorte;
- implementar `addAssignment`, `removeAssignment`, `moveAssignment`;
- devolver vistas inmutables.

No debe hacer:

- scoring;
- SA;
- persistencia.

Criterio de salida:

- no permite duplicar sesion;
- remover limpia todos los indices;
- mover es atomico en caso de error;
- docente/aula/cohorte consultan correcto.

Verificacion:

```bash
mvn -pl horarios-solver test
```

## T05 - prevalidation-session-factory

Contexto obligatorio:

- `docs/motor-generador-plan.md`
- `docs/database-design.md`

Archivos esperados:

- `horarios-solver/src/main/java/**`
- `horarios-solver/src/test/java/**`

Debe hacer:

- crear `ProblemPreValidator`;
- crear `SessionFactory`;
- crear `CommonAreaMerger`;
- devolver `PreValidationIssue`;
- generar sesiones por cohorte/pensum/jornada.

No debe hacer:

- generar horarios;
- persistir issues.

Criterio de salida:

- curso sin docente = error;
- lab sin aula compatible = error;
- area comun = una sesion con multiples cohortes;
- pensum duplicado = error.

Verificacion:

```bash
mvn -pl horarios-solver test
```

## T06 - constructive-solver

Contexto obligatorio:

- `docs/motor-generador-plan.md`

Archivos esperados:

- `horarios-solver/src/main/java/**`
- `horarios-solver/src/test/java/**`

Debe hacer:

- `TimeGridBuilder`;
- `CandidateGenerator`;
- `HardConstraintChecker`;
- `DifficultyRanker`;
- `ConstructiveScheduler`;
- `UnassignedSession`.

No debe hacer:

- SA;
- LNS;
- API.

Criterio de salida:

- docente no se solapa;
- aula no se solapa;
- cohorte no se solapa;
- `FixedBreak` bloquea;
- sin candidato queda `UNASSIGNED`;
- misma seed/entrada produce mismo resultado.

Verificacion:

```bash
mvn -pl horarios-solver test
```

## T07 - api-catalogs

Contexto obligatorio:

- `docs/project-implementation-plan.md`
- `docs/api-contracts.md`
- `docs/database-design.md`

Archivos esperados:

- `horarios-api/src/main/java/**`
- `horarios-api/src/test/java/**`

Debe hacer:

- entidades JPA/catalogos minimos;
- repositorios;
- endpoints CRUD para carreras, cursos, docentes, aulas, jornadas;
- separar servicios internos por grupo: `catalog.academic`, `catalog.time`,
  `catalog.teacher`, `catalog.room`;
- validacion de request;
- errores con formato estable.

No debe hacer:

- auth final si no existe aun;
- submodulos Maven por catalogo;
- schema separado por catalogo;
- solver;
- UI.

Criterio de salida:

- crear/listar catalogos;
- duplicado devuelve error estable;
- request invalido devuelve `400`.

Verificacion:

```bash
mvn -pl horarios-api test
```

## T08 - import-academic-data

Contexto obligatorio:

- `docs/project-implementation-plan.md`
- `docs/api-contracts.md`
- `docs/database-design.md`

Archivos esperados:

- `horarios-api/src/main/java/**`
- `horarios-api/src/test/java/**`

Debe hacer:

- endpoint `POST /api/imports/academic-data`;
- soportar CSV y XLSX;
- validar hojas/columnas obligatorias;
- validar FK por codigo;
- crear `import_batch` e `import_error`;
- persistir solo si todo es valido.

No debe hacer:

- generar horarios;
- normalizar nombres como clave.

Criterio de salida:

- archivo invalido no cambia catalogos;
- errores incluyen hoja/fila/columna/valor;
- archivo valido importa en transaccion.

Verificacion:

```bash
mvn -pl horarios-api test
```

## T09 - api-generate-result

Contexto obligatorio:

- `docs/motor-generador-plan.md`
- `docs/project-implementation-plan.md`
- `docs/api-contracts.md`

Archivos esperados:

- `horarios-api/src/main/java/**`
- `horarios-api/src/test/java/**`

Debe hacer:

- endpoint `validate`;
- endpoint `generate`;
- endpoint `result`;
- endpoint `violations`;
- mapear DB -> `ScheduleProblem`;
- persistir `schedule_run`, `schedule_assignment`, `schedule_violation`;
- guardar `input_snapshot`, `output_snapshot`, `seed`, `engineVersion`.

No debe hacer:

- SA si no existe;
- UI.

Criterio de salida:

- plan invalido queda `INVALID_INPUT`;
- plan valido genera run;
- resultado recupera asignaciones y no asignadas;
- errores del solver no dejan estado colgado en `GENERATING`.

Verificacion:

```bash
mvn -pl horarios-api test
```

## T10 - auth-roles

Contexto obligatorio:

- `docs/project-implementation-plan.md`
- `docs/database-design.md`
- `docs/api-contracts.md`

Archivos esperados:

- `horarios-api/src/main/java/**`
- `horarios-api/src/test/java/**`

Debe hacer:

- Spring Security;
- login;
- BCrypt;
- JWT;
- `/api/me`;
- permisos por rol.

No debe hacer:

- OAuth;
- recuperacion de password;
- multi-tenant.

Criterio de salida:

- login valido entrega JWT;
- login invalido = `401`;
- rol insuficiente = `403`;
- teacher solo edita disponibilidad propia;
- student solo ve su cohorte.

Verificacion:

```bash
mvn -pl horarios-api test
```

## T11 - frontend-shell-catalogs

Contexto obligatorio:

- `docs/project-implementation-plan.md`
- `docs/api-contracts.md`

Archivos esperados:

- `horarios-web/src/**`

Debe hacer:

- login;
- layout admin;
- navegacion;
- paginas CRUD de catalogos base;
- manejo de errores API.

No debe hacer:

- grilla drag and drop;
- diseno tipo landing;
- datos mock permanentes si API existe.

Criterio de salida:

- admin inicia sesion;
- lista/crea catalogos;
- errores visibles;
- UI respeta rol.

Verificacion:

```bash
cd horarios-web && pnpm run build
```

## T12 - frontend-import-plan

Contexto obligatorio:

- `docs/project-implementation-plan.md`
- `docs/api-contracts.md`

Archivos esperados:

- `horarios-web/src/**`

Debe hacer:

- Import Wizard;
- subir CSV/XLSX;
- mostrar resumen;
- listar errores por hoja/fila/columna;
- vista de plan con validar/generar/aprobar/bloquear.

No debe hacer:

- editar horario visual;
- reportes PDF/XLSX.

Criterio de salida:

- admin importa archivo valido;
- admin entiende archivo invalido;
- botones cambian segun estado.

Verificacion:

```bash
cd horarios-web && pnpm run build
```

## T13 - solver-sa

Contexto obligatorio:

- `docs/motor-generador-plan.md`

Archivos esperados:

- `horarios-solver/src/main/java/**`
- `horarios-solver/src/test/java/**`

Debe hacer:

- `Score`;
- `RoomDistanceCalculator`;
- `IncrementalSoftScorer`;
- `MoveGenerator`;
- `MoveEvaluator`;
- `MoveApplier`;
- `AnnealingOptimizer`.

No debe hacer:

- LNS;
- API manual edit.

Criterio de salida:

- mejor global no empeora;
- movimiento invalido no muta;
- modo normal conserva duras;
- seed fija produce resultado fijo;
- score separa metricas.

Verificacion:

```bash
mvn -pl horarios-solver test
```

## T14 - benchmarks

Contexto obligatorio:

- `docs/motor-generador-plan.md`
- `docs/project-implementation-plan.md`

Archivos esperados:

- `horarios-testkit/**`;
- `horarios-solver/src/test/**`;
- `docs/benchmarks.md`.

Debe hacer:

- fixtures `small`, `medium`, `large`, `infeasible-room`, `infeasible-teacher`;
- `BenchmarkRunner`;
- reporte con metricas obligatorias.

No debe hacer:

- optimizar hiperparametros sin registrar seed;
- depender de datos reales privados.

Criterio de salida:

- `small` < 2s;
- `large` respeta `timeLimitSeconds`;
- infactibles explican razones;
- reporte compara constructiva vs SA.

Verificacion:

```bash
mvn test
```

## T15 - manual-edit-lns

Contexto obligatorio:

- `docs/motor-generador-plan.md`
- `docs/project-implementation-plan.md`
- `docs/api-contracts.md`

Archivos esperados:

- `horarios-solver/src/main/java/**`
- `horarios-api/src/main/java/**`
- tests relacionados.

Debe hacer:

- `ManualEditCommand`;
- `ManualEditApplier`;
- `NeighborhoodSelector`;
- `NeighborhoodRepairer`;
- endpoint `POST /api/schedule-plans/{id}/manual-edits`;
- idempotencia por `clientRequestId`.

No debe hacer:

- cambiar plantilla base aprobada;
- permitir romper `SessionGroup`.

Criterio de salida:

- edicion limpia crea nuevo run;
- edicion conflictiva repara vecindario;
- pinned no se mueve;
- reintento no duplica;
- conflictos restantes se reportan.

Verificacion:

```bash
mvn test
```

## T16 - frontend-schedule-grid

Contexto obligatorio:

- `docs/project-implementation-plan.md`
- `docs/motor-generador-plan.md`
- `docs/api-contracts.md`

Archivos esperados:

- `horarios-web/src/**`

Debe hacer:

- grilla por cohorte/docente/aula;
- filtros;
- panel de conflictos;
- drag and drop con `dnd-kit`;
- drawer de edicion manual;
- mostrar resultado LNS.

No debe hacer:

- simular reparacion en frontend;
- permitir acciones deshabilitadas por estado.

Criterio de salida:

- admin mueve sesion en `APPROVED`;
- conflictos aparecen;
- resultado muestra moved/pinned/remaining;
- pantalla no rompe en movil/tablet basico.

Verificacion:

```bash
cd horarios-web && pnpm run build
```

## T17 - reports

Contexto obligatorio:

- `docs/project-implementation-plan.md`
- `docs/api-contracts.md`

Archivos esperados:

- `horarios-api/src/main/java/**`
- `horarios-api/src/test/java/**`

Debe hacer:

- PDF con OpenPDF;
- Excel con Apache POI;
- reportes por cohorte, docente, aula;
- hoja de conflictos;
- hoja metadata.

No debe hacer:

- recalcular motor;
- meter JasperReports.

Criterio de salida:

- PDF abre;
- XLSX abre;
- metadata incluye seed, engineVersion, pesos, tiempos;
- conflicto/no asignable aparece.

Verificacion:

```bash
mvn -pl horarios-api test
```

## T18 - substitutions

Contexto obligatorio:

- `docs/project-implementation-plan.md`
- `docs/database-design.md`
- `docs/api-contracts.md`

Archivos esperados:

- `horarios-api/src/main/java/**`
- `horarios-web/src/**`
- tests relacionados.

Debe hacer:

- CRUD de `substitution_event`;
- validar docente sustituto sin solape;
- vista publicada combina plantilla + sustituciones vigentes;
- UI simple para crear sustitucion.

No debe hacer:

- modificar `schedule_assignment` base;
- mover aula/bloque.

Criterio de salida:

- sustitucion vigente aparece en vista docente;
- plantilla base queda intacta;
- solape de sustituto se rechaza.

Verificacion:

```bash
mvn test
cd horarios-web && pnpm run build
```

## T19 - ci-docker-deploy

Contexto obligatorio:

- `docs/project-implementation-plan.md`

Archivos esperados:

- `.github/workflows/**`
- `Dockerfile`
- `docker-compose.yml`
- `horarios-web/Dockerfile`
- docs de despliegue.

Debe hacer:

- CI backend;
- CI frontend;
- validar migraciones;
- Docker para api/web;
- compose dev con PostgreSQL;
- healthcheck.

No debe hacer:

- acoplar secretos al repo;
- deploy manual no reproducible.

Criterio de salida:

- pipeline verde;
- compose levanta local;
- `/actuator/health` responde;
- variables documentadas.

Verificacion:

```bash
docker compose up --build
```

## Orden de asignacion sugerido

- T01 primero.
- Luego T02 y T03.
- Luego T04, T07 y T10 si T01 listo.
- T05 depende de T03.
- T06 depende de T04 y T05.
- T08 depende de T02 y T07.
- T09 depende de T06 y T08.
- T11 depende de T10 y T07.
- T12 depende de T08 y T09.
- T13 depende de T06.
- T14 depende de T13.
- T15 depende de T13 y T09.
- T16 depende de T11, T12 y T15.
- T17 depende de T09.
- T18 depende de T10, T16 y schema.
- T19 puede empezar tras T01, termina al final.
