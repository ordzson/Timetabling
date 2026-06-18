# ADR 0008 - OpenPDF y Apache POI

## Estado

Aceptado

## Contexto

El sistema debe exportar horarios y reportes en PDF y Excel. No se necesita diseno editorial complejo.

## Decision

Usar OpenPDF para PDF y Apache POI para XLSX.

## Consecuencias

- Cubre reportes tabulares sin introducir JasperReports.
- Exportaciones se generan desde `schedule_run` persistido, no recalculan motor.
- XLSX puede incluir hojas de metadata, conflictos y vistas por cohorte/docente/aula.
- PDF debe mantenerse simple y legible.
