# KoreaPoint
메타빌드 3기 2팀 중간프로젝트(외국인 플랫폼)

## Local PostgreSQL with Docker

The shared development database runs on PostgreSQL 18 with pgvector in Docker and is exposed
on port `5433` so it does not conflict with a PostgreSQL installation using
the default port `5432`.

1. Start Docker Desktop.
2. Copy `src/main/resources/application-local.properties.example` to
   `src/main/resources/application-local.properties`.
3. Start `DemoApplication` from IntelliJ or run `mvnw spring-boot:run`.

Spring Boot finds the root `compose.yaml` and starts the database automatically.
The `start-only` lifecycle keeps the database running after the application stops,
which makes repeated application restarts faster. The local datasource properties
remain available as a fallback; while Compose integration is enabled, Spring Boot's
service connection uses the running container's mapped port and credentials.

To opt out of automatic Docker Compose management, add the following to your
uncommitted `application-local.properties`:

```properties
spring.docker.compose.enabled=false
```

You can still start the database manually when needed:

```bash
docker compose up -d
```

Check the database container:

```bash
docker compose ps
```

Stop it without deleting data:

```bash
docker compose down
```

The database is stored in the named Docker volume `korea_point_postgres`.
Do not use `docker compose down -v` unless you intentionally want to delete all
local development data.

The pgvector extension is enabled for a new database volume by the initialization
script. The application also prepares the extension and the chatbot vector schema
when it starts, so existing volumes require no separate schema command.
