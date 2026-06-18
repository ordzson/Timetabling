# Contratos API

Proyecto: Sistema de Generacion de Horarios UdeO/UTP  
Version del contrato: `v1`  
Base path: `/api`

Este documento es fuente de verdad para backend y frontend. Si un endpoint cambia
request, response, error, permiso o estado permitido, actualizar este archivo en
el mismo PR.

## Convenciones HTTP

- JSON usa `application/json; charset=utf-8`.
- Fechas usan ISO-8601: `YYYY-MM-DD`.
- Fechas con hora usan ISO-8601 con zona: `2026-01-15T10:30:00Z`.
- IDs numericos son `number` en JSON.
- Dinero no aplica.
- Campos desconocidos en request se rechazan con `VALIDATION_FAILED`.
- Campos `null` solo se aceptan cuando el DTO lo indique.
- Mutaciones admin deben registrar usuario actual y fecha en backend.

Headers comunes:

```http
Accept: application/json
Content-Type: application/json
Authorization: Bearer <jwt>
Idempotency-Key: <client-request-id opcional>
```

`Authorization` no se envia en:

- `POST /api/auth/login`

`Idempotency-Key` es obligatorio solo para edicion manual si
`clientRequestId` no viene en el body. Si ambos vienen, deben coincidir.

## Roles

```text
SUPERADMIN
ADMIN
TEACHER
STUDENT
```

Regla de permisos:

- `SUPERADMIN`: todo.
- `ADMIN`: catalogos, importacion, planes, generacion, aprobacion, reportes.
- `TEACHER`: disponibilidad propia y horario propio.
- `STUDENT`: horario publicado de su cohorte.

## Paginacion

Query params estandar para listados:

```text
page=0
size=20
sort=code,asc
q=texto opcional
active=true opcional
```

Limites:

- `page >= 0`
- `1 <= size <= 100`
- `sort` solo acepta campos permitidos por cada endpoint.

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

## ErrorResponse

Todos los errores JSON usan esta forma:

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

Codigos HTTP:

| HTTP | Uso |
|---:|---|
| `400` | request invalido, transicion invalida, archivo invalido |
| `401` | sin token, token invalido, token expirado |
| `403` | rol insuficiente o recurso ajeno |
| `404` | recurso no existe |
| `409` | duplicado, conflicto de estado, idempotencia incompatible |
| `422` | datos validos en JSON pero bloqueantes para dominio |
| `500` | error interno sin stack trace |

Codigos de error estables:

| Code | HTTP | Uso |
|---|---:|---|
| `AUTH_INVALID_CREDENTIALS` | `401` | email/password invalido |
| `AUTH_TOKEN_MISSING` | `401` | falta bearer token |
| `AUTH_TOKEN_INVALID` | `401` | token malformado |
| `AUTH_TOKEN_EXPIRED` | `401` | token expirado |
| `FORBIDDEN` | `403` | rol insuficiente |
| `RESOURCE_NOT_FOUND` | `404` | id inexistente |
| `VALIDATION_FAILED` | `400` | campos invalidos |
| `DUPLICATE_CODE` | `409` | `code` ya existe |
| `STATE_TRANSITION_NOT_ALLOWED` | `409` | estado no permite accion |
| `IDEMPOTENCY_CONFLICT` | `409` | mismo idempotency key con payload distinto |
| `IMPORT_INVALID_FILE` | `400` | formato/hojas invalidas |
| `IMPORT_HAS_ERRORS` | `422` | filas con errores |
| `COURSE_WITHOUT_TEACHER` | `422` | pre-validacion |
| `LAB_WITHOUT_ROOM` | `422` | pre-validacion |
| `JOURNEY_WITHOUT_ENOUGH_BLOCKS` | `422` | pre-validacion |
| `COMMON_AREA_WITHOUT_CAPACITY` | `422` | pre-validacion |
| `TEACHER_WITHOUT_AVAILABILITY` | `422` | pre-validacion |
| `FIXED_BREAK_OUT_OF_RANGE` | `422` | pre-validacion |
| `SOLVER_FAILED` | `500` | corrida fallo tecnicamente |
| `MANUAL_EDIT_REJECTED_BY_STATE` | `409` | plan no editable manualmente |
| `MANUAL_EDIT_REJECTED_BY_INPUT` | `400` | sesion/docente/aula/bloque invalido |
| `SUBSTITUTE_TEACHER_CONFLICT` | `422` | sustituto se solapa |

