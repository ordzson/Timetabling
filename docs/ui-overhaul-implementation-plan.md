# Plan ejecutable: Overhaul UI sin tocar Login

Estado: documento de ejecucion para la etapa UI primero.  
Alcance: `horarios-web` despues del login.  
Regla critica: Login queda exactamente igual en flujo, JSX, clases CSS y estilos visuales.

## Objetivo operativo

Reconstruir la interfaz administrativa de `horarios-web` para que explique y opere el modelo real:

```text
carrera -> pensum -> cursos por semestre -> cohortes -> docentes por carrera+jornada -> disponibilidad -> planes -> horario
```

La etapa no cambia DB ni API. Si falta endpoint, la UI muestra estado vacio accionable con etiqueta `API pendiente`; no usa mocks permanentes.

## Fuentes obligatorias

- `docs/ui-design-base.md`: tokens, densidad, bordes, sombras y patrones.
- `docs/example.html`: inspiracion visual posterior al login, sin copiar CDN, HTML estatico ni imagenes externas.
- `docs/api-contracts.md`: contratos REST y estados de `SchedulePlan`.
- `horarios-web/src/main.tsx`: estado actual; contiene Login, API helpers, dashboard, catalogos, importacion, planes y grilla.
- `horarios-web/src/styles.css`: estilos actuales; contiene estilos de Login que no deben moverse ni cambiarse en esta etapa salvo que se extraigan byte-a-byte sin diferencia visual.
- `horarios-api/src/main/java/...`: endpoints realmente disponibles.

## Diagnostico actual

El frontend existe, pero esta concentrado en dos archivos:

- `horarios-web/src/main.tsx`
- `horarios-web/src/styles.css`

Actualmente ya consume:

- `POST /api/auth/login`
- `GET /api/catalog/{resource}` para `careers`, `courses`, `teachers`, `rooms`, `journeys`
- `POST /api/catalog/{resource}`
- `POST /api/imports/academic-data`
- `GET /api/imports/{id}/errors`
- `GET /api/schedule-plans`
- `POST /api/schedule-plans`
- `POST /api/schedule-plans/{id}/validate`
- `POST /api/schedule-plans/{id}/generate`
- `GET /api/schedule-plans/{id}/result`
- `GET /api/schedule-plans/{id}/violations`
- `POST /api/schedule-plans/{id}/approve`
- `POST /api/schedule-plans/{id}/lock`
- `POST /api/schedule-plans/{id}/manual-edits`
- `GET /api/substitutions`
- `POST /api/substitutions`

Recursos con DB/import parcial o total, pero sin CRUD API administrativo completo:

- `curricula`
- `curriculum_courses`
- `cohorts`
- `teacher_courses`
- `teacher_availability`
- `fixed_breaks`
- `common_area_rule` / `common_areas`
- `common_area_careers`

Recurso pedido por el plan que no aparece como tabla/API actual:

- `teacher-career-journeys`

Conclusion: UI debe mostrar el flujo real, pero marcar como `API pendiente` toda escritura/lectura fina que no tenga endpoint confirmado.

## Principios de ejecucion

- No tocar Login: no modificar `Login`, `SESSION_KEY`, `POST /api/auth/login`, clases `.login-*` ni experiencia de entrada.
- No agregar backend en esta etapa.
- No agregar mocks permanentes. Se permiten constantes descriptivas para hojas requeridas, navegacion, estados, columnas y mensajes.
- No usar Tailwind CDN, Material Symbols CDN, Google Fonts por CDN ni imagenes externas de `example.html`.
- Usar React + CSS local.
- Agregar `lucide-react` solo para iconos de sidebar, dashboard y acciones: `pnpm add lucide-react`.
- Densidad primero: tablas, matrices, filtros, estados, acciones por rol.
- Acciones visibles deben respetar estado backend. Accion no permitida por estado aparece deshabilitada con razon.

## Arquitectura objetivo

