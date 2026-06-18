# Graph Report - horarios  (2026-06-18)

## Corpus Check
- 119 files · ~37,749 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1260 nodes · 2248 edges · 101 communities (85 shown, 16 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 184 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `a2e257d5`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_API and Persistence|API and Persistence]]
- [[_COMMUNITY_Candidate Ranking|Candidate Ranking]]
- [[_COMMUNITY_Domain Assignments|Domain Assignments]]
- [[_COMMUNITY_Schedule Indexes|Schedule Indexes]]
- [[_COMMUNITY_Prevalidation Tests|Prevalidation Tests]]
- [[_COMMUNITY_Prevalidation Rules|Prevalidation Rules]]
- [[_COMMUNITY_Hard Constraints|Hard Constraints]]
- [[_COMMUNITY_Frontend and LNS|Frontend and LNS]]
- [[_COMMUNITY_Session Factory|Session Factory]]
- [[_COMMUNITY_Common Area Merge|Common Area Merge]]
- [[_COMMUNITY_T06 Solver Plan|T06 Solver Plan]]
- [[_COMMUNITY_Scheduling Problem|Scheduling Problem]]
- [[_COMMUNITY_Java Architecture|Java Architecture]]
- [[_COMMUNITY_Timetabling Research|Timetabling Research]]
- [[_COMMUNITY_Project Planning|Project Planning]]
- [[_COMMUNITY_Agent Instructions|Agent Instructions]]
- [[_COMMUNITY_Repository Scaffold|Repository Scaffold]]
- [[_COMMUNITY_API Errors|API Errors]]
- [[_COMMUNITY_C4 Architecture|C4 Architecture]]
- [[_COMMUNITY_Room Types|Room Types]]
- [[_COMMUNITY_Teacher Continuity|Teacher Continuity]]
- [[_COMMUNITY_No Timetabling Libraries|No Timetabling Libraries]]
- [[_COMMUNITY_Candidate Space File|Candidate Space File]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Issue Severity File|Issue Severity File]]
- [[_COMMUNITY_Schedule Result File|Schedule Result File]]
- [[_COMMUNITY_Unassigned Session File|Unassigned Session File]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 52|Community 52]]
- [[_COMMUNITY_Community 53|Community 53]]
- [[_COMMUNITY_Community 54|Community 54]]
- [[_COMMUNITY_Community 55|Community 55]]
- [[_COMMUNITY_Community 56|Community 56]]
- [[_COMMUNITY_Community 57|Community 57]]
- [[_COMMUNITY_Community 58|Community 58]]
- [[_COMMUNITY_Community 59|Community 59]]
- [[_COMMUNITY_Community 60|Community 60]]
- [[_COMMUNITY_Community 61|Community 61]]
- [[_COMMUNITY_Community 62|Community 62]]
- [[_COMMUNITY_Community 63|Community 63]]
- [[_COMMUNITY_Community 64|Community 64]]
- [[_COMMUNITY_Community 65|Community 65]]
- [[_COMMUNITY_Community 66|Community 66]]
- [[_COMMUNITY_Community 67|Community 67]]
- [[_COMMUNITY_Community 68|Community 68]]
- [[_COMMUNITY_Community 69|Community 69]]
- [[_COMMUNITY_Community 70|Community 70]]
- [[_COMMUNITY_Community 71|Community 71]]
- [[_COMMUNITY_Community 72|Community 72]]
- [[_COMMUNITY_Community 73|Community 73]]
- [[_COMMUNITY_Community 74|Community 74]]
- [[_COMMUNITY_Community 75|Community 75]]
- [[_COMMUNITY_Community 76|Community 76]]
- [[_COMMUNITY_Community 77|Community 77]]
- [[_COMMUNITY_Community 78|Community 78]]
- [[_COMMUNITY_Community 79|Community 79]]
- [[_COMMUNITY_Community 80|Community 80]]
- [[_COMMUNITY_Community 81|Community 81]]
- [[_COMMUNITY_Community 94|Community 94]]
- [[_COMMUNITY_Community 95|Community 95]]
- [[_COMMUNITY_Community 96|Community 96]]
- [[_COMMUNITY_Community 97|Community 97]]