## Estados y transiciones

Estados:

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

Transiciones permitidas:

| Accion | Desde | Hacia |
|---|---|---|
| crear plan | n/a | `DRAFT` |
| validar sin errores | `DRAFT`, `INVALID_INPUT`, `GENERATED`, `GENERATED_WITH_CONFLICTS` | `DRAFT` o estado previo |
| validar con errores | `DRAFT`, `INVALID_INPUT`, `GENERATED`, `GENERATED_WITH_CONFLICTS` | `INVALID_INPUT` |
| generar inicio | `DRAFT`, `INVALID_INPUT`, `GENERATED`, `GENERATED_WITH_CONFLICTS` | `GENERATING` |
| generar sin conflictos | `GENERATING` | `GENERATED` |
| generar parcial/conflictos | `GENERATING` | `GENERATED_WITH_CONFLICTS` |
| generar falla tecnica | `GENERATING` | estado anterior con `schedule_run.status=FAILED` |
| aprobar | `GENERATED`, `GENERATED_WITH_CONFLICTS` | `APPROVED` |
| publicar/bloquear | `APPROVED` | `LOCKED` |
| archivar | cualquiera excepto `GENERATING`, `VALIDATING` | `ARCHIVED` |
| edicion manual limpia/reparada | `APPROVED` | `APPROVED` |
| sustitucion | `LOCKED` | `LOCKED` |

Acciones no permitidas devuelven `409 STATE_TRANSITION_NOT_ALLOWED`.

## DTOs comunes

### UserDto

```json
{
  "id": 1,
  "email": "admin@udeo.edu.gt",
  "fullName": "Admin UdeO",
  "role": "ADMIN",
  "teacherId": null,
  "cohortId": null,
  "active": true
}
```

### Catalog DTOs

Campos base:

```json
{
  "id": 1,
  "code": "SOFT",
  "name": "Ingenieria en Sistemas",
  "active": true
}
```

Catalogos soportados por `/api/catalog/{resource}`:

| Resource | CreateRequest campos |
|---|---|
| `careers` | `code`, `name`, `active` |
| `curricula` | `code`, `careerId`, `year`, `isActive`, `validFrom`, `validUntil` |
| `courses` | `code`, `name`, `requiresLab`, `weeklyBlocksMin`, `weeklyBlocksMax`, `preferences` |
| `cohorts` | `careerId`, `curriculumId`, `semesterNumber`, `section`, `journeyId`, `expectedStudents`, `active` |
| `teachers` | `code`, `fullName`, `priority`, `minCourses`, `maxCourses`, `active` |
| `rooms` | `code`, `capacity`, `type`, `floor`, `number`, `active` |
| `journeys` | `code`, `name`, `blockMinutes`, `startTime`, `endTime` |

Grupos de implementacion:

| Grupo | Resources |
|---|---|
| Academico | `careers`, `curricula`, `courses`, `cohorts` |
| Tiempo | `journeys` |
| Docentes | `teachers` |
| Espacios | `rooms` |

Mantener una ruta publica simple (`/api/catalog/{resource}`), pero separar
servicios internos por grupo cuando el CRUD deje de ser identico. No crear
submodulos Maven ni schema separado para esto.

Tipos permitidos:

- `room.type`: `THEORY`, `LAB`, `MIXED`.
- `time`: `HH:mm:ss`, ejemplo `07:00:00`.
- `preferences`: objeto JSON; default `{}`.

### IssueDto

