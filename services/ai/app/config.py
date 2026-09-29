"""Adi-Vritti AI services. Money in int paise; datetimes tz-aware.

Environment variables this service reads (no prefix; all optional except where a
protected route depends on them):

``AI_SERVICE_TOKEN``
    The shared bearer token that every route except ``/health`` requires. It is
    the only thing standing between the network and ``POST /gap/hash``, whose
    request body *is* a raw Aadhaar reference -- so it must be a real secret
    that exists only in the deployment's environment.

    The default below is a development placeholder that is committed in this
    repository, which makes it worthless as a credential. While the configured
    token is blank or still that published value, protected routes are refused
    with 503 and only ``/health`` answers (see ``app.security.require_service_token``).

``ALLOW_INSECURE_DEV``
    Escape hatch for local demos, mirroring Core's ``app.security.allow-insecure-dev``
    (env var of the same name). Defaults to false. Set it to ``true`` to accept the
    blank/placeholder token and get the old behaviour back. It must never be set in
    a real deployment: it re-opens the raw-PII routes to anyone who can reach the
    port and who has read this repository.

    Keeping ``make dev`` honest without touching the compose file: the
    ``ai`` service in ``infra/docker-compose.yml`` pins its token to
    ``${AI_SERVICE_TOKEN:-dev-only-ai-service-token}`` and does not forward
    ``ALLOW_INSECURE_DEV`` at all, so a plain ``make dev`` now starts with the
    published default and every protected route answers 503 while ``/health``
    stays green. That is the intended fail-closed state, not a broken stack.
    Clear it either by exporting a real secret before ``make dev``::

        export AI_SERVICE_TOKEN='<a long random secret>'

    or by adding ``ALLOW_INSECURE_DEV: ${ALLOW_INSECURE_DEV:-true}`` to the
    ``ai`` service's ``environment:`` block in that compose file (owned elsewhere).
"""

from __future__ import annotations

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# Placeholder that must never survive into a deployed environment.
DEV_SALT = "dev-salt-rotate-in-prod"
DEV_TOKEN = "dev-only-ai-service-token"


class Settings(BaseSettings):
    core_service_url: str = "http://localhost:8080"

    # Shared, rotating salt for the privacy-preserving coverage-gap join.
    gap_hmac_salt: str = DEV_SALT

    # Bearer token callers must present. Guards the raw-PII-taking routes.
    # The default is only ever valid for a local demo, and the gate in
    # app.security refuses it unless ALLOW_INSECURE_DEV is explicitly set.
    ai_service_token: str = DEV_TOKEN

    # Bearer token presented to Core on proxied calls. Core is JWT-protected, so
    # without this every JAGO proxy call comes back 401/403.
    core_service_token: str = ""

    # Local-demo escape hatch. See the module docstring: it is the only way to
    # run with a blank or published-development service token.
    allow_insecure_dev: bool = False

    # Hard cap on a single /docai/parse upload. read() is unbounded without it.
    docai_max_upload_bytes: int = 5 * 1024 * 1024

    @field_validator("gap_hmac_salt")
    @classmethod
    def _reject_known_dev_salt_in_production(cls, v: str) -> str:
        if v.strip() == DEV_SALT:
            # Not fatal: `make dev` depends on the default. Loud enough that a
            # deployment that forgot to set it is obvious in the logs.
            import logging

            logging.getLogger("app.config").warning(
                "GAP_HMAC_SALT is still the published development value. The coverage-gap "
                "join is only privacy-preserving with a real, rotated salt."
            )
        return v

    @field_validator("docai_max_upload_bytes")
    @classmethod
    def _cap_upload_limit(cls, v: int) -> int:
        if v <= 0:
            raise ValueError("DOCAI_MAX_UPLOAD_BYTES must be positive")
        return min(v, 50 * 1024 * 1024)

    model_config = SettingsConfigDict(env_prefix="", extra="ignore")


settings = Settings()