## God Nodes (most connected - your core abstractions)
1. `AcademicImportService` - 43 edges
2. `ScheduleGenerationService` - 32 edges
3. `String` - 30 edges
4. `Endpoints` - 25 edges
5. `toAssignment()` - 24 edges
6. `Backlog ejecutable para agentes AI` - 22 edges
7. `Plan del motor generador de horarios` - 20 edges
8. `ImportRow` - 19 edges
9. `AcademicCatalogService` - 17 edges
10. `List` - 17 edges

## Surprising Connections (you probably didn't know these)
- `Area Comun` --semantically_similar_to--> `common_area_rule`  [INFERRED] [semantically similar]
  horarios.md → docs/database.sql
- `Estados del Horario` --semantically_similar_to--> `schedule_plan`  [INFERRED] [semantically similar]
  horarios.md → docs/database.sql
- `Mejor Horario Parcial Posible` --semantically_similar_to--> `schedule_assignment`  [INFERRED] [semantically similar]
  horarios.md → docs/database.sql
- `Score` --semantically_similar_to--> `schedule_run`  [INFERRED] [semantically similar]
  docs/motor-generador-plan.md → docs/database.sql
- `ManualEditCommand` --semantically_similar_to--> `manual_edit`  [INFERRED] [semantically similar]
  docs/motor-generador-plan.md → docs/database.sql

## Import Cycles
- None detected.

## Communities (101 total, 16 thin omitted)

### Community 0 - "API and Persistence"
Cohesion: 0.08
Nodes (25): AcademicCatalogService, isBlockRangeValid(), CourseEntity, CareerEntity, CareerRepository, CourseEntity, CourseRepository, AssertTrue (+17 more)

### Community 1 - "Candidate Ranking"
Cohesion: 0.17
Nodes (13): withDuration(), TimeRange, CandidateSpace, Course, List, Room, SchedulableSession, SchedulingProblem (+5 more)

### Community 2 - "Domain Assignments"
Cohesion: 0.09
Nodes (18): AssertTrue, Long, Object, Override, Pageable, PageResponse, PatchMapper, RequestMapper (+10 more)

### Community 3 - "Schedule Indexes"
Cohesion: 0.09
Nodes (26): CatalogService, PatchMapper, E, Class, LocalTime, Map, Object, RequestValidationException (+18 more)

### Community 4 - "Prevalidation Tests"
Cohesion: 0.11
Nodes (17): LocalTime, Long, String, AssertTrue, Long, Object, Override, Pageable (+9 more)

### Community 5 - "Prevalidation Rules"
Cohesion: 0.19
Nodes (14): Assignment, SchedulableSession, Assignment, List, Long, Map, Optional, Assignment (+6 more)

### Community 6 - "Hard Constraints"
Cohesion: 0.19
Nodes (17): CatalogExceptionHandler, duplicateCode(), notFound(), validation(), FieldError, ErrorResponse, ExceptionHandler, FieldErrorDto (+9 more)

### Community 7 - "Frontend and LNS"
Cohesion: 0.12
Nodes (23): ProblemPreValidator, Course, List, Long, Map, PreValidationIssue, Room, SchedulingProblem (+15 more)

### Community 8 - "Session Factory"
Cohesion: 0.15
Nodes (15): CommonAreaMerger, List, SchedulableSession, SchedulableSession, Cohort, CurriculumCourse, List, SchedulableSession (+7 more)

### Community 9 - "Common Area Merge"
Cohesion: 0.09
Nodes (31): Candidate, Comparator, DifficultyRanker, CandidateSpace, ScheduleResult, SchedulingProblem, SessionFactory, CandidateSpace (+23 more)

### Community 10 - "T06 Solver Plan"
Cohesion: 0.10
Nodes (29): Codes, DataFormatter, Date, ImportErrorResponse, ImportMode, ImportResponse, Integer, JdbcClient (+21 more)

### Community 11 - "Scheduling Problem"
Cohesion: 0.21
Nodes (13): BadRequestException, CatalogController, CatalogService, GetMapping, List, Long, Object, Pageable (+5 more)

