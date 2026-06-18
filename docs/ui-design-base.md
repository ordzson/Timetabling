# Base de diseno UI

Fuente: `DesignSystem.html`, reducido a lo util para el proyecto UdeO/UTP.

## Objetivo

Guiar la futura app `horarios-web` con React + TypeScript + shadcn/ui + dnd-kit.
Este archivo no es una implementacion. Es la base visual minima para construir
componentes reales.

## Direccion visual

- Admin academico claro, denso y operativo.
- Estilo "papel de horario": fondo claro, bordes negros, sombras duras.
- La primera pantalla tras login debe servir para trabajar, no para vender.
- Priorizar grillas, filtros, estados, conflictos y acciones por rol.
- Usar iconos de la libreria del proyecto, no Material Symbols por CDN.

## Tokens base

### Color

| Token | Hex | Uso |
|---|---:|---|
| `background` | `#fff8f8` | Fondo general |
| `surface` | `#fff0f3` | Sidebar, barras, zonas secundarias |
| `surface-muted` | `#fde9ee` | Hover, paneles suaves |
| `surface-strong` | `#f7e3e8` | Headers de tablas, filtros activos |
| `paper` | `#fff9ec` | Grillas, tarjetas de trabajo, reportes |
| `ink` | `#1d1c14` | Texto principal, bordes, sombras |
| `muted-ink` | `#554248` | Texto secundario |
| `primary` | `#841a52` | Accion principal, estado activo |
| `primary-soft` | `#ffd9e5` | Fondo de seleccion primaria |
| `teal` | `#006d77` | Exito, valido, generado |
| `cyan` | `#90eefb` | Info, bloque sugerido |
| `warning` | `#ffdf9b` | Pendiente, advertencia suave |
| `warning-strong` | `#785a00` | Texto/borde de advertencia |
| `danger` | `#ba1a1a` | Conflicto duro, error |
| `danger-soft` | `#ffdad6` | Fondo de conflicto |
| `border` | `#887178` | Bordes secundarios |

### Tipografia

| Token | Familia | Uso |
|---|---|---|
| `font-sans` | `Work Sans`, system sans | Texto de UI |
| `font-heading` | `Plus Jakarta Sans`, system sans | Titulos |
| `font-label` | `Space Grotesk`, system sans | Badges, tabs, acciones cortas |

Escala inicial:

- `text-xs`: `12px / 1.4`
- `text-sm`: `14px / 1.5`
- `text-base`: `16px / 1.5`
- `text-lg`: `18px / 1.6`
- `heading-sm`: `24px / 1.2`
- `heading-md`: `32px / 1.2`
- `heading-lg`: `40px / 1.1`

No usar letter spacing negativo. Labels pueden usar `0.04em` maximo.

### Forma y espacio

- Radio default: `4px`.
- Cards/paneles: maximo `8px`.
- Borde principal: `2px solid var(--ink)`.
- Borde fuerte solo en superficies clave: `3px solid var(--ink)`.
- Sombra dura chica: `4px 4px 0 var(--ink)`.
- Sombra dura media: `6px 6px 0 var(--ink)`.
- Sombra dura grande: `8px 8px 0 var(--ink)`.
- Espaciado base: `4px`; pasos utiles: `8`, `16`, `24`, `40`, `64`.

## Componentes a construir

### `AdminShell`

- Sidebar fijo en desktop.
- Topbar con ciclo academico, plan activo, estado y usuario.
- En movil, sidebar colapsable. No depender de `ml-64` fijo.
- Navegacion minima: Dashboard, Catalogos, Planes de horario, Disponibilidad,
  Reportes, Usuarios.

### `SchedulePlanPage`

- Vista principal para coordinador.
- Header: nombre del plan, estado `SchedulePlan.status`, seed, version del motor.
- Acciones segun estado: validar, generar, aprobar, publicar, exportar.
- No mostrar acciones que backend rechazaria por estado.

