# ADR 0001 - Java 21 y Spring Boot 3

## Estado

Aceptado

## Contexto

El sistema necesita API REST, validacion, seguridad, persistencia, tareas de generacion y reportes. El equipo acordo Java como stack principal.

## Decision

Usar Java 21 y Spring Boot 3 para el backend. El motor de optimizacion queda en modulo Java puro sin dependencia de Spring.

## Consecuencias

- Java 21 da rendimiento estable, records, colecciones maduras y buena herramienta de pruebas.
- Spring Boot reduce infraestructura propia para API, seguridad, configuracion y observabilidad.
- El motor sigue testeable aislado.
- El equipo debe respetar frontera: `horarios-domain` y `horarios-solver` no dependen de Spring.