### Community 12 - "Java Architecture"
Cohesion: 0.07
Nodes (26): 10. Pruebas, benchmarks y defensa, 11. CI/CD y despliegue, 1. Fundacion del repositorio, 2. Base de datos y migraciones, 3. Dominio y motor, 4. API backend, 5. Importacion CSV/XLSX, 6. Autenticacion y seguridad (+18 more)

### Community 13 - "Timetabling Research"
Cohesion: 0.11
Nodes (18): DomainCoreTest, distanceTo(), endMinuteOfWeek(), overlaps(), TimeRange, Test, Assignment, Course (+10 more)

### Community 14 - "Project Planning"
Cohesion: 0.14
Nodes (21): /api/catalog/{resource}, POST /api/substitutions, common_area_rule, course, course_required_resource, fixed_break, journey, resource (+13 more)

### Community 15 - "Agent Instructions"
Cohesion: 0.26
Nodes (7): CatalogService, Long, Object, Pageable, PageResponse, Set, String

### Community 16 - "Repository Scaffold"
Cohesion: 0.24
Nodes (3): CareerEntity, Long, String

### Community 17 - "API Errors"
Cohesion: 0.25
Nodes (8): React TypeScript dnd-kit shadcn/ui, GET /api/schedule-plans/{id}/result, Idempotency-Key, POST /api/schedule-plans/{id}/manual-edits, manual_edit, ManualEditCommand, horarios-web, ScheduleGrid

### Community 18 - "C4 Architecture"
Cohesion: 0.08
Nodes (29): AssignmentResponse, ScoreResponse, GenerationRequest, GenerationResponse, Instant, Integer, JdbcTemplate, List (+21 more)

### Community 19 - "Room Types"
Cohesion: 0.25
Nodes (8): T06 - constructive-solver, POST /api/schedule-plans/{id}/generate, CandidateSpace, HardConstraintChecker, SchedulableSession, Schedule, ScheduleProblem, AdminShell

### Community 20 - "Teacher Continuity"
Cohesion: 0.47
Nodes (6): career, cohort, common_area_career, curriculum, schedule_session_cohort, section_suggestion

### Community 21 - "No Timetabling Libraries"
Cohesion: 0.15
Nodes (13): PostgreSQL y JSONB, OpenPDF y Apache POI, T02 - database-migration, T13 - solver-sa, GET /api/reports/schedule-plans/{id}, exam_plan view, schedule_plan, schedule_run (+5 more)

### Community 22 - "Candidate Space File"
Cohesion: 0.39
Nodes (5): RequestMapper, ConstraintViolation, FieldErrorDto, ObjectMapper, Validator

### Community 23 - "Community 23"
Cohesion: 0.07
Nodes (30): FilterChain, AppUser, JdbcClient, Long, Optional, ResultSet, String, AppUser (+22 more)

### Community 24 - "Issue Severity File"
Cohesion: 0.48
Nodes (6): Cohort, CurriculumCourse, List, Room, Teacher, SchedulingProblem()

### Community 25 - "Schedule Result File"
Cohesion: 0.25
Nodes (8): Motor Propio con Constructiva SA y LNS, T15 - manual-edit-lns, NeighborhoodRepairer, Busqueda de Gran Vecindario, Heuristica Constructiva, Heuristica Constructiva Recocido Simulado LNS, Pisinger and Ropke 2010, Recocido Simulado

### Community 26 - "Unassigned Session File"
Cohesion: 0.40
Nodes (6): JWT y Roles, POST /api/auth/login, POST /api/imports/academic-data, app_user, import_batch, import_error

### Community 27 - "Community 27"
Cohesion: 0.50
Nodes (5): Java 21 y Spring Boot 3, Motor Java Puro sin Spring, horarios-api, horarios-domain, horarios-solver

### Community 28 - "Community 28"
Cohesion: 0.14
Nodes (16): SecurityConfig, FilterRegistrationBean, Bean, ObjectMapper, PasswordEncoder, String, ExceptionHandler, ResponseStatus (+8 more)

