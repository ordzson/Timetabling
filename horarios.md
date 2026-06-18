# Contexto del proyecto — Sistema de Generación de Horarios UdeO/UTP

Estoy planificando un proyecto académico del curso **Ingeniería de Software I** en la Universidad de Occidente (UdeO/UTP), Quetzaltenango, Guatemala. Necesito continuar desde un punto de planificación ya avanzado. Arranca asumiendo que TODO lo que sigue ya está decidido y acordado, y no vuelvas a cuestionar estas decisiones salvo que yo te lo pida explícitamente.

## 1. Naturaleza del problema

Es un **University Course Timetabling Problem (UCTP)** con particularidades no triviales:
- Restricciones duras estándar: sin solapes de docente, sin solapes de aula, capacidad, equipamiento (labs).
- Restricciones duras no estándar:
  - **Área común**: un mismo docente imparte simultáneamente el mismo contenido a varias carreras. Debe modelarse como **una sola sesión física** (un docente, un aula, un bloque horario) cuyos estudiantes son de múltiples cohortes. Esa sesión bloquea el bloque en los horarios de todas las carreras involucradas, por ese semestre.
  - **Continuidad docente intra-carrera**: un mismo catedrático debe dar todos los periodos de una materia para una carrera específica. Distintos catedráticos pueden dar un curso con el mismo nombre en carreras distintas.
  - **Cobertura completa por jornada**: cada jornada activa de una carrera (matutino, vespertino, fin de semana, etc.) debe tener el pensum completo. Una carrera puede tener varias jornadas; el pensum es el mismo en todas.
  - **Migración de pensum**: coexistencia de pensums viejos/nuevos con phase-out progresivo (ej. pensum 2022 convive con pensum 2026 hasta que los estudiantes de pensum viejo egresen).
- Restricciones blandas, **en este orden de prioridad**:
  1. Disponibilidad docente respetada + bloques consecutivos del mismo curso juntos
  2. Sin ventanas muertas; si las hay, que queden al final de la jornada
  3. Minimizar caminata entre aulas consecutivas
  4. Balance de carga entre docentes (se puede ajustar manualmente)

## 2. Datos confirmados del cliente

- **Estructura temporal**: bloques de 45 min por defecto, configurables (cualquier n minutos). Una vez establecida la duración de bloque para una jornada, se respeta en todos los bloques de esa jornada. Cursos tienen mínimo ~3 bloques semanales, pero es variable (n min, n max configurables por admin/superadmin). Se prefiere fuertemente que los bloques de un mismo curso queden **consecutivos**.
- **Horario semanal único**: el sistema genera UNA plantilla semanal que se repite todas las semanas del ciclo académico (semestre/bimestre), no un calendario de todas las semanas. El ciclo tiene fecha inicio y fecha fin.
- **Conflictos de estudiantes**: modelo **por cohorte**, no individual. "Software 3er año sección A pensum 2022 jornada matutina" es un grupo que lleva los mismos cursos. Estudiantes con cursos atrasados se adaptan al horario existente; el sistema no les genera horario individual.
- **Aulas**: ~20 aulas iniciales, mitad capacidad 60 y mitad capacidad 30. Posición en formato coordenada piso+número (ej. 301 = aula 1 piso 3). Objetivo: minimizar "casillas" de desplazamiento entre aulas consecutivas (no requiere exactitud métrica).
- **Secciones**: nombradas A, B, C. El sistema **sugiere** crear nueva sección si capacidad excede demanda, pero NO la crea automáticamente — requiere autorización.
- **Capacidad**: las cohortes registran estudiantes esperados. El motor puede generar `SectionSuggestion` cuando la demanda supera aulas compatibles.
- **Datos de escala**: ~20 carreras. Licenciaturas/Ingenierías 45-60 cursos totales (9-10 semestres, 5-6 cursos/semestre). Técnicos 30-35 cursos. Maestrías 12-16 cursos. No hay Excel actual para analizar — cada carrera hace su horario por separado.
- **Docentes**: atributos = nombre, cursos que puede dar (lo define admin, no el docente), prioridad/preferencia (más preferencia → más cursos asignados), disponibilidad horaria (la registra el docente en su portal). Mínimo 1 curso, máximo 4-6 cursos (configurable).
- **Laboratorios**: algunos cursos requieren laboratorio específico. Debe registrarse el requerimiento y evitar conflicto con otro curso que también lo necesite.

