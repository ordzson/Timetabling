# ADR 0006 - React, TypeScript y dnd-kit

## Estado

Aceptado

## Contexto

La interfaz necesita formularios, filtros, grillas de horario y edicion manual por drag and drop.

## Decision

Usar React + TypeScript para frontend y `dnd-kit` para arrastrar sesiones en la grilla. Usar `shadcn/ui` para componentes base.

## Consecuencias

- TypeScript reduce errores en DTOs y estados.
- `dnd-kit` cubre drag and drop sin construir interaccion desde cero.
- La UI debe seguir estados del backend; no debe simular reglas del motor.
- La primera pantalla tras login debe ser operativa, no landing page.
