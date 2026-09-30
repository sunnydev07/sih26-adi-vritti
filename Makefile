# Docker Compose v2 is invoked as `docker compose` (two words). The old
# `docker-compose` v1 binary was removed from Docker Desktop, so the previous
# `dev` target failed immediately on a current install.
#
# COMPOSE is overridable for people who still have v1 shims:
#   make dev COMPOSE="docker-compose"
COMPOSE ?= docker compose
COMPOSE_FILES = -f infra/docker-compose.yml

# 21 is build.gradle's default toolchain AND the version the core Dockerfile
# builds with (eclipse-temurin:21-jdk), so leaving it alone means a JDK 21
# install works with no flags. It was previously pinned to 23 for one Windows box
# that had no JDK 21; override on the command line if you must:
#   make test-core JAVA_VERSION=23
JAVA_VERSION ?= 21

.PHONY: help dev dev-detach down logs rebuild seed api test test-core test-ai e2e ps clean init-db docker-compose-config

help:
	@echo "make dev        - build and start the whole stack in the foreground"
	@echo "make dev-detach - same, but detached (use this for init-db / scripted checks)"
	@echo "make down       - stop the stack"
	@echo "make logs       - tail logs from every service"
	@echo "make test       - run core + ai test suites"
	@echo "make seed       - regenerate synthetic demo data AND load it into postgres"
	@echo "make api        - regenerate the API client from docs/openapi/core.yaml"
	@echo "make init-db    - apply the DB extensions to a RUNNING database (see infra/init-db.sql)"
	@echo "make clean      - stop the stack and delete volumes"

dev: ## Build and start the full stack in the foreground
	$(COMPOSE) $(COMPOSE_FILES) up --build

dev-detach:
	$(COMPOSE) $(COMPOSE_FILES) up --build -d

down:
	$(COMPOSE) $(COMPOSE_FILES) down

logs:
	$(COMPOSE) $(COMPOSE_FILES) logs -f

ps:
	$(COMPOSE) $(COMPOSE_FILES) ps

# Render and validate the compose file without starting a single container.
# Catches YAML syntax errors, bad `depends_on` conditions and unset interpolation
# variables, which otherwise only surface as a failed `make dev`.
docker-compose-config:
	$(COMPOSE) $(COMPOSE_FILES) config

rebuild:
	$(COMPOSE) $(COMPOSE_FILES) up --build -d

# JAVA_VERSION matches the local JDK override used by the core build; Java 21 is
# the project default and needs no override on a machine that has it.
test-core:
	cd services/core && ./gradlew test -PjavaToolchainVersion=$(JAVA_VERSION)

# pyproject.toml deliberately keeps pytest in the `dev` extra instead of the
# runtime dependencies, so a plain `pip install -e .` yields an environment with
# no pytest at all. One-time setup:  pip install -e '.[dev]'
test-ai:
	@echo "note: run 'pip install -e .[dev]' in services/ai once, or pytest is missing"
	cd services/ai && python3 -m pytest tests/ -v

test: test-core test-ai

# The previous target called `./gradlew e2eTest`, which is not a task that exists
# in build.gradle, so it always failed with "Task 'e2eTest' not found". There is
# no end-to-end suite yet; this target only says so, rather than pretending to
# verify a demo path.
e2e:
	@echo "No e2e suite exists yet. Start the stack with 'make dev' and walk the"
	@echo "demo path, or add a Gradle e2eTest source set first."

api:
	cd packages/api-client && npm run generate

seed:
	cd data/synthetic && python3 generate.py
	$(COMPOSE) $(COMPOSE_FILES) exec -T postgres \
		psql -v ON_ERROR_STOP=1 -U adivritti -d adivritti \
		< data/synthetic/output/seed.sql

# /docker-entrypoint-initdb.d/*.sql is executed by the postgres entrypoint ONLY
# when PGDATA is empty, so this file never runs against a volume that already
# exists -- which is exactly the case when upgrading the image from the stock
# postgres:16 to pgvector/pgvector:pg16. That target applies the extensions to a
# live database without touching any data:
#   make dev-detach && make init-db
# Every statement in infra/init-db.sql is IF NOT EXISTS, so it is idempotent and
# safe to re-run. The alternative is `make clean`, which deletes the volume.
init-db:
	$(COMPOSE) $(COMPOSE_FILES) exec -T postgres \
		psql -v ON_ERROR_STOP=1 -U adivritti -d adivritti \
		-f /docker-entrypoint-initdb.d/init.sql

# DESTRUCTIVE: drops the pgdata volume, so the next `make dev`
# starts from a blank database and the init script runs for real. This is the
# one-time step required when upgrading from the pre-pgvector stack.
clean: down
	$(COMPOSE) $(COMPOSE_FILES) down -v