## 3. Comportamiento del sistema ya decidido

- **Edición manual**: permite violar restricciones duras temporalmente. Si la edición no genera conflictos, se mantiene tal cual. Si genera conflictos, se **muestran** y el sistema **regenera el vecindario afectado** (Búsqueda de Gran Vecindario con fijación de la edición del usuario).
- **Infeasibilidad**: el sistema **siempre entrega el mejor horario parcial posible** + reporte de qué no se pudo asignar. Nunca falla silenciosamente.
- **Sustituciones**: el horario aprobado NO cambia. Las sustituciones (temporales o permanentes) se registran como eventos separados sin alterar la plantilla base.
- **Roles**: Superadmin, Coordinador académico (admin), Docente (solo ve su horario + registra disponibilidad), Alumno (solo ve su horario).
- **Reportes**: PDF, Excel, vista web.
- **Integración**: debe aceptar importación CSV/XLSX desde el día 1. API para el futuro.
- **Pre-validación**: antes de generar, el sistema detecta datos bloqueantes como cursos sin docente, laboratorios inexistentes, exceso de bloques por jornada o áreas comunes sin aula suficiente.
- **Estados del horario**: el plan pasa por `DRAFT`, `VALIDATING`, `INVALID_INPUT`, `GENERATING`, `GENERATED`, `GENERATED_WITH_CONFLICTS`, `APPROVED`, `LOCKED`, `ARCHIVED`.

## 4. Restricción técnica no negociable

El proyecto **NO permite usar librerías que ataquen directamente el problema de generación de horarios**. Esto descarta: OR-Tools CP-SAT, Timefold, OptaPlanner, UniTime, FET, o cualquier motor específico de horarios/planificación. Sí se pueden usar frameworks/librerías genéricos de todo tipo (web, ORM, interfaz, matemática general, pruebas, etc.). El motor de optimización se implementa desde cero.

## 5. Equipo y entrega

- **Equipo**: 4-5 personas (número final pendiente). Stack acordado: Java. Flexibles para cualquier tech estándar si el proyecto lo exige.
- **Entregable**: sistema completo, no MVP por fases. Un solo entregable final.
- **División sugerida (5 personas)**:
  - 1 líder técnico (revisión, arquitectura)
  - 2 en motor de optimización y servidor de horarios
  - 1 en interfaz/UX (crítico por la edición manual)
  - 1 transversal (CRUDs, autenticación, reportes, DevOps)

## 6. Stack técnico acordado

- **Servidor**: Java 21 + Spring Boot 3
- **Motor de optimización**: módulo Java separado dentro del mismo repositorio, sin depender de Spring, testeable aislado
- **Interfaz**: React + TypeScript + `dnd-kit` (arrastrar y soltar) + `shadcn/ui`
- **Base de datos**: PostgreSQL (con JSONB para disponibilidad docente y preferencias flexibles)
- **Construcción**: Maven multi-módulo
- **Reportes**: OpenPDF para PDF, Apache POI para Excel
- **Auth**: Spring Security + JWT
- **Pruebas**: JUnit 5
- **CI/CD**: GitHub Actions
- **Despliegue**: Docker en nube (Azure App Service, DigitalOcean, Hetzner o similar)

## 7. Algoritmo elegido: Hybrid Metaheuristic

**Nombre formal**: Heurística Constructiva + Recocido Simulado + Búsqueda de Gran Vecindario (metaheurística híbrida/memética). Es UN algoritmo integrado académicamente, aunque internamente tenga tres fases con una única función objetivo y un único bucle de mejora.