```text
horarios-web/src/
  main.tsx
  styles.css
  api/
    client.ts
    catalogs.ts
    imports.ts
    schedulePlans.ts
    substitutions.ts
  types/
    auth.ts
    catalog.ts
    schedule.ts
    import.ts
  layout/
    AdminShell.tsx
  pages/
    DashboardPage.tsx
    AcademiaPage.tsx
    TeachersPage.tsx
    TimePage.tsx
    RoomsPage.tsx
    ImportWizardPage.tsx
    SchedulePlanPage.tsx
    AvailabilityPage.tsx
    ReportsPage.tsx
  components/
    ApiPending.tsx
    Badge.tsx
    ConflictPanel.tsx
    EmptyState.tsx
    ScheduleGrid.tsx
    StatusBadge.tsx
    Table.tsx
```

Ponytail aplicado: no crear router si no hace falta. Un estado `activeView` dentro de `AdminShell` basta para esta etapa.

## Definicion de terminado global

La implementacion completa se considera terminada cuando:

- `pnpm run build` pasa en `horarios-web`.
- Login se ve y funciona igual que antes.
- El area administrativa usa AdminShell nuevo y no depende de ID manual como flujo principal de planes.
- Academia permite entender carrera -> pensum -> semestre -> cohorte aunque `curricula/cohorts` queden en `API pendiente`.
- Docentes permite entender disponibilidad -> carrera+jornada -> cursos aunque relaciones pendientes queden bloqueadas.
- Planes permite listar, crear, seleccionar, validar, generar, aprobar, bloquear, cargar resultado y ver conflictos.
- Grilla permite filtrar por datos disponibles y muestra badges legibles.
- Drag and drop solo esta activo cuando `planStatus === 'APPROVED'`.
- Importacion lista hojas requeridas, columnas y parte del modelo que llenan.
- No hay datos ficticios persistentes que parezcan reales.
- QA visual desktop y movil completada.
- `graphify . --update` ejecutado o reportado como pendiente si pide API key LLM.

## Tareas UIO

### UIO01 - Congelar Login como contrato

Debe hacer:

- Identificar bloque `Login`, helpers de sesion y estilos `.login-*`.
- Crear checklist local de regresion visual y funcional.
- Si se extraen archivos, hacerlo sin cambio de comportamiento ni nombres de clases.

No debe hacer:

- Cambiar textos, layout, colores, placeholders, submit, errores o almacenamiento de sesion.

Evaluar cumplimiento:

- Diff no cambia JSX de `Login` ni reglas `.login-*`, o el cambio es extraccion identica.
- Login permite enviar `POST /api/auth/login`.
- Captura visual antes/despues no muestra diferencia intencional.

### UIO02 - Instalar iconos permitidos

Debe hacer:

- Ejecutar `pnpm add lucide-react` dentro de `horarios-web`.
- Usar iconos solo donde aportan reconocimiento: sidebar, topbar, acciones, estados.

No debe hacer:

- Instalar otra libreria de UI.
- Usar Material Symbols o CDN.

Evaluar cumplimiento:

- `horarios-web/package.json` incluye `lucide-react`.
- `pnpm-lock.yaml` actualizado.
- `pnpm run build` resuelve imports.

### UIO03 - Separar tipos compartidos

Debe hacer:

- Mover tipos de auth, catalogos, importacion y horarios desde `main.tsx` a `src/types/*`.
- Mantener nombres cercanos al contrato API.

No debe hacer:

- Cambiar shape de DTOs.
- Crear tipos genericos no usados.

Evaluar cumplimiento:

- `main.tsx` reduce responsabilidad.
- TypeScript compila.
- No hay duplicados importantes de `PlanStatus`, `Assignment`, `PageResponse`.

### UIO04 - Separar cliente API

Debe hacer:

- Mover `api`, `apiForm`, `formatApiError` a `src/api/client.ts`.
- Crear wrappers finos para recursos ya existentes.
- Mantener `API_BASE` y bearer token igual.

No debe hacer:

- Agregar cache global, retries o interceptores complejos.
- Cambiar manejo de errores del backend.

Evaluar cumplimiento:

- Todas las pantallas consumen API desde `src/api/*`.
- Errores siguen mostrando `code`, `message` y campos.
- Login sigue usando el mismo endpoint.

### UIO05 - Crear primitives UI locales