```json
{
  "id": 20,
  "severity": "ERROR",
  "code": "COURSE_WITHOUT_TEACHER",
  "entityType": "course",
  "entityId": 10,
  "message": "El curso no tiene docente habilitado.",
  "suggestedAction": "Asignar al menos un docente al curso.",
  "source": {
    "sheet": "teacher_courses",
    "row": 12
  }
}
```

### ScoreDto

```json
{
  "total": 1240,
  "hardViolations": 0,
  "teacherUnavailableBlocks": 0,
  "nonContiguousCourseBlocks": 2,
  "gapsNotAtEnd": 5,
  "walkingDistance": 14,
  "teacherLoadDeviation": 3
}
```

### AssignmentDto

```json
{
  "id": 501,
  "sessionId": 301,
  "courseId": 90,
  "courseCode": "MAT101",
  "courseName": "Matematica I",
  "teacherId": 40,
  "teacherName": "Luis Perez",
  "roomId": 8,
  "roomCode": "301",
  "cohortIds": [1001],
  "dayOfWeek": 1,
  "startBlock": 0,
  "durationBlocks": 2,
  "status": "ASSIGNED",
  "pinned": false
}
```

## Endpoints

### POST /api/auth/login

Rol: publico.

Request:

```json
{
  "email": "admin@udeo.edu.gt",
  "password": "secret"
}
```

Response `200`:

```json
{
  "accessToken": "jwt",
  "tokenType": "Bearer",
  "expiresAt": "2026-01-15T12:00:00Z",
  "user": {
    "id": 1,
    "email": "admin@udeo.edu.gt",
    "fullName": "Admin UdeO",
    "role": "ADMIN",
    "teacherId": null,
    "cohortId": null,
    "active": true
  }
}
```

Errores: `401 AUTH_INVALID_CREDENTIALS`.

### GET /api/me

Rol: cualquier usuario autenticado.

Response `200`: `UserDto`.

### GET /api/catalog/{resource}

Rol: `ADMIN`, `SUPERADMIN`.

Query: `page`, `size`, `sort`, `q`, `active`.

Response `200`:

```json
{
  "items": [
    {
      "id": 1,
      "code": "SOFT",
      "name": "Ingenieria en Sistemas",
      "active": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

### POST /api/catalog/{resource}

Rol: `ADMIN`, `SUPERADMIN`.

Request ejemplo `careers`:

```json
{
  "code": "SOFT",
  "name": "Ingenieria en Sistemas",
  "active": true
}
```

Response `201`: catalogo creado.

Errores: `400 VALIDATION_FAILED`, `409 DUPLICATE_CODE`.

### PATCH /api/catalog/{resource}/{id}

Rol: `ADMIN`, `SUPERADMIN`.

Request: campos parciales del recurso.

Response `200`: catalogo actualizado.

Errores: `404 RESOURCE_NOT_FOUND`, `409 DUPLICATE_CODE`.

### GET /api/schedule-plans

Rol: `ADMIN`, `SUPERADMIN`.

Query: `page`, `size`, `sort`, `status`, `scheduleType`, `q`.

Response `200`:

```json
{
  "items": [
    {
      "id": 10,
      "code": "2026-S1-CLASSES",
      "name": "Primer semestre 2026",
      "scheduleType": "CLASSES",
      "status": "DRAFT",
      "startDate": "2026-01-15",
      "endDate": "2026-06-15",
      "createdAt": "2026-01-01T10:00:00Z",
      "updatedAt": "2026-01-01T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

### POST /api/schedule-plans

Rol: `ADMIN`, `SUPERADMIN`.

Request:

```json
{
  "code": "2026-S1-CLASSES",
  "name": "Primer semestre 2026",
  "scheduleType": "CLASSES",
  "startDate": "2026-01-15",
  "endDate": "2026-06-15",
  "config": {
    "defaultBlockMinutes": 45
  }
}
```

Response `201`: plan con `status=DRAFT`.

### POST /api/schedule-plans/{id}/validate

Rol: `ADMIN`, `SUPERADMIN`.

Estados permitidos: `DRAFT`, `INVALID_INPUT`, `GENERATED`,
`GENERATED_WITH_CONFLICTS`.

Request: `{}`.

Response `200`:

```json
{
  "planId": 10,
  "status": "INVALID_INPUT",
  "hasBlockingErrors": true,
  "issues": [
    {
      "id": 20,
      "severity": "ERROR",
      "code": "COURSE_WITHOUT_TEACHER",
      "entityType": "course",
      "entityId": 90,
      "message": "El curso no tiene docente habilitado.",
      "suggestedAction": "Asignar al menos un docente al curso.",
      "source": {}
    }
  ]
}
```

### POST /api/schedule-plans/{id}/generate

Rol: `ADMIN`, `SUPERADMIN`.

Estados permitidos: `DRAFT`, `INVALID_INPUT`, `GENERATED`,
`GENERATED_WITH_CONFLICTS`.

Request:

```json
{
  "solverMode": "NORMAL",
  "seed": 12345,
  "maxIterations": 50000,
  "timeLimitSeconds": 30,
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

Response `200`:

```json
{
  "planId": 10,
  "runId": 77,
  "runNumber": 1,
  "status": "COMPLETED",
  "planStatus": "GENERATED",
  "seed": 12345,
  "engineVersion": "solver-0.1.0",
  "score": {
    "total": 1240,
    "hardViolations": 0,
    "teacherUnavailableBlocks": 0,
    "nonContiguousCourseBlocks": 2,
    "gapsNotAtEnd": 5,
    "walkingDistance": 14,
    "teacherLoadDeviation": 3
  },
  "assignedCount": 880,
  "unassignedCount": 0,
  "startedAt": "2026-01-01T10:00:00Z",
  "finishedAt": "2026-01-01T10:00:12Z"
}
```

Errores: `422 IMPORT_HAS_ERRORS`, `500 SOLVER_FAILED`.

### GET /api/schedule-plans/{id}/result

Rol: `ADMIN`, `SUPERADMIN`.

Query: `runId` opcional. Si falta, usa ultimo run terminado.

Response `200`:

```json
{
  "planId": 10,
  "runId": 77,
  "planStatus": "GENERATED",
  "score": {
    "total": 1240,
    "hardViolations": 0,
    "teacherUnavailableBlocks": 0,
    "nonContiguousCourseBlocks": 2,
    "gapsNotAtEnd": 5,
    "walkingDistance": 14,
    "teacherLoadDeviation": 3
  },
  "assignments": [
    {
      "id": 501,
      "sessionId": 301,
      "courseId": 90,
      "courseCode": "MAT101",
      "courseName": "Matematica I",
      "teacherId": 40,
      "teacherName": "Luis Perez",
      "roomId": 8,
      "roomCode": "301",
      "cohortIds": [1001],
      "dayOfWeek": 1,
      "startBlock": 0,
      "durationBlocks": 2,
      "status": "ASSIGNED",
      "pinned": false
    }
  ],
  "unassigned": []
}
```

### GET /api/schedule-plans/{id}/violations

Rol: `ADMIN`, `SUPERADMIN`.

Query: `runId` opcional, `severity` opcional.

Response `200`:

```json
{
  "items": [
    {
      "id": 900,
      "severity": "WARNING",
      "code": "NON_CONTIGUOUS_COURSE_BLOCKS",
      "message": "El curso tiene bloques no contiguos.",
      "affectedEntities": [
        {
          "type": "session",
          "id": 301
        }
      ],
      "cost": 500
    }
  ]
}
```

### POST /api/schedule-plans/{id}/approve

Rol: `ADMIN`, `SUPERADMIN`.

Estados permitidos: `GENERATED`, `GENERATED_WITH_CONFLICTS`.

Request:

```json
{
  "runId": 77,
  "comment": "Aprobado por coordinacion."
}
```

Response `200`:

```json
{
  "planId": 10,
  "status": "APPROVED",
  "approvedRunId": 77
}
```

### POST /api/schedule-plans/{id}/lock

Rol: `ADMIN`, `SUPERADMIN`.

Estados permitidos: `APPROVED`.

Request:

```json
{
  "comment": "Publicado para estudiantes y docentes."
}
```

Response `200`:

```json
{
  "planId": 10,
  "status": "LOCKED"
}
```

### POST /api/schedule-plans/{id}/manual-edits

Rol: `ADMIN`, `SUPERADMIN`.

Estados permitidos: `APPROVED`.

Request:

```json
{
  "clientRequestId": "edit-20260115-0001",
  "baseRunId": 77,
  "sessionId": 301,
  "targetTeacherId": null,
  "targetRoomId": 9,
  "targetTimeBlockId": 120
}
```

Response `200`:

```json
{
  "manualEditId": 88,
  "status": "APPLIED_WITH_REPAIR",
  "resultRunId": 78,
  "pinnedSessionIds": [301],
  "neighborhoodSessionIds": [301, 322, 350],
  "movedSessionIds": [322, 350],
  "remainingViolations": [],
  "scoreBefore": 1240,
  "scoreAfter": 1310,
  "repairTimeMs": 248
}
```

Errores: `409 MANUAL_EDIT_REJECTED_BY_STATE`,
`400 MANUAL_EDIT_REJECTED_BY_INPUT`, `409 IDEMPOTENCY_CONFLICT`.

### POST /api/imports/academic-data

Rol: `ADMIN`, `SUPERADMIN`.

Content-Type: `multipart/form-data`.

Request fields:

```text
file=<csv-or-xlsx>
mode=VALIDATE_ONLY|IMPORT
```

Response `200`:

```json
{
  "importBatchId": 55,
  "status": "INVALID",
  "filename": "horarios.xlsx",
  "summary": {
    "rowsRead": 1200,
    "rowsValid": 1190,
    "rowsInvalid": 10
  },
  "errorCount": 10
}
```

### GET /api/imports/{id}/errors

Rol: `ADMIN`, `SUPERADMIN`.

Query: `page`, `size`, `sheetName`.

Response `200`:

```json
{
  "items": [
    {
      "id": 1,
      "sheetName": "teacher_courses",
      "rowNumber": 12,
      "columnName": "teacher_code",
      "rawValue": "DOC-404",
      "code": "RESOURCE_NOT_FOUND",
      "message": "El docente no existe.",
      "suggestedAction": "Crear el docente o corregir el codigo."
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1
}
```

### GET /api/teacher/availability

Rol: `TEACHER`.

Response `200`:

```json
{
  "teacherId": 40,
  "items": [
    {
      "journeyId": 1,
      "dayOfWeek": 1,
      "startBlock": 0,
      "durationBlocks": 4,
      "preference": 0,
      "source": "PORTAL"
    }
  ]
}
```

### PUT /api/teacher/availability

Rol: `TEACHER`.

Request:

```json
{
  "items": [
    {
      "journeyId": 1,
      "dayOfWeek": 1,
      "startBlock": 0,
      "durationBlocks": 4,
      "preference": 0
    }
  ]
}
```

Response `200`: mismo formato que `GET /api/teacher/availability`.

### GET /api/public/schedules/cohorts/{id}

Rol: `STUDENT`, `ADMIN`, `SUPERADMIN`.

Regla: `STUDENT` solo puede pedir su `cohortId`.

Query: `planId` obligatorio si hay mas de un plan `LOCKED`.

Response `200`:

```json
{
  "planId": 10,
  "cohortId": 1001,
  "assignments": [
    {
      "id": 501,
      "sessionId": 301,
      "courseId": 90,
      "courseCode": "MAT101",
      "courseName": "Matematica I",
      "teacherId": 40,
      "teacherName": "Luis Perez",
      "roomId": 8,
      "roomCode": "301",
      "cohortIds": [1001],
      "dayOfWeek": 1,
      "startBlock": 0,
      "durationBlocks": 2,
      "status": "ASSIGNED",
      "pinned": false
    }
  ],
  "substitutions": []
}
```

### GET /api/teacher/schedule

Rol: `TEACHER`.

Query: `planId` obligatorio si hay mas de un plan `LOCKED`.

Response `200`:

```json
{
  "planId": 10,
  "teacherId": 40,
  "assignments": [
    {
      "id": 501,
      "sessionId": 301,
      "courseId": 90,
      "courseCode": "MAT101",
      "courseName": "Matematica I",
      "teacherId": 40,
      "teacherName": "Luis Perez",
      "roomId": 8,
      "roomCode": "301",
      "cohortIds": [1001],
      "dayOfWeek": 1,
      "startBlock": 0,
      "durationBlocks": 2,
      "status": "ASSIGNED",
      "pinned": false
    }
  ],
  "substitutions": []
}
```

### GET /api/reports/schedule-plans/{id}.pdf

Rol: `ADMIN`, `SUPERADMIN`.

Query: `runId` opcional, `view=cohort|teacher|room|conflicts`.

Response `200`:

```http
Content-Type: application/pdf
Content-Disposition: attachment; filename="schedule-plan-10.pdf"
```

### GET /api/reports/schedule-plans/{id}.xlsx

Rol: `ADMIN`, `SUPERADMIN`.

Query: `runId` opcional.

Response `200`:

```http
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename="schedule-plan-10.xlsx"
```

### POST /api/substitutions

Rol: `ADMIN`, `SUPERADMIN`.

Estado requerido: plan del `assignmentId` debe estar `LOCKED`.

Request:

```json
{
  "assignmentId": 501,
  "substituteTeacherId": 41,
  "startsAt": "2026-02-01T00:00:00Z",
  "endsAt": "2026-02-15T23:59:59Z",
  "isPermanent": false,
  "reason": "Sustitucion temporal"
}
```

Response `201`:

```json
{
  "id": 70,
  "assignmentId": 501,
  "originalTeacherId": 40,
  "substituteTeacherId": 41,
  "startsAt": "2026-02-01T00:00:00Z",
  "endsAt": "2026-02-15T23:59:59Z",
  "isPermanent": false,
  "reason": "Sustitucion temporal"
}
```

Errores: `422 SUBSTITUTE_TEACHER_CONFLICT`.

### GET /api/substitutions

Rol: `ADMIN`, `SUPERADMIN`, `TEACHER`.

Query: `planId`, `teacherId`, `activeAt`.

Regla: `TEACHER` solo ve sustituciones donde es docente original o sustituto.

Response `200`:

```json
{
  "items": [
    {
      "id": 70,
      "assignmentId": 501,
      "originalTeacherId": 40,
      "substituteTeacherId": 41,
      "startsAt": "2026-02-01T00:00:00Z",
      "endsAt": "2026-02-15T23:59:59Z",
      "isPermanent": false,
      "reason": "Sustitucion temporal"
    }
  ]
}
```

## Matriz endpoint x rol

| Endpoint | SUPERADMIN | ADMIN | TEACHER | STUDENT |
|---|---:|---:|---:|---:|
| `POST /api/auth/login` | si | si | si | si |
| `GET /api/me` | si | si | si | si |
| `/api/catalog/**` | si | si | no | no |
| `/api/schedule-plans/**` | si | si | no | no |
| `/api/imports/**` | si | si | no | no |
| `/api/teacher/availability` | no | no | si | no |
| `/api/teacher/schedule` | no | no | si | no |
| `/api/public/schedules/cohorts/{id}` | si | si | no | propia |
| `/api/reports/**` | si | si | no | no |
| `/api/substitutions` | si | si | lectura propia | no |

## Criterio de salida para agentes

- Tests cubren caso feliz, `401`, `403`, `404`, `409` y validacion principal.
- Endpoint devuelve exactamente el DTO documentado.
- Error nunca incluye stack trace.
- Mutacion rechazada por estado conserva datos.
- Listados usan `PageResponse`.
- Frontend no debe depender de campos no documentados aqui.