### Justificación académica (para la defensa)

UCTP es NP-hard (reducible desde graph coloring). Por eso no se busca óptimo exacto sino buena solución en tiempo razonable.

Alternativas descartadas:
- **MILP (Gurobi/CPLEX)**: requiere librería comercial prohibida
- **Backtracking/CSP puro**: escala mal a ~900 asignaciones
- **Algoritmos Genéticos**: convergencia más lenta que SA en UCTP según literatura; operadores crossover difíciles sin violar duras
- **Tabu Search puro**: más hiperparámetros que SA, menos justificación teórica limpia
- **SA puro**: bueno pero sin mecanismo natural para reparar ediciones manuales

Referencias clave: Burke & Petrovic 2002 (surveys UCTP), Schaerf 1999, Lewis 2008 (survey timetabling), Pisinger & Ropke 2010 (LNS).

### Fase 1 — Heurística constructiva

```
Entrada: sesiones a programar, restricciones duras
Salida: horario inicial factible (o parcialmente factible)

1. Precomputar `CandidateSpace` por sesión:
   docentes habilitados, aulas compatibles y bloques válidos sin `FixedBreak`.

2. Ordenar sesiones por dificultad (Most Constrained First) con comparador
   determinista:
   candidateCount asc
   possibleTeacherCount asc
   compatibleRoomCount asc
   validStartCount asc
   es_area_común desc
   requiere_lab desc
   cohortes_afectadas desc
   duración desc
   sessionId asc

3. Para cada sesión s en ese orden:
     candidatos = CandidateSpace(s) filtrado contra el Schedule actual
     Si candidatos vacío:
       marcar s como NO_ASIGNABLE con razón técnica y continuar
     Sino:
       escoger candidato por menor costo blando incremental
       desempatar por menor pérdida de opciones futuras locales
       asignar con Schedule.addAssignment

4. Retornar horario + lista de no asignables
```

Tiempo esperado: 1-2 segundos para ~900 asignaciones.

### Fase 2 — Recocido Simulado

```
Entrada: horario inicial
Salida: horario optimizado

T ← T_inicial (100.0)
α ← 0.995 (enfriamiento)
iter_max ← 50000
mejor ← horario_inicial

Mientras T > T_min y no se alcance iter_max:
  vecino ← aplicar_movimiento_aleatorio(horario_actual)
  Δ ← costo(vecino) - costo(horario_actual)
  Si Δ < 0:
    horario_actual ← vecino
    Si costo(vecino) < costo(mejor): mejor ← vecino
  Sino:
    p ← exp(-Δ / T)
    Si random() < p: horario_actual ← vecino
  T ← T × α

Retornar mejor
```

**Movimientos (vecindario)**:
- `MoverSesion(s, nuevo_slot)`
- `CambiarAula(s, nueva_aula)`
- `IntercambiarSlots(s1, s2)`
- `CambiarDocenteGrupo(g, nuevo_docente)` — respeta continuidad docente del `SessionGroup`
- `MoverBloque(s1, s2, ..., sn)` — mover varias sesiones consecutivas juntas (clave para bloques consecutivos del mismo curso)
- `AsignarNoAsignada(s)` — intenta recuperar sesiones que fase 1 dejó `UNASSIGNED`

**Función de costo**:
```
costo(H) = 
    w1 × violaciones_duras(H)                      [w1 = 100000]
  + w2 × horas_docente_fuera_de_disponibilidad(H)  [w2 = 1000]
  + w3 × sesiones_mismo_curso_no_contiguas(H)      [w3 = 500]
  + w4 × ventanas_no_al_final_jornada(H)           [w4 = 100]
  + w5 × distancia_total_caminata(H)               [w5 = 10]
  + w6 × desviación_carga_docentes(H)              [w6 = 5]
```

En modo normal w1=0 porque construimos factible y rechazamos movimientos que violen duras. En modo de respaldo (infeasibilidad), w1 finito alto para minimizar violaciones.

