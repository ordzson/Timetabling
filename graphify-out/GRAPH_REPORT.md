# Graph Report - .  (2026-06-18)

## Corpus Check
- Corpus is ~19,261 words - fits in a single context window. You may not need a graph.

## Summary
- 94 nodes · 109 edges · 17 communities (12 shown, 5 thin omitted)
- Extraction: 79% EXTRACTED · 21% INFERRED · 0% AMBIGUOUS · INFERRED: 23 edges (avg confidence: 0.89)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Catalogo Academico|Catalogo Academico]]
- [[_COMMUNITY_Area Comun Curricula|Area Comun Curricula]]
- [[_COMMUNITY_Edicion Manual UI|Edicion Manual UI]]
- [[_COMMUNITY_Planificacion Persistida|Planificacion Persistida]]
- [[_COMMUNITY_Generacion Solver|Generacion Solver]]
- [[_COMMUNITY_Reportes Conflictos|Reportes Conflictos]]
- [[_COMMUNITY_Metaheuristica SA LNS|Metaheuristica SA LNS]]
- [[_COMMUNITY_Auth Importaciones|Auth Importaciones]]
- [[_COMMUNITY_Arquitectura Backend|Arquitectura Backend]]
- [[_COMMUNITY_Base Timetabling|Base Timetabling]]
- [[_COMMUNITY_Plan Entrega|Plan Entrega]]
- [[_COMMUNITY_Instrucciones Agente|Instrucciones Agente]]
- [[_COMMUNITY_Scaffold Repo|Scaffold Repo]]
- [[_COMMUNITY_Errores API|Errores API]]
- [[_COMMUNITY_Arquitectura C4|Arquitectura C4]]
- [[_COMMUNITY_Continuidad Docente|Continuidad Docente]]
- [[_COMMUNITY_Decision Solver Propio|Decision Solver Propio]]

## God Nodes (most connected - your core abstractions)
1. `schedule_plan` - 10 edges
2. `schedule_assignment` - 8 edges
3. `course` - 7 edges
4. `schedule_session` - 7 edges
5. `schedule_run` - 7 edges
6. `manual_edit` - 7 edges
7. `cohort` - 6 edges
8. `teacher` - 6 edges
9. `common_area_rule` - 6 edges
10. `journey` - 5 edges

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

## Hyperedges (group relationships)
- **Scheduling Persistence Flow** — docs_database_schedule_plan, docs_database_schedule_session, docs_database_schedule_run, docs_database_schedule_assignment, docs_database_schedule_violation, docs_database_manual_edit [EXTRACTED 1.00]
- **Solver Algorithm Pipeline** — horarios_heuristica_constructiva, horarios_recocido_simulado, horarios_busqueda_gran_vecindario, docs_motor_generador_plan_candidate_space, docs_motor_generador_plan_hard_constraint_checker, docs_motor_generador_plan_score [EXTRACTED 1.00]
- **Frontend API Database Contract** — docs_ui_design_base_schedule_grid, docs_ui_design_base_conflict_panel, docs_api_contracts_result_endpoint, docs_api_contracts_manual_edits_endpoint, docs_database_schedule_assignment, docs_database_schedule_violation [INFERRED 0.85]

## Communities (17 total, 5 thin omitted)

### Community 0 - "Catalogo Academico"
Cohesion: 0.14
Nodes (16): /api/catalog/{resource}, POST /api/substitutions, course_required_resource, fixed_break, journey, resource, room, room_resource (+8 more)

### Community 1 - "Area Comun Curricula"
Cohesion: 0.27
Nodes (12): career, cohort, common_area_career, common_area_rule, course, curriculum, curriculum_course, schedule_session (+4 more)

### Community 2 - "Edicion Manual UI"
Cohesion: 0.18
Nodes (11): React TypeScript dnd-kit shadcn/ui, T15 manual-edit-lns, Idempotency-Key, POST /api/schedule-plans/{id}/manual-edits, GET /api/schedule-plans/{id}/result, manual_edit, ManualEditCommand, NeighborhoodRepairer (+3 more)

### Community 3 - "Planificacion Persistida"
Cohesion: 0.25
Nodes (8): PostgreSQL y JSONB, T02 database-migration, exam_plan view, pre_validation_issue, schedule_plan, ProblemPreValidator, SchedulingContext, Estados del Horario

### Community 4 - "Generacion Solver"
Cohesion: 0.25
Nodes (8): T06 constructive-solver, POST /api/schedule-plans/{id}/generate, CandidateSpace, HardConstraintChecker, SchedulableSession, Schedule, ScheduleProblem, AdminShell

### Community 5 - "Reportes Conflictos"
Cohesion: 0.29
Nodes (7): OpenPDF y Apache POI, T13 solver-sa, GET /api/reports/schedule-plans/{id}, schedule_run, schedule_violation, Score, ConflictPanel

### Community 6 - "Metaheuristica SA LNS"
Cohesion: 0.33
Nodes (6): Motor Propio con Constructiva SA y LNS, Busqueda de Gran Vecindario, Heuristica Constructiva, Heuristica Constructiva Recocido Simulado LNS, Pisinger and Ropke 2010, Recocido Simulado

### Community 7 - "Auth Importaciones"
Cohesion: 0.40
Nodes (6): JWT y Roles, POST /api/imports/academic-data, POST /api/auth/login, app_user, import_batch, import_error

### Community 8 - "Arquitectura Backend"
Cohesion: 0.50
Nodes (5): Java 21 y Spring Boot 3, Motor Java Puro sin Spring, horarios-api, horarios-domain, horarios-solver

### Community 9 - "Base Timetabling"
Cohesion: 0.50
Nodes (4): Burke and Petrovic 2002, Lewis 2008, Schaerf 1999, University Course Timetabling Problem

### Community 10 - "Plan Entrega"
Cohesion: 0.67
Nodes (3): Maven Multi Modulo, Entrega Completa sin MVP, Plan Maestro de Implementacion

### Community 11 - "Instrucciones Agente"
Cohesion: 0.67
Nodes (3): Caveman, gstack /browse, Ponytail

## Knowledge Gaps
- **29 isolated node(s):** `Caveman`, `gstack /browse`, `Area Comun`, `Continuidad Docente Intra Carrera`, `Heuristica Constructiva` (+24 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **5 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `schedule_run` connect `Reportes Conflictos` to `Catalogo Academico`, `Edicion Manual UI`, `Planificacion Persistida`, `Generacion Solver`?**
  _High betweenness centrality (0.227) - this node is a cross-community bridge._
- **Why does `manual_edit` connect `Edicion Manual UI` to `Area Comun Curricula`, `Planificacion Persistida`, `Reportes Conflictos`, `Auth Importaciones`?**
  _High betweenness centrality (0.207) - this node is a cross-community bridge._
- **Why does `schedule_assignment` connect `Catalogo Academico` to `Area Comun Curricula`, `Edicion Manual UI`, `Reportes Conflictos`?**
  _High betweenness centrality (0.175) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `schedule_plan` (e.g. with `PostgreSQL y JSONB` and `Estados del Horario`) actually correct?**
  _`schedule_plan` has 2 INFERRED edges - model-reasoned connections that need verification._
- **Are the 2 inferred relationships involving `schedule_run` (e.g. with `GET /api/reports/schedule-plans/{id}` and `Score`) actually correct?**
  _`schedule_run` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Caveman`, `gstack /browse`, `Area Comun` to the rest of the system?**
  _39 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Catalogo Academico` be split into smaller, more focused modules?**
  _Cohesion score 0.14166666666666666 - nodes in this community are weakly interconnected._