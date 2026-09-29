-- Runs ONLY on a FRESH volume.
--
-- The official postgres entrypoint executes everything in
-- /docker-entrypoint-initdb.d/ exactly once, and only while PGDATA is empty. If
-- the `pgdata` volume already exists, this file is mounted but never run: the
-- container comes up "healthy" (pg_isready passes) with none of these extensions
-- present, and the first vector DDL in a later migration fails.
--
-- So if you are upgrading an existing checkout from the pre-pgvector
-- postgres:16 image, do one of these, exactly once:
--
--   make clean
--     i.e. docker compose -f infra/docker-compose.yml down -v
--     Destroys the database. Simplest, and correct for a dev stack.
--
--   make dev-detach && make init-db
--     Applies this same file to the already-running database without touching
--     any data. Preferred when you have seed data you want to keep.
--
-- Every statement below is IF NOT EXISTS, so init-db is idempotent and safe to
-- re-run at any time.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";