Debe hacer:

- Crear `Badge`, `StatusBadge`, `EmptyState`, `ApiPending`, `Table` y botones de accion.
- Basarlos en tokens de `docs/ui-design-base.md`.

No debe hacer:

- Crear design system grande.
- Meter cards decorativas donde una tabla sirve.

Evaluar cumplimiento:

- Estados `DRAFT` a `ARCHIVED` tienen color consistente.
- `API pendiente` se ve diferente de error real.
- Botones tienen efecto presionado.

### UIO06 - Reorganizar CSS sin romper Login

Debe hacer:

- Mantener variables actuales.
- Separar visualmente secciones CSS: base, login, shell, primitives, pages, responsive.
- Agregar estilos de papel administrativo: fondo claro, paneles crema, bordes negros 2-3px, sombras duras.

No debe hacer:

- Cambiar `.login-*`.
- Introducir paleta fuera de `docs/ui-design-base.md`.

Evaluar cumplimiento:

- Busqueda CSS confirma colores principales desde tokens.
- Mobile no tiene overflow horizontal global.
- Login no cambia visualmente.

### UIO07 - Nuevo AdminShell

Debe hacer:

- Sidebar fijo desktop con: Dashboard, Academia, Docentes, Tiempo, Espacios, Importacion, Planes, Disponibilidad, Reportes.
- Topbar densa con usuario, rol, plan activo/estado si existe, y acciones rapidas.
- Sidebar colapsable o superior en movil sin depender de margen fijo fragil.

No debe hacer:

- Implementar router si estado local basta.
- Mostrar vistas sin permiso como editables.

Evaluar cumplimiento:

- Navegacion cambia vistas sin recargar.
- Sidebar no tapa contenido en desktop ni movil.
- Rol sin permisos ve estado claro, no pantalla rota.

### UIO08 - Dashboard administrativo realista

Debe hacer:

- Mostrar conteos disponibles de catalogos existentes.
- Mostrar planes recientes y estado.
- Mostrar panel de “datos listos / API pendiente” sin inventar metricas.

No debe hacer:

- Mostrar progreso ficticio, porcentajes falsos o actividad simulada.

Evaluar cumplimiento:

- Si API responde, dashboard muestra conteos reales.
- Si endpoint falla, muestra error no bloqueante por seccion.
- Visual se inspira en `example.html`: papel, tabla, badges, sombras.

### UIO09 - AcademiaPage: estructura carrera -> pensum -> semestre -> cohorte

Debe hacer:

- Selector de carrera desde `/api/catalog/careers`.
- Seccion Pensums marcada `API pendiente` si `curricula` no responde.
- Matriz de cursos por semestre marcada `API pendiente` si `curriculum-courses` no responde.
- Cohortes por pensum/semestre/seccion/jornada marcadas `API pendiente` si `cohorts` no responde.
- Mostrar cursos existentes desde `/api/catalog/courses` como catalogo base.

No debe hacer:

- Fabricar pensums/cohortes desde cursos.
- Guardar relaciones inexistentes.

Evaluar cumplimiento:

- Usuario entiende jerarquia completa aun con endpoints pendientes.
- Recursos disponibles cargan; recursos pendientes quedan bloqueados.
- Estados vacios dicen que falta API, no que no existen datos.

### UIO10 - TeachersPage: docentes, relaciones y disponibilidad

Debe hacer:

- Listar docentes desde `/api/catalog/teachers`.
- Mostrar panel “docente -> carrera+jornada” como `API pendiente` para `teacher-career-journeys`.
- Mostrar panel “docente -> cursos” como `API pendiente` si no hay API CRUD de `teacher_courses`.
- Preparar filtros carrera+jornada sin inventar asignaciones.
- Mostrar disponibilidad como matriz, bloqueada si no hay endpoint admin.

No debe hacer:

- Usar disponibilidad del docente autenticado como si fuera admin CRUD global.
- Simular cursos asignados a docentes.

Evaluar cumplimiento:

- Flujo disponibilidad -> carrera+jornada -> cursos queda claro.
- Cada accion pendiente esta deshabilitada con razon.
- Lista real de docentes funciona.

