# Plan del motor generador de horarios

Proyecto: Sistema de Generación de Horarios UdeO/UTP  
Alcance actual: servidor sólido, motor reproducible y contratos listos para una interfaz futura  
Pila tecnológica acordada: Java 21 + Spring Boot 3 + Maven + PostgreSQL + JUnit 5

## Decisión base

Java queda como decisión técnica del proyecto:

- Java 21 da POO limpia, rendimiento estable, buen perfilado y concurrencia madura.
- Spring Boot cubre API, validación, tareas, seguridad y observabilidad sin inventar infraestructura.
- PostgreSQL queda igual. JSONB sirve para disponibilidad docente y preferencias flexibles.
- El motor vive como módulo puro, sin depender de Spring, para probarlo aislado.
- No se usa OR-Tools, Timefold, OptaPlanner, UniTime, FET ni motor específico.
- La interfaz puede cambiar después; el contrato del servidor no debe depender de React.

Pila mínima:

- `horarios-domain`: entidades, objetos de valor, reglas de negocio.
- `horarios-solver`: heurística constructiva, recocido simulado, LNS, puntuación.
- `horarios-api`: Spring Boot REST API, autenticación, persistencia, importaciones, reportes.
- `horarios-testkit`: generadores de escenarios sintéticos pequeños/medianos/grandes.

Construcción:

- Maven multi-módulo.
- `horarios-solver` y `horarios-domain` no dependen de Spring.
- `horarios-api` depende de Spring Boot, JPA, PostgreSQL, OpenPDF y Apache POI.

Principio técnico: el motor no debe saber si agenda clases o exámenes. Agenda
`SchedulableSession`; clases y exámenes son variantes con reglas distintas.

## Arquitectura

```text
Cliente futuro
   |
   v
Spring Boot API
   |
   +-- Servicios de aplicación
   |     +-- GenerateScheduleService
   |     +-- RepairScheduleService
   |     +-- ImportAcademicDataService
   |
   +-- Adaptadores de persistencia
   |     +-- PostgreSQL
   |     +-- Importación CSV/XLSX
   |
   v
Núcleo puro del motor
   |
   +-- Modelo de dominio
   +-- Validaciones de restricciones
   +-- Función de costo
   +-- Fase 1 Constructiva
   +-- Fase 2 Recocido Simulado
   +-- Fase 3 Reparación LNS
```

Regla: `horarios-solver` no conoce HTTP, base de datos, Spring ni archivos. Recibe `ScheduleProblem`, devuelve `ScheduleResult`.

## Modelo POO del motor

Entidades principales:

- `ScheduleProblem`: entrada completa del motor para un `PlanHorario`.
- `Schedule`: asignaciones actuales + no asignables + sesiones fijadas. Es la
  única fuente mutable del motor: encapsula sus índices por sesión, docente,
  aula y cohorte; solo se modifica con `addAssignment`, `removeAssignment` y
  `moveAssignment`. `HardConstraintChecker` valida antes de mutar; `Schedule`
  rechaza inconsistencias internas y actualiza índices de forma atómica.
- `SchedulableSession`: unidad atómica que el motor agenda.
- `SessionGroup`: agrupa sesiones del mismo curso/oferta que deben mantener docente.
- `ClassSession`: sesión de clase; puede tener una o varias cohortes.
- `ExamSession`: sesión de examen; puede agrupar cohortes y requerir duración distinta.
- `Cohort`: carrera + semestre/año + sección + pensum + jornada + estudiantes esperados.
- `Course`: nombre, duración semanal, requiere laboratorio, área común, bloques min/max.
- `Teacher`: disponibilidad, prioridad, cursos habilitados, carga min/max.
- `Room`: capacidad, tipo, coordenada piso/número.
- `TimeSlot`: minuto absoluto dentro de la semana + jornada/bloque derivado para
  UI. Evita asumir que una asignación nunca cruza medianoche.
- `TimeGrid`: bloques válidos de una jornada según duración de bloque.
- `FixedBreak`: recreo fijo; bloquea bloques para todas las carreras seleccionadas.
- `SchedulingContext`: modo `CLASSES` o `EXAMS`, jornadas, cohortes y restricciones activas.
- `Assignment`: session + teacher + room + startSlot + duration.
- `Candidate`: teacher + room + startSlot + duration para una sesión.
- `CandidateSpace`: candidatos estáticos precomputados por sesión antes de
  mutar el horario.
- `UnassignedSession`: sesión + razón técnica (`NO_TEACHER`, `NO_ROOM`,
  `NO_TIME`, `HARD_CONFLICT`) + candidatos descartados relevantes.
- `ConstraintViolation`: tipo, severidad, sesiones afectadas, mensaje.
- `Score`: costo total + métricas separadas.
- `SectionSuggestion`: cohorte afectada, demanda, capacidad disponible, sección sugerida, razón.
- `PreValidationIssue`: error o advertencia antes de generar.

Ejemplo mínimo de estructura interna:

```java
record TimeRange(int startMinuteOfWeek, int durationMinutes) {
  int endMinuteOfWeek() {
    return startMinuteOfWeek + durationMinutes;
  }

  boolean overlaps(TimeRange other) {
    return startMinuteOfWeek < other.endMinuteOfWeek()
        && other.startMinuteOfWeek() < endMinuteOfWeek();
  }
}

record Assignment(
    long sessionId,
    long teacherId,
    long roomId,
    List<Long> cohortIds,
    TimeRange time
) {}

final class Schedule {
  private final Map<Long, Assignment> bySession = new HashMap<>();
  private final Map<Long, List<Assignment>> byTeacher = new HashMap<>();
  private final Map<Long, List<Assignment>> byRoom = new HashMap<>();
  private final Map<Long, List<Assignment>> byCohort = new HashMap<>();

  void addAssignment(Assignment assignment) {
    if (bySession.containsKey(assignment.sessionId())) {
      throw new IllegalArgumentException("Session already assigned");
    }

    bySession.put(assignment.sessionId(), assignment);
    byTeacher.computeIfAbsent(assignment.teacherId(), id -> new ArrayList<>()).add(assignment);
    byRoom.computeIfAbsent(assignment.roomId(), id -> new ArrayList<>()).add(assignment);

    for (long cohortId : assignment.cohortIds()) {
      byCohort.computeIfAbsent(cohortId, id -> new ArrayList<>()).add(assignment);
    }
  }

  void removeAssignment(long sessionId) {
    Assignment assignment = bySession.remove(sessionId);
    if (assignment == null) {
      return;
    }

    removeIndexed(byTeacher, assignment.teacherId(), assignment);
    removeIndexed(byRoom, assignment.roomId(), assignment);

    for (long cohortId : assignment.cohortIds()) {
      removeIndexed(byCohort, cohortId, assignment);
    }
  }

  private void removeIndexed(Map<Long, List<Assignment>> index, long key, Assignment assignment) {
    List<Assignment> assignments = index.get(key);
    if (assignments == null) {
      return;
    }

    assignments.remove(assignment);
    if (assignments.isEmpty()) {
      index.remove(key);
    }
  }

  List<Assignment> teacherSchedule(long teacherId) {
    return List.copyOf(byTeacher.getOrDefault(teacherId, List.of()));
  }

  List<Assignment> cohortSchedule(long cohortId) {
    return List.copyOf(byCohort.getOrDefault(cohortId, List.of()));
  }
}
```

Vistas de horario:

```java
List<Assignment> careerSchedule(long careerId) {
  return cohortsByCareer(careerId).stream()
      .flatMap(cohort -> schedule.cohortSchedule(cohort.id()).stream())
      .toList();
}

List<Assignment> teacherSchedule(long teacherId) {
  return schedule.teacherSchedule(teacherId);
}

List<Assignment> studentSchedule(long studentId) {
  Student student = studentById(studentId);
  return schedule.cohortSchedule(student.cohortId());
}
```

Servicios/clases del motor:

- `SessionFactory`: expande pensum/cohortes/jornadas a sesiones requeridas.
- `ExamSessionFactory`: crea sesiones de examen desde cursos activos o asignaciones aprobadas.
- `CommonAreaMerger`: une sesiones de área común en una sola sesión física.
- `ProblemPreValidator`: detecta datos imposibles antes de correr el motor.
- `DifficultyRanker`: ordena sesiones con `Most Constrained First` determinista.
- `CandidateGenerator`: genera `CandidateSpace` con docentes, aulas y bloques
  estáticamente compatibles.
- `HardConstraintChecker`: docente, aula, cohorte, capacidad, laboratorio, jornada.
- `GlobalTeacherCalendar`: vista de ocupación docente derivada de `Schedule`,
  nunca una segunda fuente mutable.
- `TimeGridBuilder`: genera bloques y bloquea recreos fijos por jornada.
- `RoomDistanceCalculator`: calcula distancia blanda entre aulas consecutivas.
- `IncrementalSoftScorer`: costo blando incremental + pequeño look-ahead local.
- `ConstructiveScheduler`: fase 1.
- `AnnealingOptimizer`: fase 2.
- `NeighborhoodRepairer`: fase 3 LNS.
- `ManualEditApplier`: valida y aplica una edición manual sobre una copia de
  trabajo, nunca sobre el `schedule_run` aprobado.
- `NeighborhoodSelector`: elige el vecindario mínimo afectado usando índices de
  docente, aula, cohorte, grupo de sesión y cercanía horaria.
- `ScheduleTransaction`: acumula deltas reversibles para que una edición o
  reparación fallida no deje índices internos corruptos.
- `MoveGenerator`: movimientos de vecindario.
- `BenchmarkRunner`: pequeño/mediano/grande + infeasibilidad forzada.
- `ImportValidator`: valida CSV/XLSX y devuelve errores por fila antes de persistir.

## Flujo del algoritmo

```text
Datos académicos
   |
   v
Validación y normalización
   |
   v
Crear `TimeGrid` por jornada y bloquear `FixedBreak`
   |
   v
Crear sesiones de clase o examen
   |
   v
Unir área común
   |
   v
Fase 1: construir horario parcial/factible
   |
   v
Fase 2: optimizar con SA
   |
   v
Resultado aprobado
   |
   +-- Reporte: puntuación, violaciones, no asignables
   |
   v
Fase 3: reparar vecindario tras edición manual
```