### Community 29 - "Community 29"
Cohesion: 0.08
Nodes (23): API inicial del servidor, Arquitectura, Criterios de aceptación medibles, Decisión base, Estados del horario, Fase 1 del proyecto: dominio + constructiva, Fase 1: Heurística constructiva, Fase 2 del proyecto: optimización + pruebas de rendimiento (+15 more)

### Community 35 - "Community 35"
Cohesion: 0.50
Nodes (4): Burke and Petrovic 2002, Lewis 2008, Schaerf 1999, University Course Timetabling Problem

### Community 36 - "Community 36"
Cohesion: 0.67
Nodes (3): Maven Multi Modulo, Entrega Completa sin MVP, Plan Maestro de Implementacion

### Community 37 - "Community 37"
Cohesion: 0.67
Nodes (3): Caveman, gstack /browse, Ponytail

### Community 40 - "Community 40"
Cohesion: 0.11
Nodes (18): Backlog ejecutable para agentes AI, Orden de asignacion sugerido, Reglas globales, T01 - scaffold-repo, T03 - domain-core, T04 - schedule-indexes, T05 - prevalidation-session-factory, T07 - api-catalogs (+10 more)

### Community 41 - "Community 41"
Cohesion: 0.11
Nodes (19): Endpoints, GET /api/catalog/{resource}, GET /api/imports/{id}/errors, GET /api/me, GET /api/public/schedules/cohorts/{id}, GET /api/reports/schedule-plans/{id}.pdf, GET /api/reports/schedule-plans/{id}.xlsx, GET /api/schedule-plans (+11 more)

### Community 52 - "Community 52"
Cohesion: 0.11
Nodes (18): dependencies, react, react-dom, typescript, vite, @vitejs/plugin-react, devDependencies, @types/react (+10 more)

### Community 53 - "Community 53"
Cohesion: 0.11
Nodes (17): compilerOptions, allowJs, allowSyntheticDefaultImports, esModuleInterop, forceConsistentCasingInFileNames, isolatedModules, jsx, lib (+9 more)

### Community 54 - "Community 54"
Cohesion: 0.10
Nodes (19): `AdminShell`, Base de diseno UI, `CatalogPages`, Color, Componentes a construir, `ConflictPanel`, Direccion visual, Estados visuales (+11 more)

### Community 55 - "Community 55"
Cohesion: 0.12
Nodes (15): 10. Puntos para la defensa académica, 1. Naturaleza del problema, 2. Datos confirmados del cliente, 3. Comportamiento del sistema ya decidido, 4. Restricción técnica no negociable, 5. Equipo y entrega, 6. Stack técnico acordado, 7. Algoritmo elegido: Hybrid Metaheuristic (+7 more)

### Community 56 - "Community 56"
Cohesion: 0.10
Nodes (18): BadRequestException, NotFoundException, RequestValidationException, String, String, FieldErrorDto, List, AppUser (+10 more)

### Community 57 - "Community 57"
Cohesion: 0.13
Nodes (14): AssignmentDto, Catalog DTOs, Contratos API, Convenciones HTTP, Criterio de salida para agentes, DTOs comunes, ErrorResponse, Estados y transiciones (+6 more)

### Community 58 - "Community 58"
Cohesion: 0.13
Nodes (14): Catálogos académicos, DER resumido, Diseño de base de datos, Docentes, aulas y recursos, Edición manual y sustituciones, Importación, Planes, sesiones y corridas, Principios (+6 more)

### Community 59 - "Community 59"
Cohesion: 0.09
Nodes (17): CatalogControllerTest, String, Test, List, Map, String, Test, String (+9 more)

### Community 60 - "Community 60"
Cohesion: 0.19
Nodes (10): AcademicImportService, GetMapping, ImportErrorResponse, ImportMode, ImportResponse, MultipartFile, PageResponse, PostMapping (+2 more)

### Community 61 - "Community 61"
Cohesion: 0.43
Nodes (4): Connection, DatabaseMigrationTest, String, Test

