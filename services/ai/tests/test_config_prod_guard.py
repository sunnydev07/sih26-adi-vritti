"""Production refuses to boot on development secrets (task 0.2).

`infra/docker-compose.yml` pins every credential to a value committed in this
repository, and `application-dev.yml` does the same for Core. That is what makes
`make dev` work with no setup, and it is exactly the thing that must not survive
into a deployment. The prod profile now fails at import rather than coming up and
serving, because a Core running with `ALLOW_INSECURE_DEV=true` accepts
unauthenticated requests on every `/v1` route and its Aadhaar vault key, claim key
and JWT secret are all public.

The singleton `app.config.settings` is built at import, so these tests exercise
`Settings` directly rather than mutating a module global.
"""

from __future__ import annotations

import pytest

from app.config import (
    DEV_SALT,
    DEV_TOKEN,
    Settings,
    _is_production,
    _reject_unknown_env_vars,
    _typo_candidates,
)


@pytest.fixture
def prod_env(monkeypatch):
    monkeypatch.setenv("ENV", "prod")
    monkeypatch.delenv("SPRING_PROFILES_ACTIVE", raising=False)
    return monkeypatch


def test_prod_is_recognised_from_env(prod_env):
    assert _is_production() is True


def test_prod_is_recognised_from_spring_profiles(monkeypatch):
    monkeypatch.delenv("ENV", raising=False)
    monkeypatch.setenv("SPRING_PROFILES_ACTIVE", "production")
    assert _is_production() is True


def test_a_comma_separated_profile_list_still_counts(monkeypatch):
    monkeypatch.delenv("ENV", raising=False)
    monkeypatch.setenv("SPRING_PROFILES_ACTIVE", "staging,prod,metrics")
    assert _is_production() is True


def test_development_is_not_production(monkeypatch):
    monkeypatch.setenv("ENV", "dev")
    monkeypatch.delenv("SPRING_PROFILES_ACTIVE", raising=False)
    assert _is_production() is False


def test_prod_rejects_the_published_gap_salt(prod_env):
    with pytest.raises(ValueError, match="GAP_HMAC_SALT"):
        Settings(gap_hmac_salt=DEV_SALT)


def test_prod_rejects_the_published_service_token(prod_env):
    with pytest.raises(ValueError, match="AI_SERVICE_TOKEN"):
        Settings(ai_service_token=DEV_TOKEN)


def test_prod_rejects_allow_insecure_dev(prod_env):
    with pytest.raises(ValueError, match="ALLOW_INSECURE_DEV"):
        Settings(allow_insecure_dev=True)


def test_prod_rejects_reusing_the_service_token_as_the_core_credential(prod_env):
    with pytest.raises(ValueError, match="CORE_SERVICE_TOKEN"):
        Settings(core_service_token=DEV_TOKEN)


def test_prod_starts_on_real_secrets(prod_env):
    settings = Settings(
        gap_hmac_salt="a-real-rotated-salt",
        ai_service_token="a-real-service-token",
        core_service_token="a-real-jwt",
    )
    assert settings.gap_hmac_salt == "a-real-rotated-salt"
    assert settings.ai_service_token == "a-real-service-token"


def test_development_still_starts_on_the_published_values(monkeypatch):
    # `make dev` depends on this. Failing here would make the local stack
    # unbootable, which is a different problem from the one being fixed.
    monkeypatch.setenv("ENV", "dev")
    monkeypatch.delenv("SPRING_PROFILES_ACTIVE", raising=False)
    settings = Settings(gap_hmac_salt=DEV_SALT, ai_service_token=DEV_TOKEN)
    assert settings.gap_hmac_salt == DEV_SALT


def test_a_misspelled_key_in_the_env_file_is_rejected(tmp_path):
    # extra="forbid" covers the dotenv file: an unknown key there is an error
    # rather than a value that is quietly not applied.
    env_file = tmp_path / ".env"
    env_file.write_text("GAP_HMAC_SALT_TYPO=value\n", encoding="utf-8")
    with pytest.raises(ValueError):
        Settings(_env_file=str(env_file))


def test_a_misspelled_environment_variable_is_rejected(monkeypatch):
    # The deployment path: a container started with GAP_HMAC_SALT_TYPO kept the
    # published dev salt, /gap/hash answered 503, and nothing said why.
    monkeypatch.setenv("GAP_HMAC_SALT_TYPO", "value")
    with pytest.raises(ValueError, match="GAP_HMAC_SALT_TYPO"):
        _reject_unknown_env_vars()


def test_a_truncated_variable_name_is_rejected(monkeypatch):
    monkeypatch.setenv("GAP_HMAC_SA", "value")
    with pytest.raises(ValueError, match="GAP_HMAC_SA"):
        _reject_unknown_env_vars()


def test_a_recognised_variable_passes(monkeypatch):
    monkeypatch.setenv("GAP_HMAC_SALT", "a-real-salt")
    _reject_unknown_env_vars()  # must not raise


def test_an_unrelated_prefixed_variable_is_left_alone():
    # Regression guard for a real failure: a broad "anything starting with AI_"
    # check refused to boot on an unrelated AI_AGENT in the surrounding shell.
    # A check that breaks on a variable it does not own is a check that gets
    # turned off, which restores the silent-typo behaviour it exists to prevent.
    fields = {"ai_service_token", "core_service_url"}
    assert _typo_candidates(fields, {"AI_AGENT": "x"}) == []
    assert _typo_candidates(fields, {"PATH": "x", "HOME": "x", "JAVA_HOME": "x"}) == []
    assert _typo_candidates(fields, {"AI_SERVICE_TOKEN": "x"}) == []
    assert _typo_candidates(fields, {"AI_SERVICE_TOK": "x"}) == ["AI_SERVICE_TOK"]


def test_the_check_survives_the_surrounding_shell():
    # Not a mock: this is the environment the suite actually runs in. If any
    # variable in it looks like a near-miss of a real setting, the process would
    # refuse to import, and so would every deployed service on that host.
    _reject_unknown_env_vars()