## Pre-validación antes del motor

Objetivo: fallar temprano en datos claramente malos y no gastar tiempo del
motor explicando problemas obvios.

Errores bloqueantes:

- curso activo sin docente habilitado;
- curso con laboratorio requerido sin aula/laboratorio compatible;
- cohorte con más bloques requeridos que bloques disponibles en su jornada;
- área común cuya demanda total excede toda aula compatible;
- docente sin disponibilidad registrada en jornadas donde debe impartir;
- pensum activo sin cursos o con cursos duplicados en el mismo semestre;
- duración de curso no divisible en bloques válidos;
- `FixedBreak` fuera del rango de la jornada.

Advertencias útiles:

- docente con carga potencial mayor al máximo configurado;
- capacidad cercana al límite;
- curso con un solo docente posible;
- aula/laboratorio crítico usado por muchos cursos;
- datos importados con nombres normalizados o coincidencias aproximadas.

Salida:

- `List<PreValidationIssue>` con severidad, entidad, fila de importación si aplica,
  mensaje claro y acción sugerida.
- Si hay errores bloqueantes, no se corre el motor.
- Si solo hay advertencias, se permite generar y se guardan con el `schedule_run`.

## Fase 1: Heurística constructiva

Objetivo: primera solución factible o mejor parcial posible.

Entrada:

- Sesiones generadas.
- Docentes, aulas, jornadas, bloques.
- `TimeGrid` con recreos fijos ya bloqueados.
- `Schedule` con calendario docente global derivado de sus índices.
- Reglas duras.

Proceso:

1. Normalizar datos.
2. Crear bloques por jornada con longitud configurable.
3. Bloquear `FixedBreak` para todas las carreras seleccionadas.
4. Construir sesiones por cohorte y pensum activo.
5. Fusionar área común antes de ordenar.
6. Precomputar `CandidateSpace` por sesión:
   - docentes habilitados y disponibles para el curso;
   - aulas compatibles por tipo, capacidad y recursos;
   - inicios válidos por jornada/duración, excluyendo `FixedBreak`;
   - razón temprana si falta docente, aula o bloque.
7. Ordenar por dificultad con comparador lexicográfico determinista:

```text
dificultad(s) =
  candidateCount asc
  possibleTeacherCount asc
  compatibleRoomCount asc
  validStartCount asc
  isCommonArea desc
  requiresLab desc
  affectedCohortCount desc
  durationBlocks desc
  sessionId asc
```

`sessionId asc` evita empates no deterministas. Cualquier desempate aleatorio
debe usar la `seed` de la corrida.

8. Para cada sesión:
   - leer su `CandidateSpace`;
   - filtrar restricciones duras dinámicas contra índices de `Schedule`;
   - rankear candidatos por:
     1. menor costo blando incremental;
     2. menor pérdida de opciones para sesiones restantes cercanas
        (mismo docente, aula, cohorte o franja);
     3. desempate determinista por ids;
   - si no hay candidato dinámicamente válido, marcar `UNASSIGNED` con razón;
   - si hay candidato, aplicar `Schedule.addAssignment` una sola vez.

Reglas de implementación:

- `ConstructiveScheduler` solo orquesta: ordena, pide candidatos, asigna y
  registra no asignables.
- `Schedule` mantiene los índices; `GlobalTeacherCalendar` es una vista, no se
  actualiza por separado.
- `HardConstraintChecker` no muta estado.
- `IncrementalSoftScorer` no recalcula todo el horario; solo mira asignaciones
  del docente, aula y cohortes afectadas.
- La fase 1 no reintenta backtracking completo. Si una sesión queda
  `UNASSIGNED`, fase 2/modo respaldo o LNS reparan después.

Salida:

- `Schedule` parcial o factible.
- `List<UnassignedSession>`.
- `ScoreBreakdown`.

Verificaciones mínimas:

- área común produce una sola `Session` con múltiples cohortes;
- docente no solapa dos sesiones;
- aula no solapa dos sesiones;
- docente no solapa aunque las sesiones sean de carreras distintas;
- ninguna sesión cae en `FixedBreak`;
- sesión sin aula compatible queda no asignable, no falla.
- dos ejecuciones con misma entrada, `seed` y version producen el mismo
  horario inicial.

## Fase 2: Recocido Simulado

Objetivo: mejorar calidad sin romper duras en modo normal.

Entrada:

- `Schedule` de fase 1.
- Configuración: `initialTemperature`, `coolingRate`, `maxIterations`, pesos.

Diseño POO:

- `AnnealingOptimizer`: orquesta temperatura, aceptación, mejor global y límite
  de tiempo.
- `MoveGenerator`: elige movimientos con `SplittableRandom` sembrado; nunca muta
  `Schedule`.
- `MoveProposal`: describe un cambio reversible mínimo:
  - sesiones afectadas;
  - asignaciones que salen;
  - asignaciones que entran;
  - razón de rechazo si no aplica.