### Community 62 - "Community 62"
Cohesion: 0.33
Nodes (5): ADR 0001 - Java 21 y Spring Boot 3, Consecuencias, Contexto, Decision, Estado

### Community 63 - "Community 63"
Cohesion: 0.33
Nodes (5): ADR 0002 - PostgreSQL y JSONB, Consecuencias, Contexto, Decision, Estado

### Community 64 - "Community 64"
Cohesion: 0.33
Nodes (5): ADR 0003 - Motor propio con constructiva, SA y LNS, Consecuencias, Contexto, Decision, Estado

### Community 65 - "Community 65"
Cohesion: 0.33
Nodes (5): ADR 0004 - Maven multi-modulo, Consecuencias, Contexto, Decision, Estado

### Community 66 - "Community 66"
Cohesion: 0.33
Nodes (5): ADR 0005 - Entrega completa sin MVP por fases, Consecuencias, Contexto, Decision, Estado

### Community 67 - "Community 67"
Cohesion: 0.33
Nodes (5): ADR 0006 - React, TypeScript y dnd-kit, Consecuencias, Contexto, Decision, Estado

### Community 68 - "Community 68"
Cohesion: 0.33
Nodes (5): ADR 0007 - JWT y roles, Consecuencias, Contexto, Decision, Estado

### Community 69 - "Community 69"
Cohesion: 0.33
Nodes (5): ADR 0008 - OpenPDF y Apache POI, Consecuencias, Contexto, Decision, Estado

### Community 70 - "Community 70"
Cohesion: 0.33
Nodes (5): Graphify, gstack, Instrucciones para Codex, Modo de comunicación, Modo de programación

### Community 71 - "Community 71"
Cohesion: 0.33
Nodes (5): Backend y motor, Frontend, Horarios UdeO/UTP, Regla de dependencias, Requisitos

### Community 77 - "Community 77"
Cohesion: 0.67
Nodes (3): requirePositive(), requireText(), String

### Community 81 - "Community 81"
Cohesion: 0.11
Nodes (10): api(), ApiError, Catalog, CATALOGS, Field, formatApiError(), PageResponse, Role (+2 more)

### Community 94 - "Community 94"
Cohesion: 0.15
Nodes (11): GenerationRequest, GenerationResponse, GetMapping, Long, PostMapping, ResultResponse, String, ValidationResponse (+3 more)

### Community 95 - "Community 95"
Cohesion: 0.33
Nodes (7): ErrorResponse, ExceptionHandler, HttpServletRequest, ResponseEntity, String, ScheduleApiException, ScheduleApiExceptionHandler

### Community 96 - "Community 96"
Cohesion: 0.60
Nodes (3): TimeConfig, Bean, Clock

### Community 97 - "Community 97"
Cohesion: 0.50
Nodes (3): AppUser, AppUserDto, from()

## Knowledge Gaps
- **336 isolated node(s):** `String`, `Long`, `Long`, `String`, `Long` (+331 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **16 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `schedule_plan` connect `No Timetabling Libraries` to `API Errors`, `Teacher Continuity`, `Project Planning`, `Frontend and LNS`?**
  _High betweenness centrality (0.128) - this node is a cross-community bridge._
- **Why does `HttpStatus` connect `Community 28` to `Hard Constraints`, `Scheduling Problem`, `C4 Architecture`, `Community 23`, `Community 59`?**
  _High betweenness centrality (0.100) - this node is a cross-community bridge._
- **What connects `String`, `Long`, `Long` to the rest of the system?**
  _346 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `API and Persistence` be split into smaller, more focused modules?**
  _Cohesion score 0.07918552036199095 - nodes in this community are weakly interconnected._
- **Should `Domain Assignments` be split into smaller, more focused modules?**
  _Cohesion score 0.09358974358974359 - nodes in this community are weakly interconnected._
- **Should `Schedule Indexes` be split into smaller, more focused modules?**
  _Cohesion score 0.08874912648497554 - nodes in this community are weakly interconnected._
- **Should `Prevalidation Tests` be split into smaller, more focused modules?**
  _Cohesion score 0.11092436974789915 - nodes in this community are weakly interconnected._