### UIO11 - AvailabilityPage para rol docente

Debe hacer:

- Si existe endpoint `GET/PUT /api/teacher/availability`, usarlo para docente autenticado.
- Si backend actual no lo implementa, mostrar `API pendiente`.
- Usar matriz dia x bloque con estados disponible/no disponible/preferido.

No debe hacer:

- Permitir que ADMIN edite disponibilidad propia como si editara docentes globales.

Evaluar cumplimiento:

- TEACHER entiende su disponibilidad y estado de guardado.
- Si API no existe, no hay formulario falso.
- ADMIN ve mensaje de alcance si entra a esta vista.

### UIO12 - TimePage

Debe hacer:

- Gestionar jornadas desde `/api/catalog/journeys`.
- Mostrar `fixed_breaks` como `API pendiente`.
- Representar bloques por jornada con tabla densa.

No debe hacer:

- Calcular y persistir time blocks desde frontend.

Evaluar cumplimiento:

- Jornadas CRUD actual sigue funcionando.
- Descansos fijos quedan visibles como dependencia pendiente.

### UIO13 - RoomsPage

Debe hacer:

- Gestionar aulas desde `/api/catalog/rooms`.
- Preparar seccion recursos/areas comunes como `API pendiente`.
- Mostrar capacidad, tipo, piso, numero, activo.

No debe hacer:

- Mezclar aulas con areas comunes sin endpoint.

Evaluar cumplimiento:

- Tabla de aulas real funciona.
- Areas comunes indican endpoint/modelo pendiente con nombres correctos.

### UIO14 - ImportWizard mejorado

Debe hacer:

- Mostrar hojas requeridas y columnas:
  `careers`, `curricula`, `courses`, `curriculum_courses`, `cohorts`, `teachers`, `teacher_courses`, `teacher_availability`, `rooms`, `journeys`, `fixed_breaks`, `common_areas`, `common_area_careers`.
- Indicar que parte del modelo llena cada hoja.
- Mantener upload actual `POST /api/imports/academic-data`.
- Agregar filtros locales para errores por hoja y columna; usar `sheetName` del endpoint cuando aplique.

No debe hacer:

- Cambiar formato de importacion.
- Ocultar errores por fila/columna.

Evaluar cumplimiento:

- Usuario sabe que plantilla necesita antes de subir.
- Errores son filtrables por hoja/columna.
- Resultado `VALID`, `INVALID`, `IMPORTED` se muestra con badge claro.

### UIO15 - SchedulePlanPage sin flujo principal por ID manual

Debe hacer:

- Listar planes desde `GET /api/schedule-plans`.
- Crear planes desde `POST /api/schedule-plans`.
- Seleccionar plan desde tabla/lista.
- Mantener campo ID solo como detalle secundario o diagnostico, no flujo principal.

No debe hacer:

- Obligar a escribir ID para validar/generar/cargar resultado.

Evaluar cumplimiento:

- Usuario puede crear plan y operar sobre el seleccionado.
- Lista muestra estado, tipo, fechas, actualizado.
- Estado vacio invita a crear plan.

### UIO16 - Acciones de plan por estado

Debe hacer:

- Validar/generar solo desde `DRAFT`, `INVALID_INPUT`, `GENERATED`, `GENERATED_WITH_CONFLICTS`.
- Aprobar solo desde `GENERATED`, `GENERATED_WITH_CONFLICTS` con `runId`.
- Bloquear solo desde `APPROVED`.
- Cargar resultado y violaciones del plan seleccionado.
- Mostrar razones de bloqueo.

No debe hacer:

- Mostrar acciones que backend rechazaria como principales.

Evaluar cumplimiento:

- Matriz de estados en `docs/api-contracts.md` esta reflejada.
- Botones deshabilitados explican razon.
- Errores `STATE_TRANSITION_NOT_ALLOWED` se presentan claro.

### UIO17 - Resultado de plan y ConflictPanel

Debe hacer:

- Mostrar score, seed, engine version, asignadas, sin asignar.
- Mostrar conflictos duros, blandos, sesiones no asignadas y sugerencias si vienen de API.
- Usar `GET /result` y `GET /violations`.