- `MoveEvaluator`: valida duras y calcula `ScoreDelta`.
- `MoveApplier`: aplica o revierte un `MoveProposal` usando solo
  `Schedule.addAssignment`, `removeAssignment` y `moveAssignment`.
- `PinnedSessionGuard`: rechaza movimientos sobre sesiones fijadas; lo usa la
  misma fase 2 cuando se ejecuta como mini-SA dentro de LNS.

Regla de eficiencia: no copiar todo el horario por iteración. Cada iteración
trabaja con un `MoveProposal`, calcula delta local y muta el `Schedule` solo si
el movimiento fue aceptado. El mejor global se copia solo cuando mejora.

Movimientos:

- `MoveSession`: cambia día/bloque.
- `ChangeRoom`: cambia aula.
- `SwapSlots`: intercambia dos sesiones.
- `ChangeTeacherGroup`: cambia docente habilitado para todo el `SessionGroup`
  cuando `continuityTeacher = true`; si no, puede cambiar una sesión individual.
- `MoveBlock`: mueve bloque contiguo del mismo curso.
- `AssignUnassigned`: intenta insertar una sesión `UNASSIGNED` si aparece un
  candidato válido durante la mejora.

Generación de vecinos:

1. Elegir tipo de movimiento con pesos configurables simples.
2. Preferir sesiones con peor contribución local al costo.
3. Probar hasta `maxMoveAttempts` propuestas por iteración.
4. Si ninguna propuesta válida aparece, enfriar y continuar; si esto se repite
   muchas veces, terminar temprano.

Reglas duras en modo normal:

- `HardConstraintChecker.canApply(schedule, proposal)` revisa solo docentes,
  aulas y cohortes afectadas por el delta.
- Si el movimiento crea solape, excede capacidad, rompe recurso/laboratorio,
  usa `FixedBreak`, cambia una sesión fijada o rompe continuidad docente, se
  rechaza sin mutar.
- `Schedule` sigue siendo la última barrera: si un delta inconsistente llega a
  aplicación, lanza excepción y la corrida falla con reporte técnico.

Scoring incremental:

- Mantener `ScoreBreakdown currentScore`.
- `IncrementalSoftScorer.delta(schedule, proposal)` recalcula solo:
  - docentes afectados;
  - cohortes afectadas;
  - aulas afectadas si cambia distancia;
  - `SessionGroup` afectado si cambia contiguidad o docente.
- Usar `long` para costos ponderados y métricas contables. Usar `double` solo
  para `exp(-delta / temperatura)`.
- En modo respaldo, `ScoreDelta` incluye violaciones duras con peso alto; en
  modo normal, las violaciones duras ni entran al costo porque el movimiento se
  rechaza antes.

Regla de aceptación:

```text
delta = costo(vecino) - costo(actual)
aceptar si delta < 0
si no, aceptar si random < exp(-delta / temperatura)
temperatura *= coolingRate
```

Costo:

```text
100000 * hardViolations
  1000 * teacherUnavailableBlocks
   500 * nonContiguousCourseBlocks
   100 * gapsNotAtEnd
    10 * walkingDistance
     5 * teacherLoadDeviation
```

Comparación para reportes/defensa:

1. Menos violaciones duras.
2. Menos bloques fuera de disponibilidad y menos no contiguidad.
3. Menos ventanas muertas no al final.
4. Menor distancia de caminata.
5. Mejor balance de carga.
6. Menor costo ponderado total.

Esto evita vender una "puntuación única mágica": el costo sirve al algoritmo, pero la
decisión se explica por prioridades separadas.

Distancia entre aulas:

```text
roomDistance(a, b) =
  abs(a.floor - b.floor) * 10
  + abs(a.number - b.number)
```

Se evalúa solo entre sesiones consecutivas de la misma cohorte o del mismo
docente. Si hay recreo fijo entre ambas, la penalización baja porque hay tiempo
real para moverse.

Modo normal:

- rechazar movimientos con violaciones duras;
- `hardViolations` queda 0.

Modo de respaldo:

- permitir violaciones duras con peso alto;
- usarlo cuando el problema sea infactible;
- activar más peso para `AssignUnassigned`, porque asignar una sesión faltante
  suele valer más que pulir ventanas;
- reportar violaciones separadas.

Reproducibilidad y reliability:

- Toda aleatoriedad sale de la `seed` de `schedule_run`.
- No iterar sobre `HashMap` para decisiones; ordenar ids antes de desempatar.
- Guardar `engineVersion`, pesos, hiperparámetros y `seed`.
- Ejecutar chequeo de invariantes al final y opcionalmente cada N iteraciones en
  modo debug:
  - todos los índices de `Schedule` apuntan a las mismas asignaciones;
  - no hay duplicado por sesión;
  - sesiones fijadas conservaron docente/aula/bloque;
  - en modo normal no hay violaciones duras.
- Si una excepción ocurre al aplicar un movimiento, revertir delta, marcar run
  `FAILED` y conservar snapshot de diagnóstico.

Salida:

