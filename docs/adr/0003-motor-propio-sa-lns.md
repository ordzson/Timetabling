# ADR 0003 - Motor propio con constructiva, SA y LNS

## Estado

Aceptado

## Contexto

El proyecto no permite librerias que resuelvan directamente timetabling. El problema UCTP es NP-hard y requiere buena solucion en tiempo razonable, no optimizacion exacta.

## Decision

Implementar motor propio con heuristica constructiva, Recocido Simulado y Busqueda de Gran Vecindario para reparacion de ediciones manuales.

## Consecuencias

- Cumple restriccion academica de no usar OR-Tools, Timefold, OptaPlanner, UniTime, FET ni similares.
- Permite explicar algoritmo y resultados en defensa.
- Requiere benchmarks, seeds fijas y metricas separadas para justificar calidad.
- Aumenta responsabilidad de pruebas sobre restricciones duras.