No debe hacer:

- Inventar score o conflictos si no hay resultado.

Evaluar cumplimiento:

- Resultado vacio indica generar/cargar.
- Conflictos tienen codigo, mensaje y entidad afectada cuando exista.
- Panel lateral visible en desktop y no rompe movil.

### UIO18 - ScheduleGrid con filtros completos

Debe hacer:

- Filtros visibles: carrera, pensum, semestre, cohorte, docente, aula, jornada.
- Filtros con datos no disponibles quedan deshabilitados o `API pendiente`.
- Filtros disponibles por `AssignmentDto`: docente, aula, cohorte.
- Badges en bloque: carrera, pensum, semestre, seccion, jornada, aula, docente cuando existan; si no, mostrar solo datos disponibles.

No debe hacer:

- Derivar carrera/pensum/semestre desde `cohortIds` sin endpoint.

Evaluar cumplimiento:

- Grilla filtra realmente por docente/aula/cohorte.
- Badges no desbordan en desktop/movil.
- Datos pendientes son honestos y no parecen bug.

### UIO19 - Drag and drop solo en APPROVED

Debe hacer:

- Activar dnd-kit solo cuando `planStatus === 'APPROVED'`.
- Mantener manual edit contra `POST /manual-edits`.
- Mostrar drawer/form con destino permitido y feedback de reparacion.

No debe hacer:

- Permitir arrastre en `GENERATED`, `LOCKED` o `ARCHIVED`.
- Enviar edit sin `baseRunId`.

Evaluar cumplimiento:

- En `APPROVED`, arrastre abre edicion manual.
- En otros estados, bloques son lectura.
- Respuesta LNS actualiza run, grilla y violaciones.

### UIO20 - Sustituciones y reportes

Debe hacer:

- Mantener sustituciones contra `/api/substitutions`.
- Mostrar que sustituciones requieren plan `LOCKED`.
- Reportes deben exponer links/acciones a PDF/XLSX existentes.

No debe hacer:

- Crear generador frontend de PDF/XLSX.

Evaluar cumplimiento:

- En plan no `LOCKED`, sustitucion queda bloqueada con razon.
- PDF/XLSX usan `/api/reports/schedule-plans/{id}.pdf|xlsx`.

### UIO21 - Responsividad y accesibilidad minima

Debe hacer:

- Probar desktop ancho, laptop y movil.
- Controles tienen labels.
- Botones icon-only tienen `aria-label` o tooltip.
- Tablas densas usan scroll horizontal contenido, no body completo.

No debe hacer:

- Usar texto gigante en paneles operativos.
- Dejar texto superpuesto o fuera de botones.

Evaluar cumplimiento:

- No hay overlap visual en vistas principales.
- Navegacion usable en movil.
- Focus visible en inputs/botones.

### UIO22 - Verificacion final y grafo

Debe hacer:

- Ejecutar `pnpm run build` en `horarios-web`.
- QA visual:
  - Login sigue igual.
  - AdminShell/Dashboard inspirado en `example.html`.
  - Academia explica carrera -> pensum -> semestre -> cohorte.
  - Docentes explica disponibilidad -> carrera+jornada -> cursos.
  - Planes lista/crea/selecciona plan, genera/carga resultado y filtra grilla.
- Ejecutar `graphify . --update`.

No debe hacer:

- Marcar listo si build falla.
- Ignorar falla de graphify sin reportarla.

Evaluar cumplimiento:

- Build pasa.
- QA manual documentada en resumen de entrega.
- Graphify actualizado o pendiente explicado con causa exacta.

## Orden recomendado

1. UIO01
2. UIO02
3. UIO03
4. UIO04
5. UIO05
6. UIO06
7. UIO07
8. UIO08
9. UIO15
10. UIO16
11. UIO17
12. UIO18
13. UIO19
14. UIO14
15. UIO09
16. UIO10
17. UIO11
18. UIO12
19. UIO13
20. UIO20
21. UIO21
22. UIO22

Razon: primero blindar Login y reducir `main.tsx`; luego shell/planes, porque ya tienen endpoints reales; despues pantallas con mas `API pendiente`.

