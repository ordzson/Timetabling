# ADR 0002 - PostgreSQL y JSONB

## Estado

Aceptado

## Contexto

El modelo combina datos relacionales fuertes con preferencias y snapshots flexibles. Se necesitan FK, CHECK, indices, transacciones y auditoria.

## Decision

Usar PostgreSQL como fuente de verdad. Usar JSONB solo para preferencias flexibles, snapshots de entrada/salida, metricas y payloads de auditoria.

## Consecuencias

- FK y CHECK protegen integridad basica.
- JSONB evita tablas prematuras para datos variables.
- Las reglas complejas de solape, scoring y LNS viven en dominio/motor, no en SQL.
- Los JSONB deben tener contrato documentado cuando se vuelvan parte de API.