### `ScheduleGrid`

- Fondo `paper` con lineas sutiles.
- Columnas por dia, filas por bloque.
- Filtros: carrera, cohorte, docente, aula, jornada.
- Bloque de sesion como "sheet": borde negro, sombra dura chica, color por tipo.
- Badges dentro del bloque: aula, docente, cohorte.
- Drag and drop solo para admin en `APPROVED`.
- dnd-kit maneja arrastre; backend decide validez final.

### `ConflictPanel`

- Panel lateral siempre visible en desktop.
- Separar:
  - conflictos duros;
  - restricciones blandas;
  - sesiones no asignadas;
  - sugerencias de seccion.
- Cada item debe explicar causa y entidad afectada sin abrir consola.
- Usar `danger-soft` para duro, `warning` para advertencia, `cyan` para sugerencia.

### `CatalogPages`

- Tablas densas, no cards decorativas.
- Busqueda, filtros, acciones de fila.
- Secciones: academico (carreras, pensums, cursos, cohortes), docentes,
  espacios (aulas), tiempo (jornadas).
- Badges utiles: requiere laboratorio, area comun, activo/inactivo.

### `TeacherAvailabilityPage`

- Matriz por dia x bloque.
- Estados visuales: disponible, no disponible, preferido.
- Docente solo edita su propia disponibilidad.
- Guardado con confirmacion visible, no modal pesada.

### `PublishedSchedulePage`

- Vista limpia para docente/alumno.
- Sin controles administrativos.
- Filtros minimos: ciclo y vista semanal.
- Exportar si rol lo permite.

## Estados visuales

| Estado | Color | UI |
|---|---|---|
| `DRAFT` | `surface-strong` | Editable |
| `VALIDATING` | `cyan` | Cargando/validando |
| `INVALID_INPUT` | `danger-soft` | Bloqueante |
| `GENERATING` | `cyan` | Progreso |
| `GENERATED` | `teal` | Listo para revisar |
| `GENERATED_WITH_CONFLICTS` | `warning` | Revisar panel |
| `APPROVED` | `primary-soft` | Edicion manual permitida |
| `LOCKED` | `ink` | Solo lectura/publicado |
| `ARCHIVED` | `surface-muted` | Solo lectura |

## Patrones utiles del HTML original

- Mantener: paleta clara, papel, bordes fuertes, sombras duras, grilla semanal,
  panel de conflictos, badges compactos, matriz de disponibilidad.
- Adaptar: textos a UdeO/UTP, roles reales, estados reales, componentes shadcn/ui.
- No copiar: HTML multi-documento, Tailwind CDN, Google image URLs, Material
  Symbols CDN, JavaScript DOM manual, datos ficticios `EduPaper`/`V3.4`.

## Tailwind minimo sugerido

Cuando exista `horarios-web`, mover estos valores al tema o a CSS variables:

```ts
export const uiTokens = {
  colors: {
    background: "#fff8f8",
    surface: "#fff0f3",
    surfaceMuted: "#fde9ee",
    surfaceStrong: "#f7e3e8",
    paper: "#fff9ec",
    ink: "#1d1c14",
    mutedInk: "#554248",
    primary: "#841a52",
    primarySoft: "#ffd9e5",
    teal: "#006d77",
    cyan: "#90eefb",
    warning: "#ffdf9b",
    warningStrong: "#785a00",
    danger: "#ba1a1a",
    dangerSoft: "#ffdad6",
    border: "#887178",
  },
  boxShadow: {
    hardSm: "4px 4px 0 #1d1c14",
    hardMd: "6px 6px 0 #1d1c14",
    hardLg: "8px 8px 0 #1d1c14",
  },
  borderWidth: {
    3: "3px",
  },
};
```

## Regla de uso

Para cada pantalla: primero funcionalidad del flujo, luego estilo. Si un adorno
no ayuda a leer horario, conflicto o accion disponible, no entra.
