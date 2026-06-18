# Horarios UdeO/UTP

Sistema de generacion de horarios academicos.

## Requisitos

- Java 21+
- Maven 3.9+
- Node.js 22+
- pnpm 11+

## Backend y motor

```bash
mvn test
```

Modulos Maven:

- `horarios-domain`: dominio puro, sin Spring.
- `horarios-solver`: motor puro, depende de `horarios-domain`.
- `horarios-testkit`: fixtures y benchmarks sinteticos.
- `horarios-api`: Spring Boot API.

## Frontend

```bash
cd horarios-web
pnpm install
pnpm run build
```

## Regla de dependencias

- `horarios-domain` no depende de Spring.
- `horarios-solver` no depende de Spring ni de `horarios-api`.
- `horarios-testkit` no depende de Spring.
- `horarios-api` es el unico modulo Spring Boot.