- Mejor `Schedule`.
- Evolución muestreada de puntuación, no cada iteración, para no inflar
  snapshots.
- Motivos de no asignación.

Verificaciones mínimas:

- costo baja o se mantiene mejor global;
- movimiento inválido no muta el horario original;
- resultado conserva sesiones fijadas y duras en modo normal.
- dos ejecuciones con misma entrada, `seed` y versión producen el mismo resultado.
- `ChangeTeacherGroup` no rompe continuidad docente.
- `AssignUnassigned` puede recuperar una sesión que fase 1 dejó sin asignar.

## Fase 3: LNS para edición manual

Objetivo: aceptar una edición y reparar solo el vecindario afectado.

Entrada:

- Horario aprobado.
- Edición manual: mover/cambiar aula/cambiar docente.
- Sesiones fijadas.
- `clientRequestId` opcional para idempotencia si el usuario reintenta la misma
  petición.

Contratos POO mínimos:

- `ManualEditCommand`: valor inmutable con `baseRunId`, `sessionId`,
  `targetTeacherId`, `targetRoomId`, `targetTimeBlockId` y `clientRequestId`.
  Si la petición no puede normalizarse, se guarda `requestPayload` y estado
  `REJECTED_BY_INPUT`.
- `RepairPlan`: edición normalizada + sesiones fijadas + vecindario elegido.
- `RepairNeighborhood`: `Set<Long> sessionIds` + causa por sesión
  (`DIRECT_CONFLICT`, `TEACHER_NEAR`, `ROOM_NEAR`, `COHORT_NEAR`,
  `SESSION_GROUP`).
- `RepairResult`: estado, `resultRunId`, sesiones movidas, violaciones
  restantes, puntuación antes/después y métricas.

Reglas:

- El `schedule_run` aprobado es inmutable. Fase 3 siempre crea un nuevo
  `schedule_run` resultado.
- La edición se aplica dentro de `ScheduleTransaction`; si falla, se revierte
  todo el delta.
- Cambio de docente en una sesión con `SessionGroup` aplica al grupo completo o
  se rechaza como `REJECTED_BY_INPUT`; no se permite romper continuidad docente.
- Sesiones fijadas no salen del horario ni entran a movimientos aleatorios.
- La semilla de mini-SA se deriva de `baseRunId + manualEditId + engineVersion`
  para reproducibilidad.

Proceso:

1. Validar estado del plan y existencia de sesión/docente/aula/bloque.
2. Si `clientRequestId` ya existe para el plan, devolver el resultado guardado.
3. Crear copia de trabajo desde `baseRunId`.
4. Aplicar edición con `ManualEditApplier`.
5. Marcar edición como fijada.
6. Detectar conflictos con índices de `Schedule`, no con escaneo completo.
7. Si no hay conflictos, persistir nuevo `schedule_run` y devolver horario.
8. Seleccionar vecindario con `NeighborhoodSelector`:
   - sesiones en conflicto;
   - misma aula y franja cercana;
   - mismo docente y franja cercana;
   - mismas cohortes afectadas;
   - sesiones del mismo `SessionGroup` si la regla de continuidad puede romperse;
   - límite inicial: 5-15 sesiones;
   - expansión a 30 sesiones solo si quedan conflictos directos y hay tiempo.
9. Destruir solo sesiones no fijadas del vecindario.
10. Reconstruir con mini-SA:
   - `maxIterations = 5000`;
   - enfriamiento más agresivo;
   - sesiones fijadas.
11. Persistir nuevo `schedule_run` con violaciones restantes, aunque la
    reparación sea parcial.
12. Devolver horario reparado o parcial con reporte.

Meta de respuesta:

- 200-500 ms para vecindarios chicos.
- Si no logra reparar, mantiene edición fijada y reporta conflictos restantes.

Verificaciones mínimas:

- edición limpia no dispara reconstrucción;
- sesión fijada nunca se mueve;
- conflicto docente/aula/cohorte aparece en reporte;
- LNS toca solo sesiones del vecindario.
- reintento con mismo `clientRequestId` no crea dos ediciones.
- excepción durante reparación deja intacto el horario base.
- cambio de docente no rompe `SessionGroup`.

## Uso del mismo motor para exámenes

No crear segundo motor. Crear otro `SchedulingContext`:

- `mode = EXAMS`;
- sesiones vienen de `ExamSessionFactory`;
- duración puede ser distinta a clase;
- no se exige contiguidad de bloques del mismo curso;
- se penalizan exámenes seguidos para la misma cohorte;
- se evita que docente vigile o imparta dos exámenes simultáneos;
- se puede permitir más de un aula para examen solo si el cliente lo pide.

Reglas duras para exámenes:

- sin solape de docente;
- sin solape de aula;
- sin solape de cohorte;
- capacidad suficiente;
- no usar `FixedBreak`;
- respetar jornada seleccionada.

Reglas blandas para exámenes:

- distribuir exámenes de la misma cohorte en días distintos;
- evitar dos exámenes pesados el mismo día;
- minimizar caminata si hay exámenes consecutivos;
- balancear carga de vigilancia docente si se modela vigilancia.

