# ADR 0007 - JWT y roles

## Estado

Aceptado

## Contexto

El sistema tiene cuatro roles: `SUPERADMIN`, `ADMIN`, `TEACHER`, `STUDENT`. Se necesita proteger endpoints y mantener auditoria de usuario.

## Decision

Usar Spring Security con BCrypt para passwords y JWT para autenticacion stateless.

## Consecuencias

- API queda simple para frontend.
- Roles se validan por endpoint y servicio.
- Tokens expirados devuelven `401`; rol insuficiente devuelve `403`.
- Recuperacion de password y OAuth quedan fuera hasta que sean requeridos.
