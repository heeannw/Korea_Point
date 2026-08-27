# KoreaPoint
메타빌드 3기 2팀 중간프로젝트(외국인 플랫폼)

## Local PostgreSQL with Docker

The shared development database runs on PostgreSQL 18 in Docker and is exposed
on port `5433` so it does not conflict with a PostgreSQL installation using
the default port `5432`.

1. Start Docker Desktop.
2. From the project root, run:

   ```bash
   docker compose up -d
   ```

3. Copy `src/main/resources/application-local.properties.example` to
   `src/main/resources/application-local.properties`.
4. Start `DemoApplication` from IntelliJ.

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
