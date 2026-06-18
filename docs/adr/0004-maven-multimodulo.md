# ADR 0004 - Maven multi-modulo

## Estado

Aceptado

## Contexto

El sistema separa dominio, solver y API. El motor debe probarse sin Spring y sin base de datos.

## Decision

Usar Maven multi-modulo con `horarios-domain`, `horarios-solver`, `horarios-testkit`, `horarios-api` y `horarios-web`.

## Consecuencias

- Fronteras de dependencia quedan visibles.
- CI puede ejecutar pruebas por modulo.
- El solver se mantiene portable.
- `horarios-testkit` concentra fixtures sinteticos y benchmarks sin contaminar codigo productivo.
- El build exige disciplina al agregar dependencias.