## Matriz de endpoints por pantalla

| Pantalla | Endpoint real | Pendiente UI |
|---|---|---|
| Dashboard | `/api/catalog/{careers,courses,teachers,rooms,journeys}`, `/api/schedule-plans` | resumen global de conflictos/sesiones |
| Academia | `/api/catalog/careers`, `/api/catalog/courses` | `curricula`, `curriculum-courses`, `cohorts` |
| Docentes | `/api/catalog/teachers`, `/api/catalog/courses`, `/api/catalog/careers`, `/api/catalog/journeys` | `teacher-career-journeys`, `teacher-courses`, admin `teacher-availability` |
| Tiempo | `/api/catalog/journeys` | `fixed-breaks`, bloques calculados si se exponen |
| Espacios | `/api/catalog/rooms` | `common-areas`, `common-area-careers`, recursos de aulas |
| Importacion | `/api/imports/academic-data`, `/api/imports/{id}/errors` | plantilla descargable si se pide despues |
| Planes | `/api/schedule-plans`, acciones de generacion, resultado, violaciones, manual edits | ninguno critico para flujo actual |
| Disponibilidad | contrato documentado `/api/teacher/availability` | confirmar implementacion real; si falta, `API pendiente` |
| Reportes | `/api/reports/schedule-plans/{id}.pdf`, `.xlsx` | vista previa embebida |

## Riesgos y mitigaciones

| Riesgo | Impacto | Mitigacion |
|---|---|---|
| Tocar Login accidentalmente | Regresion de acceso | UIO01 primero; diff separado; captura antes/despues |
| `main.tsx` grande dificulta cambios | Bugs por mezcla de responsabilidades | UIO03/UIO04 antes de pantallas nuevas |
| UI promete backend inexistente | Usuario cree que puede guardar | `ApiPending`, botones disabled, copy explicito |
| Filtros piden datos que `AssignmentDto` no trae | Filtros falsos | Solo filtrar por campos disponibles; deshabilitar resto |
| DnD en estado incorrecto | Backend rechaza/manual edit invalida | Gate unico por `planStatus === 'APPROVED'` |
| Estilo deriva del login o de Tailwind | Inconsistencia visual | CSS local con tokens de `ui-design-base.md` |
| Mobile con tablas rompe layout | UI dificil de usar | Scroll horizontal por tabla, shell responsive |

## Checklist de QA manual

Login:

- Abrir app sin sesion.
- Verificar misma pantalla, textos y estilo.
- Login exitoso conserva sesion.
- Login fallido muestra error.

AdminShell:

- Sidebar desktop fijo.
- En movil, navegacion accesible sin tapar contenido.
- Topbar muestra usuario, rol y plan activo si hay seleccionado.

Academia:

- Carrera carga desde API.
- Pensums/cohortes pendientes muestran `API pendiente`.
- Cursos base aparecen o estado vacio real.

Docentes:

- Docentes cargan desde API.
- Relacion carrera+jornada queda bloqueada si no hay endpoint.
- Matriz disponibilidad no permite guardar si endpoint falta.

Planes:

- Crear plan.
- Seleccionar plan desde lista.
- Validar/generar segun estado.
- Cargar resultado.
- Ver conflictos.
- Aprobar y habilitar drag and drop solo en `APPROVED`.
- Bloquear y deshabilitar edicion manual.

Importacion:

- Ver hojas requeridas antes de subir archivo.
- Subir archivo valido/invalido.
- Filtrar errores por hoja/columna.

Reportes:

- Acciones PDF/XLSX apuntan a endpoints correctos con plan seleccionado.

## Entrega esperada por PR

Para mantener el cambio confiable, dividir en PRs pequenos:

1. PR base: UIO01-UIO07.
2. PR planes: UIO15-UIO19.
3. PR import/dashboard: UIO08, UIO14, UIO20.
4. PR academia/docentes/tiempo/espacios/disponibilidad: UIO09-UIO13.
5. PR QA final: UIO21-UIO22.

Si se hace en un solo PR, mantener commits separados por los mismos grupos.
