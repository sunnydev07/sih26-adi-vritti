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

import logging
import os
from collections.abc import Mapping

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# Placeholder that must never survive into a deployed environment.
DEV_SALT = "dev-salt-rotate-in-prod"
DEV_TOKEN = "dev-only-ai-service-token"

PROD_BLOCK = "*** REFUSING TO START: prod environment with development secrets ***"

#: Env vars that select the production posture. ``ENV`` is what the container
#: image and the hosting platforms set; ``SPRING_PROFILES_ACTIVE`` is accepted
#: because Core is a Spring service and a deployment that profiles one of them
#: very often profiles the other the same way.
_PROD_MARKERS = ("prod", "production")


def _is_production() -> bool:
    for var in ("ENV", "SPRING_PROFILES_ACTIVE"):
        raw = os.getenv(var, "")
        if any(marker == part.strip().lower() for marker in _PROD_MARKERS for part in raw.split(",")):
            return True
    return False


#: A configuration name this close to a real field is a typo, not somebody
#: else's variable. The test is deliberately narrow in *both* directions:
#:
#:   * a real field is a case-insensitive prefix of the variable (``GAP_HMAC_SALT``
#:     vs ``GAP_HMAC_SALT_TYPO``), or
#:   * the variable is a case-insensitive prefix of a real field
#:     (``GAP_HMAC_SALT_TYP`` vs ``GAP_HMAC_SALT``).
#:
#: The first version of this check used a broad prefix list ("anything starting
#: with AI_") and immediately failed on an unrelated ``AI_AGENT`` in the
#: surrounding shell. A check that breaks on a variable it does not own is a
#: check that gets switched off, which restores the silent-typo behaviour it
#: exists to prevent. The 4-character floor keeps a two-letter fragment from
#: matching everything.
_MIN_TYPO_NAME_LENGTH = 4


def _typo_candidates(fields: set[str], environ: Mapping[str, str] | None = None) -> list[str]:
    """Environment variables that are near-misses of a real setting's name."""
    env = os.environ if environ is None else environ
    known = {name.lower() for name in fields}
    suspicious: list[str] = []
    for name in env:
        lowered = name.lower()
        if lowered in known:
            continue
        if len(lowered) < _MIN_TYPO_NAME_LENGTH:
            continue
        if any(field.startswith(lowered) or lowered.startswith(field) for field in known):
            suspicious.append(name)
    return sorted(suspicious)


def _reject_unknown_env_vars() -> None:
    """Fail fast on a misspelled configuration variable.

    ``extra="forbid"`` only covers the dotenv file and explicit constructor
    arguments -- pydantic-settings reads the OS environment by looking up the
    fields it knows about, so an unknown name there is invisible. That is the
    deployment path that matters: a container started with ``GAP_HMAC_SALT_TYPO``
    silently kept the published development salt, ``/gap/hash`` answered 503, and
    nothing in the logs said the deployment was the reason.

    Not every typo is caught. ``GPA_HMAC_SALT`` shares no prefix with a real
    field, so it passes; catching that needs an allowlist per deployment, which
    is a different tool. What is caught is the common case: an extra or a
    truncated suffix on a name that was otherwise right.
    """
    suspicious = _typo_candidates(set(Settings.model_fields))
    if suspicious:
        raise ValueError(
            "Unrecognised configuration variable(s): "
            f"{', '.join(suspicious)}. This service is configured entirely by "
            f"environment variables, so an unknown name means the intended value "
            f"was not applied."
        )


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

    # OpenCode Zen (JEV 1.13 - System One decisions)
    opencode_zen_api_key: str = ""
    opencode_zen_model: str = "jev-1.13-free"
    opencode_zen_url: str = "https://opencode.ai/zen/v1/systemone"

    # Groq (openai/gpt-oss-20b - chat completions)
    groq_api_key: str = ""
    groq_model: str = "openai/gpt-oss-20b"
    groq_base_url: str = "https://api.groq.com/openai/v1"

    # Feature flags for external AI integrations
    enable_jev: bool = True
    enable_groq: bool = True

    @field_validator("gap_hmac_salt")
    @classmethod
    def _reject_known_dev_salt_in_production(cls, v: str) -> str:
        if v.strip() == DEV_SALT:
            if _is_production():
                raise ValueError(
                    f"{PROD_BLOCK} GAP_HMAC_SALT is still the published development value. "
                    "Set a real rotated salt before running with ENV=prod."
                )
            logging.getLogger("app.config").warning(
                "GAP_HMAC_SALT is still the published development value. The coverage-gap "
                "join is only privacy-preserving with a real, rotated salt."
            )
        return v

    @field_validator("ai_service_token")
    @classmethod
    def _reject_known_dev_token_in_production(cls, v: str) -> str:
        if v.strip() == DEV_TOKEN:
            if _is_production():
                raise ValueError(
                    f"{PROD_BLOCK} AI_SERVICE_TOKEN is still the published development value. "
                    "Set a real secret before running with ENV=prod."
                )
            logging.getLogger("app.config").warning(
                "AI_SERVICE_TOKEN is still the published development value. Every route "
                "except /health answers 503 until it is replaced (see app.security)."
            )
        return v

    @field_validator("core_service_token")
    @classmethod
    def _reject_known_dev_token_as_core_credential(cls, v: str) -> str:
        if v.strip() == DEV_TOKEN and _is_production():
            raise ValueError(
                f"{PROD_BLOCK} CORE_SERVICE_TOKEN cannot be the AI service token: Core "
                "verifies a real signed JWT, and reusing one shared string for both "
                "means a token leaked from either side authenticates against both."
            )
        return v

    @field_validator("allow_insecure_dev")
    @classmethod
    def _refuse_insecure_dev_in_production(cls, v: bool) -> bool:
        if v and _is_production():
            raise ValueError(
                f"{PROD_BLOCK} ALLOW_INSECURE_DEV=true re-opens /gap/hash, which takes a "
                "raw Aadhaar reference, to anyone who can reach the port."
            )
        if v:
            logging.getLogger("app.config").warning(
                "ALLOW_INSECURE_DEV=true — the published dev service token is accepted. "
                "Every protected route is open to anyone who has read this repository."
            )
        return v

    @field_validator("docai_max_upload_bytes")
    @classmethod
    def _cap_upload_limit(cls, v: int) -> int:
        if v <= 0:
            raise ValueError("DOCAI_MAX_UPLOAD_BYTES must be positive")
        return min(v, 50 * 1024 * 1024)

    # `extra="forbid"` covers the dotenv file and any explicit constructor
    # argument. For the OS environment -- the path a real deployment uses --
    # `_reject_unknown_env_vars` does the same job, because pydantic-settings
    # looks up only the fields it knows about and cannot notice a name it was
    # never told to read.
    model_config = SettingsConfigDict(
        env_prefix="", env_file=".env", env_file_encoding="utf-8", extra="forbid"
    )


settings = Settings()
_reject_unknown_env_vars()