### Fase 3 — LNS para edición manual

```
Entrada: horario aprobado H, edición manual (sesión s movida a bloque X)
Salida: horario actualizado H'

1. Aplicar edición: H' ← H con s en bloque X
2. Marcar s como PINNED
3. Detectar conflictos generados → conjunto C
4. Si C vacío: Retornar H' (edición limpia)
5. Destruir: quitar de H' sesiones en C + radio R de cercanía 
   (misma franja/día/aula/docente), típicamente 5-15 sesiones
6. Reconstruir: mini-SA solo sobre las destruidas, con sesiones fijadas
   - iter_max = 5000, enfriamiento más agresivo
7. Persistir siempre un nuevo `schedule_run`; el horario aprobado base queda
   inmutable
8. Retornar H' reconstruido o parcial con conflictos restantes
```

Respuesta esperada: 200-500 ms. Se siente instantáneo al usuario.

## 8. Modelo de datos (entidades core)

- **Jornada**: matutino/vespertino/fin de semana/etc., duración de bloque configurable
- **PlanHorario**: semestre/bimestre con fechas inicio/fin
- **Carrera** → múltiples **Cohortes** (carrera + año + sección + pensum + jornada)
- **Cohorte**: incluye estudiantes esperados para capacidad y sugerencia de secciones
- **Pensum**: año, cursos con su semestre de cursado
- **Curso**: id, nombre, requiere_lab, es_area_común, carreras_comparten[]
- **Docente**: disponibilidad (matriz día×bloque booleana), prioridad, cursos_que_puede_dar[]
- **Aula**: capacidad, tipo (teórica/laboratorio), coordenadas (piso, número)
- **Sesion** (unidad atómica del motor): curso + cohorte(s) + docente + aula + día + slot_inicio + duración_en_bloques
- **Asignacion** (pre-scheduling): quién puede dar qué (lo define admin)
- **SugerenciaSeccion**: recomendación de nueva sección por capacidad, requiere autorización
- **PreValidationIssue**: error/advertencia de datos antes del motor
- **EventoSustitucion**: cambios temporales/permanentes sin modificar horario base

Las sesiones de área común tienen **múltiples cohortes** asociadas — es la única entidad que rompe la simetría carrera-cohorte.

## 9. Próximos pasos acordados (en este orden)

1. **Plan maestro de implementación**: `docs/project-implementation-plan.md`.
2. **Motor generador**: `docs/motor-generador-plan.md`.
3. **Base de datos y DER**: `docs/database-design.md` + `docs/database.sql`.
4. **ADRs**: decisiones base documentadas en `docs/adr/`.
5. **Diagrama visual del flujo del algoritmo** (las 3 fases) para explicarlo al equipo.

Orden de lectura para agentes AI:

1. `horarios.md`
2. `docs/project-implementation-plan.md`
3. `docs/agent-task-backlog.md`
4. `docs/database-design.md`
5. `docs/motor-generador-plan.md`
6. `docs/database.sql`
7. `docs/adr/` si la tarea cambia una decisión técnica.

Regla de ejecución: cada agente debe tomar una tarea pequeña del plan maestro,
leer su contexto obligatorio, tocar solo los archivos esperados y cerrar con
verificación runnable.

## 10. Puntos para la defensa académica

En el informe debe cubrirse:
1. Complejidad NP-hard del problema (reducción desde graph coloring)
2. Tabla de comparación con alternativas descartadas + citas a literatura
3. Justificación de hiperparámetros (T inicial, α, pesos) vía búsqueda en cuadrícula experimental
4. Pruebas de rendimiento sobre 3-5 escenarios sintéticos: pequeño (50 sesiones), mediano (300), grande (900). Medir: (a) tiempo a primera factible, (b) calidad final vs tiempo, (c) comportamiento en infeasibilidad forzada
5. Métricas por categoría (violaciones separadas, no solo costo agregado)

---