Esto mantiene motor dinámico: cambia entrada + reglas activas, no algoritmo.

## Estados del horario

`SchedulePlan.status`:

- `DRAFT`: datos editables, aún no generado.
- `VALIDATING`: pre-validación en curso.
- `INVALID_INPUT`: hay errores bloqueantes de datos.
- `GENERATING`: corrida del motor en curso.
- `GENERATED`: resultado sin violaciones duras.
- `GENERATED_WITH_CONFLICTS`: resultado parcial o de respaldo con conflictos reportados.
- `APPROVED`: coordinador aprobó la plantilla.
- `LOCKED`: horario publicado; solo sustituciones/eventos externos.
- `ARCHIVED`: ciclo viejo, solo lectura.

Regla simple: solo `DRAFT`, `INVALID_INPUT`, `GENERATED` y
`GENERATED_WITH_CONFLICTS` aceptan cambios estructurales. `APPROVED` acepta
edición manual controlada. `LOCKED` no cambia la plantilla.

## API inicial del servidor

Rutas HTTP mínimas, sin interfaz:

Contrato completo: `docs/api-contracts.md`. Si hay diferencia entre esta lista
resumida y ese documento, gana `docs/api-contracts.md`.

- `POST /api/schedule-plans/{id}/generate`
- `GET /api/schedule-plans/{id}/result`
- `GET /api/schedule-plans/{id}/violations`
- `POST /api/schedule-plans/{id}/manual-edits`
- `POST /api/schedule-plans/{id}/validate`
- `POST /api/schedule-plans/{id}/approve`
- `POST /api/schedule-plans/{id}/lock`
- `POST /api/exam-plans/{id}/generate`
- `GET /api/exam-plans/{id}/result`
- `POST /api/imports/academic-data`
- `GET /api/imports/{id}/errors`
- `GET /api/teacher/schedule`
- `GET /api/benchmarks/{runId}`

DTO clave:

```json
{
  "solverMode": "NORMAL",
  "scheduleType": "CLASSES",
  "seed": 12345,
  "maxIterations": 50000,
  "timeLimitSeconds": 30,
  "journeyConfig": {
    "blockMinutes": 45,
    "fixedBreaks": [
      { "day": "MONDAY", "startBlock": 4, "durationBlocks": 1 }
    ]
  },
  "weights": {
    "hardViolation": 100000,
    "teacherUnavailable": 1000,
    "nonContiguous": 500,
    "gaps": 100,
    "walking": 10,
    "loadBalance": 5
  }
}
```

Respuesta mínima de edición manual:

```json
{
  "manualEditId": "edit-123",
  "status": "APPLIED_WITH_REPAIR",
  "pinnedSessionIds": ["session-10"],
  "neighborhoodSessionIds": ["session-10", "session-21", "session-35"],
  "movedSessionIds": ["session-21", "session-35"],
  "remainingViolations": [],
  "scoreBefore": 1240,
  "scoreAfter": 1310,
  "repairTimeMs": 248
}
```

Estados de respuesta:

- `APPLIED_CLEAN`: no hubo conflictos.
- `APPLIED_WITH_REPAIR`: hubo conflictos y LNS reparó.
- `APPLIED_WITH_REMAINING_CONFLICTS`: se mantiene edición fijada, pero quedan conflictos.
- `REJECTED_BY_STATE`: el plan no permite ediciones.
- `REJECTED_BY_INPUT`: sesión, aula, docente o bloque no existe.

Siempre guardar:

- instantánea de entrada;
- seed;
- configuración;
- versión del motor;
- vecindario elegido y causa por sesión;
- petición cruda si fue rechazada antes de normalizar;
- resultado;
- métricas.

Sin eso, no hay reproducibilidad.

## Importación CSV/XLSX

Objetivo: aceptar datos imperfectos sin meter basura silenciosa a la base.

Soporte desde el día 1:

- `.csv`: un archivo por entidad.
- `.xlsx`: un libro con una hoja por entidad.
- Misma estructura de columnas para ambos formatos.

Hojas/archivos mínimos:

- `careers`: `career_code`, `career_name`
- `curricula`: `curriculum_code`, `career_code`, `year`, `is_active`
- `courses`: `course_code`, `course_name`, `requires_lab`, `weekly_blocks_min`, `weekly_blocks_max`
- `curriculum_courses`: `curriculum_code`, `course_code`, `semester_number`
- `cohorts`: `career_code`, `curriculum_code`, `semester_number`, `section`, `journey_code`, `expected_students`
- `teachers`: `teacher_code`, `teacher_name`, `priority`, `min_courses`, `max_courses`
- `teacher_courses`: `teacher_code`, `course_code`
- `teacher_availability`: `teacher_code`, `day`, `start_block`, `duration_blocks`
- `rooms`: `room_code`, `capacity`, `room_type`, `floor`, `number`
- `journeys`: `journey_code`, `block_minutes`, `start_time`, `end_time`
- `fixed_breaks`: `journey_code`, `day`, `start_block`, `duration_blocks`
- `common_areas`: `common_area_code`, `course_code`, `journey_code`, `semester_number`
- `common_area_careers`: `common_area_code`, `career_code`, `curriculum_code`

