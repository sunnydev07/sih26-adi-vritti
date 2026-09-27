.PHONY: dev test-core test-ai e2e api seed

dev:
	docker-compose -f infra/docker-compose.yml up --build

test-core:
	cd services/core && ./gradlew test

test-ai:
	cd services/ai && python -m pytest tests/ -v

e2e:
	# Run the demo path end-to-end
	cd services/core && ./gradlew e2eTest

api:
	# Regenerate API clients from the contract
	cd packages/api-client && npm run generate

seed:
	cd data/synthetic && python generate.py
