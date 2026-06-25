# Despliegue local

Requisitos:

- Docker con Docker Compose.
- Puertos libres: `5433`, `8080`, `8081`.

Levantar todo:

```bash
docker compose up --build
```

Servicios:

- web: <http://localhost:8081>
- API healthcheck: <http://localhost:8080/actuator/health>
- PostgreSQL: `localhost:5433`, base `horarios`, usuario `usuario_horarios`, password `1234`.

Usuario inicial:

- email: `admin@udeo.edu.gt`
- password: `admin123`

Variables principales:

```text
HORARIOS_DB_URL=jdbc:postgresql://postgres:5432/horarios
HORARIOS_DB_USER=usuario_horarios
HORARIOS_DB_PASSWORD=1234
HORARIOS_AUTH_JWT_SECRET=local-dev-secret-change-me
HORARIOS_AUTH_JWT_TTL_SECONDS=3600
SPRING_PROFILES_ACTIVE=docker
```

La web usa `/api` por same-origin. En desarrollo Vite proxy redirige a `localhost:8080`; en Docker nginx redirige al servicio `api`.

Apagar:

```bash
docker compose down
```

Borrar datos locales:

```bash
docker compose down -v
```