Reglas de importación:

- claves por código, no por nombre;
- nombres se normalizan solo para mostrar sugerencias, no para relacionar;
- cada error incluye archivo/hoja, fila, columna, valor y acción sugerida;
- importación válida crea instantánea antes de persistir;
- importación inválida no modifica datos existentes.

Ejemplos de errores:

- `teacher_course` referencia docente inexistente;
- `cohort.expected_students` excede toda aula compatible;
- `course.weekly_blocks_min` mayor que `weekly_blocks_max`;
- `fixed_break` cae fuera de jornada;
- `common_area_careers.career_code` referencia carrera inexistente;
- `common_area_careers.curriculum_code` no pertenece a esa carrera.

## Persistencia

Tablas core:

- `schedule_plan`
- `career`
- `curriculum`
- `curriculum_course`
- `cohort`
- `course`
- `teacher`
- `teacher_course`
- `teacher_availability`
- `room`
- `journey`
- `time_block`
- `fixed_break`
- `common_area_rule`
- `common_area_career`
- `schedule_session_group`
- `schedule_run`
- `schedule_assignment`
- `schedule_violation`
- `pre_validation_issue`
- `section_suggestion`
- `exam_plan` (vista)
- `manual_edit`
- `substitution_event`

JSONB permitido:

- disponibilidad docente;
- preferencias flexibles;
- instantáneas de entrada/salida del motor.

Restricciones útiles de base de datos:

- FK reales en catálogos.
- Unique para nombres dentro de contexto.
- CHECK `capacity > 0`.
- CHECK `block duration > 0`.
- CHECK `fixed break duration > 0`.
- CHECK `expected students >= 0`.
- CHECK `min weekly blocks <= max weekly blocks`.

No meter reglas complejas del motor en la base de datos. La base de datos protege integridad, el motor decide horarios.

## Plan de implementación del servidor

### Fase 1 del proyecto: dominio + constructiva

Duración sugerida: 4-6 semanas.

Entregables:

- Proyecto Java multi-módulo con Maven.
- Modelo de dominio.
- Estados de `SchedulePlan`.
- `ProblemPreValidator`.
- `TimeGridBuilder` con recreos fijos.
- Importación CSV/XLSX básica con contrato y errores por fila.
- `ImportValidator`.
- `SessionFactory`.
- `CommonAreaMerger`.
- Calendario docente global derivado de índices de `Schedule`.
- `HardConstraintChecker`.
- `ConstructiveScheduler`.
- API `generate` sin SA todavía.
- Escenarios sintéticos pequeños/medianos.

Criterio de salida:

- Pre-validación bloquea datos imposibles con errores legibles.
- Genera horario parcial/factible.
- Reporta no asignables.
- Tests de restricciones duras pasan.
- Recreos fijos nunca reciben sesiones.
- Docente compartido bloquea todas las carreras.
- Importación inválida no persiste cambios.

### Fase 2 del proyecto: optimización + pruebas de rendimiento

Duración sugerida: 6-8 semanas.

Entregables:

- `ScoreCalculator`.
- `RoomDistanceCalculator`.
- `MoveGenerator`.
- `AnnealingOptimizer`.
- Configuración por pesos/seed/límite de tiempo.
- Benchmarks pequeños/medianos/grandes.
- Reporte de métricas separadas.
- Modo de respaldo infactible.

Criterio de salida:

- Escenario grande de 900 sesiones termina en tiempo razonable.
- Tiempo a primera solución medido.
- Score mejora vs constructiva.
- Infeasibilidad produce horario parcial + reporte.
- Distancia entre aulas aparece como métrica separada.

### Fase 3 del proyecto: LNS + confiabilidad operativa

Duración sugerida: 4-6 semanas.

Entregables:

- `NeighborhoodDetector`.
- `NeighborhoodRepairer`.
- `ExamSessionFactory`.
- API `exam-plans`.
- API de ediciones manuales.
- Pinning.
- Auditoría de cambios.
- Pruebas de regresión con seeds fijas.
- Observabilidad: tiempos por fase, conteo de movimientos, puntuación.

Criterio de salida:

- Edición limpia queda inmediata.
- Edición conflictiva repara vecindario.
- Pinned nunca se rompe.
- Reporte explica conflictos restantes.
- Motor genera clases y exámenes con el mismo flujo.

## Plan de pruebas

Unitarias:

- pre-validación de entrada;
- objetos de valor;
- fusión de área común;
- time grid con recreos fijos;
- calendario global docente entre carreras;
- restricciones duras;
- distancia de aulas;
- reglas de exámenes;
- costo blando por categoría;
- movimientos;
- sugerencia de sección por capacidad.

Integración:

- importar CSV/XLSX válido;
- rechazar import con errores por fila;
- generar horario desde conjunto de datos pequeño;
- persistir resultado;
- recuperar resultado;
- aplicar edición manual;
- generar exámenes desde cursos activos;
- aprobar y bloquear horario.

Herramientas dev opcionales:

- `graphify`: usar cuando haya varios ADRs, DER, C4, fixtures y módulos Java.
- No es dependencia del sistema ni parte del entorno de ejecución.
- No bloquear fase 1 por graphify; correrlo después de tener estructura Maven y docs base.

Benchmarks:

- pequeño: 50 sesiones;
- mediano: 300 sesiones;
- grande: 900 sesiones;
- infactible: capacidad insuficiente o docente sin disponibilidad.

Fixtures obligatorios:

| Fixture | Debe incluir |
|---|---|
| `small` | área común, lab, recreo fijo, docente compartido entre carreras |
| `medium` | pensum viejo/nuevo, dos jornadas, secciones A/B |
| `large` | escala cercana a 20 carreras y 900 sesiones |
| `infeasible-room` | demanda mayor que toda aula compatible |
| `infeasible-teacher` | curso sin docente disponible |

Métricas obligatorias:

- tiempo a primera solución;
- tiempo total;
- violaciones duras;
- bloques fuera de disponibilidad docente;
- bloques no contiguos del mismo curso;
- ventanas muertas;
- distancia de caminata;
- desviación de carga;
- sesiones no asignadas.

## Criterios de aceptación medibles

- Misma entrada + misma `seed` + misma versión de motor produce mismo resultado.
- `NORMAL` nunca retorna violaciones duras si el problema es factible.
- Problema infactible retorna horario parcial + sesiones no asignadas + motivos.
- Fixture `small` genera en menos de 2 segundos en máquina de desarrollo.
- Fixture `large` genera en menos de 60 segundos con `timeLimitSeconds = 60`.
- LNS repara vecindario de 5-15 sesiones en menos de 500 ms.
- Edición fijada nunca se mueve durante reparación.
- Reporte muestra métricas separadas y puntuación total.
- Importación inválida no modifica datos persistidos.

## Riesgos y mitigación

| Riesgo | Impacto | Mitigación |
|---|---:|---|
| Modelo de datos ambiguo | Alto | primero instantáneas y fixtures pequeños |
| Área común mal modelada | Alto | `CommonAreaMerger` antes del motor |
| Docente visto por carrera, no global | Alto | indice docente global dentro de `Schedule` |
| Recreos tratados como preferencia | Alto | `FixedBreak` como bloqueo duro en `TimeGrid` |
| Exámenes duplican motor | Medio | `SchedulableSession` + `SchedulingContext` |
| Mutaciones accidentales en SA | Alto | objetos inmutables o copia controlada |
| Ajuste sin evidencia | Medio | búsqueda en cuadrícula con seeds fijas |
| LNS lento | Medio | límite 5-15 sesiones, presupuesto de tiempo |
| Infactible no explicado | Alto | `ConstraintViolation` legible por categoría |
| Importación ensucia datos | Alto | validar instantánea completa antes de persistir |
| Estados ambiguos del plan | Medio | ciclo de vida explícito de `SchedulePlan.status` |

## Fuera de alcance por ahora

- Interfaz.
- Arrastrar y soltar visual.
- Reportes PDF/Excel.
- Sustituciones avanzadas.
- Optimización exacta.
- Motor externo de generación de horarios.

## Registro de decisiones

| # | Decisión | Clasificación | Razón |
|---|---|---|---|
| 1 | Java 21 + Spring Boot del proyecto | Preferencia | Decisión acordada; servidor puro lo soporta bien |
| 2 | Motor como módulo puro sin Spring | Mecánica | Testeabilidad y rendimiento, menos acoplamiento |
| 3 | PostgreSQL se mantiene | Mecánica | Ya encaja con JSONB y datos relacionales |
| 4 | Sin interfaz en este plan | Mecánica | Usuario lo pidió explícitamente |
| 5 | Pruebas de rendimiento desde fase 2 | Mecánica | Sin pruebas de rendimiento no hay defensa académica ni ajuste |
| 6 | Exámenes usan mismo motor con `SchedulingContext` | Mecánica | Evita segundo motor y mantiene reglas dinámicas |
| 7 | Recreos fijos son restricción dura | Mecánica | Nadie debe quedar asignado en descanso institucional |
| 8 | Docente se agenda en calendario global | Mecánica | Docente puede compartir cursos entre carreras |
| 9 | Pre-validación antes del motor | Mecánica | Errores de datos deben explicarse antes de optimizar |
| 10 | Importación por códigos, no nombres | Mecánica | Evita empates y errores por variantes de texto |
| 11 | Ciclo de vida explícito de `SchedulePlan` | Mecánica | Evita editar horarios en estados incorrectos |
| 12 | Score reportado por prioridades separadas | Mecánica | Defensa más clara que un costo agregado opaco |
| 13 | Maven multi-módulo | Preferencia | Convención simple para Java académico y CI |
| 14 | OpenPDF para reportes PDF | Mecánica | Suficiente para PDFs del sistema sin meter JasperReports |
| 15 | CSV y XLSX desde día 1 | Mecánica | Datos reales llegarán en ambos formatos |
| 16 | Graphify solo como herramienta dev opcional | Mecánica | Útil cuando crezca el repo; no aporta al entorno de ejecución |
